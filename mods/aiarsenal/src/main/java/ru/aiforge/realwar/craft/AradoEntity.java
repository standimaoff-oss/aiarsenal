package ru.aiforge.realwar.craft;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.ordnance.BulletManager;
import ru.aiforge.realwar.registry.ModSounds;

/**
 * Arado Ar 196 A-3 shipboard floatplane (reply 10), flown by its own crew (AI) from the Bismarck's catapult.
 * <p>Flight model: point mass with speed, heading and climb; turns are coordinated - the bank angle (max 45 deg,
 * rolled at 60 deg/s) sets the turn rate omega = g * tan(bank) / V - so it orbits at a real radius (~200 m at
 * 45 m/s). 1:1 metres = blocks (aircraft are not Froude-scaled).
 * <p>Mission: climb out, fly to the ship's aim point and orbit it as spotter (the ship's dispersion halves), dive on
 * hostile mobs there with the two 20 mm MG FF and its two SC 50 bombs, return after ~5 min / weapons spent, land on
 * the water beside the ship and be craned back aboard. Can be shot down (it is an air target for every flak / SAM in
 * the mod, except its own ship's).
 */
public class AradoEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(AradoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(AradoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(AradoEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> HP = SynchedEntityData.defineId(AradoEntity.class, EntityDataSerializers.FLOAT);

    public static final int S_LAUNCH = 0, S_SPOT = 1, S_ATTACK = 2, S_RETURN = 3, S_WATER = 4, S_CRASH = 5;
    private static final double G = 9.81, DT = 0.05;
    public static final double V_CRUISE = 45.0, V_MAX = 86.0, V_STALL = 30.0, ORBIT_ALT = 160.0;

    /** speed m/s, heading (MC yaw deg), flight path angle deg - server state. */
    private double v, gamma;
    private float rollC;
    private int timer;
    private int fuel = 6000;
    private int bombs = 2, mg = 160;
    private int mgCd, bombCd;
    @Nullable private Entity target;
    @Nullable private Vec3 home;
    private final List<double[]> falling = new ArrayList<>();
    public float propAngle, propAngleO, rollO;

    public AradoEntity(EntityType<? extends AradoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Nullable
    static AradoEntity launch(ServerLevel sl, BattleshipEntity ship, Vec3 at, Vec3 dir) {
        AradoEntity a = CraftRegistry.ARADO.get().create(sl);
        if (a == null) return null;
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        a.moveTo(at.x, at.y, at.z, yaw, 0f);
        a.entityData.set(OWNER, ship.getId());
        a.entityData.set(STATE, (byte) S_LAUNCH);
        // Kreiselkatapult: ~ 140 km/h at the end of the run, plus the ship's own speed
        a.v = 38.0 + ship.vel.horizontalDistance() * 0.5;
        a.gamma = 4.0;
        sl.addFreshEntity(a);
        return a;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER, -1);
        this.entityData.define(ROLL, 0f);
        this.entityData.define(STATE, (byte) S_SPOT);
        this.entityData.define(HP, 40f);
    }

    public int ownerId() { return this.entityData.get(OWNER); }
    public int state() { return this.entityData.get(STATE); }
    public float roll() { return this.entityData.get(ROLL); }
    public float hp() { return this.entityData.get(HP); }
    public boolean airborne() { int s = this.state(); return s != S_WATER && this.isAlive(); }
    public double speed() { return this.v; }
    /** Client: smoothed bank angle (deg, + = right wing down). */
    public float rollC() { return this.rollC; }

    @Nullable
    public BattleshipEntity owner() {
        Entity e = this.level().getEntity(this.ownerId());
        return e instanceof BattleshipEntity b && b.isAlive() ? b : null;
    }

    private void setState(int s) { this.entityData.set(STATE, (byte) s); }

    // ------------------------------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        this.propAngleO = this.propAngle;
        this.rollO = this.rollC;
        if (this.level().isClientSide) {
            this.rollC += Mth.wrapDegrees(this.roll() - this.rollC) * 0.5f;
            this.propAngle = (this.propAngle + (this.state() == S_WATER ? 25f : 61f)) % 360f;
            if (this.state() == S_CRASH && this.tickCount % 2 == 0)
                this.level().addParticle(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), this.getX(), this.getY() + 1.5, this.getZ(), 0, 0.05, 0);
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        this.timer++;
        if (this.mgCd > 0) this.mgCd--;
        if (this.bombCd > 0) this.bombCd--;
        if (--this.fuel == 0 && this.state() <= S_ATTACK) this.setState(S_RETURN);
        this.tickBombs(sl);
        BattleshipEntity ship = this.owner();
        switch (this.state()) {
            case S_LAUNCH -> {
                // accelerate off the catapult, keep the launch heading, climb gently
                this.v = Math.min(V_CRUISE + 5, this.v + 3.0 * DT * 20 * DT);
                this.fly(this.getYRot(), this.getY() + 60, 0, V_CRUISE + 5);
                if (this.timer > 70) this.setState(S_SPOT);
            }
            case S_SPOT -> {
                Vec3 spot = this.spot(ship);
                double waterY = this.waterLevel(ship);
                this.orbit(spot, Math.max(spot.y, waterY) + ORBIT_ALT, 200.0, V_CRUISE);
                if (this.timer % 20 == 0 && (this.bombs > 0 || this.mg > 0)) {
                    this.target = this.findTarget(sl, spot);
                    if (this.target != null) this.setState(S_ATTACK);
                }
                if (ship == null && this.timer > 400) this.setState(S_RETURN);
            }
            case S_ATTACK -> this.attack(sl, ship);
            case S_RETURN -> this.returnToShip(ship);
            case S_WATER -> this.onWater(ship);
            case S_CRASH -> this.crashFall(sl);
            default -> { }
        }
        // terrain: flying into a block = crash
        if (this.state() != S_WATER && this.state() != S_CRASH) {
            BlockPos bp = this.blockPosition();
            if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) this.explodeAndDie(sl, 8.0);
        }
    }

    /** Point the ship's gunnery officer wants observed (the director aim point) or the ship itself. */
    private Vec3 spot(@Nullable BattleshipEntity ship) {
        if (ship != null && ship.aimPoint != null) return ship.aimPoint;
        if (ship != null) return ship.position();
        if (this.home == null) this.home = this.position();
        return this.home;
    }

    private double waterLevel(@Nullable BattleshipEntity ship) {
        if (ship != null) return ship.getY() + ship.kind.water.draft();
        BlockPos.MutableBlockPos mp = this.blockPosition().mutable();
        for (int i = 0; i < 320 && mp.getY() > this.level().getMinBuildHeight(); i++, mp.move(0, -1, 0)) {
            if (this.level().getFluidState(mp).is(FluidTags.WATER)) return mp.getY() + 0.9;
            if (!this.level().getBlockState(mp).isAir()) return mp.getY() + 1.0;
        }
        return this.level().getSeaLevel();
    }

    /**
     * One step of coordinated flight towards heading hdg (MC yaw), altitude alt, speed vt.
     * bankMax limits the turn (deg); climb limited to +8 / -dive m/s.
     */
    private void fly(float hdg, double alt, double bankMax, double vt) {
        float err = Mth.wrapDegrees(hdg - this.getYRot());
        float bankT = (float) Mth.clamp(err * 1.5, -bankMax, bankMax);
        this.rollC += Mth.clamp(bankT - this.rollC, -3f, 3f);                 // 60 deg/s roll rate
        double omega = G * Math.tan(Math.toRadians(this.rollC)) / Math.max(V_STALL, this.v);   // rad/s
        float yaw = this.getYRot() + (float) Math.toDegrees(omega * DT);
        // speed: thrust / drag towards vt, plus exchange with climb (energy)
        this.v += Mth.clamp(vt - this.v, -4.0, 2.5) * DT - G * Math.sin(Math.toRadians(this.gamma)) * DT * 0.6;
        this.v = Mth.clamp(this.v, V_STALL * 0.8, V_MAX * 1.15);
        double dz = alt - this.getY();
        double vzT = Mth.clamp(dz * 0.15, -18.0, 8.0);
        double gT = Math.toDegrees(Math.asin(Mth.clamp(vzT / this.v, -0.9, 0.9)));
        this.gamma += Mth.clamp(gT - this.gamma, -1.2, 1.2);
        this.move3(yaw);
    }

    private void move3(float yaw) {
        double h = this.v * Math.cos(Math.toRadians(this.gamma)) * DT, vy = this.v * Math.sin(Math.toRadians(this.gamma)) * DT;
        double r = Math.toRadians(yaw);
        Vec3 d = new Vec3(-Math.sin(r) * h, vy, Math.cos(r) * h);
        this.setDeltaMovement(d);
        this.setPos(this.getX() + d.x, this.getY() + d.y, this.getZ() + d.z);
        this.setYRot(yaw);
        this.setXRot((float) -this.gamma);
        this.entityData.set(ROLL, this.rollC);
    }

    private void orbit(Vec3 c, double alt, double radius, double vt) {
        double dx = this.getX() - c.x, dz = this.getZ() - c.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        float hdg;
        if (d > radius * 1.6) {
            hdg = (float) Math.toDegrees(Math.atan2(-(c.x - this.getX()), c.z - this.getZ()));
        } else {
            // tangent (counter-clockwise from above) with a radial correction
            double ang = Math.atan2(dz, dx) + Math.PI / 2 + Mth.clamp((d - radius) / radius, -0.6, 0.6);
            double tx = Math.cos(ang), tz = Math.sin(ang);
            hdg = (float) Math.toDegrees(Math.atan2(-tx, tz));
        }
        this.fly(hdg, alt, 45, vt);
    }

    @Nullable
    private Entity findTarget(ServerLevel sl, Vec3 spot) {
        Entity best = null;
        double bd = 64 * 64;
        for (Entity e : sl.getEntities(this, new AABB(spot, spot).inflate(64, 48, 64), e -> e instanceof Enemy && e instanceof LivingEntity && e.isAlive())) {
            double d = e.distanceToSqr(spot);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private void attack(ServerLevel sl, @Nullable BattleshipEntity ship) {
        Entity t = this.target;
        if (t == null || !t.isAlive() || (this.bombs <= 0 && this.mg <= 0)) {
            this.target = null;
            this.setState(this.bombs <= 0 && this.mg <= 0 ? S_RETURN : S_SPOT);
            return;
        }
        Vec3 tp = t.position().add(0, t.getBbHeight() * 0.5, 0);
        double dx = tp.x - this.getX(), dz = tp.z - this.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        double above = this.getY() - tp.y;
        float hdg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (this.timer % 200 < 130 && hd < 420 && above > 35) {
            // dive: aim the nose at the target (~30-40 deg), guns + bombs
            double dive = Math.toDegrees(Math.atan2(above, Math.max(1, hd)));
            float err = Math.abs(Mth.wrapDegrees(hdg - this.getYRot()));
            this.fly(hdg, tp.y + 20, 40, V_MAX);
            if (err < 25) this.gamma += Mth.clamp(-Math.min(dive, 50) - this.gamma, -2.0, 2.0);
            Vec3 nose = Vec3.directionFromRotation(this.getXRot(), this.getYRot());
            Vec3 to = tp.subtract(this.position()).normalize();
            if (this.mg > 0 && this.mgCd == 0 && nose.dot(to) > 0.995 && hd < 260) {
                this.mgCd = 3;
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 side = new Vec3(Math.cos(Math.toRadians(this.getYRot())), 0, Math.sin(Math.toRadians(this.getYRot()))).scale(2.2 * s);
                    Vec3 m = this.position().add(0, 1.6, 0).add(side).add(nose.scale(2.5));
                    Vec3 d = nose.add(this.random.nextGaussian() * 0.006, this.random.nextGaussian() * 0.006, this.random.nextGaussian() * 0.006).normalize();
                    BulletManager.spawn(sl, m, d.scale(10.0).add(this.getDeltaMovement()), this.getId(), true);
                }
                this.mg -= 2;
                sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.CRAFT_CANNON.get(), SoundSource.NEUTRAL, 4f, 1.1f);
            }
            // bomb release: predict the fall from the current velocity (no drag over ~100 m)
            if (this.bombs > 0 && this.bombCd == 0 && above < 140) {
                Vec3 vel = this.getDeltaMovement();
                double g = G * DT * DT, tFall = (vel.y + Math.sqrt(vel.y * vel.y + 2 * g * Math.max(1, above))) / g;
                Vec3 impact = this.position().add(vel.x * tFall, 0, vel.z * tFall);
                if (Math.hypot(impact.x - tp.x, impact.z - tp.z) < 6.0) {
                    this.falling.add(new double[]{this.getX(), this.getY() + 0.8, this.getZ(), vel.x, vel.y, vel.z});
                    this.bombs--;
                    this.bombCd = 12;
                    sl.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOMB_WHISTLE.get(), SoundSource.NEUTRAL, 3f, 1.4f);
                }
            }
            if (above < 45) this.gamma = Math.max(this.gamma, 5.0);         // pull out
        } else {
            // extend and climb back for another pass
            this.fly(hdg + 180f, tp.y + ORBIT_ALT, 40, V_CRUISE + 10);
            if (hd > 380 && above > 120) this.timer = 0;
        }
    }

    private void tickBombs(ServerLevel sl) {
        for (int k = this.falling.size() - 1; k >= 0; k--) {
            double[] b = this.falling.get(k);
            b[4] -= G * DT * DT;
            Vec3 a = new Vec3(b[0], b[1], b[2]), n = new Vec3(b[0] + b[3], b[1] + b[4], b[2] + b[5]);
            BlockHitResult h = sl.clip(new ClipContext(a, n, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            List<Entity> hitE = sl.getEntities(this, new AABB(a, n).inflate(0.4), e -> e instanceof LivingEntity && e.isAlive());
            if (h.getType() != HitResult.Type.MISS || !hitE.isEmpty() || n.y < sl.getMinBuildHeight()) {
                Vec3 p = h.getType() != HitResult.Type.MISS ? h.getLocation() : n;
                this.falling.remove(k);
                // SC 50: 50 kg, 25 kg Amatol
                if (RWConfig.COMMON.destroyBlocks.get()) ConventionalBlast.explode(sl, p, 25.0, 0.8);
                else sl.explode(this, p.x, p.y, p.z, 3.0f, Level.ExplosionInteraction.NONE);
                continue;
            }
            b[0] = n.x; b[1] = n.y; b[2] = n.z;
            sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.SMOKE.get(), n.x, n.y, n.z, 1, 0, 0, 0, 0);
        }
    }

    private void returnToShip(@Nullable BattleshipEntity ship) {
        double waterY = this.waterLevel(ship);
        if (ship == null) {
            // no ship: put down on the water / ground below
            this.fly(this.getYRot() + 2f, waterY - 2, 25, V_STALL + 6);
            this.checkTouchdown(waterY);
            return;
        }
        // landing spot: 70 m abeam to port, upwind of the crane; final approach parallel to the ship
        Vec3 spot = ship.localToWorld(70, 0, 30);
        double dx = spot.x - this.getX(), dz = spot.z - this.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);
        float hdg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        double alt = hd > 900 ? waterY + 120 : waterY + Math.max(-3, hd * 0.12 - 4);
        this.fly(hdg, alt, hd > 300 ? 40 : 15, hd > 900 ? V_CRUISE + 10 : V_STALL + 6);
        this.checkTouchdown(waterY);
    }

    private void checkTouchdown(double waterY) {
        if (this.getY() <= waterY + 0.1 && this.level().getFluidState(BlockPos.containing(this.getX(), waterY - 0.5, this.getZ())).is(FluidTags.WATER)) {
            this.setPos(this.getX(), waterY - 0.25, this.getZ());
            this.gamma = 0;
            this.setState(S_WATER);
            this.timer = 0;
        }
    }

    private void onWater(@Nullable BattleshipEntity ship) {
        double waterY = this.waterLevel(ship);
        this.rollC *= 0.8f;
        this.gamma = 0;
        float hdg = this.getYRot();
        double vt = 0;
        if (ship != null) {
            Vec3 c = ship.localToWorld(22, 0, -10);              // under the port crane
            double dx = c.x - this.getX(), dz = c.z - this.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            hdg = (float) Math.toDegrees(Math.atan2(-dx, dz));
            vt = Mth.clamp(d * 0.08, 0, 6);
            if (d < 70 && this.v < 4) {
                ship.recoverArado();
                this.discard();
                return;
            }
        } else if (this.timer > 1200) {
            this.discard();
            return;
        }
        this.v += Mth.clamp(vt - this.v, -1.2, 0.4);
        float yaw = this.getYRot() + Mth.clamp(Mth.wrapDegrees(hdg - this.getYRot()), -3f, 3f);
        double r = Math.toRadians(yaw);
        Vec3 d = new Vec3(-Math.sin(r) * this.v * DT, 0, Math.cos(r) * this.v * DT);
        this.setDeltaMovement(d);
        this.setPos(this.getX() + d.x, waterY - 0.25, this.getZ() + d.z);
        this.setYRot(yaw);
        this.setXRot(0f);
        this.entityData.set(ROLL, this.rollC);
    }

    private void crashFall(ServerLevel sl) {
        this.rollC += 9f;
        this.gamma = Math.max(-70, this.gamma - 1.5);
        this.v = Math.max(this.v, 30);
        this.move3(this.getYRot() + 2f);
        BlockPos bp = this.blockPosition();
        if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty() || sl.getFluidState(bp).is(FluidTags.WATER) || this.timer > 600) {
            this.explodeAndDie(sl, 6.0);
        }
    }

    private void explodeAndDie(ServerLevel sl, double kg) {
        if (this.isRemoved()) return;
        BattleshipEntity ship = this.owner();
        if (ship != null) ship.aradoLost();
        this.discard();
        sl.explode(this, this.getX(), this.getY() + 1, this.getZ(), (float) Math.min(4.0, 1.2 * Math.cbrt(kg + this.bombs * 25)), Level.ExplosionInteraction.NONE);
    }

    // ------------------------------------------------------------------------------------------ damage

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (this.level().isClientSide || this.isRemoved() || this.state() == S_CRASH) return false;
        float h = this.hp() - amount;
        this.entityData.set(HP, Math.max(0, h));
        if (h <= 0) {
            if (this.state() == S_WATER) this.explodeAndDie((ServerLevel) this.level(), 4.0);
            else { this.setState(S_CRASH); this.timer = 0; }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------ misc

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 640 * 640; }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    /** Tests: remaining weapons. */
    public int bombsLeft() { return this.bombs; }

    public void forceState(int s) { this.setState(s); this.timer = 0; }

    public void setFuelTicks(int f) { this.fuel = f; }
}
