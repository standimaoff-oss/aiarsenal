package ru.aiforge.realwar.wwi;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.realwar.missile.GlbItemExt;
import net.minecraft.sounds.SoundSource;
import ru.aiforge.realwar.registry.ModEntities;

/** Game grenade: the fuse starts only after a successful server-side throw. */
public final class GrenadeItem extends Item {
    public GrenadeItem(Properties properties) { super(properties); }

    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        GlbItemExt.apply(consumer);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            GrenadeEntity grenade = ModEntities.WWI_GRENADE.get().create(level);
            if (grenade == null) return InteractionResultHolder.fail(stack);
            grenade.setOwner(player);
            grenade.setItem(stack.copyWithCount(1));
            grenade.setPos(player.getX(), player.getEyeY() - .1, player.getZ());
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), -8f, .9f, 1f);
            if (!level.addFreshEntity(grenade)) return InteractionResultHolder.fail(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.getCooldowns().addCooldown(this, 15);
            level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, .7f, .65f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
