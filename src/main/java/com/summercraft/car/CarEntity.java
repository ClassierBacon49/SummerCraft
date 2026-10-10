package com.summercraft.car;

import com.summercraft.ModRegistry;
import com.summercraft.item.PartItem;
import com.summercraft.item.WrenchItem;
import com.summercraft.network.DriveStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.summercraft.car.PartSlot.*;

public class CarEntity extends Entity {
    /** Wrench clicks needed to fully tighten one bolt. */
    public static final int BOLT_STEPS = 4;
    public static final float TANK_SIZE = 100f;
    public static final int PRIMER_GREY = 0xFF8C9089;
    private static final float AMBIENT_TEMP = 20f;
    private static final double HALF_WIDTH = 0.84, HALF_LENGTH = 1.95;
    private static final Vec3 DRIVER_SEAT = new Vec3(0.34, 0.38, -0.25);
    private static final Vec3 PASSENGER_SEAT = new Vec3(-0.34, 0.38, -0.25);

    private static final EnumSet<PartSlot> WHEELS = EnumSet.of(WHEEL_FL, WHEEL_FR, WHEEL_RL, WHEEL_RR);
    /** Parts a hard enough crash can knock loose. */
    private static final List<PartSlot> CRUMPLE_FRONT = List.of(BUMPER_F, FENDER_L, FENDER_R, HOOD, RADIATOR);
    private static final List<PartSlot> CRUMPLE_REAR = List.of(BUMPER_R, TRUNK_LID, EXHAUST);

    public static final float DEFAULT_MIXTURE = 11.5f, DEFAULT_TIMING = 6f;

    private static final EntityDataAccessor<CompoundTag> DATA_PARTS = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_OIL = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_COOLANT = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TEMP = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CRANKING = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_RPM = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_GEAR = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SKID = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BOOST = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<CompoundTag> DATA_SETUP = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> DATA_LIFT = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HORN = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_DRIVER = SynchedEntityData.defineId(CarEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Reads the driver's keyboard. Common code can't touch LocalPlayer, so the client mod swaps this in. */
    public static Function<Player, CarPhysics.Input> inputSource = player -> CarPhysics.Input.NONE;
    /** Client-only per-tick work such as sounds; set by the client mod. */
    public static Consumer<CarEntity> clientTick = car -> {};

    public static final class InstalledPart {
        public final int[] bolts;
        /** 0-100; wearing parts mirror it in item durability. */
        public float condition;
        /** Carburetor mixture (AFR) or distributor timing (degrees). */
        public float setting;
        public boolean open;

        InstalledPart(int boltCount, float condition, float setting) {
            this.bolts = new int[boltCount];
            this.condition = condition;
            this.setting = setting;
        }

        public boolean isSecure() {
            for (int bolt : bolts) if (bolt < BOLT_STEPS) return false;
            return true;
        }

        public boolean isFullyLoose() {
            for (int bolt : bolts) if (bolt > 0) return false;
            return true;
        }

        public int tightBolts() {
            int count = 0;
            for (int bolt : bolts) if (bolt == BOLT_STEPS) count++;
            return count;
        }

        /** 0 when every bolt is tight, 1 when every bolt is undone. */
        public float looseness() {
            int total = 0;
            for (int bolt : bolts) total += bolt;
            return 1f - (float) total / (bolts.length * BOLT_STEPS);
        }
    }

    /** What the crosshair is on: a part, and which bolt head if one was hit. */
    public record Aim(PartSlot slot, int bolt) {}

    private final EnumMap<PartSlot, InstalledPart> parts = new EnumMap<>(PartSlot.class);
    public final CarPhysics physics = new CarPhysics();

    private boolean wasLocallyControlled;
    private boolean wasOnDyno;
    /** True while the rear wheels sit on dyno rollers; the car is held in place. */
    public boolean dynoActive;
    @Nullable
    private Vec3 lastServerPos;
    private int crankTicks, crankNeeded;
    private float lastPartsSyncCondition;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;
    private float lerpYRot;

    // Client-side visuals
    public float frontWheelAngle, rearWheelAngle, steerVisual, bodyRoll, bodyPitch;
    private final EnumMap<PartSlot, Float> openAmount = new EnumMap<>(PartSlot.class);

    public CarEntity(EntityType<? extends CarEntity> type, Level level) {
        super(type, level);
        // Reserve ids for the hitbox parts right after the car's own, like the ender dragon does.
        if (!isStand()) setId(ENTITY_COUNTER.getAndAdd(PART_X.length * PART_Z.length + 1) + 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PARTS, new CompoundTag());
        builder.define(DATA_FUEL, 0f);
        builder.define(DATA_OIL, 0f);
        builder.define(DATA_COOLANT, 0f);
        builder.define(DATA_TEMP, AMBIENT_TEMP);
        builder.define(DATA_RUNNING, false);
        builder.define(DATA_CRANKING, false);
        builder.define(DATA_RPM, 0f);
        builder.define(DATA_GEAR, 0);
        builder.define(DATA_SKID, 0f);
        builder.define(DATA_BOOST, 0f);
        builder.define(DATA_SETUP, CarSetup.STOCK.save());
        builder.define(DATA_LIFT, 0f);
        builder.define(DATA_HORN, false);
        builder.define(DATA_COLOR, PRIMER_GREY);
        builder.define(DATA_DRIVER, Optional.empty());
    }

    // ================================================================ parts

    @Nullable
    public InstalledPart getPart(PartSlot slot) {
        return parts.get(slot);
    }

    public boolean has(PartSlot slot) {
        return parts.containsKey(slot);
    }

    private boolean isSecure(PartSlot slot) {
        InstalledPart part = parts.get(slot);
        return part != null && part.isSecure();
    }

    public boolean isOpen(PartSlot slot) {
        InstalledPart part = parts.get(slot);
        return part != null && part.open;
    }

    private float condition(PartSlot slot) {
        InstalledPart part = parts.get(slot);
        return part == null ? 0 : part.condition;
    }

    private float setting(PartSlot slot, float fallback) {
        InstalledPart part = parts.get(slot);
        return part == null ? fallback : part.setting;
    }

    /** Can {@code slot} take a part now? {@code type} is the engine it would sit on (for an engine block, its own). */
    private boolean canMount(PartSlot slot, @Nullable EngineType type) {
        if (has(slot) || (slot.parent() != null && !has(slot.parent()))) return false;
        if (isStand() && !slot.isPowertrain()) return false;
        return geometry(slot, type) != null;
    }

    private EngineType mountType(PartSlot slot, Item item) {
        return slot == ENGINE_BLOCK ? EngineType.byBlock(item) : engineType();
    }

    // ================================================================ geometry

    /** On the engine stand, the powertrain sits this far from where it would be in a car. */
    public static final Vec3 STAND_OFFSET = new Vec3(0, 0.45, -1.15);

    public boolean isStand() {
        return getType() == ModRegistry.ENGINE_STAND.get();
    }

    @Nullable
    public EngineType engineType() {
        InstalledPart block = parts.get(ENGINE_BLOCK);
        return block == null ? null : EngineType.byId((int) block.setting);
    }

    @Nullable
    public EngineLayout.Geometry geometry(PartSlot slot) {
        return geometry(slot, engineType());
    }

    /** Where a part sits: engine parts follow the engine type, and on the stand the powertrain is moved onto it. */
    @Nullable
    public EngineLayout.Geometry geometry(PartSlot slot, @Nullable EngineType type) {
        EngineLayout.Geometry geometry;
        if (slot.isEngine()) {
            if (type == null) return null;
            geometry = EngineLayout.get(type, slot);
            if (geometry == null) return null;
        } else {
            geometry = slot.fixedGeometry();
        }
        return isStand() && slot.isPowertrain() ? EngineLayout.shifted(geometry, STAND_OFFSET) : geometry;
    }

    /** Aiming box of an installed part, following a panel that's swung open. */
    public AABB partBounds(PartSlot slot, boolean open) {
        AABB bounds = geometry(slot).bounds();
        if (!open || slot.hinge() == null) return bounds;
        AABB result = null;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 p = slot.swing(new Vec3((corner & 1) == 0 ? bounds.minX : bounds.maxX,
                    (corner & 2) == 0 ? bounds.minY : bounds.maxY,
                    (corner & 4) == 0 ? bounds.minZ : bounds.maxZ), 1f);
            result = result == null ? new AABB(p, p) : result.minmax(new AABB(p, p));
        }
        return result;
    }

    /** Aiming box of one bolt head. */
    public AABB boltBounds(PartSlot slot, int index, boolean open) {
        EngineLayout.Geometry geometry = geometry(slot);
        Vec3 normal = Vec3.atLowerCornerOf(geometry.face().getNormal());
        Vec3 centre = slot.swing(geometry.bolts().get(index).add(normal.scale(0.02)), open ? 1f : 0f);
        return new AABB(centre, centre).inflate(0.035);
    }

    private Optional<PartSlot> installedChild(PartSlot slot) {
        for (PartSlot other : PartSlot.values()) {
            if (other.parent() == slot && has(other)) return Optional.of(other);
        }
        return Optional.empty();
    }

    /** The installed part (and bolt) under the player's crosshair. */
    public Optional<Aim> aim(Player player) {
        Vec3[] ray = localRay(player);
        Aim best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PartSlot slot : PartSlot.values()) {
            InstalledPart part = parts.get(slot);
            if (part == null) continue;
            for (int i = 0; i < part.bolts.length; i++) {
                Optional<Vec3> hit = boltBounds(slot, i, part.open).clip(ray[0], ray[1]);
                if (hit.isPresent() && hit.get().distanceToSqr(ray[0]) < bestDistance) {
                    bestDistance = hit.get().distanceToSqr(ray[0]);
                    best = new Aim(slot, i);
                }
            }
            Optional<Vec3> hit = partBounds(slot, part.open).clip(ray[0], ray[1]);
            // Bolt heads sit proud of the part, so a bolt hit wins ties against its own part.
            if (hit.isPresent() && hit.get().distanceToSqr(ray[0]) < bestDistance - 1e-4) {
                bestDistance = hit.get().distanceToSqr(ray[0]);
                best = new Aim(slot, -1);
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<PartSlot> pickInstalled(Player player) {
        return aim(player).map(Aim::slot);
    }

    /** Where a part item would go: the free slot being aimed at, else the first free one. */
    public Optional<PartSlot> pickInstallTarget(Player player, Item item) {
        Predicate<PartSlot> fits = slot -> slot.accepts(item) && canMount(slot, mountType(slot, item));
        Vec3[] ray = localRay(player);
        PartSlot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PartSlot slot : PartSlot.values()) {
            if (!fits.test(slot)) continue;
            Optional<Vec3> hit = geometry(slot, mountType(slot, item)).bounds().clip(ray[0], ray[1]);
            if (hit.isPresent() && hit.get().distanceToSqr(ray[0]) < bestDistance) {
                bestDistance = hit.get().distanceToSqr(ray[0]);
                best = slot;
            }
        }
        return Optional.ofNullable(best).or(() -> forItem(item).stream().filter(fits).findFirst());
    }

    /** Explains why a part item can't go on right now. */
    public Component installProblem(Item item) {
        for (PartSlot slot : forItem(item)) {
            if (isStand() && !slot.isPowertrain()) return Component.translatable("message.summercraft.not_on_stand");
            if (!has(slot) && slot.parent() != null && !has(slot.parent())) {
                return Component.translatable("message.summercraft.needs_parent", slot.parent().displayName());
            }
            if (!has(slot) && geometry(slot, mountType(slot, item)) == null) {
                return Component.translatable("message.summercraft.not_for_engine", engineType().displayName());
            }
        }
        return Component.translatable("message.summercraft.already_installed");
    }

    /** The player's look ray in car-local space, as {from, to}. */
    private Vec3[] localRay(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(player.entityInteractionRange() + 1));
        return new Vec3[]{worldToLocal(eye), worldToLocal(end)};
    }

    public Vec3 localToWorld(Vec3 local) {
        return position().add(local.yRot(-getYRot() * Mth.DEG_TO_RAD));
    }

    public Vec3 worldToLocal(Vec3 world) {
        return world.subtract(position()).yRot(getYRot() * Mth.DEG_TO_RAD);
    }

    private void syncParts() {
        entityData.set(DATA_PARTS, writeParts());
    }

    private CompoundTag writeParts() {
        return writeParts(slot -> true);
    }

    private CompoundTag writeParts(Predicate<PartSlot> which) {
        CompoundTag tag = new CompoundTag();
        parts.forEach((slot, part) -> {
            if (!which.test(slot)) return;
            CompoundTag entry = new CompoundTag();
            entry.putIntArray("bolts", part.bolts);
            entry.putFloat("condition", part.condition);
            entry.putFloat("setting", part.setting);
            entry.putBoolean("open", part.open);
            tag.put(slot.id(), entry);
        });
        return tag;
    }

    private void readParts(CompoundTag tag) {
        parts.clear();
        readParts(tag, isStand());
    }

    /** Adds saved parts, skipping any that don't fit the engine they're on. */
    private void readParts(CompoundTag tag, boolean powertrainOnly) {
        // The engine block comes first in the enum, so the engine type is known before its parts are read.
        for (PartSlot slot : PartSlot.values()) {
            if (!tag.contains(slot.id()) || (powertrainOnly && !slot.isPowertrain())) continue;
            CompoundTag entry = tag.getCompound(slot.id());
            EngineType type = slot == ENGINE_BLOCK ? EngineType.byId((int) entry.getFloat("setting")) : engineType();
            EngineLayout.Geometry geometry = geometry(slot, type);
            if (geometry == null) continue;
            InstalledPart part = new InstalledPart(geometry.boltCount(), entry.getFloat("condition"), entry.getFloat("setting"));
            int[] saved = entry.getIntArray("bolts");
            System.arraycopy(saved, 0, part.bolts, 0, Math.min(saved.length, part.bolts.length));
            part.open = entry.getBoolean("open");
            parts.put(slot, part);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PARTS.equals(key) && level().isClientSide) {
            readParts(entityData.get(DATA_PARTS));
        }
    }

    private static ItemStack toStack(PartSlot slot, InstalledPart part) {
        ItemStack stack = new ItemStack(slot == ENGINE_BLOCK ? EngineType.byId((int) part.setting).block() : slot.item());
        if (stack.isDamageableItem()) {
            stack.setDamageValue(Math.round((1f - part.condition / 100f) * stack.getMaxDamage()));
        }
        return stack;
    }

    private static float conditionOf(ItemStack stack) {
        return stack.isDamageableItem() ? 100f * (1f - (float) stack.getDamageValue() / stack.getMaxDamage()) : 100f;
    }

    private static float factorySetting(PartSlot slot) {
        return switch (slot) {
            case CARBURETOR -> DEFAULT_MIXTURE;
            case DISTRIBUTOR -> DEFAULT_TIMING;
            default -> 0;
        };
    }

    /** Removing a part that holds a fluid spills it. */
    private void drainFluid(PartSlot slot) {
        switch (slot) {
            case OIL_PAN -> setOil(0);
            case FUEL_TANK -> setFuel(0);
            case RADIATOR -> setCoolant(0);
            default -> {}
        }
    }

    private void wear(PartSlot slot, float amount) {
        InstalledPart part = parts.get(slot);
        if (part != null && amount > 0) part.condition = Math.max(0, part.condition - amount);
    }

    // ================================================================ interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        Optional<Aim> aim = aim(player);
        if (tryFill(player, hand, held)) return InteractionResult.CONSUME;

        if (held.getItem() instanceof DyeItem dye && !isStand()) {
            paint(player, held, dye);
        } else if (held.is(ModRegistry.ENGINE_ASSEMBLY.get())) {
            installEngine(player, held);
        } else if (held.getItem() instanceof PartItem) {
            install(player, held);
        } else if (held.getItem() instanceof WrenchItem) {
            aim.ifPresentOrElse(target -> turnWrench(player, target, WrenchItem.size(held), player.isShiftKeyDown()),
                    () -> say(player, Component.translatable("message.summercraft.aim_at_part")));
        } else if (held.is(ModRegistry.SCREWDRIVER.get())) {
            aim.ifPresentOrElse(target -> adjust(player, target.slot(), player.isShiftKeyDown()),
                    () -> say(player, Component.translatable("message.summercraft.aim_at_part")));
        } else if (held.isEmpty() && player.isShiftKeyDown()) {
            aim.ifPresent(target -> takeOff(player, target.slot()));
        } else if (held.isEmpty() && aim.isPresent() && aim.get().slot().hinge() != null) {
            toggleOpen(aim.get().slot());
        } else {
            enter(player);
        }
        return InteractionResult.CONSUME;
    }

    private void install(Player player, ItemStack stack) {
        Optional<PartSlot> target = pickInstallTarget(player, stack.getItem());
        if (target.isEmpty()) {
            say(player, installProblem(stack.getItem()));
            return;
        }
        PartSlot slot = target.get();
        EngineType type = mountType(slot, stack.getItem());
        int bolts = geometry(slot, type).boltCount();
        float setting = slot == ENGINE_BLOCK ? type.ordinal() : factorySetting(slot);
        parts.put(slot, new InstalledPart(bolts, conditionOf(stack), setting));
        stack.consume(1, player);
        playSound(SoundEvents.CHAIN_PLACE, 1f, 0.8f);
        syncParts();
        say(player, Component.translatable("message.summercraft.installed", slot.displayName(), bolts, slot.boltSize()));
    }

    /** Lowers a whole engine (from the stand or another car) into an empty engine bay. */
    private void installEngine(Player player, ItemStack stack) {
        if (has(ENGINE_BLOCK)) {
            say(player, Component.translatable("message.summercraft.engine_bay_full"));
            return;
        }
        CompoundTag contents = EngineAssembly.contents(stack);
        readParts(contents.getCompound("Parts"), true);
        // Engine mounts (or stand bolts) always start undone on the new home.
        InstalledPart block = parts.get(ENGINE_BLOCK);
        if (block != null) java.util.Arrays.fill(block.bolts, 0);
        if (has(OIL_PAN)) setOil(contents.getFloat("Oil"));
        stack.consume(1, player);
        playSound(SoundEvents.PISTON_CONTRACT, 1f, 0.6f);
        syncParts();
        say(player, Component.translatable(isStand() ? "message.summercraft.engine_on_stand" : "message.summercraft.engine_in"));
    }

    /** Hoists the engine out with everything bolted to it, as one item. */
    private void liftEngine(Player player) {
        EngineType type = engineType();
        CompoundTag powertrain = writeParts(PartSlot::isPowertrain);
        ItemStack stack = EngineAssembly.create(type, powertrain, has(OIL_PAN) ? getOil() : 0);
        for (PartSlot slot : PartSlot.values()) if (slot.isPowertrain()) parts.remove(slot);
        setOil(0);
        if (isRunning()) stopEngine(null);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        playSound(SoundEvents.PISTON_EXTEND, 1f, 0.6f);
        syncParts();
    }

    private void turnWrench(Player player, Aim aim, int wrenchSize, boolean loosen) {
        PartSlot slot = aim.slot();
        InstalledPart part = parts.get(slot);
        if (wrenchSize != slot.boltSize()) {
            playSound(SoundEvents.CHAIN_STEP, 0.5f, 1.8f);
            say(player, Component.translatable("message.summercraft.wrong_size", wrenchSize));
            return;
        }
        int[] bolts = part.bolts;
        int index = aim.bolt();
        if (index < 0) {
            // Not on a specific bolt: work the next one in line.
            index = -1;
            for (int i = 0; i < bolts.length && index < 0; i++) {
                int candidate = loosen ? bolts.length - 1 - i : i;
                if (loosen ? bolts[candidate] > 0 : bolts[candidate] < BOLT_STEPS) index = candidate;
            }
        }
        if (index < 0 || (loosen ? bolts[index] == 0 : bolts[index] == BOLT_STEPS)) {
            say(player, Component.translatable(loosen ? "message.summercraft.bolt_loose" : "message.summercraft.bolt_tight"));
            return;
        }
        bolts[index] += loosen ? -1 : 1;
        playSound(ModRegistry.RATCHET.get(), 0.7f, 0.85f + bolts[index] * 0.08f + random.nextFloat() * 0.05f);
        syncParts();
    }

    private void adjust(Player player, PartSlot slot, boolean down) {
        InstalledPart part = parts.get(slot);
        switch (slot) {
            case CARBURETOR -> {
                part.setting = Mth.clamp(part.setting + (down ? -0.25f : 0.25f), 8f, 20f);
                say(player, Component.translatable("message.summercraft.mixture", String.format("%.2f", part.setting)));
            }
            case DISTRIBUTOR -> {
                part.setting = Mth.clamp(part.setting + (down ? -1f : 1f), 0f, 30f);
                say(player, Component.translatable("message.summercraft.timing", Math.round(part.setting)));
            }
            default -> {
                say(player, Component.translatable("message.summercraft.nothing_to_adjust"));
                return;
            }
        }
        playSound(SoundEvents.UI_LOOM_SELECT_PATTERN, 0.6f, down ? 0.8f : 1.2f);
        syncParts();
    }

    private void toggleOpen(PartSlot slot) {
        InstalledPart part = parts.get(slot);
        part.open = !part.open;
        SoundEvent sound = switch (slot) {
            case DOOR_L, DOOR_R -> part.open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE;
            default -> part.open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE;
        };
        playSound(sound, 0.8f, 1.1f);
        syncParts();
    }

    private void paint(Player player, ItemStack stack, DyeItem dye) {
        int color = 0xFF000000 | dye.getDyeColor().getTextureDiffuseColor();
        if (getColor() == color) return;
        entityData.set(DATA_COLOR, color);
        stack.consume(1, player);
        playSound(SoundEvents.BUCKET_EMPTY_POWDER_SNOW, 0.8f, 1.4f);
    }

    private void enter(Player player) {
        if (isStand()) return;
        if (isLifted()) {
            say(player, Component.translatable("message.summercraft.on_lift"));
            return;
        }
        boolean driverSide = worldToLocal(player.position()).x > 0;
        PartSlot seat = driverSide ? SEAT_L : SEAT_R;
        PartSlot door = driverSide ? DOOR_L : DOOR_R;
        if (!has(seat)) {
            say(player, Component.translatable("message.summercraft.no_seat"));
        } else if (has(door) && !isOpen(door)) {
            say(player, Component.translatable("message.summercraft.door_closed"));
        } else if (driverSide ? getDriver() != null : passengerCount() > 0) {
            say(player, Component.translatable("message.summercraft.seat_taken"));
        } else {
            if (driverSide) entityData.set(DATA_DRIVER, Optional.of(player.getUUID()));
            if (!player.startRiding(this) && driverSide) entityData.set(DATA_DRIVER, Optional.empty());
        }
    }

    private int passengerCount() {
        int count = 0;
        for (Entity passenger : getPassengers()) if (passenger != getDriver()) count++;
        return count;
    }

    private void takeOff(Player player, PartSlot slot) {
        InstalledPart part = parts.get(slot);
        if (!part.isFullyLoose()) {
            say(player, Component.translatable("message.summercraft.still_bolted", slot.displayName()));
            return;
        }
        Optional<PartSlot> child = installedChild(slot);
        if (slot == ENGINE_BLOCK && child.isPresent()) {
            liftEngine(player);
            return;
        }
        if (child.isPresent()) {
            say(player, Component.translatable("message.summercraft.remove_first", child.get().displayName()));
            return;
        }
        parts.remove(slot);
        drainFluid(slot);
        ItemStack stack = toStack(slot, part);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        playSound(SoundEvents.ITEM_PICKUP, 0.5f, 0.8f);
        syncParts();
    }

    /** A part shaking loose on the road takes everything mounted on it along. */
    private void detach(PartSlot slot) {
        for (Optional<PartSlot> child = installedChild(slot); child.isPresent(); child = installedChild(slot)) {
            detach(child.get());
        }
        if (!has(slot)) return;
        Vec3 at = localToWorld(geometry(slot).bounds().getCenter());
        InstalledPart part = parts.remove(slot);
        drainFluid(slot);
        ItemEntity drop = new ItemEntity(level(), at.x, at.y, at.z, toStack(slot, part));
        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
    }

    private boolean tryFill(Player player, InteractionHand hand, ItemStack held) {
        if (held.is(ModRegistry.MOTOR_OIL.get())) {
            return fill(player, hand, OIL_PAN, getOil(), this::setOil, 25f, new ItemStack(Items.GLASS_BOTTLE));
        }
        if (held.is(ModRegistry.JERRY_CAN.get())) {
            return fill(player, hand, FUEL_TANK, getFuel(), this::setFuel, 50f, ItemStack.EMPTY);
        }
        if (held.is(Items.WATER_BUCKET)) {
            return fill(player, hand, RADIATOR, getCoolant(), this::setCoolant, TANK_SIZE, new ItemStack(Items.BUCKET));
        }
        return false;
    }

    private boolean fill(Player player, InteractionHand hand, PartSlot into, float current, Consumer<Float> setter, float amount, ItemStack leftover) {
        if (!has(into)) {
            say(player, Component.translatable("message.summercraft.nothing_to_fill", into.displayName()));
            return true;
        }
        if (current >= TANK_SIZE - 0.5f) {
            say(player, Component.translatable("message.summercraft.full", into.displayName()));
            return true;
        }
        setter.accept(current + amount);
        if (!player.getAbilities().instabuild) {
            ItemStack held = player.getItemInHand(hand);
            held.shrink(1);
            if (!leftover.isEmpty()) {
                if (held.isEmpty()) player.setItemInHand(hand, leftover);
                else if (!player.getInventory().add(leftover)) player.drop(leftover, false);
            }
        }
        playSound(SoundEvents.BUCKET_EMPTY, 0.6f, 1.2f);
        say(player, Component.translatable("message.summercraft.filled", into.displayName(), Math.round(Math.min(TANK_SIZE, current + amount))));
        return true;
    }

    private static void say(Player player, Component message) {
        player.displayClientMessage(message, true);
    }

    private void notifyDriver(Component message) {
        if (getControllingPassenger() instanceof Player driver) say(driver, message);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return true;
        if (!(source.getEntity() instanceof Player player)) return false;
        if (!parts.isEmpty()) {
            say(player, Component.translatable("message.summercraft.strip_first"));
        } else if (!player.isShiftKeyDown()) {
            say(player, Component.translatable("message.summercraft.sneak_to_pick_up"));
        } else {
            ItemStack drop = new ItemStack(isStand() ? ModRegistry.ENGINE_STAND_ITEM.get() : ModRegistry.CAR_CHASSIS.get());
            if (!player.getAbilities().instabuild) spawnAtLocation(drop);
            discard();
        }
        return true;
    }

    /**
     * Fits every part, fully tightened and tuned, and fills it up. For testing and the spawn command.
     * On a stand only the powertrain goes on.
     */
    public void buildComplete(EngineType type, boolean turbo, boolean supercharger) {
        parts.clear();
        InstalledPart block = new InstalledPart(geometry(ENGINE_BLOCK, type).boltCount(), 100, type.ordinal());
        parts.put(ENGINE_BLOCK, block);
        for (PartSlot slot : PartSlot.values()) {
            if (slot == ENGINE_BLOCK || (slot == TURBO && !turbo) || (slot == SUPERCHARGER && !supercharger)) continue;
            if (isStand() && !slot.isPowertrain()) continue;
            EngineLayout.Geometry geometry = geometry(slot, type);
            if (geometry == null) continue;
            parts.put(slot, new InstalledPart(geometry.boltCount(), 100, factorySetting(slot)));
        }
        for (InstalledPart part : parts.values()) java.util.Arrays.fill(part.bolts, BOLT_STEPS);
        parts.get(CARBURETOR).setting = 12.8f;
        // Boost wants less ignition advance.
        parts.get(DISTRIBUTOR).setting = turbo || supercharger ? 10f : 14f;
        setFuel(TANK_SIZE);
        setOil(TANK_SIZE);
        setCoolant(TANK_SIZE);
        syncParts();
    }

    public void setColor(int color) {
        entityData.set(DATA_COLOR, 0xFF000000 | color);
    }

    /** Opens or closes a hinged panel; used by the showcase. */
    public void setOpen(PartSlot slot, boolean open) {
        InstalledPart part = parts.get(slot);
        if (part != null && slot.hinge() != null) {
            part.open = open;
            syncParts();
        }
    }

    // ================================================================ setup and lift

    /** How high a raised lift holds the car. */
    public static final float LIFT_HEIGHT = 1.5f;
    @Nullable
    private BlockPos liftPos;
    private float liftTarget;

    public CarSetup getSetup() {
        return CarSetup.load(entityData.get(DATA_SETUP));
    }

    /** Height of the lift under the car, animated. */
    public float liftHeight() {
        return entityData.get(DATA_LIFT);
    }

    public boolean isLifted() {
        return liftHeight() > 0.01f || liftPos != null && liftTarget > 0;
    }

    /** Raises or lowers the car on the lift at {@code pos}. Anyone inside has to get out first. */
    public void setLift(BlockPos pos, boolean up) {
        if (up) {
            ejectPassengers();
            if (isRunning()) stopEngine(null);
            liftPos = pos;
            liftTarget = LIFT_HEIGHT;
            setDeltaMovement(Vec3.ZERO);
            physics.vx = physics.vz = physics.yawRate = physics.rearWheelSpeed = 0;
        } else if (liftPos != null) {
            liftTarget = 0;
        }
        playSound(SoundEvents.PISTON_EXTEND, 0.6f, up ? 0.7f : 0.5f);
    }

    /** Sets the setup directly, no lift needed; for the showcase and commands. */
    public void setSetup(CarSetup setup) {
        entityData.set(DATA_SETUP, setup.validated().save());
    }

    /** Applies a new setup. Only while the car is up on the lift. */
    public void applySetup(Player player, CarSetup setup) {
        if (liftHeight() < LIFT_HEIGHT - 0.05f) {
            say(player, Component.translatable("message.summercraft.raise_first"));
            return;
        }
        entityData.set(DATA_SETUP, setup.validated().save());
        playSound(ModRegistry.RATCHET.get(), 0.8f, 1.1f);
        say(player, Component.translatable("message.summercraft.setup_applied"));
    }

    private void tickLift() {
        if (!level().getBlockState(liftPos).is(ModRegistry.CAR_LIFT.get())) {
            // The lift was broken out from under it: drop.
            liftPos = null;
            entityData.set(DATA_LIFT, 0f);
            return;
        }
        float height = liftHeight();
        height += Mth.clamp(liftTarget - height, -0.03f, 0.03f);
        entityData.set(DATA_LIFT, height);
        setDeltaMovement(Vec3.ZERO);
        setPos(getX(), liftPos.getY() + 2 / 16.0 + height, getZ());
        if (height <= 0 && liftTarget <= 0) liftPos = null;
    }

    // ================================================================ seats

    @Nullable
    public Player getDriver() {
        Optional<UUID> id = entityData.get(DATA_DRIVER);
        if (id.isEmpty()) return null;
        for (Entity passenger : getPassengers()) {
            if (passenger.getUUID().equals(id.get()) && passenger instanceof Player player) return player;
        }
        return null;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getDriver();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < 2;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && entityData.get(DATA_DRIVER).filter(passenger.getUUID()::equals).isPresent()) {
            entityData.set(DATA_DRIVER, Optional.empty());
            entityData.set(DATA_HORN, false);
            entityData.set(DATA_CRANKING, false);
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Vec3 seat = (passenger == getDriver() ? DRIVER_SEAT : PASSENGER_SEAT).add(0, getSetup().rideHeight() * 0.01, 0);
        return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        float turn = getYRot() - yRotO;
        passenger.setYRot(passenger.getYRot() + turn);
        passenger.setYHeadRot(passenger.getYHeadRot() + turn);
    }

    // ================================================================ engine

    /** Everything that has to be bolted in for the engine to turn over and fire. */
    private List<PartSlot> engineParts() {
        List<PartSlot> list = new java.util.ArrayList<>(List.of(
                ENGINE_BLOCK, CRANKSHAFT, PISTONS, CYLINDER_HEAD, SPARK_PLUGS, CARBURETOR, DISTRIBUTOR, OIL_PAN, CLUTCH, GEARBOX, STARTER, BATTERY, FUEL_TANK));
        if (engineType() != null && engineType().banks == 2) list.addAll(List.of(CYLINDER_HEAD_B, SPARK_PLUGS_B));
        return list;
    }

    /** Engine parts that lose compression or spark while loose. */
    private List<PartSlot> compressionParts() {
        List<PartSlot> list = new java.util.ArrayList<>(List.of(ENGINE_BLOCK, CRANKSHAFT, PISTONS, CYLINDER_HEAD, SPARK_PLUGS, CARBURETOR, DISTRIBUTOR));
        if (engineType() != null && engineType().banks == 2) list.addAll(List.of(CYLINDER_HEAD_B, SPARK_PLUGS_B));
        return list;
    }

    /** The worse of the two plug sets on a two-bank engine. */
    private float plugCondition() {
        EngineType type = engineType();
        return type != null && type.banks == 2 ? Math.min(condition(SPARK_PLUGS), condition(SPARK_PLUGS_B)) : condition(SPARK_PLUGS);
    }

    public float getBoost() {
        return isControlledByLocalInstance() ? (float) physics.boost : entityData.get(DATA_BOOST);
    }

    /** Rear wheels sitting on dyno rollers. */
    public boolean onDyno() {
        if (isStand()) return false;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 at = localToWorld(new Vec3(side * 0.7, 0, -1.15));
            BlockPos pos = BlockPos.containing(at.x, at.y - 0.05, at.z);
            if (level().getBlockState(pos).is(ModRegistry.DYNO.get()) || level().getBlockState(pos.below()).is(ModRegistry.DYNO.get())) return true;
        }
        return false;
    }

    public boolean isRunning() { return entityData.get(DATA_RUNNING); }
    public boolean isCranking() { return entityData.get(DATA_CRANKING); }
    public boolean isHornOn() { return entityData.get(DATA_HORN); }
    public float getFuel() { return entityData.get(DATA_FUEL); }
    public float getOil() { return entityData.get(DATA_OIL); }
    public float getCoolant() { return entityData.get(DATA_COOLANT); }
    public float getTemperature() { return entityData.get(DATA_TEMP); }
    public int getColor() { return entityData.get(DATA_COLOR); }

    /** Engine speed: live for the driver, synced for everyone else. */
    public float getRpm() {
        return isControlledByLocalInstance() ? (float) physics.rpm() : entityData.get(DATA_RPM);
    }

    public int getGear() {
        return isControlledByLocalInstance() ? physics.gear : entityData.get(DATA_GEAR);
    }

    /** 0-1, how hard the tyres are sliding. */
    public float getSkid() {
        return isControlledByLocalInstance() ? skidAmount() : entityData.get(DATA_SKID);
    }

    private float skidAmount() {
        if (!onGround()) return 0;
        double wheelspin = Math.abs(physics.rearWheelSpeed - physics.vz) / 5;
        double sideways = physics.speed() > 3 ? Math.abs(physics.vx) / 3 : 0;
        return (float) Mth.clamp(Math.max(wheelspin, sideways) - 0.15, 0, 1);
    }

    private void setRunning(boolean running) { entityData.set(DATA_RUNNING, running); }
    private void setFuel(float value) { entityData.set(DATA_FUEL, Mth.clamp(value, 0, TANK_SIZE)); }
    private void setOil(float value) { entityData.set(DATA_OIL, Mth.clamp(value, 0, TANK_SIZE)); }
    private void setCoolant(float value) { entityData.set(DATA_COOLANT, Mth.clamp(value, 0, TANK_SIZE)); }
    private void setTemperature(float value) { entityData.set(DATA_TEMP, value); }

    public float getMixture() { return setting(CARBURETOR, DEFAULT_MIXTURE); }
    public float getTiming() { return setting(DISTRIBUTOR, DEFAULT_TIMING); }

    /** Engine torque multiplier from tuning, wear and heat. Shared so the driver's physics agree with the server. */
    public double enginePower() {
        double mixture = getMixture() - 12.8, timing = getTiming() - 14;
        double power = Mth.clamp(1 - 0.03 * mixture * mixture, 0.15, 1)
                * Mth.clamp(1 - 0.002 * timing * timing, 0.3, 1)
                * (0.6 + 0.4 * Math.min(1, plugCondition() / 50))
                * (0.55 + 0.45 * condition(PISTONS) / 100)
                * (getTemperature() > 115 ? 0.75 : 1)
                * (has(EXHAUST) ? getSetup().exhaust().power : 0.92);
        return power;
    }

    private CarPhysics.Env physicsEnv() {
        CarSetup setup = getSetup();
        boolean drivenWheels = switch (setup.drivetrain()) {
            case RWD -> has(WHEEL_RL) && has(WHEEL_RR);
            case FWD -> has(WHEEL_FL) && has(WHEEL_FR);
            case AWD -> has(WHEEL_FL) && has(WHEEL_FR) && has(WHEEL_RL) && has(WHEEL_RR);
        };
        boolean drivetrain = has(CLUTCH) && has(GEARBOX) && drivenWheels;
        double clutchGrip = (0.25 + 0.75 * Math.min(1, condition(CLUTCH) / 30)) * (isSecure(CLUTCH) ? 1 : 0.5);
        double surfaceFront = surfaceGrip(new Vec3(0, 0, 1.15)), surfaceRear = surfaceGrip(new Vec3(0, 0, -1.15));
        int missing = 0;
        for (PartSlot wheel : WHEELS) if (!has(wheel)) missing++;
        EngineType type = engineType() != null ? engineType() : EngineType.INLINE_4;
        double muFront = surfaceFront * tyreGrip(WHEEL_FL, WHEEL_FR) * CarSetup.widthGrip(setup.widthFront()) * CarSetup.camberLongitudinal(setup.camberFront());
        double muRear = surfaceRear * tyreGrip(WHEEL_RL, WHEEL_RR) * CarSetup.widthGrip(setup.widthRear()) * CarSetup.camberLongitudinal(setup.camberRear());
        double rolling = surfaceRolling() * (setup.widthFront() + setup.widthRear()) / 370.0 + 0.004 * (Math.abs(setup.toeFront()) + Math.abs(setup.toeRear()));
        return new CarPhysics.Env(isRunning(), isCranking(), enginePower(), drivetrain, clutchGrip,
                muFront, muRear, rolling, brakeStrength(BRAKES_F), brakeStrength(BRAKES_R), has(STEERING_WHEEL), missing,
                type.peakTorque, type.peakRpm, type.redline,
                isSecure(TURBO) && has(EXHAUST), isSecure(SUPERCHARGER), onDyno(), chassis(setup));
    }

    /** What the alignment, springs and ride height do to the handling. */
    private static CarPhysics.Chassis chassis(CarSetup setup) {
        double balance = setup.springFront() - setup.springRear();
        double track = 1 + setup.offset() * 0.001;
        double latFront = CarSetup.camberLateral(setup.camberFront()) / CarSetup.camberLongitudinal(setup.camberFront())
                * (1 - 0.06 * balance) * track;
        double latRear = CarSetup.camberLateral(setup.camberRear()) / CarSetup.camberLongitudinal(setup.camberRear())
                * (1 + 0.06 * balance) * track;
        double response = 0.9 + 0.1 * setup.springAverage();
        // Front toe-out sharpens turn-in, rear toe-in calms the back end.
        double stiffFront = Mth.clamp(1 - 0.1 * setup.toeFront(), 0.85, 1.15) * response;
        double stiffRear = Mth.clamp(1 + 0.15 * setup.toeRear(), 0.8, 1.2) * response;
        return new CarPhysics.Chassis(latFront, latRear, stiffFront, stiffRear, setup.drivetrain().frontShare,
                0.45 + setup.rideHeight() * 0.01);
    }

    private double tyreGrip(PartSlot left, PartSlot right) {
        return (tyreGrip(left) + tyreGrip(right)) / 2;
    }

    private double tyreGrip(PartSlot wheel) {
        InstalledPart part = parts.get(wheel);
        if (part == null) return 0.4;
        return (0.55 + 0.45 * part.condition / 100) * (part.isSecure() ? 1 : 0.85);
    }

    private double brakeStrength(PartSlot brakes) {
        InstalledPart part = parts.get(brakes);
        if (part == null) return 0.05;
        return (0.3 + 0.7 * part.condition / 100) * (part.isSecure() ? 1 : 0.6);
    }

    private BlockState groundAt(Vec3 local) {
        Vec3 at = localToWorld(local);
        BlockState surface = level().getBlockState(BlockPos.containing(at.x, at.y - 0.05, at.z));
        return surface.is(Blocks.SNOW) || surface.is(Blocks.MUD) ? surface
                : level().getBlockState(BlockPos.containing(at.x, at.y - 0.3, at.z));
    }

    /** Grip of the ground, adjusted for the tyre compound: slicks love tarmac, knobblies love dirt. */
    private double surfaceGrip(Vec3 local) {
        BlockState ground = groundAt(local);
        CarSetup.TyreType tyres = getSetup().tyres();
        if (ground.is(BlockTags.ICE)) return 0.12 * tyres.ice;
        if (ground.is(Blocks.SNOW) || ground.is(Blocks.SNOW_BLOCK) || ground.is(Blocks.POWDER_SNOW)) return 0.35 * tyres.loose;
        if (ground.is(Blocks.MUD) || ground.is(Blocks.SOUL_SAND)) return 0.4 * tyres.loose;
        if (ground.is(BlockTags.SAND) || ground.is(Blocks.GRAVEL)) return 0.55 * tyres.loose;
        if (ground.is(Blocks.DIRT_PATH)) return 0.85 * (tyres.loose + tyres.tarmac) / 2;
        if (ground.is(BlockTags.DIRT) || ground.is(BlockTags.LEAVES)) return 0.7 * tyres.loose;
        return 1.0 * tyres.tarmac;
    }

    private double surfaceRolling() {
        BlockState ground = groundAt(Vec3.ZERO);
        if (ground.is(BlockTags.SAND) || ground.is(Blocks.MUD) || ground.is(Blocks.SOUL_SAND)) return 0.08;
        if (ground.is(Blocks.SNOW) || ground.is(Blocks.SNOW_BLOCK)) return 0.05;
        if (ground.is(BlockTags.DIRT) || ground.is(Blocks.GRAVEL)) return 0.025;
        return 0.012;
    }

    /** Called when the driver turns or releases the key. */
    public void setIgnition(Player player, boolean turning) {
        if (!turning) {
            entityData.set(DATA_CRANKING, false);
            return;
        }
        if (isRunning()) {
            stopEngine(null);
            return;
        }
        if (!isSecure(BATTERY) || !isSecure(STARTER)) {
            playSound(SoundEvents.LEVER_CLICK, 0.6f, 1.4f);
            say(player, Component.translatable("message.summercraft.start.click"));
            return;
        }
        crankTicks = 0;
        crankNeeded = 10 + random.nextInt(10)
                + (getTemperature() < 40 && getMixture() > 13.5f ? 25 : 0)
                + (plugCondition() < 50 ? 15 : 0)
                + (Math.abs(getTiming() - 12) > 8 ? 15 : 0);
        entityData.set(DATA_CRANKING, true);
    }

    /** Why the engine can't fire right now, or null if it can. */
    @Nullable
    private Component fireFailure() {
        for (PartSlot slot : engineParts()) {
            if (!has(slot)) return Component.translatable("message.summercraft.start.missing", slot.displayName());
        }
        if (condition(PISTONS) <= 0) return Component.translatable("message.summercraft.start.seized");
        for (PartSlot slot : compressionParts()) {
            if (!isSecure(slot)) return Component.translatable("message.summercraft.start.loose");
        }
        if (getFuel() <= 0) return Component.translatable("message.summercraft.start.no_fuel");
        if (getMixture() < 8.5f || getMixture() > 17.5f || getTiming() > 28 || plugCondition() <= 0) {
            return Component.translatable("message.summercraft.start.badly_tuned");
        }
        return null;
    }

    private void tickCranking() {
        if (!isCranking()) return;
        if (getDriver() == null) {
            entityData.set(DATA_CRANKING, false);
            return;
        }
        crankTicks++;
        Component failure = fireFailure();
        if (failure == null && crankTicks >= crankNeeded) {
            entityData.set(DATA_CRANKING, false);
            setRunning(true);
            playSound(ModRegistry.BACKFIRE.get(), 0.6f, 1.3f);
        } else if (failure != null && crankTicks == 50) {
            notifyDriver(failure);
        }
    }

    private void stopEngine(@Nullable Component reason) {
        setRunning(false);
        if (reason != null) notifyDriver(reason);
    }

    private float leakRate(PartSlot slot) {
        InstalledPart part = parts.get(slot);
        return part == null ? 0 : part.looseness() * 0.05f;
    }

    private void tickEngine(double movedPerTick) {
        float temperature = getTemperature();
        if (isRunning()) {
            if (fireFailure() != null) {
                Component failure = fireFailure();
                stopEngine(condition(PISTONS) <= 0 ? Component.translatable("message.summercraft.stall.seized")
                        : getFuel() <= 0 ? Component.translatable("message.summercraft.stall.fuel")
                        : failure);
            }
        }
        if (isRunning()) {
            EngineType type = engineType();
            float rpm = entityData.get(DATA_RPM);
            float load = rpm / (float) type.redline;
            float boost = entityData.get(DATA_BOOST);
            float mixture = getMixture(), timing = getTiming();

            setFuel(getFuel() - (0.002f + load * 0.012f) * (14.7f / mixture) * type.thirst * (1 + boost) - leakRate(FUEL_TANK));
            setOil(getOil() - 0.001f - leakRate(OIL_PAN));

            float heat = 82 + load * 25 + boost * 25 + Math.max(0, mixture - 14.5f) * 12 + Math.max(0, 8 - timing) * 3;
            boolean cooled = has(RADIATOR) && getCoolant() > 0;
            float target = (cooled ? heat : heat + 70) - (float) Math.min(10, movedPerTick * 15);
            temperature += (target - temperature) * 0.004f;
            setCoolant(getCoolant() - leakRate(RADIATOR) - (temperature > 105 ? 0.02f : 0));

            // Knock: too much ignition advance, and boost lowers how much advance the engine will take.
            float knockLimit = 20 - boost * 12;
            wear(PISTONS, (getOil() <= 0 ? 0.1f : 0) + (temperature > 120 ? 0.05f : 0)
                    + Math.max(0, timing - knockLimit) * 0.002f * load * (1 + boost * 3));
            float plugWear = 0.0005f * (0.3f + load) + Math.max(0, 11 - mixture) * 0.0008f;
            wear(SPARK_PLUGS, plugWear);
            wear(SPARK_PLUGS_B, plugWear);

            // Rough running: worn plugs or a rich mixture now and then bang in the exhaust.
            if ((plugCondition() < 35 || mixture < 10.5f) && random.nextInt(120) == 0) {
                playSound(ModRegistry.BACKFIRE.get(), 1f, 0.9f + random.nextFloat() * 0.3f);
            }
        } else {
            temperature += (AMBIENT_TEMP - temperature) * 0.002f;
        }
        setTemperature(temperature);

        // Wear changes slowly; resync now and then rather than every tick.
        if (tickCount % 40 == 0) {
            float total = 0;
            for (InstalledPart part : parts.values()) total += part.condition;
            if (Math.abs(total - lastPartsSyncCondition) > 0.05f) {
                lastPartsSyncCondition = total;
                syncParts();
            }
        }
    }

    private void tickLooseParts(double moved) {
        if (moved < 0.15) return;
        for (PartSlot slot : PartSlot.values()) {
            InstalledPart part = parts.get(slot);
            if (part == null || part.isSecure()) continue;
            if (random.nextFloat() < 0.003f * part.looseness() * (float) moved) {
                detach(slot);
                syncParts();
                playSound(SoundEvents.CHAIN_BREAK, 1f, 0.7f);
                notifyDriver(Component.translatable("message.summercraft.fell_off", slot.displayName()));
            }
        }
    }

    /** Applies what the driver's client reports: engine state for others to hear, and work done for wear. */
    public void receiveDriveState(DriveStatePayload state) {
        if (state.gear() != entityData.get(DATA_GEAR)) {
            level().playSound(getDriver(), getX(), getY(), getZ(), ModRegistry.GEAR_SHIFT.get(), getSoundSource(), 0.5f, 1f);
        }
        entityData.set(DATA_RPM, Mth.clamp(state.rpm(), 0, 8000));
        entityData.set(DATA_GEAR, Mth.clamp(state.gear(), CarPhysics.REVERSE, CarPhysics.TOP_GEAR));
        entityData.set(DATA_SKID, Mth.clamp(state.skid(), 0, 1));
        entityData.set(DATA_BOOST, Mth.clamp(state.boost(), 0, 2));
        applyWear(state.tyreWork(), state.clutchWork(), state.brakeWork());
        if (state.stalled() && isRunning()) stopEngine(Component.translatable("message.summercraft.stall.stalled"));
        if (state.impact() > 7) crash(state.impact(), state.impactFront());
    }

    public void setHorn(boolean on) {
        entityData.set(DATA_HORN, on && has(BATTERY));
    }

    private void applyWear(float tyreWork, float clutchWork, float brakeWork) {
        CarSetup setup = getSetup();
        float tyre = Math.min(tyreWork, 1e6f) * 1e-5f * (float) setup.tyres().wear
                * (1 + Math.abs(setup.toeFront()) + Math.abs(setup.toeRear()));
        for (PartSlot wheel : WHEELS) wear(wheel, tyre);
        wear(CLUTCH, Math.min(clutchWork, 1e6f) * 2e-5f);
        float brake = Math.min(brakeWork, 1e7f) * 2e-6f;
        wear(BRAKES_F, brake * 0.6f);
        wear(BRAKES_R, brake * 0.4f);
    }

    /** A hard hit shakes bolts loose and can knock panels clean off. */
    private void crash(float impact, boolean front) {
        playSound(ModRegistry.CRASH.get(), Math.min(1.5f, impact / 10), 0.8f + random.nextFloat() * 0.3f);
        int loosen = (int) (impact / 3);
        for (PartSlot slot : front ? CRUMPLE_FRONT : CRUMPLE_REAR) {
            InstalledPart part = parts.get(slot);
            if (part == null) continue;
            for (int i = 0; i < part.bolts.length; i++) {
                part.bolts[i] = Math.max(0, part.bolts[i] - random.nextInt(loosen + 1));
            }
            if (impact > 14 && random.nextInt(3) == 0) {
                detach(slot);
                notifyDriver(Component.translatable("message.summercraft.fell_off", slot.displayName()));
            }
        }
        if (front && has(RADIATOR) && impact > 10) setCoolant(getCoolant() * 0.5f);
        syncParts();
    }

    // ================================================================ hitbox and terrain

    private static final double PART_SIZE = 0.84;
    private static final double[] PART_X = {-0.42, 0.42};
    private static final double[] PART_Z = {-1.56, -0.78, 0, 0.78, 1.56};
    private static final Vec3[] WHEEL_CONTACTS = {
            new Vec3(0.7, 0, 1.15), new Vec3(-0.7, 0, 1.15), new Vec3(0.7, 0, -1.15), new Vec3(-0.7, 0, -1.15)};

    @Nullable
    private CarPart[] hitboxParts;
    /** Tilt from the ground under the wheels, radians: nose up, and left side up. */
    public float terrainPitch, terrainRoll, terrainPitchO, terrainRollO;

    private CarPart[] hitboxParts() {
        if (hitboxParts == null) {
            hitboxParts = new CarPart[PART_X.length * PART_Z.length];
            int i = 0;
            for (double z : PART_Z) {
                for (double x : PART_X) hitboxParts[i++] = new CarPart(this, new Vec3(x, 0, z), (float) PART_SIZE, 1.5f);
            }
        }
        return hitboxParts;
    }

    @Override
    public boolean isMultipartEntity() {
        return !isStand();
    }

    @Override
    public net.neoforged.neoforge.entity.PartEntity<?>[] getParts() {
        return isStand() ? null : hitboxParts();
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        // Parts take the ids right after the car, so client and server agree on them.
        if (!isStand()) {
            CarPart[] parts = hitboxParts();
            for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
        }
    }

    /** Keeps each hitbox part where it belongs on the car. */
    private void updateParts() {
        if (isStand()) return;
        for (CarPart part : hitboxParts()) {
            Vec3 at = localToWorld(part.offset);
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.getX();
            part.yOld = part.getY();
            part.zOld = part.getZ();
            part.setPos(at.x, at.y + getSetup().rideHeight() * 0.01, at.z);
        }
    }

    /** The footprint boxes at a position and heading, from just above step height up to the roof. */
    private List<AABB> footprint(Vec3 pos, float yaw) {
        List<AABB> boxes = new java.util.ArrayList<>(PART_X.length * PART_Z.length);
        double half = PART_SIZE / 2, bottom = pos.y + maxUpStep() + 0.01, top = pos.y + 1.45;
        float rad = -yaw * Mth.DEG_TO_RAD;
        for (double z : PART_Z) {
            for (double x : PART_X) {
                Vec3 c = pos.add(new Vec3(x, 0, z).yRot(rad));
                boxes.add(new AABB(c.x - half, bottom, c.z - half, c.x + half, top, c.z + half));
            }
        }
        return boxes;
    }

    /** Other things the car can bump into: other cars and anything else solid, but not itself or its passengers. */
    private List<net.minecraft.world.phys.shapes.VoxelShape> nearbyShapes(AABB area) {
        List<net.minecraft.world.phys.shapes.VoxelShape> shapes = new java.util.ArrayList<>();
        for (Entity other : level().getEntities(this, area, e -> e.canBeCollidedWith()
                && !(e instanceof CarPart part && part.getParent() == this) && !e.isPassengerOfSameVehicle(this))) {
            shapes.add(net.minecraft.world.phys.shapes.Shapes.create(other.getBoundingBox()));
        }
        return shapes;
    }

    private boolean footprintClear(Vec3 pos, float yaw) {
        for (AABB box : footprint(pos, yaw)) {
            if (level().getBlockCollisions(this, box).iterator().hasNext()) return false;
            for (var shape : nearbyShapes(box)) if (shape.bounds().intersects(box)) return false;
        }
        return true;
    }

    /** How far the footprint can actually move by {@code delta}: the most restrictive of all its boxes. */
    private Vec3 collideFootprint(Vec3 delta) {
        if (delta.lengthSqr() < 1e-10) return Vec3.ZERO;
        List<AABB> boxes = footprint(position(), getYRot());
        AABB area = boxes.get(0);
        for (AABB box : boxes) area = area.minmax(box);
        List<net.minecraft.world.phys.shapes.VoxelShape> shapes = nearbyShapes(area.expandTowards(delta).inflate(0.5));
        Vec3 allowed = new Vec3(delta.x, 0, delta.z);
        for (AABB box : boxes) allowed = Entity.collideBoundingBox(this, allowed, box, level(), shapes);
        return new Vec3(allowed.x, 0, allowed.z);
    }

    /** Ground heights under the four wheels at a position and heading: front left, front right, rear left, rear right. */
    private double[] wheelGround(Vec3 pos, float yaw) {
        double[] heights = new double[4];
        float rad = -yaw * Mth.DEG_TO_RAD;
        for (int i = 0; i < 4; i++) {
            Vec3 at = pos.add(WHEEL_CONTACTS[i].yRot(rad));
            heights[i] = CarTerrain.groundHeight(level(), at.x, at.z, pos.y + maxUpStep() + 0.05, pos.y - 4);
        }
        return heights;
    }

    /**
     * Puts the car down at {@code pos} resting on its wheels: its height is the average of the ground under the four
     * wheels, so a step or a run of slabs lifts one end first and the car tilts up it instead of jumping.
     * Returns how much the car rose.
     */
    private double settleOnGround(Vec3 pos, Vec3 horizontal) {
        double[] h = wheelGround(pos, getYRot());
        double front = (h[0] + h[1]) / 2, rear = (h[2] + h[3]) / 2;
        double target = (front + rear) / 2;
        double vy = getDeltaMovement().y;
        double y = pos.y;
        boolean grounded;
        if (target >= y - 0.05) {
            // Wheels on the ground: roll up onto whatever is under them, a little at a time.
            y = Math.min(target, y + 0.35);
            vy = 0;
            grounded = true;
        } else {
            vy = (vy - 0.08) * 0.98;
            y = Math.max(y + vy, target);
            grounded = y <= target + 1e-3;
            if (grounded) vy = 0;
        }
        double climbed = y - pos.y;
        setPos(pos.x, y, pos.z);
        setDeltaMovement(horizontal.x, vy, horizontal.z);
        setOnGround(grounded);
        if (grounded) tiltTo(h);
        physics.slope = dynoActive ? 0 : terrainPitch;
        return climbed;
    }

    /** Tilts the car to match the ground under its wheels, easing so it doesn't snap. */
    private void tiltTo(double[] h) {
        double front = (h[0] + h[1]) / 2, rear = (h[2] + h[3]) / 2;
        double left = (h[0] + h[2]) / 2, right = (h[1] + h[3]) / 2;
        float pitch = (float) Math.atan2(front - rear, 2.3);
        float roll = (float) Math.atan2(left - right, 1.4);
        terrainPitch += (pitch - terrainPitch) * 0.5f;
        terrainRoll += (roll - terrainRoll) * 0.5f;
    }

    // ================================================================ movement

    @Override
    public void tick() {
        terrainPitchO = terrainPitch;
        terrainRollO = terrainRoll;
        super.tick();
        tickLerp();
        boolean local = isControlledByLocalInstance();
        if (liftPos != null) {
            if (!level().isClientSide) tickLift();
        } else if (isStand()) {
            if (!level().isClientSide) {
                setDeltaMovement(0, (getDeltaMovement().y - 0.08) * 0.98, 0);
                move(MoverType.SELF, getDeltaMovement());
            }
        } else if (local) {
            if (!wasLocallyControlled) takeControl();
            driveTick();
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        wasLocallyControlled = local;
        updateParts();
        // Cars driven by someone else, parked or on a lift still sit on their wheels.
        if (!local && level().isClientSide && !isStand()) tiltTo(wheelGround(position(), getYRot()));

        if (!level().isClientSide) {
            double moved = lastServerPos == null ? 0 : position().subtract(lastServerPos).horizontalDistance();
            lastServerPos = position();
            if (local) {
                entityData.set(DATA_RPM, (float) physics.rpm());
                entityData.set(DATA_GEAR, physics.gear);
                entityData.set(DATA_BOOST, (float) physics.boost);
            }
            tickCranking();
            tickEngine(moved);
            tickLooseParts(moved);
        } else {
            tickVisuals(local);
            tickParticles();
            clientTick.accept(this);
        }
    }

    /** Picks the drivetrain up where the last controller left it. */
    private void takeControl() {
        physics.gear = entityData.get(DATA_GEAR);
        physics.engineSpeed = entityData.get(DATA_RPM) / (60 / (2 * Math.PI));
        Vec3 velocity = getDeltaMovement().scale(1 / CarPhysics.TICK);
        Vec3 forward = forward(), left = left();
        physics.vz = velocity.x * forward.x + velocity.z * forward.z;
        physics.vx = velocity.x * left.x + velocity.z * left.z;
        physics.rearWheelSpeed = physics.vz;
        physics.yawRate = 0;
    }

    private Vec3 forward() {
        return Vec3.directionFromRotation(0, getYRot());
    }

    private Vec3 left() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
    }

    private void driveTick() {
        CarPhysics.Input input;
        if (getDriver() != null) {
            input = level().isClientSide ? inputSource.apply(getDriver()) : CarPhysics.Input.NONE;
        } else {
            // Nobody inside: the handbrake is on.
            input = new CarPhysics.Input(0, 0, 0, true, false);
        }
        CarPhysics.Env env = physicsEnv();
        dynoActive = env.onDyno();
        // A fresh set of readings each time the car rolls onto the dyno.
        if (dynoActive && !wasOnDyno) physics.resetDyno();
        wasOnDyno = dynoActive;
        physics.step(input, env, onGround());
        if (!level().isClientSide && physics.stalled && isRunning()) {
            stopEngine(null);
        }

        // Turn only if the body has room to swing round; otherwise it's pressed against something.
        float newYaw = getYRot() - (float) Math.toDegrees(physics.yawRate * CarPhysics.TICK);
        if (newYaw != getYRot()) {
            if (footprintClear(position(), newYaw)) {
                setYRot(newYaw);
            } else {
                physics.yawRate *= -0.2;
            }
        }

        Vec3 forward = forward(), left = left();
        Vec3 planned = forward.scale(physics.vz).add(left.scale(physics.vx)).scale(CarPhysics.TICK);
        if (dynoActive) planned = Vec3.ZERO;

        Vec3 before = position();
        double speedBefore = physics.speed();
        Vec3 allowed = collideFootprint(planned);
        horizontalCollision = Math.abs(allowed.x - planned.x) > 1e-4 || Math.abs(allowed.z - planned.z) > 1e-4;
        double climbed = settleOnGround(before.add(allowed.x, 0, allowed.z), allowed);
        Vec3 moved = position().subtract(before);

        float impact = 0;
        boolean impactFront = physics.vz >= 0;
        if (horizontalCollision) {
            double vxWorld = moved.x / CarPhysics.TICK, vzWorld = moved.z / CarPhysics.TICK;
            physics.vz = vxWorld * forward.x + vzWorld * forward.z;
            physics.vx = vxWorld * left.x + vzWorld * left.z;
            physics.rearWheelSpeed = physics.vz;
            physics.yawRate *= 0.5;
            impact = (float) (speedBefore - physics.speed());
            if (impact > 3 && level().isClientSide) {
                level().playLocalSound(getX(), getY(), getZ(), ModRegistry.CRASH.get(), getSoundSource(), Math.min(1.5f, impact / 10), 1f, false);
            }
        }
        // Bumping up a big step costs some speed.
        if (climbed > 0.6) physics.vz *= 0.85;

        if (level().isClientSide && getDriver() != null) {
            PacketDistributor.sendToServer(new DriveStatePayload(getId(), (float) physics.rpm(), physics.gear, skidAmount(), (float) physics.boost, physics.stalled,
                    (float) physics.tyreWork, (float) physics.clutchWork, (float) physics.brakeWork, impact, impactFront));
        }
        physics.tyreWork = physics.clutchWork = physics.brakeWork = 0;
    }

    @Override
    protected AABB makeBoundingBox() {
        // The car's real shape is carried by its parts; its own box is just a core around the middle.
        if (!isStand()) return new AABB(getX() - 0.6, getY(), getZ() - 0.6, getX() + 0.6, getY() + getBbHeight(), getZ() + 0.6);
        // A stand has no parts: axis-aligned box around its rotated footprint.
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double cos = Math.abs(Mth.cos(yaw)), sin = Math.abs(Mth.sin(yaw));
        double width = isStand() ? 0.6 : HALF_WIDTH, length = isStand() ? 0.9 : HALF_LENGTH;
        double halfX = width * cos + length * sin;
        double halfZ = width * sin + length * cos;
        return new AABB(getX() - halfX, getY(), getZ() - halfZ, getX() + halfX, getY() + getBbHeight(), getZ() + halfZ);
    }

    private void tickVisuals(boolean local) {
        double forwardSpeed, latAccel, longAccel;
        if (local) {
            frontWheelAngle = (float) physics.frontWheelAngle;
            rearWheelAngle = (float) physics.rearWheelAngle;
            steerVisual = (float) physics.steer;
            latAccel = physics.latAccelSmoothed;
            longAccel = physics.longAccelSmoothed;
        } else {
            Vec3 moved = new Vec3(getX() - xo, 0, getZ() - zo);
            forwardSpeed = moved.dot(forward()) / CarPhysics.TICK;
            float yawRate = -Mth.wrapDegrees(getYRot() - yRotO) * Mth.DEG_TO_RAD / (float) CarPhysics.TICK;
            frontWheelAngle += (float) (forwardSpeed * CarPhysics.TICK / CarPhysics.WHEEL_RADIUS);
            rearWheelAngle = frontWheelAngle;
            float targetSteer = Math.abs(forwardSpeed) > 1 ? (float) Mth.clamp(yawRate * 2.3 / forwardSpeed, -0.6, 0.6) : steerVisual;
            steerVisual += (targetSteer - steerVisual) * 0.3f;
            latAccel = yawRate * forwardSpeed;
            longAccel = 0;
        }
        // Stiffer springs lean less; stiffer dampers settle faster.
        CarSetup setup = getSetup();
        float softness = (float) (1 / setup.springAverage());
        float settle = 0.15f + 0.1f * setup.dampers();
        bodyRoll += ((float) Mth.clamp(-latAccel * 0.006 * softness, -0.09, 0.09) - bodyRoll) * settle;
        bodyPitch += ((float) Mth.clamp(longAccel * 0.006 * softness, -0.06, 0.06) - bodyPitch) * settle;

        for (PartSlot slot : PartSlot.values()) {
            if (slot.hinge() == null) continue;
            float current = openAmount.getOrDefault(slot, 0f);
            float target = isOpen(slot) ? 1f : 0f;
            openAmount.put(slot, current + Mth.clamp(target - current, -0.12f, 0.12f));
        }
    }

    /** 0 closed to 1 open, animated. */
    public float openAmount(PartSlot slot) {
        return openAmount.getOrDefault(slot, 0f);
    }

    private void tickParticles() {
        if (isRunning() || isCranking()) {
            Vec3 pipe = has(EXHAUST) ? localToWorld(new Vec3(-0.42, 0.16, -2.0)) : localToWorld(new Vec3(-0.3, 0.5, 0.9));
            float mixture = getMixture();
            if (mixture < 11f && random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.02, 0);
            } else if (random.nextInt(getRpm() > 3000 ? 2 : 5) == 0) {
                level().addParticle(ParticleTypes.SMOKE, pipe.x, pipe.y, pipe.z, 0, 0.01, 0);
            }
        }
        if (getTemperature() > 110 && random.nextInt(3) == 0) {
            Vec3 bay = localToWorld(new Vec3((random.nextDouble() - 0.5) * 1.2, 0.9, 1.0 + random.nextDouble() * 0.7));
            level().addParticle(ParticleTypes.CLOUD, bay.x, bay.y, bay.z, 0, 0.06, 0);
        }
        float skid = getSkid();
        if (skid > 0.3f) {
            for (PartSlot wheel : List.of(WHEEL_RL, WHEEL_RR)) {
                if (!has(wheel) || random.nextFloat() > skid) continue;
                Vec3 at = localToWorld(wheel.fixedGeometry().bounds().getCenter().multiply(1, 0, 1));
                level().addParticle(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 0, 0.02, 0);
            }
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = 3;
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    public float maxUpStep() {
        return 1.0f - Math.max(0, -getSetup().rideHeight()) * 0.04f;
    }

    @Override
    public boolean isPickable() {
        // Cars are picked through their parts.
        return !isRemoved() && isStand();
    }

    @Override
    public boolean canBeCollidedWith() {
        return isStand();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.0);
    }

    // ================================================================ saving

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readParts(tag.getCompound("Parts"));
        syncParts();
        setFuel(tag.getFloat("Fuel"));
        setOil(tag.getFloat("Oil"));
        setCoolant(tag.getFloat("Coolant"));
        setTemperature(tag.contains("Temperature") ? tag.getFloat("Temperature") : AMBIENT_TEMP);
        setRunning(tag.getBoolean("Running"));
        entityData.set(DATA_GEAR, tag.getInt("Gear"));
        if (tag.contains("Color")) entityData.set(DATA_COLOR, tag.getInt("Color"));
        entityData.set(DATA_SETUP, CarSetup.load(tag.getCompound("Setup")).save());
        if (tag.contains("Lift")) {
            liftPos = BlockPos.of(tag.getLong("Lift"));
            liftTarget = tag.getFloat("LiftTarget");
            entityData.set(DATA_LIFT, tag.getFloat("LiftHeight"));
        }
        physics.gear = tag.getInt("Gear");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Parts", writeParts());
        tag.putFloat("Fuel", getFuel());
        tag.putFloat("Oil", getOil());
        tag.putFloat("Coolant", getCoolant());
        tag.putFloat("Temperature", getTemperature());
        tag.putBoolean("Running", isRunning());
        tag.putInt("Gear", entityData.get(DATA_GEAR));
        tag.putInt("Color", getColor());
        tag.put("Setup", getSetup().save());
        if (liftPos != null) {
            tag.putLong("Lift", liftPos.asLong());
            tag.putFloat("LiftTarget", liftTarget);
            tag.putFloat("LiftHeight", entityData.get(DATA_LIFT));
        }
    }
}
