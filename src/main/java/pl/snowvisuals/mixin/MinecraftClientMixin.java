package pl.snowvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pl.snowvisuals.GlowTarget;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(
            method = "hasOutline",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snow$outline(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {

        if (GlowTarget.canGlow(entity)) {
            cir.setReturnValue(true);
        }
    }
}