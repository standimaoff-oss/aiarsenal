package ru.aiforge.realwar.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.deco.WreckBlock;
import ru.aiforge.realwar.deco.WreckBlockEntity;
import ru.aiforge.realwar.registry.ModBlocks;

/** r17: burnt-out vehicle (tank / Mi-24 / MiG-29): the main piece plus some of the other pieces thrown a few metres
 *  away, scorched ground, coarse dirt under overhanging cells, sometimes still smouldering and, half the time, a
 *  military / weapons crate in the debris. Everything stays within 13 blocks of the chunk centre (feature write limit). */
public class WreckSiteFeature extends Feature<NoneFeatureConfiguration> {
    /** Footprint centre of the piece placed last (worldgen runs on one thread per feature call). */
    private static final ThreadLocal<BlockPos> LAST = new ThreadLocal<>();

    public WreckSiteFeature(Codec<NoneFeatureConfiguration> c) {
        super(c);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!RWConfig.COMMON.wreckSites.get()) return false;
        WorldGenLevel l = ctx.level();
        RandomSource r = ctx.random();
        BlockPos c = new BlockPos((ctx.origin().getX() & ~15) + 8, 0, (ctx.origin().getZ() & ~15) + 8);
        Block[][] groups = {
                {ModBlocks.WRECK_TANK_HULL.get(), ModBlocks.WRECK_TANK_TURRET.get(), ModBlocks.WRECK_TANK_TRACK.get()},
                {ModBlocks.WRECK_HELI_BODY.get(), ModBlocks.WRECK_HELI_TAIL.get(), ModBlocks.WRECK_HELI_ROTOR.get()},
                {ModBlocks.WRECK_JET_BODY.get(), ModBlocks.WRECK_JET_WING.get(), ModBlocks.WRECK_JET_TAIL.get()}};
        Block[] g = groups[r.nextInt(groups.length)];
        BlockPos main = place(l, r, c, (WreckBlock) g[0], c.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2));
        if (main == null) return false;
        BlockPos lastMainCentre = LAST.get();
        for (int i = 1; i < g.length; i++) {
            if (r.nextFloat() > 0.7f) continue;
            for (int tries = 0; tries < 4; tries++) {
                double a = r.nextDouble() * Math.PI * 2, d = 5 + r.nextDouble() * 4;
                BlockPos at = main.offset((int) Math.round(Math.cos(a) * d), 0, (int) Math.round(Math.sin(a) * d));
                if (place(l, r, c, (WreckBlock) g[i], at) != null) break;
            }
        }
        BlockPos hull = lastMainCentre;
        scorch(l, r, hull, 6);
        if (r.nextBoolean()) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            BlockPos cp = SiteUtil.surface(l, hull.getX() + d.getStepX() * 4 + r.nextInt(3) - 1, hull.getZ() + d.getStepZ() * 4 + r.nextInt(3) - 1);
            if (SiteUtil.dryGround(l, cp)) SiteUtil.crate(l, cp, r.nextFloat() < 0.75f ? ModBlocks.CRATE_MILITARY.get() : ModBlocks.CRATE_WEAPONS.get(), r);
        }
        return true;
    }

    /** Places one wreck piece with its anchor at column 'at'; returns the anchor or null when it does not fit / is wet. */
    private static BlockPos place(WorldGenLevel l, RandomSource r, BlockPos centre, WreckBlock wb, BlockPos at) {
        BlockPos a = SiteUtil.surface(l, at.getX(), at.getZ());
        if (!SiteUtil.dryGround(l, a)) return null;
        Direction f = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockPos o = wb.originFromAnchor(a, f);
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : wb.footprint(o, f)) {
            if (Math.abs(p.getX() - centre.getX()) > 13 || Math.abs(p.getZ() - centre.getZ()) > 13) return null;
            BlockState here = l.getBlockState(p);
            if (!here.canBeReplaced() || !l.getFluidState(p).isEmpty()) return null;   // never cut into villages / trees
            if (p.getY() != a.getY()) continue;
            if (!natural(l.getBlockState(p.below())) && !l.getBlockState(p.below()).canBeReplaced()) return null;
            BlockPos s = SiteUtil.surface(l, p.getX(), p.getZ());
            if (!l.getFluidState(s.below()).isEmpty()) return null;
            minY = Math.min(minY, s.getY());
            maxY = Math.max(maxY, s.getY());
        }
        if (maxY - minY > 4) return null;                          // too steep
        wb.placeAt(l, a, f, 2);
        LAST.set(centre(wb, a, f));
        // coarse dirt under ground-level cells that hang over a dip (up to 3 blocks)
        for (int i = 0; i < wb.kind.count(); i++) {
            if (wb.kind.cells[i * 4 + 1] != 0) continue;
            BlockPos p = WreckBlock.cell(o, f, wb.kind.cells[i * 4], 0, wb.kind.cells[i * 4 + 2]).below();
            for (int k = 0; k < 3 && l.getBlockState(p).canBeReplaced() && l.getFluidState(p).isEmpty(); k++, p = p.below())
                l.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), 2);
        }
        if (r.nextFloat() < 0.35f && l.getBlockEntity(a) instanceof WreckBlockEntity be) be.setSmoking(true);
        return a;
    }

    /** Natural ground the wreck may rest on (no farmland, paths, planks or other built blocks). */
    private static boolean natural(BlockState b) {
        return b.is(BlockTags.DIRT) || b.is(BlockTags.SAND) || b.is(Blocks.GRAVEL) || b.is(BlockTags.BASE_STONE_OVERWORLD)
                || b.is(Blocks.SNOW_BLOCK) || b.is(BlockTags.TERRACOTTA) || b.is(ModBlocks.SCORCHED_EARTH.get());
    }

    /** Centre of the footprint (scorch mark is centred on the hull, not on the anchor cell). */
    private static BlockPos centre(WreckBlock wb, BlockPos anchor, Direction f) {
        long x = 0, z = 0;
        int n = 0;
        for (BlockPos p : wb.footprint(wb.originFromAnchor(anchor, f), f)) {
            x += p.getX();
            z += p.getZ();
            n++;
        }
        return new BlockPos((int) Math.round((double) x / n), anchor.getY(), (int) Math.round((double) z / n));
    }

    private static void scorch(WorldGenLevel l, RandomSource r, BlockPos c, int rad) {
        BlockState scorched = ModBlocks.SCORCHED_EARTH.get().defaultBlockState();
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                if (dx * dx + dz * dz > rad * rad || r.nextFloat() < 0.25f) continue;
                BlockPos s = SiteUtil.surface(l, c.getX() + dx, c.getZ() + dz);
                BlockPos g = s.below();
                BlockState b = l.getBlockState(g);
                if (!(b.is(BlockTags.DIRT) || b.is(Blocks.SAND) || b.is(Blocks.RED_SAND) || b.is(Blocks.GRAVEL))) continue;
                boolean inner = dx * dx + dz * dz < rad * rad / 3;
                l.setBlock(g, r.nextFloat() < (inner ? 0.7f : 0.35f) ? scorched : Blocks.COARSE_DIRT.defaultBlockState(), 2);
                BlockState above = l.getBlockState(s);
                if (!above.isAir() && above.canBeReplaced() && !(above.getBlock() instanceof WreckBlock)) l.setBlock(s, Blocks.AIR.defaultBlockState(), 2);
            }
    }
}
