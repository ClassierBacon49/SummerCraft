package com.summercraft.car;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * How a car is set up on the lift: tyres, wheel alignment, suspension, exhaust and drivetrain.
 * Angles are in degrees (negative camber = tops of the wheels lean in, positive toe = toe-in),
 * widths and offsets in millimetres, ride height in centimetres, springs and dampers as 0.5 (soft) to 2.0 (stiff).
 */
public record CarSetup(TyreType tyres, int widthFront, int widthRear, int offset,
                       float camberFront, float camberRear, float toeFront, float toeRear,
                       int rideHeight, float springFront, float springRear, float dampers,
                       ExhaustType exhaust, Drivetrain drivetrain) {

    public static final CarSetup STOCK = new CarSetup(TyreType.STREET, 185, 185, 0, -0.5f, -0.5f, 0.1f, 0.2f,
            0, 1f, 1f, 1f, ExhaustType.STOCK, Drivetrain.RWD);

    public static final int MIN_WIDTH = 155, MAX_WIDTH = 265;
    public static final int MIN_OFFSET = -20, MAX_OFFSET = 40;
    public static final float MIN_CAMBER = -5, MAX_CAMBER = 2;
    public static final float MIN_TOE = -1, MAX_TOE = 1;
    public static final int MIN_RIDE = -8, MAX_RIDE = 6;
    public static final float MIN_SPRING = 0.5f, MAX_SPRING = 2f;

    /** Grip of each tyre compound on tarmac, on loose ground (dirt, sand, gravel, snow) and on ice, and wear rate. */
    public enum TyreType {
        STREET(1.0, 1.0, 1.0, 1.0),
        SPORT(1.12, 0.9, 0.9, 1.3),
        SEMI_SLICK(1.28, 0.7, 0.6, 2.2),
        DRIFT(0.82, 0.85, 1.0, 1.6),
        OFFROAD(0.9, 1.35, 1.2, 1.0);

        public final double tarmac, loose, ice, wear;

        TyreType(double tarmac, double loose, double ice, double wear) {
            this.tarmac = tarmac;
            this.loose = loose;
            this.ice = ice;
            this.wear = wear;
        }

        public Component displayName() {
            return Component.translatable("setup.summercraft.tyre." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Power gain and loudness. */
    public enum ExhaustType {
        STOCK(1.0, 1.0f), SPORT(1.04, 1.35f), STRAIGHT_PIPE(1.08, 1.9f);

        public final double power;
        public final float volume;

        ExhaustType(double power, float volume) {
            this.power = power;
            this.volume = volume;
        }

        public Component displayName() {
            return Component.translatable("setup.summercraft.exhaust." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Share of drive going to the front wheels. */
    public enum Drivetrain {
        RWD(0), FWD(1), AWD(0.4);

        public final double frontShare;

        Drivetrain(double frontShare) {
            this.frontShare = frontShare;
        }

        public Component displayName() {
            return Component.translatable("setup.summercraft.drivetrain." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Clamps every value into its legal range, so a client can't send anything silly. */
    public CarSetup validated() {
        return new CarSetup(tyres,
                Mth.clamp(widthFront, MIN_WIDTH, MAX_WIDTH), Mth.clamp(widthRear, MIN_WIDTH, MAX_WIDTH), Mth.clamp(offset, MIN_OFFSET, MAX_OFFSET),
                Mth.clamp(camberFront, MIN_CAMBER, MAX_CAMBER), Mth.clamp(camberRear, MIN_CAMBER, MAX_CAMBER),
                Mth.clamp(toeFront, MIN_TOE, MAX_TOE), Mth.clamp(toeRear, MIN_TOE, MAX_TOE),
                Mth.clamp(rideHeight, MIN_RIDE, MAX_RIDE),
                Mth.clamp(springFront, MIN_SPRING, MAX_SPRING), Mth.clamp(springRear, MIN_SPRING, MAX_SPRING), Mth.clamp(dampers, MIN_SPRING, MAX_SPRING),
                exhaust, drivetrain);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Tyres", tyres.name());
        tag.putInt("WidthFront", widthFront);
        tag.putInt("WidthRear", widthRear);
        tag.putInt("Offset", offset);
        tag.putFloat("CamberFront", camberFront);
        tag.putFloat("CamberRear", camberRear);
        tag.putFloat("ToeFront", toeFront);
        tag.putFloat("ToeRear", toeRear);
        tag.putInt("RideHeight", rideHeight);
        tag.putFloat("SpringFront", springFront);
        tag.putFloat("SpringRear", springRear);
        tag.putFloat("Dampers", dampers);
        tag.putString("Exhaust", exhaust.name());
        tag.putString("Drivetrain", drivetrain.name());
        return tag;
    }

    public static CarSetup load(CompoundTag tag) {
        if (!tag.contains("Tyres")) return STOCK;
        return new CarSetup(parse(TyreType.class, tag.getString("Tyres"), TyreType.STREET),
                tag.getInt("WidthFront"), tag.getInt("WidthRear"), tag.getInt("Offset"),
                tag.getFloat("CamberFront"), tag.getFloat("CamberRear"), tag.getFloat("ToeFront"), tag.getFloat("ToeRear"),
                tag.getInt("RideHeight"), tag.getFloat("SpringFront"), tag.getFloat("SpringRear"), tag.getFloat("Dampers"),
                parse(ExhaustType.class, tag.getString("Exhaust"), ExhaustType.STOCK),
                parse(Drivetrain.class, tag.getString("Drivetrain"), Drivetrain.RWD)).validated();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- what it does to the handling

    /** Sideways grip multiplier from camber: a little negative camber helps, too much (or any positive) hurts. */
    public static double camberLateral(float camber) {
        double negative = -camber;
        if (negative < 0) return 1 + 0.05 * negative;
        return 1 + 0.035 * Math.min(negative, 3) - 0.05 * Math.max(0, negative - 3);
    }

    /** Braking and traction lose a little contact patch with any camber. */
    public static double camberLongitudinal(float camber) {
        return 1 - 0.015 * Math.abs(camber);
    }

    public static double widthGrip(int width) {
        return 1 + (width - 185) / 185.0 * 0.3;
    }

    /** Average spring stiffness, used for body roll and response. */
    public double springAverage() {
        return (springFront + springRear) / 2;
    }
}
