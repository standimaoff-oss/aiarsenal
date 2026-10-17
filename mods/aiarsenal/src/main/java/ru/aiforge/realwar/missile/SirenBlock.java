/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.core.Direction$Axis
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.context.BlockPlaceContext
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.LevelReader
 *  net.minecraft.world.level.block.BaseEntityBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.RenderShape
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityTicker
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.StateDefinition$Builder
 *  net.minecraft.world.level.block.state.properties.BlockStateProperties
 *  net.minecraft.world.level.block.state.properties.DoubleBlockHalf
 *  net.minecraft.world.level.block.state.properties.EnumProperty
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.Shapes
 *  net.minecraft.world.phys.shapes.VoxelShape
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.SirenBlockEntity;
import ru.aiforge.realwar.registry.ModS3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SirenBlock
extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER = Block.box((double)4.0, (double)0.0, (double)4.0, (double)12.0, (double)16.0, (double)12.0);
    private static final VoxelShape UPPER = Shapes.or((VoxelShape)Block.box((double)4.0, (double)0.0, (double)4.0, (double)12.0, (double)10.0, (double)12.0), (VoxelShape)Block.box((double)1.0, (double)10.0, (double)1.0, (double)15.0, (double)14.0, (double)15.0));

    public SirenBlock(BlockBehaviour.Properties p) {
        super(p);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(new Property[]{HALF});
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockPos p = c.getClickedPos();
        if (p.getY() < c.getLevel().getMaxBuildHeight() - 1 && c.getLevel().getBlockState(p.above()).canBeReplaced(c)) {
            return this.defaultBlockState();
        }
        return null;
    }

    public void setPlacedBy(Level l, BlockPos pos, BlockState st, @Nullable LivingEntity who, ItemStack stack) {
        l.setBlock(pos.above(), (BlockState)st.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    public boolean canSurvive(BlockState st, LevelReader l, BlockPos pos) {
        if (st.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return l.getBlockState(pos.below()).is((Block)this);
        }
        BlockPos b = pos.below();
        return l.getBlockState(b).isFaceSturdy((BlockGetter)l, b, Direction.UP);
    }

    public BlockState updateShape(BlockState st, Direction d, BlockState nb, LevelAccessor l, BlockPos pos, BlockPos npos) {
        DoubleBlockHalf h = (DoubleBlockHalf)st.getValue(HALF);
        if (!(d.getAxis() != Direction.Axis.Y || h == DoubleBlockHalf.LOWER != (d == Direction.UP) || nb.is((Block)this) && nb.getValue(HALF) != h)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (h == DoubleBlockHalf.LOWER && d == Direction.DOWN && !st.canSurvive((LevelReader)l, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return st;
    }

    public void playerWillDestroy(Level l, BlockPos pos, BlockState st, Player pl) {
        BlockPos lo;
        if (!l.isClientSide && pl.isCreative() && st.getValue(HALF) == DoubleBlockHalf.UPPER && l.getBlockState(lo = pos.below()).is((Block)this)) {
            l.setBlock(lo, Blocks.AIR.defaultBlockState(), 35);
        }
        super.playerWillDestroy(l, pos, st, pl);
    }

    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    public RenderShape getRenderShape(BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return st.getValue(HALF) == DoubleBlockHalf.LOWER ? new SirenBlockEntity(pos, st) : null;
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState st, BlockEntityType<T> t) {
        return t == ModS3.SIREN_BE.get() ? (lv, p, s, be) -> SirenBlockEntity.tick(lv, p, s, (SirenBlockEntity)be) : null;
    }

    private static BlockPos lower(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
    }

    public void neighborChanged(BlockState st, Level l, BlockPos pos, Block b, BlockPos from, boolean moving) {
        if (l.isClientSide) {
            return;
        }
        BlockPos lo = SirenBlock.lower(pos, st);
        boolean pw = l.hasNeighborSignal(lo) || l.hasNeighborSignal(lo.above());
        BlockEntity blockEntity = l.getBlockEntity(lo);
        if (blockEntity instanceof SirenBlockEntity) {
            SirenBlockEntity be = (SirenBlockEntity)blockEntity;
            be.setPowered(pw);
        }
    }

    public InteractionResult use(BlockState st, Level l, BlockPos pos, Player pl, InteractionHand h, BlockHitResult hit) {
        BlockEntity blockEntity;
        if (!l.isClientSide && (blockEntity = l.getBlockEntity(SirenBlock.lower(pos, st))) instanceof SirenBlockEntity) {
            SirenBlockEntity be = (SirenBlockEntity)blockEntity;
            be.toggleTest();
        }
        return InteractionResult.sidedSuccess((boolean)l.isClientSide);
    }
}

