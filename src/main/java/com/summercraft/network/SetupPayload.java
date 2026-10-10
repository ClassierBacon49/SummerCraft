package com.summercraft.network;

import com.summercraft.SummerCraft;
import com.summercraft.ModRegistry;
import com.summercraft.car.CarEntity;
import com.summercraft.car.CarSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

/**
 * From the setup screen: raise or lower the lift ({@code action} 1 or 2), or apply a new setup ({@code action} 0).
 */
public record SetupPayload(int carId, BlockPos lift, int action, CompoundTag setup) implements CustomPacketPayload {
    public static final int APPLY = 0, RAISE = 1, LOWER = 2;
    public static final Type<SetupPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "setup"));
    public static final StreamCodec<FriendlyByteBuf, SetupPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.carId);
                buf.writeBlockPos(p.lift);
                buf.writeByte(p.action);
                buf.writeNbt(p.setup);
            },
            buf -> new SetupPayload(buf.readVarInt(), buf.readBlockPos(), buf.readByte(), orEmpty(buf.readNbt())));

    private static CompoundTag orEmpty(@Nullable CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.level().getEntity(payload.carId()) instanceof CarEntity car)) return;
            if (player.distanceToSqr(car) > 64 || !player.level().getBlockState(payload.lift()).is(ModRegistry.CAR_LIFT.get())) return;
            if (car.position().distanceToSqr(payload.lift().getCenter()) > 9) return;
            switch (payload.action()) {
                case RAISE -> car.setLift(payload.lift(), true);
                case LOWER -> car.setLift(payload.lift(), false);
                default -> car.applySetup(player, CarSetup.load(payload.setup()));
            }
        });
    }
}
