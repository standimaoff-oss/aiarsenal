package ru.aiforge.realwar.craft;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Places a helicopter on the ground or a boat / submarine on the water. */
public class CraftItem extends Item {
    public final CraftKind kind;

    public CraftItem(CraftKind kind, Properties p) {
        super(p);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, this.kind.isAir() ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY);
        if (hit.getType() != HitResult.Type.BLOCK) return InteractionResultHolder.pass(st);
        Vec3 at = hit.getLocation();
        if (!this.kind.isAir() && !level.getFluidState(hit.getBlockPos()).is(net.minecraft.tags.FluidTags.WATER)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("craft.aiarsenal.need_water"), true);
            return InteractionResultHolder.fail(st);
        }
        if (level instanceof ServerLevel sl) {
            double y = this.kind.isAir() ? at.y : (this.kind.isSub() ? hit.getBlockPos().getY() + 1 - this.kind.water.draft() : hit.getBlockPos().getY() + 1 - this.kind.water.draft());
            float yaw = player.getYRot();
            CraftEntity e = CraftRegistry.spawn(sl, this.kind, at.x, y, at.z, yaw);
            if (e == null || !sl.noCollision(e) && this.kind.isAir()) {
                if (e != null) e.discard();
                player.displayClientMessage(Component.translatable("craft.aiarsenal.no_room"), true);
                return InteractionResultHolder.fail(st);
            }
            if (!player.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.aiarsenal." + this.kind.id + ".tip").withStyle(ChatFormatting.GRAY));
    }
}
