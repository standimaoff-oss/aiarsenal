/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.util.RandomSource
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.BaseEntityBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.RenderShape
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.StateDefinition$Builder
 *  net.minecraft.world.level.block.state.properties.BlockStateProperties
 *  net.minecraft.world.level.block.state.properties.DirectionProperty
 *  net.minecraft.world.level.block.state.properties.IntegerProperty
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.Vec3
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.VoxelShape
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.block;

import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.NuclearBlast;
import ru.aiforge.realwar.blockentity.BombBlockEntity;
import ru.aiforge.realwar.entity.FallingBombEntity;
import ru.aiforge.realwar.registry.BombType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BombBlock
extends BaseEntityBlock {
    public static final int PARTS = 3;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create((String)"part", (int)0, (int)2);
    private static final VoxelShape SHAPE = Block.box((double)1.5, (double)0.0, (double)1.5, (double)14.5, (double)12.5, (double)14.5);
    public final BombType type;

    public BombBlock(BombType type, BlockBehaviour.Properties props) {
        super(props);
        this.type = type;
        this.registerDefaultState((BlockState)((BlockState)((BlockState)this.stateDefinition.any()).setValue((Property)FACING, Direction.NORTH)).setValue((Property)PART, Integer.valueOf(0)));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(new Property[]{FACING, PART});
    }

    public static BlockPos origin(BlockPos pos, BlockState state) {
        return pos.relative(((Direction)state.getValue((Property)FACING)).getOpposite(), ((Integer)state.getValue((Property)PART)).intValue());
    }

    public static boolean supported(Level level, BlockPos origin, Direction facing) {
        for (int i = 0; i < 3; ++i) {
            BlockPos below = origin.relative(facing, i).below();
            if (!level.getBlockState(below).isFaceSturdy((BlockGetter)level, below, Direction.UP)) continue;
            return true;
        }
        return false;
    }

    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return (Integer)state.getValue((Property)PART) == 0 ? new BombBlockEntity(pos, state) : null;
    }

    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && (Integer)state.getValue((Property)PART) == 0 && !old.is((Block)this)) {
            level.scheduleTick(pos, (Block)this, 2);
        }
    }

    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos npos) {
        Direction f = (Direction)state.getValue((Property)FACING);
        int part = (Integer)state.getValue((Property)PART);
        if (dir == f.getOpposite() && part > 0 ? !neighbor.is((Block)this) || neighbor.getValue((Property)FACING) != f || (Integer)neighbor.getValue((Property)PART) != part - 1 : dir == f && part < 2 && (!neighbor.is((Block)this) || neighbor.getValue((Property)FACING) != f || (Integer)neighbor.getValue((Property)PART) != part + 1)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        BombBlockEntity be;
        BlockEntity blockEntity;
        if (level.isClientSide || !(level instanceof ServerLevel)) {
            return;
        }
        ServerLevel sl = (ServerLevel)level;
        BlockPos origin = BombBlock.origin(pos, state);
        level.scheduleTick(origin, (Block)this, 2);
        if (level.hasNeighborSignal(pos) && (blockEntity = sl.getBlockEntity(origin)) instanceof BombBlockEntity && (be = (BombBlockEntity)blockEntity).isArmed()) {
            this.detonate(sl, origin, sl.getBlockState(origin));
        }
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rnd) {
        if ((Integer)state.getValue((Property)PART) != 0) {
            return;
        }
        if (!BombBlock.supported((Level)level, pos, (Direction)state.getValue((Property)FACING))) {
            this.fall(level, pos, state);
        }
    }

    private void fall(ServerLevel level, BlockPos origin, BlockState state) {
        BombBlockEntity be;
        Direction f = (Direction)state.getValue((Property)FACING);
        BlockEntity blockEntity = level.getBlockEntity(origin);
        boolean armed = blockEntity instanceof BombBlockEntity && (be = (BombBlockEntity)blockEntity).isArmed();
        BombBlock.removeAll(level, origin, f);
        BlockPos mid = origin.relative(f, 1);
        level.addFreshEntity((Entity)FallingBombEntity.create((Level)level, (double)mid.getX() + 0.5, (double)mid.getY() + 0.4, (double)mid.getZ() + 0.5, this.type, armed, f));
    }

    public static void removeAll(ServerLevel level, BlockPos origin, Direction f) {
        for (int i = 0; i < 3; ++i) {
            BlockPos p = origin.relative(f, i);
            if (!(level.getBlockState(p).getBlock() instanceof BombBlock)) continue;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
        }
    }

    public boolean detonate(ServerLevel level, BlockPos origin, BlockState state) {
        if (!((Boolean)RWConfig.COMMON.nuclearEnabled.get()).booleanValue()) {
            return false;
        }
        Direction f = (Direction)state.getValue((Property)FACING);
        BlockPos mid = origin.relative(f, 1);
        Vec3 c = new Vec3((double)mid.getX() + 0.5, (double)mid.getY() + 0.4, (double)mid.getZ() + 0.5);
        BombBlock.removeAll(level, origin, f);
        NuclearBlast.start(level, c, this.type.yieldKt, true);
        return true;
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            Direction f = (Direction)state.getValue((Property)FACING);
            BlockPos origin = BombBlock.origin(pos, state);
            for (int i = 0; i < 3; ++i) {
                BlockState s;
                BlockPos p = origin.relative(f, i);
                if (p.equals(pos) || !(s = level.getBlockState(p)).is((Block)this) || s.getValue((Property)FACING) != f || (Integer)s.getValue((Property)PART) != i) continue;
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            BombBlock.popResource((Level)level, (BlockPos)BombBlock.origin(pos, state), (ItemStack)new ItemStack((ItemLike)this));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BombBlockEntity be;
            BlockEntity blockEntity = level.getBlockEntity(BombBlock.origin(pos, state));
            boolean armed = blockEntity instanceof BombBlockEntity && (be = (BombBlockEntity)blockEntity).isArmed();
            player.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.bomb_status", (Object[])new Object[]{Component.translatable((String)this.getDescriptionId()), Component.translatable((String)(armed ? "message.aiarsenal.rw.status_armed" : "message.aiarsenal.rw.status_safe"))}), true);
        }
        return InteractionResult.sidedSuccess((boolean)level.isClientSide);
    }
}

