package com.summercraft.car;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reading the ground under the wheels. */
public final class CarTerrain {
    /**
     * The height of the ground directly under (x, z): the top of the highest collision shape at that point,
     * searching down from {@code top} to {@code bottom}. Slabs, stairs, snow layers and paths all count at their
     * real height, which is what lets a run of slabs make a smooth ramp. Returns {@code bottom} if there's nothing.
     */
    public static double groundHeight(Level level, double x, double z, double top, double bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = Mth.floor(x), bz = Mth.floor(z);
        double lx = x - bx, lz = z - bz;
        for (int by = Mth.floor(top); by >= Mth.floor(bottom); by--) {
            pos.set(bx, by, bz);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double best = Double.NEGATIVE_INFINITY;
            for (AABB box : shape.toAabbs()) {
                if (lx >= box.minX - 1e-3 && lx <= box.maxX + 1e-3 && lz >= box.minZ - 1e-3 && lz <= box.maxZ + 1e-3) {
                    best = Math.max(best, box.maxY);
                }
            }
            if (best == Double.NEGATIVE_INFINITY) continue;
            double height = by + best;
            // Too tall to roll onto (an overhang above a wheel): keep looking further down.
            if (height <= top) return height;
        }
        return bottom;
    }

    private CarTerrain() {}
}
