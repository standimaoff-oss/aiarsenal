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

public record NukeEffectPacket(double x, double y, double z, float yieldKt, boolean surface, long seed, float metersPerBlock, int maxR, int groundY, int craterR) {
    public static void encode(NukeEffectPacket m, FriendlyByteBuf b) {
        b.writeDouble(m.x);
        b.writeDouble(m.y);
        b.writeDouble(m.z);
        b.writeFloat(m.yieldKt);
        b.writeBoolean(m.surface);
        b.writeLong(m.seed);
        b.writeFloat(m.metersPerBlock);
        b.writeVarInt(m.maxR);
        b.writeVarInt(m.groundY);
        b.writeVarInt(m.craterR);
    }

    public static NukeEffectPacket decode(FriendlyByteBuf b) {
        return new NukeEffectPacket(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readBoolean(), b.readLong(), b.readFloat(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(NukeEffectPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPackets.nuke(m)));
        ctx.get().setPacketHandled(true);
    }
}

