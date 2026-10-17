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

public record RadiationSyncPacket(float rate, float dose) {
    public static void encode(RadiationSyncPacket m, FriendlyByteBuf b) {
        b.writeFloat(m.rate);
        b.writeFloat(m.dose);
    }

    public static RadiationSyncPacket decode(FriendlyByteBuf b) {
        return new RadiationSyncPacket(b.readFloat(), b.readFloat());
    }

    public static void handle(RadiationSyncPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPackets.radiation(m)));
        ctx.get().setPacketHandled(true);
    }
}

