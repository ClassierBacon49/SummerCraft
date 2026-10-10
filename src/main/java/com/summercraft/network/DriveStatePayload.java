package com.summercraft.network;

import com.summercraft.SummerCraft;
import com.summercraft.car.CarEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent every tick by the driver's client, which runs the car's physics: what the engine is doing,
 * and how much work the tyres, clutch and brakes did, so the server can wear them down.
 */
public record DriveStatePayload(int carId, float rpm, int gear, float skid, float boost, boolean stalled,
                                float tyreWork, float clutchWork, float brakeWork,
                                float impact, boolean impactFront) implements CustomPacketPayload {
    public static final Type<DriveStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SummerCraft.MODID, "drive_state"));
    public static final StreamCodec<FriendlyByteBuf, DriveStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.carId);
                buf.writeFloat(p.rpm);
                buf.writeByte(p.gear);
                buf.writeFloat(p.skid);
                buf.writeFloat(p.boost);
                buf.writeBoolean(p.stalled);
                buf.writeFloat(p.tyreWork);
                buf.writeFloat(p.clutchWork);
                buf.writeFloat(p.brakeWork);
                buf.writeFloat(p.impact);
                buf.writeBoolean(p.impactFront);
            },
            buf -> new DriveStatePayload(buf.readVarInt(), buf.readFloat(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DriveStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().getVehicle() instanceof CarEntity car && car.getId() == payload.carId()
                    && car.getDriver() == context.player() && Float.isFinite(payload.rpm())) {
                car.receiveDriveState(payload);
            }
        });
    }
}
