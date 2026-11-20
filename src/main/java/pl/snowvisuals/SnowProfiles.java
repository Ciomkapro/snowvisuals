package pl.snowvisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Named snapshots of the complete Snow Visuals configuration. */
public final class SnowProfiles {
    public static final int MAX_NAME_LENGTH = 32;

    private static final Logger LOGGER = LoggerFactory.getLogger("SnowVisuals");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PROFILES_FILE = SnowConfig.activePath().resolveSibling("snowvisuals-profiles.json");
    private static final List<String> LEGACY_NAMES = List.of("Default", "PvP", "Survival");

    private static List<StoredProfile> profiles;
    private static boolean opened;

    private SnowProfiles() {}

    public record Profile(String id, String name) {}

    public static synchronized List<Profile> list() {
        List<StoredProfile> current = profiles();
        if (current == null) return List.of();
        return current.stream().map(StoredProfile::summary).toList();
    }

    public static boolean validName(String name) {
        return normalizedName(name) != null;
    }

    public static synchronized Profile create(String name, SnowConfig config) {
        String normalized = normalizedName(name);
        List<StoredProfile> current = profiles();
        if (normalized == null || config == null || current == null
                || current.stream().anyMatch(profile -> profile.name.equalsIgnoreCase(normalized))) {
            return null;
        }

        StoredProfile created = new StoredProfile(UUID.randomUUID().toString(), normalized, snapshot(config));
        List<StoredProfile> changed = new ArrayList<>(current);
        changed.add(created);
        return commit(changed) ? created.summary() : null;
    }

    public static synchronized boolean rename(String id, String name) {
        String normalized = normalizedName(name);
        List<StoredProfile> current = profiles();
        int index = indexOf(current, id);
        if (normalized == null || index < 0 || current.stream().anyMatch(profile ->
                !profile.id.equals(id) && profile.name.equalsIgnoreCase(normalized))) {
            return false;
        }

        StoredProfile previous = current.get(index);
        if (previous.name.equals(normalized)) return true;
        return replace(current, index, new StoredProfile(previous.id, normalized, previous.snapshot));
    }

    public static synchronized boolean update(String id, SnowConfig config) {
        List<StoredProfile> current = profiles();
        int index = indexOf(current, id);
        if (index < 0 || config == null) return false;

        StoredProfile previous = current.get(index);
        return replace(current, index, new StoredProfile(previous.id, previous.name, snapshot(config)));
    }

    public static synchronized boolean delete(String id) {
        List<StoredProfile> current = profiles();
        int index = indexOf(current, id);
        if (index < 0) return false;

        List<StoredProfile> changed = new ArrayList<>(current);
        changed.remove(index);
        return commit(changed);
    }

    public static synchronized SnowConfig load(String id) {
        List<StoredProfile> current = profiles();
        int index = indexOf(current, id);
        if (index < 0) return null;

        try {
            SnowConfig loaded = GSON.fromJson(current.get(index).snapshot, SnowConfig.class);
            loaded.initialize();
            return loaded;
        } catch (RuntimeException failure) {
            LOGGER.warn("Could not initialize profile {}", id, failure);
            return null;
        }
    }

    /** Applies a profile on the client thread after persisting the active configuration. */
    public static synchronized boolean use(String id) {
        SnowConfig loaded = load(id);
        if (loaded == null || !loaded.write(SnowConfig.activePath())) return false;

        SnowVisualsClient.CONFIG = loaded;
        SnowVisualsClient.consumeBind();
        return true;
    }

    private static String normalizedName(String name) {
        if (name == null) return null;
        String normalized = Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > MAX_NAME_LENGTH
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            return null;
        }
        return normalized;
    }

    private static int indexOf(List<StoredProfile> entries, String id) {
        if (entries == null || id == null) return -1;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id.equals(id)) return index;
        }
        return -1;
    }

    private static JsonObject snapshot(SnowConfig config) {
        return GSON.toJsonTree(config).getAsJsonObject();
    }

    private static boolean replace(List<StoredProfile> current, int index, StoredProfile updated) {
        List<StoredProfile> changed = new ArrayList<>(current);
        changed.set(index, updated);
        return commit(changed);
    }

    private static List<StoredProfile> profiles() {
        if (opened) return profiles;
        opened = true;
        try {
            Store store = GSON.fromJson(Files.readString(PROFILES_FILE), Store.class);
            profiles = validate(store);
        } catch (NoSuchFileException missing) {
            profiles = legacyProfiles();
        } catch (IOException | RuntimeException failure) {
            // Preserve an unreadable store for recovery without an accidental overwrite.
            LOGGER.warn("Could not read named profiles", failure);
        }
        return profiles;
    }

    private static List<StoredProfile> validate(Store store) throws IOException {
        if (store == null || store.profiles == null) throw new IOException("Missing profile list");
        Set<String> ids = new HashSet<>();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        List<StoredProfile> validated = new ArrayList<>(store.profiles.size());
        for (StoredProfile profile : store.profiles) {
            String name = profile == null ? null : normalizedName(profile.name);
            if (name == null || profile.snapshot == null || profile.id == null || profile.id.isBlank()
                    || !ids.add(profile.id) || !names.add(name)) {
                throw new IOException("Invalid or duplicate profile");
            }
            validated.add(new StoredProfile(profile.id, name, profile.snapshot));
        }
        return List.copyOf(validated);
    }

    private static List<StoredProfile> legacyProfiles() {
        List<StoredProfile> migrated = new ArrayList<>();
        for (int slot = 0; slot < LEGACY_NAMES.size(); slot++) {
            SnowConfig previous = SnowConfig.loadProfile(slot);
            if (previous == null) continue;
            String id = UUID.nameUUIDFromBytes(("snowvisuals:legacy:" + slot).getBytes(StandardCharsets.UTF_8)).toString();
            migrated.add(new StoredProfile(id, LEGACY_NAMES.get(slot), snapshot(previous)));
        }
        return List.copyOf(migrated);
    }

    private static boolean commit(List<StoredProfile> changed) {
        try {
            writeAtomically(PROFILES_FILE, GSON.toJson(new Store(changed)));
            profiles = List.copyOf(changed);
            return true;
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not save named profiles", failure);
            return false;
        }
    }

    static void writeAtomically(Path target, String content) throws IOException {
        Path directory = target.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString() + "-", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    LOGGER.warn("Could not remove temporary configuration {}", temporary, failure);
                }
            }
        }
    }

    private record StoredProfile(String id, String name, JsonObject snapshot) {
        private Profile summary() {
            return new Profile(id, name);
        }
    }

    private record Store(List<StoredProfile> profiles) {}
}
