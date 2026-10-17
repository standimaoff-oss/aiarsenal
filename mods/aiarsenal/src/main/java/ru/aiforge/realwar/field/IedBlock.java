package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r17: improvised explosive device - a 152 mm shell (~3 kg TNT equivalent with fragments) wired to a receiver.
 *  Arms 3 s after placing. PRESSURE: goes off under anything living or a vehicle; REMOTE: only from a linked remote
 *  detonator (sneak + right-click the IED with the detonator, then use it anywhere in the dimension).
 *  Empty-hand right-click toggles the mode, sneak + shears disarms, digging it up armed sets it off. */
public class IedBlock extends HorizontalDirectionalBlock implements Detonatable {
    public static final BooleanProperty REMOTE = BooleanProperty.create("remote");
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    public static final double KG = 3.0;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 4, 14);

    public IedBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(REMOTE, false).setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, REMOTE, ARMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState st, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !st.getValue(ARMED) && !old.is(this)) level.scheduleTick(pos, this, 60);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState st, ServerLevel level, BlockPos pos, RandomSource r) {
        if (!st.getValue(ARMED)) {
            level.setBlock(pos, st.setValue(ARMED, true), 3);
            level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.3f, 2.0f);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !st.getValue(ARMED) || st.getValue(REMOTE) || e.getY() > pos.getY() + 0.5) return;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if (e instanceof LivingEntity || e instanceof ru.aiforge.realwar.vehicle.VehicleEntity) detonate((ServerLevel) level, pos, st);
    }

    public void detonate(ServerLevel level, BlockPos pos, BlockState st) {
        level.removeBlock(pos, false);
        Detonations.queue(level, Vec3.atBottomCenterOf(pos).add(0, 0.2, 0), KG, 2.5, 0, 1);
    }

    @Override
    public void sympathetic(ServerLevel level, BlockPos pos, BlockState state) {
        Detonations.queue(level, Vec3.atBottomCenterOf(pos).add(0, 0.2, 0), KG, 2.5, 0, 2 + level.random.nextInt(6));
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion ex) {
        if (level instanceof ServerLevel sl) sympathetic(sl, pos, defaultBlockState());
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = p.getItemInHand(hand);
        if (p.isShiftKeyDown() && held.is(Items.SHEARS)) {
            if (!level.isClientSide) {
                level.removeBlock(pos, false);
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(asItem())));
                level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.4f);
                held.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!held.isEmpty() || p.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            boolean remote = !st.getValue(REMOTE);
            level.setBlock(pos, st.setValue(REMOTE, remote), 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, remote ? 1.4f : 0.9f);
            p.displayClientMessage(Component.translatable(remote ? "msg.aiarsenal.ied.mode_remote" : "msg.aiarsenal.ied.mode_pressure"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState st, Player p) {
        if (!level.isClientSide && st.getValue(ARMED) && !p.isCreative()) {
            detonate((ServerLevel) level, pos, st);
            return;
        }
        super.playerWillDestroy(level, pos, st, p);
    }
}
