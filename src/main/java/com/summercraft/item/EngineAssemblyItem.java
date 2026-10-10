package com.summercraft.item;

import com.summercraft.car.EngineAssembly;
import com.summercraft.car.EngineType;
import com.summercraft.car.PartSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A complete (or partly built) engine on a hoist. Right-click an empty engine bay or engine stand to set it down. */
public class EngineAssemblyItem extends Item {
    public EngineAssemblyItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        EngineType type = EngineAssembly.type(stack);
        return type == null ? super.getName(stack) : Component.translatable("item.summercraft.engine_assembly.typed", type.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(EngineAssembly.isComplete(stack) ? "tooltip.summercraft.engine_complete" : "tooltip.summercraft.engine_incomplete")
                .withStyle(EngineAssembly.isComplete(stack) ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        if (EngineAssembly.has(stack, PartSlot.TURBO)) lines.add(Component.translatable("tooltip.summercraft.turbo").withStyle(ChatFormatting.AQUA));
        if (EngineAssembly.has(stack, PartSlot.SUPERCHARGER)) lines.add(Component.translatable("tooltip.summercraft.supercharger").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.summercraft.engine_assembly.desc").withStyle(ChatFormatting.GRAY));
    }
}
