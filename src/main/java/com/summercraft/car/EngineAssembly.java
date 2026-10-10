package com.summercraft.car;

import com.summercraft.ModRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/**
 * A whole engine lifted out as one item: the engine block and everything bolted to it, with each part's
 * bolts, wear and tuning kept, plus the oil in its sump. Stored in the item's custom data.
 */
public final class EngineAssembly {
    public static ItemStack create(EngineType type, CompoundTag parts, float oil) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Type", type.ordinal());
        tag.put("Parts", parts);
        tag.putFloat("Oil", oil);
        ItemStack stack = new ItemStack(ModRegistry.ENGINE_ASSEMBLY.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static CompoundTag contents(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    @Nullable
    public static EngineType type(ItemStack stack) {
        CompoundTag tag = contents(stack);
        return tag.contains("Type") ? EngineType.byId(tag.getInt("Type")) : null;
    }

    /** True if the engine has every part it needs to run, ignoring boost. */
    public static boolean isComplete(ItemStack stack) {
        EngineType type = type(stack);
        if (type == null) return false;
        CompoundTag parts = contents(stack).getCompound("Parts");
        for (PartSlot slot : PartSlot.values()) {
            if (!slot.isPowertrain() || slot == PartSlot.TURBO || slot == PartSlot.SUPERCHARGER) continue;
            if (slot.isEngine() && EngineLayout.get(type, slot) == null) continue;
            if (!parts.contains(slot.id())) return false;
        }
        return true;
    }

    public static boolean has(ItemStack stack, PartSlot slot) {
        return contents(stack).getCompound("Parts").contains(slot.id());
    }

    private EngineAssembly() {}
}
