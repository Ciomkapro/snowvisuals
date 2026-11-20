package pl.snowvisuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pl.snowvisuals.SnowEffects;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getYaw(F)F"), require = 2)
    private float snow$cameraYaw(Entity entity, float tickProgress, Operation<Float> original) {
        return SnowEffects.cameraYaw(entity, original.call(entity, tickProgress));
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getPitch(F)F"), require = 2)
    private float snow$cameraPitch(Entity entity, float tickProgress, Operation<Float> original) {
        return SnowEffects.cameraPitch(entity, original.call(entity, tickProgress));
    }
}
