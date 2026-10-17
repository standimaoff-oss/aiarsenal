package ru.aiforge.realwar.deco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Red obstruction light (rotating = false: blinks 1 Hz) or orange rotating warning beacon. Animation = renderer only. */
public class BeaconLampBlock extends LampBlock implements EntityBlock {
    public final boolean rotating;
    private static final VoxelShape RED = Block.box(6.5, 0, 6.5, 9.5, 2.8, 9.5);
    private static final VoxelShape ROT = Block.box(6, 0, 6, 10, 4, 10);

    public BeaconLampBlock(boolean rotating, Properties p) {
        super(0, p);
        this.rotating = rotating;
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader lv, BlockPos p) {
        return Block.canSupportCenter(lv, p.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        return d == Direction.DOWN && !s.canSurvive(lv, p) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter lv, BlockPos p, CollisionContext c) {
        return rotating ? ROT : RED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new BeaconLampBlockEntity(p, s);
    }
}
