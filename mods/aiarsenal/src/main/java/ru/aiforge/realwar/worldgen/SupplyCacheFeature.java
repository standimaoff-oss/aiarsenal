package ru.aiforge.realwar.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.registry.ModBlocks;

/** r17: small sandbagged supply cache: a 5 x 5 ring of sandbags with an entrance, a camouflage canopy on spruce posts
 *  (forest / desert / winter by biome) and 1-3 crates (ammo 50 %, medical 25 %, military 20 %, weapons 5 %). */
public class SupplyCacheFeature extends Feature<NoneFeatureConfiguration> {
    public SupplyCacheFeature(Codec<NoneFeatureConfiguration> c) {
        super(c);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!RWConfig.COMMON.supplyCaches.get()) return false;
        WorldGenLevel l = ctx.level();
        RandomSource r = ctx.random();
        BlockPos c = SiteUtil.surface(l, (ctx.origin().getX() & ~15) + 8, (ctx.origin().getZ() & ~15) + 8);
        if (!SiteUtil.dryGround(l, c)) return false;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos s = SiteUtil.surface(l, c.getX() + dx, c.getZ() + dz);
                if (Math.abs(s.getY() - c.getY()) > 1 || !SiteUtil.dryGround(l, s)) return false;
            }
        // level the floor and clear 3 blocks of headroom
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos col = c.offset(dx, 0, dz);
                if (l.getBlockState(col.below()).canBeReplaced()) l.setBlock(col.below(), Blocks.COARSE_DIRT.defaultBlockState(), 2);
                for (int y = 0; y < 3; y++) l.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), 2);
            }
        Direction gap = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockState bag = ModBlocks.SANDBAGS.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                if (dx == gap.getStepX() * 2 && dz == gap.getStepZ() * 2) continue;
                if (gap.getAxis() == Direction.Axis.X ? (dx == gap.getStepX() * 2 && Math.abs(dz) == 0) : (dz == gap.getStepZ() * 2 && Math.abs(dx) == 0)) continue;
                l.setBlock(c.offset(dx, 0, dz), bag, 2);
            }
        Holder<Biome> b = l.getBiome(c);
        Block canopy = b.value().coldEnoughToSnow(c) ? ModBlocks.CAMO_CANOPY_WINTER.get()
                : b.value().getBaseTemperature() > 1.0f ? ModBlocks.CAMO_CANOPY_DESERT.get() : ModBlocks.CAMO_CANOPY_FOREST.get();
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2)
                for (int y = 0; y < 3; y++) l.setBlock(c.offset(sx, y, sz), Blocks.SPRUCE_FENCE.defaultBlockState(), 2);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (!(Math.abs(dx) == 1 && Math.abs(dz) == 1)) l.setBlock(c.offset(dx, 2, dz), canopy.defaultBlockState(), 2);
        // connections (sandbags / canopy / fences pick their shape from the neighbours)
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int y = 0; y <= 2; y++) {
                    BlockPos p = c.offset(dx, y, dz);
                    BlockState s = l.getBlockState(p);
                    if (!s.isAir()) l.setBlock(p, Block.updateFromNeighbourShapes(s, l, p), 2);
                }
        BlockPos[] spots = {c, c.offset(1, 0, 0), c.offset(-1, 0, 0), c.offset(0, 0, 1), c.offset(0, 0, -1)};
        int n = 1 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            float t = r.nextFloat();
            Block crate = t < 0.5f ? ModBlocks.CRATE_AMMO.get() : t < 0.75f ? ModBlocks.CRATE_MEDICAL.get()
                    : t < 0.95f ? ModBlocks.CRATE_MILITARY.get() : ModBlocks.CRATE_WEAPONS.get();
            SiteUtil.crate(l, spots[(i + r.nextInt(2)) % spots.length], crate, r);
        }
        return true;
    }
}
