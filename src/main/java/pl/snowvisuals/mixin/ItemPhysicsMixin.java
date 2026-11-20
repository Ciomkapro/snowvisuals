package pl.snowvisuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.snowvisuals.SnowEffects;
import pl.snowvisuals.SnowItemRenderState;
import pl.snowvisuals.SnowModules.Module;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Unique
    private static final String RENDER_ITEM = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V";

    @Inject(method = "createRenderState()Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;",
            at = @At("RETURN"), cancellable = true)
    private void snow$createState(CallbackInfoReturnable<ItemEntityRenderState> callback) {
        if (SnowEffects.enabled(Module.PHYSICS)
                && callback.getReturnValue().getClass() == ItemEntityRenderState.class) {
            callback.setReturnValue(new SnowItemRenderState());
        }
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("RETURN"))
    private void snow$updateState(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo callback) {
        if (state instanceof SnowItemRenderState physics) physics.updatePhysics(entity);
    }

    @WrapOperation(method = RENDER_ITEM,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"))
    private void snow$lift(MatrixStack matrices, float x, float y, float z, Operation<Void> original,
                           ItemEntityRenderState state) {
        SnowItemRenderState physics = snow$physics(state);
        original.call(matrices, x, physics == null ? y : physics.lift(), z);
    }

    @WrapOperation(method = RENDER_ITEM,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
    private void snow$rotate(MatrixStack matrices, Quaternionfc spin, Operation<Void> original,
                             ItemEntityRenderState state) {
        SnowItemRenderState physics = snow$physics(state);
        original.call(matrices, physics == null ? spin : physics.rotation());
    }

    @Unique
    private static SnowItemRenderState snow$physics(ItemEntityRenderState state) {
        if (!SnowEffects.enabled(Module.PHYSICS)
                || !(state instanceof SnowItemRenderState physics) || !physics.physicsReady()) {
            return null;
        }
        return physics;
    }
}
