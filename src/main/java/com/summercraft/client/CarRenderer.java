package com.summercraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.summercraft.SummerCraft;
import com.summercraft.car.CarEntity;
import com.summercraft.car.CarPhysics;
import com.summercraft.car.CarSetup;
import com.summercraft.car.EngineLayout;
import com.summercraft.car.EngineType;
import com.summercraft.car.PartSlot;
import com.summercraft.item.PartItem;
import com.summercraft.item.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import static com.summercraft.car.PartSlot.*;

/** Draws the car from textured solids: shell, bolt-on parts, engine internals, wheels and visible bolt heads. */
public class CarRenderer extends EntityRenderer<CarEntity> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "textures/entity/car.png");
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GHOST = 0x6070C8FF;

    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2f;
    }

    @Override
    public void render(CarEntity car, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        // Sit on the slope under the wheels.
        pose.mulPose(Axis.XP.rotation(-Mth.lerp(partialTick, car.terrainPitchO, car.terrainPitch)));
        pose.mulPose(Axis.ZP.rotation(Mth.lerp(partialTick, car.terrainRollO, car.terrainRoll)));

        Shapes solid = new Shapes(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light);

        if (car.isStand()) {
            drawStand(solid);
            drawEngine(car, solid, partialTick);
            drawBolts(car, solid);
            drawAimHelpers(car, pose, buffers, light);
            pose.popPose();
            super.render(car, yaw, partialTick, pose, buffers, light);
            return;
        }

        // Unsprung parts: wheels and brakes don't lean with the body.
        CarSetup setup = car.getSetup();
        if (car.liftHeight() > 0.01f) drawLift(solid, car.liftHeight());
        drawWheels(car, solid, setup);
        drawDriveshafts(car, solid, setup);
        if (car.has(BRAKES_F)) drawBrakes(solid, 1.15);
        if (car.has(BRAKES_R)) drawBrakes(solid, -1.15);

        pose.pushPose();
        // Ride height raises or drops the body; the wheels stay where they are.
        pose.translate(0, 0.3 + setup.rideHeight() * 0.01, 0);
        pose.mulPose(Axis.ZP.rotation(car.bodyRoll));
        pose.mulPose(Axis.XP.rotation(car.bodyPitch));
        pose.translate(0, -0.3, 0);

        int paint = car.getColor();
        drawShell(solid, paint, car.isRunning());
        drawExhaust(car, solid, setup);
        drawEngine(car, solid, partialTick);
        drawCabin(car, solid);
        drawPanels(car, solid, paint);
        drawBolts(car, solid);
        // Fetching another buffer ends the solid one, so everything solid has to be drawn before this point.
        drawGlass(car, new Shapes(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light));
        drawAimHelpers(car, pose, buffers, light);

        pose.popPose();
        pose.popPose();
        super.render(car, yaw, partialTick, pose, buffers, light);
    }

    // ---------------------------------------------------------------- body

    private static void drawShell(Shapes s, int paint, boolean running) {
        int baseLight = s.light();
        // Floor, sills, firewall
        s.box(-0.76, 0.22, -1.82, 0.76, 0.28, 0.58, Tex.INTERIOR, WHITE);
        for (int side = -1; side <= 1; side += 2) {
            s.box(side * 0.74, 0.22, -0.8, side * 0.8, 0.32, 0.6, Tex.PAINT, paint);
            // Rear quarter panel around the rear wheel arch
            s.box(side * 0.74, 0.62, -1.85, side * 0.8, 0.87, -0.78, Tex.PAINT, paint);
            s.box(side * 0.74, 0.32, -0.85, side * 0.8, 0.62, -0.78, Tex.PAINT, paint);
            s.box(side * 0.74, 0.32, -1.85, side * 0.8, 0.62, -1.48, Tex.PAINT, paint);
            s.box(side * 0.6, 0.6, -1.48, side * 0.74, 0.64, -0.85, Tex.FRAME, WHITE);
            // Engine bay inner wings and front arch
            s.box(side * 0.6, 0.32, 0.62, side * 0.64, 0.82, 1.8, Tex.PAINT, paint);
            s.box(side * 0.64, 0.6, 0.85, side * 0.76, 0.64, 1.48, Tex.FRAME, WHITE);
            // Chassis rails
            s.box(side * 0.36, 0.2, 0.6, side * 0.44, 0.28, 1.8, Tex.FRAME, WHITE);
            // Pillars: A (windscreen), B (behind the door), C (rear window)
            s.hexa(new Vec3[]{
                    new Vec3(side * 0.70, 0.86, 0.52), new Vec3(side * 0.76, 0.86, 0.52), new Vec3(side * 0.76, 0.86, 0.6), new Vec3(side * 0.70, 0.86, 0.6),
                    new Vec3(side * 0.68, 1.48, 0.08), new Vec3(side * 0.74, 1.48, 0.08), new Vec3(side * 0.74, 1.48, 0.14), new Vec3(side * 0.68, 1.48, 0.14)},
                    Tex.PAINT, paint);
            s.box(side * 0.72, 0.86, -0.82, side * 0.77, 1.47, -0.76, Tex.PAINT, paint);
            s.hexa(new Vec3[]{
                    new Vec3(side * 0.66, 0.86, -1.18), new Vec3(side * 0.77, 0.86, -1.18), new Vec3(side * 0.77, 0.86, -1.1), new Vec3(side * 0.66, 0.86, -1.1),
                    new Vec3(side * 0.66, 1.48, -0.82), new Vec3(side * 0.74, 1.48, -0.82), new Vec3(side * 0.74, 1.48, -0.74), new Vec3(side * 0.66, 1.48, -0.74)},
                    Tex.PAINT, paint);
            // Lights
            s.light(running ? LightTexture.FULL_BRIGHT : baseLight);
            s.cylinder(side * 0.58, 0.6, 1.86, 'z', 0.085, 0.02, 12, Tex.CHROME, Tex.HEADLIGHT, WHITE);
            s.box(side * 0.5, 0.64, -1.89, side * 0.72, 0.76, -1.87, Tex.TAILLIGHT, WHITE);
            s.box(side * 0.72, 0.5, 1.83, side * 0.78, 0.56, 1.87, Tex.INDICATOR, WHITE);
            s.light(baseLight);
        }

        s.box(-0.76, 0.28, 0.55, 0.76, 0.85, 0.6, Tex.PAINT, paint);           // firewall
        s.box(-0.76, 0.85, 0.55, 0.76, 0.88, 0.62, Tex.PAINT, paint);          // cowl
        s.box(-0.76, 0.68, 0.32, 0.76, 0.86, 0.55, Tex.DASH, WHITE);           // dashboard
        s.box(-0.76, 0.86, 0.3, 0.76, 0.88, 0.58, Tex.BLACK_PLASTIC, WHITE);   // dash top
        s.box(-0.72, 1.46, -0.82, 0.72, 1.51, 0.14, Tex.PAINT, paint);         // roof
        s.box(-0.7, 1.44, -0.78, 0.7, 1.46, 0.1, Tex.INTERIOR, WHITE);         // headliner
        s.box(-0.8, 0.32, -1.88, 0.8, 0.87, -1.82, Tex.PAINT, paint);          // rear panel
        s.box(-0.74, 0.84, -1.16, 0.74, 0.88, -1.1, Tex.PAINT, paint);         // rear deck
        s.box(-0.8, 0.3, 1.82, 0.8, 0.82, 1.86, Tex.PAINT, paint);             // front panel
        s.box(-0.42, 0.42, 1.86, 0.42, 0.72, 1.875, Tex.GRILLE, WHITE);        // grille
        s.box(-0.76, 0.2, 1.78, 0.76, 0.3, 1.86, Tex.FRAME, WHITE);            // front valance
        s.box(-0.12, 0.28, -0.3, 0.12, 0.4, 0.55, Tex.INTERIOR, WHITE);        // transmission tunnel
        s.box(-0.44, 0.2, 1.6, 0.44, 0.28, 1.66, Tex.FRAME, WHITE);            // radiator support
        s.box(0.4, 0.28, 1.28, 0.64, 0.3, 1.62, Tex.FRAME, WHITE);             // battery tray
        s.box(-0.62, 0.24, -1.19, 0.62, 0.34, -1.11, Tex.FRAME, WHITE);        // rear axle
        s.box(-0.62, 0.24, 1.11, 0.62, 0.3, 1.19, Tex.FRAME, WHITE);           // front crossmember
        // Rear bench
        s.box(-0.68, 0.28, -1.05, 0.68, 0.48, -0.65, Tex.FABRIC, WHITE);
        s.slope(-0.68, 0.68, 0.48, -1.04, 0.95, -1.1, 0.1, Tex.FABRIC, WHITE);
        s.box(-0.72, 0.84, -1.15, 0.72, 0.87, -1.0, Tex.INTERIOR, WHITE);      // parcel shelf
    }

    private static void drawGlass(CarEntity car, Shapes glass) {
        int tint = 0x80FFFFFF;
        glass.slope(-0.7, 0.7, 0.87, 0.57, 1.46, 0.12, 0.01, Tex.GLASS, tint);
        glass.slope(-0.68, 0.68, 0.87, -1.13, 1.46, -0.79, 0.01, Tex.GLASS, tint);
        for (PartSlot door : new PartSlot[]{DOOR_L, DOOR_R}) {
            if (!car.has(door)) continue;
            int side = door == DOOR_L ? 1 : -1;
            glass.pose().pushPose();
            hinge(glass.pose(), door, car.openAmount(door));
            glass.hexa(new Vec3[]{
                    new Vec3(side * 0.785, 0.87, -0.73), new Vec3(side * 0.795, 0.87, -0.73), new Vec3(side * 0.795, 0.87, 0.44), new Vec3(side * 0.785, 0.87, 0.44),
                    new Vec3(side * 0.785, 1.42, -0.73), new Vec3(side * 0.795, 1.42, -0.73), new Vec3(side * 0.795, 1.42, 0.1), new Vec3(side * 0.785, 1.42, 0.1)},
                    Tex.GLASS, tint);
            glass.pose().popPose();
        }
    }

    private static void drawPanels(CarEntity car, Shapes s, int paint) {
        PoseStack pose = s.pose();
        if (car.has(HOOD)) {
            pose.pushPose();
            hinge(pose, HOOD, car.openAmount(HOOD));
            s.box(-0.76, 0.83, 0.62, 0.76, 0.87, 1.84, Tex.PAINT, paint);
            s.box(-0.3, 0.87, 0.7, 0.3, 0.885, 1.8, Tex.PAINT, paint);
            pose.popPose();
        }
        if (car.has(TRUNK_LID)) {
            pose.pushPose();
            hinge(pose, TRUNK_LID, car.openAmount(TRUNK_LID));
            s.box(-0.74, 0.83, -1.84, 0.74, 0.87, -1.15, Tex.PAINT, paint);
            s.box(-0.2, 0.79, -1.84, 0.2, 0.83, -1.82, Tex.CHROME, WHITE);
            pose.popPose();
        }
        for (PartSlot door : new PartSlot[]{DOOR_L, DOOR_R}) {
            if (!car.has(door)) continue;
            int side = door == DOOR_L ? 1 : -1;
            pose.pushPose();
            hinge(pose, door, car.openAmount(door));
            s.box(side * 0.77, 0.3, -0.78, side * 0.82, 0.87, 0.5, Tex.PAINT, paint);
            s.box(side * 0.75, 0.32, -0.76, side * 0.77, 0.86, 0.48, Tex.INTERIOR, WHITE);
            s.box(side * 0.77, 1.42, -0.76, side * 0.81, 1.46, 0.1, Tex.PAINT, paint);
            s.box(side * 0.77, 0.87, -0.78, side * 0.81, 1.46, -0.73, Tex.PAINT, paint);
            s.hexa(new Vec3[]{
                    new Vec3(side * 0.77, 0.87, 0.44), new Vec3(side * 0.81, 0.87, 0.44), new Vec3(side * 0.81, 0.87, 0.5), new Vec3(side * 0.77, 0.87, 0.5),
                    new Vec3(side * 0.77, 1.46, 0.06), new Vec3(side * 0.81, 1.46, 0.06), new Vec3(side * 0.81, 1.46, 0.12), new Vec3(side * 0.77, 1.46, 0.12)},
                    Tex.PAINT, paint);
            s.box(side * 0.82, 0.74, -0.66, side * 0.83, 0.77, -0.52, Tex.CHROME, WHITE);
            pose.popPose();
        }
        for (PartSlot fender : new PartSlot[]{FENDER_L, FENDER_R}) {
            if (!car.has(fender)) continue;
            int side = fender == FENDER_L ? 1 : -1;
            s.box(side * 0.76, 0.62, 0.6, side * 0.83, 0.87, 1.85, Tex.PAINT, paint);
            s.box(side * 0.76, 0.3, 1.48, side * 0.83, 0.62, 1.85, Tex.PAINT, paint);
            s.box(side * 0.76, 0.3, 0.6, side * 0.83, 0.62, 0.82, Tex.PAINT, paint);
        }
        if (car.has(BUMPER_F)) s.box(-0.84, 0.26, 1.85, 0.84, 0.42, 1.97, Tex.CHROME, WHITE);
        if (car.has(BUMPER_R)) s.box(-0.84, 0.26, -1.97, 0.84, 0.42, -1.85, Tex.CHROME, WHITE);
    }

    private static void hinge(PoseStack pose, PartSlot slot, float amount) {
        PartSlot.Hinge hinge = slot.hinge();
        if (hinge == null || amount <= 0) return;
        // Ease in and out so panels swing rather than snap.
        float eased = amount * amount * (3 - 2 * amount);
        Vec3 pivot = hinge.pivot();
        pose.translate(pivot.x, pivot.y, pivot.z);
        float angle = hinge.openDegrees() * eased;
        pose.mulPose(hinge.axis() == net.minecraft.core.Direction.Axis.X ? Axis.XP.rotationDegrees(angle) : Axis.YP.rotationDegrees(angle));
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
    }

    // ---------------------------------------------------------------- running gear

    private static void drawWheels(CarEntity car, Shapes s, CarSetup setup) {
        Tex tread = switch (setup.tyres()) {
            case SEMI_SLICK, DRIFT -> Tex.TREAD_SLICK;
            case OFFROAD -> Tex.TREAD_OFFROAD;
            default -> Tex.TREAD;
        };
        for (PartSlot slot : new PartSlot[]{WHEEL_FL, WHEEL_FR, WHEEL_RL, WHEEL_RR}) {
            CarEntity.InstalledPart part = car.getPart(slot);
            if (part == null) continue;
            boolean front = slot == WHEEL_FL || slot == WHEEL_FR;
            int side = slot == WHEEL_FL || slot == WHEEL_RL ? 1 : -1;
            Vec3 hub = new Vec3(side * 0.7, 0.3, front ? 1.15 : -1.15);
            double halfWidth = (front ? setup.widthFront() : setup.widthRear()) / 2000.0;
            float camber = front ? setup.camberFront() : setup.camberRear();
            float toe = front ? setup.toeFront() : setup.toeRear();
            PoseStack pose = s.pose();
            pose.pushPose();
            // Wheel offset pushes the wheel out; on a lift the wheels hang a little below the arches.
            pose.translate(hub.x + side * setup.offset() / 1000.0, hub.y - (car.liftHeight() > 0.01f ? 0.04 : 0), hub.z);
            if (front) pose.mulPose(Axis.YP.rotation(car.steerVisual));
            // Toe-in turns the front of each wheel towards the middle; negative camber leans its top inwards.
            pose.mulPose(Axis.YP.rotationDegrees(-side * toe));
            pose.mulPose(Axis.ZP.rotationDegrees(side * -camber));
            pose.mulPose(Axis.XP.rotation(front ? car.frontWheelAngle : car.rearWheelAngle));
            // Wobble when the nuts are loose.
            if (!part.isSecure()) pose.mulPose(Axis.ZP.rotation(Mth.sin(car.frontWheelAngle * 2) * 0.04f * part.looseness()));
            int tyre = part.condition < 15 ? 0xFFB0A8A0 : WHITE;
            s.cylinderX(0.3, halfWidth, 18, tread, Tex.TYRE_WALL, tyre);
            pose.pushPose();
            pose.translate(side * 0.005, 0, 0);
            s.cylinderX(0.17, halfWidth, 14, Tex.RIM, Tex.RIM, WHITE);
            pose.popPose();
            // Wheel nuts turn with the wheel.
            for (int i = 0; i < part.bolts.length; i++) {
                Vec3 nut = slot.fixedGeometry().bolts().get(i).subtract(hub);
                drawBolt(s, nut.x, nut.y, nut.z, 'x', side, part.bolts[i]);
            }
            pose.popPose();
        }
    }

    private static void drawBrakes(Shapes s, double z) {
        for (int side = -1; side <= 1; side += 2) {
            s.cylinder(side * 0.565, 0.3, z, 'x', 0.17, 0.012, 16, Tex.BRAKE_DISC, Tex.BRAKE_DISC, WHITE);
            s.box(side * 0.54, 0.4, z - 0.1, side * 0.59, 0.48, z + 0.1, Tex.RED_METAL, WHITE);
        }
    }

    private static void drawExhaust(CarEntity car, Shapes s, CarSetup setup) {
        if (!car.has(EXHAUST)) return;
        s.cylinder(-0.4, 0.16, -0.79, 'z', 0.035, 1.19, 8, Tex.EXHAUST, Tex.BLACK_PLASTIC, WHITE);
        s.cylinder(-0.4, 0.16, 0.45, 'y', 0.035, 0.15, 8, Tex.EXHAUST, Tex.EXHAUST, WHITE);
        switch (setup.exhaust()) {
            case STOCK -> s.cylinder(-0.38, 0.165, -1.375, 'z', 0.085, 0.23, 12, Tex.EXHAUST, Tex.EXHAUST, WHITE);
            case SPORT -> {
                s.cylinder(-0.38, 0.165, -1.375, 'z', 0.075, 0.18, 12, Tex.CHROME, Tex.CHROME, WHITE);
                s.cylinder(-0.4, 0.16, -1.97, 'z', 0.05, 0.04, 12, Tex.CHROME, Tex.BLACK_PLASTIC, WHITE);
            }
            // Straight through: no silencer, a big open tip.
            case STRAIGHT_PIPE -> s.cylinder(-0.4, 0.16, -1.98, 'z', 0.065, 0.05, 14, Tex.CHROME, Tex.BLACK_PLASTIC, WHITE);
        }
    }

    /** Propshaft to the rear axle for rear or all-wheel drive, a front diff and driveshafts for front or all-wheel. */
    private static void drawDriveshafts(CarEntity car, Shapes s, CarSetup setup) {
        if (!car.has(GEARBOX)) return;
        boolean rear = setup.drivetrain() != CarSetup.Drivetrain.FWD;
        boolean front = setup.drivetrain() != CarSetup.Drivetrain.RWD;
        if (rear) {
            s.cylinder(0, 0.3, -0.4, 'z', 0.03, 0.75, 8, Tex.FRAME, Tex.FRAME, WHITE);
            s.cylinder(0, 0.3, -1.15, 'x', 0.1, 0.08, 12, Tex.CAST_IRON, Tex.CAST_IRON, WHITE);
        }
        if (front) {
            s.cylinder(0, 0.3, 1.15, 'x', 0.08, 0.07, 12, Tex.CAST_IRON, Tex.CAST_IRON, WHITE);
            s.cylinder(0, 0.3, 1.15, 'x', 0.02, 0.62, 8, Tex.FRAME, Tex.FRAME, WHITE);
        }
    }

    /** The lift under a raised car: a floor plate, two posts and pads under the sills. */
    private static void drawLift(Shapes s, float height) {
        double floor = -height;
        s.box(-0.9, floor, -1.0, 0.9, floor + 0.12, 1.0, Tex.LIFT, WHITE);
        for (int side = -1; side <= 1; side += 2) {
            s.box(side * 0.68, floor + 0.12, -0.12, side * 0.56, 0.2, 0.12, Tex.LIFT, WHITE);
            s.box(side * 0.75, 0.16, -0.75, side * 0.5, 0.22, 0.55, Tex.FRAME, WHITE);
            for (double z : new double[]{-0.7, 0.5}) {
                s.box(side * 0.72, 0.2, z - 0.05, side * 0.62, 0.22, z + 0.05, Tex.RUBBER, WHITE);
            }
        }
    }

    // ---------------------------------------------------------------- engine bay

    /** Draws every installed powertrain part from its layout pieces, shaking a little while it runs. */
    private static void drawEngine(CarEntity car, Shapes s, float partialTick) {
        PoseStack pose = s.pose();
        pose.pushPose();
        if (car.isRunning()) {
            float time = car.tickCount + partialTick;
            CarEntity.InstalledPart plugs = car.getPart(SPARK_PLUGS);
            float rough = plugs != null && plugs.condition < 35 ? 3f : 1f;
            float amp = 0.0035f * rough;
            pose.translate(Mth.sin(time * 4.1f) * amp, Mth.cos(time * 5.3f) * amp * 0.6f, 0);
        }
        for (PartSlot slot : PartSlot.values()) {
            if (!slot.isPowertrain() || !car.has(slot)) continue;
            EngineLayout.Geometry geometry = car.geometry(slot);
            if (geometry == null) continue;
            if (slot.isEngine()) {
                for (EngineLayout.Piece piece : geometry.pieces()) drawPiece(s, piece);
            } else {
                AABB b = geometry.bounds();
                if (slot == STARTER) {
                    Vec3 c = b.getCenter();
                    s.cylinder(c.x, c.y, c.z, 'z', 0.06, b.getZsize() / 2, 10, Tex.BLACK_PLASTIC, Tex.COPPER, WHITE);
                } else {
                    s.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Tex.ALUMINIUM, WHITE);
                }
            }
        }
        pose.popPose();

        if (car.isStand()) return;
        if (car.has(RADIATOR)) {
            s.box(-0.32, 0.32, 1.66, 0.32, 0.74, 1.72, Tex.RADIATOR, WHITE);
            s.cylinder(0.2, 0.755, 1.69, 'y', 0.025, 0.015, 8, Tex.ALUMINIUM, Tex.ALUMINIUM, WHITE);
        }
        if (car.has(BATTERY)) {
            s.box(0.42, 0.3, 1.3, 0.62, 0.5, 1.6, Tex.BATTERY, WHITE);
            s.box(0.45, 0.5, 1.33, 0.48, 0.52, 1.36, Tex.RED_METAL, WHITE);
            s.box(0.56, 0.5, 1.54, 0.59, 0.52, 1.57, Tex.BLACK_PLASTIC, WHITE);
        }
        if (car.has(FUEL_TANK)) {
            s.box(-0.5, 0.28, -1.7, 0.5, 0.5, -1.3, Tex.FUEL_TANK, WHITE);
            s.cylinder(0.38, 0.53, -1.6, 'y', 0.04, 0.03, 8, Tex.CHROME, Tex.BLACK_PLASTIC, WHITE);
        }
    }

    private static void drawPiece(Shapes s, EngineLayout.Piece piece) {
        AABB b = piece.box();
        Tex tex = switch (piece.material()) {
            case IRON -> Tex.CAST_IRON;
            case ALUMINIUM -> Tex.ALUMINIUM;
            case RED -> Tex.RED_METAL;
            case BLACK -> Tex.BLACK_PLASTIC;
            case CERAMIC -> Tex.CERAMIC;
            case CHROME -> Tex.CHROME;
            case EXHAUST -> Tex.EXHAUST;
        };
        Vec3 c = b.getCenter();
        switch (piece.shape()) {
            case BOX -> s.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, tex, WHITE);
            case CYL_X -> s.cylinder(c.x, c.y, c.z, 'x', Math.min(b.getYsize(), b.getZsize()) / 2, b.getXsize() / 2, 14, tex, tex, WHITE);
            case CYL_Y -> s.cylinder(c.x, c.y, c.z, 'y', Math.min(b.getXsize(), b.getZsize()) / 2, b.getYsize() / 2, 12, tex, tex, WHITE);
            case CYL_Z -> s.cylinder(c.x, c.y, c.z, 'z', Math.min(b.getXsize(), b.getYsize()) / 2, b.getZsize() / 2, 14, tex, tex, WHITE);
        }
    }

    /** A red engine stand on casters, cradling the engine under its sump and gearbox. */
    private static void drawStand(Shapes s) {
        for (int side = -1; side <= 1; side += 2) {
            s.box(side * 0.42, 0.06, -0.9, side * 0.36, 0.12, 0.5, Tex.RED_METAL, WHITE);
            for (double z : new double[]{-0.85, 0.45}) {
                s.cylinder(side * 0.39, 0.035, z, 'x', 0.035, 0.02, 8, Tex.RUBBER, Tex.ALUMINIUM, WHITE);
            }
        }
        s.box(-0.42, 0.06, 0.38, 0.42, 0.12, 0.44, Tex.RED_METAL, WHITE);
        s.box(-0.42, 0.06, -0.86, 0.42, 0.12, -0.8, Tex.RED_METAL, WHITE);
        s.box(-0.04, 0.06, -0.86, 0.04, 0.12, 0.44, Tex.RED_METAL, WHITE);
        s.box(-0.04, 0.12, -0.04, 0.04, 0.67, 0.04, Tex.RED_METAL, WHITE);
        s.box(-0.12, 0.64, -0.12, 0.12, 0.67, 0.12, Tex.RED_METAL, WHITE);
        s.box(-0.04, 0.12, -0.6, 0.04, 0.75, -0.52, Tex.RED_METAL, WHITE);
        s.box(-0.1, 0.72, -0.62, 0.1, 0.75, -0.5, Tex.RED_METAL, WHITE);
    }

    // ---------------------------------------------------------------- interior

    private static void drawCabin(CarEntity car, Shapes s) {
        PoseStack pose = s.pose();
        for (PartSlot seat : new PartSlot[]{SEAT_L, SEAT_R}) {
            if (!car.has(seat)) continue;
            int side = seat == SEAT_L ? 1 : -1;
            double x1 = side * 0.1, x2 = side * 0.58;
            s.box(x1, 0.28, -0.48, x2, 0.32, -0.02, Tex.FRAME, WHITE);
            s.box(x1, 0.32, -0.5, x2, 0.45, 0.0, Tex.FABRIC, WHITE);
            s.hexa(new Vec3[]{
                    new Vec3(Math.min(x1, x2), 0.45, -0.6), new Vec3(Math.max(x1, x2), 0.45, -0.6), new Vec3(Math.max(x1, x2), 0.45, -0.48), new Vec3(Math.min(x1, x2), 0.45, -0.48),
                    new Vec3(Math.min(x1, x2), 1.05, -0.72), new Vec3(Math.max(x1, x2), 1.05, -0.72), new Vec3(Math.max(x1, x2), 1.05, -0.6), new Vec3(Math.min(x1, x2), 1.05, -0.6)},
                    Tex.FABRIC, WHITE);
            s.box(side * 0.22, 1.05, -0.74, side * 0.46, 1.2, -0.64, Tex.FABRIC, WHITE);
        }
        if (car.has(STEERING_WHEEL)) {
            pose.pushPose();
            pose.translate(0.34, 0.95, 0.26);
            pose.mulPose(Axis.XP.rotationDegrees(-25));
            s.cylinder(0, 0, 0.12, 'z', 0.02, 0.12, 6, Tex.BLACK_PLASTIC, Tex.BLACK_PLASTIC, WHITE);
            pose.mulPose(Axis.ZP.rotation(-car.steerVisual * 4.5f));
            for (int i = 0; i < 16; i++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotation((float) (Math.PI * 2 * i / 16)));
                s.box(-0.033, 0.15, -0.012, 0.033, 0.175, 0.012, Tex.RUBBER, WHITE);
                pose.popPose();
            }
            s.box(-0.15, -0.012, -0.008, 0.15, 0.012, 0.008, Tex.CHROME, WHITE);
            s.box(-0.012, -0.15, -0.008, 0.012, 0, 0.008, Tex.CHROME, WHITE);
            s.cylinder(0, 0, 0, 'z', 0.035, 0.015, 8, Tex.BLACK_PLASTIC, Tex.CHROME, WHITE);
            pose.popPose();
        }
        if (car.has(GEARBOX)) drawGearLever(car, s);
    }

    /** H-pattern lever: 1 and 2 to the left, 3 and 4 in the middle, reverse right and back. */
    private static void drawGearLever(CarEntity car, Shapes s) {
        int gear = car.getGear();
        float across = switch (gear) {
            case 1, 2 -> 1;
            case CarPhysics.REVERSE -> -1;
            default -> 0;
        };
        float along = switch (gear) {
            case 1, 3 -> 1;
            case 2, 4, CarPhysics.REVERSE -> -1;
            default -> 0;
        };
        PoseStack pose = s.pose();
        pose.pushPose();
        pose.translate(0, 0.4, 0.12);
        pose.mulPose(Axis.XP.rotationDegrees(along * 14));
        pose.mulPose(Axis.ZP.rotationDegrees(-across * 12));
        s.box(-0.012, 0, -0.012, 0.012, 0.3, 0.012, Tex.CHROME, WHITE);
        s.box(-0.03, 0.3, -0.03, 0.03, 0.36, 0.03, Tex.BLACK_PLASTIC, WHITE);
        pose.popPose();
        s.box(-0.06, 0.4, 0.06, 0.06, 0.42, 0.18, Tex.RUBBER, WHITE);
    }

    // ---------------------------------------------------------------- bolts and aiming

    private static void drawBolts(CarEntity car, Shapes s) {
        for (PartSlot slot : PartSlot.values()) {
            CarEntity.InstalledPart part = car.getPart(slot);
            if (part == null || slot.name().startsWith("WHEEL_")) continue;
            EngineLayout.Geometry geometry = car.geometry(slot);
            if (geometry == null) continue;
            float open = car.openAmount(slot);
            Vec3 normal = Vec3.atLowerCornerOf(geometry.face().getNormal());
            for (int i = 0; i < part.bolts.length && i < geometry.boltCount(); i++) {
                Vec3 point = geometry.bolts().get(i);
                Vec3 base = slot.swing(point, open);
                Vec3 out = slot.swing(point.add(normal), open).subtract(base);
                char axis = Math.abs(out.x) > 0.5 ? 'x' : Math.abs(out.y) > 0.5 ? 'y' : 'z';
                double sign = axis == 'x' ? Math.signum(out.x) : axis == 'y' ? Math.signum(out.y) : Math.signum(out.z);
                drawBolt(s, base.x, base.y, base.z, axis, sign, part.bolts[i]);
            }
        }
    }

    /** A hex-ish bolt head standing out from the surface; loose ones stick out further. */
    private static void drawBolt(Shapes s, double x, double y, double z, char axis, double sign, int turns) {
        double height = 0.012 + (1 - turns / (double) CarEntity.BOLT_STEPS) * 0.05;
        double r = 0.022;
        int color = turns == CarEntity.BOLT_STEPS ? WHITE : 0xFFE8D088;
        double x1 = x - r, x2 = x + r, y1 = y - r, y2 = y + r, z1 = z - r, z2 = z + r;
        switch (axis) {
            case 'x' -> { x1 = sign > 0 ? x : x - height; x2 = sign > 0 ? x + height : x; }
            case 'y' -> { y1 = sign > 0 ? y : y - height; y2 = sign > 0 ? y + height : y; }
            default -> { z1 = sign > 0 ? z : z - height; z2 = sign > 0 ? z + height : z; }
        }
        s.box(x1, y1, z1, x2, y2, z2, Tex.BOLT, color);
    }

    /** Ghosts where the held part can go, and an outline on the part or bolt under the crosshair. */
    private static void drawAimHelpers(CarEntity car, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.getVehicle() == car) return;

        Item held = player.getMainHandItem().getItem();
        boolean holdingPart = held instanceof PartItem;
        if (holdingPart) {
            Shapes ghost = new Shapes(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT);
            for (PartSlot slot : PartSlot.forItem(held)) {
                if (car.has(slot) || (slot.parent() != null && !car.has(slot.parent()))) continue;
                if (car.isStand() && !slot.isPowertrain()) continue;
                EngineType type = slot == ENGINE_BLOCK ? EngineType.byBlock(held) : car.engineType();
                EngineLayout.Geometry geometry = car.geometry(slot, type);
                if (geometry == null) continue;
                AABB b = geometry.bounds();
                ghost.box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, Tex.WHITE, GHOST);
            }
        }
        if (targetedCar(mc) != car) return;
        if (holdingPart) {
            car.pickInstallTarget(player, held).ifPresent(slot -> outline(pose, buffers,
                    car.geometry(slot, slot == ENGINE_BLOCK ? EngineType.byBlock(held) : car.engineType()).bounds(), 1f));
            return;
        }
        Optional<CarEntity.Aim> aim = car.aim(player);
        aim.ifPresent(target -> {
            boolean open = car.isOpen(target.slot());
            outline(pose, buffers, car.partBounds(target.slot(), open), 0.6f);
            if (target.bolt() >= 0 && player.getMainHandItem().getItem() instanceof WrenchItem) {
                outline(pose, buffers, car.boltBounds(target.slot(), target.bolt(), open), 1f);
            }
        });
    }

    /** The car under the crosshair, whether the crosshair is on the car itself or one of its hitbox parts. */
    public static CarEntity targetedCar(Minecraft mc) {
        if (mc.crosshairPickEntity instanceof CarEntity car) return car;
        if (mc.crosshairPickEntity instanceof com.summercraft.car.CarPart part) return part.getParent();
        return null;
    }

    private static void outline(PoseStack pose, MultiBufferSource buffers, AABB box, float alpha) {
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box.inflate(0.01), 1f, 1f, 1f, alpha);
    }

    @Override
    public ResourceLocation getTextureLocation(CarEntity car) {
        return TEXTURE;
    }
}
