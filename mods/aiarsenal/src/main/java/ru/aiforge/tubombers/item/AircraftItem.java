package ru.aiforge.tubombers.item;

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
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.registry.ModRegistry;

import java.util.List;

/** Places a bomber on the ground, nose pointing where the player looks. */
public class AircraftItem extends Item {
    public final AircraftType type;

    public AircraftItem(AircraftType type, Properties p) {
        super(p);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        BomberEntity b = spawn(sl, type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0);
        if (b == null) return InteractionResult.FAIL;
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    public static BomberEntity spawn(ServerLevel sl, AircraftType t, double x, double y, double z, float yaw) {
        BomberEntity b = (t == AircraftType.TU160 ? ModRegistry.TU160.get() : ModRegistry.TU95.get()).create(sl);
        if (b == null) return null;
        b.initState(x, y, z, yaw);
        sl.addFreshEntity(b);
        return b;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.aiarsenal." + type.id).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.aiarsenal.place").withStyle(ChatFormatting.DARK_GRAY));
    }
}
