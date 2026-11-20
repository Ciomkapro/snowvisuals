package pl.snowvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import pl.snowvisuals.gui.SnowText;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Locale;
import static pl.snowvisuals.SnowModules.Module;

public final class SnowEffects {
    private static Object connection, world;
    private static long started;
    private static Vec3d lastPosition;
    private static double speed;
    private static float zoom = 1f;
    private static long zoomTime;
    private static boolean chatChanged;
    private static double oldChatScale, oldChatOpacity;
    private static float freeYaw;
    private static float freePitch;
    private static boolean returnToFirstPerson;
    private static boolean freeStarted;
    private SnowEffects() {}

    public static boolean enabled(Module module) {
        return SnowVisualsClient.CONFIG != null && SnowVisualsClient.CONFIG.enabled(module);
    }
    public static SnowModules.State state(Module module) { return SnowVisualsClient.CONFIG.state(module); }
    public static boolean held(Module module) { return SnowBinds.held(module); }

    public static Text font(String value) {
        return SnowText.styled(value);
    }

    public static float width(TextRenderer renderer, String value) {
        return SnowText.width(renderer, value);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value,
                            float x, float y, int color) {
        text(context, renderer, value, x, y, 1f, color);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value,
                            float x, float y, float size, int color) {
        SnowText.draw(context, renderer, value, x, y, size, color);
    }

    public static void click(int button) {
        SnowQol.click(button);
    }

    public static int cps(boolean right) {
        return SnowQol.cps(right, 1000);
    }

    public static void tick(MinecraftClient mc) {
        updateChat(mc);
        SnowWorldEffects.tick(mc);
        if (mc.player == null || mc.world == null) {
            connection = null; world = null; lastPosition = null;
            speed = 0; started = 0; zoom = 1f; zoomTime = 0;
            stopFreelook(mc);
            return;
        }
        if (connection != mc.getNetworkHandler() || started == 0) {
            connection = mc.getNetworkHandler(); started = System.nanoTime();
        }
        if (world != mc.world) { world = mc.world; lastPosition = null; speed = 0; }
        Vec3d pos = mc.player.getEntityPos();
        if (lastPosition != null) {
            double dx = pos.x - lastPosition.x, dz = pos.z - lastPosition.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            speed = distance > 10 ? 0 : distance * 20;
        }
        lastPosition = pos;
        updateFreelook(mc);
    }
    public static String speedText() { return String.format(Locale.ROOT, "%.2f m/s", speed); }
    public static String playtime() {
        if (started == 0) return "00:00:00";
        long seconds = Math.max(0, (System.nanoTime() - started) / 1_000_000_000L);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private static void updateChat(MinecraftClient mc) {
        boolean active = enabled(Module.CHAT);
        if (active && !chatChanged) {
            oldChatScale = mc.options.getChatScale().getValue();
            oldChatOpacity = mc.options.getChatOpacity().getValue();
            chatChanged = true;
        }
        if (active) {
            double scale = SnowConfig.clamp(state(Module.CHAT).a, 50, 100) / 100.0;
            double opacity = SnowConfig.clamp(state(Module.CHAT).b, 0, 100) / 100.0;
            if (mc.options.getChatScale().getValue() != scale) mc.options.getChatScale().setValue(scale);
            if (mc.options.getChatOpacity().getValue() != opacity) mc.options.getChatOpacity().setValue(opacity);
        } else if (chatChanged) {
            mc.options.getChatScale().setValue(oldChatScale);
            mc.options.getChatOpacity().setValue(oldChatOpacity);
            chatChanged = false;
        }
    }
    public static void hit(Entity target) {
        SnowWorldEffects.hit(MinecraftClient.getInstance(), target);
    }

    public static void updateCamera(MinecraftClient client) {
        updateFreelook(client);

        float target = client.isWindowFocused() && held(Module.ZOOM)
                ? SnowConfig.clamp(state(Module.ZOOM).a, 10, 90) / 100f
                : 1f;
        long now = System.nanoTime();
        float elapsed = zoomTime == 0L ? 0.016f : Math.min(0.1f, (now - zoomTime) / 1_000_000_000f);
        zoomTime = now;
        zoom += (target - zoom) * (1f - (float) Math.exp(-elapsed * 22f));
        if (Math.abs(zoom - target) < 0.001f) zoom = target;
    }

    public static float fov(float original) {
        float base = original;
        if (enabled(Module.FOV)) {
            int requested = SnowConfig.clamp(state(Module.FOV).a, 30, 200);
            base = requested <= 150 ? requested : 150f + (requested - 150) * 0.5f;
        }
        return MathHelper.clamp(base * zoom, 5f, 175f);
    }

    private static void updateFreelook(MinecraftClient client) {
        if (!client.isWindowFocused() || !held(Module.FREELOOK)
                || client.getCameraEntity() != client.player || client.player.isSleeping()) {
            stopFreelook(client);
            return;
        }
        if (freeStarted) return;

        freeYaw = client.player.getYaw();
        freePitch = client.player.getPitch();
        freeStarted = true;
        returnToFirstPerson = client.options.getPerspective().isFirstPerson();
        if (returnToFirstPerson) setPerspective(client, Perspective.THIRD_PERSON_BACK);
    }

    private static void stopFreelook(MinecraftClient client) {
        if (returnToFirstPerson && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK) {
            setPerspective(client, Perspective.FIRST_PERSON);
        }
        freeStarted = false;
        returnToFirstPerson = false;
    }

    private static void setPerspective(MinecraftClient client, Perspective perspective) {
        client.options.setPerspective(perspective);
        if (client.world != null) {
            client.gameRenderer.onCameraEntitySet(client.getCameraEntity());
            client.worldRenderer.scheduleTerrainUpdate();
        }
    }

    public static float cameraYaw(Entity entity, float original) {
        return freeStarted && entity == MinecraftClient.getInstance().player ? freeYaw : original;
    }

    public static float cameraPitch(Entity entity, float original) {
        return freeStarted && entity == MinecraftClient.getInstance().player ? freePitch : original;
    }

    public static boolean rotateFree(double dx, double dy) {
        updateFreelook(MinecraftClient.getInstance());
        if (!freeStarted) return false;

        freeYaw = MathHelper.wrapDegrees(freeYaw + (float) (dx * 0.15));
        freePitch = MathHelper.clamp(freePitch + (float) (dy * 0.15), -90f, 90f);
        return true;
    }
}
