/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.fml.DistExecutor
 *  net.minecraftforge.network.NetworkEvent$Context
 */
package ru.aiforge.realwar.radar;

import ru.aiforge.realwar.client.sight.RadarClient;
import ru.aiforge.realwar.radar.RadarNet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record RadarReplyPacket(int vehId, boolean linked, boolean self, List<float[]> sources, List<RadarNet.Contact> contacts) {
    public static void encode(RadarReplyPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.vehId).writeBoolean(m.linked).writeBoolean(m.self);
        b.writeVarInt(m.sources.size());
        for (float[] s : m.sources) {
            b.writeFloat(s[0]).writeFloat(s[1]).writeFloat(s[2]);
        }
        b.writeVarInt(m.contacts.size());
        for (RadarNet.Contact c : m.contacts) {
            b.writeVarInt(c.id()).writeByte((int)c.type());
            b.writeDouble(c.x()).writeFloat((float)c.y()).writeDouble(c.z());
            b.writeFloat(c.vx()).writeFloat(c.vy()).writeFloat(c.vz());
        }
    }

    public static RadarReplyPacket decode(FriendlyByteBuf b) {
        int veh = b.readVarInt();
        boolean linked = b.readBoolean();
        boolean self = b.readBoolean();
        int n = b.readVarInt();
        ArrayList<float[]> src = new ArrayList<float[]>();
        for (int i = 0; i < n; ++i) {
            src.add(new float[]{b.readFloat(), b.readFloat(), b.readFloat()});
        }
        n = b.readVarInt();
        ArrayList<RadarNet.Contact> cs = new ArrayList<RadarNet.Contact>();
        for (int i = 0; i < n; ++i) {
            cs.add(new RadarNet.Contact(b.readVarInt(), b.readByte(), b.readDouble(), b.readFloat(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat()));
        }
        return new RadarReplyPacket(veh, linked, self, src, cs);
    }

    public static void handle(RadarReplyPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> RadarClient.accept(m)));
        ctx.get().setPacketHandled(true);
    }
}

