package ru.aiforge.realwar.soldier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * r27: a summonable AI soldier (commander / tanker / assault / medic).
 *
 * <p>The body is the classic Steve skin; the class uniform is an overlay on top.
 * Boarding is deliberately conservative and fixes the old "AI sits at the PVO that
 * does not exist nearby" behaviour: a soldier boards a piece of equipment only when
 * that equipment is actually within {@link #BOARD_RADIUS} blocks, undamaged, and
 * still has a free seat of a role the class may take.  With no equipment nearby a
 * soldier simply stays on the ground with the group.</p>
 */
public class SoldierEntity extends PathfinderMob {
    public static final double BOARD_RADIUS = 16.0;
    private static final EntityDataAccessor<Integer> CLASS =
            SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);

    /** Scanned for a new boarding target every N ticks while on foot. */
    private static final int SCAN_TICKS = 10;
    private static final int GROUP_TICKS = 20;

    private int wantedVehicleId = -1;
    private int wantedSeat = -1;

    public SoldierEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    public static SoldierEntity create(ServerLevel level, SoldierClass cls, Vec3 pos, float yaw, @Nullable java.util.UUID owner) {
        SoldierEntity s = new SoldierEntity(ModEntities.SOLDIER.get(), level);
        s.setSoldierClass(cls);
        s.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        s.setYRot(yaw);
        s.setCustomName(Component.translatable(cls.nameKey()));
        s.setCustomNameVisible(true);
        s.setPersistenceRequired();
        s.giveClassWeapon();
        level.addFreshEntity(s);
        return s;
    }

    public SoldierClass soldierClass() {
        int i = Mth.clamp(this.entityData.get(CLASS), 0, SoldierClass.values().length - 1);
        return SoldierClass.values()[i];
    }

    public void setSoldierClass(SoldierClass c) {
        this.entityData.set(CLASS, java.util.Arrays.asList(SoldierClass.values()).indexOf(c));
    }

    private void giveClassWeapon() {
        switch (this.soldierClass()) {
            case COMMANDER, ASSAULT -> this.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ModItems.AK12.get(), 1));
            case TANKER -> this.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ModItems.TT33.get(), 1));
            case MEDIC -> this.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ModItems.BANDAGE.get(), 1));
        }
    }

    @Override
    protected void registerGoals() {
        // All behaviour is driven from serverAiStep / think(); vanilla goals stay out.
    }

    @Override
    public boolean canBeLeashed(Player p) {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CLASS, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("rw_class", this.entityData.get(CLASS));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int c = tag.getInt("rw_class");
        if (c >= 0 && c < SoldierClass.values().length) this.entityData.set(CLASS, c);
    }

    /** True when this class is allowed to take a seat of the given role. */
    public static boolean takesRole(SoldierClass c, String role) {
        if (c == SoldierClass.ASSAULT || c == SoldierClass.MEDIC) return false;
        if (c == SoldierClass.TANKER) return role.equals("driver") || role.equals("gunner");
        return true; // commander: any remaining seat
    }

    /**
     * 1.20.1 hook: called from the final Mob#serverAiStep after the navigation
     * tick and before the movement controls, so a moveTo() issued here takes
     * effect the same tick.
     */
    @Override
    protected void customServerAiStep() {
        if (this.isPassenger()) {
            // seated: no own movement, face the vehicle, watch for a destroyed carrier
            this.setDeltaMovement(Vec3.ZERO);
            Entity v = this.getVehicle();
            if (v == null || v.isRemoved() || (v instanceof VehicleEntity ve && (ve.dmg.dead() || ve.seatOf(this) < 0))) {
                this.stopRiding();
                return;
            }
            this.setYRot(v.getYRot());
            return;
        }
        this.think();
    }

    private void think() {
        if (this.level().isClientSide) return;
        SoldierClass c = this.soldierClass();
        if (c == SoldierClass.TANKER || c == SoldierClass.COMMANDER) {
            this.tryBoard();
        } else {
            this.getNavigation().stop();
            this.stayWithGroup();
        }
        if (c == SoldierClass.MEDIC) this.healNearby();
    }

    /**
     * Scan for the nearest undamaged vehicle with a free, role-matching seat within
     * BOARD_RADIUS.  Nothing in range - clear the target and stand with the group.
     */
    private void tryBoard() {
        if (this.tickCount % SCAN_TICKS == 0) {
            VehicleEntity best = null;
            int bestSeat = -1;
            float bestScore = Float.MAX_VALUE;
            for (VehicleEntity v : this.level().getEntitiesOfClass(VehicleEntity.class,
                    this.getBoundingBox().inflate(BOARD_RADIUS), e -> !e.dmg.dead())) {
                for (int s = 0; s < v.spec.seatCount(); ++s) {
                    if (v.seatUuid(s) != null || !takesRole(this.soldierClass(), v.seatRole(s))) continue;
                    Vec3 w = v.seatWorld(s);
                    float horiz = (float) Math.hypot(w.x - this.getX(), w.z - this.getZ());
                    float dy = (float) (w.y - this.getY());
                    if (dy < -0.5f || dy > 1.6f) continue;              // only seats we can actually climb into
                    // prefer a driver seat (tanker) / the closest seat; a tiny bonus keeps scans stable
                    float score = horiz + (v.seatRole(s).equals("driver") && this.soldierClass() == SoldierClass.TANKER ? -4f : 0f);
                    if (score < bestScore) {
                        bestScore = score;
                        best = v;
                        bestSeat = s;
                    }
                }
            }
            this.wantedVehicleId = best == null ? -1 : best.getId();
            this.wantedSeat = bestSeat;
            if (best != null) {
                Vec3 ground = best.getDismountLocationForPassenger(this);
                this.getNavigation().moveTo(ground.x, ground.y, ground.z, 1.2f);
            } else {
                this.getNavigation().stop();
            }
        }

        if (this.wantedVehicleId < 0) return;
        VehicleEntity v = byId(this.level(), this.wantedVehicleId);
        if (v == null || v.dmg.dead() || v.seatUuid(this.wantedSeat) != null || this.wantedSeat >= v.spec.seatCount()) {
            this.wantedVehicleId = -1;
            this.getNavigation().stop();
            return;
        }
        Vec3 w = v.seatWorld(this.wantedSeat);
        double horiz2 = Mth.square(w.x - this.getX()) + Mth.square(w.z - this.getZ());
        double dy = w.y - this.getY();
        if (horiz2 < 2.0 && dy > -0.5 && dy < 1.6) {
            this.getNavigation().stop();
            v.enterSeatFor(this, this.wantedSeat);
            this.wantedVehicleId = -1;
        }
    }

    @Nullable
    private static VehicleEntity byId(Level level, int id) {
        Entity e = level.getEntity(id);
        return e instanceof VehicleEntity v ? v : null;
    }

    /** Infantry (and a commander without a seat) hold position with the nearest unit. */
    private void stayWithGroup() {
        if (this.tickCount % GROUP_TICKS != 0) return;
        Vec3 target = null;
        float best = 9.0f * 9.0f;
        for (SoldierEntity s : this.level().getEntitiesOfClass(SoldierEntity.class,
                this.getBoundingBox().inflate(20.0), e -> e != this && !e.isPassenger() && !e.isDeadOrDying())) {
            float d2 = (float) this.distanceToSqr(s);
            if (d2 < best) {
                best = d2;
                target = s.position();
            }
        }
        if (target == null) {
            for (VehicleEntity v : this.level().getEntitiesOfClass(VehicleEntity.class,
                    this.getBoundingBox().inflate(20.0), e -> !e.dmg.dead())) {
                float d2 = (float) this.distanceToSqr(v.position());
                if (d2 < best) {
                    best = d2;
                    target = v.position();
                }
            }
        }
        if (target == null || best <= 3.0f * 3.0f) {
            this.getNavigation().stop();
        } else {
            this.getNavigation().moveTo(target.x, target.y, target.z, 1.0f);
        }
    }

    private void healNearby() {
        if (this.tickCount % 40 != 0) return;
        for (SoldierEntity s : this.level().getEntitiesOfClass(SoldierEntity.class,
                this.getBoundingBox().inflate(4.0), e -> !e.isDeadOrDying() && e.getHealth() < e.getMaxHealth() - 0.5f)) {
            s.heal(4.0f);
            this.level().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    s.getX(), s.getY() + s.getBbHeight() + 0.2, s.getZ(), 3, 0.1, 0.2, 0.1, 0.01);
            break;
        }
    }
}
