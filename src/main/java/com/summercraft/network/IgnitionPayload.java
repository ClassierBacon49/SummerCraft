package com.summercraft.network;

import com.summercraft.SummerCraft;
import com.summercraft.car.CarEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The driver turning the key ({@code turning} = true) or letting go of it. */
public record IgnitionPayload(boolean turning) implements CustomPacketPayload {
    public static final Type<IgnitionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "ignition"));
    public static final StreamCodec<ByteBuf, IgnitionPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(IgnitionPayload::new, IgnitionPayload::turning);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IgnitionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof CarEntity car && car.getDriver() == context.player()) {
                car.setIgnition(context.player(), payload.turning());
            }
        });
    }
}
