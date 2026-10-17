package ru.aiforge.realwar.field;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import ru.aiforge.realwar.registry.ModBlocks;

/**
 * r13: trench between two points marked with the entrenching tool. Shared by the server (digging) and the client
 * (semi-transparent preview), so both always agree on the result.
 * <p>
 * Path: an L in the horizontal plane - along the longer axis first, one turn, then along the shorter one (a straight
 * line when the points are aligned). Each column follows the terrain: the ground surface is searched within +-3 blocks of
 * the previous column, so the trench runs over gentle slopes. Every diggable column becomes a 2-deep trench cell: the
 * ground block and the one below are dug, a duckboard is laid on the floor; every soil face along the cells (both
 * levels, the turn and the two ends included) gets a revetted trench wall facing into the trench.
 */
public final class TrenchPlan {
    public static final int MAX_CELLS = 64;

    /** One trench column: top = ground level block (dug), floor = top.below() (dug, duckboard). */
    public record Cell(BlockPos top, boolean ok, boolean done) {
        public BlockPos floor() {
            return this.top.below();
        }
    }

    /** A wall to place: position + direction it faces (into the trench). */
    public record Wall(BlockPos pos, Direction facing) {
    }

    public final List<Cell> cells = new ArrayList<>();
    public final List<Wall> walls = new ArrayList<>();
    public final int length;

    private TrenchPlan(int length) {
        this.length = length;
    }

    /** Columns of the L path from a to b (x, z pairs), the corner included once. */
    public static List<int[]> path(BlockPos a, BlockPos b) {
        List<int[]> out = new ArrayList<>();
        int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        int sx = Integer.signum(dx), sz = Integer.signum(dz);
        int x = a.getX(), z = a.getZ();
        out.add(new int[]{x, z});
        if (Math.abs(dx) >= Math.abs(dz)) {
            while (x != b.getX()) { x += sx; out.add(new int[]{x, z}); }
            while (z != b.getZ()) { z += sz; out.add(new int[]{x, z}); }
        } else {
            while (z != b.getZ()) { z += sz; out.add(new int[]{x, z}); }
            while (x != b.getX()) { x += sx; out.add(new int[]{x, z}); }
        }
        return out;
    }

    public static int pathLength(BlockPos a, BlockPos b) {
        return Math.abs(b.getX() - a.getX()) + Math.abs(b.getZ() - a.getZ()) + 1;
    }

    private static boolean open(BlockState s) {
        return s.isAir() || s.canBeReplaced() || !s.isSolid();
    }

    /** Ground block of the column near refY, or null. A column already dug (duckboard floor) returns its old top. */
    private static Cell surface(BlockGetter lv, int x, int z, int refY) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = refY + 3; y >= refY - 3; --y) {
            BlockState s = lv.getBlockState(m.set(x, y, z));
            if (s.is(ModBlocks.DUCKBOARD.get())) return new Cell(new BlockPos(x, y + 1, z), true, true);
            if (open(s)) continue;
            if (!open(lv.getBlockState(m.set(x, y + 1, z)))) continue;           // buried: not the surface
            boolean ok = EntrenchingToolItem.soil(s) && EntrenchingToolItem.soil(lv.getBlockState(m.set(x, y - 1, z)));
            return new Cell(new BlockPos(x, y, z), ok, false);
        }
        return null;
    }

    public static TrenchPlan plan(BlockGetter lv, BlockPos a, BlockPos b) {
        int len = pathLength(a, b);
        TrenchPlan p = new TrenchPlan(len);
        if (len > MAX_CELLS) return p;
        int ref = a.getY();
        Set<Long> cols = new HashSet<>();
        for (int[] c : path(a, b)) {
            Cell cell = surface(lv, c[0], c[1], ref);
            if (cell == null) {
                p.cells.add(new Cell(new BlockPos(c[0], ref, c[1]), false, false));
                continue;
            }
            p.cells.add(cell);
            if (cell.ok) {
                ref = cell.top.getY();
                cols.add(col(c[0], c[1]));
            }
        }
        for (Cell cell : p.cells) {
            if (!cell.ok) continue;
            for (BlockPos lvl : new BlockPos[]{cell.floor(), cell.top}) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos w = lvl.relative(d);
                    if (cols.contains(col(w.getX(), w.getZ()))) continue;            // part of the trench itself
                    if (EntrenchingToolItem.soil(lv.getBlockState(w))) p.walls.add(new Wall(w, d.getOpposite()));
                }
            }
        }
        return p;
    }

    private static long col(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public int okCells() {
        int n = 0;
        for (Cell c : this.cells) if (c.ok) ++n;
        return n;
    }
}
