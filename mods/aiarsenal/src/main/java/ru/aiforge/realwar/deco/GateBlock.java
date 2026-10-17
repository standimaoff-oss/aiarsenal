package ru.aiforge.realwar.deco;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r16: multi-block gate. Every block of the footprint is this block; (ox, oy) = offset from the master (left-bottom) block
 *  along facing.getClockWise() and up. Only the master renders (static frame model + BER for the moving parts); the other
 *  parts are invisible and only carry the collision of their slice. Use / redstone on any part drives the whole gate. */
public class GateBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 4);
    public static final IntegerProperty OY = IntegerProperty.create("oy", 0, 2);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public final GateKind kind;
    private final VoxelShape[] shapes = new VoxelShape[4 * 5 * 3 * 2];
    private final VoxelShape[] outlines = new VoxelShape[4 * 5 * 3];

    public GateBlock(GateKind kind, Properties props) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OX, 0).setValue(OY, 0).setValue(OPEN, false).setValue(POWERED, false));
        for (Direction f : Direction.Plane.HORIZONTAL) {
            for (int ox = 0; ox < 5; ox++) {
                for (int oy = 0; oy < 3; oy++) {
                    VoxelShape closed = shape(kind.boxes(ox, oy, true), f), open = shape(kind.boxes(ox, oy, false), f);
                    shapes[idx(f, ox, oy) * 2] = closed;
                    shapes[idx(f, ox, oy) * 2 + 1] = open;
                    outlines[idx(f, ox, oy)] = closed.isEmpty() ? Shapes.box(0.3, 0.3, 0.3, 0.7, 0.7, 0.7) : closed;
                }
            }
        }
    }

    private static int idx(Direction f, int ox, int oy) {
        return (f.get2DDataValue() * 5 + ox) * 3 + oy;
    }

    /** Canonical (north) pixel boxes rotated clockwise (from above) to the facing: (x, z) -> (16 - z, x) per quarter turn. */
    private static VoxelShape shape(double[][] boxes, Direction f) {
        int q = ((f.get2DDataValue() + 2) % 4);
        VoxelShape s = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int i = 0; i < q; i++) {
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0;
                z1 = x1;
                x0 = nx0;
                x1 = nx1;
            }
            s = Shapes.or(s, Block.box(x0, b[1], z0, x1, b[4], z1));
        }
        return s.optimize();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OX, OY, OPEN, POWERED);
    }

    public static boolean isMaster(BlockState s) {
        return s.getValue(OX) == 0 && s.getValue(OY) == 0;
    }

    public static BlockPos origin(BlockPos pos, BlockState s) {
        return pos.relative(s.getValue(FACING).getClockWise(), -s.getValue(OX)).below(s.getValue(OY));
    }

    public List<BlockPos> footprint(BlockPos origin, Direction f) {
        List<BlockPos> out = new ArrayList<>();
        Direction r = f.getClockWise();
        for (int i = 0; i < kind.w; i++)
            for (int j = 0; j < kind.h; j++) out.add(origin.relative(r, i).above(j));
        return out;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        for (BlockPos p : footprint(ctx.getClickedPos(), f)) {
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(ctx)) {
                if (ctx.getPlayer() != null && !level.isClientSide)
                    ctx.getPlayer().displayClientMessage(Component.translatable("msg.aiarsenal.gate.blocked", kind.w, kind.h), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        Direction r = state.getValue(FACING).getClockWise();
        for (int i = 0; i < kind.w; i++)
            for (int j = 0; j < kind.h; j++)
                if (i != 0 || j != 0) level.setBlock(pos.relative(r, i).above(j), state.setValue(OX, i).setValue(OY, j), 3);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos o = origin(pos, state);
        BlockState m = level.getBlockState(o);
        if (!m.is(this)) return InteractionResult.PASS;
        if (!level.isClientSide) setOpen(level, o, !m.getValue(OPEN), player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public void setOpen(Level level, BlockPos o, boolean open, Player who) {
        BlockState m = level.getBlockState(o);
        if (!m.is(this) || m.getValue(OPEN) == open) return;
        Direction f = m.getValue(FACING);
        for (BlockPos p : footprint(o, f)) {
            BlockState s = level.getBlockState(p);
            if (s.is(this) && s.getValue(FACING) == f && origin(p, s).equals(o)) level.setBlock(p, s.setValue(OPEN, open), 2);
        }
        BlockPos c = o.relative(f.getClockWise(), kind.w / 2);
        level.playSound(null, c, kind.sound(open), SoundSource.BLOCKS, 1.0f, kind == GateKind.BLAST ? 0.6f : 0.9f);
        level.gameEvent(who, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, c);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) return;
        BlockPos o = origin(pos, state);
        BlockState m = level.getBlockState(o);
        if (!m.is(this)) return;
        boolean pw = false;
        for (BlockPos p : footprint(o, m.getValue(FACING))) {
            if (level.hasNeighborSignal(p)) {
                pw = true;
                break;
            }
        }
        if (pw != m.getValue(POWERED)) {
            level.setBlock(o, m.setValue(POWERED, pw), 2);
            setOpen(level, o, pw, null);
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isMaster(state) && !player.isCreative()) {
            BlockPos o = origin(pos, state);
            BlockState m = level.getBlockState(o);
            if (m.is(this)) Block.dropResources(m, level, o, level.getBlockEntity(o), player, player.getMainHandItem());
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            BlockPos o = origin(pos, state);
            Direction f = state.getValue(FACING);
            for (BlockPos p : footprint(o, f)) {
                if (p.equals(pos)) continue;
                BlockState s = level.getBlockState(p);
                if (s.is(this) && s.getValue(FACING) == f && origin(p, s).equals(o)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return outlines[idx(s.getValue(FACING), s.getValue(OX), s.getValue(OY))];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[idx(s.getValue(FACING), s.getValue(OX), s.getValue(OY)) * 2 + (s.getValue(OPEN) ? 1 : 0)];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new GateBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == ModBlockEntities.GATE.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<GateBlockEntity>) GateBlockEntity::clientTick : null;
    }
}
