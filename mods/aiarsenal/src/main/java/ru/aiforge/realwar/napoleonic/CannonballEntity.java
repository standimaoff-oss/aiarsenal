package ru.aiforge.realwar.napoleonic;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import ru.aiforge.realwar.registry.ModItems;
public final class CannonballEntity extends ThrowableItemProjectile {
    public float damage=28;
    public CannonballEntity(EntityType<? extends CannonballEntity> t,Level l){super(t,l);}
    @Override protected Item getDefaultItem(){return ModItems.CANNONBALL.get();}
    @Override protected float getGravity(){return .045f;}
    @Override public void tick(){super.tick();if(!level().isClientSide && tickCount>240)discard();}
    @Override protected void onHitEntity(EntityHitResult hit){super.onHitEntity(hit);if(!level().isClientSide)hit.getEntity().hurt(damageSources().thrown(this,getOwner()),damage);}
    @Override protected void onHit(HitResult hit){super.onHit(hit);if(level() instanceof ServerLevel sl){Vec3 p=hit.getLocation();sl.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.IMPACT.get(),p.x,p.y,p.z,0,0,1,0,1);discard();}}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.putFloat("RoundshotDamage",damage);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);damage=t.contains("RoundshotDamage")?Math.max(1,Math.min(40,t.getFloat("RoundshotDamage"))):28;}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
