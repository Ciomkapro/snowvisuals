package pl.snowvisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;

public final class GlowTarget {

    private GlowTarget() {}

    public static boolean canGlow(Entity entity) {

        MinecraftClient mc = MinecraftClient.getInstance();

        if (SnowVisualsClient.CONFIG == null
                || !SnowVisualsClient.CONFIG.playerGlow
                || mc.player == null
                || mc.world == null
                || mc.currentScreen != null
                || !mc.options.getPerspective().isFirstPerson()
                || !(entity instanceof PlayerEntity target)
                || target == mc.player
                || !target.isAlive()
                || target.isInvisible()
                || target.isSpectator()
                || !(mc.crosshairTarget instanceof EntityHitResult hit)
                || hit.getEntity() != entity) {

            return false;
        }

        Vec3d eye = mc.gameRenderer
                .getCamera()
                .getCameraPos();

        Box b = target.getBoundingBox();

        if (eye.squaredDistanceTo(b.getCenter()) > 36
                || target.isGliding()
                || target.isSwimming()) {

            return false;
        }

        Box area = new Box(
                b.minX - .22,
                b.minY + .015,
                b.minZ - .22,
                b.maxX + .22,
                b.maxY + .18,
                b.maxZ + .22
        );

        return clearVolume(mc, eye, area);
    }

    private static boolean clearVolume(
            MinecraftClient mc,
            Vec3d eye,
            Box target
    ) {

        int minX = (int) Math.floor(
                Math.min(eye.x, target.minX)
        ) - 1;

        int minY = (int) Math.floor(
                Math.min(eye.y, target.minY)
        ) - 1;

        int minZ = (int) Math.floor(
                Math.min(eye.z, target.minZ)
        ) - 1;

        int maxX = (int) Math.floor(
                Math.max(eye.x, target.maxX)
        ) + 1;

        int maxY = (int) Math.floor(
                Math.max(eye.y, target.maxY)
        ) + 1;

        int maxZ = (int) Math.floor(
                Math.max(eye.z, target.maxZ)
        ) + 1;

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {

                    pos.set(x, y, z);

                    BlockState state =
                            mc.world.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    for (Box local : state
                            .getOutlineShape(mc.world, pos)
                            .getBoundingBoxes()) {

                        if (VisibilityMath.intersects(
                                eye.x, eye.y, eye.z,

                                target.minX,
                                target.minY,
                                target.minZ,

                                target.maxX,
                                target.maxY,
                                target.maxZ,

                                local.minX + x,
                                local.minY + y,
                                local.minZ + z,

                                local.maxX + x,
                                local.maxY + y,
                                local.maxZ + z
                        )) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }
}