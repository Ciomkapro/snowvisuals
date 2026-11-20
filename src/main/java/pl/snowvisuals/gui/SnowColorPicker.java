package pl.snowvisuals.gui;

import net.minecraft.client.Keyboard;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.Locale;
import java.util.function.IntConsumer;

final class SnowColorPicker {
    enum ClickResult { EDIT, SAVE, CANCEL }
    private enum Drag { NONE, SATURATION, HUE }

    private static final int X = 154;
    private static final int Y = 57;
    private static final int WIDTH = 332;
    private static final int HEIGHT = 276;
    private static final int FIELD_X = X + 17;
    private static final int FIELD_Y = Y + 51;
    private static final int FIELD_SIZE = 171;
    private static final int HUE_X = FIELD_X + FIELD_SIZE + 13;
    private static final int HUE_WIDTH = 15;
    private static final int SIDECAR_X = HUE_X + HUE_WIDTH + 14;
    private static final int CONTENT_RIGHT = X + WIDTH - 17;
    private static final int HEX_Y = FIELD_Y + 140;
    private static final int DONE_Y = Y + 235;
    private static final int DONE_WIDTH = 109;

    private final String title;
    private final IntConsumer setter;
    private Drag drag = Drag.NONE;
    private float hue;
    private float saturation;
    private float brightness;
    private boolean hexFocused;
    private boolean replaceHex;
    private String hex;

    SnowColorPicker(String title, int color, IntConsumer setter) {
        this.title = title;
        this.setter = setter;
        setRgb(color);
    }

    void render(DrawContext context, TextRenderer renderer, String done, int surface,
                int border, int fg, int muted, int accent, double mouseX, double mouseY) {
        int well = SnowStyle.blend(surface, fg, .055f);
        int selected = rgb() | 0xFF000000;
        SnowStyle.round(context, X - 5, Y + 2, X + WIDTH + 5, Y + HEIGHT + 8, 20, 0x7003060E);
        SnowStyle.glass(context, X, Y, X + WIDTH, Y + HEIGHT, 15, surface,
                SnowStyle.blend(border, accent, .31f), accent);
        SnowText.draw(context, renderer, title, X + 17, Y + 19, 1.60f, fg);
        SnowStyle.round(context, FIELD_X, Y + 39, CONTENT_RIGHT, Y + 40, 0, border);
        int closeColor = SnowStyle.inside(mouseX, mouseY, CONTENT_RIGHT - 23, Y + 10, CONTENT_RIGHT, Y + 33)
                ? SnowStyle.blend(well, fg, .13f) : well;
        SnowStyle.glass(context, CONTENT_RIGHT - 23, Y + 10, CONTENT_RIGHT, Y + 33, 6, closeColor, border, accent);
        SnowStyle.cross(context, CONTENT_RIGHT - 11, Y + 21, muted);

        SnowStyle.colorField(context, FIELD_X, FIELD_Y, FIELD_SIZE, FIELD_SIZE,
                Color.HSBtoRGB(hue, 1f, 1f), false);
        SnowStyle.colorField(context, HUE_X, FIELD_Y, HUE_WIDTH, FIELD_SIZE, 0, true);
        SnowStyle.frame(context, FIELD_X - 1, FIELD_Y - 1, FIELD_SIZE + 2, FIELD_SIZE + 2, 1, border);
        SnowStyle.frame(context, HUE_X - 1, FIELD_Y - 1, HUE_WIDTH + 2, FIELD_SIZE + 2, 1, border);

        float markX = FIELD_X + FIELD_SIZE * saturation;
        float markY = FIELD_Y + FIELD_SIZE * (1f - brightness);
        SnowStyle.circle(context, markX, markY, 6, 0xE6000000);
        SnowStyle.circle(context, markX, markY, 5, 0xFFFFFFFF);
        SnowStyle.circle(context, markX, markY, 3, selected);
        int hueY = Math.round(FIELD_Y + FIELD_SIZE * hue);
        SnowStyle.round(context, HUE_X - 3, hueY - 3, HUE_X + HUE_WIDTH + 3, hueY + 4, 3, 0xDD0D1323);
        SnowStyle.frame(context, HUE_X - 3, hueY - 3, HUE_WIDTH + 6, 7, 3, 0xFFF4F8FF);
        SnowStyle.round(context, HUE_X, hueY - 1, HUE_X + HUE_WIDTH, hueY + 2, 1, Color.HSBtoRGB(hue, 1f, 1f));

        SnowStyle.surface(context, SIDECAR_X, FIELD_Y, CONTENT_RIGHT, FIELD_Y + 109, 10,
                SnowStyle.blend(selected, 0xFFFFFFFF, .10f), selected);
        SnowStyle.frame(context, SIDECAR_X, FIELD_Y, CONTENT_RIGHT - SIDECAR_X, 109, 10, border);
        SnowText.draw(context, renderer, "HEX", SIDECAR_X, HEX_Y - 15, 1f, muted);
        SnowStyle.glass(context, SIDECAR_X, HEX_Y, CONTENT_RIGHT, HEX_Y + 31, 7, well,
                hexFocused ? accent : border, accent);
        if (hexFocused && replaceHex) {
            SnowStyle.round(context, SIDECAR_X + 4, HEX_Y + 5, CONTENT_RIGHT - 4, HEX_Y + 26,
                    4, SnowStyle.opacity(accent, .30f));
        }
        String text = "#" + hex;
        if (hexFocused && !replaceHex && System.currentTimeMillis() / 500 % 2 == 0) text += "|";
        float hexScale = Math.min(1.12f, (CONTENT_RIGHT - SIDECAR_X - 10) / SnowText.width(renderer, text));
        float textWidth = SnowText.width(renderer, text) * hexScale;
        SnowText.draw(context, renderer, text,
                (SIDECAR_X + CONTENT_RIGHT - textWidth) / 2f, HEX_Y + 12, hexScale, fg);

        int doneLeft = CONTENT_RIGHT - DONE_WIDTH;
        boolean valid = hex.length() == 6;
        int button = valid ? accent : well;
        if (valid && SnowStyle.inside(mouseX, mouseY, doneLeft, DONE_Y, CONTENT_RIGHT, DONE_Y + 27)) {
            button = SnowStyle.blend(button, fg, .17f);
        }
        SnowStyle.glass(context, doneLeft, DONE_Y, CONTENT_RIGHT, DONE_Y + 27, 7, button,
                valid ? SnowStyle.blend(button, fg, .26f) : border, fg);
        float doneScale = Math.min(1.12f, (DONE_WIDTH - 14) / Math.max(1f, SnowText.width(renderer, done)));
        float doneWidth = SnowText.width(renderer, done) * doneScale;
        SnowText.draw(context, renderer, done,
                (doneLeft + CONTENT_RIGHT - doneWidth) / 2f, DONE_Y + 10, doneScale,
                valid ? SnowStyle.contrast(button) : muted);
    }

    ClickResult click(double x, double y) {
        hexFocused = false;
        if (SnowStyle.inside(x, y, CONTENT_RIGHT - 23, Y + 10, CONTENT_RIGHT, Y + 33)) {
            return ClickResult.CANCEL;
        }
        if (SnowStyle.inside(x, y, CONTENT_RIGHT - DONE_WIDTH, DONE_Y, CONTENT_RIGHT, DONE_Y + 27)) {
            return ClickResult.SAVE;
        }
        if (SnowStyle.inside(x, y, SIDECAR_X, HEX_Y, CONTENT_RIGHT, HEX_Y + 31)) {
            hexFocused = true;
            replaceHex = true;
        } else if (SnowStyle.inside(x, y, FIELD_X - 1, FIELD_Y - 1,
                FIELD_X + FIELD_SIZE + 1, FIELD_Y + FIELD_SIZE + 1)) {
            drag = Drag.SATURATION;
            dragTo(x, y);
        } else if (SnowStyle.inside(x, y, HUE_X - 4, FIELD_Y - 1,
                HUE_X + HUE_WIDTH + 4, FIELD_Y + FIELD_SIZE + 1)) {
            drag = Drag.HUE;
            dragTo(x, y);
        }
        return ClickResult.EDIT;
    }

    boolean dragging() {
        return drag != Drag.NONE;
    }

    void dragTo(double x, double y) {
        if (drag == Drag.SATURATION) {
            saturation = unit((x - FIELD_X) / FIELD_SIZE);
            brightness = 1f - unit((y - FIELD_Y) / FIELD_SIZE);
        } else if (drag == Drag.HUE) {
            hue = unit((y - FIELD_Y) / FIELD_SIZE);
        } else {
            return;
        }
        hex = hexText(rgb());
    }

    void release(double x, double y) {
        dragTo(x, y);
        drag = Drag.NONE;
    }

    void editHex(KeyInput input, Keyboard keyboard) {
        if (!hexFocused) return;
        int key = input.getKeycode();
        int modifiers = input.modifiers();
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> replaceHex = true;
                case GLFW.GLFW_KEY_C -> {
                    if (replaceHex) keyboard.setClipboard("#" + hex);
                }
                case GLFW.GLFW_KEY_V -> {
                    String clipboard = keyboard.getClipboard().strip();
                    if (clipboard.matches("#?[0-9a-fA-F]{6}")) {
                        String value = clipboard.replace("#", "");
                        setRgb(Integer.parseInt(value, 16));
                        replaceHex = true;
                    }
                }
                default -> {}
            }
        } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
            hex = replaceHex ? "" : hex.substring(0, Math.max(0, hex.length() - 1));
            replaceHex = false;
        }
    }

    void typeHex(int codepoint) {
        int digit = Character.digit(codepoint, 16);
        if (!hexFocused || digit < 0) return;
        if (replaceHex) {
            hex = "";
            replaceHex = false;
        }
        if (hex.length() < 6) {
            hex += Character.toUpperCase(Character.forDigit(digit, 16));
            if (hex.length() == 6) setRgb(Integer.parseInt(hex, 16));
        }
    }

    boolean commit() {
        if (hex.length() != 6) return false;
        setter.accept(rgb());
        return true;
    }

    private void setRgb(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        hex = hexText(rgb);
    }

    private int rgb() {
        return Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    static String hexText(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    private static float unit(double value) {
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
