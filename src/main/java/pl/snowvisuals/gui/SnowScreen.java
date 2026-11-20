package pl.snowvisuals.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import org.lwjgl.glfw.GLFW;

import pl.snowvisuals.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static pl.snowvisuals.SnowModules.Module;
import static pl.snowvisuals.SnowModules.Type;

public final class SnowScreen extends Screen {

    private static final String TITLE = "snowvisuals";
    private static final int W = 640;
    private static final int H = 390;
    private static final int LEFT = 178;
    private static final int RIGHT = 622;
    private static final float TRANSITION_DISTANCE = 8f;
    private static final float BODY_TEXT_SCALE = 1.18f;

    private static final String[] TABS = {
            "Modules", "Visuals", "HUD",
            "Camera", "Profiles", "Settings"
    };

    private record Range(
            int x1, int x2, int row,
            int min, int max,
            IntConsumer setter
    ) {}

    private record Hit(
            float x1, float y1, float x2, float y2,
            Runnable action,
            Range range,
            Module module
    ) {}

    private static final int MODULES_PER_PAGE = 8;
    private static final int PROFILES_PER_PAGE = 4;
    private static final int DIALOG_LEFT = 138;
    private static final int DIALOG_RIGHT = W - DIALOG_LEFT;

    private enum ProfileAction { CREATE, EDIT, DELETE }

    private static final class ProfileDialog {
        private final ProfileAction action;
        private final SnowProfiles.Profile profile;
        private String name;
        private int cursor;
        private boolean selected;
        private boolean failed;

        private ProfileDialog(ProfileAction action, SnowProfiles.Profile profile) {
            this.action = action;
            this.profile = profile;
            name = profile == null ? "" : profile.name();
            cursor = name.length();
            selected = action == ProfileAction.EDIT;
        }

        private void insert(String value) {
            int[] points = value.codePoints()
                    .filter(codePoint -> !Character.isISOControl(codePoint) && Character.isDefined(codePoint))
                    .limit(SnowProfiles.MAX_NAME_LENGTH)
                    .toArray();
            String typed = new String(points, 0, points.length);
            if (typed.isEmpty()) return;
            if (selected) {
                name = "";
                cursor = 0;
                selected = false;
            }
            int available = SnowProfiles.MAX_NAME_LENGTH - name.length();
            int length = Math.min(available, typed.length());
            if (length > 0 && Character.isHighSurrogate(typed.charAt(length - 1))) length--;
            if (length > 0) {
                name = name.substring(0, cursor) + typed.substring(0, length) + name.substring(cursor);
                cursor += length;
                failed = false;
            }
        }

        private void delete(boolean backward) {
            if (selected) {
                name = "";
                cursor = 0;
                selected = false;
            } else if (backward && cursor > 0) {
                int previous = name.offsetByCodePoints(cursor, -1);
                name = name.substring(0, previous) + name.substring(cursor);
                cursor = previous;
            } else if (!backward && cursor < name.length()) {
                name = name.substring(0, cursor) + name.substring(name.offsetByCodePoints(cursor, 1));
            }
            failed = false;
        }
    }

    private enum Motion { HOVER, ACTIVE, SLIDER }

    private record Control(int view, int x, int y, Motion motion) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Map<Control, Float> controls = new HashMap<>();
    private final float[] animations = new float[Module.values().length];
    private final float[] moduleHover = new float[Module.values().length];
    private final float[] sidebarHover = new float[TABS.length + 1];
    private final float[] sidebarSelection = new float[TABS.length + 1];

    private int tab, page;
    private int renderedTab = -1;
    private int renderedPage = -1;
    private Module renderedModule;
    private float paneMotion;
    private float pageMotion;
    private float contentOffset;
    private float contentOpacity = 1f;

    private Module selected, binding;
    private SnowSpotify.Action spotifyBinding;
    private boolean spotifyBindConflict;

    private boolean guiBinding, searching;

    private String search = "";
    private String notice = "";
    private List<SnowProfiles.Profile> profiles = List.of();
    private int profilePage;
    private ProfileDialog profileDialog;

    private Range dragging;
    private SnowColorPicker colorPicker;

    private float scale = 1f;
    private float frozenScale = 1f;

    private float ox, oy;

    private double mx, my;

    private long lastFrame;
    private float frameSeconds;

    public SnowScreen() {

        super(SnowEffects.font(TITLE));
        sidebarSelection[0] = 1f;

        for (Module m : Module.values()) {
            animations[m.ordinal()] =
                    cfg().enabled(m) ? 1f : 0f;
        }
    }

    private SnowConfig cfg() {
        return SnowVisualsClient.CONFIG;
    }

    private String tr(String text) {

        int lang = SnowLanguage.CODES.indexOf(
                SnowLanguage.normalize(cfg().language)
        );

        return switch (text) {

            case "Edit HUD" -> new String[]{
                    "Edit HUD", "Edytuj HUD",
                    "Настроить HUD", "HUD bearbeiten",
                    "Редагувати HUD"
            }[lang];

            case "HUD Settings" -> new String[]{
                    "HUD Settings", "Ustawienia HUD",
                    "Настройки HUD", "HUD-Einstellungen",
                    "Налаштування HUD"
            }[lang];

            case "Module settings" -> new String[]{
                    "Module settings", "Ustawienia modułu",
                    "Настройки модуля", "Moduleinstellungen",
                    "Налаштування модуля"
            }[lang];

            default -> SnowWords.tr(text);
        };
    }

    // THEME

    private int guiColor() {
        return 0xFF000000 | cfg().guiColor;
    }

    private int background() {
        return cfg().dark
                ? SnowStyle.blend(0xFF101824, guiColor(), .39f)
                : SnowStyle.blend(0xFFF3F7FD, guiColor(), .16f);
    }

    private int sidebarColor() {
        return SnowStyle.blend(background(), cfg().dark ? 0xFF080D16 : 0xFFCDDFEE, .34f);
    }

    private int panel() {
        return SnowStyle.blend(background(), cfg().dark ? 0xFFB8D7F4 : 0xFFFFFFFF, .08f);
    }

    private int hover() {
        return SnowStyle.blend(panel(), accent(), .18f);
    }

    private int border() {
        return SnowStyle.blend(panel(), fg(), cfg().dark ? .16f : .21f);
    }

    private int fg() {
        return cfg().dark ? 0xFFF3F8FE : 0xFF101D2B;
    }

    private int muted() {
        return SnowStyle.blend(fg(), panel(), .34f);
    }

    private int ice() {
        return SnowStyle.blend(accent(), cfg().dark ? 0xFFC4EBFF : 0xFF1D628F, .59f);
    }

    private int accent() {
        return 0xFF000000 | cfg().accentColor;
    }

    private int alpha(int color) {
        return SnowStyle.alpha(
                color, 100 - cfg().guiTransparency
        );
    }

    // LAYOUT

    @Override
    protected void init() {
        lastFrame = System.nanoTime();
        layout(true);
    }

    private void layout(boolean immediate) {

        float fit = Math.min(
                Math.max(1, width - 16) / (float) W,
                Math.max(1, height - 16) / (float) H
        );

        float target = Math.min(cfg().guiScale / 100f, fit);
        if (dragging != null || (colorPicker != null && colorPicker.dragging())) {
            scale = frozenScale;
        } else {
            scale = immediate ? target : SnowStyle.approach(scale, target, 18f, frameSeconds);
        }

        ox = (width - W * scale) / 2f;
        oy = (height - H * scale) / 2f;
    }

    @Override
    public void render(
            DrawContext c,
            int mouseX,
            int mouseY,
            float delta
    ) {

        long now = System.nanoTime();
        frameSeconds = SnowStyle.frameSeconds(now, lastFrame);
        lastFrame = now;
        layout(false);
        float visibility = (100 - cfg().guiTransparency) / 100f;
        c.fillGradient(0, 0, width, height,
                SnowStyle.opacity(0x58081422, visibility), SnowStyle.opacity(0x9E050913, visibility));

        var minecraft = MinecraftClient.getInstance();
        var window = minecraft.getWindow();
        mx = (minecraft.mouse.getScaledX(window) - ox) / scale;
        my = (minecraft.mouse.getScaledY(window) - oy) / scale;
        hits.clear();

        for (Module m : Module.values()) {
            int i = m.ordinal();
            animations[i] = animate(animations[i], cfg().enabled(m) ? 1f : 0f, 18f);
        }

        paneMotion = animate(paneMotion, 0f, 20f);
        updatePanelTransition();

        var matrix = c.getMatrices();

        matrix.pushMatrix();

        try {

            matrix.translate(ox, oy);
            matrix.scale(scale, scale);

            drawShell(c);
            renderSidebar(c);

            c.enableScissor(LEFT - 3, 8, RIGHT + 3, 374);
            try {
                withMotion(c, paneMotion, () -> {
                    if (tab == 5) {
                        renderSettings(c);
                    } else if (tab == 4) {
                        renderProfiles(c);
                    } else if (selected != null) {
                        renderModuleSettings(c);
                    } else {
                        renderModules(c);
                    }
                });
            } finally {
                c.disableScissor();
            }

            iconButton(c, RIGHT - 26, 14, 26, 0, this::close);

            if (colorPicker != null || profileDialog != null) {
                c.createNewRootLayer();
                c.fill((int) Math.floor(-ox / scale), (int) Math.floor(-oy / scale),
                        (int) Math.ceil((width - ox) / scale), (int) Math.ceil((height - oy) / scale),
                        0xA008101C);
                if (colorPicker != null) {
                    colorPicker.render(c, textRenderer, tr("Done"), sidebarColor(), border(), fg(), muted(),
                            accent(), mx, my);
                } else {
                    hits.clear();
                    renderProfileDialog(c);
                }
            }
        } finally {

            matrix.popMatrix();
        }
    }

    private void drawShell(DrawContext c) {
        int shadow = SnowStyle.opacity(alpha(0xFF03060E), .64f);
        round(c, -5, 7, W + 5, H + 10, 24, shadow);
        SnowStyle.surface(c, 0, 0, W, H, 20,
                alpha(SnowStyle.blend(sidebarColor(), ice(), .12f)), alpha(sidebarColor()));
        SnowStyle.frame(c, 0, 0, W, H, 20, SnowStyle.opacity(alpha(ice()), .27f));

        SnowStyle.surface(c, 158, 5, W - 5, H - 5, 16,
                alpha(SnowStyle.blend(background(), ice(), .10f)), alpha(background()));
        SnowStyle.frame(c, 158, 5, W - 163, H - 10, 16, SnowStyle.opacity(alpha(border()), .72f));
        round(c, LEFT, 46, RIGHT, 47, 0, SnowStyle.opacity(alpha(border()), .87f));
        round(c, LEFT, 374, RIGHT, 375, 0, SnowStyle.opacity(alpha(border()), .75f));
    }

    private void pageHeading(DrawContext c, String title) {
        largeText(c, fit(title, 265 * BODY_TEXT_SCALE / 1.94f), LEFT, 21, 1.94f, fg());
    }

    private void glyphArrow(DrawContext c, int x, int y, boolean right, int color) {
        int side = right ? 1 : -1;
        SnowStyle.line(c, x - 3 * side, y - 5, x + 2 * side, y, color);
        SnowStyle.line(c, x + 2 * side, y, x - 3 * side, y + 5, color);
    }

    private void iconButton(DrawContext c, int x, int y, int size, int icon, Runnable action) {
        int tile = controlBackground(x, y, x + size, y + size);
        SnowStyle.glass(c, x, y, x + size, y + size, 7, faded(alpha(tile)),
                faded(alpha(border())), faded(ice()));
        int ink = faded(fg());
        if (icon == 0) {
            SnowStyle.cross(c, x + size / 2, y + size / 2, ink);
        } else {
            glyphArrow(c, x + size / 2, y + size / 2, icon > 0, ink);
        }
        hit(x, y, x + size, y + size, action);
    }

    private void updatePanelTransition() {
        if (tab == renderedTab && selected == renderedModule) return;
        if (renderedTab >= 0) {
            boolean forward = tab != renderedTab ? tab > renderedTab : selected != null;
            paneMotion = forward ? TRANSITION_DISTANCE : -TRANSITION_DISTANCE;
        }
        renderedTab = tab;
        renderedModule = selected;
        renderedPage = -1;
        pageMotion = 0f;
    }

    private void withMotion(DrawContext c, float offset, Runnable render) {
        if (offset == 0f) {
            render.run();
            return;
        }

        var matrices = c.getMatrices();
        float previousOffset = contentOffset;
        float previousOpacity = contentOpacity;
        matrices.pushMatrix();
        try {
            matrices.translate(0f, offset);
            contentOffset += offset;
            contentOpacity *= 1f - 0.25f * Math.abs(offset) / TRANSITION_DISTANCE;
            render.run();
        } finally {
            contentOffset = previousOffset;
            contentOpacity = previousOpacity;
            matrices.popMatrix();
        }
    }

    private float animate(float current, float target, float rate) {
        return SnowStyle.approach(current, target, rate, frameSeconds);
    }

    private float control(int x, int y, Motion motion, float initial, float target, float rate) {
        int view = selected == null ? tab : TABS.length + selected.ordinal();
        var key = new Control(view, x, y, motion);
        float value = animate(controls.getOrDefault(key, initial), target, rate);
        controls.put(key, value);
        return value;
    }

    private int controlBackground(int x1, int y1, int x2, int y2) {
        float over = control(x1, y1, Motion.HOVER, 0f, inside(x1, y1, x2, y2) ? 1f : 0f, 20f);
        return SnowStyle.blend(panel(), hover(), over);
    }

    // SIDEBAR

    private void renderSidebar(DrawContext c) {
        int crystal = ice();
        SnowStyle.glass(c, 18, 22, 52, 56, 10, alpha(SnowStyle.blend(panel(), crystal, .19f)),
                alpha(SnowStyle.blend(border(), crystal, .46f)), crystal);
        SnowStyle.flake(c, 35, 39, 13, fg());
        largeText(c, TITLE, 60, 33, 1.36f, fg());
        round(c, 18, 68, 142, 69, 0, SnowStyle.opacity(alpha(border()), .66f));

        for (int i = 0; i < TABS.length; i++) {
            int target = i;
            sidebarButton(c, TABS[i], i, 82 + i * 37, tab == i, () -> changeTab(target));
        }

        round(c, 18, 319, 142, 320, 0, SnowStyle.opacity(alpha(border()), .66f));
        sidebarButton(c, "Edit HUD", 6, 335, false, this::openHudEditor);
    }

    private void sidebarButton(DrawContext c, String name, int icon, int y, boolean active, Runnable action) {
        int x = 15;
        int w = 131;
        int h = 31;
        float over = sidebarHover[icon] = animate(sidebarHover[icon],
                inside(x, y, x + w, y + h) ? 1f : 0f, 20f);
        float selection = sidebarSelection[icon] = animate(sidebarSelection[icon],
                active ? 1f : 0f, 18f);
        float strength = Math.max(over, selection);
        int crystal = ice();
        if (strength > 0f || icon == 6) {
            int base = SnowStyle.blend(panel(), crystal, .11f + .18f * selection);
            float weight = icon == 6 ? .65f + over * .35f : strength;
            SnowStyle.glass(c, x, y, x + w, y + h, 8,
                    SnowStyle.opacity(alpha(base), weight),
                    SnowStyle.opacity(alpha(SnowStyle.blend(border(), crystal, .30f * selection)), weight),
                    SnowStyle.opacity(crystal, weight));
        }
        if (selection > 0f) {
            round(c, x + 2, y + 8, x + 4, y + 23, 1, SnowStyle.opacity(alpha(crystal), selection));
        }
        int ink = SnowStyle.blend(muted(), fg(), Math.max(selection, over * .82f));
        int glyph = SnowStyle.blend(ink, crystal, .69f * selection + (icon == 6 ? .22f : 0f));
        navigationGlyph(c, icon, 31, y + 15, glyph);
        label(c, fit(tr(name), 95), 47, y + 11, ink);
        if (icon == 6) glyphArrow(c, 133, y + 15, true, muted());
        hit(x, y, x + w, y + h, action);
    }

    private void navigationGlyph(DrawContext c, int icon, int x, int y, int ink) {
        switch (icon) {
            case 0 -> {
                for (int i = 0; i < 4; i++) {
                    int gx = x - 6 + i % 2 * 7;
                    int gy = y - 6 + i / 2 * 7;
                    round(c, gx, gy, gx + 5, gy + 5, 1, ink);
                }
            }
            case 1 -> {
                SnowStyle.line(c, x, y - 7, x + 6, y, ink);
                SnowStyle.line(c, x + 6, y, x, y + 7, ink);
                SnowStyle.line(c, x, y + 7, x - 6, y, ink);
                SnowStyle.line(c, x - 6, y, x, y - 7, ink);
                SnowStyle.circle(c, x, y, 1, ink);
            }
            case 2 -> {
                SnowStyle.frame(c, x - 6, y - 5, 12, 10, 2, ink);
                round(c, x - 4, y - 2, x - 1, y + 2, 1, ink);
                round(c, x + 1, y - 2, x + 4, y - 1, 0, ink);
                round(c, x + 1, y + 1, x + 3, y + 2, 0, ink);
            }
            case 3 -> {
                SnowStyle.frame(c, x - 6, y - 5, 12, 10, 2, ink);
                SnowStyle.frame(c, x - 2, y - 2, 4, 4, 2, ink);
                round(c, x - 4, y - 7, x, y - 5, 1, ink);
            }
            case 4 -> {
                SnowStyle.frame(c, x - 6, y - 4, 10, 10, 2, ink);
                SnowStyle.line(c, x - 3, y - 6, x + 6, y - 6, ink);
                SnowStyle.line(c, x + 6, y - 6, x + 6, y + 3, ink);
            }
            case 5 -> SnowStyle.gear(c, x, y, ink);
            default -> {
                SnowStyle.frame(c, x - 5, y - 5, 10, 10, 1, ink);
                SnowStyle.line(c, x - 7, y, x + 7, y, ink);
                SnowStyle.line(c, x, y - 7, x, y + 7, ink);
            }
        }
    }

    private void changeTab(int next) {

        tab = next;
        page = 0;
        if (next == 4) {
            profilePage = 0;
            refreshProfiles();
        }

        search = "";
        notice = "";

        selected = null;
        binding = null;
        spotifyBinding = null;

        searching = false;
        guiBinding = false;
    }

    // MODULES

    private List<Module> visibleModules() {

        List<Module> result = new ArrayList<>();

        String query = search.strip()
                .toLowerCase(Locale.ROOT);

        for (Module m : Module.values()) {

            if (!query.isEmpty()) {

                if (m.name.toLowerCase(Locale.ROOT)
                        .contains(query)
                        || tr(m.name)
                        .toLowerCase(Locale.ROOT)
                        .contains(query)) {

                    result.add(m);
                }

                continue;
            }

            boolean matches = switch (tab) {

                case 0 -> true;

                case 1 -> m.type == Type.VISUAL
                        || m.type == Type.OTHER;

                case 2 -> m.type == Type.HUD;

                case 3 -> m.type == Type.CAMERA;

                default -> false;
            };

            if (matches) result.add(m);
        }

        return result;
    }

    private void renderModules(DrawContext c) {
        List<Module> modules = visibleModules();
        int pages = Math.max(1, (modules.size() + MODULES_PER_PAGE - 1) / MODULES_PER_PAGE);
        page = SnowConfig.clamp(page, 0, pages - 1);

        pageHeading(c, tr(TABS[tab]));
        String count = modules.size() + " " + tr("Modules");
        label(c, count, 579 - textWidth(count), 27, muted());
        renderSearch(c);

        pageMotion = animate(pageMotion, 0f, 20f);
        if (renderedPage >= 0 && renderedPage != page) {
            pageMotion = page > renderedPage ? TRANSITION_DISTANCE : -TRANSITION_DISTANCE;
        }
        renderedPage = page;
        c.enableScissor(LEFT - 2, 90, RIGHT + 2, 336);
        try {
            withMotion(c, pageMotion, () -> renderModulePage(c, modules));
        } finally {
            c.disableScissor();
        }

        round(c, LEFT, 338, RIGHT, 339, 0, SnowStyle.opacity(alpha(border()), .72f));
        if (pages > 1) {
            iconButton(c, LEFT, 345, 25, -1, () -> page = Math.max(0, page - 1));
            String cursor = (page + 1) + " / " + pages;
            label(c, cursor, LEFT + 57 - textWidth(cursor) * .5f, 353, muted());
            iconButton(c, LEFT + 91, 345, 25, 1, () -> page = Math.min(pages - 1, page + 1));
        }

        if (tab == 2) {
            button(c, RIGHT - 121, 345, RIGHT, 370, tr("Reset HUD"), false, () -> cfg().resetHud());
        }
    }

    private void renderModulePage(DrawContext c, List<Module> modules) {
        int start = page * MODULES_PER_PAGE;
        int columnWidth = (RIGHT - LEFT - 10) / 2;
        for (int i = start; i < Math.min(start + MODULES_PER_PAGE, modules.size()); i++) {
            int index = i - start;
            renderModule(c, modules.get(i), LEFT + index % 2 * (columnWidth + 10), 94 + index / 2 * 60,
                    columnWidth);
        }
        if (modules.isEmpty()) {
            SnowStyle.glass(c, LEFT, 94, RIGHT, 211, 12, faded(alpha(panel())), faded(alpha(border())), faded(ice()));
            SnowStyle.search(c, (LEFT + RIGHT) / 2, 138, faded(muted()));
            String message = tr("No modules found");
            label(c, message, (LEFT + RIGHT - textWidth(message)) / 2f, 158, muted());
        }
    }

    private void renderSearch(DrawContext c) {
        int y = 56;
        int h = 28;
        float focused = control(LEFT, y, Motion.ACTIVE, 0f, searching ? 1f : 0f, 18f);
        int bg = controlBackground(LEFT, y, RIGHT, y + h);
        SnowStyle.glass(c, LEFT, y, RIGHT, y + h, 8,
                faded(alpha(SnowStyle.blend(bg, hover(), focused))),
                faded(alpha(SnowStyle.blend(border(), ice(), focused * .76f))), faded(ice()));
        SnowStyle.search(c, LEFT + 17, y + 14, faded(SnowStyle.blend(muted(), fg(), focused)));
        boolean placeholder = search.isEmpty() && !searching;
        String value = placeholder ? tr("Search modules...") : search;
        if (searching && System.currentTimeMillis() / 500 % 2 == 0) value += "|";
        label(c, fit(value, RIGHT - LEFT - 72), LEFT + 34, y + 10, placeholder ? muted() : fg());

        if (search.isEmpty()) {
            hit(LEFT, y, RIGHT, y + h, () -> searching = true);
        } else {
            hit(LEFT, y, RIGHT - 34, y + h, () -> searching = true);
            iconButton(c, RIGHT - 28, y + 4, 20, 0, () -> {
                search = "";
                page = 0;
                searching = true;
            });
        }
    }

    private static String category(Module m) {
        return switch (m.type) {
            case HUD -> "HUD";
            case CAMERA -> "Camera";
            case VISUAL, OTHER -> "Visuals";
        };
    }

    private void renderModule(DrawContext c, Module m, int x, int y, int width) {
        int height = 53;
        int index = m.ordinal();
        float enabled = animations[index];
        float over = moduleHover[index] = animate(moduleHover[index],
                inside(x, y, x + width, y + height) ? 1f : 0f, 20f);
        int crystal = ice();
        int tile = SnowStyle.blend(SnowStyle.blend(panel(), hover(), .68f * over), crystal, .17f * enabled);
        int edge = SnowStyle.blend(border(), crystal, .54f * enabled + .19f * over);
        SnowStyle.glass(c, x, y, x + width, y + height, 10, faded(alpha(tile)), faded(alpha(edge)), faded(crystal));

        int iconBg = SnowStyle.blend(sidebarColor(), crystal, .10f + .23f * enabled + .08f * over);
        SnowStyle.surface(c, x + 9, y + 9, x + 44, y + 44, 8,
                faded(alpha(SnowStyle.blend(iconBg, crystal, .12f))), faded(alpha(iconBg)));
        int iconTone = faded(SnowStyle.blend(muted(), fg(), .38f + .62f * enabled));
        if (!SnowIcons.draw(c, m, x + 16, y + 16, 21, iconTone)) {
            switch (m.type) {
                case HUD -> navigationGlyph(c, 2, x + 26, y + 26, iconTone);
                case CAMERA -> navigationGlyph(c, 3, x + 26, y + 26, iconTone);
                case VISUAL, OTHER -> navigationGlyph(c, 1, x + 26, y + 26, iconTone);
            }
        }

        float titleScale = 1.32f;
        int textX = x + 53;
        int gear = x + width - 50;
        String name = fit(tr(m.name), (gear - textX - 9) * BODY_TEXT_SCALE / titleScale);
        if (tab == 0 || !search.isBlank()) {
            largeText(c, name, textX, y + 13, titleScale, fg());
            largeText(c, fit(tr(category(m)), gear - textX - 10), textX, y + 32, .95f, muted());
        } else {
            largeText(c, name, textX, y + 22, titleScale, fg());
        }
        addHit(x, y, x + width, y + height, () -> cfg().toggle(m), null, m);
        round(c, gear - 11, y + 15, gear + 11, y + 38, 6,
                faded(SnowStyle.opacity(alpha(sidebarColor()), .34f + .62f * over)));
        SnowStyle.gear(c, gear, y + 26, faded(SnowStyle.blend(muted(), crystal, over)));
        hit(gear - 11, y + 14, gear + 11, y + 39, () -> openModule(m));

        int track = faded(SnowStyle.blend(alpha(border()), crystal, enabled));
        int knob = faded(SnowStyle.blend(muted(), SnowStyle.contrast(crystal), enabled));
        SnowStyle.toggle(c, x + width - 35, y + 19, enabled, track, knob);
    }

    private void openModule(Module m) {
        selected = m;
        binding = null;
        spotifyBinding = null;
        searching = false;
    }

    // MODULE SETTINGS

    private void renderModuleSettings(DrawContext c) {

        Module m = selected;
        var s = cfg().state(m);

        iconButton(c, LEFT, 15, 26, -1, () -> {
            selected = null;
            binding = null;
            spotifyBinding = null;
        });
        int badgeTone = faded(ice());
        SnowStyle.glass(c, LEFT + 36, 13, LEFT + 66, 43, 8,
                faded(alpha(SnowStyle.blend(panel(), ice(), .16f))), faded(alpha(border())), badgeTone);
        if (!SnowIcons.draw(c, m, LEFT + 42, 19, 18, badgeTone)) {
            int categoryIcon = switch (m.type) {
                case HUD -> 2;
                case CAMERA -> 3;
                case VISUAL, OTHER -> 1;
            };
            navigationGlyph(c, categoryIcon, LEFT + 51, 28, badgeTone);
        }
        largeText(c, fit(tr(m.name), 280 * BODY_TEXT_SCALE / 1.54f), LEFT + 77, 15, 1.54f, fg());
        label(c, tr(category(m)), LEFT + 77, 33, muted());

        switchRow(c, LEFT, 59, RIGHT - LEFT,
                tr("Enabled"),
                cfg().enabled(m),
                () -> cfg().toggle(m));

        if (m.type == Type.HUD) {

            section(c, tr("HUD Settings"), 99);

            slider(c, tr("Scale"), 120,
                    s.scale, 50, 200,
                    n -> s.scale = n, "%");

            slider(c, tr("HUD background"), 154,
                    s.opacity, 0, 100,
                    n -> s.opacity = n, "%");

            if (m == Module.ARMOR) {

                section(c, tr("Durability"), 201);

                button(c, LEFT, 225, 397, 250,
                        tr("Percent"), s.percent,
                        () -> {
                            s.percent = true;
                            cfg().save();
                        });

                button(c, 403, 225, RIGHT, 250,
                        tr("Uses"), !s.percent,
                        () -> {
                            s.percent = false;
                            cfg().save();
                        });
            }
            renderHudOptions(c, m, s);
        } else {
            renderVisualOptions(c, m, s);
        }

        section(c, tr("Keybind"), 306);

        String key = binding == m

                ? tr("Press a key or scroll")

                : tr(SnowBinds.name(s.bind));

        button(c, LEFT, 329, 574, 354,
                key, binding == m,
                () -> {
                    binding = m;
                    spotifyBinding = null;
                    guiBinding = false;
                });

        button(c, 580, 329, RIGHT, 354,
                "X", false,
                () -> {
                    SnowBinds.assign(m, -1);
                    binding = null;
                    spotifyBinding = null;
                });
    }

    private void renderSpotifyBinding(DrawContext context, SnowSpotify.Action action, String name, int y) {
        boolean capturing = spotifyBinding == action;
        label(context, tr(name), LEFT + 7, y + 10, fg());
        String keyName = capturing
                ? tr(spotifyBindConflict ? "Key used by GUI" : "Press a key or scroll")
                : tr(SnowBinds.name(SnowSpotifyKeys.get(action)));
        button(context, RIGHT - 148, y + 1, RIGHT - 30, y + 25, keyName, capturing, () -> {
            binding = null;
            spotifyBinding = null;
            guiBinding = false;
            searching = false;
            spotifyBinding = action;
            spotifyBindConflict = false;
        });
        button(context, RIGHT - 25, y + 1, RIGHT, y + 25, "×", false, () -> {
            SnowSpotifyKeys.assign(action, -1);
            spotifyBinding = null;
        });
    }

    private void finishSpotifyBinding(int key) {
        if (SnowSpotifyKeys.assign(spotifyBinding, key)) {
            spotifyBinding = null;
        } else {
            spotifyBindConflict = true;
        }
    }

    private void renderHudOptions(DrawContext c, Module module, SnowModules.State settings) {
        switch (module) {
            case SPOTIFY -> {
                renderSpotifyBinding(c, SnowSpotify.Action.TOGGLE, "Play / Pause", 182);
                renderSpotifyBinding(c, SnowSpotify.Action.PREVIOUS, "Previous track", 214);
                renderSpotifyBinding(c, SnowSpotify.Action.NEXT, "Next track", 246);
                option(c, "Show lyrics (LRCLIB)", 275, settings.d, n -> settings.d = n);
            }
            case BETTER_CPS -> {
                slider(c, tr("Window"), 188, settings.a, 250, 2000, n -> settings.a = n, " ms");
                cycle(c, "Buttons", 222, settings.b, n -> settings.b = n, "Both", "Left", "Right");
                option(c, "Show peak", 256, settings.c, n -> settings.c = n);
            }
            case COMBO -> {
                slider(c, tr("Reset after"), 188, settings.a, 500, 4000, n -> settings.a = n, " ms");
                option(c, "Hide when idle", 222, settings.b, n -> settings.b = n);
            }
            case HIT_MARKER -> {
                slider(c, tr("Duration"), 188, settings.a, 100, 1000, n -> settings.a = n, " ms");
                slider(c, tr("Marker size"), 222, settings.b, 4, 20, n -> settings.b = n, "");
                colorRow(c, "Color", 256, settings.color, n -> settings.color = n);
            }
            case PEARL_COOLDOWN -> {
                option(c, "Show ready", 188, settings.a, n -> settings.a = n);
                option(c, "Progress bar", 222, settings.b, n -> settings.b = n);
            }
            case TOTEM_COUNT ->
                    option(c, "Hide when empty", 188, settings.a, n -> settings.a = n);
            case ARROW_COUNT -> {
                option(c, "Include special arrows", 188, settings.a, n -> settings.a = n);
                option(c, "Hide when empty", 222, settings.b, n -> settings.b = n);
            }
            case BOW_CHARGE -> {
                cycle(c, "Display", 188, settings.a, n -> settings.a = n, "Both", "Bar", "Percent");
                option(c, "Hide when idle", 222, settings.b, n -> settings.b = n);
            }
            case TARGET_HUD -> {
                int columnWidth = (RIGHT - LEFT - 8) / 2;
                int rightColumn = RIGHT - columnWidth;
                slider(c, LEFT, LEFT + columnWidth, tr("Hold time"), 188,
                        settings.a, 500, 8000, n -> settings.a = n, " ms");
                slider(c, rightColumn, RIGHT, tr("Marker range"), 188,
                        settings.markerRange, 2, 8, n -> settings.markerRange = n, " m");
                option(c, LEFT, columnWidth, "Health bar", 218, settings.b, n -> settings.b = n);
                option(c, rightColumn, columnWidth, "Include mobs", 218, settings.c, n -> settings.c = n);

                String shape = switch (settings.d) {
                    case 1 -> tr("Circle");
                    case 2 -> tr("Square");
                    case 3 -> tr("Triangle");
                    default -> tr("Off");
                };
                button(c, LEFT, 248, LEFT + columnWidth, 275,
                        tr("Target marker") + " · " + shape, settings.d != 0, () -> {
                            settings.d = (settings.d + 1) % 4;
                            cfg().save();
                        });
                colorRow(c, rightColumn, RIGHT, "Marker color", 248, settings.color, n -> settings.color = n);
                slider(c, LEFT, LEFT + columnWidth, tr("Marker speed"), 278,
                        settings.markerSpeed, 20, 250, n -> settings.markerSpeed = n, "%");
                slider(c, rightColumn, RIGHT, tr("Marker size"), 278,
                        settings.markerSize, 30, 200, n -> settings.markerSize = n, "%");
            }
            case PING_GRAPH -> {
                slider(c, tr("Samples"), 188, settings.a, 5, 60, n -> settings.a = n, "");
                option(c, "Show graph", 222, settings.b, n -> settings.b = n);
            }
            case DIRECTION -> {
                option(c, "Show degrees", 188, settings.a, n -> {
                    settings.a = n;
                    if (n == 0) settings.b = 1;
                });
                option(c, "Show cardinal direction", 222, settings.b, n -> {
                    settings.b = n;
                    if (n == 0) settings.a = 1;
                });
            }

            case ITEM_COUNT -> {
                section(c, tr("Tracked items"), 187);
                itemOption(c, settings, "Pearls", 1, LEFT, 204);
                itemOption(c, settings, "Golden apples", 2, 403, 204);
                itemOption(c, settings, "Enchanted apples", 4, LEFT, 235);
                itemOption(c, settings, "Totems", 8, 403, 235);
                itemOption(c, settings, "Arrows", 16, LEFT, 266);
                option(c, 403, 219, "Hide when empty", 266, settings.b, n -> settings.b = n);
            }
            default -> {}
        }
    }


    private void itemOption(DrawContext c, SnowModules.State settings, String name, int bit, int x, int y) {
        int checked = (settings.a & bit) != 0 ? 1 : 0;
        option(c, x, 219, name, y, checked, n -> {
            int selectedItems = n == 1 ? settings.a | bit : settings.a & ~bit;
            if (selectedItems != 0) settings.a = selectedItems;
        });
    }

    private void option(DrawContext c, String name, int y, int value, IntConsumer setter) {
        option(c, LEFT, RIGHT - LEFT, name, y, value, setter);
    }

    private void option(DrawContext c, int x, int width, String name, int y, int value, IntConsumer setter) {
        switchRow(c, x, y, width, tr(name), value != 0, () -> {
            setter.accept(value == 0 ? 1 : 0);
            cfg().save();
        });
    }

    private void cycle(DrawContext c, String name, int y, int value, IntConsumer setter, String... choices) {
        int index = Math.floorMod(value, choices.length);
        button(c, LEFT, y, RIGHT, y + 27, tr(name) + " · " + tr(choices[index]), false, () -> {
            setter.accept((index + 1) % choices.length);
            cfg().save();
        });
    }

    private void renderVisualOptions(
            DrawContext c,
            Module m,
            SnowModules.State s
    ) {

        switch (m) {

            case TOGGLE_SPRINT, TOGGLE_SNEAK ->
                    option(c, "Reset in menus", 120, s.a, n -> s.a = n);

            case FULLBRIGHT ->
                    slider(c, tr("Strength"), 120, s.a, 0, 100, n -> s.a = n, "%");

            case DAMAGE_TINT -> {
                slider(c, tr("Strength"), 120, s.a, 5, 90, n -> s.a = n, "%");
                slider(c, tr("Duration"), 154, s.b, 100, 1200, n -> s.b = n, " ms");
                colorRow(c, "Color", 188, s.color, n -> s.color = n);
            }

            case GLOW -> {
                colorRow(c, "Color", 120, cfg().glowColor, n -> cfg().glowColor = n);
                label(c, tr("Visible target only"), LEFT, 164, muted());
            }

            case PARTICLES -> {
                slider(c, tr("Particle count"), 120, s.a, 4, 32, n -> s.a = n, "");
                cycle(c, "Particle shape", 154, s.b, n -> s.b = n, "Mixed", "Snowflakes", "Pluses");
                colorRow(c, "Snowflake color", 188, s.color, n -> s.color = n);
                colorRow(c, "Plus color", 222, s.color2, n -> s.color2 = n);
            }

            case TRAILS -> {
                slider(c, tr("Trail lifetime"), 120, s.a, 300, 2500, n -> s.a = n, " ms");
                slider(c, tr("Trail width"), 154, s.b, 4, 36, n -> s.b = n, "%");
                slider(c, tr("Trail height"), 188, s.c, 20, 200, n -> s.c = n, " cm");
                colorRow(c, "Trail color", 222, s.color, n -> s.color = n);
            }
            case CHINA_HAT -> {
                slider(c, tr("Hat size"), 120, s.a, 50, 200, n -> s.a = n, "%");
                colorRow(c, "Hat color", 154, s.color, n -> s.color = n);
                slider(c, tr("Opacity"), 188, s.opacity, 0, 100, n -> s.opacity = n, "%");
            }
            case CROSSHAIR -> {

                section(c, tr("Shape"), 99);

                slider(c, tr("Length"), 120,
                        s.a, 1, 20, n -> s.a = n, "");

                slider(c, tr("Gap"), 154,
                        s.b, 0, 15, n -> s.b = n, "");

                slider(c, tr("Thickness"), 188,
                        s.c, 1, 5, n -> s.c = n, "");

                button(c, LEFT, 223, RIGHT, 246,
                        tr("Shape") + ": "
                                + new String[]{"+", "•", "○"}[
                                Math.floorMod(s.d, 3)],
                        false,
                        () -> {
                            s.d = Math.floorMod(s.d + 1, 3);
                            cfg().save();
                        });

                colorRow(c, "Color", 257, s.color, n -> s.color = n);
            }
            case FOV -> {

                section(c, "FOV", 99);

                slider(c, "FOV", 120,
                        s.a, 30, 200, n -> s.a = n, "");

                int fov = SnowConfig.clamp(s.a, 30, 200);

                float actual = fov <= 150
                        ? fov : 150 + (fov - 150) * .5f;

                label(c, tr("Actual FOV") + ": "
                                + actual + "°",
                        LEFT, 163, muted());
            }

            case ASPECT -> {

                section(c, tr("Aspect Ratio"), 99);

                int[] values = {100, 133, 178, 233};

                String[] names = {
                        "1:1", "4:3", "16:9", "21:9"
                };

                for (int i = 0; i < 4; i++) {

                    final int v = values[i];

                    int x = LEFT + i * 112;

                    button(c, x, 122, x + 108, 147,
                            names[i], s.a == v,
                            () -> {
                                s.a = v;
                                cfg().save();
                            });
                }

                slider(c, tr("Custom"), 164,
                        s.a, 75, 300, n -> s.a = n, "%");

                label(c, String.format(
                                Locale.ROOT,
                                "%.2f : 1", s.a / 100f
                        ),
                        LEFT, 209, muted());

                int pw = Math.round(
                        25 * SnowConfig.clamp(
                                s.a, 75, 300
                        ) / 100f
                );

                round(c, 400 - pw / 2, 236,
                        400 + pw / 2, 261,
                        4, accent());
            }

            case VIEWMODEL -> {

                section(c, tr("Scale"), 99);

                slider(c, tr("Scale"), 120,
                        s.a, 30, 150, n -> s.a = n, "%");

                slider(c, "X", 154,
                        s.b, -50, 50, n -> s.b = n, "");

                slider(c, "Y", 188,
                        s.c, -50, 50, n -> s.c = n, "");

                slider(c, tr("Rotation"), 222,
                        s.d, -45, 45, n -> s.d = n, "°");
            }

            case ZOOM -> {

                section(c, tr("Scale"), 99);

                slider(c, tr("Scale"), 120,
                        s.a, 10, 90, n -> s.a = n, "%");
            }

            case FIRE -> {

                section(c, tr("Scale"), 99);

                slider(c, tr("Scale"), 120,
                        s.a, 0, 100, n -> s.a = n, "%");
            }

            case CHAT -> {

                section(c, tr("Appearance"), 99);

                slider(c, tr("Scale"), 120,
                        s.a, 50, 100, n -> s.a = n, "%");

                slider(c, tr("Opacity"), 154,
                        s.b, 0, 100, n -> s.b = n, "%");
            }

            case FREELOOK ->

                    label(c, tr(SnowBinds.name(s.bind)),
                            LEFT, 111, muted());

            default -> {}
        }
    }

    // SETTINGS

    private void renderSettings(DrawContext c) {
        pageHeading(c, tr("Settings"));
        int center = (LEFT + RIGHT) / 2;
        int splitLeft = center - 4;
        int splitRight = center + 4;
        int splitWidth = splitLeft - LEFT;

        section(c, tr("Appearance"), 61);
        button(c, LEFT, 77, splitLeft, 103, tr("Black"), cfg().dark, () -> applyGuiMode(true));
        button(c, splitRight, 77, RIGHT, 103, tr("White"), !cfg().dark, () -> applyGuiMode(false));
        colorRow(c, LEFT, splitLeft, "GUI color", 111, cfg().guiColor, n -> cfg().guiColor = n);
        colorRow(c, splitRight, RIGHT, "Accent color", 111, cfg().accentColor, n -> cfg().accentColor = n);
        slider(c, LEFT, splitLeft, tr("GUI scale"), 145, cfg().guiScale, 50, 150, n -> cfg().guiScale = n, "%");
        slider(c, splitRight, RIGHT, tr("GUI transparency"), 145, cfg().guiTransparency, 0, 100,
                n -> cfg().guiTransparency = n, "%");

        section(c, tr("HUD Settings"), 189);
        colorRow(c, LEFT, splitLeft, "HUD color", 205, cfg().hudColor, n -> cfg().hudColor = n);
        int presets = (splitRight + RIGHT) / 2;
        button(c, splitRight, 205, presets - 3, 232, tr("White"), cfg().hudColor == 0xFFFFFF,
                () -> applyHudPreset(0xFFFFFF));
        button(c, presets + 3, 205, RIGHT, 232, tr("Black"), cfg().hudColor == 0x000000,
                () -> applyHudPreset(0x000000));
        switchRow(c, LEFT, 239, splitWidth, tr("HUD Snap"), cfg().hudSnap, () -> {
            cfg().hudSnap = !cfg().hudSnap;
            cfg().save();
        });
        button(c, splitRight, 239, RIGHT, 266, tr("Edit HUD"), false, this::openHudEditor);

        section(c, tr("Keybind"), 281);
        button(c, LEFT, 297, splitLeft, 325, tr("Language") + " · " + SnowLanguage.displayName(cfg().language),
                false, () -> {
                    int i = SnowLanguage.CODES.indexOf(SnowLanguage.normalize(cfg().language));
                    cfg().language = SnowLanguage.CODES.get((i + 1) % SnowLanguage.CODES.size());
                    cfg().save();
                });
        button(c, splitRight, 297, RIGHT, 325,
                tr("GUI keybind") + " · " + (guiBinding ? tr("Press a key...") : tr(SnowBinds.name(cfg().guiKey))),
                guiBinding, () -> {
                    binding = null;
                    spotifyBinding = null;
                    guiBinding = true;
                });

        button(c, LEFT, 338, RIGHT, 366, tr("Reset GUI settings"), false, () -> {
            cfg().resetGui();
            guiBinding = false;
        });
    }

    private void openHudEditor() {
        cfg().save();
        MinecraftClient.getInstance().setScreen(new ChatScreen("", false));
    }

    private void applyGuiMode(boolean dark) {
        cfg().dark = dark;
        cfg().save();
    }

    private void applyHudPreset(int color) {
        cfg().hudColor = color;
        cfg().save();
    }

    // PROFILES

    private void renderProfiles(DrawContext c) {
        pageHeading(c, tr("Profiles"));
        button(c, RIGHT - 153, 15, RIGHT - 34, 40, tr("New profile"), true,
                () -> openProfileDialog(ProfileAction.CREATE, null));

        int start = profilePage * PROFILES_PER_PAGE;
        int end = Math.min(start + PROFILES_PER_PAGE, profiles.size());
        if (profiles.isEmpty()) {
            SnowStyle.glass(c, LEFT, 59, RIGHT, 151, 11, faded(alpha(panel())), faded(alpha(border())), faded(ice()));
            navigationGlyph(c, 4, (LEFT + RIGHT) / 2, 89, faded(ice()));
            String empty = tr("No profiles yet");
            label(c, empty, (LEFT + RIGHT - textWidth(empty)) / 2f, 112, muted());
        }

        for (int index = start; index < end; index++) {
            SnowProfiles.Profile profile = profiles.get(index);
            int y = 59 + (index - start) * 62;
            SnowStyle.glass(c, LEFT, y, RIGHT, y + 54, 10,
                    faded(alpha(panel())), faded(alpha(border())), faded(ice()));
            SnowStyle.surface(c, LEFT + 9, y + 9, LEFT + 45, y + 45, 8,
                    faded(alpha(SnowStyle.blend(sidebarColor(), ice(), .21f))),
                    faded(alpha(SnowStyle.blend(sidebarColor(), ice(), .07f))));
            navigationGlyph(c, 4, LEFT + 27, y + 27, faded(ice()));
            largeText(c, fit(profile.name(), 298 * BODY_TEXT_SCALE / 1.30f), LEFT + 55, y + 9, 1.30f, fg());

            int rowTop = y + 27;
            int rowBottom = y + 48;
            int actionStart = LEFT + 55;
            button(c, actionStart, rowTop, actionStart + 78, rowBottom,
                    tr("Use"), false, () -> useProfile(profile));
            button(c, actionStart + 84, rowTop, actionStart + 202, rowBottom,
                    tr("Save Profile"), true, () -> saveProfile(profile));
            button(c, actionStart + 208, rowTop, actionStart + 289, rowBottom,
                    tr("Edit"), false, () -> openProfileDialog(ProfileAction.EDIT, profile));
            button(c, actionStart + 295, rowTop, RIGHT - 9, rowBottom,
                    tr("Delete"), false, () -> openProfileDialog(ProfileAction.DELETE, profile));
        }

        if (profilePageCount() > 1) {
            iconButton(c, LEFT, 320, 25, -1, () -> profilePage = Math.max(0, profilePage - 1));
            String position = (profilePage + 1) + " / " + profilePageCount();
            label(c, position, LEFT + 58 - textWidth(position) * .5f, 328, muted());
            iconButton(c, LEFT + 91, 320, 25, 1,
                    () -> profilePage = Math.min(profilePageCount() - 1, profilePage + 1));
        }
        if (!notice.isEmpty()) {
            label(c, fit(notice, RIGHT - LEFT), LEFT, 354, muted());
        }
    }

    private void refreshProfiles() {
        profiles = SnowProfiles.list();
        profilePage = Math.min(profilePage, profilePageCount() - 1);
    }

    private int profilePageCount() {
        return Math.max(1, (profiles.size() + PROFILES_PER_PAGE - 1) / PROFILES_PER_PAGE);
    }

    private void useProfile(SnowProfiles.Profile profile) {
        if (SnowProfiles.use(profile.id())) {
            SnowVisualsClient.consumeBind();
            notice = tr("Profile loaded");
        } else {
            notice = tr("Profile action failed");
        }
    }

    private void saveProfile(SnowProfiles.Profile profile) {
        notice = tr(SnowProfiles.update(profile.id(), cfg()) ? "Profile saved" : "Profile action failed");
    }

    private void openProfileDialog(ProfileAction action, SnowProfiles.Profile profile) {
        hits.clear();
        profileDialog = new ProfileDialog(action, profile);
    }

    private void closeProfileDialog() {
        profileDialog = null;
        hits.clear();
    }

    private void renderProfileDialog(DrawContext c) {
        ProfileDialog dialog = profileDialog;
        String title = switch (dialog.action) {
            case CREATE -> "New profile";
            case EDIT -> "Edit";
            case DELETE -> "Delete profile";
        };

        round(c, DIALOG_LEFT - 5, 111, DIALOG_RIGHT + 5, 277, 17, 0x7203060E);
        SnowStyle.glass(c, DIALOG_LEFT, 108, DIALOG_RIGHT, 269, 14,
                sidebarColor(), SnowStyle.blend(border(), ice(), .33f), ice());
        largeText(c, tr(title), DIALOG_LEFT + 16, 123, 1.52f, fg());
        round(c, DIALOG_LEFT + 15, 146, DIALOG_RIGHT - 15, 147, 0, border());

        if (dialog.action == ProfileAction.DELETE) {
            label(c, fit(dialog.profile.name(), DIALOG_RIGHT - DIALOG_LEFT - 30),
                    DIALOG_LEFT + 15, 169, fg());
        } else {
            label(c, tr("Profile name"), DIALOG_LEFT + 15, 151, muted());
            renderProfileName(c, dialog);
        }

        if (dialog.failed) {
            label(c, fit(tr("Profile action failed"), DIALOG_RIGHT - DIALOG_LEFT - 30),
                    DIALOG_LEFT + 15, 203, fg());
        }

        button(c, DIALOG_LEFT + 15, 227, DIALOG_LEFT + 113, 254, tr("Cancel"), false,
                this::closeProfileDialog);
        if (dialog.action == ProfileAction.EDIT) {
            button(c, DIALOG_LEFT + 119, 227, DIALOG_LEFT + 222, 254,
                    tr("Rename"), false, () -> submitProfileDialog(false));
        }
        String primary = switch (dialog.action) {
            case CREATE -> "Save Profile";
            case EDIT -> "Save Profile";
            case DELETE -> "Delete";
        };
        button(c, DIALOG_LEFT + 228, 227, DIALOG_RIGHT - 15, 254, tr(primary),
                dialog.action != ProfileAction.DELETE, () -> submitProfileDialog(dialog.action == ProfileAction.EDIT));
    }

    private void renderProfileName(DrawContext c, ProfileDialog dialog) {
        int left = DIALOG_LEFT + 15;
        int right = DIALOG_RIGHT - 15;
        SnowStyle.glass(c, left, 164, right, 192, 7, panel(), ice(), ice());

        int textLeft = left + 8;
        int textRight = right - 8;
        float beforeCursor = textWidth(dialog.name.substring(0, dialog.cursor));
        float scroll = Math.max(0f, beforeCursor - (textRight - textLeft - 2));
        c.enableScissor(textLeft, 167, textRight, 190);
        try {
            if (dialog.selected) {
                c.fill(textLeft, 170, Math.min(textRight, (int) Math.ceil(textLeft + textWidth(dialog.name))),
                        187, SnowStyle.opacity(accent(), 0.34f));
            }
            label(c, dialog.name, textLeft - scroll, 174, fg());
            if (!dialog.selected && System.currentTimeMillis() % 1000 < 500) {
                float cursor = textLeft + beforeCursor - scroll;
                SnowStyle.circle(c, cursor, 179, 1, fg());
            }
        } finally {
            c.disableScissor();
        }
        hit(left, 164, right, 192, () -> {
            dialog.selected = false;
            dialog.cursor = dialog.name.length();
        });
    }

    private void submitProfileDialog(boolean update) {
        ProfileDialog dialog = profileDialog;
        boolean success;
        String message;
        SnowProfiles.Profile created = null;
        switch (dialog.action) {
            case CREATE -> {
                created = SnowProfiles.create(dialog.name, cfg());
                success = created != null;
                message = "Profile saved";
            }
            case EDIT -> {
                success = update
                        ? SnowProfiles.update(dialog.profile.id(), cfg())
                        : SnowProfiles.rename(dialog.profile.id(), dialog.name);
                message = update ? "Profile saved" : "Profile renamed";
            }
            case DELETE -> {
                success = SnowProfiles.delete(dialog.profile.id());
                message = "Profile deleted";
            }
            default -> throw new IllegalStateException();
        }

        if (success) {
            closeProfileDialog();
            refreshProfiles();
            if (created != null) {
                int index = profiles.indexOf(created);
                if (index >= 0) profilePage = index / PROFILES_PER_PAGE;
            }
            notice = tr(message);
        } else {
            dialog.failed = true;
        }
    }

    private void handleProfileDialogKey(KeyInput input, int key) {
        ProfileDialog dialog = profileDialog;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeProfileDialog();
            return;
        }
        if (dialog.action == ProfileAction.DELETE) return;
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submitProfileDialog(false);
            return;
        }

        var keyboard = MinecraftClient.getInstance().keyboard;
        if ((input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> dialog.selected = true;
                case GLFW.GLFW_KEY_C -> {
                    if (dialog.selected) keyboard.setClipboard(dialog.name);
                }
                case GLFW.GLFW_KEY_V -> dialog.insert(keyboard.getClipboard());
                default -> {}
            }
            return;
        }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> dialog.delete(true);
            case GLFW.GLFW_KEY_DELETE -> dialog.delete(false);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                dialog.cursor = switch (key) {
                    case GLFW.GLFW_KEY_HOME -> 0;
                    case GLFW.GLFW_KEY_END -> dialog.name.length();
                    case GLFW.GLFW_KEY_LEFT -> dialog.selected || dialog.cursor == 0
                            ? 0 : dialog.name.offsetByCodePoints(dialog.cursor, -1);
                    default -> dialog.selected || dialog.cursor == dialog.name.length()
                            ? dialog.name.length() : dialog.name.offsetByCodePoints(dialog.cursor, 1);
                };
                dialog.selected = false;
            }
            default -> {}
        }
    }

    // UI COMPONENTS

    private void section(DrawContext c, String name, int y) {
        int crystal = faded(alpha(ice()));
        SnowStyle.circle(c, LEFT + 3, y + 5, 2, crystal);
        label(c, name, LEFT + 13, y, muted());
        int end = LEFT + 20 + (int) Math.ceil(textWidth(name));
        if (end < RIGHT) {
            round(c, end, y + 5, RIGHT, y + 6, 0, faded(SnowStyle.opacity(alpha(border()), .75f)));
        }
    }

    private void switchRow(DrawContext c, int x, int y, int w, String name, boolean checked, Runnable action) {
        float target = checked ? 1f : 0f;
        float progress = control(x, y, Motion.ACTIVE, target, target, 20f);
        int crystal = ice();
        int fill = SnowStyle.blend(controlBackground(x, y, x + w, y + 27), crystal, .09f * progress);
        SnowStyle.glass(c, x, y, x + w, y + 27, 7, faded(alpha(fill)), faded(alpha(border())), faded(crystal));
        label(c, fit(name, w - 49), x + 10, y + 10, fg());

        int track = faded(SnowStyle.blend(alpha(border()), crystal, progress));
        int knob = faded(SnowStyle.blend(muted(), SnowStyle.contrast(crystal), progress));
        SnowStyle.toggle(c, x + w - 35, y + 6, progress, track, knob);
        hit(x, y, x + w, y + 27, action);
    }

    private void slider(
            DrawContext c,
            String name,
            int y,
            int value,
            int min,
            int max,
            IntConsumer setter,
            String suffix
    ) {

        slider(c, LEFT, RIGHT, name, y, value, min, max, setter, suffix);
    }

    private void slider(DrawContext c, int x1, int x2, String name, int y,
                        int value, int min, int max, IntConsumer setter, String suffix) {
        boolean fullWidth = x1 == LEFT && x2 == RIGHT;
        int a = x1 + (x2 - x1) * (fullWidth ? 52 : 61) / 100;
        int b = x2 - 12;
        String number = value + suffix;

        SnowStyle.glass(c, x1, y, x2, y + 27, 7,
                faded(alpha(controlBackground(x1, y, x2, y + 27))), faded(alpha(border())), faded(ice()));
        label(c, fit(name, fullWidth ? 145 : Math.max(1f, a - x1 - 24 - textWidth(number))),
                x1 + 8, y + 10, fg());
        label(c, number,
                a - 13 - textWidth(number),
                y + 10, muted());

        float target = Math.max(0f, Math.min(1f, (value - min) / (float) (max - min)));
        float rate = dragging != null && dragging.row() == y ? 34f : 18f;
        float progress = control(a, y, Motion.SLIDER, target, target, rate);
        SnowStyle.slider(c, a, y + 13, b - a, progress, faded(alpha(border())),
                faded(ice()), faded(fg()));
        addHit(a - 6, y + 2, b + 6, y + 25, null, new Range(a, b, y, min, max, setter), null);
    }

    private void colorRow(DrawContext c, String name, int y, int color, IntConsumer setter) {
        colorRow(c, LEFT, RIGHT, name, y, color, setter);
    }

    private void colorRow(DrawContext c, int x1, int x2, String name, int y, int color, IntConsumer setter) {
        String title = tr(name);
        SnowStyle.glass(c, x1, y, x2, y + 27, 7,
                faded(alpha(controlBackground(x1, y, x2, y + 27))), faded(alpha(border())), faded(ice()));

        String hex = "#" + SnowColorPicker.hexText(color);
        float hexWidth = textWidth(hex);
        label(c, fit(title, Math.max(1, x2 - x1 - hexWidth - 51)), x1 + 8, y + 9, fg());
        label(c, hex, x2 - 37 - hexWidth, y + 9, muted());
        round(c, x2 - 29, y + 4, x2 - 10, y + 23, 4, faded(0xFF000000 | color));
        SnowStyle.frame(c, x2 - 29, y + 4, 19, 19, 4, faded(SnowStyle.opacity(fg(), 0.45f)));

        hit(x1, y, x2, y + 27, () -> {
            binding = null;
            spotifyBinding = null;
            guiBinding = false;
            colorPicker = new SnowColorPicker(title, color, setter);
        });
    }

    private void applyColor() {
        if (colorPicker.commit()) {
            cfg().save();
            colorPicker = null;
        }
    }
    private void updateRange(double x) {

        if (dragging == null) return;

        double p = Math.max(
                0, Math.min(
                        1,
                        (x - dragging.x1())
                                / (dragging.x2() - dragging.x1())
                )
        );

        dragging.setter().accept(
                dragging.min() + (int) Math.round(
                        p * (dragging.max() - dragging.min())
                )
        );
    }

    private void button(DrawContext c, int x1, int y1, int x2, int y2, String name,
                        boolean active, Runnable action) {
        float target = active ? 1f : 0f;
        float progress = control(x1, y1, Motion.ACTIVE, target, target, 18f);
        int crystal = ice();
        int bg = SnowStyle.blend(controlBackground(x1, y1, x2, y2), crystal, .22f * progress);
        int edge = SnowStyle.blend(border(), crystal, .52f * progress);
        SnowStyle.glass(c, x1, y1, x2, y2, 7, faded(alpha(bg)), faded(alpha(edge)), faded(crystal));

        String text = fit(name, x2 - x1 - 12);
        label(c, text, (x1 + x2 - textWidth(text)) / 2f, (y1 + y2 - 8) / 2f, fg());
        hit(x1, y1, x2, y2, action);
    }

    // GUI TEXT

    private float textWidth(String text) {
        return SnowEffects.width(textRenderer, text) * BODY_TEXT_SCALE;
    }

    private String fit(String text, float max) {
        if (textWidth(text) <= max) return text;
        if (textWidth("…") > max) return "";

        int end = text.length();
        do {
            end = text.offsetByCodePoints(end, -1);
        } while (end > 0 && textWidth(text.substring(0, end) + "…") > max);
        return text.substring(0, end) + "…";
    }

    private void label(DrawContext c, String text, float x, float y, int color) {
        SnowEffects.text(c, textRenderer, text, x, y, BODY_TEXT_SCALE, faded(color));
    }

    private void largeText(DrawContext c, String text, float x, float y, float size, int color) {
        SnowEffects.text(c, textRenderer, text, x, y, size, faded(color));
    }

    private int faded(int color) {
        return SnowStyle.opacity(color, contentOpacity);
    }

    private void round(
            DrawContext c,
            int a, int b,
            int e, int f,
            int r, int color
    ) {

        SnowStyle.round(c, a, b, e, f, r, faded(color));
    }

    private boolean inside(
            int a, int b,
            int e, int f
    ) {

        return SnowStyle.inside(mx, my - contentOffset, a, b, e, f);
    }

    private void hit(
            int a, int b,
            int e, int f,
            Runnable action
    ) {

        addHit(a, b, e, f, action, null, null);
    }

    private void addHit(int x1, int y1, int x2, int y2, Runnable action, Range range, Module module) {
        hits.add(new Hit(x1, y1 + contentOffset, x2, y2 + contentOffset, action, range, module));
    }

    // MOUSE INPUT

    @Override
    public boolean mouseClicked(
            Click click,
            boolean doubled
    ) {

        if (colorPicker != null) {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                double x = (click.x() - ox) / scale;
                double y = (click.y() - oy) / scale;
                frozenScale = scale;
                switch (colorPicker.click(x, y)) {
                    case SAVE -> applyColor();
                    case CANCEL -> colorPicker = null;
                    case EDIT -> {}
                }
            }
            return true;
        }
        if (profileDialog != null && click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (spotifyBinding != null && click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            finishSpotifyBinding(SnowBinds.MIDDLE);
            return true;
        }
        if (binding != null
                && click.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {

            SnowBinds.assign(binding, SnowBinds.MIDDLE);
            binding = null;
            spotifyBinding = null;

            return true;
        }

        double x = (click.x() - ox) / scale;
        double y = (click.y() - oy) / scale;

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {

            for (Hit h : hits) {

                if (h.module() != null
                        && SnowStyle.inside(
                        x, y,
                        h.x1(), h.y1(),
                        h.x2(), h.y2())) {

                    openModule(h.module());

                    return true;
                }
            }
        }

        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, doubled);
        }

        searching = false;

        for (int i = hits.size() - 1; i >= 0; i--) {

            Hit h = hits.get(i);

            if (!SnowStyle.inside(
                    x, y,
                    h.x1(), h.y1(),
                    h.x2(), h.y2())) {

                continue;
            }

            if (h.range() != null) {

                frozenScale = scale;
                dragging = h.range();

                updateRange(x);

            } else if (h.action() != null) {

                h.action().run();
            }

            return true;
        }

        return profileDialog != null || super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(
            double x,
            double y,
            double horizontal,
            double vertical
    ) {

        if (colorPicker != null || profileDialog != null) return true;
        if (vertical == 0) return false;
        if (spotifyBinding != null) {
            finishSpotifyBinding(vertical > 0 ? SnowBinds.UP : SnowBinds.DOWN);
            return true;
        }

        if (tab == 4) {
            profilePage = SnowConfig.clamp(profilePage + (vertical > 0 ? -1 : 1), 0, profilePageCount() - 1);
            return true;
        }

        if (binding != null) {

            SnowBinds.assign(
                    binding,
                    vertical > 0
                            ? SnowBinds.UP
                            : SnowBinds.DOWN
            );

            binding = null;
            spotifyBinding = null;

            return true;
        }

        if (guiBinding) return true;

        if (selected == null && tab < 4) {

            int pages = Math.max(
                    1, (visibleModules().size() + MODULES_PER_PAGE - 1) / MODULES_PER_PAGE
            );

            page = SnowConfig.clamp(
                    page + (vertical > 0 ? -1 : 1),
                    0, pages - 1
            );

            return true;
        }

        return super.mouseScrolled(
                x, y, horizontal, vertical
        );
    }

    @Override
    public boolean mouseDragged(
            Click click,
            double dx,
            double dy
    ) {

        if (colorPicker != null) {
            colorPicker.dragTo((click.x() - ox) / scale, (click.y() - oy) / scale);
            return true;
        }
        if (dragging != null) {

            updateRange((click.x() - ox) / scale);

            return true;
        }

        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {

        if (colorPicker != null) {
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                colorPicker.release((click.x() - ox) / scale, (click.y() - oy) / scale);
            }
            return true;
        }
        if (dragging != null
                && click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {

            updateRange((click.x() - ox) / scale);

            dragging = null;

            cfg().save();

            return true;
        }

        return super.mouseReleased(click);
    }

    // KEYBOARD INPUT

    @Override
    public boolean charTyped(CharInput input) {
        if (colorPicker != null) {
            colorPicker.typeHex(input.codepoint());
            return true;
        }

        if (profileDialog != null) {
            if (profileDialog.action != ProfileAction.DELETE && Character.isValidCodePoint(input.codepoint())) {
                profileDialog.insert(Character.toString(input.codepoint()));
            }
            return true;
        }

        if (searching && binding == null && !guiBinding) {

            int cp = input.codepoint();

            if (Character.isValidCodePoint(cp)
                    && !Character.isISOControl(cp)
                    && search.length() < 60) {

                search += new String(
                        Character.toChars(cp)
                );

                page = 0;
            }

            return true;
        }

        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {

        int key = input.getKeycode();
        if (colorPicker != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                colorPicker = null;
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                applyColor();
            } else {
                colorPicker.editHex(input, MinecraftClient.getInstance().keyboard);
            }
            return true;
        }

        if (profileDialog != null) {
            handleProfileDialogKey(input, key);
            return true;
        }

        if (spotifyBinding != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                spotifyBinding = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                finishSpotifyBinding(-1);
            } else if (SnowConfig.validKey(key)) {
                finishSpotifyBinding(key);
            }
            return true;
        }
        if (binding != null || guiBinding) {

            if (key == GLFW.GLFW_KEY_ESCAPE) {

                binding = null;
                spotifyBinding = null;
                guiBinding = false;

                return true;
            }

            if (binding != null
                    && key == GLFW.GLFW_KEY_BACKSPACE) {

                SnowBinds.assign(binding, -1);
                binding = null;
                spotifyBinding = null;

                return true;
            }

            if (SnowConfig.validKey(key)) {

                if (guiBinding) {

                    cfg().guiKey = key;
                    SnowSpotifyKeys.clear(cfg(), key);

                    for (Module m : Module.values()) {

                        if (cfg().state(m).bind == key) {
                            SnowBinds.assign(m, -1);
                        }
                    }

                    cfg().save();
                }

                if (binding != null) {
                    SnowBinds.assign(binding, key);
                }

                binding = null;
                spotifyBinding = null;
                guiBinding = false;

                SnowVisualsClient.consumeBind();
            }

            return true;
        }

        if (searching) {

            if (key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_ENTER) {

                searching = false;

            } else if (key == GLFW.GLFW_KEY_BACKSPACE
                    && !search.isEmpty()) {

                search = search.substring(
                        0,
                        search.offsetByCodePoints(
                                search.length(), -1
                        )
                );

                page = 0;
            }

            return true;
        }

        if (key == cfg().guiKey) {

            SnowVisualsClient.consumeBind();

            close();

            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE
                && selected != null) {

            selected = null;

            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {

        dragging = null;
        binding = null;
        spotifyBinding = null;
        colorPicker = null;
        profileDialog = null;

        cfg().save();

        super.removed();
    }
}
