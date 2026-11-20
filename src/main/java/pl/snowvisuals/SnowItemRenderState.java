package pl.snowvisuals;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class SnowItemRenderState extends ItemEntityRenderState {
    private static final float HALF_PI = (float) (Math.PI / 2);
    private static final float TAU = (float) (Math.PI * 2);
    private static final double CLEARANCE = 0.045;
    private static final Map<UUID, Motion> MOTIONS = new WeakHashMap<>();

    private final Quaternionf rotation = new Quaternionf();
    private boolean physicsReady;
    private float lift;

    public boolean physicsReady() {
        return physicsReady;
    }

    public float lift() {
        return lift;
    }

    public Quaternionfc rotation() {
        return rotation;
    }

    public void updatePhysics(ItemEntity entity) {
        physicsReady = false;
        if (itemRenderState.isEmpty() || renderedAmount <= 0) return;

        Box bounds = itemRenderState.getModelBoundingBox();
        double width = bounds.getLengthX();
        double height = bounds.getLengthY();
        double thickness = bounds.getLengthZ();
        double side = Math.max(Math.max(width, height), thickness);
        if (!Double.isFinite(side) || side < 1.0e-6
                || !Double.isFinite(bounds.minX + bounds.maxX + bounds.minY
                        + bounds.maxY + bounds.minZ + bounds.maxZ)) return;

        boolean flat = thickness < Math.max(width, height) * 0.42;
        Motion motion = MOTIONS.computeIfAbsent(entity.getUuid(), Motion::new);
        motion.update(entity, age, flat);
        rotation.set(motion.rotation);

        // Project the model bounds onto world up to keep the lowest corner above the surface.
        Vector3f up = rotation.transformInverse(0f, 1f, 0f, motion.localUp);
        double low = (up.x >= 0 ? bounds.minX : bounds.maxX) * up.x
                + (up.y >= 0 ? bounds.minY : bounds.maxY) * up.y
                + (up.z >= 0 ? bounds.minZ : bounds.maxZ) * up.z;
        double contact = Math.max(CLEARANCE, CLEARANCE - low);
        // Vanilla centers thin stacks around their local Z axis. When the item
        // lies flat this can move the bottom copy below the ground.
        if (thickness <= 0.0625 && renderedAmount > 1) {
            contact += Math.abs(up.z) * thickness * 0.75 * (renderedAmount - 1);
        }
        double flightCenter = Math.max(0.09 + Math.max(0.0, -bounds.minY), contact);
        lift = (float) MathHelper.lerp(motion.settle, flightCenter, contact);
        if (!Float.isFinite(lift) || !rotation.isFinite()) return;
        physicsReady = true;
    }

    private static final class Motion {
        private final Quaternionf rotation = new Quaternionf();
        private final Quaternionf resting = new Quaternionf();
        private final Vector3f localUp = new Vector3f();
        private final float heading;
        private final float direction;
        private float previousAge = Float.NaN;
        private float settle;
        private int supportTick = Integer.MIN_VALUE;
        private boolean grounded;

        Motion(UUID id) {
            int seed = id.hashCode();
            heading = (seed >>> 12) / 1048576f * TAU;
            direction = (seed & 1) == 0 ? 1f : -1f;
        }

        void update(ItemEntity entity, float age, boolean flat) {
            Vec3d velocity = entity.getVelocity();
            if (supportTick != entity.age) {
                grounded = isSupported(entity, velocity);
                supportTick = entity.age;
            }
            resting.rotationY(heading);
            if (flat) resting.rotateX(-HALF_PI + 0.22f);

            float elapsed = age - previousAge;
            previousAge = age;
            if (!Float.isFinite(elapsed) || elapsed < -0.5f || elapsed > 5f) {
                settle = grounded ? 1f : 0f;
                rotation.set(resting);
                if (!grounded) rotation.rotationY(heading).rotateX(0.45f * direction).rotateZ(0.25f);
                return;
            }
            if (elapsed <= 0f) return;

            if (grounded) {
                float blend = 1f - (float) Math.exp(-elapsed * 0.67);
                rotation.slerp(resting, blend).normalize();
                settle += (1f - settle) * blend;
            } else {
                float speed = (float) Math.min(0.24, velocity.length() * 0.48);
                rotation.rotateXYZ(
                        direction * elapsed * (0.13f + speed),
                        elapsed * (0.08f + speed * 0.45f),
                        direction * elapsed * (0.09f + speed * 0.65f)).normalize();
                settle *= (float) Math.exp(-elapsed * 0.9);
            }
        }

        private static boolean isSupported(ItemEntity entity, Vec3d velocity) {
            if (entity.isOnGround()) return true;
            if (Math.abs(velocity.y) > 0.065 || entity.isTouchingWater() || entity.isInLava()) return false;

            // The client can briefly lose the ground flag while a resting drop is corrected by a packet.
            Box box = entity.getBoundingBox();
            Box foot = new Box(box.minX + 0.025, box.minY - 0.027, box.minZ + 0.025,
                    box.maxX - 0.025, box.minY + 0.001, box.maxZ - 0.025);
            return entity.getEntityWorld().getBlockCollisions(entity, foot).iterator().hasNext();
        }
    }
}
