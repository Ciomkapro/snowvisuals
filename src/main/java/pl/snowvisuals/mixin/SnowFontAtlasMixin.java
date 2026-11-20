package pl.snowvisuals.mixin;

import net.minecraft.client.font.GlyphAtlasTexture;
import net.minecraft.client.font.TextRenderLayerSet;
import net.minecraft.client.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.snowvisuals.gui.SnowText;

import java.util.function.Supplier;

@Mixin(GlyphAtlasTexture.class)
public abstract class SnowFontAtlasMixin extends AbstractTexture {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void snow$trackAtlas(Supplier<String> name, TextRenderLayerSet layers,
                                 boolean hasColor, CallbackInfo callback) {
        SnowText.trackAtlas(name == null ? null : name.get(), getGlTextureView());
    }
}
