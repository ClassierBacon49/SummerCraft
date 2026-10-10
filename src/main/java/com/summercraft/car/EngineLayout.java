package com.summercraft.car;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where each engine part sits for each {@link EngineType}, in car-local coordinates with the back of the block
 * against the bellhousing at z = 0.92. Every engine shares the clutch, gearbox and starter positions, so any
 * engine bolts up to the same gearbox. On the engine stand the whole layout is shifted by the stand's offset.
 */
public final class EngineLayout {
    public enum Shape { BOX, CYL_X, CYL_Y, CYL_Z }

    public enum Material { IRON, ALUMINIUM, RED, BLACK, CERAMIC, CHROME, EXHAUST }

    /** A visual piece of a part: a box or a cylinder filling the box. */
    public record Piece(Shape shape, AABB box, Material material) {}

    /** Aiming box, bolt heads and look of one part. */
    public record Geometry(AABB bounds, Direction face, List<Vec3> bolts, List<Piece> pieces) {
        public int boltCount() {
            return bolts.size();
        }
    }

    private static final Map<EngineType, Map<PartSlot, Geometry>> LAYOUTS = new EnumMap<>(EngineType.class);

    static {
        LAYOUTS.put(EngineType.INLINE_4, inline4());
        LAYOUTS.put(EngineType.BOXER_4, boxer4());
        LAYOUTS.put(EngineType.V6, vee(6, 1.36, 0.1, 0.32, 0.64));
        LAYOUTS.put(EngineType.V8, vee(8, 1.5, 0.11, 0.34, 0.65));
    }

    /** The geometry of an engine slot for this engine type, or null if this engine has no such part. */
    public static Geometry get(EngineType type, PartSlot slot) {
        return LAYOUTS.get(type).get(slot);
    }

    /** The same geometry moved by {@code offset}, for the engine stand. */
    public static Geometry shifted(Geometry g, Vec3 offset) {
        List<Vec3> bolts = new ArrayList<>(g.bolts().size());
        for (Vec3 bolt : g.bolts()) bolts.add(bolt.add(offset));
        List<Piece> pieces = new ArrayList<>(g.pieces().size());
        for (Piece piece : g.pieces()) pieces.add(new Piece(piece.shape(), piece.box().move(offset), piece.material()));
        return new Geometry(g.bounds().move(offset), g.face(), bolts, pieces);
    }

    // ---------------------------------------------------------------- inline four

    private static Map<PartSlot, Geometry> inline4() {
        Map<PartSlot, Geometry> m = new EnumMap<>(PartSlot.class);
        m.put(PartSlot.ENGINE_BLOCK, new Builder(-0.22, 0.3, 0.92, 0.22, 0.6, 1.42, Direction.EAST)
                .bolts(pt(0.22, 0.38, 1.0), pt(0.22, 0.52, 1.0), pt(0.22, 0.38, 1.34), pt(0.22, 0.52, 1.34))
                .box(-0.22, 0.3, 0.92, 0.22, 0.6, 1.42, Material.IRON)
                .box(-0.25, 0.42, 0.98, -0.22, 0.5, 1.36, Material.EXHAUST).build());
        m.put(PartSlot.CRANKSHAFT, pulley(1.42, 0.39, 0.09));
        Builder pistons = new Builder(-0.19, 0.6, 0.95, 0.19, 0.64, 1.39, Direction.UP).bolts(line(0, 0.64, 1.0, 0, 0.64, 1.34, 4));
        for (int i = 0; i < 4; i++) {
            double z = 1.0 + i * 0.113;
            pistons.piece(Shape.CYL_Y, -0.05, 0.6, z - 0.05, 0.05, 0.64, z + 0.05, Material.ALUMINIUM);
        }
        m.put(PartSlot.PISTONS, pistons.build());
        m.put(PartSlot.CYLINDER_HEAD, new Builder(-0.21, 0.64, 0.93, 0.21, 0.74, 1.41, Direction.UP)
                .bolts(line(0.15, 0.74, 0.99, 0.15, 0.74, 1.35, 3)).bolts(line(-0.15, 0.74, 0.99, -0.15, 0.74, 1.35, 3))
                .box(-0.21, 0.64, 0.93, 0.21, 0.74, 1.41, Material.RED)
                .box(-0.21, 0.74, 0.93, -0.06, 0.755, 1.41, Material.RED)
                .box(0.06, 0.74, 0.93, 0.21, 0.755, 1.41, Material.RED).build());
        m.put(PartSlot.SPARK_PLUGS, plugsUp(0, 0.74, line(0, 0.8, 1.0, 0, 0.8, 1.34, 4)));
        m.put(PartSlot.CARBURETOR, carb(0.22, 0.36, 0.55, 1.08, 1.26, 0.72));
        m.put(PartSlot.DISTRIBUTOR, new Builder(-0.33, 0.48, 1.26, -0.22, 0.68, 1.38, Direction.WEST)
                .bolts(pt(-0.33, 0.52, 1.32))
                .piece(Shape.CYL_Y, -0.32, 0.48, 1.275, -0.23, 0.64, 1.365, Material.BLACK)
                .piece(Shape.CYL_Y, -0.325, 0.64, 1.27, -0.225, 0.68, 1.37, Material.RED).build());
        m.put(PartSlot.OIL_PAN, oilPan(0.2, 0.95, 1.4));
        m.put(PartSlot.TURBO, turbo(-0.42, 0.42, 1.0));
        m.put(PartSlot.SUPERCHARGER, supercharger(0.12, 0.48, 1.42));
        return m;
    }

    // ---------------------------------------------------------------- flat four

    private static Map<PartSlot, Geometry> boxer4() {
        Map<PartSlot, Geometry> m = new EnumMap<>(PartSlot.class);
        m.put(PartSlot.ENGINE_BLOCK, new Builder(-0.2, 0.3, 0.92, 0.2, 0.52, 1.38, Direction.UP)
                .bolts(pt(0.15, 0.52, 0.97), pt(-0.15, 0.52, 0.97), pt(0.15, 0.52, 1.33), pt(-0.15, 0.52, 1.33))
                .box(-0.2, 0.3, 0.92, 0.2, 0.52, 1.38, Material.IRON).build());
        m.put(PartSlot.CRANKSHAFT, pulley(1.38, 0.41, 0.09));
        Builder pistons = new Builder(-0.24, 0.33, 0.96, 0.24, 0.49, 1.34, Direction.UP)
                .bolts(pt(0.22, 0.49, 1.04), pt(0.22, 0.49, 1.26), pt(-0.22, 0.49, 1.04), pt(-0.22, 0.49, 1.26));
        for (double z : new double[]{1.04, 1.26}) {
            for (int side = -1; side <= 1; side += 2) {
                pistons.piece(Shape.CYL_X, side > 0 ? 0.2 : -0.24, 0.34, z - 0.07, side > 0 ? 0.24 : -0.2, 0.48, z + 0.07, Material.ALUMINIUM);
            }
        }
        m.put(PartSlot.PISTONS, pistons.build());
        for (int side = -1; side <= 1; side += 2) {
            PartSlot head = side > 0 ? PartSlot.CYLINDER_HEAD : PartSlot.CYLINDER_HEAD_B;
            PartSlot plugs = side > 0 ? PartSlot.SPARK_PLUGS : PartSlot.SPARK_PLUGS_B;
            Direction out = side > 0 ? Direction.EAST : Direction.WEST;
            double inner = side * 0.24, outer = side * 0.36;
            m.put(head, new Builder(inner, 0.3, 0.95, outer, 0.52, 1.35, out)
                    .bolts(pt(outer, 0.34, 0.99), pt(outer, 0.48, 0.99), pt(outer, 0.34, 1.31), pt(outer, 0.48, 1.31))
                    .box(inner, 0.3, 0.95, outer, 0.52, 1.35, Material.RED).build());
            Builder plug = new Builder(outer, 0.39, 1.02, side * 0.42, 0.43, 1.28, out);
            for (double z : new double[]{1.04, 1.26}) {
                plug.bolts(pt(side * 0.42, 0.41, z));
                plug.piece(Shape.CYL_X, outer, 0.392, z - 0.018, side * 0.41, 0.428, z + 0.018, Material.CERAMIC);
            }
            m.put(plugs, plug.build());
        }
        m.put(PartSlot.CARBURETOR, new Builder(-0.1, 0.52, 1.05, 0.1, 0.7, 1.25, Direction.UP)
                .bolts(line(0, 0.7, 1.09, 0, 0.7, 1.21, 2))
                .box(-0.07, 0.52, 1.07, 0.07, 0.64, 1.23, Material.ALUMINIUM)
                .piece(Shape.CYL_Y, -0.1, 0.64, 1.05, 0.1, 0.7, 1.25, Material.BLACK).build());
        m.put(PartSlot.DISTRIBUTOR, new Builder(-0.16, 0.52, 0.93, -0.08, 0.7, 1.01, Direction.WEST)
                .bolts(pt(-0.16, 0.56, 0.97))
                .piece(Shape.CYL_Y, -0.16, 0.52, 0.93, -0.08, 0.66, 1.01, Material.BLACK)
                .piece(Shape.CYL_Y, -0.165, 0.66, 0.925, -0.075, 0.7, 1.015, Material.RED).build());
        m.put(PartSlot.OIL_PAN, oilPan(0.15, 0.97, 1.33));
        m.put(PartSlot.TURBO, turbo(-0.52, 0.5, 1.0));
        m.put(PartSlot.SUPERCHARGER, supercharger(0.1, 0.5, 1.4));
        return m;
    }

    // ---------------------------------------------------------------- V6 and V8

    private static Map<PartSlot, Geometry> vee(int cylinders, double front, double headInner, double headOuter, double headTop) {
        int perBank = cylinders / 2;
        Map<PartSlot, Geometry> m = new EnumMap<>(PartSlot.class);
        double halfWidth = cylinders == 8 ? 0.22 : 0.2;
        m.put(PartSlot.ENGINE_BLOCK, new Builder(-halfWidth, 0.3, 0.92, halfWidth, 0.52, front, Direction.EAST)
                .bolts(pt(halfWidth, 0.36, 0.98), pt(halfWidth, 0.46, 0.98), pt(halfWidth, 0.36, front - 0.06), pt(halfWidth, 0.46, front - 0.06))
                .box(-halfWidth, 0.3, 0.92, halfWidth, 0.52, front, Material.IRON).build());
        m.put(PartSlot.CRANKSHAFT, pulley(front, 0.39, cylinders == 8 ? 0.1 : 0.09));

        double first = 1.0, spacing = (front - 0.08 - first) / (perBank - 1);
        Builder pistons = new Builder(-headInner - 0.06, 0.52, 0.96, headInner + 0.06, 0.56, front - 0.04, Direction.UP);
        for (int i = 0; i < perBank; i++) {
            double z = first + i * spacing;
            for (int side = -1; side <= 1; side += 2) {
                pistons.bolts(pt(side * 0.08, 0.56, z));
                pistons.piece(Shape.CYL_Y, side * 0.08 - 0.04, 0.52, z - 0.04, side * 0.08 + 0.04, 0.56, z + 0.04, Material.ALUMINIUM);
            }
        }
        m.put(PartSlot.PISTONS, pistons.build());

        for (int side = -1; side <= 1; side += 2) {
            PartSlot head = side > 0 ? PartSlot.CYLINDER_HEAD : PartSlot.CYLINDER_HEAD_B;
            PartSlot plugs = side > 0 ? PartSlot.SPARK_PLUGS : PartSlot.SPARK_PLUGS_B;
            Direction out = side > 0 ? Direction.EAST : Direction.WEST;
            double boltX = side * (headOuter - 0.05);
            m.put(head, new Builder(side * headInner, 0.52, 0.94, side * headOuter, headTop, front - 0.02, Direction.UP)
                    .bolts(line(boltX, headTop, 0.99, boltX, headTop, front - 0.07, perBank))
                    .box(side * headInner, 0.52, 0.94, side * headOuter, headTop, front - 0.02, Material.RED)
                    .box(side * (headInner + 0.02), headTop, 0.96, side * (headOuter - 0.02), headTop + 0.015, front - 0.04, Material.RED).build());
            Builder plug = new Builder(side * headOuter, 0.56, 0.97, side * (headOuter + 0.05), 0.6, front - 0.05, out);
            for (int i = 0; i < perBank; i++) {
                double z = first + i * spacing;
                plug.bolts(pt(side * (headOuter + 0.05), 0.58, z));
                plug.piece(Shape.CYL_X, side * headOuter, 0.562, z - 0.018, side * (headOuter + 0.04), 0.598, z + 0.018, Material.CERAMIC);
            }
            m.put(plugs, plug.build());
        }
        double carbHalf = cylinders == 8 ? 0.12 : 0.1;
        double carbTop = cylinders == 8 ? 0.78 : 0.76;
        double carbFront = cylinders == 8 ? 1.33 : 1.24;
        m.put(PartSlot.CARBURETOR, new Builder(-carbHalf, 0.56, 1.04, carbHalf, carbTop, carbFront, Direction.UP)
                .bolts(line(0, carbTop, 1.08, 0, carbTop, carbFront - 0.04, 2))
                .box(-carbHalf + 0.03, 0.56, 1.06, carbHalf - 0.03, carbTop - 0.06, carbFront - 0.02, Material.ALUMINIUM)
                .piece(Shape.CYL_Y, -carbHalf, carbTop - 0.06, 1.04, carbHalf, carbTop, 1.04 + carbHalf * 2, Material.BLACK).build());
        m.put(PartSlot.DISTRIBUTOR, new Builder(-0.05, 0.56, 0.93, 0.05, 0.74, 1.01, Direction.NORTH)
                .bolts(pt(0, 0.6, 0.93))
                .piece(Shape.CYL_Y, -0.045, 0.56, 0.925, 0.045, 0.7, 1.015, Material.BLACK)
                .piece(Shape.CYL_Y, -0.05, 0.7, 0.92, 0.05, 0.74, 1.02, Material.RED).build());
        m.put(PartSlot.OIL_PAN, oilPan(halfWidth - 0.02, 0.95, front - 0.03));
        m.put(PartSlot.TURBO, turbo(-headOuter - 0.2, 0.52, 1.02));
        m.put(PartSlot.SUPERCHARGER, supercharger(0.12, 0.42, front + 0.02));
        return m;
    }

    // ---------------------------------------------------------------- shared parts

    private static Geometry pulley(double z, double y, double r) {
        return new Builder(-r, y - r, z, r, y + r, z + 0.05, Direction.SOUTH)
                .bolts(ring(new Vec3(0, y, z + 0.05), 0.05, 4))
                .piece(Shape.CYL_Z, -r, y - r, z, r, y + r, z + 0.05, Material.ALUMINIUM).build();
    }

    private static Geometry plugsUp(double x, double y, Vec3[] tops) {
        Builder b = new Builder(x - 0.03, y, tops[0].z - 0.03, x + 0.03, y + 0.06, tops[tops.length - 1].z + 0.03, Direction.UP).bolts(tops);
        for (Vec3 top : tops) {
            b.piece(Shape.CYL_Y, top.x - 0.018, y, top.z - 0.018, top.x + 0.018, y + 0.05, top.z + 0.018, Material.CERAMIC);
            b.piece(Shape.CYL_Y, top.x - 0.01, y + 0.05, top.z - 0.01, top.x + 0.01, y + 0.06, top.z + 0.01, Material.BLACK);
        }
        return b.build();
    }

    private static Geometry carb(double x1, double x2, double y1, double z1, double z2, double top) {
        double cx = (x1 + x2) / 2;
        return new Builder(x1, y1, z1, x2, top, z2, Direction.UP)
                .bolts(line(cx, top, z1 + 0.03, cx, top, z2 - 0.03, 2))
                .box(x1, y1, z1, x2 - 0.02, top - 0.04, z2, Material.ALUMINIUM)
                .piece(Shape.CYL_Y, cx - 0.09, top - 0.04, (z1 + z2) / 2 - 0.09, cx + 0.09, top, (z1 + z2) / 2 + 0.09, Material.BLACK).build();
    }

    private static Geometry oilPan(double halfWidth, double z1, double z2) {
        return new Builder(-halfWidth, 0.22, z1, halfWidth, 0.3, z2, Direction.EAST)
                .bolts(line(halfWidth, 0.26, z1 + 0.05, halfWidth, 0.26, z2 - 0.05, 4))
                .box(-halfWidth, 0.22, z1, halfWidth, 0.3, z2, Material.BLACK).build();
    }

    /** A turbo hanging off the exhaust side: hot iron turbine housing, aluminium compressor. */
    private static Geometry turbo(double x, double y, double z) {
        return new Builder(x, y, z, x + 0.16, y + 0.18, z + 0.18, Direction.UP)
                .bolts(pt(x + 0.08, y + 0.18, z + 0.04), pt(x + 0.08, y + 0.18, z + 0.14))
                .piece(Shape.CYL_X, x, y, z, x + 0.1, y + 0.18, z + 0.18, Material.EXHAUST)
                .piece(Shape.CYL_X, x + 0.1, y + 0.02, z + 0.02, x + 0.16, y + 0.16, z + 0.16, Material.ALUMINIUM).build();
    }

    /** A belt-driven blower at the front, run off the crank pulley. */
    private static Geometry supercharger(double x, double y, double z) {
        return new Builder(x, y, z, x + 0.2, y + 0.16, z + 0.16, Direction.UP)
                .bolts(pt(x + 0.05, y + 0.16, z + 0.08), pt(x + 0.15, y + 0.16, z + 0.08))
                .box(x, y, z, x + 0.2, y + 0.14, z + 0.13, Material.ALUMINIUM)
                .piece(Shape.CYL_Z, x + 0.05, y + 0.02, z + 0.13, x + 0.15, y + 0.12, z + 0.16, Material.CHROME).build();
    }

    // ---------------------------------------------------------------- helpers

    private static Vec3 pt(double x, double y, double z) {
        return new Vec3(x, y, z);
    }

    private static Vec3[] line(double x1, double y1, double z1, double x2, double y2, double z2, int count) {
        Vec3[] points = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            points[i] = new Vec3(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t);
        }
        return points;
    }

    private static Vec3[] ring(Vec3 centre, double radius, int count) {
        Vec3[] points = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + Math.PI / 4;
            points[i] = centre.add(Math.cos(a) * radius, Math.sin(a) * radius, 0);
        }
        return points;
    }

    private static final class Builder {
        private final AABB bounds;
        private final Direction face;
        private final List<Vec3> bolts = new ArrayList<>();
        private final List<Piece> pieces = new ArrayList<>();

        Builder(double x1, double y1, double z1, double x2, double y2, double z2, Direction face) {
            this.bounds = new AABB(x1, y1, z1, x2, y2, z2);
            this.face = face;
        }

        Builder bolts(Vec3... points) {
            bolts.addAll(List.of(points));
            return this;
        }

        Builder box(double x1, double y1, double z1, double x2, double y2, double z2, Material material) {
            return piece(Shape.BOX, x1, y1, z1, x2, y2, z2, material);
        }

        Builder piece(Shape shape, double x1, double y1, double z1, double x2, double y2, double z2, Material material) {
            pieces.add(new Piece(shape, new AABB(x1, y1, z1, x2, y2, z2), material));
            return this;
        }

        Geometry build() {
            return new Geometry(bounds, face, List.copyOf(bolts), List.copyOf(pieces));
        }
    }

    private EngineLayout() {}
}
