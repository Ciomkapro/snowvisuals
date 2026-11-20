package pl.snowvisuals.gui;

import java.util.Map;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import pl.snowvisuals.SnowModules.Module;

public final class SnowIcons {

    private static final int RES = 256;
    private static final Identifier WIFI = texture("wifi");
    private static final Map<Module, Identifier> ICONS = Map.ofEntries(
            Map.entry(Module.PING, WIFI),
            Map.entry(Module.PING_GRAPH, WIFI),
            Map.entry(Module.FPS, texture("fps")),
            Map.entry(Module.POTIONS, texture("potions")),
            Map.entry(Module.COORDS, texture("coords")),
            Map.entry(Module.KEYS, texture("keystrokes")),
            Map.entry(Module.TOGGLE_SPRINT, texture("sprint")),
            Map.entry(Module.TOGGLE_SNEAK, texture("sneak")),
            Map.entry(Module.BETTER_CPS, texture("cps")),
            Map.entry(Module.COMBO, texture("combo")),
            Map.entry(Module.HIT_MARKER, texture("hit_marker")),
            Map.entry(Module.DAMAGE_TINT, texture("damage_tint")),
            Map.entry(Module.PEARL_COOLDOWN, texture("pearl_cooldown")),
            Map.entry(Module.TOTEM_COUNT, texture("totem")),
            Map.entry(Module.ARROW_COUNT, texture("arrow")),
            Map.entry(Module.BOW_CHARGE, texture("bow")),
            Map.entry(Module.TARGET_HUD, texture("target")),
            Map.entry(Module.DIRECTION, texture("direction")),
            Map.entry(Module.FULLBRIGHT, texture("fullbright")),
            Map.entry(Module.ITEM_COUNT, texture("inventory_count")),
            Map.entry(Module.CHINA_HAT, texture("china_hat")),
            Map.entry(Module.SPOTIFY, texture("music"))
    );

    private SnowIcons() {}

    private static Identifier texture(String name) {
        return Identifier.of("snowvisuals", "textures/modules/" + name + ".png");
    }

    public static boolean has(Module module) {
        return ICONS.containsKey(module);
    }

    public static boolean draw(
            DrawContext context,
            Module module,
            int x,
            int y,
            int size,
            int color
    ) {
        Identifier texture = ICONS.get(module);
        if (texture == null || size <= 0) {
            return false;
        }

        if ((color >>> 24) != 0) {
            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    x, y,
                    0f, 0f,
                    size, size,
                    RES, RES,
                    RES, RES,
                    color
            );
        }

        return true;
    }
}
