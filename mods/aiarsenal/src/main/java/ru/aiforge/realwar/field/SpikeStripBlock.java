package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r17: spike strip laid across the road (perpendicular to where the player looks). People are wounded and slowed;
 *  vehicles and aircraft taxiing over it lose most of their speed and take tyre damage every second. */
public class SpikeStripBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape NS = Block.box(0, 0, 5.5, 16, 2, 10.5);
    private static final VoxelShape EW = Block.box(5.5, 0, 0, 10.5, 2, 16);

    public SpikeStripBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState st, LevelReader lv, BlockPos p) {
        return Block.canSupportCenter(lv, p.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState st, Direction d, BlockState ns, net.minecraft.world.level.LevelAccessor lv, BlockPos p, BlockPos np) {
        return d == Direction.DOWN && !st.canSurvive(lv, p) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(st, d, ns, lv, p, np);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || e.getY() > pos.getY() + 0.4) return;
        if (e instanceof ru.aiforge.realwar.vehicle.VehicleEntity || e instanceof ru.aiforge.realwar.craft.CraftEntity) {
            e.setDeltaMovement(e.getDeltaMovement().multiply(0.55, 1.0, 0.55));
            if (e.tickCount % 20 == 0) e.hurt(level.damageSources().cactus(), 6.0f);
            return;
        }
        if (!(e instanceof LivingEntity le)) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        e.makeStuckInBlock(st, new Vec3(0.45, 1.0, 0.45));
        le.hurt(level.damageSources().cactus(), 2.0f);
    }
}
