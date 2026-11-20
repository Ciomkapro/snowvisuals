package pl.snowvisuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static pl.snowvisuals.SnowModules.Module;

final class SnowWorldEffects {
    private static final Identifier ATLAS = Identifier.of("snowvisuals", "textures/effects/world.png");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.of("snowvisuals", "pipeline/world_effects"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());
    private static final RenderLayer LAYER = RenderLayer.of("snowvisuals_world_effects",
            RenderSetup.builder(PIPELINE)
                    .texture("Sampler0", ATLAS, () -> RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
                    .useLightmap()
                    .translucent()
                    .expectedBufferSize(32768)
                    .build());
    private static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create();
    private static final int MAX_PARTICLES = 256;
    private static final int MAX_TRAIL = 64;
    private static final float FAR_DISTANCE_SQUARED = 64 * 64;
    private static final double NOISE_MOVEMENT_SQUARED = 0.035 * 0.035;
    private static final long MILLIS = 1_000_000L;
    private static final double MARKER_ROTATION_RATE = Math.TAU / 3_600_000_000.0;
    private static final double MARKER_HALF_WIDTH = 0.052;
    private static final ArrayDeque<Particle> particles = new ArrayDeque<>();
    private static final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();

    private static ClientWorld world;
    private static Entity player;
    private static AbstractClientPlayerEntity markerTarget;
    private static long markerAnimationNanos;
    private static double markerRotation;
    private static Vec3d trailPosition;
    private static boolean moving;
    private static BufferAllocator buffer;
    private static VertexConsumerProvider.Immediate immediate;

    private SnowWorldEffects() {}

    static void register() {
        SnowChinaHat.register();
        WorldRenderEvents.END_EXTRACTION.register(SnowWorldEffects::extract);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(SnowWorldEffects::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> closeBuffer());
    }

    private static void closeBuffer() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
            immediate = null;
        }
    }

    private static void updateWorld(MinecraftClient client) {
        if (world != client.world || player != client.player) {
            world = client.world;
            player = client.player;
            markerTarget = null;
            markerAnimationNanos = 0;
            markerRotation = 0;
            particles.clear();
            clearTrail();
        }
    }

    private static void clearTrail() {
        trail.clear();
        trailPosition = null;
        moving = false;
    }

    static void tick(MinecraftClient client) {
        updateWorld(client);
        if (player == null || world == null) return;
        updateMarkerTarget(client);
        long now = System.nanoTime();

        if (SnowEffects.enabled(Module.PARTICLES)) {
            particles.removeIf(particle -> now - particle.started > particle.lifetime);
        } else {
            particles.clear();
        }

        if (!SnowEffects.enabled(Module.TRAILS)) {
            clearTrail();
            return;
        }

        long duration = SnowEffects.state(Module.TRAILS).a * MILLIS;
        while (!trail.isEmpty() && now - trail.getFirst().time > duration) trail.removeFirst();

        Vec3d next = feet(player.getEntityPos());
        moving = false;
        if (trailPosition != null) {
            double distanceSquared = next.squaredDistanceTo(trailPosition);
            if (distanceSquared > 16.0) {
                trail.clear();
            } else if (distanceSquared >= NOISE_MOVEMENT_SQUARED) {
                if (trail.isEmpty()) trail.addLast(new TrailPoint(trailPosition, now - 50 * MILLIS));
                trail.addLast(new TrailPoint(next, now));
                if (trail.size() > MAX_TRAIL) trail.removeFirst();
                moving = true;
            } else {
                return;
            }
        }
        trailPosition = next;
    }

    private static void updateMarkerTarget(MinecraftClient client) {
        markerTarget = null;
        var local = client.player;
        if (local == null || local.isSpectator() || !SnowEffects.enabled(Module.TARGET_HUD)) return;
        var settings = SnowEffects.state(Module.TARGET_HUD);
        if (settings.d == 0) return;

        double range = MathHelper.clamp(settings.markerRange, 2, 8);
        double distanceLimit = range * range;
        for (AbstractClientPlayerEntity candidate : world.getPlayers()) {
            if (!validMarkerTarget(client, candidate)) continue;

            double distance = local.squaredDistanceTo(candidate);
            if (distance > distanceLimit || !canSeeTorso(local, candidate)) continue;
            distanceLimit = distance;
            markerTarget = candidate;
        }
    }

    private static boolean canSeeTorso(Entity viewer, Entity target) {
        Vec3d torso = target.getEntityPos().add(0, target.getHeight() * 0.53, 0);
        var ray = new RaycastContext(viewer.getEyePos(), torso, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, viewer);
        return world.raycast(ray).getType() == HitResult.Type.MISS;
    }

    private static boolean validMarkerTarget(MinecraftClient client, AbstractClientPlayerEntity target) {
        return target != null && target != client.player && target.isAlive()
                && !target.isSpectator() && !target.isInvisible()
                && !target.isInvisibleTo(client.player);
    }

    private static Marker extractMarker(MinecraftClient client, float tickProgress, long now) {
        AbstractClientPlayerEntity target = markerTarget;
        if (!SnowEffects.enabled(Module.TARGET_HUD) || client.player.isSpectator()
                || !validMarkerTarget(client, target)) return null;

        var settings = SnowEffects.state(Module.TARGET_HUD);
        if (settings.d == 0) return null;
        Vec3d center = target.getLerpedPos(tickProgress);
        double distance = center.distanceTo(client.player.getLerpedPos(tickProgress));
        int range = MathHelper.clamp(settings.markerRange, 2, 8);
        float opacity = (float) MathHelper.clamp((range - distance) / 0.65, 0.0, 1.0);
        if (opacity <= 0f) return null;
        opacity *= opacity * (3f - 2f * opacity);
        double size = MathHelper.clamp(settings.markerSize, 30, 200) / 100.0;
        double radius = MathHelper.clamp(target.getWidth() * 0.95, 0.44, 0.68) * size;
        double depthOffset = MathHelper.clamp(target.getWidth() * 0.77, 0.33, 0.48);
        if (markerAnimationNanos != 0) {
            long elapsed = MathHelper.clamp(now - markerAnimationNanos, 0L, 100 * MILLIS);
            double speed = MathHelper.clamp(settings.markerSpeed, 20, 250) / 100.0;
            markerRotation = (markerRotation + elapsed * MARKER_ROTATION_RATE * speed) % Math.TAU;
        }
        markerAnimationNanos = now;
        Vec3d torso = center.add(0, target.getHeight() * 0.53, 0);
        return new Marker(torso, radius, MARKER_HALF_WIDTH * size, depthOffset, markerRotation,
                settings.d, settings.color, opacity);
    }

    static void hit(MinecraftClient client, Entity target) {
        if (!SnowEffects.enabled(Module.PARTICLES) || client.player == null || client.world == null
                || target.getEntityWorld() != client.world) {
            return;
        }
        updateWorld(client);
        SnowModules.State settings = SnowEffects.state(Module.PARTICLES);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3d center = target.getEntityPos().add(0, target.getHeight() * 0.54, 0);
        double reach = MathHelper.clamp(target.getWidth() * 0.75, 0.20, 0.82);
        long now = System.nanoTime();
        for (int i = 0; i < settings.a; i++) {
            double angle = (i + random.nextDouble()) / settings.a * Math.TAU;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            float speed = random.nextFloat(0.95f, 2.45f);
            double radius = reach * random.nextDouble(0.40, 1.04);
            Vec3d start = center.add(x * radius, random.nextDouble(-0.22, 0.26), z * radius);
            Vec3d velocity = new Vec3d(x * speed, random.nextDouble(0.45, 1.85), z * speed);
            boolean plus = settings.b == 2 || (settings.b == 0 && i % 2 != 0);
            int sprite = plus ? 2 : random.nextInt(2);
            float spin = random.nextFloat(-3.2f, 3.2f);
            float angle0 = plus ? random.nextFloat(-0.28f, 0.28f) : random.nextFloat((float) Math.TAU);
            if (particles.size() == MAX_PARTICLES) particles.removeFirst();
            particles.addLast(new Particle(start, velocity, now, random.nextLong(475, 865) * MILLIS,
                    random.nextFloat(0.12f, 0.24f), angle0, spin, sprite));
        }
    }

    private static Vec3d feet(Vec3d position) {
        return position.add(0, 0.12, 0);
    }

    private static void extract(WorldExtractionContext context) {
        var state = context.worldState();
        MinecraftClient client = MinecraftClient.getInstance();
        if (context.world() != world || client.player == null || player != client.player) {
            state.setData(FRAME, null);
            return;
        }

        List<Particle> renderParticles = SnowEffects.enabled(Module.PARTICLES) ? List.copyOf(particles) : List.of();
        List<TrailPoint> renderTrail = List.of();
        long now = System.nanoTime();
        if (trail.size() >= 2 && SnowEffects.enabled(Module.TRAILS)) {
            ArrayList<TrailPoint> points = new ArrayList<>(trail);
            if (moving) {
                float partial = context.tickCounter().getTickProgress(false);
                Vec3d head = feet(client.player.getLerpedPos(partial));
                points.set(points.size() - 1, new TrailPoint(head, now));
            }
            renderTrail = List.copyOf(points);
        }
        Marker marker = extractMarker(client, context.tickCounter().getTickProgress(false), now);
        if (renderParticles.isEmpty() && renderTrail.isEmpty() && marker == null) {
            state.setData(FRAME, null);
            return;
        }

        SnowModules.State particleSettings = SnowEffects.state(Module.PARTICLES);
        SnowModules.State trailSettings = SnowEffects.state(Module.TRAILS);
        state.setData(FRAME, new Frame(renderParticles, renderTrail, now, particleSettings.color,
                particleSettings.color2, trailSettings.color, trailSettings.a * MILLIS, trailSettings.b * 0.01,
                trailSettings.c * 0.01, marker));
    }

    private static void render(WorldRenderContext context) {
        Frame frame = context.worldState().getData(FRAME);
        if (frame == null || context.matrices() == null) return;
        var camera = context.worldState().cameraRenderState;
        if (!camera.initialized || camera.pos == null || camera.orientation == null) return;
        if (buffer == null) {
            buffer = new BufferAllocator(LAYER.getExpectedBufferSize());
            immediate = VertexConsumerProvider.immediate(buffer);
        }

        Vec3d right = rotate(camera.orientation, 1, 0, 0);
        Vec3d up = rotate(camera.orientation, 0, 1, 0);
        Matrix4f pose = context.matrices().peek().getPositionMatrix();
        VertexConsumer vertices = immediate.getBuffer(LAYER);
        renderTrail(frame, camera.pos, pose, vertices);
        renderMarker(frame.marker, camera.pos, right, up, pose, vertices);
        renderParticles(frame, camera.pos, right, up, pose, vertices);
        immediate.draw();
    }

    private static Vec3d rotate(Quaternionfc orientation, float x, float y, float z) {
        Vector3f value = orientation.transform(new Vector3f(x, y, z));
        return new Vec3d(value.x, value.y, value.z);
    }

    private static void renderParticles(Frame frame, Vec3d camera, Vec3d right, Vec3d up, Matrix4f pose,
                                        VertexConsumer vertices) {
        for (Particle particle : frame.particles) {
            long age = frame.now - particle.started;
            if (age < 0 || age >= particle.lifetime) continue;
            double seconds = age / 1_000_000_000.0;
            double progress = (double) age / particle.lifetime;
            Vec3d position = particle.position.add(particle.velocity.multiply(seconds))
                    .add(0, -1.65 * seconds * seconds, 0).subtract(camera);
            if (position.lengthSquared() > FAR_DISTANCE_SQUARED) continue;

            double size = particle.size * (0.90 + 0.19 * Math.sin(progress * Math.PI) - 0.34 * progress) * 0.5;
            double rotation = particle.rotation + particle.spin * seconds;
            double cos = Math.cos(rotation);
            double sin = Math.sin(rotation);
            Vec3d x = right.multiply(cos * size).add(up.multiply(sin * size));
            Vec3d y = up.multiply(cos * size).subtract(right.multiply(sin * size));
            int rgb = particle.sprite == 2 ? frame.plusColor : frame.flakeColor;
            float alpha = (float) (1.0 - progress * progress * (3.0 - 2.0 * progress));
            int color = withAlpha(rgb, alpha);
            float u = (particle.sprite & 1) * 0.5f;
            float v = (particle.sprite >> 1) * 0.5f;
            quad(vertices, pose, position.subtract(x).add(y), position.add(x).add(y),
                    position.add(x).subtract(y), position.subtract(x).subtract(y), u, v, color, color);
        }
    }

    private static void renderMarker(Marker marker, Vec3d camera, Vec3d right, Vec3d up, Matrix4f pose,
                                     VertexConsumer vertices) {
        if (marker == null) return;

        Vec3d torso = marker.center.subtract(camera);
        double distance = torso.length();
        if (distance <= 0.45) return;
        Vec3d center = torso.multiply(1.0 - Math.min(marker.depthOffset, distance - 0.45) / distance);
        double cosine = Math.cos(marker.rotation);
        double sine = Math.sin(marker.rotation);
        Vec3d x = right.multiply(cosine).add(up.multiply(sine));
        Vec3d y = up.multiply(cosine).subtract(right.multiply(sine));
        if (marker.shape == 3) {
            renderMarkerTriangle(marker, center, x, y, pose, vertices);
            return;
        }
        if (marker.shape == 2) {
            for (int vertical : new int[]{-1, 1}) {
                for (int horizontal : new int[]{-1, 1}) {
                    renderMarkerCorner(marker, center, x.multiply(horizontal), y.multiply(vertical), pose, vertices);
                }
            }
            return;
        }

        for (int section = 0; section < 6; section++) {
            double angle = section * Math.TAU / 6.0;
            MarkerPoint previous = null;
            for (int step = 0; step <= 12; step++) {
                double progress = step / 12.0;
                double radians = angle + (progress - 0.5) * Math.TAU / 6.0 * 0.68;
                Vec3d radial = x.multiply(Math.cos(radians)).add(y.multiply(Math.sin(radians)));
                float tip = (float) Math.min(1.0, Math.min(progress, 1.0 - progress) * 8.0);
                MarkerPoint next = markerPoint(marker, center.add(radial.multiply(marker.radius)),
                        radial.multiply(marker.halfWidth), tip);
                if (previous != null) markerSegment(marker, previous, next, pose, vertices);
                previous = next;
            }
        }
    }

    private static void renderMarkerTriangle(Marker marker, Vec3d center, Vec3d x, Vec3d y,
                                             Matrix4f pose, VertexConsumer vertices) {
        double turn = Math.TAU / 3.0;
        double radius = marker.radius * 1.14;
        for (int corner = 0; corner < 3; corner++) {
            double angle = Math.PI / 2.0 + corner * turn;
            Vec3d outward = markerDirection(x, y, angle);
            Vec3d vertex = center.add(outward.multiply(radius));
            Vec3d previous = markerDirection(x, y, angle - turn).subtract(outward).multiply(radius);
            Vec3d next = markerDirection(x, y, angle + turn).subtract(outward).multiply(radius);
            Vec3d leftWidth = markerDirection(x, y, angle - turn / 2.0).multiply(marker.halfWidth);
            Vec3d rightWidth = markerDirection(x, y, angle + turn / 2.0).multiply(marker.halfWidth);

            MarkerPoint start = markerPoint(marker, vertex.add(previous.multiply(0.30)), leftWidth, 0.18f);
            MarkerPoint left = markerPoint(marker, vertex.add(previous.multiply(0.23)), leftWidth, 1f);
            // A 60-degree corner needs a longer join to meet both outer stroke edges.
            MarkerPoint join = markerPoint(marker, vertex, outward.multiply(2.0 * marker.halfWidth), 1f);
            MarkerPoint right = markerPoint(marker, vertex.add(next.multiply(0.23)), rightWidth, 1f);
            MarkerPoint end = markerPoint(marker, vertex.add(next.multiply(0.30)), rightWidth, 0.18f);
            markerSegment(marker, start, left, pose, vertices);
            markerSegment(marker, left, join, pose, vertices);
            markerSegment(marker, join, right, pose, vertices);
            markerSegment(marker, right, end, pose, vertices);
        }
    }

    private static Vec3d markerDirection(Vec3d x, Vec3d y, double angle) {
        return x.multiply(Math.cos(angle)).add(y.multiply(Math.sin(angle)));
    }

    private static void renderMarkerCorner(Marker marker, Vec3d center, Vec3d x, Vec3d y,
                                           Matrix4f pose, VertexConsumer vertices) {
        double corner = marker.radius;
        Vec3d verticalEdge = y.multiply(marker.halfWidth);
        Vec3d horizontalEdge = x.multiply(marker.halfWidth);
        Vec3d top = center.add(y.multiply(corner));
        Vec3d side = center.add(x.multiply(corner));
        MarkerPoint start = markerPoint(marker, top.add(x.multiply(corner * 0.46)), verticalEdge, 0.18f);
        MarkerPoint arm = markerPoint(marker, top.add(x.multiply(corner * 0.57)), verticalEdge, 1f);
        MarkerPoint join = markerPoint(marker, top.add(x.multiply(corner)), verticalEdge.add(horizontalEdge), 1f);
        MarkerPoint leg = markerPoint(marker, side.add(y.multiply(corner * 0.57)), horizontalEdge, 1f);
        MarkerPoint end = markerPoint(marker, side.add(y.multiply(corner * 0.46)), horizontalEdge, 0.18f);
        markerSegment(marker, start, arm, pose, vertices);
        markerSegment(marker, arm, join, pose, vertices);
        markerSegment(marker, join, leg, pose, vertices);
        markerSegment(marker, leg, end, pose, vertices);
    }

    private static MarkerPoint markerPoint(Marker marker, Vec3d center, Vec3d width, float opacity) {
        return new MarkerPoint(center, center.subtract(width), center.add(width),
                withAlpha(marker.color, marker.opacity * opacity * 0.94f));
    }

    private static void markerSegment(Marker marker, MarkerPoint from, MarkerPoint to, Matrix4f pose,
                                      VertexConsumer vertices) {
        int clear = marker.color & 0xFFFFFF;
        trailQuad(vertices, pose, from.inner, to.inner, to.center, from.center,
                clear, clear, to.color, from.color);
        trailQuad(vertices, pose, from.center, to.center, to.outer, from.outer,
                from.color, to.color, clear, clear);
    }

    private static void renderTrail(Frame frame, Vec3d camera, Matrix4f pose, VertexConsumer vertices) {
        List<TrailPoint> points = frame.trail;
        TrailSection previous = null;
        Vec3d normal = new Vec3d(1, 0, 0);
        for (int i = 0; i < points.size(); i++) {
            TrailPoint at = points.get(i);
            Vec3d position = at.position.subtract(camera);
            Vec3d before = points.get(Math.max(0, i - 1)).position;
            Vec3d after = points.get(Math.min(points.size() - 1, i + 1)).position;
            double dx = after.x - before.x;
            double dz = after.z - before.z;
            double horizontal = Math.hypot(dx, dz);
            if (horizontal > 1.0e-4) {
                Vec3d rotated = new Vec3d(-dz / horizontal, 0, dx / horizontal);
                normal = rotated.dotProduct(normal) < 0 ? rotated.negate() : rotated;
            }

            float remaining = MathHelper.clamp(1f - (float) (frame.now - at.time) / frame.trailLifetime, 0f, 1f);
            float taper = (float) Math.sqrt(remaining);
            float proximity = MathHelper.clamp((float) (position.length() - 0.28) / 0.65f, 0f, 1f);
            float opacity = remaining * remaining * proximity;
            double height = frame.trailHeight * (0.22 + 0.78 * taper);
            double width = frame.trailWidth * (0.24 + 0.76 * taper) * 0.5;
            Vec3d side = normal.multiply(width);
            Vec3d shoulder = position.add(0, height * 0.86, 0);
            TrailSection section = new TrailSection(position, shoulder.subtract(side), shoulder.add(side),
                    position.add(0, height, 0), withAlpha(frame.trailColor, 0f),
                    withAlpha(frame.trailColor, 0.52f * opacity), withAlpha(frame.trailColor, 0.85f * opacity));
            if (previous != null && previous.base.squaredDistanceTo(position) > 1.0e-8) {
                trailQuad(vertices, pose, previous.base, section.base, section.left, previous.left,
                        previous.bottomColor, section.bottomColor, section.sideColor, previous.sideColor);
                trailQuad(vertices, pose, previous.base, section.base, section.right, previous.right,
                        previous.bottomColor, section.bottomColor, section.sideColor, previous.sideColor);
                trailQuad(vertices, pose, previous.left, section.left, section.top, previous.top,
                        previous.sideColor, section.sideColor, section.topColor, previous.topColor);
                trailQuad(vertices, pose, previous.right, section.right, section.top, previous.top,
                        previous.sideColor, section.sideColor, section.topColor, previous.topColor);
            }
            previous = section;
        }
    }

    private static void trailQuad(VertexConsumer vertices, Matrix4f pose, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                                  int colorA, int colorB, int colorC, int colorD) {
        // Sample the opaque center of the atlas' white swatch at every vertex.
        vertex(vertices, pose, a, 0.75f, 0.75f, colorA);
        vertex(vertices, pose, b, 0.75f, 0.75f, colorB);
        vertex(vertices, pose, c, 0.75f, 0.75f, colorC);
        vertex(vertices, pose, d, 0.75f, 0.75f, colorD);
    }

    private static int withAlpha(int rgb, float alpha) {
        return (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void quad(VertexConsumer vertices, Matrix4f pose, Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             float u, float v, int startColor, int endColor) {
        float inset = 0.5f / 256f;
        vertex(vertices, pose, a, u + inset, v + inset, startColor);
        vertex(vertices, pose, b, u + 0.5f - inset, v + inset, startColor);
        vertex(vertices, pose, c, u + 0.5f - inset, v + 0.5f - inset, endColor);
        vertex(vertices, pose, d, u + inset, v + 0.5f - inset, endColor);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, Vec3d pos, float u, float v, int color) {
        vertices.vertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .texture(u, v).color(color).light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    private record Particle(Vec3d position, Vec3d velocity, long started, long lifetime, float size,
                            float rotation, float spin, int sprite) {}
    private record TrailPoint(Vec3d position, long time) {}
    private record TrailSection(Vec3d base, Vec3d left, Vec3d right, Vec3d top,
                                int bottomColor, int sideColor, int topColor) {}
    private record Marker(Vec3d center, double radius, double halfWidth, double depthOffset, double rotation,
                          int shape, int color, float opacity) {}
    private record MarkerPoint(Vec3d center, Vec3d inner, Vec3d outer, int color) {}
    private record Frame(List<Particle> particles, List<TrailPoint> trail, long now,
                         int flakeColor, int plusColor, int trailColor, long trailLifetime, double trailWidth, double trailHeight, Marker marker) {}
}
