/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.Containers
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.BaseEntityBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.RenderShape
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityTicker
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.VoxelShape
 *  org.jetbrains.annotations.Nullable
 */
package ru.aiforge.realwar.missile;

import ru.aiforge.realwar.missile.LaunchPadBlockEntity;
import ru.aiforge.realwar.missile.LaunchPadPartBlock;
import ru.aiforge.realwar.registry.ModS3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class LaunchPadBlock
extends BaseEntityBlock {
    public static final double[] HEIGHT = new double[]{0.0, 1.0, 1.5, 2.0};
    public final int level;
    private final VoxelShape shape;

    public LaunchPadBlock(int level, BlockBehaviour.Properties props) {
        super(props);
        this.level = level;
        this.shape = Block.box((double)0.0, (double)0.0, (double)0.0, (double)16.0, (double)(16.0 * HEIGHT[level]), (double)16.0);
    }

    public int n() {
        return this.level;
    }

    public VoxelShape getShape(BlockState s, BlockGetter g, BlockPos p, CollisionContext c) {
        return this.shape;
    }

    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModS3.PAD_BE.get() ? (l, p, s, be) -> LaunchPadBlockEntity.tick(l, p, s, (LaunchPadBlockEntity)be) : null;
    }

    public boolean canFit(Level l, BlockPos pos) {
        for (int dx = 0; dx < this.level; ++dx) {
            for (int dz = 0; dz < this.level; ++dz) {
                BlockPos p = pos.offset(dx, 0, dz);
                if (!(l.getBlockState(p).canBeReplaced() || dx == 0 && dz == 0)) {
                    return false;
                }
                if (!l.getWorldBorder().isWithinBounds(p)) {
                    return false;
                }
                BlockPos below = p.below();
                if (l.getBlockState(below).isFaceSturdy((BlockGetter)l, below, Direction.UP)) continue;
                return false;
            }
        }
        return true;
    }

    public void setPlacedBy(Level l, BlockPos pos, BlockState state, @Nullable LivingEntity who, ItemStack stack) {
        if (l.isClientSide) {
            return;
        }
        for (int dx = 0; dx < this.level; ++dx) {
            for (int dz = 0; dz < this.level; ++dz) {
                if (dx == 0 && dz == 0) continue;
                l.setBlock(pos.offset(dx, 0, dz), (BlockState)((LaunchPadPartBlock)(ModS3.PAD_PART.get())).defaultBlockState().setValue((Property)LaunchPadPartBlock.LEVEL, Integer.valueOf(this.level)), 3);
            }
        }
    }

    public void onRemove(BlockState state, Level l, BlockPos pos, BlockState nw, boolean moving) {
        if (!state.is(nw.getBlock())) {
            LaunchPadBlockEntity be;
            BlockEntity blockEntity;
            if (!l.isClientSide && (blockEntity = l.getBlockEntity(pos)) instanceof LaunchPadBlockEntity && (be = (LaunchPadBlockEntity)blockEntity).kind() != null) {
                Containers.dropItemStack((Level)l, (double)((double)pos.getX() + 0.5), (double)((double)pos.getY() + 1.0), (double)((double)pos.getZ() + 0.5), (ItemStack)new ItemStack((ItemLike)ModS3.missileItem(be.kind()).get()));
            }
            if (!l.isClientSide) {
                for (int dx = 0; dx < this.level; ++dx) {
                    for (int dz = 0; dz < this.level; ++dz) {
                        BlockPos p;
                        if (dx == 0 && dz == 0 || !l.getBlockState(p = pos.offset(dx, 0, dz)).is((Block)ModS3.PAD_PART.get())) continue;
                        l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        super.onRemove(state, l, pos, nw, moving);
    }

    public InteractionResult use(BlockState state, Level l, BlockPos pos, Player pl, InteractionHand hand, BlockHitResult hit) {
        if (l.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = l.getBlockEntity(pos);
        if (blockEntity instanceof LaunchPadBlockEntity) {
            LaunchPadBlockEntity be = (LaunchPadBlockEntity)blockEntity;
            be.interact(pl, hand);
        }
        return InteractionResult.CONSUME;
    }

    public ItemStack getCloneItemStack(BlockGetter g, BlockPos pos, BlockState state) {
        return new ItemStack((ItemLike)this);
    }

    public static void noRoom(Player p, int n) {
        p.displayClientMessage((Component)Component.translatable((String)"message.aiarsenal.rw.pad.noroom", (Object[])new Object[]{n + "\u00d7" + n}), true);
    }
}

