package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** r17: non-lethal signal flare on a stake. Arms 2 s after placing; the tripwire runs one block to the right of FACING
 *  (same convention as the F-1 tripwire). Tripped: a red star flare goes up, every living thing within 8 blocks glows
 *  for 10 s and players within 64 blocks get a warning. Spent flares are re-armed with gunpowder. */
public class FlareTrapBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty SPENT = BooleanProperty.create("spent");
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 13, 9.5);

    public FlareTrapBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPENT, false).setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SPENT, ARMED);
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
        if (!level.isClientSide && !st.getValue(SPENT) && !old.is(this)) level.scheduleTick(pos, this, 40);
    }

    public AABB wireBox(BlockState st, BlockPos pos) {
        Direction r = st.getValue(FACING).getClockWise();
        AABB a = new AABB(pos).setMaxY(pos.getY() + 0.6);
        return a.expandTowards(r.getStepX(), 0, r.getStepZ()).inflate(-0.05, 0, -0.05);
    }

    private static boolean triggers(Entity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e instanceof LivingEntity || e instanceof ru.aiforge.realwar.vehicle.VehicleEntity;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState st, ServerLevel level, BlockPos pos, RandomSource r) {
        if (st.getValue(SPENT)) return;
        if (!st.getValue(ARMED)) {
            level.setBlock(pos, st.setValue(ARMED, true), 3);
            level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4f, 1.4f);
            level.scheduleTick(pos, this, 4);
            return;
        }
        if (!level.getEntities((Entity) null, wireBox(st, pos), FlareTrapBlock::triggers).isEmpty()) fire(level, pos, st);
        else level.scheduleTick(pos, this, 4);
    }

    public void fire(ServerLevel level, BlockPos pos, BlockState st) {
        level.setBlock(pos, st.setValue(SPENT, true).setValue(ARMED, false), 3);
        level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3.0f, 0.8f);
        ItemStack fw = new ItemStack(Items.FIREWORK_ROCKET);
        CompoundTag t = fw.getOrCreateTagElement("Fireworks");
        t.putByte("Flight", (byte) 2);
        ListTag ex = new ListTag();
        CompoundTag star = new CompoundTag();
        star.putByte("Type", (byte) 0);
        star.putIntArray("Colors", new int[]{0xFF2A1A});
        star.putIntArray("FadeColors", new int[]{0xFFD0C0});
        star.putBoolean("Trail", true);
        star.putBoolean("Flicker", true);
        ex.add(star);
        t.put("Explosions", ex);
        Vec3 c = Vec3.atBottomCenterOf(pos);
        level.addFreshEntity(new FireworkRocketEntity(level, c.x, c.y + 0.85, c.z, fw));
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(8)))
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(c) < 64 * 64)
                p.displayClientMessage(Component.translatable("msg.aiarsenal.flare.alert", pos.getX(), pos.getY(), pos.getZ()), true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = p.getItemInHand(hand);
        if (!st.getValue(SPENT) || !held.is(Items.GUNPOWDER)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.setBlock(pos, st.setValue(SPENT, false).setValue(ARMED, false), 3);
            level.scheduleTick(pos, this, 40);
            level.playSound(null, pos, SoundEvents.CROSSBOW_LOADING_END, SoundSource.BLOCKS, 0.8f, 1.2f);
            if (!p.isCreative()) held.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
