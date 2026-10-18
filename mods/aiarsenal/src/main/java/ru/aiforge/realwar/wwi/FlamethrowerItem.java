package ru.aiforge.realwar.wwi;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.registry.ModItems;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.realwar.missile.GlbItemExt;

/** Short-range game flame projector: fuel, water cutoff and solid-wall occlusion are server authoritative. */
public final class FlamethrowerItem extends Item {
    public FlamethrowerItem(Properties properties) { super(properties); }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        GlbItemExt.apply(consumer);
    }

    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && stack.getDamageValue() > 0) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack fuel = player.getInventory().getItem(i);
                    if (!fuel.is(ModItems.WWI_FUEL.get())) continue;
                    stack.setDamageValue(0);
                    if (!player.getAbilities().instabuild) fuel.shrink(1);
                    return InteractionResultHolder.sidedSuccess(stack, false);
                }
            }
            return InteractionResultHolder.pass(stack);
        }
        if (player.isInWaterOrBubble() || stack.getDamageValue() >= stack.getMaxDamage()-1)
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        if (user.isInWaterOrBubble() || stack.getDamageValue() >= stack.getMaxDamage()-1) {
            user.stopUsingItem(); return;
        }
        boolean creative = user instanceof Player p && p.getAbilities().instabuild;
        if (!creative) stack.setDamageValue(stack.getDamageValue()+1);
        if (remaining % 4 != 0) return;
        Vec3 start = user.getEyePosition();
        Vec3 direction = user.getLookAngle();
        Vec3 end = level.clip(new ClipContext(start, start.add(direction.scale(8)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, user)).getLocation();
        double length = end.distanceTo(start);
        for (double d = 1.6; d < length; d += 0.85) {
            Vec3 point = start.add(direction.scale(d));
            double vy = 0.08 + 0.035 * d;
            server.sendParticles(ru.aiforge.arsenal.volumetric.VolumetricParticles.FIRE.get(),
                    point.x, point.y - 0.18, point.z, 0, direction.x * 0.15, vy, direction.z * 0.15, 1);
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(start,end).inflate(.45), e -> e != user && e.isAlive())) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(start);
            double along = delta.dot(direction);
            if (along <= 0 || along > length || delta.subtract(direction.scale(along)).lengthSqr() > .8*.8) continue;
            Vec3 visible = level.clip(new ClipContext(start,target.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,user)).getLocation();
            if (visible.distanceToSqr(target.getBoundingBox().getCenter()) > .01) continue;
            target.hurt(level.damageSources().inFire(), 2f);
            target.setSecondsOnFire(3);
        }
        // No block placement/ignition: the weapon cannot silently set whole builds on fire.
        level.playSound(null,user.blockPosition(),SoundEvents.FIRE_AMBIENT,SoundSource.PLAYERS,.5f,1.4f);
    }
}
