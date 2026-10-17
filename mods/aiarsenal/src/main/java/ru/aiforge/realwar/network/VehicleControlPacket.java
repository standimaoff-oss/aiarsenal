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

public record VehicleControlPacket(byte throttle, byte steer, byte flags, byte actions) {
    public static final int FIRE = 1;
    public static final int RELOAD = 2;

    public static VehicleControlPacket of(float throttle, float steer, boolean fire, boolean reload, int actions) {
        return new VehicleControlPacket((byte)Math.round(throttle * 100.0f), (byte)Math.round(steer * 100.0f), (byte)((fire ? 1 : 0) | (reload ? 2 : 0)), (byte)actions);
    }

    public static void encode(VehicleControlPacket m, FriendlyByteBuf b) {
        b.writeByte((int)m.throttle).writeByte((int)m.steer).writeByte((int)m.flags).writeByte((int)m.actions);
    }

    public static VehicleControlPacket decode(FriendlyByteBuf b) {
        return new VehicleControlPacket(b.readByte(), b.readByte(), b.readByte(), b.readByte());
    }

    public static void handle(VehicleControlPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Entity patt1497$temp;
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p != null && (patt1497$temp = p.getVehicle()) instanceof VehicleEntity) {
                VehicleEntity v = (VehicleEntity)patt1497$temp;
                v.handleControl(p, (float)m.throttle / 100.0f, (float)m.steer / 100.0f, (m.flags & 1) != 0, (m.flags & 2) != 0, m.actions);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

