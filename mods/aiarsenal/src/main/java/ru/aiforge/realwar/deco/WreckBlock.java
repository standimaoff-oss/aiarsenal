package ru.aiforge.realwar.deco;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.aiforge.realwar.registry.ModBlockEntities;

/** r17: multi-block vehicle wreck in real dimensions. Every occupied cell of the footprint is this block with its offset
 *  (ox right, oy up, oz forwards along FACING) from the origin cell; each cell renders its own baked chunk of the model
 *  (multipart blockstate) and has its own collision box. The anchor cell (where the player clicked, rear centre on the
 *  ground) carries the block entity (smoke) and the drop. Breaking any cell removes the whole wreck.
 *  Flint and steel sets it smouldering, a water bucket puts it out. */
public class WreckBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty OX = IntegerProperty.create("ox", 0, 5);
    public static final IntegerProperty OY = IntegerProperty.create("oy", 0, 3);
    public static final IntegerProperty OZ = IntegerProperty.create("oz", 0, 9);
    private static final VoxelShape FALLBACK = Block.box(6, 6, 6, 10, 10, 10);
    public final WreckKind kind;
    private final int[] lut = new int[6 * 4 * 10];
    private final VoxelShape[] coll, outline;

    public WreckBlock(WreckKind kind, Properties props) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OX, kind.ax).setValue(OY, 0).setValue(OZ, kind.az));
        java.util.Arrays.fill(lut, -1);
        int n = kind.count();
        coll = new VoxelShape[4 * n];
        outline = new VoxelShape[4 * n];
        for (int i = 0; i < n; i++) {
            lut[li(kind.cells[i * 4], kind.cells[i * 4 + 1], kind.cells[i * 4 + 2])] = i;
            double[] b = new double[6];
            for (int k = 0; k < 6; k++) b[k] = kind.boxes[i * 6 + k];
            boolean solid = kind.cells[i * 4 + 3] != 0;
            for (Direction f : Direction.Plane.HORIZONTAL) {
                VoxelShape s = shape(b, f);
                coll[f.get2DDataValue() * n + i] = solid ? s : Shapes.empty();
                outline[f.get2DDataValue() * n + i] = s;
            }
        }
    }

    private static int li(int ox, int oy, int oz) {
        return (ox * 4 + oy) * 10 + oz;
    }

    /** Canonical (north) pixel box rotated clockwise (from above) to the facing, like the blockstate y rotation. */
    static VoxelShape shape(double[] b, Direction f) {
        int q = (f.get2DDataValue() + 2) % 4;
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        for (int i = 0; i < q; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0;
            z0 = x0;
            z1 = x1;
            x0 = nx0;
            x1 = nx1;
        }
        return Block.box(Math.max(0, x0), Math.max(0, b[1]), Math.max(0, z0), Math.min(16, x1), Math.min(16, b[4]), Math.min(16, z1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, OX, OY, OZ);
    }

    public boolean isAnchor(BlockState s) {
        return s.getValue(OX) == kind.ax && s.getValue(OY) == 0 && s.getValue(OZ) == kind.az;
    }

    public static BlockPos origin(BlockPos pos, BlockState s) {
        Direction f = s.getValue(FACING);
        return pos.relative(f.getClockWise(), -s.getValue(OX)).below(s.getValue(OY)).relative(f, -s.getValue(OZ));
    }

    public static BlockPos cell(BlockPos origin, Direction f, int ox, int oy, int oz) {
        return origin.relative(f.getClockWise(), ox).above(oy).relative(f, oz);
    }

    public BlockPos originFromAnchor(BlockPos anchor, Direction f) {
        return anchor.relative(f.getClockWise(), -kind.ax).relative(f, -kind.az);
    }

    public List<BlockPos> footprint(BlockPos origin, Direction f) {
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < kind.count(); i++) out.add(cell(origin, f, kind.cells[i * 4], kind.cells[i * 4 + 1], kind.cells[i * 4 + 2]));
        return out;
    }

    public BlockState cellState(Direction f, int i) {
        return defaultBlockState().setValue(FACING, f).setValue(OX, kind.cells[i * 4]).setValue(OY, kind.cells[i * 4 + 1]).setValue(OZ, kind.cells[i * 4 + 2]);
    }

    /** Places every cell whose position is free (the anchor always); used by the gametests and the wreck-site feature.
     *  Returns the number of cells placed. */
    public int placeAt(LevelAccessor level, BlockPos anchor, Direction f, int flags) {
        BlockPos o = originFromAnchor(anchor, f);
        int placed = 0;
        for (int i = 0; i < kind.count(); i++) {
            BlockState s = cellState(f, i);
            BlockPos p = cell(o, f, kind.cells[i * 4], kind.cells[i * 4 + 1], kind.cells[i * 4 + 2]);
            if (!isAnchor(s) && !level.getBlockState(p).canBeReplaced()) continue;
            level.setBlock(p, s, flags);
            placed++;
        }
        return placed;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction f = ctx.getHorizontalDirection();
        Level level = ctx.getLevel();
        for (BlockPos p : footprint(originFromAnchor(ctx.getClickedPos(), f), f)) {
            if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced(ctx)) {
                if (ctx.getPlayer() != null && !level.isClientSide)
                    ctx.getPlayer().displayClientMessage(Component.translatable("msg.aiarsenal.wreck.blocked", kind.w, kind.h, kind.l), true);
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        Direction f = state.getValue(FACING);
        BlockPos o = origin(pos, state);
        for (int i = 0; i < kind.count(); i++) {
            BlockState s = cellState(f, i);
            if (isAnchor(s)) continue;
            level.setBlock(cell(o, f, kind.cells[i * 4], kind.cells[i * 4 + 1], kind.cells[i * 4 + 2]), s, 3);
        }
    }

    public BlockPos anchorOf(BlockPos pos, BlockState s) {
        return cell(origin(pos, s), s.getValue(FACING), kind.ax, 0, kind.az);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean fire = held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE), water = held.is(Items.WATER_BUCKET);
        if (!fire && !water) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(anchorOf(pos, state)) instanceof WreckBlockEntity be) {
            be.setSmoking(fire);
            level.playSound(null, pos, fire ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable(fire ? "msg.aiarsenal.wreck.smoke_on" : "msg.aiarsenal.wreck.smoke_off"), true);
            if (fire && !player.isCreative()) {
                if (held.is(Items.FIRE_CHARGE)) held.shrink(1);
                else held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isAnchor(state) && !player.isCreative()) {
            BlockPos a = anchorOf(pos, state);
            BlockState m = level.getBlockState(a);
            if (m.is(this)) Block.dropResources(m, level, a, level.getBlockEntity(a), player, player.getMainHandItem());
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

    private int idx(BlockState s) {
        return lut[li(s.getValue(OX), s.getValue(OY), s.getValue(OZ))];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = idx(s);
        return i < 0 ? FALLBACK : outline[s.getValue(FACING).get2DDataValue() * kind.count() + i];
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int i = idx(s);
        return i < 0 ? Shapes.empty() : coll[s.getValue(FACING).get2DDataValue() * kind.count() + i];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isAnchor(state) ? new WreckBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == ModBlockEntities.WRECK.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<WreckBlockEntity>) WreckBlockEntity::clientTick : null;
    }
}
