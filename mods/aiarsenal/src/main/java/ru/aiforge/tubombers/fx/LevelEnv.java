package ru.aiforge.tubombers.fx;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.aiforge.tubombers.aircraft.FlightModel;

/** Terrain queries for the flight model and the bomb ballistics (works on both logical sides). */
public final class LevelEnv implements FlightModel.Env {
    private final Level level;
    private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

    public LevelEnv(Level level) {
        this.level = level;
    }

    @Override
    public double ground(double x, double y, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return -1e9;
        int top = Mth.floor(y + 1);
        int hm = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz); // first air above the highest solid block
        if (hm <= top - 8) return topOf(bx, hm - 1, bz);        // far above the terrain: the heightmap answers
        int lim = Math.max(level.getMinBuildHeight(), top - 10);
        for (int by = Math.min(top, hm); by >= lim; by--) {
            double t = topOf(bx, by, bz);
            if (t > -1e8 && t <= y + 1.0) return t;
        }
        return hm <= top + 1 ? topOf(bx, hm - 1, bz) : -1e9;
    }

    private double topOf(int x, int y, int z) {
        mp.set(x, y, z);
        BlockState s = level.getBlockState(mp);
        if (!s.getFluidState().isEmpty() && s.getCollisionShape(level, mp).isEmpty()) return y + 0.9; // water surface
        VoxelShape sh = s.getCollisionShape(level, mp);
        if (sh.isEmpty()) return -1e9;
        return y + sh.max(net.minecraft.core.Direction.Axis.Y);
    }

    @Override
    public boolean solid(double x, double y, double z) {
        mp.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        if (!level.hasChunk(mp.getX() >> 4, mp.getZ() >> 4)) return false;
        BlockState s = level.getBlockState(mp);
        if (s.isAir()) return false;
        VoxelShape sh = s.getCollisionShape(level, mp);
        if (sh.isEmpty()) return false;
        double fy = y - mp.getY();
        return fy <= sh.max(net.minecraft.core.Direction.Axis.Y) && fy >= sh.min(net.minecraft.core.Direction.Axis.Y);
    }
}
