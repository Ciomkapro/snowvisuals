package pl.snowvisuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;

import org.lwjgl.glfw.GLFW;

import pl.snowvisuals.gui.SnowScreen;

import java.util.HashSet;
import java.util.Set;

import static pl.snowvisuals.SnowModules.Module;

public final class SnowVisualsClient
        implements ClientModInitializer {

    public static SnowConfig CONFIG;

    private static Set<Integer> previous =
            new HashSet<>();

    @Override
    public void onInitializeClient() {

        CONFIG = SnowConfig.load();

        SnowHud.register();
        SnowWorldEffects.register();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SnowSpotify.shutdown();
            SnowSpotifyLyrics.shutdown();
            SnowSpotifyHud.close();
        });

        ClientTickEvents.END_CLIENT_TICK.register(
                SnowVisualsClient::tick
        );
    }

    public static boolean down(int key) {

        if (!SnowConfig.validKey(key)) {
            return false;
        }

        return GLFW.glfwGetKey(
                MinecraftClient.getInstance()
                        .getWindow().getHandle(),
                key
        ) == GLFW.GLFW_PRESS;
    }

    private static void tick(MinecraftClient mc) {

        SnowBinds.tick(mc);

        Set<Integer> current = new HashSet<>();
        SnowSpotifyKeys.collectPressed(current);
        boolean spotify = CONFIG.enabled(Module.SPOTIFY);
        SnowSpotify.tick(spotify);
        SnowSpotifyHud.tick(spotify);
        SnowSpotifyLyrics.tick(spotify && CONFIG.state(Module.SPOTIFY).d != 0, SnowSpotify.track());

        int guiKey = CONFIG.guiKey;

        if (down(guiKey)) {
            current.add(guiKey);
        }

        for (Module module : Module.values()) {

            int key = CONFIG.state(module).bind;

            if (down(key)) {
                current.add(key);
            }
        }

        if (mc.player != null
                && mc.currentScreen == null) {

            if (current.contains(guiKey)
                    && !previous.contains(guiKey)) {

                mc.setScreen(new SnowScreen());

            } else {

                if (mc.isWindowFocused()) {
                    for (int key : current) {
                        if (!previous.contains(key)) SnowSpotifyKeys.dispatch(key);
                    }
                }
                for (Module module : Module.values()) {

                    if (module == Module.ZOOM
                            || module == Module.FREELOOK) {

                        continue;
                    }

                    int key = CONFIG.state(module).bind;

                    if (key != guiKey
                            && current.contains(key)
                            && !previous.contains(key)) {

                        CONFIG.toggle(module);
                    }
                }
            }
        }

        previous = current;

        SnowQol.tick(mc);
        SnowHud.tick(mc);
        SnowEffects.tick(mc);
    }

    public static void consumeBind() {

        previous.add(CONFIG.guiKey);
        SnowSpotifyKeys.collectPressed(previous);

        for (Module module : Module.values()) {

            int key = CONFIG.state(module).bind;

            if (down(key)) {
                previous.add(key);
            }
        }
    }
}