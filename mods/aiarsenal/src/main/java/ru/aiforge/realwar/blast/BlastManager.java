/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.Level
 *  net.minecraftforge.event.TickEvent$LevelTickEvent
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.event.server.ServerStoppedEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package ru.aiforge.realwar.blast;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.realwar.blast.BlastTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal")
public final class BlastManager {
    private static final Map<ResourceKey<Level>, List<BlastTask>> TASKS = new HashMap<ResourceKey<Level>, List<BlastTask>>();
    public static volatile long lastTickNanos = 0L;
    public static volatile long totalBlocksChanged = 0L;
    public static volatile int completedBlasts = 0;

    private BlastManager() {
    }

    public static synchronized void add(ServerLevel level, BlastTask task) {
        TASKS.computeIfAbsent((ResourceKey<Level>)level.dimension(), k -> new ArrayList()).add(task);
    }

    public static synchronized int active(ServerLevel level) {
        List<BlastTask> l = TASKS.get(level.dimension());
        return l == null ? 0 : l.size();
    }

    public static synchronized List<String> describe(ServerLevel level) {
        ArrayList<String> out = new ArrayList<String>();
        List<BlastTask> l = TASKS.get(level.dimension());
        if (l != null) {
            for (BlastTask t : l) {
                out.add(t.describe());
            }
        }
        return out;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        Level level;
        if (e.phase != TickEvent.Phase.END || !((level = e.level) instanceof ServerLevel)) {
            return;
        }
        ServerLevel level2 = (ServerLevel)level;
        Class<BlastManager> clazz = BlastManager.class;
        synchronized (BlastManager.class) {
            List<BlastTask> list = TASKS.get(level2.dimension());
            if (list == null || list.isEmpty()) {
                // ** MonitorExit[var3_3] (shouldn't be in output)
                return;
            }
            list = new ArrayList<BlastTask>(list);
            // ** MonitorExit[var3_3] (shouldn't be in output)
            long start = System.nanoTime();
            long deadline = start + (long)((Integer)RWConfig.COMMON.blastBudgetMs.get()).intValue() * 1000000L;
            ArrayList<BlastTask> finished = new ArrayList<BlastTask>();
            long slice = Math.max(1000000L, (deadline - start) / (long)list.size());
            for (BlastTask t : list) {
                long d = Math.min(deadline, System.nanoTime() + slice);
                try {
                    if (!t.tick(level2, d)) continue;
                    finished.add(t);
                }
                catch (Throwable ex) {
                    RealWar.LOGGER.error("\u041e\u0448\u0438\u0431\u043a\u0430 \u0432 \u0437\u0430\u0434\u0430\u0447\u0435 \u0432\u0437\u0440\u044b\u0432\u0430 {}", t.describe(), ex);
                    finished.add(t);
                }
            }
            lastTickNanos = System.nanoTime() - start;
            if (finished.isEmpty()) return;
            Class<BlastManager> clazz2 = BlastManager.class;
            synchronized (BlastManager.class) {
                List<BlastTask> real = TASKS.get(level2.dimension());
                if (real == null) return;
                Iterator<BlastTask> it = real.iterator();
                while (it.hasNext()) {
                    if (!finished.contains(it.next())) continue;
                    it.remove();
                    ++completedBlasts;
                }
                // ** MonitorExit[var10_9] (shouldn't be in output)
                return;
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        Class<BlastManager> clazz = BlastManager.class;
        synchronized (BlastManager.class) {
            TASKS.clear();
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return;
        }
    }
}

