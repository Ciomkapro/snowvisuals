package pl.snowvisuals.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;

import org.lwjgl.glfw.GLFW;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.snowvisuals.SnowBinds;
import pl.snowvisuals.SnowEffects;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(
            method = "onMouseButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snow$click(
            long window,
            MouseInput input,
            int action,
            CallbackInfo ci
    ) {

        if (action == GLFW.GLFW_PRESS) {

            SnowEffects.click(input.button());
        }

        if (input.button()
                == GLFW.GLFW_MOUSE_BUTTON_MIDDLE
                && SnowBinds.middleBound()) {

            if (action == GLFW.GLFW_PRESS) {

                SnowBinds.dispatch(
                        SnowBinds.MIDDLE
                );
            }

            ci.cancel();
        }
    }

    @Inject(
            method = "onMouseScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snow$scroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo ci
    ) {

        if (vertical == 0) {
            return;
        }

        int key = vertical > 0
                ? SnowBinds.UP
                : SnowBinds.DOWN;

        if (SnowBinds.dispatch(key)) {

            ci.cancel();
        }
    }
}