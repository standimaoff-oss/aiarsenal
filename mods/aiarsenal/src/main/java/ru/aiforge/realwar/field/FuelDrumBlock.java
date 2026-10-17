package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 200 l steel drum (585 x 880 mm). Upright, or lying on chocks when placed while sneaking. A full drum goes up when a blast,
 * fire or a burning projectile reaches it: fuel-air burst + burning spray that sets the surroundings alight (and other drums).
 */
public class FuelDrumBlock extends HorizontalDirectionalBlock implements Detonatable {
    public static final BooleanProperty LYING = BooleanProperty.create("lying");
    private static final VoxelShape UP = Block.box(3.3, 0, 3.3, 12.7, 14.1, 12.7);
    private static final VoxelShape SIDE_Z = Block.box(3.3, 0, 1, 12.7, 9.5, 15);
    private static final VoxelShape SIDE_X = Block.box(1, 0, 3.3, 15, 9.5, 12.7);
    /** TNT-equivalent of the burst and fire radius: gasoline, diesel, oil. */
    public final double kg;
    public final int fire;

    public FuelDrumBlock(double kg, int fire, Properties props) {
        super(props);
        this.kg = kg;
        this.fire = fire;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LYING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, LYING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        boolean lying = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(LYING, lying);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        if (!st.getValue(LYING)) return UP;
        return st.getValue(FACING).getAxis() == Direction.Axis.Z ? SIDE_Z : SIDE_X;
    }

    public void ignite(Level level, BlockPos pos, int delay) {
        if (!(level instanceof ServerLevel sl)) return;
        level.removeBlock(pos, false);
        Detonations.queue(sl, Vec3.atCenterOf(pos), this.kg, 0.15, this.fire, delay);
    }

    @Override
    public void sympathetic(ServerLevel level, BlockPos pos, BlockState state) {
        Detonations.queue(level, Vec3.atCenterOf(pos), this.kg, 0.15, this.fire, 3 + level.random.nextInt(8));
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion ex) {
        if (level instanceof ServerLevel sl) this.sympathetic(sl, pos, this.defaultBlockState());
    }

    @Override
    public void onProjectileHit(Level level, BlockState st, BlockHitResult hit, Projectile pr) {
        if (!level.isClientSide && pr.isOnFire()) this.ignite(level, hit.getBlockPos(), 2);
    }

    // flammable: fire spreading into the drum makes it burst (Forge hooks)
    @Override
    public boolean isFlammable(BlockState st, BlockGetter lv, BlockPos p, Direction face) {
        return true;
    }

    @Override
    public int getFlammability(BlockState st, BlockGetter lv, BlockPos p, Direction face) {
        return 40;
    }

    @Override
    public int getFireSpreadSpeed(BlockState st, BlockGetter lv, BlockPos p, Direction face) {
        return 20;
    }

    @Override
    public void onCaughtFire(BlockState st, Level level, BlockPos pos, Direction face, LivingEntity igniter) {
        this.ignite(level, pos, 20 + level.random.nextInt(40));
    }
}
