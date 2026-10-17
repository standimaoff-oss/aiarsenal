package ru.aiforge.bm21grad.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.bm21grad.entity.Bm21Entity;

import java.util.Comparator;
import java.util.List;

/**
 * Artillery laser rangefinder: right click designates the looked-at block (up to 1024 blocks) as the
 * target of the BM-21 you ride, or of the nearest BM-21 within 96 blocks.
 */
public class RangefinderItem extends Item {
    public static final double RANGE = 1024;

    public RangefinderItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(st);
        Vec3 eye = player.getEyePosition();
        // never ray-trace into unloaded chunks (would force synchronous chunk generation)
        double range = Math.min(RANGE, level.getServer().getPlayerList().getViewDistance() * 16.0);
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.translatable("msg.aiarsenal.rf_nothing"), true);
            return InteractionResultHolder.fail(st);
        }
        Bm21Entity v = player.getVehicle() instanceof Bm21Entity b ? b : null;
        if (v == null) {
            List<Bm21Entity> list = level.getEntitiesOfClass(Bm21Entity.class, player.getBoundingBox().inflate(96), e -> !e.isDestroyed());
            v = list.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        }
        double d = Math.sqrt(hit.getBlockPos().distToCenterSqr(eye));
        if (v == null) {
            player.displayClientMessage(Component.translatable("msg.aiarsenal.rf_range", (int) d), true);
            return InteractionResultHolder.success(st);
        }
        player.displayClientMessage(v.setTarget(hit.getBlockPos()), false);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.rangefinder").withStyle(ChatFormatting.GRAY));
    }
}
