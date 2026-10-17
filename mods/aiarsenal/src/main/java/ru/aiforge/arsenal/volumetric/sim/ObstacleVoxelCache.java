// Adapted from supplied VolPyro 1.5.6 (MIT), VolPyro Contributors. See THIRD_PARTY_NOTICES.md.
package ru.aiforge.arsenal.volumetric.sim;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ObstacleVoxelCache {
    private final Map<Long, CachedCell> occupancyByPos = new HashMap<Long, CachedCell>();

    public float occupancy(BlockGetter level, BlockPos pos) {
        long key = pos.asLong();
        BlockState state = level.getBlockState(pos);
        CachedCell cached = this.occupancyByPos.get(key);
        if (cached != null && cached.state.equals(state)) {
            return cached.occupancy;
        }
        VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.empty());
        float occupancy = ObstacleVoxelCache.volumeFraction(shape);
        if (this.occupancyByPos.size() >= 4096) this.occupancyByPos.clear();
        this.occupancyByPos.put(key, new CachedCell(state, occupancy));
        return occupancy;
    }

    public float clearanceAbove(BlockGetter level, BlockPos source, float maxHeight) {
        float limit = Math.max(0.0f, maxHeight);
        int checks = Math.max(1, (int)Math.ceil(limit));
        for (int step = 1; step <= checks; ++step) {
            BlockPos sample = source.above(step);
            float occupancy = this.occupancy(level, sample);
            if (!(occupancy > 0.001f)) continue;
            float open = (float)step - 1.0f + (1.0f - occupancy) * 0.5f;
            return Math.max(0.35f, Math.min(limit, open));
        }
        return limit;
    }

    public void invalidate(BlockPos pos) {
        this.occupancyByPos.remove(pos.asLong());
    }

    public void clear() {
        this.occupancyByPos.clear();
    }

    public int cachedCellCount() {
        return this.occupancyByPos.size();
    }

    public static float volumeFraction(VoxelShape shape) {
        if (shape.isEmpty()) {
            return 0.0f;
        }
        double total = 0.0;
        for (AABB box : shape.toAabbs()) {
            total += Math.max(0.0, box.getXsize()) * Math.max(0.0, box.getYsize()) * Math.max(0.0, box.getZsize());
        }
        return (float)Math.max(0.0, Math.min(1.0, total));
    }

    private record CachedCell(BlockState state, float occupancy) {
    }
}

