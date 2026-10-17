package ru.aiforge.bm21grad.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import ru.aiforge.bm21grad.entity.Bm21Entity;

import java.util.List;
import java.util.function.Consumer;

/** Places a BM-21 (keeps ammo/health when the vehicle was packed up with sneak + right click). */
public class Bm21SpawnerItem extends Item {
    public Bm21SpawnerItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        Vec3 at = Vec3.atBottomCenterOf(pos);
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0;
        Bm21Entity e = Bm21Entity.create(level, at.x, at.y, at.z, yaw);
        if (!level.noCollision(e, e.getBoundingBox())) return InteractionResult.FAIL;
        if (level instanceof ServerLevel sl) {
            ItemStack st = ctx.getItemInHand();
            if (st.hasTag() && st.getTag().contains("Vehicle")) e.loadVehicle(st.getTag().getCompound("Vehicle"));
            sl.addFreshEntity(e);
            sl.gameEvent(ctx.getPlayer(), GameEvent.ENTITY_PLACE, pos);
            if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal.bm21.1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.bm21.2").withStyle(ChatFormatting.DARK_GRAY));
        if (st.hasTag() && st.getTag().contains("Vehicle")) {
            byte[] t = st.getTag().getCompound("Vehicle").getByteArray("Tubes");
            int n = 0;
            for (byte b : t) if (b != 0) n++;
            tip.add(Component.translatable("tooltip.aiarsenal.bm21.loaded", n).withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(ru.aiforge.bm21grad.client.ClientItemExtensions.INSTANCE);
    }
}
