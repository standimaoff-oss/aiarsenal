package ru.aiforge.realwar.soldier;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

/**
 * r27: summon egg - right click on the ground spawns a soldier of the given class
 * facing the player.  All four eggs live in a single creative-tab section (ai_units).
 */
public class SoldierEggItem extends Item {
    public final SoldierClass cls;

    public SoldierEggItem(SoldierClass cls) {
        super(new Properties().stacksTo(16));
        this.cls = cls;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level lv = ctx.getLevel();
        if (!(lv instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (ctx.getClickedFace() == Direction.DOWN) return InteractionResult.FAIL;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        Player p = ctx.getPlayer();
        float yaw = p == null ? 0f : p.getYRot();
        SoldierEntity.create(sl, this.cls, Vec3.atBottomCenterOf(at), yaw, p == null ? null : p.getUUID());
        if (p == null || !p.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.soldier_egg")
                .withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable(this.cls.nameKey()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
