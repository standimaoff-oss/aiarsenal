/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.BlockPos$MutableBlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.tags.BlockTags
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.FallingBlock
 *  net.minecraft.world.level.block.TntBlock
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.phys.Vec3
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.block.BombBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlastUtil {
    private BlastUtil() {
    }

    public static boolean isUnbreakable(BlockState s) {
        if (s.isAir()) {
            return false;
        }
        Block b = s.getBlock();
        return b.defaultDestroyTime() < 0.0f || b.getExplosionResistance() >= 3600000.0f;
    }

    public static boolean isNaturalGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH);
    }

    public static boolean isLooseGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.CLAY) || s.is(Blocks.MUD) || s.is(Blocks.DIRT_PATH);
    }

    public static BlockState stateOrNull(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return null;
        }
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        return level.getBlockState(pos);
    }

    public static boolean destroy(ServerLevel level, BlockPos pos, BlockState state, boolean dropContents) {
        BlockState u;
        if (state.isAir() || BlastUtil.isUnbreakable(state)) {
            return false;
        }
        Block b = state.getBlock();
        if (b == Blocks.TNT) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            TntBlock.explode((Level)level, (BlockPos)pos);
            ++BlastManager.totalBlocksChanged;
            return true;
        }
        if (b instanceof ru.aiforge.realwar.field.Detonatable det) {      // r12: mines / fuel drums go off in a chain
            level.removeBlock(pos, false);
            det.sympathetic(level, pos, state);
            ++BlastManager.totalBlocksChanged;
            return true;
        }
        if (b instanceof BombBlock) {
            level.removeBlock(pos, false);
            ++BlastManager.totalBlocksChanged;
            return true;
        }
        if (state.hasBlockEntity()) {
            if (dropContents) {
                level.destroyBlock(pos, false);
                ++BlastManager.totalBlocksChanged;
                return true;
            }
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        BlockPos up = pos.above();
        if (!level.isOutsideBuildHeight(up) && (u = level.getBlockState(up)).getBlock() instanceof FallingBlock) {
            level.scheduleTick(up, u.getBlock(), 2);
        }
        ++BlastManager.totalBlocksChanged;
        return true;
    }

    public static boolean set(ServerLevel level, BlockPos pos, BlockState newState) {
        if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) {
            return false;
        }
        level.setBlock(pos, newState, 18);
        ++BlastManager.totalBlocksChanged;
        return true;
    }

    public static double[] trace(ServerLevel level, Vec3 from, Vec3 to, double step) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        double res = 0.0;
        double opt = 0.0;
        if (len < 0.001) {
            return new double[]{0.0, 0.0};
        }
        int steps = Math.max(1, (int)(len / step));
        double inv = 1.0 / (double)steps;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        for (int i = 1; i < steps; ++i) {
            BlockState s;
            double t = (double)i * inv;
            mp.set(from.x + d.x * t, from.y + d.y * t, from.z + d.z * t);
            long key = mp.asLong();
            if (key == last) continue;
            last = key;
            if (level.isOutsideBuildHeight((BlockPos)mp) || !level.hasChunkAt((BlockPos)mp) || (s = level.getBlockState((BlockPos)mp)).isAir()) continue;
            if ((res += Math.min((double)s.getBlock().getExplosionResistance(), 40.0)) > 120.0 && (opt += (double)s.getLightBlock((BlockGetter)level, (BlockPos)mp)) > 40.0) break;
        }
        return new double[]{res, opt};
    }

    public static double thermalTransmission(ServerLevel level, Vec3 from, Vec3 to) {
        double[] t = BlastUtil.trace(level, from, to, 1.5);
        return Math.exp(-t[1] / 5.0);
    }

    public static double blastAttenuation(ServerLevel level, Vec3 from, Vec3 to) {
        double[] t = BlastUtil.trace(level, from, to, 1.0);
        return Math.exp(-t[0] / 18.0);
    }

    public static double hash01(int x, int z, long seed) {
        long h = seed ^ (long)x * -7046029254386353131L ^ (long)z * -4417276706812531889L;
        h ^= h >>> 33;
        h *= -49064778989728563L;
        h ^= h >>> 33;
        h *= -4265267296055464877L;
        h ^= h >>> 33;
        return (double)(h >>> 11) / 9.007199254740992E15;
    }
}

