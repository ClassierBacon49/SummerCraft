package com.summercraft.item;

import com.summercraft.ModRegistry;
import com.summercraft.car.PartSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A socket wrench. Use it in the air to pick a bigger socket, sneak-use for a smaller one. */
public class WrenchItem extends DescribedItem {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    public static int size(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.WRENCH_SIZE, 10);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int[] sizes = PartSlot.WRENCH_SIZES;
            int index = 0;
            for (int i = 0; i < sizes.length; i++) if (sizes[i] == size(stack)) index = i;
            index = Math.floorMod(index + (player.isShiftKeyDown() ? -1 : 1), sizes.length);
            stack.set(ModRegistry.WRENCH_SIZE, sizes[index]);
            player.displayClientMessage(Component.translatable("message.summercraft.wrench_size", sizes[index]), true);
            level.playSound(null, player.blockPosition(), ModRegistry.RATCHET.get(), SoundSource.PLAYERS, 0.4f, 1.6f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.summercraft.wrench.sized", size(stack));
    }
}
