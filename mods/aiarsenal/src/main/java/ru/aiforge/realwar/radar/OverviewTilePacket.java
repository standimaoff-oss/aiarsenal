package ru.aiforge.realwar.radar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.io.ByteArrayOutputStream;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Server -> client: one deflated 512x512-block overview tile (heights, water depth, biome class). */
public record OverviewTilePacket(TerrainOverview.Tile tile) {
    private static final int N = TerrainOverview.CELLS * TerrainOverview.CELLS;

    public static void encode(OverviewTilePacket m, FriendlyByteBuf b) {
        TerrainOverview.Tile t = m.tile;
        byte[] raw = new byte[N * 4];
        // heights as deltas along rows (compress far better), then water, then biome
        short prev = 0;
        for (int i = 0; i < N; i++) {
            short d = (short) (t.h[i] - prev);
            prev = t.h[i];
            raw[i * 2] = (byte) (d >> 8);
            raw[i * 2 + 1] = (byte) d;
        }
        System.arraycopy(t.water, 0, raw, N * 2, N);
        System.arraycopy(t.biome, 0, raw, N * 3, N);
        Deflater df = new Deflater(Deflater.BEST_COMPRESSION);
        df.setInput(raw);
        df.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        while (!df.finished()) bo.write(buf, 0, df.deflate(buf));
        df.end();
        b.writeVarInt(t.tx).writeVarInt(t.tz);
        b.writeByteArray(bo.toByteArray());
    }

    public static OverviewTilePacket decode(FriendlyByteBuf b) {
        int tx = b.readVarInt(), tz = b.readVarInt();
        byte[] z = b.readByteArray(1 << 20);
        byte[] raw = new byte[N * 4];
        Inflater inf = new Inflater();
        inf.setInput(z);
        try {
            int off = 0;
            while (off < raw.length && !inf.finished()) {
                int n = inf.inflate(raw, off, raw.length - off);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                off += n;
            }
        } catch (DataFormatException ex) {
            throw new IllegalArgumentException("bad overview tile", ex);
        } finally {
            inf.end();
        }
        TerrainOverview.Tile t = new TerrainOverview.Tile(tx, tz);
        short prev = 0;
        for (int i = 0; i < N; i++) {
            short d = (short) (((raw[i * 2] & 0xFF) << 8) | (raw[i * 2 + 1] & 0xFF));
            prev = (short) (prev + d);
            t.h[i] = prev;
        }
        System.arraycopy(raw, N * 2, t.water, 0, N);
        System.arraycopy(raw, N * 3, t.biome, 0, N);
        return new OverviewTilePacket(t);
    }

    public static void handle(OverviewTilePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.realwar.client.sight.OverviewClient.accept(m.tile)));
        ctx.get().setPacketHandled(true);
    }
}
