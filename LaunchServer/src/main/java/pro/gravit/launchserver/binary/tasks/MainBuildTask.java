package pro.gravit.launchserver.binary.tasks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import pro.gravit.launcher.AutogenConfig;
import pro.gravit.launcher.Launcher;
import pro.gravit.launcher.LauncherConfig;
import pro.gravit.launcher.SecureAutogenConfig;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.asm.ClassMetadataReader;
import pro.gravit.launchserver.asm.ConfigGenerator;
import pro.gravit.launchserver.asm.InjectClassAcceptor;
import pro.gravit.launchserver.asm.SafeClassWriter;
import pro.gravit.launchserver.binary.BuildContext;
import pro.gravit.launchserver.binary.LauncherConfigurator;
import pro.gravit.utils.HookException;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.JarHelper;
import pro.gravit.utils.helper.LogHelper;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class MainBuildTask implements LauncherBuildTask {
    private final LaunchServer server;
    public final ClassMetadataReader reader;
    @FunctionalInterface
    public interface Transformer {
        byte[] transform(byte[] input, String classname, BuildContext context);
    }
    public static class IOHookSet<R> {
        public final Set<IOHook<R>> list = new HashSet<>();

        @FunctionalInterface
        public interface IOHook<R> {
            /**
             * @param context custom param
             * False to continue processing hook
             * @throws HookException The hook may return the error text throwing this exception
             */
            void hook(R context) throws HookException, IOException;
        }

        public void registerHook(IOHook<R> hook) {
            list.add(hook);
        }

        public boolean unregisterHook(IOHook<R> hook) {
            return list.remove(hook);
        }

        /**
         * @param context custom param
         * False to continue
         * @throws HookException The hook may return the error text throwing this exception
         */
        public void hook(R context) throws HookException, IOException {
            for (IOHook<R> hook : list) {
                hook.hook(context);
            }
        }
    }

    public interface ASMTransformer extends Transformer {
        default byte[] transform(byte[] input, String classname, BuildContext context)
        {
            ClassReader reader = new ClassReader(input);
            ClassNode cn = new ClassNode();
            reader.accept(cn, 0);
            transform(cn, classname, context);
            SafeClassWriter writer = new SafeClassWriter(context.task.reader,ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(writer);
            return writer.toByteArray();
        }
        void transform(ClassNode cn, String classname, BuildContext context);
    }
    public abstract static class ASMAnnotationFieldProcessor implements ASMTransformer
    {
        private final String desc;

        protected ASMAnnotationFieldProcessor(String desc) {
            this.desc = desc;
        }

        @Override
        public void transform(ClassNode cn, String classname, BuildContext context) {
            for(FieldNode fn : cn.fields)
            {
                if(fn.invisibleAnnotations == null || fn.invisibleAnnotations.isEmpty()) continue;
                AnnotationNode found = null;
                for(AnnotationNode an : fn.invisibleAnnotations)
                {
                    if(an == null) continue;
                    if(desc.equals(an.desc))
                    {
                        found = an;
                        break;
                    }
                }
                if(found != null)
                {
                    transformField(found, fn, cn, classname, context);
                }
            }
        }
        abstract public void transformField(AnnotationNode an, FieldNode fn, ClassNode cn, String classname, BuildContext context);
    }
    public Set<String> blacklist = new HashSet<>();
    public List<Transformer> transformers = new ArrayList<>();
    public IOHookSet<BuildContext> preBuildHook = new IOHookSet<>();
    public IOHookSet<BuildContext> postBuildHook = new IOHookSet<>();

    public Map<String, Object> properties = new HashMap<>();

    public MainBuildTask(LaunchServer srv) {
        server = srv;
        reader = new ClassMetadataReader();
        InjectClassAcceptor injectClassAcceptor = new InjectClassAcceptor(properties);
        transformers.add(injectClassAcceptor);
    }

    @Override
    public String getName() {
        return "MainBuild";
    }

    @Override
    public Path process(Path inputJar) throws IOException {
        Path outputJar = server.launcherBinary.nextPath("main");
        try (ZipOutputStream output = new ZipOutputStream(IOHelper.newOutput(outputJar))) {
            ClassNode cn = new ClassNode();
            new ClassReader(JarHelper.getClassBytes(AutogenConfig.class)).accept(cn, 0);
            LauncherConfigurator launcherConfigurator = new LauncherConfigurator(cn);
            ClassNode cn1 = new ClassNode();
            new ClassReader(JarHelper.getClassBytes(SecureAutogenConfig.class)).accept(cn1, 0);
            ConfigGenerator secureConfigurator = new ConfigGenerator(cn1);
            BuildContext context = new BuildContext(output, launcherConfigurator, reader.getCp(), this);
            preBuildHook.hook(context);
            launcherConfigurator.setStringField("address", server.config.netty.address);
            launcherConfigurator.setStringField("projectname", server.config.projectName);
            launcherConfigurator.setStringField("secretKeyClient", SecurityHelper.randomStringAESKey());
            launcherConfigurator.setIntegerField("clientPort", 32148 + SecurityHelper.newRandom().nextInt(512));
            launcherConfigurator.setStringField("guardType", server.config.launcher.guardType);
            launcherConfigurator.setBooleanField("isWarningMissArchJava", server.config.launcher.warningMissArchJava);
            launcherConfigurator.setEnv(server.config.env);
            launcherConfigurator.setStringField("passwordEncryptKey", server.runtime.passwordEncryptKey);
            List<byte[]> certificates = Arrays.stream(server.certificateManager.trustManager.getTrusted()).map(e -> {
                try {
                    return e.getEncoded();
                } catch (CertificateEncodingException e2) {
                    LogHelper.error(e2);
                    return new byte[0];
                }
            }).collect(Collectors.toList());
            if(!server.config.sign.enabled)
            {
                CertificateAutogenTask task = server.launcherBinary.getTaskByClass(CertificateAutogenTask.class).get();
                try {
                    certificates.add(task.certificate.getEncoded());
                } catch (CertificateEncodingException e) {
                    throw new InternalError(e);
                }
            }
            secureConfigurator.setByteArrayListField("certificates", certificates);
            String launcherSalt = SecurityHelper.randomStringToken();
            byte[] launcherSecureHash = SecurityHelper.digest(SecurityHelper.DigestAlgorithm.SHA256,
                    server.runtime.clientCheckSecret.concat(".").concat(launcherSalt));
            launcherConfigurator.setStringField("secureCheckHash", Base64.getEncoder().encodeToString(launcherSecureHash));
            launcherConfigurator.setStringField("secureCheckSalt", launcherSalt);
            //LogHelper.debug("[checkSecure] %s: %s", launcherSalt, Arrays.toString(launcherSecureHash));
            if (server.runtime.oemUnlockKey == null) server.runtime.oemUnlockKey = SecurityHelper.randomStringToken();
            launcherConfigurator.setStringField("oemUnlockKey", server.runtime.oemUnlockKey);
            context.clientModules.forEach(launcherConfigurator::addModuleClass);
            reader.getCp().add(new JarFile(inputJar.toFile()));
            server.launcherBinary.coreLibs.forEach(e -> {
                try {
                    reader.getCp().add(new JarFile(e.toFile()));
                } catch (IOException e1) {
                    LogHelper.error(e1);
                }
            });
            context.pushBytes(launcherConfigurator.getZipEntryPath(), launcherConfigurator.getBytecode(reader));
            context.pushBytes(secureConfigurator.getZipEntryPath(), secureConfigurator.getBytecode(reader));

            context.pushJarFile(inputJar, (e) -> blacklist.contains(e.getName()), (e) -> true);

            // map for guard
            Map<String, byte[]> runtime = new HashMap<>(256);
            // Write launcher guard dir
            context.pushDir(server.launcherBinary.runtimeDir, Launcher.RUNTIME_DIR, runtime, false);
            context.pushDir(server.launcherBinary.guardDir, Launcher.GUARD_DIR, runtime, false);

            LauncherConfig launcherConfig = new LauncherConfig(server.config.netty.address, server.publicKey, runtime, server.config.projectName);
            context.pushFile(Launcher.CONFIG_FILE, launcherConfig);
            postBuildHook.hook(context);
        }
        reader.close();
        return outputJar;
    }

    public byte[] transformClass(byte[] bytes, String classname, BuildContext context)
    {
        byte[] result = bytes;
        ClassReader cr = null;
        ClassWriter writer = null;
        ClassNode cn = null;
        for(Transformer t : transformers)
        {
            if(t instanceof ASMTransformer)
            {
                ASMTransformer asmTransformer = (ASMTransformer) t;
                if(cn == null)
                {
                    cr = new ClassReader(result);
                    cn = new ClassNode();
                    cr.accept(cn, 0);
                }
                asmTransformer.transform(cn, classname, context);
                continue;
            }
            else if(cn != null)
            {
                writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                cn.accept(writer);
                result = writer.toByteArray();
            }
            byte[] old_result = result;
            result = t.transform(result, classname, context);
            if(old_result != result)
            {
                cr = null;
                cn = null;
            }
        }
        if(cn != null)
        {
            writer = new SafeClassWriter(reader,ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(writer);
            result = writer.toByteArray();
        }
        return result;
    }

    @Override
    public boolean allowDelete() {
        return true;
    }
}
