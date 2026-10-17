package ru.aiforge.realwar.fort;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Fortification line that joins its neighbours like a fence: sandbag walls (solid, blast-resistant), barbed-wire fence and
 * concertina coil (walk-through: entangle + cut, crushed by vehicles driving over them).
 */
public class LinkedFortBlock extends CrossCollisionBlock {
    public enum Kind { SANDBAGS, WIRE, CONCERTINA, EGOZA, BRUNO }

    public final Kind kind;

    public LinkedFortBlock(Kind kind, Properties props) {
        super(kind == Kind.SANDBAGS ? 9.0f : 2.0f, kind == Kind.SANDBAGS ? 9.0f : (kind == Kind.WIRE ? 2.0f : 13.0f),
                16.0f, 16.0f, 16.0f, props);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(WATERLOGGED, false));
    }

    public boolean wire() {
        return this.kind != Kind.SANDBAGS;
    }

    /** Joins: same family (sandbags with sandbags; wire with wire/coil), fences, walls, gates; sandbags also to solid faces. */
    public boolean connectsTo(BlockState s, boolean sturdy, Direction dir) {
        Block b = s.getBlock();
        if (b instanceof LinkedFortBlock o) return o.wire() == this.wire();
        if (b instanceof FenceBlock || b instanceof WallBlock) return true;
        if (b instanceof FenceGateBlock) return FenceGateBlock.connectsToDirection(s, dir);
        return this.kind == Kind.SANDBAGS && sturdy && !isExceptionForConnection(s);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockGetter lv = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        BlockState st = this.defaultBlockState().setValue(WATERLOGGED, ctx.getLevel().getFluidState(p).getType() == Fluids.WATER);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos np = p.relative(d);
            BlockState ns = lv.getBlockState(np);
            st = st.setValue(PROPERTY_BY_DIRECTION.get(d), this.connectsTo(ns, ns.isFaceSturdy(lv, np, d.getOpposite()), d.getOpposite()));
        }
        return st;
    }

    @Override
    public BlockState updateShape(BlockState st, Direction d, BlockState ns, LevelAccessor lv, BlockPos p, BlockPos np) {
        if (st.getValue(WATERLOGGED)) lv.scheduleTick(p, Fluids.WATER, Fluids.WATER.getTickDelay(lv));
        if (d.getAxis().isHorizontal()) {
            return st.setValue(PROPERTY_BY_DIRECTION.get(d), this.connectsTo(ns, ns.isFaceSturdy(lv, np, d.getOpposite()), d.getOpposite()));
        }
        return super.updateShape(st, d, ns, lv, p, np);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState st) {
        return st.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(st);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return this.wire() ? Shapes.empty() : super.getCollisionShape(st, lv, p, ctx);
    }

    @Override
    public boolean isPathfindable(BlockState st, BlockGetter lv, BlockPos p, PathComputationType t) {
        return false;
    }

    /** Wire entangles and cuts whoever pushes through; heavy vehicles flatten it. */
    @Override
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (!this.wire()) return;
        if (FortRules.crushesObstacles(e)) {
            if (!level.isClientSide) level.destroyBlock(pos, false, e);
            return;
        }
        if (!(e instanceof LivingEntity le)) return;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return;
        double f = this.kind == Kind.WIRE ? 0.30 : (this.kind == Kind.CONCERTINA ? 0.18 : 0.12);
        e.makeStuckInBlock(st, new Vec3(f, 0.55, f));
        if (!level.isClientSide && (e.xOld != e.getX() || e.zOld != e.getZ()) && e.tickCount % 10 == 0) {
            le.hurt(level.damageSources().cactus(), this.kind == Kind.WIRE ? 1.5f : (this.kind == Kind.CONCERTINA ? 2.5f : 3.5f));
        }
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, @javax.annotation.Nullable BlockGetter lv, java.util.List<net.minecraft.network.chat.Component> tip, net.minecraft.world.item.TooltipFlag flag) {
        tip.add(net.minecraft.network.chat.Component.translatable("tooltip.aiarsenal." + net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(this).getPath()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
