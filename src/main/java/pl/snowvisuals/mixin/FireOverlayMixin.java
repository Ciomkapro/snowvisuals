package pl.snowvisuals.mixin;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.snowvisuals.SnowEffects;
import pl.snowvisuals.SnowModules.Module;

@Mixin(InGameOverlayRenderer.class)
public abstract class FireOverlayMixin {

    @Inject(
            method = "renderFireOverlay",
            at = @At("HEAD")
    )
    private static void snow$fireStart(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Sprite sprite,
            CallbackInfo ci
    ) {

        if (SnowEffects.enabled(Module.FIRE)) {

            matrices.push();

            float amount =
                    SnowEffects.state(Module.FIRE).a / 100f;

            matrices.translate(
                    0,
                    -0.45f * amount,
                    0
            );
        }
    }

    @Inject(
            method = "renderFireOverlay",
            at = @At("RETURN")
    )
    private static void snow$fireEnd(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Sprite sprite,
            CallbackInfo ci
    ) {

        if (SnowEffects.enabled(Module.FIRE)) {

            matrices.pop();
        }
    }
}