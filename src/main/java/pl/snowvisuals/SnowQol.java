package pl.snowvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

import static pl.snowvisuals.SnowModules.Module;

public final class SnowQol {
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final int MAX_CLICK_WINDOW_MS = 2000;
    private static final int MAX_PING_SAMPLES = 60;
    private static final int PING_INTERVAL_MS = 500;
    private static final ItemStack DEFAULT_PEARL = new ItemStack(Items.ENDER_PEARL);
    private static final ArrayDeque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static final ArrayDeque<Integer> PINGS = new ArrayDeque<>();

    private static SnowConfig config;
    private static ClientWorld world;
    private static PlayerEntity player;
    private static boolean sprintEnabled;
    private static boolean sneakEnabled;
    private static int peakWindow;
    private static int leftPeak;
    private static int rightPeak;
    private static int combo;
    private static int comboTarget;
    private static long comboTime;
    private static long hitTime;
    private static long hurtTime;
    private static long targetTime;
    private static long lastPingTime;
    private static LivingEntity lastTarget;

    private SnowQol() {}

    static void tick(MinecraftClient mc) {
        updateSession(mc);
        boolean sprint = SnowEffects.enabled(Module.TOGGLE_SPRINT);
        boolean sneak = SnowEffects.enabled(Module.TOGGLE_SNEAK);
        if (sprintEnabled && !sprint && !mc.options.getSprintToggled().getValue()) {
            mc.options.sprintKey.setPressed(false);
        }
        if (sneakEnabled && !sneak && !mc.options.getSneakToggled().getValue()) {
            mc.options.sneakKey.setPressed(false);
        }
        sprintEnabled = sprint;
        sneakEnabled = sneak;

        if (mc.player == null || mc.world == null) return;
        long now = System.nanoTime();
        pruneClicks(LEFT_CLICKS, now);
        pruneClicks(RIGHT_CLICKS, now);
        if (SnowEffects.enabled(Module.TARGET_HUD)
                && mc.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && validTarget(living, mc)) {
            rememberTarget(living, now);
        }
        if (!SnowEffects.enabled(Module.PING_GRAPH)) {
            PINGS.clear();
            lastPingTime = 0;
        } else if (lastPingTime == 0 || now - lastPingTime >= PING_INTERVAL_MS * NANOS_PER_MS) {
            samplePing(mc, now);
        }
    }

    private static void updateSession(MinecraftClient mc) {
        if (config == SnowVisualsClient.CONFIG && world == mc.world && player == mc.player) return;
        config = SnowVisualsClient.CONFIG;
        world = mc.world;
        player = mc.player;
        LEFT_CLICKS.clear();
        RIGHT_CLICKS.clear();
        PINGS.clear();
        peakWindow = 0;
        leftPeak = rightPeak = 0;
        combo = 0;
        comboTime = hitTime = hurtTime = targetTime = lastPingTime = 0;
        lastTarget = null;
    }

    static void click(int button) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        updateSession(mc);
        long now = System.nanoTime();
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        ArrayDeque<Long> clicks = right ? RIGHT_CLICKS : LEFT_CLICKS;
        pruneClicks(clicks, now);
        clicks.addLast(now);

        int window = SnowEffects.state(Module.BETTER_CPS).a;
        updatePeakWindow(window);
        int rate = clickRate(clicks, window, now);
        if (right) rightPeak = Math.max(rightPeak, rate);
        else leftPeak = Math.max(leftPeak, rate);
    }

    private static void pruneClicks(ArrayDeque<Long> clicks, long now) {
        long oldest = now - MAX_CLICK_WINDOW_MS * NANOS_PER_MS;
        while (!clicks.isEmpty() && clicks.getFirst() < oldest) clicks.removeFirst();
    }

    private static int clickRate(ArrayDeque<Long> clicks, int windowMs, long now) {
        long oldest = now - windowMs * NANOS_PER_MS;
        int count = 0;
        var newestFirst = clicks.descendingIterator();
        while (newestFirst.hasNext() && newestFirst.next() >= oldest) count++;
        return Math.round(count * 1000f / windowMs);
    }

    private static void updatePeakWindow(int windowMs) {
        if (peakWindow == windowMs) return;
        peakWindow = windowMs;
        leftPeak = rightPeak = 0;
    }

    public static int cps(boolean right, int windowMs) {
        return clickRate(right ? RIGHT_CLICKS : LEFT_CLICKS, windowMs, System.nanoTime());
    }

    public static int peakCps(boolean right, int windowMs) {
        updatePeakWindow(windowMs);
        return right ? rightPeak : leftPeak;
    }

    public static void damaged(EntityDamageS2CPacket packet) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || SnowVisualsClient.CONFIG == null) return;
        updateSession(mc);
        long now = System.nanoTime();
        int playerId = mc.player.getId();
        if (packet.entityId() == playerId) {
            combo = 0;
            hurtTime = now;
            return;
        }
        if (packet.sourceCauseId() != playerId) return;

        Entity entity = mc.world.getEntityById(packet.entityId());
        if (!(entity instanceof LivingEntity living)) return;
        hitTime = now;
        SnowEffects.hit(living);
        if (SnowEffects.enabled(Module.TARGET_HUD) && validTarget(living, mc)) {
            rememberTarget(living, now);
        }
        if (packet.sourceDirectId() == playerId && SnowEffects.enabled(Module.COMBO)) {
            int resetMs = SnowEffects.state(Module.COMBO).a;
            boolean sameChain = combo > 0 && comboTarget == entity.getId()
                    && now - comboTime < resetMs * NANOS_PER_MS;
            combo = sameChain ? combo + 1 : 1;
            comboTarget = entity.getId();
            comboTime = now;
        }
    }

    public static int combo() {
        long reset = SnowEffects.state(Module.COMBO).a * NANOS_PER_MS;
        return System.nanoTime() - comboTime < reset ? combo : 0;
    }

    private static float fade(long start, int durationMs) {
        if (start == 0) return 0f;
        float remaining = 1f - (System.nanoTime() - start) / (durationMs * (float) NANOS_PER_MS);
        remaining = MathHelper.clamp(remaining, 0f, 1f);
        return remaining * remaining * (3f - 2f * remaining);
    }

    public static float hitAlpha() {
        return fade(hitTime, SnowEffects.state(Module.HIT_MARKER).a);
    }

    public static float damageAlpha() {
        SnowModules.State settings = SnowEffects.state(Module.DAMAGE_TINT);
        return fade(hurtTime, settings.b) * settings.a / 100f;
    }

    private static boolean validTarget(LivingEntity living, MinecraftClient mc) {
        return living != mc.player && living.getEntityWorld() == mc.world && living.isAlive()
                && (living instanceof PlayerEntity || SnowEffects.state(Module.TARGET_HUD).c != 0);
    }

    private static void rememberTarget(LivingEntity target, long now) {
        lastTarget = target;
        targetTime = now;
    }

    public static LivingEntity target(MinecraftClient mc) {
        return lastTarget != null && validTarget(lastTarget, mc)
                && System.nanoTime() - targetTime < SnowEffects.state(Module.TARGET_HUD).a * NANOS_PER_MS
                ? lastTarget : null;
    }

    private static void samplePing(MinecraftClient mc, long now) {
        lastPingTime = now;
        var handler = mc.getNetworkHandler();
        if (handler == null || mc.isInSingleplayer()) return;
        PlayerListEntry entry = handler.getPlayerListEntry(mc.player.getUuid());
        if (entry == null || entry.getLatency() < 0) return;
        if (PINGS.size() == MAX_PING_SAMPLES) PINGS.removeFirst();
        PINGS.addLast(entry.getLatency());
    }

    public static int[] pingHistory(int sampleCount) {
        int length = Math.min(sampleCount, PINGS.size());
        int[] samples = new int[length];
        var newestFirst = PINGS.descendingIterator();
        for (int i = length - 1; i >= 0; i--) samples[i] = newestFirst.next();
        return samples;
    }

    public static int itemCount(MinecraftClient mc, Item item, boolean variants) {
        if (mc.player == null) return 0;
        PlayerInventory inventory = mc.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(item) || (item == Items.ARROW && variants
                    && (stack.isOf(Items.SPECTRAL_ARROW) || stack.isOf(Items.TIPPED_ARROW)))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static ItemStack activePearl(PlayerEntity player, ItemCooldownManager cooldowns) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(Items.ENDER_PEARL) && cooldowns.isCoolingDown(main)) return main;
        ItemStack off = player.getOffHandStack();
        if (off.isOf(Items.ENDER_PEARL) && cooldowns.isCoolingDown(off)) return off;
        if (cooldowns.isCoolingDown(DEFAULT_PEARL)) return DEFAULT_PEARL;
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.ENDER_PEARL) && cooldowns.isCoolingDown(stack)) return stack;
        }
        return DEFAULT_PEARL;
    }

    public static float pearlCooldown(MinecraftClient mc, float tickProgress) {
        if (mc.player == null) return 0;
        ItemCooldownManager cooldowns = mc.player.getItemCooldownManager();
        return cooldowns.getCooldownProgress(activePearl(mc.player, cooldowns), tickProgress);
    }

    public static float pearlSeconds(MinecraftClient mc, float tickProgress) {
        if (mc.player == null) return 0;
        ItemCooldownManager cooldowns = mc.player.getItemCooldownManager();
        ItemStack pearl = activePearl(mc.player, cooldowns);
        float current = cooldowns.getCooldownProgress(pearl, 0f);
        if (current == 0f) return 0;
        // Vanilla progress is linear. Two samples yield the remaining whole ticks
        // even when a server sends a cooldown different from the item default.
        float oneTick = current - cooldowns.getCooldownProgress(pearl, 1f);
        if (oneTick <= 0f) return 0;
        float ticks = Math.max(0f, Math.round(current / oneTick) - tickProgress);
        return ticks / mc.world.getTickManager().getTickRate();
    }

    public static float bowCharge(MinecraftClient mc) {
        if (mc.player == null || !mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.BOW)) {
            return -1f;
        }
        float time = mc.player.getItemUseTime(mc.getRenderTickCounter().getTickProgress(false));
        int ticks = (int) time;
        return MathHelper.lerp(time - ticks, BowItem.getPullProgress(ticks), BowItem.getPullProgress(ticks + 1));
    }
}
