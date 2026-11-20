package pl.snowvisuals.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import pl.snowvisuals.SnowEffects;
import pl.snowvisuals.SnowModules.Module;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class CapeMixin {

    @Unique
    private static final Identifier SNOW_CAPE =
            Identifier.of(
                    "snowvisuals",
                    "textures/cape.png"
            );

    @Unique
    private static final AssetInfo.TextureAssetInfo
            SNOW_CAPE_ASSET =
            new AssetInfo.TextureAssetInfo(
                    Identifier.of("snowvisuals", "cape"),
                    SNOW_CAPE
            );

    @Inject(
            method = "getSkin",
            at = @At("RETURN"),
            cancellable = true
    )
    private void snow$cape(
            CallbackInfoReturnable<SkinTextures> cir
    ) {

        MinecraftClient mc =
                MinecraftClient.getInstance();

        if ((Object) this != mc.player) {
            return;
        }

        if (!SnowEffects.enabled(Module.CAPE)) {
            return;
        }

        SkinTextures original =
                cir.getReturnValue();

        if (original == null) {
            return;
        }

        cir.setReturnValue(
                new SkinTextures(
                        original.body(),
                        SNOW_CAPE_ASSET,
                        original.elytra(),
                        original.model(),
                        original.secure()
                )
        );
    }
}