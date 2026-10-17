package ru.aiforge.arsenal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Glue between the Tu bomber module and the arsenal (store release, nuclear charge placement, tooltips). */
public final class ArsenalHooks {
    private ArsenalHooks() {
    }

    /**
     * Called by the bomber instead of spawning a FAB bomb: Kh-101 / Kh-55 drop clear of the rotary launcher and fly
     * to the bomber's target (or the crew's tablet designation); RDS-1 / AN602 fall with an AIR fuze and are registered
     * for the remote detonator.
     */
    public static void releaseFromBomber(ServerLevel sl, Entity bomber, Kind k, Vec3 pos, Vec3 vel, @Nullable BlockPos target) {
        ServerPlayer crew = null;
        for (Entity p : bomber.getPassengers()) if (p instanceof ServerPlayer sp) crew = sp;
        UUID owner = crew == null ? null : crew.getUUID();
        if (target == null && crew != null) target = TabletItem.findTarget(crew);
        if (k == Kind.KH101 || k == Kind.KH55) {
            MissileEntity.create(sl, k, pos, vel, target, null, owner, MissileEntity.DROP);
            if (crew != null && target == null) crew.displayClientMessage(Component.translatable("msg.aiarsenal.missile_no_target"), true);
        } else {
            NukeEntity n = NukeEntity.create(sl, k, pos, vel, ArsEntityYaw.of(vel), ChargeRegistry.FUZE_AIR, owner, true);
            if (crew != null)
                crew.displayClientMessage(Component.translatable("msg.aiarsenal.nuke_released", Component.translatable(k.translationKey()), n.charge,
                        Component.translatable("fuze.aiarsenal." + ChargeRegistry.FUZE_AIR)), false);
        }
    }

    /** Right-click with RDS-1 / AN602 on a block: places an armed charge with a REMOTE fuze. */
    public static InteractionResult placeCharge(UseOnContext ctx, Kind k) {
        if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Player p = ctx.getPlayer();
        BlockPos bp = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 pos = new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        NukeEntity n = NukeEntity.create(sl, k, pos, Vec3.ZERO, p == null ? 0 : p.getYRot(), ChargeRegistry.FUZE_REMOTE,
                p == null ? null : p.getUUID(), false);
        if (p != null) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.charge_armed", Component.translatable(k.translationKey()), n.charge),
                    false);
            if (!p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    public static void tooltip(Kind k, List<Component> tip) {
        tip.add(Component.translatable("tooltip.aiarsenal." + k.id).withStyle(ChatFormatting.GRAY));
        if (k.nuclear()) {
            float kt = k.yieldKt();
            String y = kt >= 1000 ? String.format("%.0f Mt", kt / 1000f) : String.format("%.0f kt", kt);
            tip.add(Component.translatable("tooltip.aiarsenal.yield", y, (int) NuclearBlast.radius(kt)).withStyle(ChatFormatting.RED));
            tip.add(Component.translatable("tooltip.aiarsenal.remote").withStyle(ChatFormatting.DARK_RED));
        }
        String use = switch (k) {
            case KH101, KH55 -> "bomber";
            case RDS1, AN602 -> "nuke";
            case KALIBR, TOMAHAWK -> "cruise";
            case S136, S131, S238 -> "rail";
            case YARS -> "tel";
            case LIUTYI -> "liutyi";
            default -> k.cat.name().toLowerCase();
        };
        tip.add(Component.translatable("tooltip.aiarsenal.use_" + use).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Small helper to keep yaw math in one place. */
    static final class ArsEntityYaw {
        static float of(Vec3 v) {
            return ru.aiforge.arsenal.entity.ArsEntity.yawOf(v);
        }
    }
}
