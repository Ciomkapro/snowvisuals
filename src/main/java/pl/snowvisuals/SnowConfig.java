package pl.snowvisuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import static pl.snowvisuals.SnowModules.Module;

public final class SnowConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path FILE = DIR.resolve("snowvisuals.json");

    public boolean playerGlow = false;
    public int glowColor = 0xFFFFFF;
    public boolean dark = true;
    public int accentColor = 0x8B72E9;
    public int guiColor = 0x171224;
    public int hudColor = 0xFFFFFF;
    public String language = "en";
    public int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public int guiScale = 100;
    public int guiTransparency = 0;
    public boolean hudSnap = true;

    // Read legacy settings without losing HUD positions.
    public boolean showCoords = true, showFps = true, showServer = true;
    public int coordsX = 8, coordsY = 8;
    public int fpsX = 8, fpsY = 36;
    public int serverX = 8, serverY = 64;
    public int coordsScale = 100, fpsScale = 100, serverScale = 100;
    public SnowModules modules;

    public void initialize() {
        if (modules == null) {
            modules = new SnowModules();
            migrate(Module.COORDS, showCoords, coordsX, coordsY, coordsScale);
            migrate(Module.FPS, showFps, fpsX, fpsY, fpsScale);
            migrate(Module.SERVER, showServer, serverX, serverY, serverScale);
        }
        for (Module module : Module.values()) {
            SnowModules.State s = modules.get(module);
            s.scale = clamp(s.scale, 50, 200);
            s.opacity = clamp(s.opacity, 0, 100);
            s.color &= 0xFFFFFF;
            s.color2 &= 0xFFFFFF;
            s.x = Math.max(0, s.x);
            s.y = Math.max(0, s.y);
            if (!SnowBinds.valid(s.bind)) s.bind = -1;
            validateOptions(module, s);
        }
        modules.get(Module.GLOW).enabled = playerGlow;
        glowColor &= 0xFFFFFF;
        accentColor &= 0xFFFFFF;
        guiColor &= 0xFFFFFF;
        hudColor &= 0xFFFFFF;
        guiScale = clamp(guiScale, 50, 150);
        guiTransparency = clamp(guiTransparency, 0, 100);
        language = SnowLanguage.normalize(language);
        if (!validKey(guiKey)) guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    private static void validateOptions(Module module, SnowModules.State state) {
        switch (module) {
            case PARTICLES -> {
                if (state.a == 100 && state.color == 0xFFFFFF) state.color = 0x91DBFF;
                state.a = state.a < 4 || state.a > 32 ? 12 : state.a;
                state.b = clamp(state.b, 0, 2);
            }
            case TRAILS -> {
                if (state.a == 100 && state.b == 0 && state.color == 0xFFFFFF) state.color = 0x8ACBFF;
                state.a = state.a < 300 || state.a > 2500 ? 1000 : state.a;
                state.b = state.b < 4 || state.b > 36 ? 14 : state.b;
                state.c = state.c == 0 ? 105 : clamp(state.c, 20, 200);
            }
            case TOGGLE_SPRINT, TOGGLE_SNEAK, TOTEM_COUNT -> state.a = flag(state.a);
            case BETTER_CPS -> {
                state.a = clamp(state.a, 250, 2000);
                state.b = clamp(state.b, 0, 2);
                state.c = flag(state.c);
            }
            case COMBO -> {
                state.a = clamp(state.a, 500, 4000);
                state.b = flag(state.b);
            }
            case HIT_MARKER -> {
                state.a = clamp(state.a, 100, 1000);
                state.b = clamp(state.b, 4, 20);
            }
            case DAMAGE_TINT -> {
                state.a = clamp(state.a, 5, 90);
                state.b = clamp(state.b, 100, 1200);
            }
            case PEARL_COOLDOWN, ARROW_COUNT, DIRECTION -> {
                state.a = flag(state.a);
                state.b = flag(state.b);
            }
            case BOW_CHARGE -> {
                state.a = clamp(state.a, 0, 2);
                state.b = flag(state.b);
            }
            case TARGET_HUD -> {
                state.a = clamp(state.a, 500, 8000);
                state.b = flag(state.b);
                state.c = flag(state.c);
                if (state.markerRange == 0) {
                    state.markerRange = 5;
                    state.d = 1;
                    state.color = 0x9DD7FF;
                }
                state.markerRange = clamp(state.markerRange, 2, 8);
                state.markerSpeed = state.markerSpeed <= 0 ? 100 : clamp(state.markerSpeed, 20, 250);
                state.markerSize = clamp(state.markerSize, 30, 200);
                state.d = clamp(state.d, 0, 3);
            }
            case PING_GRAPH -> {
                state.a = clamp(state.a, 5, 60);
                state.b = flag(state.b);
            }
            case FULLBRIGHT -> state.a = clamp(state.a, 0, 100);
            case CHINA_HAT -> state.a = clamp(state.a, 50, 200);
            case SPOTIFY -> {
                if (!SnowBinds.valid(state.a)) state.a = -1;
                if (!SnowBinds.valid(state.b)) state.b = -1;
                if (!SnowBinds.valid(state.c)) state.c = -1;
                state.d = flag(state.d);
            }
            case ITEM_COUNT -> {
                state.a &= 31;
                state.b = flag(state.b);
            }
            default -> {}
        }
    }

    private static int flag(int value) {
        return value != 0 ? 1 : 0;
    }

    private void migrate(Module module, boolean enabled, int x, int y, int scale) {
        SnowModules.State s = modules.get(module);
        s.enabled = enabled; s.x = x; s.y = y; s.scale = scale;
    }

    public SnowModules.State state(Module module) {
        if (modules == null) modules = new SnowModules();
        return modules.get(module);
    }

    public boolean enabled(Module module) {
        return module == Module.GLOW ? playerGlow : state(module).enabled;
    }

    public void toggle(Module module) {
        boolean value = !enabled(module);
        state(module).enabled = value;
        if (module == Module.GLOW) playerGlow = value;
        save();
    }

    public void resetHud() {
        if (modules != null) modules.resetPositions();
        save();
    }

    public void resetGui() {
        dark = true;
        accentColor = 0x8B72E9;
        guiColor = 0x171224;
        guiScale = 100;
        guiTransparency = 0;
        guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
        save();
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static boolean validKey(int key) {
        return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST
                && key != GLFW.GLFW_KEY_ESCAPE;
    }

    static Path activePath() { return FILE; }

    static SnowConfig read(Path path) {
        try {
            if (!Files.exists(path)) return null;
            SnowConfig config = GSON.fromJson(Files.readString(path), SnowConfig.class);
            if (config != null) config.initialize();
            return config;
        } catch (Exception e) {
            LoggerFactory.getLogger("SnowVisuals").warn("Could not load config", e);
            return null;
        }
    }

    public static SnowConfig load() {
        SnowConfig config = read(FILE);
        if (config == null) { config = new SnowConfig(); config.initialize(); }
        return config;
    }

    boolean write(Path path) {
        try {
            SnowProfiles.writeAtomically(path, GSON.toJson(this));
            return true;
        } catch (Exception e) {
            LoggerFactory.getLogger("SnowVisuals").warn("Could not save config", e);
            return false;
        }
    }

    public void save() { write(FILE); }
    public boolean saveProfile(int slot) { return write(DIR.resolve("snowvisuals-profile-" + slot + ".json")); }
    public static SnowConfig loadProfile(int slot) { return read(DIR.resolve("snowvisuals-profile-" + slot + ".json")); }
}
