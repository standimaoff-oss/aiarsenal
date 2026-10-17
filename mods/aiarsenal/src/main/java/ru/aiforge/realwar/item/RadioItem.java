package ru.aiforge.realwar.item;

import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

/**
 * R-187P1 "Azart" handheld tactical radio (reply 19): right click opens the fire-support screen (artillery, FAB-500 / FAB-1000
 * carpet, FAB-5000, nuclear). Cooldowns live in the stack NBT ("Cooldown" -> game time per strike type).
 */
public class RadioItem extends TooltipItem {
    public RadioItem(Item.Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.aiforge.realwar.client.strike.RadioScreen.open());
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    /** game time when strike {@code key} is available again (0 = ready) */
    public static long readyAt(ItemStack st, String key) {
        var t = st.getTagElement("Cooldown");
        return t == null ? 0L : t.getLong(key);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return ru.aiforge.realwar.client.render.GlbItemRenderer.instance();
            }
        });
    }
}
