package pl.snowvisuals.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public final class SnowText {
    private static final Identifier FONT_ID = Identifier.of("snowvisuals", "ui");
    private static final String ATLAS_PREFIX = FONT_ID + "/";
    private static final Style UI_STYLE = Style.EMPTY.withFont(new StyleSpriteSource.Font(FONT_ID));
    public static final RenderPipeline GUI_GLYPHS = RenderPipeline.builder(
                    RenderPipelines.GUI_TEXT_SNIPPET, RenderPipelines.FOG_SNIPPET)
            .withLocation(Identifier.of("snowvisuals", "pipeline/ui_glyphs"))
            .withVertexShader("core/rendertype_text_intensity")
            .withFragmentShader(Identifier.of("snowvisuals", "core/snow_gui_text"))
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .build();
    private static final float RENDER_SCALE = 0.5f;
    private static final float BASELINE = 7f;
    private static final Set<GpuTextureView> ATLASES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private SnowText() {}

    public static Text styled(String value) {
        return Text.literal(value == null ? "" : value).setStyle(UI_STYLE);
    }

    public static float width(TextRenderer renderer, String value) {
        return renderer.getTextHandler().getWidth(styled(value).asOrderedText()) * RENDER_SCALE;
    }

    public static void draw(DrawContext context, TextRenderer renderer, String value,
                            float x, float y, float size, int color) {
        if ((color >>> 24) == 0 || value == null || value.isEmpty()) return;

        // Keep Minecraft's seven-unit baseline at its original visual position.
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        try {
            matrices.translate(x, y + BASELINE * (1f - RENDER_SCALE) * size);
            matrices.scale(RENDER_SCALE * size, RENDER_SCALE * size);
            context.drawText(renderer, styled(value), 0, 0, color, false);
        } finally {
            matrices.popMatrix();
        }
    }

    public static void trackAtlas(String name, GpuTextureView texture) {
        if (name != null && name.startsWith(ATLAS_PREFIX)) ATLASES.add(texture);
    }

    public static boolean isAtlas(GpuTextureView texture) {
        return texture != null && ATLASES.contains(texture);
    }
}
