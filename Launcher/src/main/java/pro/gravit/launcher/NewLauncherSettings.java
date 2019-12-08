package pro.gravit.launcher;

import pro.gravit.launcher.client.UserSettings;
import pro.gravit.launcher.hasher.HashedDir;
import pro.gravit.launcher.profiles.ClientProfile;

import java.nio.file.Path;
import java.util.*;

public class NewLauncherSettings {
    @LauncherNetworkAPI
    public Map<String, UserSettings> userSettings = new HashMap<>();
    @LauncherNetworkAPI
    public List<String> features = new ArrayList<>();
    @LauncherNetworkAPI
    public String consoleUnlockKey;

    public static class HashedStoreEntry {
        public final HashedDir hdir;
        public final String name;
        public final String fullPath;
        public transient boolean needSave = false;

        public HashedStoreEntry(HashedDir hdir, String name, String fullPath) {
            this.hdir = hdir;
            this.name = name;
            this.fullPath = fullPath;
        }
    }

    @LauncherNetworkAPI
    public final transient List<HashedStoreEntry> lastHDirs = new ArrayList<>(16);

    public void putHDir(String name, Path path, HashedDir dir) {
        String fullPath = path.toAbsolutePath().toString();
        lastHDirs.removeIf((e) -> e.fullPath.equals(fullPath) && e.name.equals(name));
        HashedStoreEntry e = new HashedStoreEntry(dir, name, fullPath);
        e.needSave = true;
        lastHDirs.add(e);
    }
}
