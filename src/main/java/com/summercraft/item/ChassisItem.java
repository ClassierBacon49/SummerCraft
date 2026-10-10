package com.summercraft.item;

import com.summercraft.ModRegistry;
import com.summercraft.car.CarEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Places a bare car frame where you click, facing the way you're looking. */
public class ChassisItem extends DescribedItem {
    public ChassisItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            CarEntity car = ModRegistry.CAR.get().create(level);
            if (car == null) return InteractionResult.FAIL;
            Vec3 pos = context.getClickLocation();
            car.moveTo(pos.x, pos.y, pos.z, context.getRotation(), 0);
            if (!level.noCollision(car, car.getBoundingBox())) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.summercraft.no_room"), true);
                }
                return InteractionResult.FAIL;
            }
            level.addFreshEntity(car);
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
