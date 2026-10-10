package com.summercraft.car;

import com.summercraft.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The engine families a block can be. Everything else about an engine (where its parts sit, how it sounds)
 * hangs off this: see {@link EngineLayout} for geometry.
 */
public enum EngineType {
    INLINE_4(ModRegistry.ENGINE_BLOCK, 4, 1, 110, 3800, 6500, 1.0f),
    BOXER_4(ModRegistry.BOXER_ENGINE_BLOCK, 4, 2, 150, 4200, 7000, 1.15f),
    V6(ModRegistry.V6_ENGINE_BLOCK, 6, 2, 230, 4000, 6500, 1.5f),
    V8(ModRegistry.V8_ENGINE_BLOCK, 8, 2, 340, 3800, 6200, 2.0f);

    private final Supplier<? extends Item> block;
    public final int cylinders;
    /** 1 for an inline engine, 2 for a boxer or a V: two heads, two sets of plugs. */
    public final int banks;
    public final double peakTorque, peakRpm, redline;
    /** Fuel burnt relative to the inline-4. */
    public final float thirst;

    EngineType(Supplier<? extends Item> block, int cylinders, int banks, double peakTorque, double peakRpm, double redline, float thirst) {
        this.block = block;
        this.cylinders = cylinders;
        this.banks = banks;
        this.peakTorque = peakTorque;
        this.peakRpm = peakRpm;
        this.redline = redline;
        this.thirst = thirst;
    }

    public Item block() {
        return block.get();
    }

    public int plugsPerBank() {
        return cylinders / banks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("engine.summercraft." + id());
    }

    public static EngineType byBlock(Item item) {
        for (EngineType type : values()) if (type.block() == item) return type;
        return null;
    }

    public static EngineType byId(int ordinal) {
        return values()[Math.floorMod(ordinal, values().length)];
    }
}
