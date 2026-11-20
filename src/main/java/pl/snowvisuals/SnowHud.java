package pl.snowvisuals;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import org.lwjgl.glfw.GLFW;

import pl.snowvisuals.gui.SnowStyle;
import pl.snowvisuals.gui.SnowIcons;

import java.util.*;

import static pl.snowvisuals.SnowModules.Module;
import static pl.snowvisuals.SnowModules.Type;

public final class SnowHud {

    private static final Identifier ICON =
            Identifier.of("snowvisuals", "server_icon");

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static byte[] lastIcon;
    private static String lastAddress;
    private static boolean iconReady;

    private static final float[] HOVER = new float[Module.values().length];
    private static final float[] KEY_PRESS = new float[6];
    private static Drag drag;
    private static Slide settling;
    private static long lastFrame;

    private static final class Drag {
        final Module module;
        final double pressX, pressY;
        final float offsetX, offsetY;
        float x, y;
        double pointerX, pointerY;
        boolean moved;

        Drag(Module module, Layout layout, double x, double y) {
            this.module = module;
            pressX = pointerX = x;
            pressY = pointerY = y;
            this.x = layout.x();
            this.y = layout.y();
            offsetX = (float) (x - layout.x());
            offsetY = (float) (y - layout.y());
        }

        void move(double x, double y) {
            pointerX = x;
            pointerY = y;
            moved |= Math.hypot(x - pressX, y - pressY) >= 1.0;
        }
    }

    private static final class Slide {
        final Module module;
        float x, y;

        Slide(Drag from) {
            module = from.module;
            x = from.x;
            y = from.y;
        }
    }

    private record PotionRow(
            String name,
            String time,
            Identifier icon
    ) {}

    private record Layout(
            float x, float y, float width, float height,
            int baseWidth, int baseHeight, float scale,
            String value, List<PotionRow> potions, SnowHudWidgets.Card card
    ) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }

    private SnowHud() {}

    static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    private static SnowConfig cfg() {
        return SnowVisualsClient.CONFIG;
    }

    static SnowModules.State state(Module module) {
        return cfg().state(module);
    }

    static SnowHudChrome.Palette palette() {
        return SnowHudChrome.palette(cfg().hudColor);
    }

    static int accent() {
        return palette().accent();
    }

    static int fg() {
        return palette().ink();
    }

    static int muted() {
        return palette().muted();
    }

    static int border() {
        return palette().edge();
    }

    // REGISTER

    public static void register() {

        HudElementRegistry.addLast(
                Identifier.of("snowvisuals", "hud"),
                (context, ticks) -> {

                    if (MinecraftClient.isHudEnabled()
                            && mc().currentScreen == null) {

                        render(context, -1, -1, false, ticks.getTickProgress(false));
                    }
                }
        );

        HudElementRegistry.replaceElement(
                VanillaHudElements.CROSSHAIR,
                original -> (context, ticks) -> {

                    if (cfg().enabled(Module.CROSSHAIR)
                            && mc().player != null
                            && !mc().player.isSpectator()
                            && mc().options.getPerspective()
                            .isFirstPerson()) {

                        crosshair(context);

                    } else {

                        original.render(context, ticks);
                    }
                }
        );

        HudElementRegistry.replaceElement(
                VanillaHudElements.STATUS_EFFECTS,
                original -> (context, ticks) -> {

                    boolean custom =
                            cfg().enabled(Module.POTIONS)
                                    && mc().player != null
                                    && (mc().currentScreen == null
                                    || mc().currentScreen
                                    instanceof ChatScreen);

                    if (!custom) {
                        original.render(context, ticks);
                    }
                }
        );

        // HUD EDITOR

        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> {

                    if (!(screen instanceof ChatScreen)) {
                        return;
                    }

                    ScreenMouseEvents.allowMouseClick(screen)
                            .register((s, click) -> {

                                if (click.button() != 0
                                        || client.player == null
                                        || !MinecraftClient.isHudEnabled()) {

                                    return true;
                                }

                                Module module = hoveredModule(click.x(), click.y());
                                if (module != null) {
                                    drag = new Drag(module, EDIT_LAYOUTS.get(module), click.x(), click.y());
                                    settling = null;
                                    return false;
                                }

                                return true;
                            });

                    ScreenMouseEvents.allowMouseRelease(screen)
                            .register((s, click) -> {

                                if (click.button() == 0 && drag != null) {
                                    drag.move(click.x(), click.y());
                                    finishDrag();
                                    return false;
                                }
                                return true;
                            });

                    ScreenEvents.afterRender(screen)
                            .register((s, c, mx, my, delta) -> {

                                if (client.player == null) {
                                    return;
                                }

                                var window = client.getWindow();
                                double mouseX = client.mouse.getScaledX(window);
                                double mouseY = client.mouse.getScaledY(window);
                                if (drag != null) {
                                    drag.move(mouseX, mouseY);
                                    if (GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                                            != GLFW.GLFW_PRESS) {
                                        finishDrag();
                                    }
                                }

                                if (MinecraftClient.isHudEnabled()) {
                                    render(c, mouseX, mouseY, true,
                                            client.getRenderTickCounter().getTickProgress(false));
                                }
                            });
                }
        );
    }

    // SERVER ICON

    public static void tick(MinecraftClient client) {
        SnowHudWidgets.updateTargetContext(client);

        if (!(client.currentScreen instanceof ChatScreen)) {
            finishDrag();
            EDIT_LAYOUTS.clear();
        }

        ServerInfo info =
                client.player != null
                        && cfg().enabled(Module.SERVER)
                        ? client.getCurrentServerEntry()
                        : null;

        String address =
                info == null ? null : info.address;

        byte[] bytes =
                info == null ? null : info.getFavicon();

        if (Objects.equals(address, lastAddress)
                && Arrays.equals(bytes, lastIcon)) {
            return;
        }

        if (iconReady) {

            client.getTextureManager()
                    .destroyTexture(ICON);
        }

        iconReady = false;

        lastAddress = address;
        lastIcon = bytes == null ? null : bytes.clone();

        if (bytes == null
                || bytes.length > 1_048_576) {
            return;
        }

        NativeImage image = null;

        try {

            image = NativeImage.read(bytes);

            if (image.getWidth() != 64
                    || image.getHeight() != 64) {
                return;
            }

            NativeImageBackedTexture texture =
                    new NativeImageBackedTexture(
                            () -> "Snow Visuals server icon",
                            image
                    );

            image = null;

            try {

                texture.upload();

                client.getTextureManager()
                        .registerTexture(ICON, texture);

                iconReady = true;

            } catch (RuntimeException e) {

                texture.close();
                throw e;
            }

        } catch (Exception e) {

            org.slf4j.LoggerFactory
                    .getLogger("SnowVisuals")
                    .debug("Server icon unavailable", e);

        } finally {

            if (image != null) {
                image.close();
            }
        }
    }

    // HUD VALUES

    private static String value(Module m) {

        MinecraftClient mc = mc();

        return switch (m) {

            case COORDS -> {

                var pos = mc.player.getBlockPos();

                yield "X: " + pos.getX()
                        + "  Y: " + pos.getY()
                        + "  Z: " + pos.getZ();
            }

            case FPS ->
                    Integer.toString(mc.getCurrentFps());

            case SERVER -> {

                ServerInfo info =
                        mc.getCurrentServerEntry();

                yield info == null
                        ? SnowWords.tr("Singleplayer")
                        : info.address;
            }

            case PING -> {

                var handler = mc.getNetworkHandler();

                var entry = handler == null
                        ? null
                        : handler.getPlayerListEntry(
                        mc.player.getUuid()
                );

                yield entry == null
                        ? "-- ms"
                        : entry.getLatency() + " ms";
            }

            case CPS ->
                    SnowEffects.cps(false) + "  /  " + SnowEffects.cps(true);

            case PLAYTIME ->
                    SnowEffects.playtime();

            case SPEED ->
                    SnowEffects.speedText();

            default -> "";
        };
    }

    // POTIONS DATA

    private static List<PotionRow> potionRows() {

        List<PotionRow> rows = new ArrayList<>();

        for (var effect : mc().player.getStatusEffects()) {

            int duration = effect.getDuration();
            int seconds = Math.max(0, duration) / 20;

            String time = duration < 0
                    ? "∞"
                    : String.format(
                    Locale.ROOT,
                    "%d:%02d",
                    seconds / 60,
                    seconds % 60
            );

            String name =
                    effect.getEffectType()
                            .value()
                            .getName()
                            .getString();

            name += " " + roman(
                    effect.getAmplifier() + 1
            );

            Identifier icon =
                    InGameHud.getEffectTexture(
                            effect.getEffectType()
                    );

            rows.add(new PotionRow(name, time, icon));
        }

        rows.sort(
                Comparator.comparing(PotionRow::name)
        );

        return rows;
    }

    private static String roman(int level) {

        return switch (level) {

            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";

            default -> Integer.toString(level);
        };
    }

    // HUD TYPOGRAPHY

    static float textWidth(String text) {
        return SnowEffects.width(mc().textRenderer, text);
    }

    static String fit(String text, float width) {
        if (textWidth(text) <= width) {
            return text;
        }
        String suffix = "…";
        if (textWidth(suffix) > width) {
            return "";
        }
        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (textWidth(text.substring(0, end) + suffix) <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + suffix;
    }

    static float textWidth(String text, float size) {
        return textWidth(text) * size;
    }

    static void label(DrawContext context, String text, float x, float y, int color) {
        label(context, text, x, y, 1f, color);
    }

    static void label(DrawContext context, String text, float x, float y, float size, int color) {
        SnowEffects.text(context, mc().textRenderer, text, x, y, size, color);
    }

    // SCALED LAYOUT

    private static final Map<Module, Layout> EDIT_LAYOUTS = new EnumMap<>(Module.class);

    private static Layout layout(Module module, boolean editing, float tickProgress, float dt) {
        var settings = state(module);
        int baseWidth;
        int baseHeight;
        String value = "";
        List<PotionRow> potions = List.of();
        SnowHudWidgets.Card card = null;

        switch (module) {
            case ARMOR -> {
                baseWidth = 154;
                baseHeight = 103;
            }
            case KEYS -> {
                baseWidth = 106;
                baseHeight = 100;
            }
            case POTIONS -> {
                potions = potionRows();
                float width = Math.max(158, textWidth(SnowWords.tr("Potions"), 1.12f) + 41);
                for (PotionRow row : potions) {
                    width = Math.max(width, textWidth(row.name()) + textWidth(row.time(), 0.92f) + 58);
                }
                baseWidth = Math.min((int) Math.ceil(width), 290);
                baseHeight = 33 + Math.max(1, potions.size()) * 22;
            }
            case BETTER_CPS, COMBO, HIT_MARKER, PEARL_COOLDOWN, TOTEM_COUNT, ARROW_COUNT,
                    BOW_CHARGE, TARGET_HUD, PING_GRAPH, DIRECTION, ITEM_COUNT, SPOTIFY -> {
                card = SnowHudWidgets.create(module, editing, tickProgress, dt);
                if (card == null) {
                    return null;
                }
                baseWidth = card.width();
                baseHeight = card.height();
            }
            default -> {
                value = fit(value(module), 226f / 1.14f);
                float contentWidth = Math.max(textWidth(value, 1.14f), textWidth(metricTitle(module), 0.88f));
                baseWidth = Math.max(64, (int) Math.ceil(contentWidth + 44));
                baseHeight = 27;
            }
        }

        var window = mc().getWindow();
        int screenWidth = Math.max(1, window.getScaledWidth());
        int screenHeight = Math.max(1, window.getScaledHeight());
        float scale = Math.min(SnowConfig.clamp(settings.scale, 50, 200) / 100f,
                Math.min(screenWidth / (float) baseWidth, screenHeight / (float) baseHeight));
        float width = baseWidth * scale;
        float height = baseHeight * scale;
        float maxX = screenWidth - width;
        float maxY = screenHeight - height;
        boolean centered = module == Module.HIT_MARKER && settings.centered;
        float x = centered ? maxX / 2f : bounded(settings.x, maxX);
        float y = centered ? maxY / 2f : bounded(settings.y, maxY);

        if (drag != null && drag.module == module) {
            if (drag.moved) {
                float targetX = dragTarget(drag.pointerX, drag.offsetX, maxX);
                float targetY = dragTarget(drag.pointerY, drag.offsetY, maxY);
                drag.x = SnowStyle.approach(drag.x, targetX, 28f, dt);
                drag.y = SnowStyle.approach(drag.y, targetY, 28f, dt);
            }
            x = bounded(drag.x, maxX);
            y = bounded(drag.y, maxY);
        } else if (settling != null && settling.module == module) {
            settling.x = SnowStyle.approach(settling.x, x, 28f, dt);
            settling.y = SnowStyle.approach(settling.y, y, 28f, dt);
            if (Math.abs(settling.x - x) < 0.02f && Math.abs(settling.y - y) < 0.02f) {
                settling = null;
            } else {
                x = bounded(settling.x, maxX);
                y = bounded(settling.y, maxY);
            }
        }

        return new Layout(x, y, width, height, baseWidth, baseHeight, scale, value, potions, card);
    }

    private static float bounded(float value, float maximum) {
        return Math.max(0f, Math.min(value, maximum));
    }

    private static float dragTarget(double pointer, float offset, float maximum) {
        float position = (float) (pointer - offset);
        if (cfg().hudSnap) {
            position = Math.round(position / 4f) * 4f;
        }
        return bounded(position, maximum);
    }

    private static void finishDrag() {
        if (drag == null) {
            return;
        }
        Drag finished = drag;
        drag = null;
        Layout layout = EDIT_LAYOUTS.get(finished.module);
        if (!finished.moved || layout == null || mc().player == null) {
            return;
        }

        var window = mc().getWindow();
        var settings = state(finished.module);
        int x = Math.round(dragTarget(finished.pointerX, finished.offsetX, window.getScaledWidth() - layout.width()));
        int y = Math.round(dragTarget(finished.pointerY, finished.offsetY, window.getScaledHeight() - layout.height()));
        if (x != settings.x || y != settings.y || settings.centered) {
            settings.x = x;
            settings.y = y;
            settings.centered = false;
            settling = new Slide(finished);
            cfg().save();
        }
    }

    // HUD RENDER

    private static void render(
            DrawContext c, double mouseX, double mouseY, boolean editing, float tickProgress
    ) {
        if (mc().player == null) {
            EDIT_LAYOUTS.clear();
            return;
        }

        long now = System.nanoTime();
        float dt = SnowStyle.frameSeconds(now, lastFrame);
        lastFrame = now;
        EDIT_LAYOUTS.clear();

        if (!editing) {
            if (cfg().enabled(Module.DAMAGE_TINT)) {
                SnowHudWidgets.renderDamageTint(c);
            }
            for (Module module : Module.values()) {
                if (module.type == Type.HUD && cfg().enabled(module)) {
                    Layout layout = layout(module, false, tickProgress, dt);
                    if (layout != null) {
                        renderCard(c, module, layout, dt, 0f);
                    }
                }
            }
            Arrays.fill(HOVER, 0f);
            return;
        }

        for (Module module : Module.values()) {
            if (module.type == Type.HUD && cfg().enabled(module)) {
                Layout layout = layout(module, true, tickProgress, dt);
                if (layout != null) {
                    EDIT_LAYOUTS.put(module, layout);
                }
            }
        }

        Module selected = drag == null ? hoveredModule(mouseX, mouseY) : drag.module;
        for (Module module : Module.values()) {
            Layout layout = EDIT_LAYOUTS.get(module);
            if (layout == null) {
                HOVER[module.ordinal()] = 0f;
                continue;
            }
            float hover = SnowStyle.approach(HOVER[module.ordinal()], selected == module ? 1f : 0f, 20f, dt);
            HOVER[module.ordinal()] = hover;
            if (drag == null || drag.module != module) {
                renderCard(c, module, layout, dt, hover);
            }
        }
        if (drag != null) {
            Layout layout = EDIT_LAYOUTS.get(drag.module);
            if (layout != null) {
                float emphasis = HOVER[drag.module.ordinal()];
                if (drag.moved) renderDragGuides(c, layout, emphasis);
                renderCard(c, drag.module, layout, dt, emphasis);
            }
        }
        if (selected != null) {
            Layout layout = EDIT_LAYOUTS.get(selected);
            if (layout != null) renderEditorTag(c, selected, layout, HOVER[selected.ordinal()]);
        }
    }

    private static void renderDragGuides(DrawContext c, Layout layout, float visibility) {
        if (visibility <= 0.02f) return;
        int color = SnowStyle.opacity(accent(), visibility * 0.27f);
        int left = Math.round(layout.x());
        int top = Math.round(layout.y());
        int right = Math.round(layout.x() + layout.width());
        int bottom = Math.round(layout.y() + layout.height());
        int windowWidth = c.getScaledWindowWidth();
        int windowHeight = c.getScaledWindowHeight();
        int midX = (left + right) / 2;
        int midY = (top + bottom) / 2;
        c.fill(midX, 0, midX + 1, Math.max(0, top - 4), color);
        c.fill(midX, Math.min(windowHeight, bottom + 4), midX + 1, windowHeight, color);
        c.fill(0, midY, Math.max(0, left - 4), midY + 1, color);
        c.fill(Math.min(windowWidth, right + 4), midY, windowWidth, midY + 1, color);
    }

    private static void renderEditorTag(DrawContext c, Module module, Layout layout, float visibility) {
        if (visibility < 0.08f) return;
        var colors = palette();
        String position = drag != null ? Math.round(layout.x()) + " · " + Math.round(layout.y()) : null;
        String title = fit(SnowWords.tr(module.name), Math.max(1f, c.getScaledWindowWidth() - 65f));
        int positionWidth = position == null ? 0 : (int) Math.ceil(textWidth(position, 0.85f)) + 13;
        int width = (int) Math.ceil(textWidth(title, 0.90f)) + 21 + positionWidth;
        int x = Math.round(bounded(layout.x(), c.getScaledWindowWidth() - width));
        int y = Math.round(layout.y()) - 22;
        if (y < 2) y = Math.round(layout.y() + layout.height()) + 5;
        if (y + 17 > c.getScaledWindowHeight()) return;

        SnowStyle.surface(c, x, y, x + width, y + 17, 6, SnowStyle.opacity(colors.elevated(), 0.98f * visibility),
                SnowStyle.opacity(colors.base(), 0.98f * visibility));
        SnowStyle.frame(c, x, y, width, 17, 6, SnowStyle.opacity(colors.edge(), visibility * 0.78f));
        SnowStyle.circle(c, x + 8, y + 8.5f, 2, SnowStyle.opacity(colors.accent(), visibility));
        label(c, title, x + 14, y + 4, 0.90f, SnowStyle.opacity(colors.ink(), visibility));
        if (position != null) {
            label(c, position, x + width - positionWidth + 5, y + 4, 0.85f,
                    SnowStyle.opacity(colors.muted(), visibility));
        }
    }

    private static Module hoveredModule(double mouseX, double mouseY) {
        Module[] modules = Module.values();
        for (int i = modules.length - 1; i >= 0; i--) {
            Module module = modules[i];
            Layout layout = EDIT_LAYOUTS.get(module);
            if (layout != null && cfg().enabled(module) && layout.contains(mouseX, mouseY)) {
                return module;
            }
        }
        return null;
    }

    private static void renderCard(DrawContext c, Module module, Layout layout, float dt, float hover) {
        var matrix = c.getMatrices();
        matrix.pushMatrix();
        try {
            SnowHudWidgets.Card card = layout.card();
            float shift = card == null ? 0f : card.offsetY() * layout.scale();
            if (layout.y() + layout.height() * 0.5f > c.getScaledWindowHeight() * 0.5f) shift = -shift;
            matrix.translate(layout.x(), layout.y() + shift);
            matrix.scale(layout.scale(), layout.scale());
            int width = layout.baseWidth();
            int height = layout.baseHeight();

            float visibility = card == null ? 1f : card.opacity();
            float opacity = SnowConfig.clamp(state(module).opacity, 0, 100) / 100f * visibility;
            var colors = palette();
            if (module != Module.HIT_MARKER) SnowHudChrome.panel(c, width, height, colors, opacity);

            if (card != null) {
                card.paint(c);
            } else {
                switch (module) {
                    case ARMOR -> renderArmor(c, width);
                    case POTIONS -> renderPotions(c, layout);
                    case KEYS -> renderKeys(c, dt);
                    default -> renderCompact(c, module, layout);
                }
            }

            SnowHudChrome.selection(c, width, height, colors, hover);
        } finally {
            matrix.popMatrix();
        }
    }

    // CRYSTAL METRICS

    private static String metricTitle(Module module) {
        return switch (module) {
            case COORDS -> SnowWords.tr("Coordinates");
            case FPS -> "FPS";
            case SERVER -> SnowWords.tr("Server");
            case PING -> "Ping";
            case CPS -> "CPS";
            default -> SnowWords.tr(module.name);
        };
    }

    private static void renderCompact(DrawContext c, Module module, Layout layout) {
        var palette = palette();
        SnowHudChrome.inset(c, 4, 4, 26, 24, 6, palette,
                SnowConfig.clamp(state(module).opacity, 0, 100) / 110f);

        switch (module) {
            case COORDS, FPS, PING -> SnowIcons.draw(c, module, 7, 6, 16, palette.ink());
            case SERVER -> {
                if (iconReady) {
                    c.drawTexture(RenderPipelines.GUI_TEXTURED, ICON, 7, 6, 0, 0, 16, 16, 64, 64, 64, 64);
                } else {
                    SnowStyle.flake(c, 15, 14, 7, palette.accent());
                }
            }
            case CPS -> {
                SnowStyle.frame(c, 10, 7, 10, 14, 4, palette.accent());
                SnowStyle.round(c, 14, 10, 16, 14, 1, palette.accent());
            }
            case PLAYTIME -> {
                SnowStyle.frame(c, 8, 7, 14, 14, 7, palette.accent());
                SnowStyle.line(c, 15, 10, 15, 14, palette.ink());
                SnowStyle.line(c, 15, 14, 18, 16, palette.ink());
            }
            case SPEED -> {
                SnowStyle.line(c, 8, 12, 21, 12, palette.accent());
                SnowStyle.line(c, 21, 12, 17, 8, palette.accent());
                SnowStyle.line(c, 21, 12, 17, 16, palette.accent());
                SnowStyle.line(c, 9, 17, 15, 17, palette.muted());
            }
            default -> SnowStyle.flake(c, 15, 14, 6, palette.accent());
        }

        label(c, metricTitle(module), 34, 4, 0.88f, palette.muted());
        label(c, layout.value(), 34, 13, 1.14f, palette.ink());
    }

    private static void header(DrawContext c, Module module, String title, int width) {
        var colors = palette();
        SnowHudChrome.inset(c, 7, 5, 27, 25, 5, colors, state(module).opacity / 115f);
        if (!SnowIcons.draw(c, module, 10, 8, 14, colors.ink())) {
            SnowStyle.flake(c, 17, 15, 6, colors.accent());
        }
        label(c, fit(title, (width - 41) / 1.12f), 34, 10, 1.12f, colors.ink());
    }

    // POTION HUD

    private static void renderPotions(DrawContext c, Layout layout) {
        int width = layout.baseWidth();
        var colors = palette();
        header(c, Module.POTIONS, SnowWords.tr("Potions"), width);
        float opacity = state(Module.POTIONS).opacity / 100f;

        if (layout.potions().isEmpty()) {
            SnowHudChrome.inset(c, 7, 29, width - 7, 48, 6, colors, opacity * 0.47f);
            label(c, fit(SnowWords.tr("No effects"), width - 28), 14, 34, colors.muted());
            return;
        }

        int y = 29;
        for (PotionRow row : layout.potions()) {
            SnowHudChrome.inset(c, 7, y, width - 7, y + 19, 6, colors, opacity * 0.49f);
            c.drawGuiTexture(RenderPipelines.GUI_TEXTURED, row.icon(), 12, y + 3, 12, 12);

            float timeWidth = textWidth(row.time(), 0.92f);
            int chipWidth = Math.max(26, (int) Math.ceil(timeWidth) + 10);
            int chipX = width - 11 - chipWidth;
            SnowStyle.round(c, chipX, y + 3, chipX + chipWidth, y + 16, 4,
                    SnowStyle.opacity(colors.edge(), opacity * 0.66f));
            label(c, fit(row.name(), Math.max(1, chipX - 38)), 30, y + 5, colors.ink());
            label(c, row.time(), chipX + (chipWidth - timeWidth) / 2, y + 5, 0.92f, colors.muted());
            y += 22;
        }
    }

    // ARMOR HUD

    private static String durability(
            ItemStack stack
    ) {

        if (stack.isEmpty()) {
            return "--";
        }

        int max = stack.getMaxDamage();

        if (max <= 0) {
            return "∞";
        }

        int remaining = Math.max(
                0,
                max - stack.getDamage()
        );

        if (state(Module.ARMOR).percent) {

            return Math.round(
                    remaining * 100f / max
            ) + "%";
        }

        return remaining + " / " + max;
    }

    private static float durabilityRatio(
            ItemStack stack
    ) {

        if (stack.isEmpty()) return 0f;

        int max = stack.getMaxDamage();

        if (max <= 0) return 1f;

        return Math.max(
                0f,
                Math.min(
                        1f,
                        (max - stack.getDamage())
                                / (float) max
                )
        );
    }

    private static int durabilityColor(float ratio) {

        if (ratio <= .20f) {
            return 0xFFEA6868;
        }

        if (ratio <= .45f) {
            return 0xFFE7B66A;
        }

        return accent();
    }

    private static void renderArmor(DrawContext c, int width) {
        var colors = palette();
        header(c, Module.ARMOR, SnowWords.tr("Armor"), width);
        float opacity = state(Module.ARMOR).opacity / 100f;
        int cellWidth = (width - 21) / 2;

        for (int i = 0; i < ARMOR.length; i++) {
            int x = 7 + i % 2 * (cellWidth + 7);
            int y = 29 + i / 2 * 36;
            int right = x + cellWidth;
            ItemStack stack = mc().player.getEquippedStack(ARMOR[i]);
            float ratio = durabilityRatio(stack);
            int color = stack.isEmpty() ? colors.muted() : durabilityColor(ratio);

            SnowHudChrome.inset(c, x, y, right, y + 31, 6, colors, opacity * 0.64f);
            if (!stack.isEmpty()) {
                c.drawItem(stack, x + 5, y + 3);
            } else {
                SnowStyle.frame(c, x + 8, y + 6, 10, 10, 3, SnowStyle.opacity(colors.edge(), opacity));
            }

            String text = fit(durability(stack), (cellWidth - 30) / 0.96f);
            label(c, text, right - textWidth(text, 0.96f) - 6, y + 8, 0.96f, color);
            SnowHudChrome.meter(c, x + 6, y + 23, right - 6, y + 27, ratio, color,
                    SnowStyle.opacity(colors.edge(), 0.81f));
        }
    }

    // KEYSTROKES

    private static void renderKeys(DrawContext c, float dt) {
        header(c, Module.KEYS, SnowWords.tr("Keystrokes"), 106);

        var options = mc().options;
        long window = mc().getWindow().getHandle();
        key(c, 0, 38, 28, 68, 48, "W", options.forwardKey.isPressed(), dt);
        key(c, 1, 5, 51, 35, 71, "A", options.leftKey.isPressed(), dt);
        key(c, 2, 38, 51, 68, 71, "S", options.backKey.isPressed(), dt);
        key(c, 3, 71, 51, 101, 71, "D", options.rightKey.isPressed(), dt);
        key(c, 4, 5, 74, 51, 95, "LMB", GLFW.glfwGetMouseButton(window, 0) == GLFW.GLFW_PRESS, dt);
        key(c, 5, 55, 74, 101, 95, "RMB", GLFW.glfwGetMouseButton(window, 1) == GLFW.GLFW_PRESS, dt);
    }

    private static void key(DrawContext c, int index, int x1, int y1, int x2, int y2,
                            String text, boolean pressed, float dt) {
        float progress = SnowStyle.approach(KEY_PRESS[index], pressed ? 1f : 0f, 25f, dt);
        KEY_PRESS[index] = progress;
        var colors = palette();
        float opacity = state(Module.KEYS).opacity / 100f;
        int top = SnowStyle.blend(SnowStyle.opacity(colors.elevated(), opacity), colors.accent(), progress);
        int bottom = SnowStyle.blend(SnowStyle.opacity(colors.base(), opacity),
                SnowStyle.blend(colors.accent(), colors.base(), 0.17f), progress);
        int textColor = SnowStyle.blend(colors.ink(), SnowStyle.contrast(colors.accent()), progress);

        SnowStyle.surface(c, x1, y1, x2, y2, 5, top, bottom);
        SnowStyle.frame(c, x1, y1, x2 - x1, y2 - y1, 5,
                SnowStyle.opacity(colors.edge(), Math.max(opacity, progress)));
        if (progress > 0.02f) {
            SnowStyle.round(c, x1 + 7, y2 - 2, x2 - 7, y2 - 1, 0,
                    SnowStyle.opacity(colors.accent(), progress * 0.84f));
        }
        float size = text.length() == 1 ? 1.14f : 0.90f;
        label(c, text, (x1 + x2 - textWidth(text, size)) / 2f, (y1 + y2 - 8 * size) / 2f,
                size, textColor);
    }

    // CUSTOM CROSSHAIR

    private static void crosshair(DrawContext c) {

        var s = state(Module.CROSSHAIR);

        int x = mc().getWindow()
                .getScaledWidth() / 2;

        int y = mc().getWindow()
                .getScaledHeight() / 2;

        int length = SnowConfig.clamp(
                s.a, 1, 20
        );

        int gap = SnowConfig.clamp(
                s.b, 0, 15
        );

        int thick = SnowConfig.clamp(
                s.c, 1, 5
        );

        int color = 0xFF000000 | s.color;

        int start = thick / 2;
        int end = thick - start;

        if (s.d == 1) {

            c.fill(
                    x - start, y - start,
                    x + end, y + end,
                    color
            );

        } else if (s.d == 2) {

            int radius = length + gap;

            for (int i = 0; i < 64; i++) {

                double angle = i * Math.PI / 32;

                int px = x + (int) Math.round(
                        Math.cos(angle) * radius
                );

                int py = y + (int) Math.round(
                        Math.sin(angle) * radius
                );

                c.fill(
                        px - start, py - start,
                        px + end, py + end,
                        color
                );
            }

        } else {

            c.fill(
                    x - gap - length, y - start,
                    x - gap, y + end, color
            );

            c.fill(
                    x + gap + 1, y - start,
                    x + gap + length + 1,
                    y + end, color
            );

            c.fill(
                    x - start, y - gap - length,
                    x + end, y - gap, color
            );

            c.fill(
                    x - start, y + gap + 1,
                    x + end,
                    y + gap + length + 1,
                    color
            );
        }
    }
}