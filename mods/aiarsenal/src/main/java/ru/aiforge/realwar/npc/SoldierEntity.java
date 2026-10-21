package ru.aiforge.realwar.npc;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.Firearms;
import ru.aiforge.realwar.handheld.GunKind;
import ru.aiforge.realwar.handheld.MagazineItem;
import ru.aiforge.realwar.napoleonic.PowderCannonEntity;
import ru.aiforge.realwar.vehicle.VehicleEntity;

/**
 * AI soldier of a historical army.
 *
 * <p>Behaviour is a small server-side state machine instead of vanilla goals, because the squad has to coordinate
 * across three layers: dismounted fire and movement, ground-vehicle crews and aircraft pilots.</p>
 * <ul>
 *   <li>firefight: pick a hostile soldier/player, hold a sensible range, fire in bursts with rank-based dispersion;</li>
 *   <li>squad: follow the leader in a loose wedge, never run further than {@link #LEASH} blocks from him;</li>
 *   <li>crews: walk to their own nation's vehicle/aircraft of the same era, take the operator seat and drive, aim and
 *       shoot it (see {@link VehicleAi} and {@link AircraftAi});</li>
 *   <li>death: drop a partly loaded weapon, a few rounds and the damaged uniform for the player to search.</li>
 * </ul>
 */
public class SoldierEntity extends PathfinderMob {
    public static final double LEASH = 48.0;
    private static final EntityDataAccessor<Integer> D_FACTION = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_ERA = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_RANK = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_SQUAD = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_PROFESSION = SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.INT);

    /** The player who recruited this soldier (villager draft), null for the AI armies. */
    private java.util.UUID ownerUuid;
    private int healCooldown;

    private int leaderId = -1;
    private int squadId;
    private Vec3 anchor = Vec3.ZERO;
    private int assignedVehicle = -1;
    private int assignedCraft = -1;
    private int targetScan;
    private int fireCooldown;
    private int reloadTicks;
    private int magLeft = -1;
    private int burst;
    private int moveCooldown;
    private int meleeCooldown;
    private int cannonCooldown;
    private int strayTicks;

    public SoldierEntity(EntityType<? extends SoldierEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // the squad/vehicle state machine in tick() drives movement, firing and crewing
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(D_FACTION, 0);
        this.entityData.define(D_ERA, 2);
        this.entityData.define(D_RANK, 0);
        this.entityData.define(D_SQUAD, 0);
        this.entityData.define(D_PROFESSION, 0);
    }

    // ------------------------------------------------------------------ data

    public Faction faction() {
        return Faction.values()[Math.floorMod(this.entityData.get(D_FACTION), Faction.values().length)];
    }

    public Era era() {
        return Era.values()[Math.floorMod(this.entityData.get(D_ERA), Era.values().length)];
    }

    public Rank rank() {
        return Rank.values()[Math.floorMod(this.entityData.get(D_RANK), Rank.values().length)];
    }

    public int squadId() {
        return this.entityData.get(D_SQUAD);
    }

    public Profession profession() {
        return Profession.values()[Math.floorMod(this.entityData.get(D_PROFESSION), Profession.values().length)];
    }

    public void setProfession(Profession p) {
        this.entityData.set(D_PROFESSION, p.ordinal());
    }

    @javax.annotation.Nullable
    public java.util.UUID owner() {
        return this.ownerUuid;
    }

    public void setOwner(java.util.UUID uuid) {
        this.ownerUuid = uuid;
    }

    public int leaderId() {
        return this.leaderId;
    }

    public Vec3 squadAnchor() {
        return this.anchor;
    }

    public int assignedVehicle() {
        return this.assignedVehicle;
    }

    public int assignedCraft() {
        return this.assignedCraft;
    }

    public void setAssignedVehicle(int id) {
        this.assignedVehicle = id;
    }

    public void setAssignedCraft(int id) {
        this.assignedCraft = id;
    }

    public void setSquad(int squadId, int leaderId, Vec3 anchor) {
        this.squadId = squadId;
        this.leaderId = leaderId;
        this.anchor = anchor == null ? Vec3.ZERO : anchor;
        this.entityData.set(D_SQUAD, squadId);
    }

    public void initWith(Faction f, Era era, Rank rank) {
        this.entityData.set(D_FACTION, f.ordinal());
        this.entityData.set(D_ERA, era.ordinal());
        this.entityData.set(D_RANK, rank.ordinal());
        NpcGear.equip(this);
        double hp = switch (rank) {
            case PRIVATE -> 20.0;
            case SERGEANT -> 24.0;
            case OFFICER -> 24.0;
            case CREW -> 26.0;
            case PILOT -> 22.0;
            case COMMANDER -> 26.0;
        };
        if (this.getAttribute(Attributes.MAX_HEALTH) != null) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        }
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(era == Era.NAPOLEONIC ? 5.0 : 4.0);
        }
        this.setHealth((float) hp);
        this.setCustomName(net.minecraft.network.chat.Component.translatable("npc.aiarsenal.name",
                net.minecraft.network.chat.Component.translatable(f.langKey()),
                net.minecraft.network.chat.Component.translatable(era.langKey()),
                net.minecraft.network.chat.Component.translatable(rank.langKey())));
        this.setCustomNameVisible(false);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof SoldierEntity s) return s.faction() == this.faction();
        return super.isAlliedTo(other);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (src instanceof SoldierEntity s && s != this && s.faction() == this.faction()) return false;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || !this.isAlive()) return;
        if (this.fireCooldown > 0) this.fireCooldown--;
        if (this.meleeCooldown > 0) this.meleeCooldown--;
        if (this.cannonCooldown > 0) this.cannonCooldown--;
        if (this.moveCooldown > 0) this.moveCooldown--;
        if (this.reloadTicks > 0) {
            if (--this.reloadTicks == 0) {
                GunKind k = heldGun();
                if (k != null) this.magLeft = k.cap;
            }
            return;
        }
        if (this.isPassenger()) {
            crewTick();
            return;
        }
        if (this.rank() == Rank.COMMANDER && CommanderAi.tick(this)) return;
        if (this.profession() == Profession.MEDIC) medicTick();
        if (this.assignedVehicle >= 0 && tryMountVehicle()) return;
        if (this.assignedCraft >= 0 && tryMountCraft()) return;
        if (this.rank() == Rank.CREW) {
            if (this.assignedVehicle < 0 && this.tickCount % 40 == 0) claimFreeVehicle();
            PowderCannonEntity cannon = nearbyCannon();
            if (cannon != null && this.assignedVehicle < 0 && operateCannon(cannon)) return;
        } else if (this.rank() == Rank.PILOT && this.assignedCraft < 0 && this.tickCount % 40 == 0) {
            claimFreeAircraft();
        }
        fightOrFollow();
    }

    // ------------------------------------------------------------------ vehicle / aircraft crewing

    private void crewTick() {
        Entity vehicle = this.getVehicle();
        if (vehicle == null) return;
        if (vehicle.isRemoved()) {                       // wreck removed under us: back to the rifle
            this.stopRiding();
            return;
        }
        if (vehicle instanceof VehicleEntity v) {
            if (v.dmg.dead()) {                          // knocked out: bail out and fight on foot
                VehicleAi.release(v);
                this.stopRiding();
                return;
            }
            if (v.seatOf(this) == 0) {
                v.aiStartEngine();
                VehicleAi.tick(v, this);
            } else if (this.rank() == Rank.COMMANDER && this.tickCount % 20 == 0) {
                VehicleAi.commanderScan(v, this);   // the commander rides with his eyes out, not on the wheel
            }
        } else if (vehicle instanceof CraftEntity c) {
            if (c.dmg.dead()) {
                AircraftAi.release(c);
                this.stopRiding();
                return;
            }
            if (c.seatOf(this) == 0) AircraftAi.fly(c, this);
        }
    }

    private boolean tryMountVehicle() {
        ServerLevel sl = (ServerLevel) this.level();
        Entity e = sl.getEntity(this.assignedVehicle);
        if (!(e instanceof VehicleEntity v) || v.isRemoved() || v.dmg.dead()
                || !NpcGear.canUseVehicle(this.faction(), this.era(), v.kind)) {
            this.assignedVehicle = -1;
            return false;
        }
        double d = this.distanceTo(v);
        if (d > 3.6) {
            if (this.moveCooldown <= 0) {
                this.getNavigation().moveTo(v, 1.15);
                this.moveCooldown = 10;
            }
            return true;
        }
        if (v.seatUuid(0) != null) {                 // the driver's seat is taken: fight on foot
            this.assignedVehicle = -1;
            return false;
        }
        if (v.aiEnterSeat(this, 0)) this.assignedVehicle = -1;
        return true;
    }

    /** An unclaimed vehicle of this army and period nearby: the crew walks to it. */
    private void claimFreeVehicle() {
        List<VehicleEntity> list = this.level().getEntitiesOfClass(VehicleEntity.class,
                this.getBoundingBox().inflate(28.0), v -> !v.isRemoved() && !v.dmg.dead()
                        && NpcGear.canUseVehicle(this.faction(), this.era(), v.kind) && v.seatUuid(0) == null);
        if (list.isEmpty()) return;
        // the AA gunner wants the flak gun, the artilleryman a piece with a real gun on it
        list.sort((a, b) -> Integer.compare(vehicleScore(b), vehicleScore(a)));
        this.assignedVehicle = list.get(0).getId();
    }

    private int vehicleScore(VehicleEntity v) {
        return switch (this.profession()) {
            case AA_GUNNER -> v.kind.isFlak() ? 10 : 0;
            case ARTILLERY -> v instanceof ru.aiforge.realwar.vehicle.LauncherVehicle ? 10 : 2;
            case TANK_CREW -> v instanceof ru.aiforge.realwar.vehicle.GunVehicle ? 10 : 2;
            default -> 1;
        };
    }

    private void claimFreeAircraft() {
        List<CraftEntity> list = this.level().getEntitiesOfClass(CraftEntity.class,
                this.getBoundingBox().inflate(32.0), c -> !c.isRemoved() && c.kind.isAir() && c.seatUuid(0) == null
                        && NpcGear.canUseCraft(this.faction(), this.era(), c.kind));
        if (!list.isEmpty()) this.assignedCraft = list.get(0).getId();
    }

    private boolean tryMountCraft() {
        ServerLevel sl = (ServerLevel) this.level();
        Entity e = sl.getEntity(this.assignedCraft);
        if (!(e instanceof CraftEntity c) || c.isRemoved()
                || !NpcGear.canUseCraft(this.faction(), this.era(), c.kind)) {
            this.assignedCraft = -1;
            return false;
        }
        if (c.getY() > this.getY() + 6.0) {      // airborne and out of reach; fight on foot instead
            this.assignedCraft = -1;
            return false;
        }
        double d = this.distanceTo(c);
        if (d > 4.5) {
            if (this.moveCooldown <= 0) {
                this.getNavigation().moveTo(c, 1.2);
                this.moveCooldown = 10;
            }
            return true;
        }
        if (c.seatUuid(0) != null) {
            this.assignedCraft = -1;
            return false;
        }
        if (c.aiEnterSeat(this, 0)) this.assignedCraft = -1;
        return true;
    }

    /** A recruited medic: patches up wounded allies and his employer, shoots only when cornered. */
    private void medicTick() {
        if (this.healCooldown > 0) {
            this.healCooldown--;
            return;
        }
        this.healCooldown = 40;
        java.util.List<LivingEntity> allies = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(7.0), e -> e != this && e.isAlive()
                        && e.getHealth() < e.getMaxHealth() && isAllyPatient(e));
        if (allies.isEmpty()) return;
        LivingEntity patient = allies.get(0);
        if (this.distanceTo(patient) > 2.6) {
            this.getNavigation().moveTo(patient, 1.1);
        } else {
            this.getNavigation().stop();
            this.getLookControl().setLookAt(patient, 30f, 30f);
            patient.heal(3.0f);
            this.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    private boolean isAllyPatient(LivingEntity e) {
        if (e instanceof SoldierEntity s2) return s2.faction() == this.faction();
        if (e instanceof Player pl) return this.ownerUuid != null && this.ownerUuid.equals(pl.getUUID());
        return false;
    }

    // ------------------------------------------------------------------ infantry combat

    @Nullable
    private GunKind heldGun() {
        ItemStack st = this.getMainHandItem();
        return st.getItem() instanceof FirearmItem fa ? fa.kind : null;
    }

    private void fightOrFollow() {
        // a leaderless soldier holds the spot he was spawned on; an anchor handed to him (squad / gametest) wins
        if (this.leaderId < 0 && this.anchor.equals(Vec3.ZERO)) this.anchor = this.position();
        if (this.tickCount % 10 == 0) refreshTarget();
        LivingEntity target = this.getTarget();
        if (target != null && (!target.isAlive() || this.distanceToSqr(target) > 80.0 * 80.0)) {
            target = null;
            this.setTarget(null);
        }
        if (target != null) {
            double d = this.distanceTo(target);
            if (d < 3.0 && this.meleeCooldown <= 0) {
                this.getLookControl().setLookAt(target, 30f, 30f);
                this.doHurtTarget(target);
                this.swing(InteractionHand.MAIN_HAND, true);
                this.meleeCooldown = 20;
                return;
            }
            if (this.hasLineOfSight(target)) {
                Vec3 anchorPoint = leaderPoint();
                boolean farFromSquad = NpcGear.horizontalDistance(this, anchorPoint) > LEASH && d > 18.0;
                if (farFromSquad) {
                    this.setTarget(null);
                    followSquad(anchorPoint);
                    return;
                }
                this.getLookControl().setLookAt(target, 30f, 30f);
                double preferred = this.rank() == Rank.COMMANDER ? (this.era() == Era.NAPOLEONIC ? 42.0 : 58.0)
                        : this.profession() == Profession.MEDIC ? 18.0
                        : this.era() == Era.NAPOLEONIC ? 30.0 : this.era() == Era.WW2 ? 24.0 : 26.0;
                if (d > preferred + 6.0) {
                    this.moveToward(target.position(), 1.05);
                } else if (d < preferred - 8.0 && this.era() != Era.NAPOLEONIC && this.moveCooldown <= 0) {
                    Vec3 away = this.position().subtract(target.position()).normalize().scale(3.0);
                    this.getNavigation().moveTo(this.getX() + away.x, this.getY(), this.getZ() + away.z, 1.0);
                    this.moveCooldown = 12;
                } else if (this.moveCooldown <= 0) {
                    this.getNavigation().stop();
                    this.moveCooldown = 5;
                }
                tryShoot(target);
                return;
            }
        }
        this.setTarget(null);
        followSquad(leaderPoint());
    }

    private void refreshTarget() {
        if (this.tickCount < this.targetScan) return;
        this.targetScan = this.tickCount + 8;
        LivingEntity current = this.getTarget();
        if (current != null && current.isAlive() && this.distanceToSqr(current) < 70.0 * 70.0 && this.hasLineOfSight(current)) return;
        double range = this.era() == Era.NAPOLEONIC ? 55.0 : this.era() == Era.WW2 ? 60.0 : 62.0;
        List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(range), e -> e != this && e.isAlive() && isEnemy(e));
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : list) {
            double d = this.distanceToSqr(e);
            if (d >= bestD) continue;
            if (!this.hasLineOfSight(e)) continue;
            best = e;
            bestD = d;
        }
        if (best != null) this.setTarget(best);
    }

    private boolean isEnemy(Entity e) {
        if (e instanceof Player p) {
            if (this.ownerUuid != null && this.ownerUuid.equals(p.getUUID())) return false;
            if (this.faction() == Faction.PLAYER) return false;
            return !p.isCreative() && !p.isSpectator() && p.isAlive();
        }
        if (e instanceof SoldierEntity s) return s.faction().hostileTo(this.faction());
        return false;
    }

    private Vec3 leaderPoint() {
        if (this.ownerUuid != null && this.level() instanceof ServerLevel sl) {
            net.minecraft.server.level.ServerPlayer employer = sl.getServer().getPlayerList().getPlayer(this.ownerUuid);
            if (employer != null && employer.isAlive() && employer.level() == this.level()) {
                return employer.position();          // a recruit stays with the man who paid him
            }
        }
        if (this.leaderId >= 0 && this.level() instanceof ServerLevel sl) {
            Entity leader = sl.getEntity(this.leaderId);
            if (leader != null && leader.isAlive()) return leader.position();
        }
        return this.anchor;
    }

    private void followSquad(Vec3 point) {
        if (point == null || point.equals(Vec3.ZERO)) return;
        int slot = Math.floorMod(this.getId(), 7);
        Vec3 goal;
        if (this.era() == Era.NAPOLEONIC && this.leaderId >= 0 && this.level() instanceof ServerLevel sl2) {
            // 1812: two dressed ranks behind the commander, shoulder to shoulder, at marching pace
            Entity leader = sl2.getEntity(this.leaderId);
            if (leader != null) {
                int file = slot % 4;
                int row = slot / 4;
                double yaw = Math.toRadians(leader.getYRot());
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);        // leader's forward
                double rx = Math.cos(yaw), rz = Math.sin(yaw);         // leader's right
                double back = 2.1 + row * 1.9;
                double side = (file - 1.5) * 1.7;
                goal = point.add(-fx * back + rx * side, 0, -fz * back + rz * side);
                double d0 = NpcGear.horizontalDistance(this, point);
                if (d0 > 3.2 && this.moveCooldown <= 0) {
                    this.getNavigation().moveTo(goal.x, goal.y, goal.z, 0.92);   // the march: nobody runs
                    this.setSprinting(false);
                    this.moveCooldown = 10;
                } else if (d0 <= 3.2) {
                    this.getNavigation().stop();
                    this.getLookControl().setLookAt(leader, 20f, 20f);
                }
                return;
            }
        }
        double angle = Math.toRadians(slot * 51.4);
        double radius = slot == 0 ? 0.0 : 2.8 + (slot % 3) * 1.1;
        goal = point.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        double d = NpcGear.horizontalDistance(this, point);
        this.strayTicks = d > LEASH ? this.strayTicks + 1 : 0;
        if (d > 3.2 && this.moveCooldown <= 0) {
            this.getNavigation().moveTo(goal.x, goal.y, goal.z, d > 20.0 ? 1.35 : 0.95);
            this.moveCooldown = 10;
            this.setSprinting(d > 16.0);
        } else if (d <= 3.2) {
            this.getNavigation().stop();
            this.setSprinting(false);
            if (this.leaderId >= 0 && this.level() instanceof ServerLevel sl) {
                Entity leader = sl.getEntity(this.leaderId);
                if (leader != null) this.getLookControl().setLookAt(leader, 20f, 20f);
            }
        }
        if (this.strayTicks > 40) this.strayTicks = 0;
    }

    private void moveToward(Vec3 p, double speed) {
        if (this.moveCooldown > 0) return;
        this.getNavigation().moveTo(p.x, p.y, p.z, speed);
        this.setSprinting(true);
        this.moveCooldown = 10;
    }

    /** Fires the firearm in hand with lead, gravity-independent bullet ballistics and rank-based dispersion. */
    private void tryShoot(LivingEntity target) {
        GunKind k = heldGun();
        if (k == null || this.fireCooldown > 0) return;
        if (this.magLeft < 0) this.magLeft = k.cap;
        if (this.magLeft <= 0) {
            this.reloadTicks = Math.max(20, k.reloadEmpty);
            this.fireCooldown = this.reloadTicks;
            return;
        }
        Vec3 from = NpcGear.eye(this);
        Vec3 aim = NpcGear.aimPoint(from, target, this.rank().skill, this.random);
        Vec3 dir = aim.subtract(from).normalize();
        double spread = k.hipSpread * (1.05 + (1.0 - this.rank().skill) * 2.4);
        if (this.era() == Era.NAPOLEONIC) spread *= 1.35;
        dir = NpcGear.jitter(dir, spread, this.random);
        Firearms.npcShot((ServerLevel) this.level(), this, from, dir, k);
        this.magLeft--;
        this.swing(InteractionHand.MAIN_HAND, true);
        if (k.manual() || k.rpm < 120) {
            this.fireCooldown = Math.max(24, k.reload / (k.clipFed() ? 1 : 2));
        } else {
            this.fireCooldown = Math.max(3, (int) Math.round(1200.0 / Math.max(120, k.rpm)));
            if (++this.burst >= 3 + this.random.nextInt(4)) {
                this.burst = 0;
                this.fireCooldown += 12 + this.random.nextInt(18);
            }
        }
        if (this.rank() == Rank.PRIVATE && this.random.nextFloat() < 0.06f) {
            this.fireCooldown += 10;
        }
    }

    // ------------------------------------------------------------------ Napoleonic powder gun

    @Nullable
    private PowderCannonEntity nearbyCannon() {
        List<PowderCannonEntity> list = this.level().getEntitiesOfClass(PowderCannonEntity.class,
                this.getBoundingBox().inflate(36.0), c -> c.isAlive() && this.faction().ownsCannon(c.french()));
        return list.isEmpty() ? null : list.get(0);
    }

    /** Stands at the gun, lays it and serves the piece while hostiles are within range. */
    private boolean operateCannon(PowderCannonEntity cannon) {
        if (this.isPassenger() || this.era() != Era.NAPOLEONIC) return false;
        double d = this.distanceTo(cannon);
        if (d > 3.0) {
            if (this.moveCooldown <= 0) {
                this.getNavigation().moveTo(cannon, 1.05);
                this.moveCooldown = 12;
            }
            return true;
        }
        this.getNavigation().stop();
        LivingEntity target = this.getTarget();
        if (target == null && this.tickCount % 20 == 0) {
            List<LivingEntity> list = this.level().getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(110.0), e -> e != this && e.isAlive() && isEnemy(e));
            if (!list.isEmpty()) this.setTarget(list.get(0));
            target = this.getTarget();
        }
        if (target == null) return true;
        this.getLookControl().setLookAt(target, 30f, 30f);
        cannon.aiAim(target.position().add(0, target.getBbHeight() * 0.4, 0),
                this.rank().skill, this.random);
        if (cannon.loading() < 0) {
            cannon.aiLoad();
        } else if (cannon.loading() == 0 && this.cannonCooldown <= 0) {
            cannon.aiFire(this);
            this.cannonCooldown = 100 + this.random.nextInt(80);
        }
        return true;
    }

    // ------------------------------------------------------------------ loot

    @Override
    public void die(DamageSource source) {
        Entity riding = this.getVehicle();
        if (riding instanceof VehicleEntity v) VehicleAi.release(v);
        else if (riding instanceof CraftEntity c) AircraftAi.release(c);
        super.die(source);
    }

    @Override
    protected void dropEquipment() {
        // the searchable loot below replaces the vanilla random equipment drops
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!(this.level() instanceof ServerLevel sl)) return;
        GunKind k = heldGun();
        ItemStack gun = this.getMainHandItem().copy();
        if (!gun.isEmpty()) {
            gun.setCount(1);
            if (k != null) {
                int left = 1 + this.random.nextInt(Math.max(2, Math.min(k.cap, 4)));
                FirearmItem.setAmmo(gun, left);
            }
            this.spawnAtLocation(gun, 0.6f);
            if (k != null) {
                ItemStack ammo = new ItemStack(NpcGear.ammoItem(k));
                if (!ammo.isEmpty()) {
                    if (ammo.getItem() instanceof MagazineItem mi) {
                        ammo = MagazineItem.withRounds(mi, 1 + this.random.nextInt(Math.max(2, mi.kind.cap / 3)));
                    } else {
                        ammo.setCount(1 + this.random.nextInt(2));
                    }
                    this.spawnAtLocation(ammo, 0.4f);
                }
            }
        }
        ItemStack sabre = this.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        if (!sabre.isEmpty() && this.random.nextFloat() < 0.8f) this.spawnAtLocation(sabre, 0.3f);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = this.getItemBySlot(slot).copy();
            if (piece.isEmpty()) continue;
            if (piece.isDamageableItem()) {
                piece.setDamageValue(Math.min(piece.getMaxDamage() - 1,
                        (int) (piece.getMaxDamage() * (0.35 + this.random.nextDouble() * 0.45))));
            }
            this.spawnAtLocation(piece, 0.4f);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) this.setItemSlot(slot, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("npcFaction", this.faction().ordinal());
        tag.putInt("npcEra", this.era().ordinal());
        tag.putInt("npcRank", this.rank().ordinal());
        tag.putInt("npcSquad", this.squadId);
        tag.putInt("npcLeader", this.leaderId);
        tag.putInt("npcVehicle", this.assignedVehicle);
        tag.putInt("npcCraft", this.assignedCraft);
        tag.putDouble("npcAnchorX", this.anchor.x);
        tag.putDouble("npcAnchorY", this.anchor.y);
        tag.putDouble("npcAnchorZ", this.anchor.z);
        tag.putInt("npcMag", this.magLeft);
        tag.putInt("npcProfession", this.profession().ordinal());
        if (this.ownerUuid != null) tag.putUUID("npcOwner", this.ownerUuid);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(D_FACTION, tag.getInt("npcFaction"));
        this.entityData.set(D_ERA, tag.getInt("npcEra"));
        this.entityData.set(D_RANK, tag.getInt("npcRank"));
        this.squadId = tag.getInt("npcSquad");
        this.entityData.set(D_SQUAD, this.squadId);
        this.leaderId = tag.getInt("npcLeader");
        this.assignedVehicle = tag.contains("npcVehicle") ? tag.getInt("npcVehicle") : -1;
        this.assignedCraft = tag.contains("npcCraft") ? tag.getInt("npcCraft") : -1;
        this.anchor = new Vec3(tag.getDouble("npcAnchorX"), tag.getDouble("npcAnchorY"), tag.getDouble("npcAnchorZ"));
        this.magLeft = tag.contains("npcMag") ? tag.getInt("npcMag") : -1;
        if (tag.contains("npcProfession")) this.entityData.set(D_PROFESSION, tag.getInt("npcProfession"));
        this.ownerUuid = tag.hasUUID("npcOwner") ? tag.getUUID("npcOwner") : null;
        this.entityData.set(D_SQUAD, this.squadId);
    }

    /** Spawn hook used by the spawn eggs / command: the faction data is set before the entity joins the world. */
    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data, tag);
        if (tag == null || !tag.contains("npcFaction")) {
            this.initWith(SoldierSpawner.factionOf(this.getType()), SoldierSpawner.eraOf(this.getType()), this.rank());
        }
        return out;
    }
}
