package com.summercraft.client;

import com.summercraft.ModRegistry;
import com.summercraft.car.CarEntity;
import com.summercraft.car.EngineType;
import com.summercraft.car.PartSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Looping sounds that follow a car. Each engine type has two recordings, one low and one high, crossfaded and
 * pitched to the engine speed so it runs smoothly from idle to the limiter. Turbo whistle and supercharger
 * whine follow boost, and the blow-off valve goes when boost collapses.
 */
public final class CarSounds {
    private enum Kind {
        ENGINE_0, ENGINE_1, ENGINE_2, ENGINE_3, STARTER, SQUEAL, HORN, TURBO, SUPERCHARGER;

        /** Which engine recording this is, or -1. */
        int layer() {
            return ordinal() <= ENGINE_3.ordinal() ? ordinal() : -1;
        }
    }

    private static final Map<Integer, EnumMap<Kind, Loop>> PLAYING = new HashMap<>();
    private static final Map<Integer, Float> LAST_BOOST = new HashMap<>();
    private static final Map<Integer, Float> LAST_RPM = new HashMap<>();

    public static void tick(CarEntity car) {
        if (car.isStand()) return;
        EnumMap<Kind, Loop> loops = PLAYING.computeIfAbsent(car.getId(), id -> new EnumMap<>(Kind.class));
        for (Kind kind : Kind.values()) {
            Loop loop = loops.get(kind);
            if (wanted(car, kind, car.engineType()) && (loop == null || loop.isStopped())) {
                loop = new Loop(car, kind, car.engineType());
                loops.put(kind, loop);
                Minecraft.getInstance().getSoundManager().play(loop);
            }
        }
        loops.values().removeIf(Loop::isStopped);
        if (loops.isEmpty()) PLAYING.remove(car.getId());

        // A straight pipe pops and crackles when the revs fall off.
        float rpm = car.getRpm();
        float lastRpm = LAST_RPM.getOrDefault(car.getId(), rpm);
        LAST_RPM.put(car.getId(), rpm);
        if (car.isRunning() && car.getSetup().exhaust() == com.summercraft.car.CarSetup.ExhaustType.STRAIGHT_PIPE
                && rpm > 2500 && lastRpm - rpm > 60 && car.getRandom().nextInt(4) == 0) {
            car.level().playLocalSound(car.getX(), car.getY() + 0.3, car.getZ(), ModRegistry.BACKFIRE.get(),
                    SoundSource.NEUTRAL, 0.35f + car.getRandom().nextFloat() * 0.3f, 1.3f + car.getRandom().nextFloat() * 0.4f, false);
        }

        float boost = car.getBoost();
        float last = LAST_BOOST.getOrDefault(car.getId(), 0f);
        if (car.has(PartSlot.TURBO) && last > 0.35f && boost < last - 0.1f) {
            car.level().playLocalSound(car.getX(), car.getY() + 0.6, car.getZ(), ModRegistry.BLOW_OFF.get(),
                    SoundSource.NEUTRAL, Math.min(1f, last), 0.9f + last * 0.2f, false);
            LAST_BOOST.put(car.getId(), 0f);
        } else {
            LAST_BOOST.put(car.getId(), boost);
        }
    }

    /**
     * Equal-power crossfade between the two recordings either side of the current revs, so each one is only
     * pitched a little and the engine keeps its character all the way up.
     */
    private static float layerWeight(int layer, float rpm) {
        float[] rpms = ModRegistry.ENGINE_LAYER_RPMS;
        int last = rpms.length - 1;
        if (rpm <= rpms[0]) return layer == 0 ? 1 : 0;
        if (rpm >= rpms[last]) return layer == last ? 1 : 0;
        for (int i = 0; i < last; i++) {
            if (rpm <= rpms[i + 1]) {
                float t = (float) (Math.log(rpm / rpms[i]) / Math.log(rpms[i + 1] / rpms[i]));
                if (layer == i) return Mth.cos(t * Mth.HALF_PI);
                if (layer == i + 1) return Mth.sin(t * Mth.HALF_PI);
                return 0;
            }
        }
        return 0;
    }

    private static boolean wanted(CarEntity car, Kind kind, EngineType loopEngine) {
        return !car.isRemoved() && switch (kind) {
            case ENGINE_0, ENGINE_1, ENGINE_2, ENGINE_3 -> car.isRunning() && car.engineType() != null
                    && car.engineType() == loopEngine && layerWeight(kind.layer(), car.getRpm()) > 0.01f;
            case STARTER -> car.isCranking();
            case SQUEAL -> car.getSkid() > 0.05f;
            case HORN -> car.isHornOn();
            case TURBO -> car.isRunning() && car.has(PartSlot.TURBO) && car.getBoost() > 0.05f;
            case SUPERCHARGER -> car.isRunning() && car.has(PartSlot.SUPERCHARGER);
        };
    }

    private static SoundEvent event(Kind kind, EngineType engine) {
        String id = engine != null ? engine.id() : EngineType.INLINE_4.id();
        return switch (kind) {
            case ENGINE_0, ENGINE_1, ENGINE_2, ENGINE_3 -> ModRegistry.ENGINE_SOUNDS.get(id).get(kind.layer()).get();
            case STARTER -> ModRegistry.STARTER.get();
            case SQUEAL -> ModRegistry.TYRE_SQUEAL.get();
            case HORN -> ModRegistry.HORN.get();
            case TURBO -> ModRegistry.TURBO_WHISTLE.get();
            case SUPERCHARGER -> ModRegistry.SUPERCHARGER_WHINE.get();
        };
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final CarEntity car;
        private final Kind kind;
        private final EngineType engine;

        Loop(CarEntity car, Kind kind, EngineType engine) {
            super(event(kind, engine), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.kind = kind;
            this.engine = engine;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            follow();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (car.isRemoved()) {
                stop();
                return;
            }
            follow();
            float targetVolume = wanted(car, kind, engine) ? targetVolume() : 0;
            volume += (targetVolume - volume) * 0.4f;
            pitch = targetPitch();
            if (targetVolume == 0 && volume < 0.02f) stop();
        }

        private void follow() {
            x = car.getX();
            y = car.getY() + 0.6;
            z = car.getZ();
        }

        private float targetVolume() {
            float rpm = car.getRpm();
            float loud = car.has(PartSlot.EXHAUST) ? car.getSetup().exhaust().volume : 1.7f;
            float boost = car.getBoost();
            return switch (kind) {
                case ENGINE_0, ENGINE_1, ENGINE_2, ENGINE_3 -> (0.45f + rpm / 8000f) * layerWeight(kind.layer(), rpm) * loud;
                case STARTER -> 0.8f;
                case SQUEAL -> Math.min(1f, car.getSkid() * 1.3f);
                case HORN -> 1.2f;
                case TURBO -> Math.min(0.5f, boost * 0.6f);
                case SUPERCHARGER -> 0.1f + Math.min(0.4f, rpm / 14000f);
            };
        }

        private float targetPitch() {
            float rpm = Math.max(car.getRpm(), 300);
            return switch (kind) {
                case ENGINE_0, ENGINE_1, ENGINE_2, ENGINE_3 -> Mth.clamp(rpm / ModRegistry.ENGINE_LAYER_RPMS[kind.layer()], 0.5f, 2f);
                case STARTER, HORN -> 1f;
                case SQUEAL -> 0.9f + car.getSkid() * 0.25f;
                case TURBO -> Mth.clamp(0.6f + car.getBoost() * 1.4f, 0.5f, 2f);
                case SUPERCHARGER -> Mth.clamp(rpm / 3500f, 0.5f, 2f);
            };
        }
    }

    private CarSounds() {}
}
