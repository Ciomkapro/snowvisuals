package pl.snowvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pl.snowvisuals.SnowConfig;
import pl.snowvisuals.SnowEffects;
import pl.snowvisuals.SnowModules.Module;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "updateCamera", at = @At("HEAD"))
    private void snow$updateCamera(RenderTickCounter tickCounter, CallbackInfo ci) {
        SnowEffects.updateCamera(MinecraftClient.getInstance());
    }

    // FOV AND ZOOM

    @Inject(
            method = "getFov",
            at = @At("RETURN"),
            cancellable = true
    )
    private void snow$fov(
            Camera camera,
            float tickProgress,
            boolean changingFov,
            CallbackInfoReturnable<Float> cir
    ) {

        if (changingFov) {

            cir.setReturnValue(
                    SnowEffects.fov(
                            cir.getReturnValue()
                    )
            );
        }
    }

    // NO HURT CAM

    @Inject(
            method = "tiltViewWhenHurt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snow$hurt(
            MatrixStack matrices,
            float tickProgress,
            CallbackInfo ci
    ) {

        if (SnowEffects.enabled(Module.NOHURT)) {

            ci.cancel();
        }
    }

    // ASPECT RATIO

    @Inject(
            method = "getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;",
            at = @At("RETURN")
    )
    private void snow$aspect(
            float fov,
            CallbackInfoReturnable<Matrix4f> cir
    ) {

        MinecraftClient mc =
                MinecraftClient.getInstance();

        if (mc.world == null
                || !SnowEffects.enabled(Module.ASPECT)) {

            return;
        }

        float aspect = SnowConfig.clamp(
                SnowEffects.state(Module.ASPECT).a,
                75,
                300
        ) / 100f;

        Matrix4f matrix = cir.getReturnValue();

        matrix.m00(
                matrix.m11() / aspect
        );
    }
}