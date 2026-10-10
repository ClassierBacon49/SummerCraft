package com.summercraft.item;

import com.summercraft.car.EngineType;
import com.summercraft.car.PartSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A car part. Right-click a car (or an engine stand) with it to mount it, then bolt it down with a wrench. */
public class PartItem extends Item {
    public PartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        EngineType engine = EngineType.byBlock(this);
        if (engine != null) {
            lines.add(Component.translatable("tooltip.summercraft.engine_spec", engine.cylinders, Math.round(engine.peakTorque),
                    Math.round(engine.peakRpm), Math.round(engine.redline)).withStyle(ChatFormatting.AQUA));
        }
        List<PartSlot> slots = PartSlot.forItem(this);
        if (slots.isEmpty()) return;
        PartSlot slot = slots.get(0);
        if (slot.isEngine()) {
            lines.add(Component.translatable("tooltip.summercraft.bolt_size", slot.boltSize()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.summercraft.bolts", slot.fixedGeometry().boltCount(), slot.boltSize()).withStyle(ChatFormatting.GRAY));
        }
        if (slot.parent() != null) {
            lines.add(Component.translatable("tooltip.summercraft.mounts_on", slot.parent().displayName()).withStyle(ChatFormatting.GRAY));
        }
        if (slot == PartSlot.TURBO || slot == PartSlot.SUPERCHARGER) {
            lines.add(Component.translatable("tooltip.summercraft." + slot.id() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
