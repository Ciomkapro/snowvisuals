package pl.snowvisuals.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pl.snowvisuals.GlowTarget;
import pl.snowvisuals.SnowVisualsClient;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "getAndUpdateRenderState",
            at = @At("RETURN")
    )
    private void snow$color(
            Entity entity,
            float tickProgress,
            CallbackInfoReturnable<EntityRenderState> cir
    ) {

        if (GlowTarget.canGlow(entity)) {

            cir.getReturnValue().outlineColor =
                    0xFF000000
                            | SnowVisualsClient.CONFIG.glowColor;
        }
    }
}