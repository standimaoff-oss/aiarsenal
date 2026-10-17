package ru.aiforge.realwar.field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Land mines. They arm 3 s after being placed (ARMED), then:
 *  PMN2     - pressure, anything living or a vehicle stepping on it; 0.24 kg TNT, the victim takes the full charge;
 *  TM62     - anti-tank, only vehicles / very heavy creatures (> 120 kg class); 7.5 kg TNT under the hull;
 *  OZM72    - bounding: click, 0.4 s later it jumps to ~1 m and bursts with 2400 fragments (lethal 25 m);
 *  MON50    - directional: break-wire 6 blocks in front of its convex face, 54 deg sheaf of 540 balls;
 *  TRIPWIRE - F-1 on a stake, 2-block wire to the right of the facing; UZRGM delay 3.2-4 s after the pull.
 * Sneak + right-click with shears disarms (drops the mine); mining an armed mine without shears sets it off.
 */
public class MineBlock extends HorizontalDirectionalBlock implements Detonatable {
    public enum Kind { PMN2, TM62, MON50, OZM72, TRIPWIRE }

    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    public static final int ARM_TICKS = 60;
    private static final VoxelShape PMN = Block.box(6.5, 0, 6.5, 9.5, 1.2, 9.5);
    private static final VoxelShape TM = Block.box(5.4, 0, 5.4, 10.6, 2.4, 10.6);
    private static final VoxelShape OZM = Block.box(6.8, 0, 6.8, 9.2, 2.2, 9.2);
    private static final VoxelShape MON = Block.box(5.8, 0, 6.2, 10.2, 4.6, 9.8);
    private static final VoxelShape TRIP = Block.box(6, 0, 6, 9, 5.2, 10);
    public final Kind kind;

    public MineBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ARMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ARMED);
    }

    /** MON-50: the convex face points where the player looks; the others face the player. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getHorizontalDirection();
        return this.defaultBlockState().setValue(FACING, this.kind == Kind.MON50 ? d : d.getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState st, LevelReader lv, BlockPos p) {
        return Block.canSupportCenter(lv, p.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState st, Direction d, BlockState ns, net.minecraft.world.level.LevelAccessor lv, BlockPos p, BlockPos np) {
        return d == Direction.DOWN && !st.canSurvive(lv, p) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(st, d, ns, lv, p, np);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return switch (this.kind) {
            case PMN2 -> PMN;
            case TM62 -> TM;
            case OZM72 -> OZM;
            case MON50 -> MON;
            case TRIPWIRE -> TRIP;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState st, BlockGetter lv, BlockPos p, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean isPathfindable(BlockState st, BlockGetter lv, BlockPos p, PathComputationType t) {
        return true;          // mobs walk into minefields
    }

    @Override
    public void onPlace(BlockState st, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && !st.getValue(ARMED)) level.scheduleTick(pos, this, ARM_TICKS);
    }

    @Override
    public void tick(BlockState st, ServerLevel level, BlockPos pos, RandomSource r) {
        if (!st.getValue(ARMED)) {
            level.setBlock(pos, st.setValue(ARMED, true), 3);
            level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 0.4f, 1.6f);
            if (this.kind == Kind.MON50 || this.kind == Kind.TRIPWIRE) level.scheduleTick(pos, this, 4);
            return;
        }
        if (this.kind == Kind.MON50 || this.kind == Kind.TRIPWIRE) {
            if (!level.getEntities((Entity) null, this.wireBox(st, pos), MineBlock::triggers).isEmpty()) {
                this.trigger(level, pos, st, null);
                return;
            }
            level.scheduleTick(pos, this, 4);
        }
    }

    /** Break-wire volume: MON-50 straight ahead 6 blocks, tripwire 2 blocks to the right of FACING (model: +X when facing north). */
    public AABB wireBox(BlockState st, BlockPos pos) {
        Direction f = st.getValue(FACING);
        Vec3 c = Vec3.atBottomCenterOf(pos);
        Vec3 d = this.kind == Kind.MON50 ? Vec3.atLowerCornerOf(f.getNormal()) : Vec3.atLowerCornerOf(f.getClockWise().getNormal());
        double len = this.kind == Kind.MON50 ? 6.0 : 2.1;
        Vec3 a = c.add(d.scale(0.4)), b = c.add(d.scale(len));
        return new AABB(a.x, c.y, a.z, b.x, c.y + (this.kind == Kind.MON50 ? 1.2 : 0.5), b.z).inflate(0.15, 0, 0.15);
    }

    private static boolean triggers(Entity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        return e instanceof LivingEntity || e instanceof ru.aiforge.realwar.vehicle.VehicleEntity || e instanceof ru.aiforge.realwar.craft.CraftEntity;
    }

    private boolean heavy(Entity e) {
        return ru.aiforge.realwar.fort.FortRules.crushesObstacles(e) || e instanceof ru.aiforge.realwar.craft.CraftEntity
                || (e instanceof LivingEntity && e.getBbWidth() * e.getBbWidth() * e.getBbHeight() > 3.0);
    }

    @Override
    public void entityInside(BlockState st, Level level, BlockPos pos, Entity e) {
        if (level.isClientSide || !st.getValue(ARMED) || !triggers(e)) return;
        switch (this.kind) {
            case PMN2, OZM72 -> {
                if (e.getY() < pos.getY() + 0.5) this.trigger((ServerLevel) level, pos, st, e);
            }
            case TM62 -> {
                if (this.heavy(e)) this.trigger((ServerLevel) level, pos, st, e);
            }
            default -> {
            }
        }
    }

    public void trigger(ServerLevel level, BlockPos pos, BlockState st, Entity victim) {
        level.removeBlock(pos, false);
        Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 0.1, 0);
        switch (this.kind) {
            case PMN2 -> {
                if (victim != null) victim.hurt(level.damageSources().explosion(null, null), victim instanceof LivingEntity ? 30.0f : 25.0f);
                Detonations.queue(level, c, 0.24, 0.3, 0, 1);
            }
            case TM62 -> {
                if (victim != null) victim.hurt(level.damageSources().explosion(null, null), 260.0f);
                Detonations.queue(level, c, 7.5, 0.4, 0, 1);
            }
            case OZM72 -> {
                level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.0f, 0.8f);
                level.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.3, c.z, 8, 0.1, 0.4, 0.1, 0.02);
                Detonations.queue(level, c.add(0, 1.0, 0), 0.66, 1.4, 0, 8);
            }
            case MON50 -> {
                level.playSound(null, pos, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.2f);
                Vec3 d = Vec3.atLowerCornerOf(st.getValue(FACING).getNormal());
                Detonations.directional(level, c.add(0, 0.2, 0), d, 0.7, Math.cos(Math.toRadians(27)), 30.0, 40.0f, 2);
            }
            case TRIPWIRE -> {
                level.playSound(null, pos, SoundEvents.TRIPWIRE_DETACH, SoundSource.BLOCKS, 1.0f, 1.0f);
                level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 0.5f, 1.8f);
                Detonations.queue(level, c.add(0, 0.25, 0), 0.06, 4.0, 0, 64 + level.random.nextInt(16));
            }
        }
    }

    @Override
    public void sympathetic(ServerLevel level, BlockPos pos, BlockState state) {
        double kg = switch (this.kind) { case TM62 -> 7.5; case MON50 -> 0.7; case OZM72 -> 0.66; case PMN2 -> 0.24; case TRIPWIRE -> 0.06; };
        Detonations.queue(level, Vec3.atBottomCenterOf(pos).add(0, 0.1, 0), kg, 0.6, 0, 2 + level.random.nextInt(6));
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion ex) {
        if (level instanceof ServerLevel sl) this.sympathetic(sl, pos, this.defaultBlockState());
    }

    /** Sneak + right-click with shears: disarm and pick up. */
    @Override
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = p.getItemInHand(hand);
        if (!p.isShiftKeyDown() || !held.is(Items.SHEARS)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            level.removeBlock(pos, false);
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, new ItemStack(this.asItem())));
            level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8f, 1.4f);
            held.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(hand));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Digging up an armed mine without disarming it first is a bad idea. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState st, Player p) {
        if (!level.isClientSide && st.getValue(ARMED) && !p.isCreative()) {
            this.trigger((ServerLevel) level, pos, st, p);
            return;
        }
        super.playerWillDestroy(level, pos, st, p);
    }
}
