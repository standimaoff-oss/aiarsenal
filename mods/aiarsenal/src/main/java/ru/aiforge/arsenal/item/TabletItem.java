package ru.aiforge.arsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.ReconDrone;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Ground control tablet: right-click a recon drone to link and take control, right-click in the air to reconnect.
 * Stores the last designated target which launchers, the Shahed rail and the Liutyi take over.
 */
public class TabletItem extends Item {
    public TabletItem(Properties p) {
        super(p);
    }

    public static void link(ItemStack s, Entity drone) {
        s.getOrCreateTag().putUUID("Drone", drone.getUUID());
        s.getOrCreateTag().putString("DroneKind", ((ArsEntity) drone).kind().id);
    }

    @Nullable
    public static BlockPos target(ItemStack s) {
        CompoundTag t = s.getTag();
        return t != null && t.contains("TX") ? new BlockPos(t.getInt("TX"), t.getInt("TY"), t.getInt("TZ")) : null;
    }

    public static void setTarget(ItemStack s, BlockPos p) {
        CompoundTag t = s.getOrCreateTag();
        t.putInt("TX", p.getX());
        t.putInt("TY", p.getY());
        t.putInt("TZ", p.getZ());
    }

    /** Writes a designation into the operator's tablet(s). */
    public static void storeTarget(ServerPlayer p, BlockPos pos) {
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof TabletItem) setTarget(s, pos);
        for (ItemStack s : p.getInventory().offhand) if (s.getItem() instanceof TabletItem) setTarget(s, pos);
    }

    @Nullable
    public static BlockPos findTarget(Player p) {
        if (p.getMainHandItem().getItem() instanceof TabletItem) {
            BlockPos t = target(p.getMainHandItem());
            if (t != null) return t;
        }
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof TabletItem && target(s) != null) return target(s);
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(s);
        ServerPlayer sp = (ServerPlayer) player;
        CompoundTag t = s.getTag();
        if (sp.isShiftKeyDown() || t == null || !t.hasUUID("Drone")) {
            // designate by looking: ray up to 1 km over loaded terrain
            Vec3 eye = sp.getEyePosition();
            Vec3 end = eye.add(sp.getLookAngle().scale(1000));
            BlockHitResult h = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, sp));
            if (h.getType() == HitResult.Type.BLOCK) {
                BlockPos bp = h.getBlockPos();
                setTarget(s, bp);
                level.playSound(null, sp.blockPosition(), ru.aiforge.arsenal.registry.ArsenalRegistry.SND_BEEP.get(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
                sp.displayClientMessage(Component.translatable("msg.aiarsenal.tablet_designated", bp.getX(), bp.getY(), bp.getZ(),
                        (int) Math.sqrt(sp.blockPosition().distSqr(bp))), true);
                return InteractionResultHolder.consume(s);
            }
            sp.displayClientMessage(Component.translatable(t == null || !t.hasUUID("Drone") ? "msg.aiarsenal.tablet_unlinked"
                    : "msg.aiarsenal.tablet_no_hit"), true);
            return InteractionResultHolder.fail(s);
        }
        UUID id = t.getUUID("Drone");
        for (ServerLevel l : sp.server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof ReconDrone d && l == sp.level()) {
                RemoteControl.start(sp, d);
                return InteractionResultHolder.consume(s);
            }
        }
        sp.displayClientMessage(Component.translatable("msg.aiarsenal.drone_not_found"), true);
        return InteractionResultHolder.fail(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("DroneKind"))
            tip.add(Component.translatable("tooltip.aiarsenal.linked", Component.translatable("item.aiarsenal." + t.getString("DroneKind")))
                    .withStyle(ChatFormatting.AQUA));
        BlockPos tg = target(stack);
        if (tg != null) tip.add(Component.translatable("tooltip.aiarsenal.target", tg.getX(), tg.getY(), tg.getZ()).withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("tooltip.aiarsenal.tablet").withStyle(ChatFormatting.GRAY));
    }
}
