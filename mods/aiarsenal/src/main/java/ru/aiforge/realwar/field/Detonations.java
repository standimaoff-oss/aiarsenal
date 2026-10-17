package ru.aiforge.realwar.field;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.blast.ConventionalBlast;

/**
 * Deferred detonations of field blocks (mines, drums). Queued instead of exploding inline so that chain reactions started from
 * inside a running blast task never re-enter the BlastManager list; each entry fires after its delay on the level tick.
 */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class Detonations {
    private Detonations() {
    }

    /** kg = TNT equivalent, frag = ConventionalBlast fragment factor, fire = incendiary radius (drums), dir/cone = directional mine. */
    public record Job(ServerLevel level, Vec3 pos, double kg, double frag, int fire, Vec3 dir, double coneCos, double coneRange,
                      float coneDamage, int[] delay) {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    public static synchronized void queue(ServerLevel level, Vec3 pos, double kg, double frag, int fire, int delayTicks) {
        JOBS.add(new Job(level, pos, kg, frag, fire, null, 0, 0, 0, new int[]{delayTicks}));
    }

    public static synchronized void directional(ServerLevel level, Vec3 pos, Vec3 dir, double kg, double coneCos, double range, float dmg, int delayTicks) {
        JOBS.add(new Job(level, pos, kg, 0.0, 0, dir.normalize(), coneCos, range, dmg, new int[]{delayTicks}));
    }

    public static synchronized int pending() {
        return JOBS.size();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        List<Job> fire = new ArrayList<>();
        synchronized (Detonations.class) {
            Iterator<Job> it = JOBS.iterator();
            while (it.hasNext()) {
                Job j = it.next();
                if (j.level != sl) continue;
                if (--j.delay[0] <= 0) {
                    fire.add(j);
                    it.remove();
                }
            }
        }
        for (Job j : fire) run(j);
    }

    private static void run(Job j) {
        ServerLevel l = j.level;
        if (!l.isLoaded(BlockPos.containing(j.pos))) return;
        ConventionalBlast.explode(l, j.pos, j.kg, j.frag);
        if (j.dir != null) cone(j);
        if (j.fire > 0) ignite(l, j.pos, j.fire);
    }

    /** MON-50 style sheaf: ~540 steel balls in a 54 deg fan, lethal to ~50 m in reality (capped by range). Line of sight checked. */
    private static void cone(Job j) {
        ServerLevel l = j.level;
        AABB box = new AABB(j.pos, j.pos).inflate(j.coneRange);
        for (Entity e : l.getEntities((Entity) null, box, x -> x instanceof LivingEntity || x instanceof ru.aiforge.realwar.vehicle.VehicleEntity)) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            Vec3 d = c.subtract(j.pos);
            double dist = d.length();
            if (dist < 0.3 || dist > j.coneRange) continue;
            if (d.normalize().dot(j.dir) < j.coneCos) continue;
            if (l.clip(new ClipContext(j.pos, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.BLOCK) continue;
            float dmg = (float) (j.coneDamage * Mth.clamp(1.0 - dist / j.coneRange, 0.15, 1.0));
            e.hurt(l.damageSources().explosion(null, null), dmg);
        }
        for (int i = 0; i < 40; i++) {                      // visible sheaf of dust / sparks along the fan
            double a = (l.random.nextDouble() - 0.5) * Math.acos(j.coneCos) * 2.0;
            double ca = Math.cos(a), sa = Math.sin(a);
            Vec3 v = new Vec3(j.dir.x * ca - j.dir.z * sa, (l.random.nextDouble() - 0.4) * 0.15, j.dir.x * sa + j.dir.z * ca);
            double r = 1.0 + l.random.nextDouble() * Math.min(12.0, j.coneRange);
            l.sendParticles(ParticleTypes.CRIT, j.pos.x + v.x * r, j.pos.y + 0.5 + v.y * r, j.pos.z + v.z * r, 1, 0, 0, 0, 0);
        }
    }

    /** Burning fuel spray: fire on top of solid blocks around the drum. */
    private static void ignite(ServerLevel l, Vec3 c, int r) {
        BlockPos o = BlockPos.containing(c);
        for (int i = 0; i < r * r * 3; i++) {
            BlockPos p = o.offset(l.random.nextInt(2 * r + 1) - r, l.random.nextInt(3) - 1, l.random.nextInt(2 * r + 1) - r);
            if (l.getBlockState(p).isAir() && BaseFireBlock.canBePlacedAt(l, p, net.minecraft.core.Direction.UP)) {
                l.setBlock(p, BaseFireBlock.getState(l, p), 11);
            }
        }
        l.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.5, c.z, 60, 1.2, 0.8, 1.2, 0.08);
        l.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), c.x, c.y + 1.0, c.z, 30, 1.0, 1.2, 1.0, 0.03);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 2.0f, 0.6f);
        if (l.getBlockState(o).isAir() && BaseFireBlock.canBePlacedAt(l, o, net.minecraft.core.Direction.UP)) l.setBlock(o, BaseFireBlock.getState(l, o), 11);
    }
}
