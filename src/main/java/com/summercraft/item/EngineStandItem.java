package com.summercraft.item;

import com.summercraft.ModRegistry;
import com.summercraft.car.CarEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Places an engine stand to build engines on, facing the player. */
public class EngineStandItem extends DescribedItem {
    public EngineStandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            CarEntity stand = ModRegistry.ENGINE_STAND.get().create(level);
            if (stand == null) return InteractionResult.FAIL;
            Vec3 pos = context.getClickLocation();
            stand.moveTo(pos.x, pos.y, pos.z, context.getRotation() + 90, 0);
            if (!level.noCollision(stand, stand.getBoundingBox())) return InteractionResult.FAIL;
            level.addFreshEntity(stand);
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
