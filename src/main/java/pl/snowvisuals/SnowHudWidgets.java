package pl.snowvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import pl.snowvisuals.gui.SnowIcons;
import pl.snowvisuals.gui.SnowStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static pl.snowvisuals.SnowHud.accent;
import static pl.snowvisuals.SnowHud.border;
import static pl.snowvisuals.SnowHud.fg;
import static pl.snowvisuals.SnowHud.fit;
import static pl.snowvisuals.SnowHud.label;
import static pl.snowvisuals.SnowHud.mc;
import static pl.snowvisuals.SnowHud.muted;
import static pl.snowvisuals.SnowHud.palette;
import static pl.snowvisuals.SnowHud.state;
import static pl.snowvisuals.SnowHud.textWidth;
import static pl.snowvisuals.SnowWords.tr;

final class SnowHudWidgets {
    private static final String[] HEADINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final Item[] TRACKED = {
            Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.ARROW
    };
    private static float drawnBowCharge;
    private static float drawnHealth;
    private static float targetVisibility;
    private static TargetSnapshot drawnTarget;
    private static ClientWorld targetWorld;
    private static ClientPlayerEntity targetPlayer;
    private static SnowConfig targetConfig;

    record Card(int width, int height, float opacity, float offsetY, Consumer<DrawContext> renderer) {
        Card(int width, int height, Consumer<DrawContext> renderer) {
            this(width, height, 1f, 0f, renderer);
        }

        void paint(DrawContext context) {
            renderer.accept(context);
        }
    }

    private record ItemCount(Item item, int count) {}

    private record TargetSnapshot(UUID id, SkinTextures skin, String name,
                                  float health, float maxHealth, float absorption) {
        float healthRatio() {
            return health / maxHealth;
        }
    }

    private SnowHudWidgets() {}

    static Card create(SnowModules.Module module, boolean editing, float tickProgress, float dt) {
        var settings = state(module);
        return switch (module) {
            case BETTER_CPS -> cps(module, settings);
            case COMBO -> combo(module, settings, editing);
            case HIT_MARKER -> marker(settings, editing);
            case PEARL_COOLDOWN -> pearl(settings, editing, tickProgress);
            case TOTEM_COUNT -> count(Items.TOTEM_OF_UNDYING, false, settings.a != 0, editing);
            case ARROW_COUNT -> count(Items.ARROW, settings.a != 0, settings.b != 0, editing);
            case BOW_CHARGE -> bow(settings, editing, dt);
            case TARGET_HUD -> target(settings, editing, dt);
            case PING_GRAPH -> ping(module, settings);
            case DIRECTION -> direction(module, settings);
            case ITEM_COUNT -> items(settings, editing);
            case SPOTIFY -> SnowSpotifyHud.create(settings.d != 0);
            default -> null;
        };
    }

    private static Card cps(SnowModules.Module module, SnowModules.State settings) {
        int mode = SnowConfig.clamp(settings.b, 0, 2);
        int left = mode == 2 ? 0 : SnowQol.cps(false, settings.a);
        int right = mode == 1 ? 0 : SnowQol.cps(true, settings.a);
        String current = buttons(mode, left, right);
        if (settings.c == 0) return compact(module, current);

        int peakLeft = mode == 2 ? 0 : SnowQol.peakCps(false, settings.a);
        int peakRight = mode == 1 ? 0 : SnowQol.peakCps(true, settings.a);
        String peak = tr("Peak") + "  " + buttons(mode, peakLeft, peakRight);
        int width = Math.max(96, (int) Math.ceil(Math.max(textWidth(current, 1.18f), textWidth(peak, 0.91f))) + 45);
        return new Card(width, 41, c -> {
            drawSymbol(c, module, 6, 8, 22);
            label(c, "CPS", 35, 5, 0.86f, muted());
            label(c, current, 35, 14, 1.18f, fg());
            label(c, peak, 35, 28, 0.91f, muted());
        });
    }

    private static String buttons(int mode, int left, int right) {
        return switch (mode) {
            case 1 -> "L " + left;
            case 2 -> "R " + right;
            default -> "L " + left + "  /  R " + right;
        };
    }

    private static Card combo(SnowModules.Module module, SnowModules.State settings, boolean editing) {
        int combo = SnowQol.combo();
        if (combo == 0 && settings.b != 0 && !editing) {
            return null;
        }
        return compact(module, "×" + (combo == 0 && editing ? 3 : combo));
    }

    private static Card marker(SnowModules.State settings, boolean editing) {
        float visibility = editing ? 1f : SnowQol.hitAlpha();
        int opacity = Math.round(visibility * SnowConfig.clamp(settings.opacity, 0, 100));
        if (opacity <= 0 && !editing) {
            return null;
        }
        int size = SnowConfig.clamp(settings.b, 4, 20);
        int radius = size + 2;
        int markerColor = SnowStyle.alpha(settings.color, editing ? Math.max(45, opacity) : opacity);
        return new Card(radius * 2 + 1, radius * 2 + 1, c -> {
            int gap = Math.max(2, size / 3);
            SnowStyle.line(c, radius - size, radius - size, radius - gap, radius - gap, markerColor);
            SnowStyle.line(c, radius + gap, radius - gap, radius + size, radius - size, markerColor);
            SnowStyle.line(c, radius - size, radius + size, radius - gap, radius + gap, markerColor);
            SnowStyle.line(c, radius + gap, radius + gap, radius + size, radius + size, markerColor);
        });
    }

    private static Card pearl(SnowModules.State settings, boolean editing, float tickProgress) {
        float remaining = SnowQol.pearlCooldown(mc(), tickProgress);
        if (remaining <= 0f && settings.a == 0 && !editing) return null;

        float seconds = SnowQol.pearlSeconds(mc(), tickProgress);
        int tenths = Math.max(1, (int) Math.ceil(seconds * 10f));
        String status = remaining <= 0f ? tr("Ready") : tenths / 10 + "." + tenths % 10 + " s";
        if (settings.b == 0) return itemCard(Items.ENDER_PEARL, status);

        return timedItem(Items.ENDER_PEARL, status, 1f - remaining, true);
    }

    private static Card count(Item item, boolean variants, boolean hideEmpty, boolean editing) {
        int count = SnowQol.itemCount(mc(), item, variants);
        return count == 0 && hideEmpty && !editing ? null : itemCard(item, "×" + count);
    }

    private static Card bow(SnowModules.State settings, boolean editing, float dt) {
        float current = SnowQol.bowCharge(mc());
        if (current < 0f && !editing && settings.b != 0) {
            drawnBowCharge = 0f;
            return null;
        }
        float charge = current < 0f && editing ? .68f : Math.max(0f, current);
        if (current >= 0f || !editing) {
            drawnBowCharge = SnowStyle.approach(drawnBowCharge, charge, 29f, dt);
        }
        int mode = SnowConfig.clamp(settings.a, 0, 2);
        String status = Math.round(charge * 100f) + "%";
        if (mode == 2) return itemCard(Items.BOW, status);

        float progress = current < 0f && editing ? charge : drawnBowCharge;
        return timedItem(Items.BOW, status, progress, mode == 0);
    }

    private static Card timedItem(Item item, String status, float progress, boolean showNumber) {
        ItemStack stack = item.getDefaultStack();
        String caption = fit(stack.getName().getString(), 103);
        int width = metricWidth(caption, showNumber ? status : "", 109);
        return new Card(width, showNumber ? 41 : 34, c -> {
            drawItemTile(c, stack, 6, 7, 22);
            label(c, caption, 35, 6, 0.89f, muted());
            if (showNumber) label(c, status, 35, 16, 1.15f, fg());
            int barY = showNumber ? 31 : 21;
            bar(c, 35, barY, width - 9, barY + 4, progress, accent());
        });
    }

    static void updateTargetContext(MinecraftClient client) {
        SnowConfig config = SnowVisualsClient.CONFIG;
        if (targetWorld != client.world || targetPlayer != client.player || targetConfig != config
                || config == null || !config.enabled(SnowModules.Module.TARGET_HUD)) {
            targetWorld = client.world;
            targetPlayer = client.player;
            targetConfig = config;
            drawnTarget = null;
            drawnHealth = 0f;
            targetVisibility = 0f;
        }
    }

    private static Card target(SnowModules.State settings, boolean editing, float dt) {
        var entity = SnowQol.target(mc());
        if (editing) {
            LivingEntity preview = entity == null ? mc().player : entity;
            if (preview == null) return null;
            TargetSnapshot snapshot = snapshot(preview);
            return targetCard(settings, snapshot, snapshot.healthRatio(), 1f, 0f);
        }

        if (entity != null) {
            TargetSnapshot current = snapshot(entity);
            drawnHealth = drawnTarget != null && current.id().equals(drawnTarget.id())
                    ? SnowStyle.approach(drawnHealth, current.healthRatio(), 18f, dt)
                    : current.healthRatio();
            drawnTarget = current;
            targetVisibility = Math.min(1f, targetVisibility + dt / 0.16f);
        } else {
            targetVisibility = Math.max(0f, targetVisibility - dt / 0.24f);
            if (targetVisibility == 0f) drawnTarget = null;
        }
        if (drawnTarget == null || targetVisibility == 0f) return null;

        float eased = targetVisibility * targetVisibility * (3f - 2f * targetVisibility);
        return targetCard(settings, drawnTarget, drawnHealth, eased, (1f - eased) * 6f);
    }

    private static TargetSnapshot snapshot(LivingEntity entity) {
        return new TargetSnapshot(entity.getUuid(),
                entity instanceof AbstractClientPlayerEntity player ? player.getSkin() : null,
                entity.getDisplayName().getString(), Math.max(0f, entity.getHealth()),
                Math.max(1f, entity.getMaxHealth()), Math.max(0f, entity.getAbsorptionAmount()));
    }

    private static Card targetCard(SnowModules.State settings, TargetSnapshot target, float shownHealth,
                                   float opacity, float offsetY) {
        int width = 166;
        String healthText = "♥  " + hearts(target.health()) + " / " + hearts(target.maxHealth())
                + (target.absorption() > 0f ? "  +" + hearts(target.absorption()) : "");
        String name = fit(target.name(), (width - 61) / 1.16f);
        healthText = fit(healthText, width - 61);
        String vitals = healthText;
        boolean showBar = settings.b != 0;
        return new Card(width, showBar ? 53 : 44, opacity, offsetY, c -> {
            var colors = palette();
            SnowHudChrome.inset(c, 7, 7, 44, 37, 7, colors, opacity * 0.82f);
            if (target.skin() != null) {
                PlayerSkinDrawer.draw(c, target.skin(), 13, 10, 24, SnowStyle.opacity(0xFFFFFFFF, opacity));
            } else if (!SnowIcons.draw(c, SnowModules.Module.TARGET_HUD, 17, 14, 16,
                    SnowStyle.opacity(colors.ink(), opacity))) {
                SnowStyle.circle(c, 25, 22, 3, SnowStyle.opacity(colors.accent(), opacity));
            }

            label(c, name, 52, 9, 1.16f, SnowStyle.opacity(colors.ink(), opacity));
            label(c, vitals, 52, 24, SnowStyle.opacity(colors.muted(), opacity));
            if (showBar) {
                int health = SnowStyle.blend(0xFFFF7894, colors.accent(),
                        MathHelper.clamp(shownHealth * 1.5f, 0f, 1f));
                bar(c, 8, 43, width - 8, 47, shownHealth, SnowStyle.opacity(health, opacity),
                        SnowStyle.opacity(colors.edge(), opacity));
            }
        });
    }

    private static String hearts(float health) {
        int tenths = Math.round(health * 5);
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    private static Card ping(SnowModules.Module module, SnowModules.State settings) {
        var handler = mc().getNetworkHandler();
        var entry = handler == null ? null : handler.getPlayerListEntry(mc().player.getUuid());
        String status = mc().isInSingleplayer() || entry == null || entry.getLatency() < 0
                ? "— ms" : entry.getLatency() + " ms";
        if (settings.b == 0) return compact(module, status);

        int[] samples = SnowQol.pingHistory(settings.a);
        int width = 132;
        return new Card(width, 60, c -> {
            drawSymbol(c, module, 6, 6, 22);
            label(c, "Ping", 35, 7, 0.88f, muted());
            label(c, status, width - 10 - textWidth(status, 1.16f), 9, 1.16f, fg());
            graph(c, samples, 7, 32, width - 7, 53);
        });
    }

    private static void graph(DrawContext c, int[] samples, int left, int top, int right, int bottom) {
        var colors = palette();
        SnowHudChrome.inset(c, left, top, right, bottom, 5, colors, 0.67f);
        int gridColor = SnowStyle.opacity(colors.muted(), 0.20f);
        for (int y = top + 6; y < bottom - 2; y += 5) {
            for (int x = left + 5; x < right - 3; x += 5) {
                c.fill(x, y, x + 1, y + 1, gridColor);
            }
        }
        if (samples.length == 0) return;

        int max = 50;
        for (int sample : samples) max = Math.max(max, sample);

        int plotWidth = right - left - 13;
        int plotHeight = bottom - top - 8;
        int color = colors.accent();
        int y = bottom - 4 - Math.round(plotHeight * samples[0] / (float) max);
        int x = samples.length == 1 ? right - 6 : left + 6;
        for (int i = 1; i < samples.length; i++) {
            int nextX = left + 6 + Math.round(plotWidth * i / (float) (samples.length - 1));
            int nextY = bottom - 4 - Math.round(plotHeight * samples[i] / (float) max);
            SnowStyle.line(c, x, y, nextX, nextY, color);
            x = nextX;
            y = nextY;
        }
        SnowStyle.circle(c, x, y, 3, SnowStyle.opacity(color, 0.30f));
        SnowStyle.circle(c, x, y, 1, color);
    }

    private static Card direction(SnowModules.Module module, SnowModules.State settings) {
        float yaw = MathHelper.wrapDegrees(mc().player.getYaw());
        String degrees = Math.round(yaw) + "°";
        String cardinal = HEADINGS[Math.floorMod(Math.round(yaw / 45f), HEADINGS.length)];
        String status = settings.b == 0 ? degrees : settings.a == 0 ? cardinal : cardinal + "  " + degrees;
        return compact(module, status);
    }

    private static Card items(SnowModules.State settings, boolean editing) {
        List<ItemCount> tracked = new ArrayList<>(TRACKED.length);
        for (int i = 0; i < TRACKED.length; ++i) {
            if ((settings.a & (1 << i)) == 0) continue;
            Item item = TRACKED[i];
            int quantity = SnowQol.itemCount(mc(), item, item == Items.ARROW);
            if (quantity > 0 || settings.b == 0 || editing) tracked.add(new ItemCount(item, quantity));
        }
        if (tracked.isEmpty()) {
            return editing ? compact(SnowModules.Module.ITEM_COUNT, tr("Select tracked items")) : null;
        }

        String heading = tr(SnowModules.Module.ITEM_COUNT.name);
        int columns = Math.min(2, tracked.size());
        int width = Math.max(24 + columns * 52, (int) Math.ceil(textWidth(heading, 0.96f)) + 17);
        int cellWidth = (width - 14 - (columns - 1) * 5) / columns;
        int rows = (tracked.size() + columns - 1) / columns;
        return new Card(width, 24 + rows * 23, c -> {
            var colors = palette();
            label(c, heading, 8, 7, 0.96f, colors.muted());
            for (int i = 0; i < tracked.size(); i++) {
                ItemCount entry = tracked.get(i);
                int x = 7 + i % columns * (cellWidth + 5);
                int y = 20 + i / columns * 23;
                SnowHudChrome.inset(c, x, y, x + cellWidth, y + 19, 6, colors, 0.56f);
                c.drawItemWithoutEntity(entry.item().getDefaultStack(), x + 3, y + 2);
                String amount = "×" + entry.count();
                label(c, amount, x + cellWidth - textWidth(amount) - 6, y + 5, colors.ink());
            }
        });
    }

    private static Card compact(SnowModules.Module module, String value) {
        String title = switch (module) {
            case BETTER_CPS -> "CPS";
            case COMBO -> tr("Combo");
            case PING_GRAPH -> "Ping";
            default -> tr(module.name);
        };
        String clippedTitle = fit(title, 116);
        int width = metricWidth(clippedTitle, value, 78);
        return new Card(width, 30, c -> {
            drawSymbol(c, module, 6, 4, 22);
            label(c, clippedTitle, 35, 5, 0.88f, muted());
            label(c, value, 35, 15, 1.18f, fg());
        });
    }

    private static Card itemCard(Item item, String value) {
        ItemStack stack = item.getDefaultStack();
        String title = fit(stack.getName().getString(), 124);
        int width = metricWidth(title, value, 71);
        return new Card(width, 30, c -> {
            drawItemTile(c, stack, 6, 4, 22);
            label(c, title, 35, 5, 0.88f, muted());
            label(c, value, 35, 15, 1.18f, fg());
        });
    }

    private static int metricWidth(String title, String value, int minimum) {
        float textWidth = Math.max(textWidth(title, 0.88f), textWidth(value, 1.18f));
        return Math.max(minimum, (int) Math.ceil(textWidth) + 45);
    }

    private static void drawSymbol(DrawContext c, SnowModules.Module module, int x, int y, int size) {
        SnowHudChrome.inset(c, x, y, x + size, y + size, 6, palette(), state(module).opacity / 110f);
        int centerX = x + size / 2;
        int centerY = y + size / 2;
        if (!SnowIcons.draw(c, module, centerX - 8, centerY - 8, 16, fg())) {
            SnowStyle.flake(c, centerX, centerY, 6, accent());
        }
    }

    private static void drawItemTile(DrawContext c, ItemStack item, int x, int y, int size) {
        SnowHudChrome.inset(c, x, y, x + size, y + size, 6, palette(), 0.70f);
        c.drawItemWithoutEntity(item, x + (size - 16) / 2, y + (size - 16) / 2);
    }

    private static void bar(DrawContext c, int left, int top, int right, int bottom, float progress, int color) {
        bar(c, left, top, right, bottom, progress, color, border());
    }

    private static void bar(DrawContext c, int left, int top, int right, int bottom,
                            float progress, int color, int trackColor) {
        SnowHudChrome.meter(c, left, top, right, bottom, progress, color, trackColor);
    }

    static void renderDamageTint(DrawContext c) {
        float alpha = SnowQol.damageAlpha();
        if (alpha <= 0f) {
            return;
        }
        int width = c.getScaledWindowWidth();
        int height = c.getScaledWindowHeight();
        int depth = Math.max(1, Math.min(width, height) / 6);
        int color = SnowStyle.alpha(state(SnowModules.Module.DAMAGE_TINT).color, Math.round(alpha * 100f));
        int clear = color & 0xFFFFFF;
        c.fillGradient(0, 0, width, depth, color, clear);
        c.fillGradient(0, height - depth, width, height, clear, color);

        var matrix = c.getMatrices();
        matrix.pushMatrix();
        try {
            matrix.translate(0f, height);
            matrix.rotate(-(float) (Math.PI / 2));
            c.fillGradient(0, 0, height, depth, color, clear);
            c.fillGradient(0, width - depth, height, width, clear, color);
        } finally {
            matrix.popMatrix();
        }
    }
}

