package pl.snowvisuals;

import net.minecraft.client.gui.DrawContext;
import pl.snowvisuals.gui.SnowStyle;

final class SnowHudChrome {
    private static Palette cached;

    private SnowHudChrome() {}

    record Palette(int ink, int muted, int base, int elevated, int edge, int accent, boolean light) {}

    static Palette palette(int rgb) {
        int ink = 0xFF000000 | rgb;
        if (cached != null && cached.ink() == ink) return cached;

        double luminance = 0.2126 * channel(ink >>> 16) + 0.7152 * channel(ink >>> 8) + 0.0722 * channel(ink);
        boolean light = luminance < 0.195;
        int neutral = light ? 0xFFF4F8FF : 0xFF0D1626;
        int ice = light ? 0xFF206188 : 0xFFA3DBFF;
        int accent = SnowStyle.blend(ink, ice, 0.46f);
        int base = SnowStyle.blend(neutral, ink, light ? 0.028f : 0.038f);
        int elevated = SnowStyle.blend(base, ice, light ? 0.07f : 0.13f);
        cached = new Palette(ink, SnowStyle.blend(ink, base, 0.30f), base, elevated,
                SnowStyle.blend(base, ink, 0.20f), accent, light);
        return cached;
    }

    private static double channel(int value) {
        double sample = (value & 255) / 255.0;
        return sample <= 0.04045 ? sample / 12.92 : Math.pow((sample + 0.055) / 1.055, 2.4);
    }

    static void panel(DrawContext c, int width, int height, Palette palette, float opacity) {
        if (opacity <= 0f) return;

        SnowStyle.round(c, -1, 1, width + 1, height + 2, 10,
                SnowStyle.opacity(0x43070E1A, opacity));
        SnowStyle.surface(c, 0, 0, width, height, 9, SnowStyle.opacity(palette.elevated(), opacity),
                SnowStyle.opacity(palette.base(), opacity));
        SnowStyle.frame(c, 0, 0, width, height, 9, SnowStyle.opacity(palette.edge(), opacity * 0.78f));
        int highlightWidth = Math.min(width - 18, 32);
        if (highlightWidth > 0) {
            SnowStyle.round(c, 9, 0, 9 + highlightWidth, 1, 0,
                    SnowStyle.opacity(palette.accent(), opacity * 0.55f));
        }
    }

    static void inset(DrawContext c, int x1, int y1, int x2, int y2, int radius,
                      Palette palette, float opacity) {
        if (x2 <= x1 || y2 <= y1 || opacity <= 0f) return;
        int tile = SnowStyle.blend(palette.elevated(), palette.accent(), palette.light() ? 0.10f : 0.14f);
        SnowStyle.surface(c, x1, y1, x2, y2, radius, SnowStyle.opacity(tile, opacity),
                SnowStyle.opacity(palette.elevated(), opacity * 0.74f));
        SnowStyle.frame(c, x1, y1, x2 - x1, y2 - y1, radius,
                SnowStyle.opacity(palette.edge(), opacity * 0.64f));
    }

    static void meter(DrawContext c, int left, int top, int right, int bottom,
                      float ratio, int fill, int track) {
        if (right <= left || bottom <= top) return;
        int radius = (bottom - top) / 2;
        SnowStyle.round(c, left, top, right, bottom, radius, track);
        float progress = Math.max(0f, Math.min(1f, ratio));
        if (progress == 0f) return;

        var matrix = c.getMatrices();
        matrix.pushMatrix();
        try {
            matrix.translate(left, top);
            matrix.scale(progress, 1f);
            SnowStyle.surface(c, 0, 0, right - left, bottom - top, radius,
                    SnowStyle.blend(fill, (fill & 0xFF000000) | 0xFFFFFF, 0.23f), fill);
        } finally {
            matrix.popMatrix();
        }
    }

    static void selection(DrawContext c, int width, int height, Palette palette, float emphasis) {
        if (emphasis < 0.02f) return;
        int color = SnowStyle.opacity(palette.accent(), Math.min(1f, emphasis));
        SnowStyle.frame(c, 0, 0, width, height, 9, SnowStyle.opacity(color, 0.53f));
        for (int x : new int[]{1, width - 1}) {
            for (int y : new int[]{1, height - 1}) {
                SnowStyle.round(c, x - 2, y - 2, x + 3, y + 3, 1, palette.base());
                SnowStyle.round(c, x - 1, y - 1, x + 2, y + 2, 1, color);
            }
        }
    }
}
