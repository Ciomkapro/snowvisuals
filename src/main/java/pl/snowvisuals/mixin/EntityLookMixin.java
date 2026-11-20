package pl.snowvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.snowvisuals.SnowEffects;

@Mixin(Entity.class)
public abstract class EntityLookMixin {

    @Inject(
            method = "changeLookDirection",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snow$freelook(
            double dx,
            double dy,
            CallbackInfo ci
    ) {

        if ((Object) this
                == MinecraftClient.getInstance().player
                && SnowEffects.rotateFree(dx, dy)) {

            ci.cancel();
        }
    }
}