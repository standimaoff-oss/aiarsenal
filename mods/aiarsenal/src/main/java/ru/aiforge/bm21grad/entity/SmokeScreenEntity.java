package ru.aiforge.bm21grad.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import ru.aiforge.bm21grad.registry.ModRegistry;

/** Persistent smoke cloud of the 9M43 rocket: dense visible particles; mobs lose their targets, no blindness status. */
public class SmokeScreenEntity extends Entity {
    public static final int LIFETIME = 600;
    public static final float MAX_RADIUS = 10f;

    public SmokeScreenEntity(EntityType<? extends SmokeScreenEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static SmokeScreenEntity create(Level level, double x, double y, double z) {
        SmokeScreenEntity e = new SmokeScreenEntity(ModRegistry.SMOKE_SCREEN.get(), level);
        e.setPos(x, y, z);
        return e;
    }

    public float radius() {
        float grow = Math.min(1f, tickCount / 60f);
        float fade = tickCount > LIFETIME - 100 ? (LIFETIME - tickCount) / 100f : 1f;
        return Math.max(1f, MAX_RADIUS * (0.3f + 0.7f * grow) * (0.6f + 0.4f * fade));
    }

    @Override
    public void tick() {
        super.tick();
        float r = radius();
        if (level().isClientSide) {
            // smoke wall: big billboard puffs (live ~14 s) keep ~300-400 puffs inside the radius
            int n = tickCount < 40 ? 5 : (tickCount % 2 == 0 ? 3 : 2);
            if (tickCount > LIFETIME - 100) n = random.nextInt(3) == 0 ? 1 : 0;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
                level().addAlwaysVisibleParticle(ru.aiforge.bm21grad.registry.ModRegistry.P_SMOKE_WALL.get(), true,
                        getX() + Math.cos(a) * d, getY() + 0.8 + random.nextDouble() * random.nextDouble() * 4.0, getZ() + Math.sin(a) * d,
                        (random.nextDouble() - 0.5) * 0.02, random.nextDouble() * 0.006, (random.nextDouble() - 0.5) * 0.02);
            }
            return;
        }
        if (tickCount > LIFETIME) {
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            AABB box = new AABB(getX() - r, getY() - 2, getZ() - r, getX() + r, getY() + 6, getZ() + r);
            for (LivingEntity le : level().getEntitiesOfClass(LivingEntity.class, box, e -> e.distanceToSqr(this) < r * r + 16)) {
                if (le instanceof Mob m) m.setTarget(null);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        tickCount = t.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("Age", tickCount);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 256 * 256;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity e) {
        return true;
    }
}
