/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.fml.DistExecutor
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.network;

import ru.aiforge.realwar.client.ClientPackets;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record ConventionalEffectPacket(double x, double y, double z, float massKg, boolean surface, long seed) {
    public static void encode(ConventionalEffectPacket m, FriendlyByteBuf b) {
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeFloat(m.massKg);
        b.writeBoolean(m.surface);
        b.writeLong(m.seed);
    }

    public static ConventionalEffectPacket decode(FriendlyByteBuf b) {
        return new ConventionalEffectPacket(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean(), b.readLong());
    }

    public static void handle(ConventionalEffectPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPackets.conventional(m)));
        ctx.get().setPacketHandled(true);
    }
}

