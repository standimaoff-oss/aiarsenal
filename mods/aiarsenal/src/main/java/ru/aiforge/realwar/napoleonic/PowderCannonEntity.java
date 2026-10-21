package ru.aiforge.realwar.napoleonic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.realwar.registry.*;

/** Muzzle-loaded, manually aimed solid-roundshot gun. No explosive shell disguised as an iron ball. */
public final class PowderCannonEntity extends Entity {
    private static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(PowderCannonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ELEVATION = SynchedEntityData.defineId(PowderCannonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RECOIL = SynchedEntityData.defineId(PowderCannonEntity.class, EntityDataSerializers.INT);
    private float health=40;
    public PowderCannonEntity(EntityType<? extends PowderCannonEntity> type, Level level) { super(type,level); }
    public boolean french() { return getType()==ModEntities.CANNON_FR6.get(); }
    public String model() { return french()?"cannon_fr6":"cannon_ri12"; }
    public Item charge() { return french()?ModItems.CHARGE_FR6.get():ModItems.CHARGE_RI12.get(); }
    public int loading() { return entityData.get(LOAD); } // -1 empty; >0 loading; 0 ready
    public int elevation() { return entityData.get(ELEVATION); }
    public float recoil(float partial) { return Math.max(0,entityData.get(RECOIL)-partial)/10f; }
    @Override protected void defineSynchedData() { entityData.define(LOAD,-1);entityData.define(ELEVATION,5);entityData.define(RECOIL,0); }
    @Override public boolean isPickable() { return isAlive(); }
    @Override public boolean canBeCollidedWith() { return isAlive(); }
    @Override public void tick() {
        super.tick();
        if(!level().isClientSide) {
            if(loading()>0) entityData.set(LOAD,loading()-1);
            if(entityData.get(RECOIL)>0) entityData.set(RECOIL,entityData.get(RECOIL)-1);
            if(!isNoGravity()) { setDeltaMovement(getDeltaMovement().add(0,-.04,0));move(MoverType.SELF,getDeltaMovement());setDeltaMovement(getDeltaMovement().multiply(.8,.98,.8)); }
        }
    }
    public boolean load(Player p, ItemStack stack) {
        if(loading()!=-1 || !stack.is(charge()) || level().isClientSide) return false;
        if(!p.getAbilities().instabuild) stack.shrink(1);
        entityData.set(LOAD,160);return true;
    }
    public Vec3 muzzle() { return position().add(0,1.05,0).add(Vec3.directionFromRotation(-elevation(),getYRot()).scale(french()?1.45:1.7)); }
    /** r22 AI crew: sponging the piece without a player's item stack. */
    public boolean aiLoad() {
        if(loading()!=-1 || level().isClientSide) return false;
        entityData.set(LOAD,160);return true;
    }

    /** r22 AI crew: lays the gun on a world point with a rank-scaled error and tops the elevation within the arc. */
    public void aiAim(Vec3 target, double skill, RandomSource rnd) {
        if(level().isClientSide) return;
        double dx=target.x-getX(), dz=target.z-getZ();
        double yaw=Math.toDegrees(Math.atan2(-dx,dz));
        double errDeg=(1.0-skill)*4.0;
        yaw+=rnd.nextGaussian()*errDeg;
        setYRot((float)yaw);yRotO=getYRot();
        double v=french()?3.0:2.8;
        double g=0.045;
        double d=Math.sqrt(dx*dx+dz*dz);
        double dy=target.y-muzzle().y+0.6;
        double v2=v*v;
        double disc=v2*v2-g*(g*d*d+2*dy*v2);
        if(disc<0) { entityData.set(ELEVATION,elevation()); return; }
        double angle=Math.toDegrees(Math.atan((v2-Math.sqrt(disc))/(g*d)));
        int step=(int)Math.round(Math.max(0,Math.min(35,angle))/5.0)*5;
        entityData.set(ELEVATION,step);
    }

    public boolean fire(Player owner) { return this.fire((Entity)owner); }

    public boolean aiFire(Entity owner) { return this.fire(owner); }

    public boolean fire(Entity owner) {
        if(!(level() instanceof ServerLevel level) || loading()!=0) return false;
        double spread = owner instanceof ru.aiforge.realwar.npc.SoldierEntity s
                ? (1.0 - s.rank().skill) * 0.06 : 0.0;
        var dir=Vec3.directionFromRotation(-elevation(),getYRot());
        if (spread > 0) {
            java.util.Random rnd = new java.util.Random(level.random.nextLong());
            dir = new Vec3(dir.x + rnd.nextGaussian() * spread, dir.y + rnd.nextGaussian() * spread * 0.5,
                    dir.z + rnd.nextGaussian() * spread).normalize();
        }
        var at=muzzle();
        var ball=new CannonballEntity(ModEntities.CANNONBALL.get(),level);
        ball.setOwner(owner);ball.setPos(at);ball.setDeltaMovement(dir.scale(french()?3.0:2.8));
        ball.damage=french()?28:36;level.addFreshEntity(ball);
        // Pre-hydraulic gun: the carriage, not a sliding barrel, rolls back.
        setDeltaMovement(getDeltaMovement().add(-dir.x * .14, 0, -dir.z * .14));
        entityData.set(LOAD, -1);
        entityData.set(RECOIL, 10);
        level.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.CANNON.get(),at.x,at.y,at.z,0,dir.x,dir.y,dir.z,1);
        level.playSound(null,getX(),getY(),getZ(),ModSounds.POWDER_CANNON.get(),SoundSource.BLOCKS,3f,.85f);
        return true;
    }
    @Override public InteractionResult interact(Player p, InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(level().isClientSide)return InteractionResult.SUCCESS;
        ItemStack item=p.getItemInHand(hand);
        if(item.is(charge())) { load(p,item); }
        else if(item.is(Items.FLINT_AND_STEEL)) {
            if(fire(p) && !p.getAbilities().instabuild)item.hurtAndBreak(1,p,e->e.broadcastBreakEvent(hand));
        } else if(item.isEmpty()) {
            if(p.isShiftKeyDown())entityData.set(ELEVATION,(elevation()+5)%40);
            else {setYRot(p.getYRot());yRotO=getYRot();}
        }
        p.displayClientMessage(Component.translatable("msg.aiarsenal.cannon.status",elevation(),
                Component.translatable(loading()<0?"msg.aiarsenal.cannon.empty":loading()==0?"msg.aiarsenal.cannon.ready":"msg.aiarsenal.cannon.loading")),true);
        return InteractionResult.CONSUME;
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide || isInvulnerableTo(source))return false;
        if(source.getEntity() instanceof Player p && p.getAbilities().instabuild){discard();return true;}
        health-=amount;
        if(health<=0){spawnAtLocation(french()?ModItems.CANNON_FR6.get():ModItems.CANNON_RI12.get());discard();}
        return true;
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {tag.putInt("LoadTicks",loading());tag.putInt("Elevation",elevation());tag.putFloat("Health",health);}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {entityData.set(LOAD,tag.contains("LoadTicks")?Math.max(-1,Math.min(160,tag.getInt("LoadTicks"))):-1);entityData.set(ELEVATION,Math.max(0,Math.min(35,tag.getInt("Elevation"))));health=tag.contains("Health")?tag.getFloat("Health"):40;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
