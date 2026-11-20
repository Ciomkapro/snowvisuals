package pl.snowvisuals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.StickyKeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.snowvisuals.SnowEffects;

import java.util.function.BooleanSupplier;

import static pl.snowvisuals.SnowModules.Module;

@Mixin(StickyKeyBinding.class)
public abstract class SnowMovementMixin {
    @Shadow private boolean resetOnScreenClose;

    @WrapOperation(
            method = {"setPressed", "reset", "shouldSetOnGameFocus"},
            at = @At(value = "INVOKE", target = "Ljava/util/function/BooleanSupplier;getAsBoolean()Z", remap = false),
            require = 3
    )
    private boolean snow$useToggle(BooleanSupplier supplier, Operation<Boolean> original) {
        Module module = snow$movementModule();
        return (module != null && SnowEffects.enabled(module)) || original.call(supplier);
    }

    @Inject(method = "setPressed", at = @At("TAIL"))
    private void snow$stopSprintOnToggleOff(boolean pressed, CallbackInfo ci) {
        if (!pressed || snow$movementModule() != Module.TOGGLE_SPRINT
                || !SnowEffects.enabled(Module.TOGGLE_SPRINT)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && !mc.options.sprintKey.isPressed()) mc.player.setSprinting(false);
    }

    @Inject(method = "shouldRestoreOnScreenClose", at = @At("HEAD"), cancellable = true)
    private void snow$restoreToggle(CallbackInfoReturnable<Boolean> cir) {
        Module module = snow$movementModule();
        if (module == null || !SnowEffects.enabled(module)) return;
        boolean restore = resetOnScreenClose && SnowEffects.state(module).a == 0;
        resetOnScreenClose = false;
        cir.setReturnValue(restore);
    }

    @Unique
    private Module snow$movementModule() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return null;
        Object key = this;
        if (key == mc.options.sprintKey) return Module.TOGGLE_SPRINT;
        if (key == mc.options.sneakKey) return Module.TOGGLE_SNEAK;
        return null;
    }
}
