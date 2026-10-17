package ru.aiforge.realwar.field;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** r17: electric fence - connects like iron bars / fences. The whole connected run (up to 256 sections) is live while
 *  any section receives a redstone signal: touching it shocks (4 damage), throws the victim back and sparks. */
public class ElectricFenceBlock extends IronBarsBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ElectricFenceBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
                .setValue(WATERLOGGED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(POWERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState st, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !old.is(this)) refresh(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState st, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide) refresh(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState st, Level level, BlockPos pos, BlockState ns, boolean moving) {
        super.onRemove(st, level, pos, ns, moving);
        if (!level.isClientSide && !ns.is(this))
            for (Direction d : Direction.Plane.HORIZONTAL)
                if (level.getBlockState(pos.relative(d)).is(this)) refresh(level, pos.relative(d));
    }

    /** Flood-fill the connected run and set POWERED on every section to "any section has a redstone signal". */
    public void refresh(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> run = new ArrayList<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        boolean live = false;
        while (!q.isEmpty() && run.size() < 256) {
            BlockPos p = q.poll();
            if (!level.getBlockState(p).is(this)) continue;
            run.add(p);
            if (!live && level.hasNeighborSignal(p)) live = true;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.add(n) && level.getBlockState(n).is(this)) q.add(n);
            }
        }
        for (BlockPos p : run) {
            BlockState s = level.getBlockState(p);
            if (s.getValue(POWERED) != live) level.setBlock(p, s.setValue(POWERED, live), 2);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !st.getValue(POWERED) || !(e instanceof LivingEntity le)) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (le.hurt(level.damageSources().lightningBolt(), 4.0f)) {
            double dx = pos.getX() + 0.5 - e.getX(), dz = pos.getZ() + 0.5 - e.getZ();
            if (dx * dx + dz * dz < 1e-4) dx = 0.1;
            le.knockback(0.7, dx, dz);
            level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.9f, 1.6f);
            ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 14, 0.2, 0.3, 0.2, 0.3);
        }
    }

    @Override
    public void animateTick(BlockState st, Level level, BlockPos pos, RandomSource r) {
        if (st.getValue(POWERED) && r.nextFloat() < 0.25f) {
            double y = pos.getY() + 0.25 + r.nextInt(4) * 0.235;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 0.9, y, pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 0.9, 0, 0, 0);
        }
    }
}
