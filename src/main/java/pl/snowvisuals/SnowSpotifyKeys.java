package pl.snowvisuals;

import java.util.Set;

import static pl.snowvisuals.SnowModules.Module;
import static pl.snowvisuals.SnowSpotify.Action;

public final class SnowSpotifyKeys {
    private static final Action[] ACTIONS = Action.values();

    private SnowSpotifyKeys() {}

    public static int get(Action action) {
        return key(SnowVisualsClient.CONFIG.state(Module.SPOTIFY), action);
    }

    private static int key(SnowModules.State state, Action action) {
        return switch (action) {
            case TOGGLE -> state.a;
            case PREVIOUS -> state.b;
            case NEXT -> state.c;
        };
    }

    private static void set(SnowModules.State state, Action action, int key) {
        switch (action) {
            case TOGGLE -> state.a = key;
            case PREVIOUS -> state.b = key;
            case NEXT -> state.c = key;
        }
    }

    public static boolean assign(Action action, int key) {
        SnowConfig config = SnowVisualsClient.CONFIG;
        if (config == null || !SnowBinds.valid(key) || key == config.guiKey) return false;

        clear(config, key);
        if (key != -1) {
            for (Module module : Module.values()) {
                if (config.state(module).bind == key) SnowBinds.clearModuleBind(module);
            }
        }
        set(config.state(Module.SPOTIFY), action, key);
        config.save();
        SnowVisualsClient.consumeBind();
        return true;
    }

    public static void clear(SnowConfig config, int key) {
        if (key == -1) return;
        var state = config.state(Module.SPOTIFY);
        for (Action action : ACTIONS) {
            if (key(state, action) == key) set(state, action, -1);
        }
    }

    static void collectPressed(Set<Integer> pressed) {
        for (Action action : ACTIONS) {
            int key = get(action);
            if (SnowVisualsClient.down(key)) pressed.add(key);
        }
    }

    static boolean isBound(int key) {
        return boundAction(key) != null;
    }

    static boolean dispatch(int key) {
        Action action = boundAction(key);
        return action != null && SnowSpotify.control(action);
    }

    private static Action boundAction(int key) {
        SnowConfig config = SnowVisualsClient.CONFIG;
        if (key == -1 || key == config.guiKey || !config.enabled(Module.SPOTIFY)) return null;
        for (Action action : ACTIONS) {
            if (get(action) == key) return action;
        }
        return null;
    }
}