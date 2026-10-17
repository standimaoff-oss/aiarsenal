package ru.aiforge.realwar.radar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client -> server: "I am looking at (cx, cz), these overview tiles are missing". */
public record OverviewRequestPacket(int cx, int cz, List<int[]> tiles) {
    public static void encode(OverviewRequestPacket m, FriendlyByteBuf b) {
        b.writeInt(m.cx).writeInt(m.cz);
        b.writeVarInt(m.tiles.size());
        for (int[] t : m.tiles) b.writeVarInt(t[0]).writeVarInt(t[1]);
    }

    public static OverviewRequestPacket decode(FriendlyByteBuf b) {
        int cx = b.readInt(), cz = b.readInt();
        int n = Math.min(b.readVarInt(), 200);
        List<int[]> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new int[]{b.readVarInt(), b.readVarInt()});
        return new OverviewRequestPacket(cx, cz, l);
    }

    public static void handle(OverviewRequestPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            // the strategic map is centred on the player's own position (+-2.6 km): no remote scouting of the world
            if (Math.abs(m.cx - p.getX()) > TerrainOverview.RADIUS || Math.abs(m.cz - p.getZ()) > TerrainOverview.RADIUS) return;
            TerrainOverview.request(p, m.cx, m.cz, new ArrayList<>(m.tiles));
        });
        ctx.get().setPacketHandled(true);
    }
}
