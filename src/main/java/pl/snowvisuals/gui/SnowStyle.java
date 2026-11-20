package pl.snowvisuals.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public final class SnowStyle {

    private SnowStyle() {}

    private static final int RES = 256;
    private static final int SHAPE_PADDING = 4;
    private static final float STYLE_PRECISION = 128f;
    private static final Identifier SHAPE_SHADER = Identifier.of("snowvisuals", "core/snow_gui_shape");

    private static final RenderPipeline SHAPE_PIPELINE = texturedPipeline("snow_gui_shape")
            .withVertexShader(SHAPE_SHADER)
            .withFragmentShader(SHAPE_SHADER)
            .build();
    private static final RenderPipeline SURFACE_PIPELINE = geometryPipeline("snow_gui_surface")
            .withVertexShader(SHAPE_SHADER)
            .withFragmentShader(Identifier.of("snowvisuals", "core/snow_gui_surface"))
            .build();
    private static final RenderPipeline COLOR_FIELD_PIPELINE = texturedPipeline("snow_color_field")
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.of("snowvisuals", "core/snow_color_field"))
            .build();

    private static final Identifier LOGO =
            Identifier.of("snowvisuals", "textures/logo.png");

    private static final Identifier ROUND = texture("round");
    private static final Identifier STROKE = texture("stroke");

    private static final Identifier CROSS = texture("cross");
    private static final Identifier BARS = texture("bars");
    private static final Identifier SEARCH = texture("search");
    private static final Identifier GEAR = texture("gear");

    private static Identifier texture(String name) {
        return Identifier.of(
                "snowvisuals",
                "textures/hd/" + name + ".png"
        );
    }

    private static RenderPipeline.Builder texturedPipeline(String name) {
        return geometryPipeline(name).withSampler("Sampler0");
    }

    private static RenderPipeline.Builder geometryPipeline(String name) {
        return RenderPipeline.builder()
                .withLocation(Identifier.of("snowvisuals", "pipeline/" + name))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(false);
    }

    // OPACITY

    public static int alpha(int color, int percent) {

        int a = Math.round(
                255 * Math.max(0, Math.min(100, percent))
                        / 100f
        );

        return (color & 0xFFFFFF) | (a << 24);
    }

    public static int opacity(int color, float amount) {
        int alpha = Math.round((color >>> 24) * Math.max(0f, Math.min(1f, amount)));
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    public static float frameSeconds(long now, long previous) {
        if (previous == 0L) return 0f;
        return Math.max(0f, Math.min(0.1f, (now - previous) / 1_000_000_000f));
    }

    public static float approach(float current, float target, float rate, float seconds) {
        float next = target + (current - target) * (float) Math.exp(-rate * seconds);
        return Math.abs(next - target) < 0.002f ? target : next;
    }

    // COLOR ANIMATION

    public static int blend(int a, int b, float t) {

        t = Math.max(0f, Math.min(1f, t));

        int result = 0;

        for (int shift = 0; shift <= 24; shift += 8) {

            int value = Math.round(
                    ((a >>> shift) & 255) * (1 - t)
                            + ((b >>> shift) & 255) * t
            );

            result |= value << shift;
        }

        return result;
    }

    public static int contrast(int color) {

        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;

        double light = r * .299 + g * .587 + b * .114;

        return light > 165
                ? 0xFF15151C
                : 0xFFFFFFFF;
    }

    // MOUSE

    public static boolean inside(
            double mx, double my,
            double x1, double y1,
            double x2, double y2
    ) {

        return mx >= x1 && mx < x2
                && my >= y1 && my < y2;
    }

    // HD TEXTURE RENDERER

    private static void image(
            DrawContext c,
            Identifier texture,
            int x, int y,
            int width, int height,
            int color
    ) {

        if (width <= 0 || height <= 0
                || (color >>> 24) == 0) {
            return;
        }

        c.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x, y,
                0, 0,
                width, height,
                RES, RES,
                RES, RES,
                color
        );
    }

    public static void round(DrawContext context, int x1, int y1, int x2, int y2, int radius, int color) {
        roundedShape(context, x1, y1, x2 - x1, y2 - y1, radius, 0, color);
    }

    public static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        frame(context, x, y, width, height, 4, color);
    }

    public static void frame(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        roundedShape(context, x, y, width, height, radius, 1, color);
    }

    private static void roundedShape(DrawContext context, int x, int y, int width, int height,
                                     int radius, int stroke, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) return;
        int r = Math.max(0, Math.min(63, Math.min(radius, Math.min(width, height) / 2)));
        int paddedWidth = width + 2 * SHAPE_PADDING;
        int paddedHeight = height + 2 * SHAPE_PADDING;

        // Integer UVs carry twice the logical vertex position. Fractional parts carry radius/stroke.
        context.drawTexture(SHAPE_PIPELINE, ROUND, x - SHAPE_PADDING, y - SHAPE_PADDING,
                -paddedWidth + r / STYLE_PRECISION, -paddedHeight + stroke / STYLE_PRECISION,
                paddedWidth, paddedHeight, 2 * paddedWidth, 2 * paddedHeight, 1, 1, color);
    }

    /**
     * A rounded surface with a vertical ARGB gradient. The corners are softened in
     * screen pixels, so small GUI scales retain the same apparent edge quality.
     */
    public static void surface(DrawContext context, int x1, int y1, int x2, int y2,
                               int radius, int top, int bottom) {
        gradedShape(context, x1, y1, x2, y2, radius, 0, top, bottom);
    }

    /**
     * A lightly graded panel. The highlight tints the upper face and the top of the
     * edge while the base and edge colours retain their own opacity.
     */
    public static void glass(DrawContext context, int x1, int y1, int x2, int y2,
                             int radius, int base, int edge, int highlight) {
        int lit = tint(base, highlight, 0.24f);
        int shaded = tint(base, 0xFF080D17, 0.16f);
        surface(context, x1, y1, x2, y2, radius, lit, shaded);
        gradedShape(context, x1, y1, x2, y2, radius, 1,
                tint(edge, highlight, 0.42f), opacity(edge, 0.60f));
    }

    private static int tint(int base, int tint, float strength) {
        float amount = strength * (tint >>> 24) / 255f;
        return (base & 0xFF000000) | (blend(base, tint, amount) & 0xFFFFFF);
    }

    private static void gradedShape(DrawContext context, int x1, int y1, int x2, int y2,
                                    int radius, int stroke, int top, int bottom) {
        int width = x2 - x1;
        int height = y2 - y1;
        if (width <= 0 || height <= 0 || ((top | bottom) >>> 24) == 0) return;
        int r = Math.max(0, Math.min(63, Math.min(radius, Math.min(width, height) / 2)));
        int left = x1 - SHAPE_PADDING;
        int upper = y1 - SHAPE_PADDING;
        int right = x2 + SHAPE_PADDING;
        int lower = y2 + SHAPE_PADDING;

        var pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = context.scissorStack.peekLast();
        ScreenRect bounds = new ScreenRect(left, upper, right - left, lower - upper).transformEachVertex(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;
        context.state.addSimpleElement(new SurfaceQuad(pose, left, upper, right, lower,
                r, stroke, top, bottom, scissor, bounds));
    }

    private record SurfaceQuad(Matrix3x2f pose, int x1, int y1, int x2, int y2,
                               int radius, int stroke, int top, int bottom,
                               ScreenRect scissorArea, ScreenRect bounds) implements SimpleGuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return SURFACE_PIPELINE;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.empty();
        }

        @Override
        public void setupVertices(VertexConsumer vertices) {
            float radiusBits = radius / STYLE_PRECISION;
            float strokeBits = stroke / STYLE_PRECISION;
            float u1 = x1 - x2 + radiusBits;
            float u2 = x2 - x1 + radiusBits;
            float v1 = y1 - y2 + strokeBits;
            float v2 = y2 - y1 + strokeBits;
            vertices.vertex(pose, x1, y1).texture(u1, v1).color(top);
            vertices.vertex(pose, x1, y2).texture(u1, v2).color(bottom);
            vertices.vertex(pose, x2, y2).texture(u2, v2).color(bottom);
            vertices.vertex(pose, x2, y1).texture(u2, v1).color(top);
        }
    }

    public static void colorField(DrawContext context, int x, int y, int width, int height,
                                  int hue, boolean spectrum) {
        context.drawTexture(COLOR_FIELD_PIPELINE, ROUND, x, y, 0f, spectrum ? 2f : 0f,
                width, height, 1, 1, 1, 1, 0xFF000000 | hue);
    }

    // SMOOTH LINE

    public static void line(
            DrawContext c,
            int x1, int y1,
            int x2, int y2,
            int color
    ) {

        if ((color >>> 24) == 0) return;

        float dx = x2 - x1;
        float dy = y2 - y1;

        float length = (float) Math.sqrt(
                dx * dx + dy * dy
        );

        var matrix = c.getMatrices();

        matrix.pushMatrix();

        try {

            matrix.translate(
                    x1 + .5f, y1 + .5f
            );

            matrix.rotate(
                    (float) Math.atan2(dy, dx)
            );

            image(
                    c, STROKE,
                    0, -1,
                    Math.max(1, (int) Math.ceil(length)),
                    2, color
            );

        } finally {

            matrix.popMatrix();
        }
    }

    // OFFICIAL LOGO

    public static void flake(
            DrawContext c,
            int x, int y,
            int size, int color
    ) {

        if (size <= 0) return;

        int diameter = size * 2 + 1;

        image(
                c, LOGO,
                x - size, y - size,
                diameter, diameter,
                0xFFFFFFFF
        );
    }

    // HD ICONS

    public static void cross(
            DrawContext c,
            int x, int y,
            int color
    ) {

        image(
                c, CROSS,
                x - 7, y - 7,
                15, 15,
                color
        );
    }

    public static void bars(
            DrawContext c,
            int x, int y,
            int color
    ) {

        image(
                c, BARS,
                x, y,
                14, 14,
                color
        );
    }

    public static void search(
            DrawContext c,
            int x, int y,
            int color
    ) {

        image(
                c, SEARCH,
                x - 6, y - 6,
                15, 15,
                color
        );
    }

    public static void gear(
            DrawContext c,
            int x, int y,
            int color
    ) {

        image(
                c, GEAR,
                x - 7, y - 7,
                15, 15,
                color
        );
    }

    // SMOOTH CIRCLES

    public static void circle(
            DrawContext c,
            int x, int y,
            int radius, int color
    ) {

        if (radius < 0) return;

        int size = radius * 2 + 1;

        roundedShape(c, x - radius, y - radius, size, size, radius + 1, 0, color);
    }

    public static void circle(DrawContext c, float x, float y, int radius, int color) {
        var matrix = c.getMatrices();
        matrix.pushMatrix();
        try {
            matrix.translate(x, y);
            circle(c, 0, 0, radius, color);
        } finally {
            matrix.popMatrix();
        }
    }

    public static void slider(DrawContext c, int x, int y, int width,
                              float progress, int track, int fill, int knob) {
        if (width <= 0) return;
        progress = Math.max(0f, Math.min(1f, progress));
        surface(c, x, y - 2, x + width, y + 3, 2,
                tint(track, 0xFF080D17, 0.18f), track);
        float length = width * progress;
        if (length > 0f) {
            var matrix = c.getMatrices();
            matrix.pushMatrix();
            try {
                matrix.translate(x, y - 2);
                matrix.scale(progress, 1f);
                surface(c, 0, 0, width, 5, 2,
                        tint(fill, 0xFFF0F7FF, 0.32f), tint(fill, 0xFF233553, 0.14f));
            } finally {
                matrix.popMatrix();
            }
        }
        circle(c, x + length, y + 1f, 6, opacity(knob, 0.16f));
        circle(c, x + length, y + 1f, 4, knob);
    }

    public static void toggle(DrawContext c, int x, int y, float progress, int track, int knob) {
        progress = Math.max(0f, Math.min(1f, progress));
        surface(c, x, y, x + 29, y + 15, 7,
                tint(track, 0xFFE7F0FF, 0.24f), tint(track, 0xFF0B1221, 0.22f));
        gradedShape(c, x, y, x + 29, y + 15, 7, 1,
                opacity(tint(track, 0xFFFFFFFF, 0.50f), 0.46f),
                opacity(tint(track, 0xFF020612, 0.45f), 0.42f));

        var matrix = c.getMatrices();
        matrix.pushMatrix();
        try {
            matrix.translate(x + 2 + progress * 14f, y + 2f);
            round(c, 0, 1, 11, 12, 5, opacity(0xE608101E, (knob >>> 24) / 255f * 0.34f));
            surface(c, 0, 0, 11, 11, 5,
                    tint(knob, 0xFFE8F4FF, 0.32f), tint(knob, 0xFF233553, 0.16f));
        } finally {
            matrix.popMatrix();
        }
    }
}
