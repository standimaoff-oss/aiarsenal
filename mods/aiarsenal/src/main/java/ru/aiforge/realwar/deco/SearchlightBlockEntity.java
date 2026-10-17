package ru.aiforge.realwar.deco;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.registry.ModBlockEntities;
import ru.aiforge.realwar.registry.ModBlocks;

/** Searchlight state. Yaw 0 = north, +90 = east; pitch + = up. Sweep is a pure function of game time so the client
 *  animates it without packets; only mode / base aim / lock-on target are synced. */
public class SearchlightBlockEntity extends BlockEntity {
    public static final double RANGE = 64.0, CONE_DEG = 7.0, SWEEP_AMP = 50.0, SWEEP_PERIOD = 240.0;
    public static final Vec3 PIVOT = new Vec3(0.5, 1.02, 0.5);
    public int mode;                 // 0 off, 1 fixed, 2 sweep (+ lock-on)
    public float baseYaw, basePitch = 8f;
    public UUID owner;
    public int trackId = -1;         // entity id of the locked target (synced), -1 = none
    public float yaw, pitch;         // current aim (server: authoritative while tracking; client: smoothed)
    public int signal;               // redstone output ticks left
    private int lostTicks;
    private BlockPos spot;
    // client-side caches
    public float cYaw = Float.NaN, cPitch;
    public double cBeam = RANGE;
    public long cBeamTick = -1;

    public SearchlightBlockEntity(BlockPos p, BlockState s) {
        super(ModBlockEntities.SEARCHLIGHT.get(), p, s);
        this.baseYaw = yawOf(s.getValue(SearchlightBlock.FACING));
        this.yaw = baseYaw;
        this.pitch = basePitch;
    }

    public static float yawOf(Direction d) {
        return switch (d) {
            case EAST -> 90f;
            case SOUTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    public static Vec3 dir(float yawDeg, float pitchDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        return new Vec3(Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p));
    }

    public Vec3 pivot() {
        return Vec3.atLowerCornerOf(worldPosition).add(PIVOT);
    }

    /** Aim from base + sweep at the given (partial) time; used when not locked on. */
    public float[] patrolAim(double time) {
        if (mode == 2) return new float[]{(float) (baseYaw + SWEEP_AMP * Math.sin(time * 2 * Math.PI / SWEEP_PERIOD)), basePitch};
        return new float[]{baseYaw, basePitch};
    }

    public boolean on() {
        return mode != 0;
    }

    public void cycleMode() {
        mode = (mode + 1) % 3;
        trackId = -1;
        if (mode == 0) {
            clearSpot();
            setSignal(0);
        }
        BlockState s = getBlockState();
        if (s.getValue(SearchlightBlock.LIT) != on()) level.setBlock(worldPosition, s.setValue(SearchlightBlock.LIT, on()), 3);
        sync();
    }

    public void aimAt(Player p) {
        baseYaw = Mth.wrapDegrees(p.getYRot() + 180f);
        if (baseYaw < 0) baseYaw += 360f;
        basePitch = Mth.clamp(-p.getXRot(), -20f, 80f);
        trackId = -1;
        yaw = baseYaw;
        pitch = basePitch;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void setSignal(int t) {
        boolean was = signal > 0;
        signal = t;
        if (was != (signal > 0) && level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    public void clearSpot() {
        if (spot != null && level != null) {
            SpotLightBlock.forget(level, spot);
            if (level.getBlockState(spot).is(ModBlocks.SPOTLIGHT.get())) level.setBlock(spot, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }
        spot = null;
    }

    private boolean visible(Vec3 from, Vec3 to) {
        BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        return r.getType() == HitResult.Type.MISS || r.getLocation().distanceToSqr(from) >= to.distanceToSqr(from) - 0.25;
    }

    private boolean target(LivingEntity e) {
        if (!e.isAlive() || e.isSpectator()) return false;
        if (e instanceof Player pl) return !pl.isCreative() && !pl.getUUID().equals(owner);
        return e instanceof Enemy;
    }

    public void serverTick() {
        if (signal > 0 && --signal == 0) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        if (!on()) return;
        long t = level.getGameTime();
        Vec3 o = pivot();
        // aim: locked target or patrol
        Entity tgt = trackId >= 0 ? level.getEntity(trackId) : null;
        if (tgt != null && tgt.isAlive()) {
            Vec3 d = tgt.getBoundingBox().getCenter().subtract(o);
            float wy = (float) Math.toDegrees(Math.atan2(d.x, -d.z));
            float wp = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            yaw += Mth.clamp(Mth.wrapDegrees(wy - yaw), -6f, 6f);            // 120 deg/s slew
            pitch += Mth.clamp(wp - pitch, -6f, 6f);
            pitch = Mth.clamp(pitch, -20f, 80f);
        } else {
            if (trackId >= 0) {
                trackId = -1;
                sync();
            }
            float[] a = patrolAim(t);
            yaw = a[0];
            pitch = a[1];
        }
        Vec3 dv = dir(yaw, pitch);
        // detection every 5 ticks
        if (t % 5 == 0) {
            double cos = Math.cos(Math.toRadians(CONE_DEG));
            AABB box = new AABB(o, o.add(dv.scale(RANGE))).inflate(RANGE * Math.tan(Math.toRadians(CONE_DEG)) + 1);
            List<LivingEntity> list = level.getEntitiesOfClass(LivingEntity.class, box, this::target);
            LivingEntity best = null;
            double bestD = 1e9;
            for (LivingEntity e : list) {
                Vec3 c = e.getBoundingBox().getCenter();
                Vec3 to = c.subtract(o);
                double dist = to.length();
                if (dist > RANGE || dist < 0.5) continue;
                double ang = to.normalize().dot(dv);
                double tol = Math.cos(Math.toRadians(CONE_DEG) + Math.atan2(e.getBbWidth() * 0.6, dist));
                if (ang < Math.min(cos, tol)) continue;
                if (!visible(o.add(dv.scale(0.45)), c)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
                if (dist < bestD) {
                    bestD = dist;
                    best = e;
                }
            }
            if (best != null) {
                setSignal(40);
                lostTicks = 0;
                if (mode == 2 && best.getId() != trackId) {
                    trackId = best.getId();
                    sync();
                }
            } else if (trackId >= 0 && (lostTicks += 5) > 40) {
                trackId = -1;
                sync();
            }
        }
        // lit spot where the beam lands (every 4 ticks)
        if (t % 4 == 0) {
            BlockHitResult r = level.clip(new ClipContext(o.add(dv.scale(0.45)), o.add(dv.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
            BlockPos np = null;
            if (r.getType() == HitResult.Type.BLOCK) {
                BlockPos q = r.getBlockPos().relative(r.getDirection());
                BlockState qs = level.getBlockState(q);
                if (qs.isAir() || qs.is(ModBlocks.SPOTLIGHT.get())) np = q;
            }
            if (np == null ? spot != null : !np.equals(spot)) {
                clearSpot();
                if (np != null && level.isLoaded(np)) {
                    SpotLightBlock.refresh(level, np);
                    level.setBlock(np, ModBlocks.SPOTLIGHT.get().defaultBlockState(), 3);
                    spot = np;
                }
            }
        }
        if (spot != null) SpotLightBlock.refresh(level, spot);
    }

    public BlockPos spot() {
        return spot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
        tag.putFloat("Yaw", baseYaw);
        tag.putFloat("Pitch", basePitch);
        tag.putInt("Track", trackId);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mode = tag.getInt("Mode");
        baseYaw = tag.getFloat("Yaw");
        basePitch = tag.contains("Pitch") ? tag.getFloat("Pitch") : 8f;
        trackId = tag.contains("Track") ? tag.getInt("Track") : -1;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        saveAdditional(t);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return on() ? INFINITE_EXTENT_AABB : new AABB(worldPosition).inflate(0.6);
    }
}
