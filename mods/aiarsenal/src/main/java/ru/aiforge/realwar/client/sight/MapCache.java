/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientLevel
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.util.Mth
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.ChunkStatus
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.level.material.MapColor
 */
package ru.aiforge.realwar.client.sight;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class MapCache {
    private static final Map<String, Map<Long, Tile>> DIMS = new HashMap<String, Map<Long, Tile>>();
    private static int ring;

    private MapCache() {
    }

    private static Map<Long, Tile> dim(ClientLevel l) {
        return DIMS.computeIfAbsent(l.dimension().location().toString(), k -> new HashMap());
    }

    public static Tile tile(ClientLevel l, int cx, int cz) {
        return MapCache.dim(l).get(ChunkPos.asLong((int)cx, (int)cz));
    }

    public static int known(ClientLevel l) {
        return MapCache.dim(l).size();
    }

    public static int heightAt(ClientLevel l, int x, int z) {
        Tile t = MapCache.tile(l, x >> 4, z >> 4);
        return t == null ? Integer.MIN_VALUE : t.y[(z & 0xF) * 16 + (x & 0xF)];
    }

    public static void clear() {
        DIMS.clear();
    }

    public static void tick(Minecraft mc) {
        ClientLevel l = mc.level;
        if (l == null || mc.player == null) {
            return;
        }
        int r = Math.min(mc.options.getEffectiveRenderDistance(), 18);
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        int side = r * 2 + 1;
        int total = side * side;
        Map<Long, Tile> m = MapCache.dim(l);
        long now = l.getGameTime();
        int budget = 3;
        for (int i = 0; i < 24 && budget > 0; ++i) {
            LevelChunk ch;
            int cx = pcx - r + (ring = (ring + 1) % total) % side;
            int cz = pcz - r + ring / side;
            Tile t = m.get(ChunkPos.asLong((int)cx, (int)cz));
            if (t != null && now - t.stamp < 600L || (ch = l.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false)) == null) continue;
            if (t == null) {
                t = new Tile();
                m.put(ChunkPos.asLong((int)cx, (int)cz), t);
            }
            MapCache.scan(l, (ChunkAccess)ch, t);
            t.stamp = now;
            --budget;
        }
    }

    private static void scan(ClientLevel l, ChunkAccess ch, Tile t) {
        int x;
        int z;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] depth = new int[256];
        for (z = 0; z < 16; ++z) {
            for (x = 0; x < 16; ++x) {
                MapColor mc;
                int wx = ch.getPos().getMinBlockX() + x;
                int wz = ch.getPos().getMinBlockZ() + z;
                int top = ch.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                if (top < l.getMinBuildHeight()) {
                    top = l.getMinBuildHeight();
                }
                pos.set(wx, top, wz);
                BlockState st = ch.getBlockState((BlockPos)pos);
                int water = 0;
                if (!st.getFluidState().isEmpty()) {
                    int yy = top;
                    while (yy > l.getMinBuildHeight() && !ch.getBlockState((BlockPos)pos.set(wx, yy, wz)).getFluidState().isEmpty()) {
                        --yy;
                        ++water;
                    }
                    pos.set(wx, top, wz);
                    st = ch.getBlockState((BlockPos)pos);
                }
                int col = (mc = st.getMapColor((BlockGetter)l, (BlockPos)pos)) == MapColor.NONE ? 0x161A16 : mc.col;
                t.y[z * 16 + x] = (short)top;
                depth[z * 16 + x] = water;
                t.rgb[z * 16 + x] = col;
            }
        }
        for (z = 0; z < 16; ++z) {
            for (x = 0; x < 16; ++x) {
                float f;
                int i = z * 16 + x;
                int col = t.rgb[i];
                if (depth[i] > 0) {
                    f = 1.0f - (float)Math.min(depth[i], 12) * 0.045f;
                } else {
                    short hn = t.y[Math.max(0, z - 1) * 16 + Math.min(15, x + 1)];
                    f = 0.92f + 0.06f * (float)Mth.clamp((int)(t.y[i] - hn), (int)-3, (int)3);
                }
                int r = Mth.clamp((int)((int)((float)(col >> 16 & 0xFF) * f)), (int)0, (int)255);
                int g = Mth.clamp((int)((int)((float)(col >> 8 & 0xFF) * f)), (int)0, (int)255);
                int b = Mth.clamp((int)((int)((float)(col & 0xFF) * f)), (int)0, (int)255);
                t.rgb[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
    }

    public static final class Tile {
        public final int[] rgb = new int[256];
        public final short[] y = new short[256];
        public long stamp;
    }
}

