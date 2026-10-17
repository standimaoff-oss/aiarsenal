package ru.aiforge.realwar.field;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MPL-50 small infantry shovel. Works as an iron shovel, plus (r13) a two-point trench tool:
 * <ul>
 *   <li>right-click the ground - start point; the planned trench then follows the crosshair as a semi-transparent
 *       preview (client: {@code client.TrenchPreview});</li>
 *   <li>right-click the second point - the trench is dug along an L path (turns included), 2 deep, duckboards on the
 *       floor, revetted walls on every soil face (see {@link TrenchPlan});</li>
 *   <li>sneak + right-click on a block - dig one block; sneak + right-click in the air - cancel the start point.</li>
 * </ul>
 */
public class EntrenchingToolItem extends ShovelItem {
    public static final String TAG_A = "TrenchA";
    public static final String TAG_DIM = "TrenchDim";

    public EntrenchingToolItem(Properties p) {
        super(Tiers.IRON, 1.5f, -3.0f, p);
    }

    @Override
    public void appendHoverText(ItemStack st, @javax.annotation.Nullable Level lv, java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag fl) {
        tip.add(Component.translatable("item.aiarsenal.entrenching_tool.desc1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.aiarsenal.entrenching_tool.desc2").withStyle(ChatFormatting.DARK_GRAY));
        BlockPos a = start(st, lv);
        if (a != null) tip.add(Component.translatable("msg.aiarsenal.trench.marked", a.toShortString()).withStyle(ChatFormatting.GOLD));
    }

    public static boolean soil(BlockState s) {
        return s.is(BlockTags.MINEABLE_WITH_SHOVEL) && !s.hasBlockEntity() && s.isSolid() && !(s.getBlock() instanceof TrenchBlock);
    }

    /** Start point stored on the stack (only if it was set in this dimension). */
    @javax.annotation.Nullable
    public static BlockPos start(ItemStack st, @javax.annotation.Nullable Level lv) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains(TAG_A)) return null;
        if (lv != null && !lv.dimension().location().toString().equals(t.getString(TAG_DIM))) return null;
        return BlockPos.of(t.getLong(TAG_A));
    }

    private static void clear(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t != null) {
            t.remove(TAG_A);
            t.remove(TAG_DIM);
            if (t.isEmpty()) st.setTag(null);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (p.isShiftKeyDown() && start(st, null) != null) {
            if (!level.isClientSide) {
                clear(st);
                p.displayClientMessage(Component.translatable("msg.aiarsenal.trench.cancel"), true);
            }
            return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
        }
        BlockPos a = start(st, level);
        if (a != null && !p.isShiftKeyDown()) {                                // second point beyond arm's reach
            BlockPos b = aimed(p, 1.0f);
            if (b != null) return new InteractionResultHolder<>(this.dig(level, p, st, a, b, hand), st);
        }
        return InteractionResultHolder.pass(st);
    }

    /** Long aim for the trench end point (the planned trench is pointed across the field, not touched). */
    @javax.annotation.Nullable
    public static BlockPos aimed(Player p, float pt) {
        net.minecraft.world.phys.HitResult hr = p.pick(AIM_RANGE, pt, false);
        return hr instanceof net.minecraft.world.phys.BlockHitResult bh && hr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? bh.getBlockPos() : null;
    }

    public static final double AIM_RANGE = 64.0;

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player p = ctx.getPlayer();
        ItemStack st = ctx.getItemInHand();
        if (p != null && p.isShiftKeyDown()) {                                  // single block, also drops the start point
            if (!soil(level.getBlockState(pos))) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            clear(st);
            level.destroyBlock(pos, true, p);
            st.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(ctx.getHand()));
            return InteractionResult.CONSUME;
        }
        BlockPos a = start(st, level);
        if (a == null) {
            if (level.isClientSide) return InteractionResult.SUCCESS;
            st.getOrCreateTag().putLong(TAG_A, pos.asLong());
            st.getOrCreateTag().putString(TAG_DIM, level.dimension().location().toString());
            level.playSound(null, pos, SoundEvents.ROOTED_DIRT_HIT, SoundSource.BLOCKS, 1.0f, 1.0f);
            if (p != null) p.displayClientMessage(Component.translatable("msg.aiarsenal.trench.start", pos.toShortString(), TrenchPlan.MAX_CELLS), true);
            return InteractionResult.CONSUME;
        }
        return this.dig(level, p, st, a, pos, ctx.getHand());
    }

    private InteractionResult dig(Level level, @javax.annotation.Nullable Player p, ItemStack st, BlockPos a, BlockPos pos, InteractionHand hand) {
        int len = TrenchPlan.pathLength(a, pos);
        if (len > TrenchPlan.MAX_CELLS) {
            if (!level.isClientSide && p != null)
                p.displayClientMessage(Component.translatable("msg.aiarsenal.trench.too_long", len, TrenchPlan.MAX_CELLS).withStyle(ChatFormatting.RED), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        TrenchPlan plan = TrenchPlan.plan(level, a, pos);
        clear(st);
        int n = plan.okCells();
        if (n == 0) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.aiarsenal.trench.nothing").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        TrenchJobs.start((ServerLevel) level, plan);
        if (p != null) {
            p.displayClientMessage(Component.translatable("msg.aiarsenal.trench.dig", n, plan.cells.size() - n), true);
            if (!p.getAbilities().instabuild) st.hurtAndBreak(Math.max(1, n / 2), p, x -> x.broadcastBreakEvent(hand));
        }
        return InteractionResult.CONSUME;
    }
}
