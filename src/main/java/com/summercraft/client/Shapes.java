package com.summercraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/**
 * Draws textured solids into an entity vertex buffer: boxes, skewed boxes (for pillars and glass) and
 * many-sided cylinders (for wheels, discs and pipes). Each face samples one atlas tile, scaled so
 * small faces show a small patch of the tile rather than a squashed copy of the whole thing.
 */
public final class Shapes {
    /** Blocks of surface that show one whole tile. */
    private static final float TILE_SPAN = 0.5f;

    private final PoseStack pose;
    private VertexConsumer buffer;
    private int light;

    public Shapes(PoseStack pose, VertexConsumer buffer, int light) {
        this.pose = pose;
        this.buffer = buffer;
        this.light = light;
    }

    public PoseStack pose() {
        return pose;
    }

    public Shapes buffer(VertexConsumer buffer) {
        this.buffer = buffer;
        return this;
    }

    public Shapes light(int light) {
        this.light = light;
        return this;
    }

    public int light() {
        return light;
    }

    public void box(double x1, double y1, double z1, double x2, double y2, double z2, Tex tex, int color) {
        hexa(new Vec3[]{
                new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), new Vec3(x1, y1, z2),
                new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), new Vec3(x1, y2, z2)}, tex, color);
    }

    /**
     * A solid with eight arbitrary corners: bottom four then top four, each going
     * (-x,-z), (+x,-z), (+x,+z), (-x,+z).
     */
    public void hexa(Vec3[] c, Tex tex, int color) {
        quad(c[0], c[1], c[2], c[3], tex, color); // bottom
        quad(c[7], c[6], c[5], c[4], tex, color); // top
        quad(c[4], c[5], c[1], c[0], tex, color); // -z
        quad(c[6], c[7], c[3], c[2], tex, color); // +z
        quad(c[7], c[4], c[0], c[3], tex, color); // -x
        quad(c[5], c[6], c[2], c[1], tex, color); // +x
    }

    /** A sloped slab: a bottom edge line and a top edge line, both spanning x1..x2, with a thickness. */
    public void slope(double x1, double x2, double yBottom, double zBottom, double yTop, double zTop, double thickness, Tex tex, int color) {
        double t = thickness / 2;
        hexa(new Vec3[]{
                new Vec3(x1, yBottom, zBottom - t), new Vec3(x2, yBottom, zBottom - t), new Vec3(x2, yBottom, zBottom + t), new Vec3(x1, yBottom, zBottom + t),
                new Vec3(x1, yTop, zTop - t), new Vec3(x2, yTop, zTop - t), new Vec3(x2, yTop, zTop + t), new Vec3(x1, yTop, zTop + t)}, tex, color);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Tex tex, int color) {
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() < 1e-12) normal = c.subtract(b).cross(a.subtract(b));
        normal = normal.normalize();
        float uSpan = (float) Math.min(1, a.distanceTo(b) / TILE_SPAN) * tex.size();
        float vSpan = (float) Math.min(1, a.distanceTo(d) / TILE_SPAN) * tex.size();
        float u0 = tex.u0(), v0 = tex.v0();
        vertex(a, u0, v0, normal, color);
        vertex(b, u0 + uSpan, v0, normal, color);
        vertex(c, u0 + uSpan, v0 + vSpan, normal, color);
        vertex(d, u0, v0 + vSpan, normal, color);
    }

    /** Cylinder along the local X axis, centred on the current pose origin. */
    public void cylinderX(double radius, double halfWidth, int sides, Tex side, Tex cap, int color) {
        double previousY = radius, previousZ = 0;
        Vec3 left = new Vec3(-halfWidth, 0, 0), right = new Vec3(halfWidth, 0, 0);
        for (int i = 1; i <= sides; i++) {
            double angle = Math.PI * 2 * i / sides;
            double y = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
            Vec3 a = new Vec3(-halfWidth, previousY, previousZ), b = new Vec3(halfWidth, previousY, previousZ);
            Vec3 c = new Vec3(halfWidth, y, z), d = new Vec3(-halfWidth, y, z);
            quad(d, c, b, a, side, color);
            capSlice(right, b, c, cap, color, radius);
            capSlice(left, d, a, cap, color, radius);
            previousY = y;
            previousZ = z;
        }
    }

    /** Cylinder along an axis, by rotating a pose around {@link #cylinderX}. */
    public void cylinder(double x, double y, double z, char axis, double radius, double halfLength, int sides, Tex side, Tex cap, int color) {
        pose.pushPose();
        pose.translate(x, y, z);
        if (axis == 'y') pose.mulPose(Axis.ZP.rotationDegrees(90));
        if (axis == 'z') pose.mulPose(Axis.YP.rotationDegrees(90));
        cylinderX(radius, halfLength, sides, side, cap, color);
        pose.popPose();
    }

    private void capSlice(Vec3 centre, Vec3 a, Vec3 b, Tex tex, int color, double radius) {
        Vec3 normal = a.subtract(centre).cross(b.subtract(centre)).normalize();
        float half = tex.size() / 2, u = tex.u0() + half, v = tex.v0() + half;
        float scale = half / (float) radius;
        vertex(centre, u, v, normal, color);
        vertex(a, u + (float) a.z * scale, v - (float) a.y * scale, normal, color);
        vertex(b, u + (float) b.z * scale, v - (float) b.y * scale, normal, color);
        vertex(b, u + (float) b.z * scale, v - (float) b.y * scale, normal, color);
    }

    private void vertex(Vec3 p, float u, float v, Vec3 normal, int color) {
        PoseStack.Pose last = pose.last();
        buffer.addVertex(last, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
