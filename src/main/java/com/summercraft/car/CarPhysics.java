package com.summercraft.car;


/**
 * Vehicle dynamics for a small rear-wheel-drive car, in SI units (metres, seconds, newtons).
 * It runs on whichever side controls the car: the driver's client, or the server when nobody is driving.
 *
 * <p>Body frame: x is lateral (+ = left), z is longitudinal (+ = forward). A positive yaw rate turns left.
 * The engine is linked to the rear axle through a clutch that can lock or slip, and each axle's tyres
 * have a grip budget shared between cornering and accelerating or braking. That budget is what makes
 * wheelspin, power oversteer, locked brakes and handbrake turns happen.
 */
public final class CarPhysics {
    public static final double TICK = 0.05;
    private static final int SUBSTEPS = 20;

    private static final double MASS = 900, GRAVITY = 9.81;
    private static final double FRONT = 1.15, REAR = 1.15, WHEELBASE = FRONT + REAR;
    private static final double YAW_INERTIA = 1300;
    private static final double CORNERING_FRONT = 42000, CORNERING_REAR = 60000;
    public static final double WHEEL_RADIUS = 0.3;
    private static final double AXLE_MASS = 30;
    private static final double SLIDING_GRIP = 0.85;
    private static final double YAW_DAMPING = 1500;

    private static final double ENGINE_INERTIA = 0.16;
    public static final double IDLE_RPM = 850;
    private static final double STALL_RPM = 330, CRANK_RPM = 260;
    private static final double MAX_TURBO_BOOST = 0.8, MAX_SUPERCHARGER_BOOST = 0.4;
    private static final double FINAL_DRIVE = 4.1;
    /** Indexed by gear + 1: reverse, neutral, 1st to 4th. */
    private static final double[] RATIOS = {-3.5, 0, 3.6, 2.1, 1.4, 1.0};
    public static final int REVERSE = -1, NEUTRAL = 0, TOP_GEAR = 4;

    private static final double BRAKE_FORCE = 9500, HANDBRAKE_FORCE = 7000;
    private static final double RPM_PER_RAD = 60 / (2 * Math.PI);

    public record Input(double throttle, double brake, double steer, boolean handbrake, boolean clutchPedal) {
        public static final Input NONE = new Input(0, 0, 0, false, false);
    }

    /**
     * What the car's parts allow this tick.
     * {@code power} scales engine torque (tuning and wear), {@code clutchGrip} scales clutch capacity,
     * {@code mu*} are tyre grip coefficients for the surface, {@code brake*} scale brake strength.
     */
    public record Env(boolean running, boolean cranking, double power, boolean drivetrain, double clutchGrip,
                      double muFront, double muRear, double rolling, double brakeFront, double brakeRear,
                      boolean steering, int missingWheels,
                      double peakTorque, double peakRpm, double redline,
                      boolean turbo, boolean supercharger, boolean onDyno,
                      Chassis chassis) {}

    /**
     * Setup from the lift: sideways-grip and cornering-stiffness multipliers per axle (camber, toe, springs),
     * share of drive to the front wheels, and centre-of-gravity height (ride height).
     */
    public record Chassis(double lateralFront, double lateralRear, double stiffnessFront, double stiffnessRear,
                          double frontDrive, double cgHeight) {
        public static final Chassis STOCK = new Chassis(1, 1, 1, 1, 0, 0.45);
    }

    // State
    public double vx, vz, yawRate, steer;
    public double rearWheelSpeed;
    public double engineSpeed;
    public double clutch = 1;
    public int gear;
    private double throttle;
    private double longAccel, lastRearForce, lastFrontDrive;
    private boolean wasRunning;
    private int lowRpmTicks;
    private double turboBoost;

    /** How steep the ground is along the car, radians, nose up positive. Gravity pulls the car back down it. */
    public double slope;

    /** Boost pressure in bar, from turbo and supercharger together. */
    public double boost;

    /** Best engine torque (Nm) and wheel power (W) seen at each 100 rpm, while on the dyno at full throttle. */
    public final double[] dynoTorque = new double[90], dynoPower = new double[90];

    // Per-tick outputs
    public double latAccelSmoothed, longAccelSmoothed;
    public double frontWheelAngle, rearWheelAngle;
    public boolean stalled;
    /** Energy spent sliding tyres, slipping the clutch and on the brakes, for the server to turn into wear. */
    public double tyreWork, clutchWork, brakeWork;

    public static double ratio(int gear) {
        return RATIOS[gear + 1];
    }

    /** How far the rear tyres are sliding sideways, in radians. */
    public double rearSlipAngle() {
        return Math.abs(vz) < 1 ? 0 : Math.atan2(vx - yawRate * REAR, Math.abs(vz));
    }

    public void resetDyno() {
        java.util.Arrays.fill(dynoTorque, 0);
        java.util.Arrays.fill(dynoPower, 0);
    }

    public double rpm() {
        return engineSpeed * RPM_PER_RAD;
    }

    public double speed() {
        return Math.hypot(vx, vz);
    }

    public boolean shift(int newGear) {
        if (newGear < REVERSE || newGear > TOP_GEAR) return false;
        if (clutch > 0.3) return false;
        // Reverse has no synchro: going into it while rolling forward just grinds.
        if (newGear == REVERSE && vz > 1.0) return false;
        gear = newGear;
        return true;
    }

    public void step(Input in, Env env, boolean onGround) {
        stalled = false;
        if (env.running() && !wasRunning) engineSpeed = Math.max(engineSpeed, IDLE_RPM / RPM_PER_RAD);
        wasRunning = env.running();

        clutch = in.clutchPedal() || !env.drivetrain() ? Math.max(0, clutch - 0.34) : Math.min(1, clutch + 0.06);
        throttle += clamp(in.throttle() - throttle, -0.25, 0.15);

        // Steering lock shrinks with speed so keyboard steering isn't twitchy, but once the car is sliding the
        // front wheels are helped toward the direction of travel (countersteer), and the player can steer
        // into the slide past the normal limit. Without this a keyboard can't catch a slide.
        double maxSteer = env.onDyno() ? 0 : 0.8 / (1 + Math.pow(speed() / 9, 1.5));
        double targetSteer = steer;
        if (env.steering()) {
            // Body slip: the angle between where the car points and where it's going. Near zero in a clean
            // corner, large once the tail is out.
            double travel = Math.abs(vz) > 1 ? Math.atan2(vx, Math.abs(vz)) * Math.signum(vz) : 0;
            double sliding = clamp((Math.abs(travel) - 0.08) / 0.15, 0, 1);
            targetSteer = in.steer() * maxSteer + travel * sliding * 0.8;
            boolean counterSteering = in.steer() * yawRate < 0 && Math.signum(in.steer()) == Math.signum(travel);
            if (counterSteering) {
                targetSteer = Math.max(Math.abs(targetSteer), Math.abs(in.steer()) * 0.6 * sliding) * Math.signum(targetSteer);
            }
            targetSteer = clamp(targetSteer, -0.75, 0.75);
        }
        steer += clamp(targetSteer - steer, -0.15, 0.15);

        updateBoost(env);

        double k = env.drivetrain() ? ratio(gear) * FINAL_DRIVE / WHEEL_RADIUS : 0;
        double muFront = onGround ? env.muFront() : 0;
        // On the dyno the car is strapped down, so the rear tyres can't spin on the rollers.
        double muRear = env.onDyno() ? 3 : onGround ? env.muRear() : 0;
        double dt = TICK / SUBSTEPS;
        double vxBefore = vx, vzBefore = vz;
        for (int i = 0; i < SUBSTEPS; i++) substep(in, env, k, muFront, muRear, dt);

        if (env.onDyno()) {
            // Strapped down on the rollers: the body can't move or turn, the rollers stand in for it.
            vx = 0;
            yawRate = 0;
            recordDyno();
        }
        if (speed() < 0.4 && throttle < 0.05) {
            vx *= 0.7;
            yawRate *= 0.7;
        }
        double latAccel = (vx - vxBefore) / TICK + yawRate * vz;
        longAccelSmoothed += ((vz - vzBefore) / TICK - longAccelSmoothed) * 0.3;
        latAccelSmoothed += (latAccel - latAccelSmoothed) * 0.3;
        if (!env.onDyno()) frontWheelAngle += vz * TICK / WHEEL_RADIUS;
        rearWheelAngle += rearWheelSpeed * TICK / WHEEL_RADIUS;

        // A few ticks of grace, so a momentary dip while the clutch bites doesn't kill it.
        lowRpmTicks = env.running() && rpm() < STALL_RPM ? lowRpmTicks + 1 : 0;
        stalled = lowRpmTicks > 3;
    }

    private void substep(Input in, Env env, double k, double muFront, double muRear, double dt) {
        double rpm = engineSpeed * RPM_PER_RAD;
        double engineTorque = engineTorque(env, rpm);

        Chassis chassis = env.chassis();
        double loadShift = MASS * longAccel * chassis.cgHeight() / WHEELBASE;
        double gripFront = muFront * Math.max(MASS * GRAVITY * 0.15, MASS * GRAVITY * REAR / WHEELBASE - loadShift);
        double gripRear = muRear * 1.08 * Math.max(MASS * GRAVITY * 0.15, MASS * GRAVITY * FRONT / WHEELBASE + loadShift);

        // Front tyres: brakes, then whatever grip is left goes to steering.
        double frontBrake = in.brake() * BRAKE_FORCE * 0.6 * env.brakeFront();
        boolean frontLocked = frontBrake > gripFront && Math.abs(vz) > 0.5;
        double fxFront = -Math.min(frontBrake, gripFront * (frontLocked ? SLIDING_GRIP : 1)) * soft(vz, 0.3);
        // Driven front wheels spend some of their grip on traction, which is why front-drive cars push wide under power.
        double frontUsed = fxFront + lastFrontDrive;
        double capFront = Math.max(frontLastResort(gripFront, chassis), Math.sqrt(Math.max(0, gripFront * gripFront - frontUsed * frontUsed)))
                * (frontLocked ? 0.3 : 1) * chassis.lateralFront();
        // Spinning rear tyres still hold some sideways grip, so wheelspin pushes the tail out rather than letting go.
        double capRear = Math.max(gripRear * 0.6, Math.sqrt(Math.max(0, gripRear * gripRear - lastRearForce * lastRearForce)))
                * chassis.lateralRear();
        // With the drive at the front, the handbrake just locks the rear wheels on their own.
        boolean separateHandbrake = chassis.frontDrive() > 0 && in.handbrake();
        if (separateHandbrake && Math.abs(vz) > 0.5) capRear *= 0.3;

        double forwardRef = Math.max(Math.abs(vz), 1.5);
        double direction = vz >= 0 ? 1 : -1;
        double slipFront = Math.atan2(vx + yawRate * FRONT, forwardRef) - steer * direction;
        double slipRear = Math.atan2(vx - yawRate * REAR, forwardRef);
        double fyFront = tyreLateral(-CORNERING_FRONT * chassis.stiffnessFront() * slipFront, capFront);
        double fyRear = tyreLateral(-CORNERING_REAR * chassis.stiffnessRear() * slipRear, capRear);

        double cos = Math.cos(steer), sin = Math.sin(steer);
        double drag = (env.onDyno() ? 0 : 0.9 * vz * Math.abs(vz)) + (env.rolling() * MASS * GRAVITY + env.missingWheels() * 2500) * soft(vz, 0.4);
        double external = fxFront * cos - fyFront * sin - drag + MASS * yawRate * vx - MASS * GRAVITY * Math.sin(slope);
        double rearBrake = in.brake() * BRAKE_FORCE * 0.4 * env.brakeRear() + (in.handbrake() && !separateHandbrake ? HANDBRAKE_FORCE : 0);
        if (separateHandbrake) external -= Math.min(HANDBRAKE_FORCE, gripRear * SLIDING_GRIP) * soft(vz, 0.3);

        // The drive is modelled as one driven "axle". With drive split across both axles it can push until
        // whichever axle runs out of grip first for its share of the drive.
        double share = chassis.frontDrive();
        double driveGrip = share <= 0 ? gripRear : share >= 1 ? gripFront : Math.min(gripRear / (1 - share), gripFront / share);
        double vzBefore = vz;
        double driveForce = driveRearAxle(engineTorque, k, driveGrip, rearBrake, external, env, dt);
        lastRearForce = driveForce * (1 - share);
        lastFrontDrive = driveForce * share;
        longAccel = (vz - vzBefore) / dt;

        vx += ((fyFront * cos + fxFront * sin + fyRear) / MASS - yawRate * vz) * dt;
        // Mild damping on rotation beyond what the steering asks for, so slides settle instead of snapping round.
        // The target is the turn the steering asks for, capped at what the tyres can actually hold.
        double reachableYaw = Math.max(muRear, 0.1) * GRAVITY / Math.max(Math.abs(vz), 3);
        double targetYaw = clamp(Math.abs(vz) > 1 ? vz * Math.tan(steer) / WHEELBASE : 0, -reachableYaw, reachableYaw);
        double excessYaw = yawRate - targetYaw;
        boolean overRotating = Math.abs(yawRate) > Math.abs(targetYaw) || Math.signum(yawRate) != Math.signum(targetYaw);
        double damping = overRotating ? -YAW_DAMPING * excessYaw * clamp(Math.abs(excessYaw) / 0.4, 0, 1) : 0;
        yawRate += (FRONT * (fyFront * cos + fxFront * sin) - REAR * fyRear + damping) / YAW_INERTIA * dt;

        tyreWork += (Math.abs(fyFront * Math.sin(slipFront)) + Math.abs(fyRear * Math.sin(slipRear))) * Math.abs(vz) * dt;
        brakeWork += (frontBrake * Math.abs(vz) + rearBrake * Math.abs(rearWheelSpeed)) * dt;
    }

    /**
     * Couples engine, clutch, rear axle and body for one substep, and returns the rear tyres' force.
     * The cases follow which joints are locked: clutch locked or slipping, tyres gripping or spinning.
     */
    private double driveRearAxle(double engineTorque, double k, double grip, double rearBrake, double external, Env env, double dt) {
        double capacity = k != 0 ? clutchCapacity(env) * clutch * env.clutchGrip() : 0;

        // Tyres gripping: the rear axle turns with the body.
        if (Math.abs(rearWheelSpeed - vz) < 0.3) {
            if (capacity > 0 && Math.abs(engineSpeed - k * vz) < 4) {
                double lumped = MASS + AXLE_MASS + ENGINE_INERTIA * k * k;
                double accel = (engineTorque * k + external - rearBrake * soft(vz, 0.2)) / lumped;
                double tyreForce = MASS * accel - external;
                double clutchTorque = engineTorque - ENGINE_INERTIA * k * accel;
                if (Math.abs(tyreForce) <= grip && Math.abs(clutchTorque) <= capacity) {
                    vz += accel * dt;
                    rearWheelSpeed = vz;
                    engineSpeed = Math.max(0, k * vz);
                    return tyreForce;
                }
            }
            double clutchTorque = clutchTorque(engineTorque, k * vz, capacity);
            double accel = (clutchTorque * k + external - rearBrake * soft(vz, 0.2)) / (MASS + AXLE_MASS);
            double tyreForce = MASS * accel - external;
            if (Math.abs(tyreForce) <= grip) {
                vz += accel * dt;
                rearWheelSpeed = vz;
                spinEngine(engineTorque - clutchTorque, k * vz, capacity, env, dt);
                return tyreForce;
            }
        }

        // Tyres spinning or locked: the axle and the body move separately.
        double slip = rearWheelSpeed - vz;
        double tyreForce = grip * SLIDING_GRIP * Math.signum(slip == 0 ? engineTorque * k : slip);
        vz += (tyreForce + external) / MASS * dt;
        tyreWork += Math.abs(tyreForce * slip) * dt;

        if (capacity > 0 && Math.abs(engineSpeed - k * rearWheelSpeed) < 4) {
            double lumped = AXLE_MASS + ENGINE_INERTIA * k * k;
            double accel = (engineTorque * k - tyreForce) / lumped;
            if (Math.abs(engineTorque - ENGINE_INERTIA * k * accel) <= capacity) {
                rearWheelSpeed = applyBrake(rearWheelSpeed + accel * dt, rearBrake / lumped * dt);
                engineSpeed = Math.max(0, k * rearWheelSpeed);
                regainGrip(slip);
                return tyreForce;
            }
        }
        double clutchTorque = clutchTorque(engineTorque, k * rearWheelSpeed, capacity);
        rearWheelSpeed = applyBrake(rearWheelSpeed + (clutchTorque * k - tyreForce) / AXLE_MASS * dt, rearBrake / AXLE_MASS * dt);
        spinEngine(engineTorque - clutchTorque, k * rearWheelSpeed, capacity, env, dt);
        regainGrip(slip);
        return tyreForce;
    }

    private double clutchTorque(double engineTorque, double drivenSpeed, double capacity) {
        if (capacity <= 0) return 0;
        double slip = engineSpeed - drivenSpeed;
        double direction = Math.abs(slip) > 0.5 ? Math.signum(slip) : Math.signum(engineTorque);
        clutchWork += Math.abs(capacity * slip) * TICK / SUBSTEPS;
        return capacity * direction;
    }

    private void spinEngine(double netTorque, double drivenSpeed, double capacity, Env env, double dt) {
        double before = engineSpeed - drivenSpeed;
        engineSpeed += netTorque / ENGINE_INERTIA * dt;
        // The clutch plates catch up to each other: lock rather than overshoot.
        if (capacity > 0 && Math.signum(engineSpeed - drivenSpeed) != Math.signum(before)) engineSpeed = drivenSpeed;
        if (engineSpeed < 0) engineSpeed = 0;
    }

    private void regainGrip(double slipBefore) {
        if (slipBefore != 0 && Math.signum(rearWheelSpeed - vz) != Math.signum(slipBefore)) rearWheelSpeed = vz;
    }

    private static double applyBrake(double wheelSpeed, double brakeStep) {
        return Math.abs(wheelSpeed) <= brakeStep ? 0 : wheelSpeed - Math.signum(wheelSpeed) * brakeStep;
    }

    private double engineTorque(Env env, double rpm) {
        double friction = rpm < 60 ? engineSpeed * 1.5 : 9 + rpm * 0.0045 + (env.running() ? 0 : 12)
                + (env.supercharger() ? 2 + rpm * 0.001 : 0);
        double drive = 0;
        if (env.running()) {
            double idleAssist = clamp((IDLE_RPM - rpm) / 300, 0, 0.5);
            double pedal = rpm > env.redline() + 300 ? 0 : Math.max(throttle, idleAssist);
            double x = (rpm - env.peakRpm()) / env.peakRpm();
            drive = pedal * env.peakTorque() * clamp(1 - 0.55 * x * x, 0.3, 1) * (1 + boost) * env.power();
        } else if (env.cranking()) {
            drive = rpm < CRANK_RPM ? 55 + env.peakTorque() * 0.1 : 15;
        }
        return drive - friction;
    }

    /** A clutch sized for the engine, with a margin so a healthy one only slips when it's worn. */
    private static double clutchCapacity(Env env) {
        double maxBoost = (env.turbo() ? MAX_TURBO_BOOST : 0) + (env.supercharger() ? MAX_SUPERCHARGER_BOOST : 0);
        return Math.max(260, 2.0 * env.peakTorque() * (1 + maxBoost));
    }

    /**
     * Turbo boost builds with revs and throttle but lags behind (spool), then dumps the moment you lift.
     * Supercharger boost is instant and follows the revs.
     */
    private void updateBoost(Env env) {
        double rpm = rpm();
        double turboTarget = env.turbo() && env.running() ? throttle * clamp((rpm - 2000) / 2200, 0, 1) * MAX_TURBO_BOOST : 0;
        turboBoost += (turboTarget - turboBoost) * (turboTarget > turboBoost ? 0.035 : 0.3);
        double supercharger = env.supercharger() && env.running()
                ? MAX_SUPERCHARGER_BOOST * clamp(rpm / env.redline(), 0, 1) * (0.3 + 0.7 * throttle) : 0;
        boost = turboBoost + supercharger;
    }

    private void recordDyno() {
        double r = ratio(gear);
        if (throttle < 0.9 || clutch < 0.95 || r <= 0 || Math.abs(rearWheelSpeed - vz) > 0.5) return;
        int bin = (int) (rpm() / 100);
        if (bin < 0 || bin >= dynoTorque.length) return;
        double torque = lastRearForce * WHEEL_RADIUS / (r * FINAL_DRIVE);
        double power = lastRearForce * rearWheelSpeed;
        dynoTorque[bin] = Math.max(dynoTorque[bin], torque);
        dynoPower[bin] = Math.max(dynoPower[bin], power);
    }

    /** Linear tyre up to its grip limit, then a little less once it's sliding. */
    private static double tyreLateral(double force, double cap) {
        if (cap <= 0) return 0;
        // Grip rounds off near the limit and then fades slowly past it, so a slide builds progressively
        // instead of snapping from full grip to none.
        double x = force / cap;
        double fade = 1 - 0.12 * clamp((Math.abs(x) - 1.5) / 3, 0, 1);
        return cap * Math.tanh(x) * fade;
    }

    /** sign(v), but ramped near zero so forces don't flip back and forth at standstill. */
    private static double soft(double v, double width) {
        return clamp(v / width, -1, 1);
    }

    /** Even a front tyre that's spinning keeps some steering. */
    private static double frontLastResort(double gripFront, Chassis chassis) {
        return chassis.frontDrive() > 0 ? gripFront * 0.45 : 0;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }
}
