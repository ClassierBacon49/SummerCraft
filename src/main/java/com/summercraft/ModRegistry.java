package com.summercraft;

import com.mojang.serialization.Codec;
import com.summercraft.car.CarEntity;
import com.summercraft.item.ChassisItem;
import com.summercraft.item.DescribedItem;
import com.summercraft.item.PartItem;
import com.summercraft.item.WrenchItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import com.summercraft.block.CarLiftBlock;
import com.summercraft.block.DynoBlock;
import com.summercraft.item.EngineAssemblyItem;
import com.summercraft.item.EngineStandItem;
import java.util.List;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SummerCraft.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SummerCraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, SummerCraft.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SummerCraft.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, SummerCraft.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, SummerCraft.MODID);

    /** Socket size a wrench is set to, in mm. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WRENCH_SIZE = COMPONENTS.register("wrench_size",
            () -> DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());

    // Tools and consumables
    public static final DeferredItem<Item> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> SCREWDRIVER = ITEMS.registerItem("screwdriver", DescribedItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> CAR_CHASSIS = ITEMS.registerItem("car_chassis", ChassisItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> MOTOR_OIL = ITEMS.registerItem("motor_oil", DescribedItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> JERRY_CAN = ITEMS.registerItem("jerry_can", DescribedItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> ENGINE_STAND_ITEM = ITEMS.registerItem("engine_stand", EngineStandItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> ENGINE_ASSEMBLY = ITEMS.registerItem("engine_assembly", EngineAssemblyItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredBlock<DynoBlock> DYNO = BLOCKS.register("dyno", () -> new DynoBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> DYNO_ITEM = ITEMS.registerSimpleBlockItem("dyno", DYNO);
    public static final DeferredBlock<CarLiftBlock> CAR_LIFT = BLOCKS.register("car_lift", () -> new CarLiftBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(3f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> CAR_LIFT_ITEM = ITEMS.registerSimpleBlockItem("car_lift", CAR_LIFT);

    // Engine. Parts that wear out use durability as their condition.
    public static final DeferredItem<Item> ENGINE_BLOCK = part("engine_block");
    public static final DeferredItem<Item> BOXER_ENGINE_BLOCK = part("boxer_engine_block");
    public static final DeferredItem<Item> V6_ENGINE_BLOCK = part("v6_engine_block");
    public static final DeferredItem<Item> V8_ENGINE_BLOCK = part("v8_engine_block");
    public static final DeferredItem<Item> CRANKSHAFT = part("crankshaft");
    public static final DeferredItem<Item> PISTONS = wearingPart("pistons");
    public static final DeferredItem<Item> CYLINDER_HEAD = part("cylinder_head");
    public static final DeferredItem<Item> SPARK_PLUGS = wearingPart("spark_plugs");
    public static final DeferredItem<Item> CARBURETOR = part("carburetor");
    public static final DeferredItem<Item> DISTRIBUTOR = part("distributor");
    public static final DeferredItem<Item> OIL_PAN = part("oil_pan");
    public static final DeferredItem<Item> CLUTCH = wearingPart("clutch");
    public static final DeferredItem<Item> GEARBOX = part("gearbox");
    public static final DeferredItem<Item> STARTER_MOTOR = part("starter_motor");
    public static final DeferredItem<Item> RADIATOR = part("radiator");
    public static final DeferredItem<Item> BATTERY = part("battery");
    public static final DeferredItem<Item> EXHAUST = part("exhaust");
    public static final DeferredItem<Item> TURBO = part("turbo");
    public static final DeferredItem<Item> SUPERCHARGER = part("supercharger");

    // Running gear and interior
    public static final DeferredItem<Item> FUEL_TANK = part("fuel_tank");
    public static final DeferredItem<Item> BRAKE_PADS = wearingPart("brake_pads");
    public static final DeferredItem<Item> WHEEL = wearingPart("wheel");
    public static final DeferredItem<Item> CAR_SEAT = part("car_seat");
    public static final DeferredItem<Item> STEERING_WHEEL = part("steering_wheel");

    // Body panels
    public static final DeferredItem<Item> HOOD = part("hood");
    public static final DeferredItem<Item> TRUNK_LID = part("trunk_lid");
    public static final DeferredItem<Item> DOOR = part("door");
    public static final DeferredItem<Item> FENDER = part("fender");
    public static final DeferredItem<Item> BUMPER = part("bumper");

    public static final DeferredHolder<EntityType<?>, EntityType<CarEntity>> CAR = ENTITY_TYPES.register("car",
            () -> EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC)
                    .sized(1.7f, 1.55f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("car"));
    /** Same entity class as the car, holding only an engine. */
    public static final DeferredHolder<EntityType<?>, EntityType<CarEntity>> ENGINE_STAND = ENTITY_TYPES.register("engine_stand",
            () -> EntityType.Builder.<CarEntity>of(CarEntity::new, MobCategory.MISC)
                    .sized(1.2f, 1.3f)
                    .clientTrackingRange(10)
                    .build("engine_stand"));

    /** Engine loops recorded at {@code ENGINE_LAYER_RPMS} for each engine type, keyed by {@code EngineType.id()}. */
    public static final java.util.Map<String, List<DeferredHolder<SoundEvent, SoundEvent>>> ENGINE_SOUNDS = new java.util.HashMap<>();
    /** Engine speed each engine loop was made at; must match tools/engine_synth.py. */
    public static final float[] ENGINE_LAYER_RPMS = {1000, 2200, 3800, 5800};

    static {
        for (String engine : List.of("inline_4", "boxer_4", "v6", "v8")) {
            ENGINE_SOUNDS.put(engine, List.of(sound("engine_" + engine + "_0"), sound("engine_" + engine + "_1"),
                    sound("engine_" + engine + "_2"), sound("engine_" + engine + "_3")));
        }
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBO_WHISTLE = sound("turbo_whistle");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERCHARGER_WHINE = sound("supercharger_whine");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLOW_OFF = sound("blow_off");
    public static final DeferredHolder<SoundEvent, SoundEvent> STARTER = sound("starter");
    public static final DeferredHolder<SoundEvent, SoundEvent> TYRE_SQUEAL = sound("tyre_squeal");
    public static final DeferredHolder<SoundEvent, SoundEvent> HORN = sound("horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> RATCHET = sound("ratchet");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_SHIFT = sound("gear_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_GRIND = sound("gear_grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKFIRE = sound("backfire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH = sound("crash");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.summercraft"))
            .icon(() -> new ItemStack(ENGINE_BLOCK.get()))
            .displayItems((params, output) -> ITEMS.getEntries().stream()
                    .filter(item -> item != ENGINE_ASSEMBLY)
                    .forEach(item -> output.accept(item.get())))
            .build());

    private static DeferredItem<Item> part(String name) {
        return ITEMS.registerItem(name, PartItem::new, new Item.Properties().stacksTo(1));
    }

    private static DeferredItem<Item> wearingPart(String name) {
        return ITEMS.registerItem(name, PartItem::new, new Item.Properties().durability(100));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, name)));
    }

    private ModRegistry() {}
}
