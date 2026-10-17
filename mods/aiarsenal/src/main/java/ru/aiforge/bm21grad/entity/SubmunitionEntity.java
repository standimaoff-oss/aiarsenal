package ru.aiforge.bm21grad.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.bm21grad.fx.ImpactHandler;
import ru.aiforge.bm21grad.registry.ModRegistry;

/** HEAT-fragmentation bomblet released by the 9M218 cluster rocket (falls with a small drag ribbon). */
public class SubmunitionEntity extends Projectile {
    public SubmunitionEntity(EntityType<? extends SubmunitionEntity> type, Level level) {
        super(type, level);
    }

    public static SubmunitionEntity create(Level level, Vec3 pos, Vec3 vel, Entity owner) {
        SubmunitionEntity e = new SubmunitionEntity(ModRegistry.SUBMUNITION.get(), level);
        e.setPos(pos.x, pos.y, pos.z);
        e.setDeltaMovement(vel);
        e.setOwner(owner);
        return e;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement().scale(0.96).add(0, -0.05, 0);
        Vec3 from = position(), to = from.add(v);
        if (level().isClientSide) {
            setPos(to.x, to.y, to.z);
            setDeltaMovement(v);
            if (tickCount % 2 == 0) level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), from.x, from.y, from.z, 0, 0, 0);
            return;
        }
        if (tickCount > 400) {
            discard();
            return;
        }
        HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 end = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level(), this, from, end, getBoundingBox().expandTowards(v).inflate(0.5),
                e -> e.isAlive() && e.isPickable() && !(e instanceof SubmunitionEntity) && !(e instanceof RocketEntity));
        if (eh != null) hit = eh;
        if (hit.getType() != HitResult.Type.MISS) {
            ImpactHandler.bomblet((ServerLevel) level(), hit.getLocation().add(0, 0.2, 0), this, getOwner());
            discard();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(v);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
