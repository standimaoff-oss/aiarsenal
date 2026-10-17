/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.fml.DistExecutor
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.client.ClientPackets;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record TracerPacket(double x, double y, double z, double vx, double vy, double vz, int life) {
    public TracerPacket(Vec3 p, Vec3 v, int life) {
        this(p.x, p.y, p.z, v.x, v.y, v.z, life);
    }

    public static void encode(TracerPacket m, FriendlyByteBuf b) {
        b.writeDouble(m.x).writeDouble(m.y).writeDouble(m.z);
        b.writeFloat((float)m.vx).writeFloat((float)m.vy).writeFloat((float)m.vz);
        b.writeVarInt(m.life);
    }

    public static TracerPacket decode(FriendlyByteBuf b) {
        return new TracerPacket(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readVarInt());
    }

    public static void handle(TracerPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPackets.tracer(m)));
        ctx.get().setPacketHandled(true);
    }
}

