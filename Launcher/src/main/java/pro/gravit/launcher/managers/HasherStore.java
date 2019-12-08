package pro.gravit.launcher.managers;

import pro.gravit.launcher.hasher.FileNameMatcher;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.hasher.HashedEntry;
import pro.gravit.launcher.profiles.ClientProfile;
import pro.gravit.utils.helper.IOHelper;
import pro.gravit.utils.helper.LogHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

public class HasherStore {
    public Map<String, HasherStoreEnity> store;

    public static class HasherStoreEnity {

        public HashedDir hdir;

        public Path dir;

        public Collection<String> shared;
    }


    public void addProfileUpdateDir(ClientProfile profile, Path dir, HashedDir hdir) {
        HasherStoreEnity e = new HasherStoreEnity();
        e.hdir = hdir;
        e.dir = dir;
        e.shared = profile.getShared();

        store.put(profile.getTitle(), e);
    }


    public void copyCompareFilesTo(String name, Path targetDir, HashedDir targetHDir, String[] shared) {
        store.forEach((key, e) -> {
            if (key.equals(name)) return;
            FileNameMatcher nm = new FileNameMatcher(shared, null, null);
            HashedDir compare = targetHDir.sideCompare(e.hdir, nm, new LinkedList<>(), true);
            compare.map().forEach((arg1, arg2) -> recurseCopy(arg1, arg2, name, targetDir, e.dir));
        });
    }


    public void recurseCopy(String filename, HashedEntry entry, String name, Path targetDir, Path sourceDir) {
        if (!IOHelper.isDir(targetDir)) {
            try {
                Files.createDirectories(targetDir);
            } catch (IOException e1) {
                LogHelper.error(e1);
            }
        }
        if (entry.getType().equals(HashedEntry.Type.DIR)) {
            ((HashedDir) entry).map().forEach((arg1, arg2) -> recurseCopy(arg1, arg2, name, targetDir.resolve(filename), sourceDir.resolve(filename)));
        } else if (entry.getType().equals(HashedEntry.Type.FILE)) {
            try {
                IOHelper.copy(sourceDir.resolve(filename), targetDir.resolve(filename));
            } catch (IOException e) {
                LogHelper.error(e);
            }
        }
    }
}
