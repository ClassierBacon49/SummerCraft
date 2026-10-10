package com.summercraft.network;

import com.summercraft.SummerCraft;
import com.summercraft.car.CarEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The driver pressing or releasing the horn. */
public record HornPayload(boolean on) implements CustomPacketPayload {
    public static final Type<HornPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "horn"));
    public static final StreamCodec<ByteBuf, HornPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(HornPayload::new, HornPayload::on);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HornPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof CarEntity car && car.getDriver() == context.player()) {
                car.setHorn(payload.on());
            }
        });
    }
}
