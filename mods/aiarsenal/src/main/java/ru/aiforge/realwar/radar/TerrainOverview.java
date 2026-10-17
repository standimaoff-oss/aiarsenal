package ru.aiforge.realwar.radar;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.network.ModNetwork;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Strategic 5x5 km map: a low-resolution picture of terrain the client has never loaded, computed on the server from the
 * world generator itself (noise columns + biome source + structure placement) — no chunk is generated or loaded.
 * <p>
 * Tiles are 512x512 blocks = 64x64 cells of 8 blocks. Per cell: surface height (incl. water), water depth, biome class.
 * Terrain columns are sampled every 16 blocks (33x33 per tile, bilinear in between), biomes every 8 blocks, so one tile
 * costs ~1100 noise columns (~40-80 ms on one core). Work runs on ONE low-priority daemon thread, nearest tiles first;
 * finished tiles are cached (LRU, 1024 tiles ~ 12 MB) and sent deflated (~3-6 KB each).
 * Villages come from the village structure-set placement grid + a biome check of every candidate chunk.
 */
public final class TerrainOverview {
    public static final int CELL = 8, CELLS = 64, TILE = CELL * CELLS;     // 512 blocks
    public static final int RADIUS = 2600;                                  // blocks around the map centre
    private static final int STEP = 16, GRID = TILE / STEP + 1;             // 33 noise columns per side

    // biome classes (client colours them)
    public static final byte B_PLAINS = 0, B_FOREST = 1, B_JUNGLE = 2, B_TAIGA = 3, B_SNOW = 4, B_SAND = 5, B_BADLANDS = 6,
            B_SAVANNA = 7, B_SWAMP = 8, B_MOUNTAIN = 9, B_RIVER = 10, B_OCEAN = 11, B_DEEP = 12, B_FROZEN = 13, B_MUSHROOM = 14,
            B_MEADOW = 15;

    public static final class Tile {
        public final int tx, tz;
        public final short[] h = new short[CELLS * CELLS];
        public final byte[] water = new byte[CELLS * CELLS];
        public final byte[] biome = new byte[CELLS * CELLS];

        public Tile(int tx, int tz) {
            this.tx = tx;
            this.tz = tz;
        }
    }

    private record Key(ResourceKey<Level> dim, int tx, int tz) {
    }

    private static final Map<Key, Tile> CACHE = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Tile> e) {
            return this.size() > 1024;
        }
    });
    private static final Map<Key, Set<UUID>> WAITING = new ConcurrentHashMap<>();
    private static ExecutorService worker;
    private static final Map<ResourceKey<Level>, List<int[]>> VILLAGE_CACHE = new ConcurrentHashMap<>();

    private TerrainOverview() {
    }

    private static synchronized ExecutorService worker() {
        if (worker == null || worker.isShutdown()) {
            worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "aiarsenal-terrain-overview");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return worker;
    }

    /** Server stopping: drop the queue and the caches (world seed may differ next time). */
    public static synchronized void shutdown() {
        if (worker != null) worker.shutdownNow();
        worker = null;
        CACHE.clear();
        WAITING.clear();
        VILLAGE_CACHE.clear();
    }

    // ------------------------------------------------------------------ requests
    /** A client asks for the listed tiles (those it does not have yet) and the villages around (cx, cz). */
    public static void request(ServerPlayer p, int cx, int cz, List<int[]> tiles) {
        ServerLevel sl = p.serverLevel();
        ResourceKey<Level> dim = sl.dimension();
        UUID who = p.getUUID();
        MinecraftServer srv = sl.getServer();
        // villages: computed once per 1 km grid square of the request centre
        int vx = Math.floorDiv(cx, 1024) * 1024 + 512, vz = Math.floorDiv(cz, 1024) * 1024 + 512;
        worker().execute(() -> {
            try {
                List<int[]> v = villages(sl, vx, vz, RADIUS + 600);
                srv.execute(() -> send(srv, who, new OverviewVillagesPacket(v)));
            } catch (Throwable t) {
                RealWar.LOGGER.warn("terrain overview: village scan failed", t);
            }
        });
        tiles.sort(Comparator.comparingDouble(t -> Math.hypot((t[0] + 0.5) * TILE - cx, (t[1] + 0.5) * TILE - cz)));
        int n = 0;
        for (int[] t : tiles) {
            if (++n > 160) break;                       // 5.2 km square = 121 tiles max
            if (Math.abs((t[0] + 0.5) * TILE - cx) > RADIUS + TILE || Math.abs((t[1] + 0.5) * TILE - cz) > RADIUS + TILE) continue;
            Key k = new Key(dim, t[0], t[1]);
            Tile done = CACHE.get(k);
            if (done != null) {
                send(srv, who, new OverviewTilePacket(done));
                continue;
            }
            Set<UUID> w = WAITING.computeIfAbsent(k, kk -> ConcurrentHashMap.newKeySet());
            boolean first = w.isEmpty();
            w.add(who);
            if (!first) continue;
            worker().execute(() -> {
                Tile tile;
                try {
                    tile = compute(sl, k.tx, k.tz);
                } catch (Throwable ex) {
                    RealWar.LOGGER.warn("terrain overview: tile {} {} failed", k.tx, k.tz, ex);
                    WAITING.remove(k);
                    return;
                }
                CACHE.put(k, tile);
                Set<UUID> ws = WAITING.remove(k);
                if (ws != null) srv.execute(() -> {
                    OverviewTilePacket pk = new OverviewTilePacket(tile);
                    for (UUID u : ws) send(srv, u, pk);
                });
            });
        }
    }

    private static void send(MinecraftServer srv, UUID who, Object pk) {
        ServerPlayer p = srv.getPlayerList().getPlayer(who);
        if (p != null) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pk);
    }

    // ------------------------------------------------------------------ sampling
    /** Computes one tile directly from the generator (thread-safe: vanilla worldgen calls the same code off-thread). */
    private static final java.util.concurrent.atomic.AtomicInteger LOGGED = new java.util.concurrent.atomic.AtomicInteger();

    public static Tile compute(ServerLevel sl, int tx, int tz) {
        long t0 = System.nanoTime();
        ChunkGenerator gen = sl.getChunkSource().getGenerator();
        RandomState rs = sl.getChunkSource().randomState();
        int x0 = tx * TILE, z0 = tz * TILE;
        short[] gs = new short[GRID * GRID];
        short[] gw = new short[GRID * GRID];
        int minY = sl.getMinBuildHeight();
        net.minecraft.world.level.levelgen.DensityFunction dens = null;
        int nMin = minY, nMax = minY + sl.getHeight();
        if (gen instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator ng) {
            // fast path: the preliminary-surface density (no 3D caves/jaggedness), sampled directly - ~30 noise evaluations per column
            dens = rs.router().initialDensityWithoutJaggedness();
            net.minecraft.world.level.levelgen.NoiseSettings ns = ng.generatorSettings().value().noiseSettings();
            nMin = Math.max(minY, ns.minY());
            nMax = Math.min(minY + sl.getHeight(), ns.minY() + ns.height());
        }
        int seaL = gen.getSeaLevel();
        for (int j = 0; j < GRID; j++)
            for (int i = 0; i < GRID; i++) {
                int[] sw = dens != null ? fastColumn(dens, x0 + i * STEP, z0 + j * STEP, nMin, nMax, seaL)
                        : column(gen, sl, rs, x0 + i * STEP, z0 + j * STEP, minY);
                gs[j * GRID + i] = (short) sw[0];
                gw[j * GRID + i] = (short) (sw[0] - sw[1]);
            }
        Tile t = new Tile(tx, tz);
        int sea = gen.getSeaLevel();
        for (int cz = 0; cz < CELLS; cz++)
            for (int cx = 0; cx < CELLS; cx++) {
                float fx = (cx * CELL + CELL * 0.5f) / STEP, fz = (cz * CELL + CELL * 0.5f) / STEP;
                int i = Math.min(GRID - 2, (int) fx), j = Math.min(GRID - 2, (int) fz);
                float ax = fx - i, az = fz - j;
                float h = lerp2(gs, i, j, ax, az);
                float w = lerp2(gw, i, j, ax, az);
                int idx = cz * CELLS + cx;
                int hy = Math.round(h);
                t.h[idx] = (short) hy;
                int wx = x0 + cx * CELL + CELL / 2, wz = z0 + cz * CELL + CELL / 2;
                byte bc;
                if ((cx & 1) == 0 && (cz & 1) == 0) {
                    // biomes on a 16-block lattice (noise biomes are 4-block quarts; rivers/forests stay readable)
                    Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(wx), QuartPos.fromBlock(Math.max(hy, sea - 8)),
                            QuartPos.fromBlock(wz), rs.sampler());
                    bc = classify(b);
                } else {
                    bc = t.biome[(cz & ~1) * CELLS + (cx & ~1)];
                }
                int depth = Math.round(w);
                if (bc == B_RIVER && depth < 1 && hy <= sea + 1) depth = 2;          // river beds the bilinear grid smoothed away
                t.water[idx] = (byte) Math.max(0, Math.min(127, depth));
                t.biome[idx] = bc;
            }
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (LOGGED.getAndIncrement() < 3 || ms > 1500) RealWar.LOGGER.info("terrain overview tile {} {} ({}) took {} ms", tx, tz, dens != null ? "noise" : "columns", ms);
        return t;
    }

    private static float lerp2(short[] g, int i, int j, float ax, float az) {
        float a = g[j * GRID + i] + (g[j * GRID + i + 1] - g[j * GRID + i]) * ax;
        float b = g[(j + 1) * GRID + i] + (g[(j + 1) * GRID + i + 1] - g[(j + 1) * GRID + i]) * ax;
        return a + (b - a) * az;
    }

    private static final double SURFACE_DENSITY = 0.390625;   // vanilla NoiseChunk preliminary surface threshold

    /** {surface y incl. sea, floor y} from the preliminary density: 8-block steps from the top, linear refine. */
    public static int[] fastColumn(net.minecraft.world.level.levelgen.DensityFunction d, int x, int z, int minY, int maxY, int sea) {
        double prev = Double.NaN;
        int floor = minY;
        for (int y = maxY - 8; y >= minY; y -= 8) {
            double v = d.compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z));
            if (v > SURFACE_DENSITY) {
                if (Double.isNaN(prev)) {
                    floor = y;
                } else {
                    double f = (v - SURFACE_DENSITY) / Math.max(1e-6, v - prev);
                    floor = y + (int) Math.round(8.0 * Math.min(1.0, f));
                }
                break;
            }
            prev = v;
        }
        return new int[]{Math.max(floor, floor < sea ? sea - 1 : floor), floor};
    }

    /** {surface y incl. fluids, solid floor y} of one generator noise column. */
    private static int[] column(ChunkGenerator gen, ServerLevel sl, RandomState rs, int x, int z, int minY) {
        NoiseColumn col = gen.getBaseColumn(x, z, sl, rs);
        int top = minY + sl.getHeight() - 1;
        int surf = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
        for (int y = top; y >= minY; y--) {
            BlockState s = col.getBlock(y);
            if (s.isAir()) continue;
            if (surf == Integer.MIN_VALUE) surf = y;
            if (s.getFluidState().isEmpty()) {
                floor = y;
                break;
            }
        }
        if (surf == Integer.MIN_VALUE) surf = minY;
        if (floor == Integer.MIN_VALUE) floor = minY;
        return new int[]{surf, floor};
    }

    public static byte classify(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_RIVER)) return B_RIVER;
        boolean cold = b.value().getBaseTemperature() < 0.15f;
        if (b.is(BiomeTags.IS_DEEP_OCEAN)) return cold ? B_FROZEN : B_DEEP;
        if (b.is(BiomeTags.IS_OCEAN)) return cold ? B_FROZEN : B_OCEAN;
        if (b.is(Biomes.MUSHROOM_FIELDS)) return B_MUSHROOM;
        if (b.is(Biomes.SWAMP) || b.is(Biomes.MANGROVE_SWAMP)) return B_SWAMP;
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BEACH)) return cold ? B_SNOW : B_SAND;
        if (b.is(BiomeTags.IS_BADLANDS)) return B_BADLANDS;
        if (b.is(Biomes.STONY_PEAKS) || b.is(Biomes.JAGGED_PEAKS) || b.is(Biomes.FROZEN_PEAKS) || b.is(Biomes.WINDSWEPT_HILLS)
                || b.is(Biomes.WINDSWEPT_GRAVELLY_HILLS) || b.is(Biomes.STONY_SHORE)) return B_MOUNTAIN;
        if (b.is(Biomes.MEADOW) || b.is(Biomes.CHERRY_GROVE)) return B_MEADOW;
        if (cold) return B_SNOW;
        if (b.is(BiomeTags.IS_JUNGLE) || b.is(Biomes.DARK_FOREST)) return B_JUNGLE;
        if (b.is(BiomeTags.IS_SAVANNA)) return B_SAVANNA;
        if (b.is(BiomeTags.IS_TAIGA)) return B_TAIGA;
        if (b.is(BiomeTags.IS_FOREST)) return B_FOREST;
        if (b.is(BiomeTags.IS_MOUNTAIN)) return B_MOUNTAIN;
        return B_PLAINS;
    }

    // ------------------------------------------------------------------ villages
    /** Village positions {x, z} within r blocks of (cx, cz) predicted from the structure placement + biome validity. */
    public static List<int[]> villages(ServerLevel sl, int cx, int cz, int r) {
        ChunkGeneratorStructureState st = sl.getChunkSource().getGeneratorState();
        ChunkGenerator gen = sl.getChunkSource().getGenerator();
        RandomState rs = sl.getChunkSource().randomState();
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Holder<StructureSet> set : st.possibleStructureSets()) {
            StructureSet ss = set.value();
            List<StructureSet.StructureSelectionEntry> vil = ss.structures().stream().filter(e -> e.structure().is(StructureTags.VILLAGE)).toList();
            if (vil.isEmpty() || !(ss.placement() instanceof RandomSpreadStructurePlacement rp)) continue;
            int sp = rp.spacing();
            int c0x = (cx - r) >> 4, c1x = (cx + r) >> 4, c0z = (cz - r) >> 4, c1z = (cz + r) >> 4;
            for (int rz = Math.floorDiv(c0z, sp); rz <= Math.floorDiv(c1z, sp); rz++)
                for (int rx = Math.floorDiv(c0x, sp); rx <= Math.floorDiv(c1x, sp); rx++) {
                    ChunkPos cp = rp.getPotentialStructureChunk(st.getLevelSeed(), rx * sp, rz * sp);
                    if (!((StructurePlacement) rp).isStructureChunk(st, cp.x, cp.z)) continue;
                    int bx = cp.getMiddleBlockX(), bz = cp.getMiddleBlockZ();
                    if (Math.abs(bx - cx) > r || Math.abs(bz - cz) > r) continue;
                    int[] sw = column(gen, sl, rs, bx, bz, sl.getMinBuildHeight());
                    if (sw[0] != sw[1]) continue;                        // under water
                    Holder<Biome> b = gen.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(sw[0]), QuartPos.fromBlock(bz), rs.sampler());
                    boolean ok = false;
                    for (StructureSet.StructureSelectionEntry e : vil) if (e.structure().value().biomes().contains(b)) { ok = true; break; }
                    if (ok && seen.add(cp.toLong())) out.add(new int[]{bx, bz});
                }
        }
        return out;
    }
}
