/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.vehicle.VehicleEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public record VehicleStatePacket(int entityId, double x, double y, double z, float yaw, float speed, float steer) {
    public static void encode(VehicleStatePacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.entityId).writeDouble(m.x).writeDouble(m.y).writeDouble(m.z).writeFloat(m.yaw).writeFloat(m.speed).writeFloat(m.steer);
    }

    public static VehicleStatePacket decode(FriendlyByteBuf b) {
        return new VehicleStatePacket(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat());
    }

    public static void handle(VehicleStatePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            VehicleEntity v;
            Entity patt1392$temp;
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p != null && (patt1392$temp = p.getVehicle()) instanceof VehicleEntity && (v = (VehicleEntity)patt1392$temp).getId() == m.entityId) {
                v.acceptClientState(p, m.x, m.y, m.z, m.yaw, m.speed, m.steer);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

