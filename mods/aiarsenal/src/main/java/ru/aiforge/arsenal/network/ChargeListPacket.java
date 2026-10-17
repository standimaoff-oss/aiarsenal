package ru.aiforge.arsenal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: armed charges for the detonator screen. */
public record ChargeListPacket(List<Entry> entries, boolean open) {
    public record Entry(int id, int kind, int fuze, int status, int x, int y, int z, String dim) {
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(open);
        b.writeVarInt(entries.size());
        for (Entry e : entries) {
            b.writeVarInt(e.id);
            b.writeByte(e.kind);
            b.writeByte(e.fuze);
            b.writeByte(e.status);
            b.writeInt(e.x);
            b.writeInt(e.y);
            b.writeInt(e.z);
            b.writeUtf(e.dim, 128);
        }
    }

    public static ChargeListPacket decode(FriendlyByteBuf b) {
        boolean open = b.readBoolean();
        int n = Math.min(512, b.readVarInt());
        List<Entry> l = new ArrayList<>();
        for (int i = 0; i < n; i++)
            l.add(new Entry(b.readVarInt(), b.readByte(), b.readByte(), b.readByte(), b.readInt(), b.readInt(), b.readInt(), b.readUtf(128)));
        return new ChargeListPacket(l, open);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.arsenal.client.ArsClient.charges(this));
        ctx.get().setPacketHandled(true);
    }
}
