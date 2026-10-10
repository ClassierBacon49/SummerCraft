package com.summercraft.block;

import com.summercraft.car.CarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.function.BiConsumer;

/**
 * A two-post lift. Park a car over it and use the lift to open the setup screen, where it can raise the car and
 * change tyres, alignment, suspension, exhaust and drivetrain.
 */
public class CarLiftBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    /** Opens the setup screen on the client; set by the client mod. */
    public static BiConsumer<CarEntity, BlockPos> openSetup = (car, pos) -> {};

    public CarLiftBlock(Properties properties) {
        super(properties);
    }

    /** The car parked on (or raised by) the lift at {@code pos}. */
    @Nullable
    public static CarEntity carOn(Level level, BlockPos pos) {
        AABB area = new AABB(pos).inflate(1.2, 0, 1.2).expandTowards(0, CarEntity.LIFT_HEIGHT + 1, 0);
        return level.getEntitiesOfClass(CarEntity.class, area, car -> !car.isStand()).stream()
                .min(Comparator.comparingDouble(car -> car.position().distanceToSqr(pos.getCenter())))
                .orElse(null);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CarEntity car = carOn(level, pos);
        if (car == null) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.summercraft.no_car_on_lift"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) openSetup.accept(car, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
