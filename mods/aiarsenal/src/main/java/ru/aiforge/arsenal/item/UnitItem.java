package ru.aiforge.arsenal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.ArsenalHooks;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import java.util.List;

/** Places a drone or a launcher on the clicked block, facing the player's heading. */
public class UnitItem extends Item {
    public final Kind kind;

    public UnitItem(Kind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level lv = ctx.getLevel();
        if (!(lv instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos bp = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 pos = new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        Player p = ctx.getPlayer();
        float yaw = p == null ? 0 : p.getYRot();
        java.util.UUID owner = p == null ? null : p.getUUID();
        switch (kind.cat) {
            case RECON -> ReconDrone.create(sl, kind, pos, yaw, owner);
            case FPV -> {
                FpvDrone d = new FpvDrone(ArsenalRegistry.FPV.get(), sl);
                d.setKind(kind);
                d.setPos(pos);
                d.setYRot(yaw);
                d.owner = owner;
                sl.addFreshEntity(d);
            }
            case SHAHED -> ShahedDrone.create(sl, kind, pos, yaw, 0, null, owner, ShahedDrone.GROUND);
            case LAUNCHER -> {
                if (kind == Kind.YARS_TEL) {
                    // r3: the Yars TEL is a drivable Real War vehicle now (driver + launch officer, radar-map launch)
                    ru.aiforge.realwar.vehicle.YarsVehicle v = ru.aiforge.realwar.registry.ModEntities.YARS.get().create(sl);
                    if (v == null) return InteractionResult.FAIL;
                    v.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
                    v.setYRot(yaw);
                    v.yRotO = yaw;
                    sl.addFreshEntity(v);
                } else LauncherEntity.create(sl, kind, pos, yaw);
            }
            default -> {
                return InteractionResult.PASS;
            }
        }
        if (p == null || !p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        ArsenalHooks.tooltip(kind, tip);
    }
}
