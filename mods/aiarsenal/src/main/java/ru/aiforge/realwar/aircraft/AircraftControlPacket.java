/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AircraftEntity;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public record AircraftControlPacket(byte thr, byte roll, byte pitch, byte rudder, byte flags, byte actions, float lx, float lz) {
    public static final int FIRE = 1;
    public static final int BRAKE = 2;

    public static AircraftControlPacket of(float thr, float roll, float pitch, float rudder, boolean fire, boolean brake, int actions, float lx, float lz) {
        return new AircraftControlPacket((byte)Math.round(thr * 100.0f), (byte)Math.round(roll * 100.0f), (byte)Math.round(pitch * 100.0f), (byte)Math.round(rudder * 100.0f), (byte)((fire ? 1 : 0) | (brake ? 2 : 0)), (byte)actions, lx, lz);
    }

    public static void encode(AircraftControlPacket m, FriendlyByteBuf b) {
        b.writeByte((int)m.thr).writeByte((int)m.roll).writeByte((int)m.pitch).writeByte((int)m.rudder).writeByte((int)m.flags).writeByte((int)m.actions).writeFloat(m.lx).writeFloat(m.lz);
    }

    public static AircraftControlPacket decode(FriendlyByteBuf b) {
        return new AircraftControlPacket(b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readByte(), b.readFloat(), b.readFloat());
    }

    public static void handle(AircraftControlPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Entity patt1639$temp;
            ServerPlayer p = ((NetworkEvent.Context)ctx.get()).getSender();
            if (p != null && (patt1639$temp = p.getVehicle()) instanceof AircraftEntity) {
                AircraftEntity a = (AircraftEntity)patt1639$temp;
                a.handleControl(p, (float)m.thr / 100.0f, (float)m.roll / 100.0f, (float)m.pitch / 100.0f, (float)m.rudder / 100.0f, (m.flags & 2) != 0, (m.flags & 1) != 0, m.actions & 0xFF, m.lx, m.lz);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}

