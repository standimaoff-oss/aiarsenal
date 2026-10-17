package ru.aiforge.realwar.vehicle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * r20: smoke screens of the tank grenade dischargers (902G Shtora / 902V Tucha, M250 / M257, NbK 39) and the T-34's
 * MDSh smoke drums. Server side clouds that keep emitting long-lived campfire smoke for ~25 s, so they really block
 * the view of everybody (sights included).
 */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class SmokeScreen {
    private record Cloud(ResourceKey<Level> dim, Vec3 pos, double r, int[] left) {}

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    public static final int LIFE = 500;

    private SmokeScreen() {
    }

    public static synchronized void add(ServerLevel sl, Vec3 pos, double r, int ticks) {
        if (CLOUDS.size() > 256) CLOUDS.remove(0);
        CLOUDS.add(new Cloud(sl.dimension(), pos, r, new int[]{ticks}));
        sl.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 30, r * 0.4, r * 0.3, r * 0.4, 0.08);
    }

    public static synchronized int active() {
        return CLOUDS.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        synchronized (SmokeScreen.class) {
            if (CLOUDS.isEmpty()) return;
            boolean emit = sl.getGameTime() % 2 == 0;
            Iterator<Cloud> it = CLOUDS.iterator();
            while (it.hasNext()) {
                Cloud c = it.next();
                if (c.dim != sl.dimension()) continue;
                if (--c.left[0] <= 0) {
                    it.remove();
                    continue;
                }
                if (!emit) continue;
                // dense, low, flattened cloud: particles placed one by one (count 0 = exact position, own velocity)
                double grow = Math.min(1.0, (LIFE - c.left[0]) / 30.0 + 0.45);
                double r = c.r * grow;
                int n = c.left[0] > 100 ? 9 : 3;
                for (int i = 0; i < n; i++) {
                    double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * r;
                    double x = c.pos.x + Math.cos(a) * d, z = c.pos.z + Math.sin(a) * d;
                    double y = c.pos.y - 0.8 + sl.random.nextDouble() * r * 0.6;
                    double wx = (sl.random.nextDouble() - 0.5) * 0.01, wz = (sl.random.nextDouble() - 0.5) * 0.01;
                    sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), x, y, z, 0, wx, 0.0015, wz, 1.0);
                }
            }
        }
    }
}
