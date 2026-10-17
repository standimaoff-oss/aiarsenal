/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.RenderShape
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.StateDefinition$Builder
 *  net.minecraft.world.level.block.state.properties.IntegerProperty
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.VoxelShape
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.LaunchPadBlock;
import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.registry.ModS3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LaunchPadPartBlock
extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create((String)"level", (int)2, (int)3);

    public LaunchPadPartBlock(BlockBehaviour.Properties p) {
        super(p);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue((Property)LEVEL, Integer.valueOf(2)));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(new Property[]{LEVEL});
    }

    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return Block.box((double)0.0, (double)0.0, (double)0.0, (double)16.0, (double)(16.0 * LaunchPadBlock.HEIGHT[(Integer)s.getValue((Property)LEVEL)]), (double)16.0);
    }

    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    public static BlockPos findMaster(Level l, BlockPos pos, int level) {
        for (int dx = 0; dx >= -(level - 1); --dx) {
            for (int dz = 0; dz >= -(level - 1); --dz) {
                BlockPos p = pos.offset(dx, 0, dz);
                Block block = l.getBlockState(p).getBlock();
                if (!(block instanceof LaunchPadBlock)) continue;
                LaunchPadBlock b = (LaunchPadBlock)block;
                if (b.level != level) continue;
                return p;
            }
        }
        return null;
    }

    public InteractionResult use(BlockState state, Level l, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        BlockPos m = LaunchPadPartBlock.findMaster(l, pos, (Integer)state.getValue((Property)LEVEL));
        if (m == null) {
            return InteractionResult.PASS;
        }
        if (l.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = l.getBlockEntity(m);
        if (blockEntity instanceof LaunchPadBlockEntity) {
            LaunchPadBlockEntity be = (LaunchPadBlockEntity)blockEntity;
            be.interact(pl, hand);
        }
        return InteractionResult.CONSUME;
    }

    public void onRemove(BlockState state, Level l, BlockPos pos, BlockState nw, boolean moving) {
        BlockPos m;
        if (!state.is(nw.getBlock()) && !l.isClientSide && (m = LaunchPadPartBlock.findMaster(l, pos, (Integer)state.getValue((Property)LEVEL))) != null) {
            l.destroyBlock(m, true);
        }
        super.onRemove(state, l, pos, nw, moving);
    }

    public ItemStack getCloneItemStack(BlockGetter g, BlockPos pos, BlockState state) {
        return new ItemStack((Integer)state.getValue((Property)LEVEL) == 2 ? (ItemLike)ModS3.PAD_2.get() : (ItemLike)ModS3.PAD_3.get());
    }
}

