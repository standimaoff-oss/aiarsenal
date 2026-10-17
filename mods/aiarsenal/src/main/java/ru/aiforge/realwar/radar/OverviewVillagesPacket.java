package ru.aiforge.realwar.radar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: village positions around the strategic map centre. */
public record OverviewVillagesPacket(List<int[]> villages) {
    public static void encode(OverviewVillagesPacket m, FriendlyByteBuf b) {
        b.writeVarInt(m.villages.size());
        for (int[] v : m.villages) b.writeInt(v[0]).writeInt(v[1]);
    }

    public static OverviewVillagesPacket decode(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 4096);
        List<int[]> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new int[]{b.readInt(), b.readInt()});
        return new OverviewVillagesPacket(l);
    }

    public static void handle(OverviewVillagesPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.realwar.client.sight.OverviewClient.acceptVillages(m.villages)));
        ctx.get().setPacketHandled(true);
    }
}
