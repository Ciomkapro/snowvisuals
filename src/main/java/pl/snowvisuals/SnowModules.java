package pl.snowvisuals;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SnowModules {

    public enum Type {
        HUD, VISUAL, CAMERA, OTHER
    }

    public enum Module {

        COORDS("Show Coords", Type.HUD),
        FPS("Show FPS", Type.HUD),
        SERVER("Show Server IP", Type.HUD),
        PING("Ping Display", Type.HUD),
        CPS("CPS Counter", Type.HUD),
        KEYS("Keystrokes", Type.HUD),
        ARMOR("Armor HUD", Type.HUD),
        POTIONS("Potion HUD", Type.HUD),
        PLAYTIME("Playtime", Type.HUD),
        SPEED("Movement Speed", Type.HUD),

        GLOW("Player Glow", Type.VISUAL),
        CROSSHAIR("Custom Crosshair", Type.VISUAL),
        FIRE("Low Fire", Type.VISUAL),
        PARTICLES("Hit Particles", Type.VISUAL),
        TRAILS("Player Trails", Type.VISUAL),
        PHYSICS("Item Physics", Type.VISUAL),
        CAPE("Custom Cape", Type.VISUAL),

        VIEWMODEL("ViewModel", Type.CAMERA),
        ZOOM("Custom Zoom", Type.CAMERA),
        FREELOOK("Freelook", Type.CAMERA),
        FOV("Custom FOV", Type.CAMERA),
        NOHURT("No Hurt Cam", Type.CAMERA),

        CHAT("Custom Chat", Type.OTHER),
        ASPECT("Aspect Ratio", Type.CAMERA),

        TOGGLE_SPRINT("Toggle Sprint", Type.OTHER),
        TOGGLE_SNEAK("Toggle Sneak", Type.OTHER),
        BETTER_CPS("Better CPS HUD", Type.HUD),
        COMBO("Combo Counter", Type.HUD),
        HIT_MARKER("Hit Marker", Type.HUD),
        DAMAGE_TINT("Damage Tint", Type.VISUAL),
        PEARL_COOLDOWN("Pearl Cooldown HUD", Type.HUD),
        TOTEM_COUNT("Totem Counter", Type.HUD),
        ARROW_COUNT("Arrow Counter", Type.HUD),
        BOW_CHARGE("Bow Charge Indicator", Type.HUD),
        TARGET_HUD("Target HUD", Type.HUD),
        PING_GRAPH("Ping Graph", Type.HUD),
        DIRECTION("Direction / Yaw HUD", Type.HUD),
        FULLBRIGHT("Fullbright", Type.VISUAL),
        ITEM_COUNT("Item Count HUD", Type.HUD),
        CHINA_HAT("China Hat", Type.VISUAL),
        SPOTIFY("Spotify Listener", Type.HUD);

        public final String name;
        public final Type type;

        Module(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    public static final class State {

        public boolean enabled = false;

        public int x = 8;
        public int y = 8;

        public int scale = 100;
        public int opacity = 85;
        public boolean centered;

        public int bind = -1;
        public int color = 0xFFFFFF;
        public int color2 = 0xFF557A;

        public int a = 100;
        public int b = 0;
        public int c = 0;
        public int d = 0;
        public int markerRange;
        public int markerSpeed = 100;
        public int markerSize = 100;

        public boolean percent = true;
    }

    public Map<String, State> states =
            new LinkedHashMap<>();

    public State get(Module module) {

        if (states == null) {
            states = new LinkedHashMap<>();
        }

        return states.computeIfAbsent(
                module.name(),
                key -> defaults(module)
        );
    }

    private State defaults(Module module) {

        State s = new State();

        int i = module.ordinal();

        s.x = i < 5 ? 8 : 180;
        s.y = 8 + (i % 5) * 28;

        switch (module) {

            case COORDS, FPS, SERVER ->
                    s.enabled = true;

            case CROSSHAIR -> {
                s.a = 5;
                s.b = 3;
                s.c = 1;
            }

            case FIRE -> s.a = 65;
            case PARTICLES -> {
                s.a = 12;
                s.color = 0x91DBFF;
            }
            case TRAILS -> {
                s.a = 1000;
                s.b = 14;
                s.c = 105;
                s.color = 0x8ACBFF;
            }

            case ZOOM -> {
                s.a = 30;
                s.bind = 90;
                s.enabled = true;
            }

            case FREELOOK -> {
                s.bind = 342;
                s.enabled = true;
            }

            case FOV -> s.a = 100;

            case ASPECT -> s.a = 133;

            case CHAT -> {
                s.a = 100;
                s.b = 85;
            }

            case TOGGLE_SPRINT, TOGGLE_SNEAK -> s.a = 0;
            case TOTEM_COUNT -> {
                position(s, 180, 264);
                s.a = 0;
            }
            case BETTER_CPS -> {
                position(s, 8, 152);
                s.a = 1000;
                s.c = 1;
            }
            case COMBO -> {
                position(s, 180, 152);
                s.a = 1500;
                s.b = 1;
            }
            case HIT_MARKER -> {
                s.centered = true;
                s.a = 250;
                s.b = 9;
            }
            case DAMAGE_TINT -> {
                s.a = 35;
                s.b = 350;
                s.color = 0xFF536A;
            }
            case PEARL_COOLDOWN -> {
                position(s, 8, 180);
                s.a = 1;
                s.b = 1;
            }
            case ARROW_COUNT -> {
                position(s, 180, 208);
                s.a = 1;
            }
            case BOW_CHARGE -> {
                position(s, 8, 208);
                s.a = 0;
                s.b = 1;
            }
            case TARGET_HUD -> {
                position(s, 180, 180);
                s.d = 1;
                s.markerRange = 5;
                s.color = 0x9DD7FF;
                s.a = 3000;
                s.b = 1;
            }
            case PING_GRAPH -> {
                position(s, 8, 236);
                s.a = 30;
                s.b = 1;
            }
            case DIRECTION -> {
                position(s, 180, 236);
                s.a = 1;
                s.b = 1;
            }
            case FULLBRIGHT -> s.a = 100;
            case CHINA_HAT -> {
                s.color = 0xA7D8FF;
                s.opacity = 100;
            }
            case SPOTIFY -> {
                position(s, 8, 152);
                s.a = -1;
                s.b = -1;
                s.c = -1;
            }
            case ITEM_COUNT -> {
                position(s, 8, 278);
                s.a = 1 | 2 | 8 | 16;
            }
            default -> {}
        }

        return s;
    }

    private static void position(State state, int x, int y) {
        state.x = x;
        state.y = y;
    }

    public void resetPositions() {
        for (Module module : Module.values()) {
            if (module.type != Type.HUD) continue;

            State state = get(module);
            State defaults = defaults(module);

            state.x = defaults.x;
            state.y = defaults.y;
            state.centered = defaults.centered;

            state.scale = 100;
            state.opacity = 85;
        }
    }
}