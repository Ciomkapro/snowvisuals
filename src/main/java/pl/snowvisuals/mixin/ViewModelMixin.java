package pl.snowvisuals.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.snowvisuals.SnowEffects;
import pl.snowvisuals.SnowModules.Module;

@Mixin(HeldItemRenderer.class)
public abstract class ViewModelMixin {

    @Inject(
            method = "renderFirstPersonItem",
            at = @At("HEAD")
    )
    private void snow$viewModelStart(
            AbstractClientPlayerEntity player,
            float tickProgress,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {

        if (!SnowEffects.enabled(Module.VIEWMODEL)) {
            return;
        }

        var s = SnowEffects.state(Module.VIEWMODEL);

        matrices.push();

        matrices.translate(
                s.b / 100f,
                s.c / 100f,
                0
        );

        float scale = s.a / 100f;

        matrices.scale(scale, scale, scale);

        matrices.multiply(
                RotationAxis.POSITIVE_Z.rotationDegrees(s.d)
        );
    }

    @Inject(
            method = "renderFirstPersonItem",
            at = @At("RETURN")
    )
    private void snow$viewModelEnd(
            AbstractClientPlayerEntity player,
            float tickProgress,
            float pitch,
            Hand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light,
            CallbackInfo ci
    ) {

        if (SnowEffects.enabled(Module.VIEWMODEL)) {

            matrices.pop();
        }
    }
}