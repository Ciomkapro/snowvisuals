package pl.snowvisuals;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static pl.snowvisuals.SnowModules.Module;

public final class SnowBinds {

    public static final int UP = -100;
    public static final int DOWN = -101;
    public static final int MIDDLE = -102;

    private static final Set<Module> latched =
            EnumSet.noneOf(Module.class);

    private static SnowConfig lastConfig;
    private static Object lastWorld;

    private SnowBinds() {}

    public static boolean isScroll(int key) {
        return key == UP || key == DOWN;
    }

    public static boolean valid(int key) {

        return key == -1
                || key == UP
                || key == DOWN
                || key == MIDDLE
                || SnowConfig.validKey(key);
    }

    public static String name(int key) {

        return switch (key) {

            case UP -> "Scroll Up";
            case DOWN -> "Scroll Down";
            case MIDDLE -> "Middle Mouse";

            case -1 -> "Unbound";

            case GLFW.GLFW_KEY_RIGHT_SHIFT ->
                    "Right Shift";

            case GLFW.GLFW_KEY_LEFT_SHIFT ->
                    "Left Shift";

            case GLFW.GLFW_KEY_LEFT_CONTROL ->
                    "Left Ctrl";

            case GLFW.GLFW_KEY_RIGHT_CONTROL ->
                    "Right Ctrl";

            case GLFW.GLFW_KEY_LEFT_ALT ->
                    "Left Alt";

            case GLFW.GLFW_KEY_RIGHT_ALT ->
                    "Right Alt";

            case GLFW.GLFW_KEY_SPACE ->
                    "Space";

            default -> {

                if (key >= GLFW.GLFW_KEY_F1
                        && key <= GLFW.GLFW_KEY_F25) {

                    yield "F" + (
                            key - GLFW.GLFW_KEY_F1 + 1
                    );
                }

                if (!SnowConfig.validKey(key)) {

                    yield "Unbound";
                }

                String text =
                        GLFW.glfwGetKeyName(key, 0);

                yield text == null
                        ? "Key " + key
                        : text.toUpperCase(Locale.ROOT);
            }
        };
    }

    private static boolean holdModule(Module module) {

        return module == Module.ZOOM
                || module == Module.FREELOOK;
    }

    public static void assign(Module module, int key) {

        SnowConfig cfg = SnowVisualsClient.CONFIG;

        if (cfg == null || !valid(key)) return;

        // Prevent conflicts with the GUI opening key.

        if (key == cfg.guiKey) {
            return;
        }

        // Remove duplicate binds.

        if (key != -1) {

            for (Module other : Module.values()) {

                if (other != module
                        && cfg.state(other).bind == key) {

                    cfg.state(other).bind = -1;

                    latched.remove(other);
                }
            }
        }

        SnowSpotifyKeys.clear(cfg, key);
        cfg.state(module).bind = key;

        latched.remove(module);

        cfg.save();

        SnowVisualsClient.consumeBind();
    }

    static void clearModuleBind(Module module) {
        SnowVisualsClient.CONFIG.state(module).bind = -1;
        latched.remove(module);
    }

    public static void tick(MinecraftClient mc) {

        SnowConfig cfg = SnowVisualsClient.CONFIG;

        if (cfg == null) return;

        if (lastConfig != cfg
                || lastWorld != mc.world
                || mc.player == null) {

            latched.clear();

            lastConfig = cfg;
            lastWorld = mc.world;
        }

        latched.removeIf(module ->
                !cfg.enabled(module)
                        || !isScroll(cfg.state(module).bind)
        );
    }

    private static boolean inGame() {

        MinecraftClient mc =
                MinecraftClient.getInstance();

        return SnowVisualsClient.CONFIG != null
                && mc.player != null
                && mc.world != null
                && mc.currentScreen == null;
    }

    public static boolean dispatch(int key) {

        if (!inGame() || key == -1) return false;

        SnowConfig cfg = SnowVisualsClient.CONFIG;

        boolean consumed = SnowSpotifyKeys.dispatch(key);

        for (Module module : Module.values()) {

            if (cfg.state(module).bind != key) {
                continue;
            }

            if (holdModule(module)) {

                if (!cfg.enabled(module)) continue;

                if (isScroll(key)) {

                    if (!latched.add(module)) {
                        latched.remove(module);
                    }
                }

            } else {

                cfg.toggle(module);
            }

            consumed = true;
        }

        return consumed;
    }

    public static boolean middleBound() {
        if (inGame() && SnowSpotifyKeys.isBound(MIDDLE)) return true;

        if (!inGame()) return false;

        SnowConfig cfg = SnowVisualsClient.CONFIG;

        for (Module module : Module.values()) {

            if (cfg.state(module).bind == MIDDLE
                    && (!holdModule(module)
                    || cfg.enabled(module))) {

                return true;
            }
        }

        return false;
    }

    public static boolean held(Module module) {

        if (!inGame()) return false;

        SnowConfig cfg = SnowVisualsClient.CONFIG;

        if (!cfg.enabled(module)) return false;

        int key = cfg.state(module).bind;

        if (isScroll(key)) {

            return latched.contains(module);
        }

        if (key == MIDDLE) {

            return GLFW.glfwGetMouseButton(
                    MinecraftClient.getInstance()
                            .getWindow().getHandle(),
                    GLFW.GLFW_MOUSE_BUTTON_MIDDLE
            ) == GLFW.GLFW_PRESS;
        }

        return SnowVisualsClient.down(key);
    }
}