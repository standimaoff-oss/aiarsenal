package ru.aiforge.realwar.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * r21 weather and ground under a vehicle (shared by the server drive physics and the client track marks):
 * soil in the rain turns to mud, snow cover / falling snow is snow. Mud and snow raise the rolling resistance and cut
 * the top speed (wheels suffer more than tracks), and both keep the vehicle's tracks for a while.
 */
public final class Terrain {
    public static final int FIRM = 0, MUD = 1, SNOW = 2, BOG = 3;

    private Terrain() {
    }

    static boolean soil(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.DIRT) || s.is(Blocks.FARMLAND) || s.is(Blocks.DIRT_PATH) || s.is(Blocks.CLAY)
                || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.PODZOL);
    }

    static boolean snow(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW);
    }

    /** Ground at a world point (feet level of a wheel / track). */
    public static int at(Level level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y + 0.05, z);
        BlockState in = level.getBlockState(p);
        BlockState below = level.getBlockState(p.below());
        if (snow(in) || snow(below)) return SNOW;
        if (in.is(Blocks.MUD) || below.is(Blocks.MUD) || below.is(Blocks.SOUL_SAND) || below.is(Blocks.SOUL_SOIL)) return BOG;
        if (!soil(below)) return FIRM;
        if (level.isRaining() && level.canSeeSky(p)) {
            return level.getBiome(p).value().coldEnoughToSnow(p) ? SNOW : MUD;
        }
        return FIRM;
    }

    public static int under(Entity v) {
        return at(v.level(), v.getX(), v.getY(), v.getZ());
    }

    /** Rolling resistance multiplier. */
    public static double rollMul(int t, boolean tracked) {
        return switch (t) {
            case MUD -> tracked ? 1.7 : 2.6;
            case SNOW -> tracked ? 1.5 : 2.2;
            case BOG -> tracked ? 2.6 : 4.0;
            default -> 1.0;
        };
    }

    /** Top speed multiplier. */
    public static double vmaxMul(int t, boolean tracked) {
        return switch (t) {
            case MUD -> tracked ? 0.85 : 0.7;
            case SNOW -> tracked ? 0.85 : 0.75;
            case BOG -> tracked ? 0.6 : 0.45;
            default -> 1.0;
        };
    }
}
