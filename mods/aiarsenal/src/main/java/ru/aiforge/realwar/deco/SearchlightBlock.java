package ru.aiforge.realwar.deco;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r14 searchlight (Soviet 60-cm pattern). Right-click cycles OFF / FIXED / SWEEP, Sneak + right-click with an empty
 *  hand aims it where you look. Lights what the beam hits (real block light), makes detected hostiles / strangers glow
 *  and outputs a redstone signal while it sees someone; in SWEEP mode it locks on and follows the target. */
public class SearchlightBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 8, 15), Block.box(3, 8, 3, 13, 16, 13));

    public SearchlightBlock(Properties p) {
        super(p);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level lv, BlockPos p, BlockState s, LivingEntity by, ItemStack st) {
        if (!lv.isClientSide && lv.getBlockEntity(p) instanceof SearchlightBlockEntity be && by != null) {
            be.owner = by.getUUID();
            be.setChanged();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level lv, BlockPos p, Player pl, InteractionHand h, BlockHitResult hit) {
        if (!(lv.getBlockEntity(p) instanceof SearchlightBlockEntity be)) return InteractionResult.PASS;
        if (lv.isClientSide) return InteractionResult.SUCCESS;
        if (pl.isShiftKeyDown()) {
            if (!pl.getItemInHand(h).isEmpty()) return InteractionResult.PASS;
            be.aimAt(pl);
            pl.displayClientMessage(Component.translatable("msg.aiarsenal.searchlight.aimed", Math.round(be.baseYaw), Math.round(be.basePitch)), true);
        } else {
            be.cycleMode();
            pl.displayClientMessage(Component.translatable("msg.aiarsenal.searchlight.mode." + be.mode).withStyle(be.mode == 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW), true);
            lv.playSound(null, p, be.mode == 0 ? SoundEvents.LEVER_CLICK : SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, be.mode == 0 ? 0.6f : 1.6f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level lv, BlockPos p, BlockState ns, boolean moving) {
        if (!s.is(ns.getBlock()) && !lv.isClientSide && lv.getBlockEntity(p) instanceof SearchlightBlockEntity be) be.clearSpot();
        super.onRemove(s, lv, p, ns, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState s) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState s, BlockGetter lv, BlockPos p, Direction d) {
        return lv.getBlockEntity(p) instanceof SearchlightBlockEntity be && be.signal > 0 ? 15 : 0;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new SearchlightBlockEntity(p, s);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level lv, BlockState s, BlockEntityType<T> t) {
        if (t != ModBlockEntities.SEARCHLIGHT.get()) return null;
        return lv.isClientSide ? null : (l, p, st, be) -> ((SearchlightBlockEntity) be).serverTick();
    }
}
