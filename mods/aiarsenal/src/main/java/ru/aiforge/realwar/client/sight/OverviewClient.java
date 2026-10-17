package ru.aiforge.realwar.client.sight;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.radar.OverviewRequestPacket;
import ru.aiforge.realwar.radar.TerrainOverview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client store of the strategic 5x5 km map: server-computed overview tiles (colour baked once on arrival: biome palette,
 * water by depth, hill-shading, 32-block contour lines, snow caps) + predicted villages. Cleared on dimension change.
 */
public final class OverviewClient {
    private static final Map<Long, int[]> RGB = new HashMap<>();
    private static final Map<Long, short[]> HEIGHT = new HashMap<>();
    private static final Set<Long> REQUESTED = new HashSet<>();
    public static List<int[]> villages = List.of();
    private static String dim = "";
    private static int lastCx = Integer.MIN_VALUE, lastCz;
    public static int received;

    private OverviewClient() {
    }

    private static long key(int tx, int tz) {
        return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    private static void checkDim() {
        ClientLevel l = Minecraft.getInstance().level;
        String d = l == null ? "" : l.dimension().location().toString();
        if (!d.equals(dim)) {
            dim = d;
            RGB.clear();
            HEIGHT.clear();
            REQUESTED.clear();
            villages = List.of();
            lastCx = Integer.MIN_VALUE;
            received = 0;
        }
    }

    /** Ask the server for every missing tile in the 5.2 km square around (cx, cz) (cheap: only once per 256 blocks moved). */
    public static void ensure(double cxd, double czd) {
        checkDim();
        int cx = Mth.floor(cxd), cz = Mth.floor(czd);
        if (lastCx != Integer.MIN_VALUE && Math.abs(cx - lastCx) < 256 && Math.abs(cz - lastCz) < 256) return;
        lastCx = cx;
        lastCz = cz;
        int r = TerrainOverview.RADIUS, T = TerrainOverview.TILE;
        List<int[]> need = new ArrayList<>();
        for (int tz = Math.floorDiv(cz - r, T); tz <= Math.floorDiv(cz + r, T); tz++)
            for (int tx = Math.floorDiv(cx - r, T); tx <= Math.floorDiv(cx + r, T); tx++) {
                long k = key(tx, tz);
                if (RGB.containsKey(k) || REQUESTED.contains(k)) continue;
                REQUESTED.add(k);
                need.add(new int[]{tx, tz});
            }
        ModNetwork.CHANNEL.sendToServer(new OverviewRequestPacket(cx, cz, need));
    }

    public static int tileCount() {
        return RGB.size();
    }

    /** ARGB of the overview at a block position, 0 if unknown. */
    public static int rgbAt(int x, int z) {
        int T = TerrainOverview.TILE, C = TerrainOverview.CELL, N = TerrainOverview.CELLS;
        int[] t = RGB.get(key(Math.floorDiv(x, T), Math.floorDiv(z, T)));
        if (t == null) return 0;
        int cx = Math.floorMod(x, T) / C, cz = Math.floorMod(z, T) / C;
        return t[cz * N + cx];
    }

    /** Overview surface height (Integer.MIN_VALUE if unknown). */
    public static int heightAt(int x, int z) {
        int T = TerrainOverview.TILE, C = TerrainOverview.CELL, N = TerrainOverview.CELLS;
        short[] h = HEIGHT.get(key(Math.floorDiv(x, T), Math.floorDiv(z, T)));
        if (h == null) return Integer.MIN_VALUE;
        return h[(Math.floorMod(z, T) / C) * N + Math.floorMod(x, T) / C];
    }

    public static void acceptVillages(List<int[]> v) {
        checkDim();
        villages = v;
    }

    public static void accept(TerrainOverview.Tile t) {
        checkDim();
        int N = TerrainOverview.CELLS;
        int[] rgb = new int[N * N];
        for (int z = 0; z < N; z++)
            for (int x = 0; x < N; x++) {
                int i = z * N + x;
                int h = t.h[i];
                int col;
                int wd = t.water[i];
                byte b = t.biome[i];
                if (wd > 0 || b == TerrainOverview.B_RIVER && wd > 0) {
                    float d = Math.min(wd, 40) / 40f;
                    col = b == TerrainOverview.B_FROZEN ? mix(0xA8CCEE, 0x6E9CD0, d) : mix(0x4A86E8, 0x1C3C8C, d);
                } else {
                    col = palette(b, h);
                    // hill-shading from the north-west neighbour + contour lines every 32 blocks
                    int hn = t.h[Math.max(0, z - 1) * N + Math.max(0, x - 1)];
                    float f = 1f + Mth.clamp((h - hn) * 0.035f, -0.28f, 0.28f);
                    int hw = t.h[z * N + Math.max(0, x - 1)], hu = t.h[Math.max(0, z - 1) * N + x];
                    if (Math.floorDiv(h, 32) != Math.floorDiv(hw, 32) || Math.floorDiv(h, 32) != Math.floorDiv(hu, 32)) f *= 0.80f;
                    col = scale(col, f);
                }
                rgb[i] = 0xFF000000 | col;
            }
        long k = key(t.tx, t.tz);
        RGB.put(k, rgb);
        HEIGHT.put(k, t.h);
        REQUESTED.remove(k);
        received++;
    }

    private static int palette(byte b, int h) {
        int c = switch (b) {
            case TerrainOverview.B_FOREST -> 0x4E7A2A;
            case TerrainOverview.B_JUNGLE -> 0x2E5A1C;
            case TerrainOverview.B_TAIGA -> 0x3D6648;
            case TerrainOverview.B_SNOW -> 0xE6ECF0;
            case TerrainOverview.B_SAND -> 0xD8C88E;
            case TerrainOverview.B_BADLANDS -> 0xB4653C;
            case TerrainOverview.B_SAVANNA -> 0xA8A052;
            case TerrainOverview.B_SWAMP -> 0x4C5A32;
            case TerrainOverview.B_MOUNTAIN -> 0x8C8C88;
            case TerrainOverview.B_MUSHROOM -> 0x8E6E8E;
            case TerrainOverview.B_MEADOW -> 0x8FBF5E;
            case TerrainOverview.B_RIVER, TerrainOverview.B_OCEAN, TerrainOverview.B_DEEP -> 0xC8BC88;   // dry banks
            case TerrainOverview.B_FROZEN -> 0xE6ECF0;
            default -> 0x7DAF3C;
        };
        // altitude tint: rock above 130, snow caps above 175
        if (h > 175) return mix(c, 0xF4F6F8, Math.min(1f, (h - 175) / 25f));
        if (h > 130) return mix(c, 0x8E8E8A, Math.min(1f, (h - 130) / 45f));
        return c;
    }

    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return r << 16 | g << 8 | bl;
    }

    private static int scale(int c, float f) {
        int r = Mth.clamp((int) (((c >> 16) & 255) * f), 0, 255);
        int g = Mth.clamp((int) (((c >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp((int) ((c & 255) * f), 0, 255);
        return r << 16 | g << 8 | b;
    }
}
