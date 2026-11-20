package pl.snowvisuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import pl.snowvisuals.gui.SnowStyle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import static pl.snowvisuals.SnowHud.border;
import static pl.snowvisuals.SnowHud.fg;
import static pl.snowvisuals.SnowHud.fit;
import static pl.snowvisuals.SnowHud.label;
import static pl.snowvisuals.SnowHud.mc;
import static pl.snowvisuals.SnowHud.muted;
import static pl.snowvisuals.SnowHud.textWidth;
import static pl.snowvisuals.SnowWords.tr;

final class SnowSpotifyHud {
    private static final Identifier COVER_TEXTURE = Identifier.of("snowvisuals", "spotify/cover");
    private static final RenderPipeline COVER_PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.of("snowvisuals", "pipeline/spotify_cover"))
            .withVertexShader("core/position_tex_color")
            .withFragmentShader(Identifier.of("snowvisuals", "core/snow_spotify_cover"))
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();
    private static final int WIDTH = 234;
    private static final int HEADER_HEIGHT = 62;
    private static final int INSET = 10;
    private static final int COVER_SIZE = 42;
    private static final int TEXT_X = INSET + COVER_SIZE + 12;
    private static final int ROW_HEIGHT = 13;
    private static final float TITLE_SIZE = 1.2f;
    private static final int LYRICS_TOP = HEADER_HEIGHT + 1;
    private static final int SCROLL_MILLIS = 260;
    private static final int MAX_IMAGE_BYTES = 512 * 1024;
    private static final int MAX_IMAGE_EDGE = 512;
    private static final long PNG_MAGIC = 0x89504E470D0A1A0AL;
    private static final int IHDR = 0x49484452;

    private static String coverIdentity;
    private static boolean triedCover;
    private static int coverWidth;
    private static int coverHeight;

    private SnowSpotifyHud() {}

    static void tick(boolean enabled) {
        syncCoverIdentity(enabled ? SnowSpotify.track() : null);
    }

    static void close() {
        syncCoverIdentity(null);
    }

    static SnowHudWidgets.Card create(boolean showLyrics) {
        SnowSpotify.Track track = SnowSpotify.track();
        long position = track == null ? 0 : track.positionMillis();
        long duration = track == null ? 0 : track.durationMillis();
        String title = fit(track == null ? tr("Spotify Listener") : track.title(), (WIDTH - TEXT_X - INSET) / TITLE_SIZE);
        String artist = fit(track == null ? tr("Open the Spotify app") : track.artist(), WIDTH - TEXT_X - INSET);
        List<SnowSpotifyLyrics.Line> lines = showLyrics && track != null
                ? SnowSpotifyLyrics.lines(track) : List.of();
        int height = lines.isEmpty() ? HEADER_HEIGHT : LYRICS_TOP + 2 * ROW_HEIGHT + INSET;
        return new SnowHudWidgets.Card(WIDTH, height, context -> {
            drawCover(context, track);
            SnowEffects.text(context, mc().textRenderer, title, TEXT_X, 12, TITLE_SIZE, fg());
            label(context, artist, TEXT_X, 28, muted());
            drawTimeline(context, position, duration);
            if (!lines.isEmpty()) drawLyrics(context, lines, position);
        });
    }

    private static void drawTimeline(DrawContext context, long position, long duration) {
        String remaining = duration > 0 ? remainingLabel(duration, position) : "—";
        float timeX = WIDTH - INSET - textWidth(remaining) - 5;
        int pill = (int) Math.floor(timeX) - 5;
        SnowStyle.round(context, pill, 40, WIDTH - INSET, 54, 4,
                SnowStyle.opacity(border(), 0.80f));
        label(context, remaining, timeX, 44, muted());

        int right = pill - 8;
        int centerY = 47;
        int fillColor = fg();
        int dimFill = SnowStyle.blend(muted(), fillColor, .45f);
        SnowStyle.round(context, TEXT_X, centerY - 2, right, centerY + 2, 2,
                SnowStyle.opacity(border(), .92f));
        float progress = duration > 0 ? MathHelper.clamp(position / (float) duration, 0f, 1f) : 0f;
        if (progress <= 0f) return;

        var matrix = context.getMatrices();
        matrix.pushMatrix();
        try {
            matrix.translate(TEXT_X, centerY - 2);
            matrix.scale(progress, 1f);
            SnowStyle.surface(context, 0, 0, right - TEXT_X, 4, 2, fillColor, dimFill);
        } finally {
            matrix.popMatrix();
        }
        SnowStyle.circle(context, TEXT_X + (right - TEXT_X) * progress, centerY, 2, fillColor);
    }
    private static String remainingLabel(long duration, long position) {
        long seconds = (Math.max(0, duration - position) + 999) / 1_000;
        long tail = seconds % 60;
        return "−" + seconds / 60 + ":" + (tail < 10 ? "0" : "") + tail;
    }

    private static void drawLyrics(DrawContext context, List<SnowSpotifyLyrics.Line> lines, long position) {
        SnowStyle.round(context, INSET, LYRICS_TOP - 2, WIDTH - INSET, LYRICS_TOP + 2 * ROW_HEIGHT + 3,
                5, SnowStyle.opacity(border(), 0.32f));
        SnowStyle.round(context, INSET + 3, LYRICS_TOP + 3, INSET + 5, LYRICS_TOP + 10, 1,
                SnowStyle.opacity(fg(), .72f));
        int active = SnowSpotifyLyrics.activeIndex(lines, position);

        context.enableScissor(INSET + 8, LYRICS_TOP, WIDTH - INSET - 3, LYRICS_TOP + 2 * ROW_HEIGHT);
        try {
            float firstY = LYRICS_TOP + 2;
            if (active < 0) {
                drawLine(context, lines, 0, firstY, muted());
                drawLine(context, lines, 1, firstY + ROW_HEIGHT, muted());
                return;
            }

            long elapsed = Math.max(0, position - lines.get(active).startMillis());
            long scrollMillis = active + 1 < lines.size()
                    ? Math.min(SCROLL_MILLIS, lines.get(active + 1).startMillis() - lines.get(active).startMillis())
                    : SCROLL_MILLIS;
            if (active == 0 || scrollMillis <= 0 || elapsed >= scrollMillis) {
                drawLine(context, lines, active, firstY, fg());
                drawLine(context, lines, active + 1, firstY + ROW_HEIGHT, muted());
                return;
            }

            float t = elapsed / (float) scrollMillis;
            float smooth = t * t * (3f - 2f * t);
            float offset = ROW_HEIGHT * smooth;
            drawLine(context, lines, active - 1, firstY - offset, SnowStyle.opacity(fg(), 1f - smooth));
            drawLine(context, lines, active, firstY + ROW_HEIGHT - offset, SnowStyle.blend(muted(), fg(), smooth));
            drawLine(context, lines, active + 1, firstY + 2 * ROW_HEIGHT - offset, muted());
        } finally {
            context.disableScissor();
        }
    }

    private static void drawLine(DrawContext context, List<SnowSpotifyLyrics.Line> lines, int index, float y, int color) {
        if (index < 0 || index >= lines.size()) return;
        String text = lines.get(index).text();
        if (!text.isEmpty()) label(context, fit(text, WIDTH - 2 * INSET - 13), INSET + 9, y, color);
    }

    private static void drawCover(DrawContext context, SnowSpotify.Track track) {
        syncCoverIdentity(track);
        if (!triedCover && track != null && track.hasCoverArt()) {
            triedCover = true;
            uploadCover(track.coverArt());
        }
        int end = INSET + COVER_SIZE;
        if (coverWidth == 0) {
            int base = SnowStyle.blend(border(), muted(), 0.18f);
            SnowStyle.glass(context, INSET, INSET, end, end, 8, base, border(), fg());
            SnowStyle.flake(context, INSET + COVER_SIZE / 2, INSET + COVER_SIZE / 2, 14, fg());
        } else {
            int edge = Math.min(coverWidth, coverHeight);
            context.drawTexture(COVER_PIPELINE, COVER_TEXTURE, INSET, INSET,
                    (coverWidth - edge) / 2f, (coverHeight - edge) / 2f,
                    COVER_SIZE, COVER_SIZE, edge, edge, coverWidth, coverHeight);
            SnowStyle.frame(context, INSET, INSET, COVER_SIZE, COVER_SIZE, 7,
                    SnowStyle.opacity(fg(), 0.23f));
        }
        if (track != null) {
            int x = end - 5;
            int y = end - 5;
            int plate = 0xEB111A29;
            int icon = 0xFFEAF3FF;
            SnowStyle.circle(context, x, y, 6, plate);
            if (track.playing()) {
                SnowStyle.circle(context, x, y, 2, 0xFFB1E7CF);
            } else {
                SnowStyle.round(context, x - 2, y - 2, x - 1, y + 2, 0, icon);
                SnowStyle.round(context, x + 1, y - 2, x + 2, y + 2, 0, icon);
            }
        }
    }
    private static void syncCoverIdentity(SnowSpotify.Track track) {
        String identity = track == null ? null : track.identity();
        if (Objects.equals(identity, coverIdentity)) return;
        if (coverWidth != 0) mc().getTextureManager().destroyTexture(COVER_TEXTURE);
        coverIdentity = identity;
        triedCover = false;
        coverWidth = 0;
        coverHeight = 0;
    }

    private static void uploadCover(byte[] png) {
        if (!acceptablePng(png)) return;

        NativeImage image = null;
        CoverTexture texture = null;
        try {
            image = NativeImage.read(png);
            int width = image.getWidth();
            int height = image.getHeight();
            texture = new CoverTexture(image);
            image = null;
            texture.upload();
            mc().getTextureManager().registerTexture(COVER_TEXTURE, texture);
            coverWidth = width;
            coverHeight = height;
            texture = null;
        } catch (IOException | RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("SnowVisuals").debug("Spotify cover art unavailable", e);
        } finally {
            if (texture != null) texture.close();
            if (image != null) image.close();
        }
    }

    private static boolean acceptablePng(byte[] bytes) {
        if (bytes == null || bytes.length < 33 || bytes.length > MAX_IMAGE_BYTES) return false;
        ByteBuffer header = ByteBuffer.wrap(bytes);
        if (header.getLong() != PNG_MAGIC || header.getInt() != 13 || header.getInt() != IHDR) return false;
        int width = header.getInt();
        int height = header.getInt();
        return width > 0 && width <= MAX_IMAGE_EDGE && height > 0 && height <= MAX_IMAGE_EDGE;
    }

    private static final class CoverTexture extends NativeImageBackedTexture {
        private CoverTexture(NativeImage image) {
            super(() -> "Snow Visuals Spotify cover", image);
            sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
        }
    }
}
