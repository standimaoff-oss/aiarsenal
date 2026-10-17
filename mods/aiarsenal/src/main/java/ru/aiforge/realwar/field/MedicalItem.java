package ru.aiforge.realwar.field;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import ru.aiforge.realwar.item.TooltipItem;

/** r17: field medicine. Bandage: 1 s, +3 health. First aid kit: 2 s, Regeneration II for 6 s and cures poison. */
public class MedicalItem extends TooltipItem {
    public final boolean kit;

    public MedicalItem(boolean kit, Properties props) {
        super(props);
        this.kit = kit;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return kit ? 40 : 20;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!kit && p.getHealth() >= p.getMaxHealth()) return InteractionResultHolder.fail(s);
        p.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level level, LivingEntity e) {
        if (!level.isClientSide) {
            if (kit) {
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
                e.removeEffect(MobEffects.POISON);
            } else {
                e.heal(3.0f);
            }
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.8f, kit ? 0.8f : 1.2f);
        }
        if (e instanceof Player p) {
            p.getCooldowns().addCooldown(this, 10);
            if (!p.getAbilities().instabuild) s.shrink(1);
        }
        return s;
    }
}
