package ru.aiforge.realwar.ordnance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * Unpowered gun projectile (tank / IFV / howitzer). Re-uses the RocketEntity point-mass flight (Ballistics.Flight with
 * mp = 0: only gravity + drag), the chunk tickets and the swept block/entity collision.
 * <ul>
 *   <li>HE (tntKg &gt; 0): inherited detonate -> ConventionalBlast (crater + fragments).</li>
 *   <li>AP / APFSDS (tntKg == 0): kinetic. Vehicles take a heavy armour-piercing hit (energy based), light structures (glass, wood, leaves, wool, fences) along the
 *   path are punched through (up to a budget), hard blocks stop the rod with sparks.</li>
 * </ul>
 * Not an air target and not a radar contact (see AirTargets / RadarNet).
 */
public class ShellEntity extends RocketEntity {
    private int punched;

    public ShellEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public static ShellEntity fire(ServerLevel level, Ballistics.Profile prof, Vec3 muzzle, Vec3 dir, int sourceVehicleId) {
        ShellEntity e = new ShellEntity(ModEntities.SHELL.get(), level);
        e.init(prof, muzzle, dir, sourceVehicleId);
        level.addFreshEntity(e);
        return e;
    }

    /** Muzzle energy scaled to Minecraft hit points: 125 mm APFSDS ~ 150, 30 mm would be ~10. */
    public float kineticDamage() {
        Ballistics.Profile p = this.profile();
        double v = this.flight() != null ? this.flight().speed() : p.ejectSpeed;
        double ej = 0.5 * p.m0 * v * v;         // J  (3BM60: ~6.7 MJ)
        return (float) Math.min(220.0, ej / 45000.0);
    }

    /** r20: projectile data for the armour model and the shooter's hit camera. */
    ru.aiforge.realwar.damage.HitContext.Shot shot() {
        Ballistics.Profile p = this.profile();
        double v = this.flight() != null ? this.flight().speed() : p.ejectSpeed;
        return new ru.aiforge.realwar.damage.HitContext.Shot(this.sourceVehicle, ru.aiforge.realwar.damage.Armour.Ammo.of(p), v, this.origin);
    }

    @Override
    protected double maxFlightTime() {
        return 160.0;
    }

    @Override
    protected void impact(ServerLevel sl, Vec3 pos, Entity direct) {
        Ballistics.Profile p = this.profile();
        if (!p.kinetic()) {
            if (direct instanceof VehicleEntity || direct instanceof ru.aiforge.realwar.craft.CraftEntity) {
                ru.aiforge.realwar.damage.HitContext.hurt(direct, ModDamage.source(sl, ModDamage.ORDNANCE), (float) (p.tntKg < 0.1 ? 9.0 : 20.0 + p.tntKg * 6.0), pos, this.lastDir, ru.aiforge.realwar.damage.HitContext.Kind.HE, Math.max(0.3, p.tntKg), this.shot());
            }
            super.impact(sl, pos, direct);
            return;
        }
        if (direct != null) {
            float dmg = this.kineticDamage();
            if (!(direct instanceof VehicleEntity)) dmg = Math.min(dmg, 60.0f);
            direct.invulnerableTime = 0;
            ru.aiforge.realwar.damage.HitContext.hurt(direct, ModDamage.source(sl, ModDamage.ORDNANCE), dmg, pos, this.lastDir, ru.aiforge.realwar.damage.HitContext.Kind.AP, this.kineticDamage(), this.shot());
            if (direct instanceof LivingEntity le) le.knockback(1.2, -this.lastDir.x, -this.lastDir.z);
            sl.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.0);
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), pos.x, pos.y, pos.z, 6, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 3.0f, 0.5f);
            this.exploded = true;
            this.discard();
            return;
        }
        // block: punch light structures (glass, panes, leaves, wood, wool, fences) and keep flying; earth, sand,
        // sandbags, stone... stop the round (so the impact reticle, which stops at the first such block, is right)
        BlockPos bp = BlockPos.containing(pos.add(this.lastDir.scale(0.05)));
        BlockState st = sl.getBlockState(bp);
        float hard = st.getDestroySpeed(sl, bp);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.x, pos.y, pos.z, 18, 0.25, 0.25, 0.25, 0.15);
        if (hard >= 0.0f && hard < 3.5f && punchable(st) && this.punched < 6 && net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(sl, this)) {
            sl.destroyBlock(bp, false, this);
            ++this.punched;
            return;   // RocketEntity keeps integrating from the current flight state
        }
        sl.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 4, 0.1, 0.1, 0.1, 0.0);
        sl.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 2.0f, 0.7f);
        this.exploded = true;
        this.discard();
    }

    /** Light structures a kinetic round passes through (also used by the client impact reticle). Natural ground and
     *  sandbags stop it. */
    public static boolean punchable(BlockState st) {
        return st.is(net.minecraftforge.common.Tags.Blocks.GLASS) || st.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES)
                || st.is(net.minecraft.tags.BlockTags.LEAVES) || st.is(net.minecraft.tags.BlockTags.LOGS)
                || st.is(net.minecraft.tags.BlockTags.PLANKS) || st.is(net.minecraft.tags.BlockTags.WOOL)
                || st.is(net.minecraft.tags.BlockTags.FENCES) || st.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                || st.is(net.minecraft.tags.BlockTags.WOODEN_DOORS) || st.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS)
                || st.is(net.minecraft.tags.BlockTags.WOODEN_SLABS) || st.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS);
    }
}
