package pl.snowvisuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import static pl.snowvisuals.SnowModules.Module;

final class SnowChinaHat extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final int SEGMENTS = 48;
    private static final float[] COS = new float[SEGMENTS];
    private static final float[] SIN = new float[SEGMENTS];
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("snowvisuals", "pipeline/china_hat"))
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withCull(false)
                    .withoutBlend()
                    .build());
    private static final RenderLayer LAYER = RenderLayer.of("snowvisuals_china_hat",
            RenderSetup.builder(PIPELINE).expectedBufferSize(16384).build());

    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("snowvisuals", "pipeline/china_hat_translucent"))
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());
    private static final RenderLayer TRANSLUCENT_LAYER = RenderLayer.of("snowvisuals_china_hat_translucent",
            RenderSetup.builder(TRANSLUCENT_PIPELINE).translucent().expectedBufferSize(16384).build());

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            double angle = i * Math.TAU / SEGMENTS;
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    static void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer<?> playerRenderer) {
                helper.register(new SnowChinaHat(playerRenderer));
            }
        });
    }

    private SnowChinaHat(PlayerEntityRenderer<?> renderer) {
        super(renderer);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                       PlayerEntityRenderState state, float headYaw, float headPitch) {
        if (!SnowEffects.enabled(Module.CHINA_HAT) || state.spectator || state.invisible || state.deathTime > 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.getId() != state.id || client.options.getPerspective().isFirstPerson()) return;

        SnowModules.State settings = SnowEffects.state(Module.CHINA_HAT);
        float scale = settings.a / 100f;
        if (settings.opacity <= 0) return;
        int alpha = Math.round(settings.opacity * 2.55f);
        int color = (alpha << 24) | settings.color;
        RenderLayer layer = alpha == 255 ? LAYER : TRANSLUCENT_LAYER;
        PlayerEntityModel model = getContextModel();
        model.setAngles(state);
        matrices.push();
        try {
            model.head.applyTransform(matrices);
            float offset = state.equippedHeadStack.isEmpty() ? -0.55f : -0.65f;
            matrices.translate(0, offset, 0);
            matrices.scale(scale, scale, scale);
            queue.submitCustom(matrices, layer, (entry, vertices) -> draw(entry.getPositionMatrix(), vertices, color));
        } finally {
            matrices.pop();
        }
    }

    private static void draw(Matrix4f pose, VertexConsumer vertices, int rgb) {
        int crown = shade(rgb, 1.0f, 0.10f);
        int brim = shade(rgb, 1.0f, 0.17f);
        int underside = shade(rgb, 0.45f, 0f);
        for (int i = 0; i < SEGMENTS; i++) {
            int j = (i + 1) % SEGMENTS;
            int colorI = shade(rgb, 0.76f + COS[i] * 0.17f, 0f);
            int colorJ = shade(rgb, 0.76f + COS[j] * 0.17f, 0f);
            // The canopy and underside fans use a repeated apex to form a triangle in the quad layer.
            point(vertices, pose, i, 0f, -0.30f, crown);
            point(vertices, pose, i, 0.61f, -0.024f, colorI);
            point(vertices, pose, j, 0.61f, -0.024f, colorJ);
            point(vertices, pose, i, 0f, -0.30f, crown);

            ring(vertices, pose, i, j, 0.61f, -0.024f, 0.68f, 0f, colorI, colorJ, brim);
            ring(vertices, pose, i, j, 0.68f, 0f, 0.67f, 0.035f, brim, brim, underside);

            point(vertices, pose, i, 0f, 0.035f, underside);
            point(vertices, pose, i, 0.67f, 0.035f, underside);
            point(vertices, pose, j, 0.67f, 0.035f, underside);
            point(vertices, pose, i, 0f, 0.035f, underside);
        }
    }

    private static void ring(VertexConsumer vertices, Matrix4f pose, int i, int j,
                             float inner, float innerY, float outer, float outerY,
                             int innerIColor, int innerJColor, int outerColor) {
        point(vertices, pose, i, inner, innerY, innerIColor);
        point(vertices, pose, j, inner, innerY, innerJColor);
        point(vertices, pose, j, outer, outerY, outerColor);
        point(vertices, pose, i, outer, outerY, outerColor);
    }

    private static void point(VertexConsumer vertices, Matrix4f pose, int segment,
                              float radius, float y, int color) {
        vertices.vertex(pose, radius * COS[segment], y, radius * SIN[segment]).color(color);
    }

    private static int shade(int rgb, float light, float highlight) {
        int r = shadeChannel((rgb >>> 16) & 255, light, highlight);
        int g = shadeChannel((rgb >>> 8) & 255, light, highlight);
        int b = shadeChannel(rgb & 255, light, highlight);
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    private static int shadeChannel(int channel, float light, float highlight) {
        float shaded = channel * light;
        return Math.round(shaded + (255 - shaded) * highlight);
    }
}
