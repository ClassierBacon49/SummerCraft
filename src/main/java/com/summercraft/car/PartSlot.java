package com.summercraft.car;

import com.summercraft.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Every mounting point on the car. Coordinates are car-local, in blocks: +X is the driver's (left) side,
 * +Y is up, +Z is the front, and the origin is the centre of the car at ground level.
 * Parts go onto their parent, so an engine is built up block, crank, pistons, heads, plugs.
 * Hidden parts stay hidden: the hood covers the engine bay and wheels cover the brakes, because aiming
 * always hits the nearest part.
 *
 * <p>Engine parts have no fixed geometry here: where they sit depends on the engine, see {@link EngineLayout}.
 */
public enum PartSlot {
    // Engine (geometry from EngineLayout)
    ENGINE_BLOCK(null, 13),
    CRANKSHAFT(ENGINE_BLOCK, 13),
    PISTONS(CRANKSHAFT, 11),
    CYLINDER_HEAD(PISTONS, 13),
    CYLINDER_HEAD_B(PISTONS, 13),
    SPARK_PLUGS(CYLINDER_HEAD, 16),
    SPARK_PLUGS_B(CYLINDER_HEAD_B, 16),
    CARBURETOR(CYLINDER_HEAD, 10),
    DISTRIBUTOR(ENGINE_BLOCK, 10),
    OIL_PAN(CRANKSHAFT, 8),
    TURBO(CYLINDER_HEAD, 12),
    SUPERCHARGER(CRANKSHAFT, 12),

    // Drivetrain: shared by every engine, and carried with it when it's lifted out
    CLUTCH(ModRegistry.CLUTCH, ENGINE_BLOCK, 10, box(-0.18, 0.3, 0.82, 0.18, 0.56, 0.92),
            bolts(Direction.UP, line(-0.12, 0.56, 0.87, 0.12, 0.56, 0.87, 4))),
    GEARBOX(ModRegistry.GEARBOX, CLUTCH, 13, box(-0.13, 0.3, 0.35, 0.13, 0.52, 0.82),
            bolts(Direction.UP, line(-0.09, 0.52, 0.7, 0.09, 0.52, 0.7), line(-0.09, 0.52, 0.78, 0.09, 0.52, 0.78))),
    STARTER(ModRegistry.STARTER_MOTOR, GEARBOX, 12, box(-0.3, 0.32, 0.7, -0.18, 0.44, 0.86),
            bolts(Direction.WEST, line(-0.3, 0.38, 0.74, -0.3, 0.38, 0.82))),

    // Car
    RADIATOR(ModRegistry.RADIATOR, null, 8, box(-0.32, 0.32, 1.66, 0.32, 0.74, 1.72),
            bolts(Direction.UP, line(-0.26, 0.74, 1.69, 0.26, 0.74, 1.69))),
    BATTERY(ModRegistry.BATTERY, null, 10, box(0.42, 0.3, 1.3, 0.62, 0.5, 1.6),
            bolts(Direction.UP, line(0.52, 0.5, 1.34, 0.52, 0.5, 1.56))),
    EXHAUST(ModRegistry.EXHAUST, null, 10, box(-0.5, 0.08, -1.98, -0.25, 0.24, 0.4),
            bolts(Direction.WEST, line(-0.5, 0.17, -1.5, -0.5, 0.17, -1.2))),
    FUEL_TANK(ModRegistry.FUEL_TANK, null, 10, box(-0.5, 0.28, -1.7, 0.5, 0.5, -1.3),
            bolts(Direction.UP, line(-0.35, 0.5, -1.5, 0.35, 0.5, -1.5))),
    BRAKES_F(ModRegistry.BRAKE_PADS, null, 12, box(-0.6, 0.12, 0.97, 0.6, 0.48, 1.33),
            bolts(Direction.UP, new Vec3(0.565, 0.48, 1.08), new Vec3(0.565, 0.48, 1.22), new Vec3(-0.565, 0.48, 1.08), new Vec3(-0.565, 0.48, 1.22))),
    BRAKES_R(ModRegistry.BRAKE_PADS, null, 12, box(-0.6, 0.12, -1.33, 0.6, 0.48, -0.97),
            bolts(Direction.UP, new Vec3(0.565, 0.48, -1.22), new Vec3(0.565, 0.48, -1.08), new Vec3(-0.565, 0.48, -1.22), new Vec3(-0.565, 0.48, -1.08))),
    WHEEL_FL(ModRegistry.WHEEL, null, 14, box(0.61, 0, 0.85, 0.79, 0.6, 1.45),
            bolts(Direction.EAST, ring(new Vec3(0.79, 0.3, 1.15), 0.07, 4))),
    WHEEL_FR(ModRegistry.WHEEL, null, 14, box(-0.79, 0, 0.85, -0.61, 0.6, 1.45),
            bolts(Direction.WEST, ring(new Vec3(-0.79, 0.3, 1.15), 0.07, 4))),
    WHEEL_RL(ModRegistry.WHEEL, null, 14, box(0.61, 0, -1.45, 0.79, 0.6, -0.85),
            bolts(Direction.EAST, ring(new Vec3(0.79, 0.3, -1.15), 0.07, 4))),
    WHEEL_RR(ModRegistry.WHEEL, null, 14, box(-0.79, 0, -1.45, -0.61, 0.6, -0.85),
            bolts(Direction.WEST, ring(new Vec3(-0.79, 0.3, -1.15), 0.07, 4))),
    SEAT_L(ModRegistry.CAR_SEAT, null, 12, box(0.1, 0.3, -0.6, 0.58, 1.05, 0.0),
            bolts(Direction.SOUTH, line(0.18, 0.36, 0.0, 0.5, 0.36, 0.0))),
    SEAT_R(ModRegistry.CAR_SEAT, null, 12, box(-0.58, 0.3, -0.6, -0.1, 1.05, 0.0),
            bolts(Direction.SOUTH, line(-0.5, 0.36, 0.0, -0.18, 0.36, 0.0))),
    STEERING_WHEEL(ModRegistry.STEERING_WHEEL, null, 14, box(0.17, 0.78, 0.18, 0.51, 1.12, 0.36),
            bolts(Direction.NORTH, new Vec3(0.34, 0.95, 0.24))),
    HOOD(ModRegistry.HOOD, null, 10, box(-0.76, 0.83, 0.6, 0.76, 0.87, 1.82),
            new Hinge(new Vec3(0, 0.87, 0.6), Direction.Axis.X, -55),
            bolts(Direction.UP, new Vec3(0.6, 0.87, 0.65), new Vec3(-0.6, 0.87, 0.65))),
    TRUNK_LID(ModRegistry.TRUNK_LID, null, 10, box(-0.74, 0.83, -1.82, 0.74, 0.87, -1.15),
            new Hinge(new Vec3(0, 0.87, -1.15), Direction.Axis.X, 60),
            bolts(Direction.UP, new Vec3(0.6, 0.87, -1.2), new Vec3(-0.6, 0.87, -1.2))),
    DOOR_L(ModRegistry.DOOR, null, 11, box(0.77, 0.3, -0.78, 0.82, 1.38, 0.52),
            new Hinge(new Vec3(0.8, 0, 0.52), Direction.Axis.Y, -70),
            bolts(Direction.EAST, new Vec3(0.82, 0.45, 0.46), new Vec3(0.82, 0.75, 0.46))),
    DOOR_R(ModRegistry.DOOR, null, 11, box(-0.82, 0.3, -0.78, -0.77, 1.38, 0.52),
            new Hinge(new Vec3(-0.8, 0, 0.52), Direction.Axis.Y, 70),
            bolts(Direction.WEST, new Vec3(-0.82, 0.45, 0.46), new Vec3(-0.82, 0.75, 0.46))),
    FENDER_L(ModRegistry.FENDER, null, 8, box(0.76, 0.3, 0.6, 0.83, 0.87, 1.85),
            bolts(Direction.UP, line(0.795, 0.87, 0.7, 0.795, 0.87, 1.75, 3))),
    FENDER_R(ModRegistry.FENDER, null, 8, box(-0.83, 0.3, 0.6, -0.76, 0.87, 1.85),
            bolts(Direction.UP, line(-0.795, 0.87, 0.7, -0.795, 0.87, 1.75, 3))),
    BUMPER_F(ModRegistry.BUMPER, null, 10, box(-0.84, 0.26, 1.85, 0.84, 0.42, 1.97),
            bolts(Direction.SOUTH, new Vec3(0.5, 0.34, 1.97), new Vec3(-0.5, 0.34, 1.97))),
    BUMPER_R(ModRegistry.BUMPER, null, 10, box(-0.84, 0.26, -1.97, 0.84, 0.42, -1.85),
            bolts(Direction.NORTH, new Vec3(0.5, 0.34, -1.97), new Vec3(-0.5, 0.34, -1.97)));

    /** Bolt sizes a wrench can be set to, in mm. */
    public static final int[] WRENCH_SIZES = {8, 10, 11, 12, 13, 14, 16};

    /** A panel that swings open around a pivot. */
    public record Hinge(Vec3 pivot, Direction.Axis axis, float openDegrees) {}

    @Nullable
    private final Supplier<? extends Item> item;
    @Nullable
    private final PartSlot parent;
    private final int boltSize;
    @Nullable
    private final Hinge hinge;
    /** Fixed geometry, or null for engine parts. */
    @Nullable
    private final EngineLayout.Geometry geometry;

    /** An engine part: its item and geometry depend on the engine. */
    PartSlot(@Nullable PartSlot parent, int boltSize) {
        this.item = null;
        this.parent = parent;
        this.boltSize = boltSize;
        this.hinge = null;
        this.geometry = null;
    }

    PartSlot(Supplier<? extends Item> item, @Nullable PartSlot parent, int boltSize, AABB bounds, EngineLayout.Geometry bolts) {
        this(item, parent, boltSize, bounds, null, bolts);
    }

    PartSlot(Supplier<? extends Item> item, @Nullable PartSlot parent, int boltSize, AABB bounds, @Nullable Hinge hinge, EngineLayout.Geometry bolts) {
        this.item = item;
        this.parent = parent;
        this.boltSize = boltSize;
        this.hinge = hinge;
        this.geometry = new EngineLayout.Geometry(bounds, bolts.face(), bolts.bolts(), List.of());
    }

    /** True for parts that sit on the engine and take their place from {@link EngineLayout}. */
    public boolean isEngine() {
        return geometry == null;
    }

    /** Parts that come out with the engine when it's lifted: the engine itself plus clutch, gearbox and starter. */
    public boolean isPowertrain() {
        return isEngine() || this == CLUTCH || this == GEARBOX || this == STARTER;
    }

    /** The item for this slot. For the engine block it depends on the engine type, see {@link EngineType}. */
    public Item item() {
        if (item != null) return item.get();
        return switch (this) {
            case ENGINE_BLOCK -> ModRegistry.ENGINE_BLOCK.get();
            case CRANKSHAFT -> ModRegistry.CRANKSHAFT.get();
            case PISTONS -> ModRegistry.PISTONS.get();
            case CYLINDER_HEAD, CYLINDER_HEAD_B -> ModRegistry.CYLINDER_HEAD.get();
            case SPARK_PLUGS, SPARK_PLUGS_B -> ModRegistry.SPARK_PLUGS.get();
            case CARBURETOR -> ModRegistry.CARBURETOR.get();
            case DISTRIBUTOR -> ModRegistry.DISTRIBUTOR.get();
            case OIL_PAN -> ModRegistry.OIL_PAN.get();
            case TURBO -> ModRegistry.TURBO.get();
            case SUPERCHARGER -> ModRegistry.SUPERCHARGER.get();
            default -> throw new IllegalStateException(name());
        };
    }

    @Nullable
    public PartSlot parent() {
        return parent;
    }

    public int boltSize() {
        return boltSize;
    }

    @Nullable
    public Hinge hinge() {
        return hinge;
    }

    /** Fixed geometry for non-engine parts. Engine parts ask {@link EngineLayout}. */
    public EngineLayout.Geometry fixedGeometry() {
        if (geometry == null) throw new IllegalStateException(name() + " is an engine part");
        return geometry;
    }

    /** Moves a car-local point along with the panel, {@code amount} 0 = closed, 1 = fully open. */
    public Vec3 swing(Vec3 point, float amount) {
        if (hinge == null || amount <= 0) return point;
        double angle = Math.toRadians(hinge.openDegrees() * amount);
        Vec3 rel = point.subtract(hinge.pivot());
        double cos = Math.cos(angle), sin = Math.sin(angle);
        Vec3 turned = hinge.axis() == Direction.Axis.X
                ? new Vec3(rel.x, rel.y * cos - rel.z * sin, rel.y * sin + rel.z * cos)
                : new Vec3(rel.x * cos + rel.z * sin, rel.y, -rel.x * sin + rel.z * cos);
        return turned.add(hinge.pivot());
    }

    public boolean accepts(Item candidate) {
        if (this == ENGINE_BLOCK) return EngineType.byBlock(candidate) != null;
        return item() == candidate;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("slot.summercraft." + id());
    }

    public static List<PartSlot> forItem(Item item) {
        return Arrays.stream(values()).filter(slot -> slot.accepts(item)).toList();
    }

    private static AABB box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return new AABB(x1, y1, z1, x2, y2, z2);
    }

    private static EngineLayout.Geometry bolts(Direction face, Vec3... points) {
        return new EngineLayout.Geometry(new AABB(0, 0, 0, 0, 0, 0), face, List.of(points), List.of());
    }

    private static EngineLayout.Geometry bolts(Direction face, Vec3[]... groups) {
        List<Vec3> all = new ArrayList<>();
        for (Vec3[] group : groups) all.addAll(Arrays.asList(group));
        return new EngineLayout.Geometry(new AABB(0, 0, 0, 0, 0, 0), face, List.copyOf(all), List.of());
    }

    private static Vec3[] line(double x1, double y1, double z1, double x2, double y2, double z2) {
        return line(x1, y1, z1, x2, y2, z2, 2);
    }

    private static Vec3[] line(double x1, double y1, double z1, double x2, double y2, double z2, int count) {
        Vec3[] points = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double t = count == 1 ? 0.5 : (double) i / (count - 1);
            points[i] = new Vec3(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t);
        }
        return points;
    }

    /** Points on a circle facing along X, like wheel nuts. */
    private static Vec3[] ring(Vec3 centre, double radius, int count) {
        Vec3[] points = new Vec3[count];
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count + Math.PI / 4;
            points[i] = centre.add(0, Math.cos(a) * radius, Math.sin(a) * radius);
        }
        return points;
    }
}
