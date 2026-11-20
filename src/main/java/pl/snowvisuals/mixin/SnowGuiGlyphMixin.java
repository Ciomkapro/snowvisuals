package pl.snowvisuals.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.font.TextDrawable;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.render.state.GlyphGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.snowvisuals.gui.SnowText;

@Mixin(GlyphGuiElementRenderState.class)
public abstract class SnowGuiGlyphMixin {
    @Shadow
    public abstract TextDrawable renderable();

    @Inject(method = "pipeline", at = @At("RETURN"), cancellable = true)
    private void snow$useUiTextPipeline(CallbackInfoReturnable<RenderPipeline> callback) {
        if (callback.getReturnValue() == RenderPipelines.GUI_TEXT_INTENSITY
                && SnowText.isAtlas(renderable().textureView())) {
            callback.setReturnValue(SnowText.GUI_GLYPHS);
        }
    }

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void snow$sampleUiGlyphs(CallbackInfoReturnable<TextureSetup> callback) {
        var texture = renderable().textureView();
        if (SnowText.isAtlas(texture)) {
            var sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            callback.setReturnValue(TextureSetup.withLightmap(texture, sampler));
        }
    }
}