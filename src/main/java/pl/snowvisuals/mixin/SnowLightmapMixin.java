package pl.snowvisuals.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.snowvisuals.SnowEffects;

import static pl.snowvisuals.SnowModules.Module;

@Mixin(LightmapTextureManager.class)
public abstract class SnowLightmapMixin {
    // The fourth LightmapInfo uniform is the shader's night-vision blend.
    @ModifyArg(
            method = "update",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3, remap = false),
            index = 0
    )
    private float snow$lightmapBrightness(float vanilla) {
        if (!SnowEffects.enabled(Module.FULLBRIGHT)) return vanilla;
        return Math.max(vanilla, SnowEffects.state(Module.FULLBRIGHT).a / 100f);
    }
}
