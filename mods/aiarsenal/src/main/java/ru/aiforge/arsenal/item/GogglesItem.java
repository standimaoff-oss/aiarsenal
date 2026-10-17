package ru.aiforge.arsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.entity.FpvDrone;

import java.util.List;

/** FPV goggles + radio: right-click an FPV drone to bind and fly it, right-click in the air to reconnect. */
public class GogglesItem extends Item {
    public GogglesItem(Properties p) {
        super(p);
    }

    public static void link(ItemStack s, Entity drone) {
        s.getOrCreateTag().putUUID("Drone", drone.getUUID());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(s);
        ServerPlayer sp = (ServerPlayer) player;
        CompoundTag t = s.getTag();
        if (t != null && t.hasUUID("Drone")) {
            Entity e = sp.serverLevel().getEntity(t.getUUID("Drone"));
            if (e instanceof FpvDrone d) {
                RemoteControl.start(sp, d);
                return InteractionResultHolder.consume(s);
            }
            sp.displayClientMessage(Component.translatable("msg.aiarsenal.drone_not_found"), true);
        } else sp.displayClientMessage(Component.translatable("msg.aiarsenal.goggles_unlinked"), true);
        return InteractionResultHolder.fail(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.goggles").withStyle(ChatFormatting.GRAY));
    }
}
