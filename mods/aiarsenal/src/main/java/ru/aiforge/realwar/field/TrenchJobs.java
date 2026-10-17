package ru.aiforge.realwar.field;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.registry.ModBlocks;

/** r13: a planned trench is dug progressively - one cell per tick (break particles + sound), then the revetments. */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class TrenchJobs {
    private static final List<Job> JOBS = new ArrayList<>();

    private TrenchJobs() {
    }

    private static final class Job {
        final ServerLevel level;
        final TrenchPlan plan;
        int cell, wall;

        Job(ServerLevel level, TrenchPlan plan) {
            this.level = level;
            this.plan = plan;
        }
    }

    public static synchronized void start(ServerLevel level, TrenchPlan plan) {
        JOBS.add(new Job(level, plan));
    }

    public static synchronized int pending() {
        return JOBS.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        synchronized (TrenchJobs.class) {
            Iterator<Job> it = JOBS.iterator();
            while (it.hasNext()) {
                Job j = it.next();
                if (j.level != sl) continue;
                if (step(j)) it.remove();
            }
        }
    }

    /** @return true when finished */
    private static boolean step(Job j) {
        ServerLevel l = j.level;
        List<TrenchPlan.Cell> cells = j.plan.cells;
        while (j.cell < cells.size()) {
            TrenchPlan.Cell c = cells.get(j.cell++);
            if (!c.ok() || c.done()) continue;
            BlockPos top = c.top(), floor = c.floor();
            BlockState above = l.getBlockState(top.above());
            if (!above.isAir() && above.canBeReplaced()) l.destroyBlock(top.above(), false);   // grass, flowers, snow
            if (EntrenchingToolItem.soil(l.getBlockState(top))) l.destroyBlock(top, false);
            if (EntrenchingToolItem.soil(l.getBlockState(floor))) l.destroyBlock(floor, false);
            if (l.getBlockState(floor).isAir()) l.setBlock(floor, ModBlocks.DUCKBOARD.get().defaultBlockState(), 3);
            l.playSound(null, top, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.9f, 0.75f + l.random.nextFloat() * 0.2f);
            return false;                                                     // one cell per tick
        }
        List<TrenchPlan.Wall> walls = j.plan.walls;
        int n = 0;
        while (j.wall < walls.size() && n < 8) {
            TrenchPlan.Wall w = walls.get(j.wall++);
            if (!EntrenchingToolItem.soil(l.getBlockState(w.pos()))) continue;
            l.setBlock(w.pos(), ModBlocks.TRENCH_WALL.get().defaultBlockState().setValue(TrenchBlock.FACING, w.facing()), 3);
            ++n;
        }
        if (n > 0) l.playSound(null, walls.get(j.wall - 1).pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 0.9f);
        return j.wall >= walls.size();
    }
}
