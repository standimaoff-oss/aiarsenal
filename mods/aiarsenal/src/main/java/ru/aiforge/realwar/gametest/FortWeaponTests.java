package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.handheld.AtMissileEntity;
import ru.aiforge.realwar.handheld.HandheldKind;
import ru.aiforge.realwar.handheld.LauncherWeaponItem;
import ru.aiforge.realwar.registry.ModBlocks;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.vehicle.MlrsEntity;

/** Fortification blocks (fence-like joining, collision rules) and shoulder-fired weapons (hit, homing, top attack, lock). */
@GameTestHolder(value = "aiarsenal")
@PrefixGameTestTemplate(value = false)
public final class FortWeaponTests {
    private FortWeaponTests() {
    }

    private static ServerPlayer player(GameTestHelper h) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "fw-test"));
    }

    private static int floor(GameTestHelper h, int rx, int rz, int half) {
        ServerLevel level = h.getLevel();
        BlockPos c = h.absolutePos(new BlockPos(rx, 0, rz));
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
        for (int dx = -half; dx <= half; ++dx)
            for (int dz = -half; dz <= half; ++dz)
                for (int y = top - 1; y <= top + 30; ++y)
                    level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), y == top - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        return top;
    }

    /** Sandbags join each other into walls and corners; barbed wire joins wire and fences but not sandbags. */
    @GameTest(template = "arena", batch = "rw_fort", timeoutTicks = 40)
    public static void fort_blocks_connect_like_fences(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 6);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 15)).atY(top);
        BlockPos a = c.west(), b = c, d = c.south();
        for (BlockPos p : new BlockPos[]{a, b, d}) place(level, p, ModBlocks.SANDBAGS.get());
        BlockState sb = level.getBlockState(b);
        h.assertTrue(sb.getValue(CrossCollisionBlock.WEST) && sb.getValue(CrossCollisionBlock.SOUTH) && !sb.getValue(CrossCollisionBlock.NORTH), "sandbag corner: " + sb);
        h.assertTrue(level.getBlockState(a).getValue(CrossCollisionBlock.EAST), "sandbag wall end: " + level.getBlockState(a));
        BlockPos w1 = c.north(2), w2 = w1.east(), f = w1.west();
        level.setBlock(f, Blocks.OAK_FENCE.defaultBlockState(), 3);
        place(level, w1, ModBlocks.BARBED_WIRE.get());
        place(level, w2, ModBlocks.BARBED_WIRE.get());
        BlockState ws = level.getBlockState(w1);
        h.assertTrue(ws.getValue(CrossCollisionBlock.EAST) && ws.getValue(CrossCollisionBlock.WEST), "wire joins wire + fence: " + ws);
        BlockPos w3 = c.north();
        place(level, w3, ModBlocks.BARBED_WIRE.get());
        h.assertTrue(!level.getBlockState(w3).getValue(CrossCollisionBlock.SOUTH), "wire must not join sandbags");
        // collision: wire walk-through, sandbags/hedgehog/barrier solid, camo net no collision
        h.assertTrue(level.getBlockState(w1).getCollisionShape(level, w1, CollisionContext.empty()).isEmpty(), "wire collision");
        h.assertTrue(!level.getBlockState(b).getCollisionShape(level, b, CollisionContext.empty()).isEmpty(), "sandbag collision");
        BlockPos hh = c.east(3);
        level.setBlock(hh, ModBlocks.CZECH_HEDGEHOG.get().defaultBlockState(), 3);
        h.assertTrue(level.getBlockState(hh).getCollisionShape(level, hh, CollisionContext.empty()).max(Direction.Axis.Y) > 1.1, "hedgehog cannot be jumped");
        BlockPos net = c.east(3).above(3);
        level.setBlock(net, ModBlocks.CAMO_NET.get().defaultBlockState(), 3);
        h.assertTrue(level.getBlockState(net).getCollisionShape(level, net, CollisionContext.empty()).isEmpty(), "camo net collision");
        h.succeed();
    }

    /** Places like a player would: the new block reads its neighbours, the neighbours update towards it. */
    private static void place(ServerLevel level, BlockPos p, net.minecraft.world.level.block.Block b) {
        level.setBlock(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(b.defaultBlockState(), level, p), 3);
    }

    /** RPG-7: an unguided PG-7VL flies straight and damages a BM-21 20 m away. */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 100)
    public static void rpg_hits_vehicle(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 14);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 3)).atY(top);
        MlrsEntity grad = (MlrsEntity) ModEntities.GRAD.get().create(level);
        grad.moveTo(c.getX() + 0.5, top, c.getZ() + 0.5 + 22, 0, 0);
        level.addFreshEntity(grad);
        float hp0 = grad.hp();
        ServerPlayer p = player(h);
        p.moveTo(c.getX() + 0.5, top, c.getZ() + 0.5, 0, 0);
        Vec3 from = new Vec3(c.getX() + 0.5, top + 1.2, c.getZ() + 1.5);
        Vec3 dir = grad.getBoundingBox().getCenter().subtract(from).normalize();
        AtMissileEntity m = AtMissileEntity.launch(level, p, HandheldKind.RPG7, from, dir, -1, null);
        TestChunks.follow(m);
        h.onEachTick(() -> {
            if (m.isRemoved()) {
                h.assertTrue(grad.isRemoved() || grad.hp() < hp0, "RPG detonated but the BM-21 is undamaged: " + grad.hp());
                grad.discard();
                h.succeed();
            }
        });
    }

    /** Igla: the IR missile homes onto a phantom off the launch axis and fuzes by proximity. */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 240)
    public static void igla_homes_on_air_target(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 14);
        BlockPos lp = h.absolutePos(new BlockPos(4, 0, 4));
        Vec3 from = new Vec3(lp.getX() + 0.5, top + 2.0, lp.getZ() + 0.5);
        Phantom ph = EntityType.PHANTOM.create(level);
        ph.moveTo(from.x + 18, from.y + 30, from.z + 60, 0, 0);
        ph.setNoAi(true);
        ph.setNoGravity(true);
        ph.setPersistenceRequired();
        level.addFreshEntity(ph);
        ServerPlayer p = player(h);
        p.moveTo(from.x, from.y, from.z, 0, 0);
        // launched straight up-north, the target is far off-axis -> has to turn
        TestChunks.path(level, from, ph.position());
        // gunner fires roughly along the line of sight; the seeker has to correct ~6 degrees
        Vec3 los = ph.position().subtract(from).normalize();
        // launch once the forced chunks are entity-ticking (other batches may be generating terrain concurrently)
        AtMissileEntity[] mm = new AtMissileEntity[1];
        h.runAfterDelay(20, () -> {
            mm[0] = AtMissileEntity.launch(level, p, HandheldKind.IGLA, from, los.add(0.1, -0.05, 0).normalize(), ph.getId(), null);
            TestChunks.follow(mm[0]);
        });
        h.onEachTick(() -> {
            AtMissileEntity m = mm[0];
            if (m != null && !m.isRemoved() && !level.isPositionEntityTicking(m.blockPosition())) {
                m.tick();
            }
            if (m != null && m.isRemoved()) {
                h.assertTrue(!ph.isAlive() || ph.getHealth() < ph.getMaxHealth(), "Igla burst without hurting the target: " + AtMissileEntity.lastDetonation + " phantom@" + ph.blockPosition().toShortString());
                ph.discard();
                h.succeed();
            }
        });
    }

    /** r25 Stinger: the faster IR missile homes onto the same off-axis phantom (impact fuze, 2.5 m tolerance). */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 240)
    public static void stinger_homes_on_air_target(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 14);
        BlockPos lp = h.absolutePos(new BlockPos(4, 0, 4));
        Vec3 from = new Vec3(lp.getX() + 0.5, top + 2.0, lp.getZ() + 0.5);
        Phantom ph = EntityType.PHANTOM.create(level);
        ph.moveTo(from.x + 18, from.y + 30, from.z + 60, 0, 0);
        ph.setNoAi(true);
        ph.setNoGravity(true);
        ph.setPersistenceRequired();
        level.addFreshEntity(ph);
        ServerPlayer p = player(h);
        p.moveTo(from.x, from.y, from.z, 0, 0);
        // launched straight up-north, the target is far off-axis -> has to turn
        TestChunks.path(level, from, ph.position());
        // gunner fires roughly along the line of sight; the seeker has to correct ~6 degrees
        Vec3 los = ph.position().subtract(from).normalize();
        // launch once the forced chunks are entity-ticking (other batches may be generating terrain concurrently)
        AtMissileEntity[] mm = new AtMissileEntity[1];
        h.runAfterDelay(20, () -> {
            mm[0] = AtMissileEntity.launch(level, p, HandheldKind.STINGER, from, los.add(0.1, -0.05, 0).normalize(), ph.getId(), null);
            TestChunks.follow(mm[0]);
        });
        h.onEachTick(() -> {
            AtMissileEntity m = mm[0];
            if (m != null && !m.isRemoved() && !level.isPositionEntityTicking(m.blockPosition())) {
                m.tick();
            }
            if (m != null && m.isRemoved()) {
                h.assertTrue(!ph.isAlive() || ph.getHealth() < ph.getMaxHealth(), "Stinger burst without hurting the target: " + AtMissileEntity.lastDetonation + " phantom@" + ph.blockPosition().toShortString());
                ph.discard();
                h.succeed();
            }
        });
    }

    /** r25: the Igla-S firearm only launches after the seeker cooled down and locked an air target. */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 60)
    public static void igla_gun_needs_lock_then_fires(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 14);
        BlockPos lp = h.absolutePos(new BlockPos(4, 0, 4));
        Vec3 from = new Vec3(lp.getX() + 0.5, top, lp.getZ() + 0.5);
        Phantom ph = EntityType.PHANTOM.create(level);
        ph.moveTo(from.x + 2, from.y + 14, from.z + 26, 0, 0);
        ph.setNoAi(true);
        ph.setNoGravity(true);
        ph.setPersistenceRequired();
        level.addFreshEntity(ph);
        ServerPlayer p = player(h);
        p.moveTo(from.x, from.y, from.z, 0, 0);
        Vec3 to = ph.getBoundingBox().getCenter().subtract(p.getEyePosition());
        p.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)))));
        net.minecraft.world.item.ItemStack gun = new net.minecraft.world.item.ItemStack(ModItems.IGLA.get());
        ru.aiforge.realwar.handheld.FirearmItem.setAmmo(gun, 1);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        int t = 1;
        for (; t <= 20; t++) ru.aiforge.realwar.handheld.Firearms.seekerTick(p, gun, ru.aiforge.realwar.handheld.GunKind.IGLA, true, t);
        h.assertTrue(ru.aiforge.realwar.handheld.Firearms.cool(gun) < 100 && ru.aiforge.realwar.handheld.Firearms.lock(gun) == 0, "seeker locked before cooling down");
        for (; t <= 120 && ru.aiforge.realwar.handheld.Firearms.lock(gun) < 100; t++)
            ru.aiforge.realwar.handheld.Firearms.seekerTick(p, gun, ru.aiforge.realwar.handheld.GunKind.IGLA, true, t);
        h.assertTrue(ru.aiforge.realwar.handheld.Firearms.lock(gun) >= 100 && ru.aiforge.realwar.handheld.Firearms.target(gun) == ph.getId(),
                "no lock on the phantom after " + t + " ticks: lock " + ru.aiforge.realwar.handheld.Firearms.lock(gun));
        h.assertTrue(t > 30 + 25, "lock was too fast: " + t);
        ru.aiforge.realwar.handheld.Firearms.fire(p, gun, ru.aiforge.realwar.handheld.GunKind.IGLA, true);
        h.assertTrue(ru.aiforge.realwar.handheld.FirearmItem.ammo(gun) == 0 && ru.aiforge.realwar.handheld.Firearms.lock(gun) == 0, "tube not spent / lock not reset");
        var mm = level.getEntitiesOfClass(AtMissileEntity.class, new net.minecraft.world.phys.AABB(BlockPos.containing(from)).inflate(6));
        h.assertTrue(!mm.isEmpty() && mm.get(0).kind() == HandheldKind.IGLA, "9M342 not launched");
        mm.forEach(Entity::discard);
        ph.discard();
        h.succeed();
    }

    /** Javelin: from 60 m it climbs well above the launch point, dives and hits the locked BM-21 from above. */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 260)
    public static void javelin_top_attack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 4, 15, 3);
        BlockPos c = h.absolutePos(new BlockPos(4, 0, 15)).atY(top);
        Vec3 from = new Vec3(c.getX() + 0.5, top + 1.5, c.getZ() + 0.5);
        Vec3 gpos = new Vec3(c.getX() + 60.5, 0, c.getZ() + 0.5);
        // the target is outside the arena: load the corridor first, then spawn and fire
        TestChunks.path(level, from, gpos);
        TestChunks.path(level, from.add(0, 0, 16), gpos.add(0, 0, 16));
        MlrsEntity[] grad = {null};
        AtMissileEntity[] m = {null};
        float[] hp0 = {0};
        double[] maxY = {from.y};
        double[] lastVy = {0};
        h.onEachTick(() -> {
            if (grad[0] == null) {
                if (!level.isPositionEntityTicking(BlockPos.containing(gpos))) return;
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(gpos.x), (int) Math.floor(gpos.z));
                grad[0] = (MlrsEntity) ModEntities.GRAD.get().create(level);
                grad[0].moveTo(gpos.x, gy, gpos.z, 90, 0);
                level.addFreshEntity(grad[0]);
                hp0[0] = grad[0].hp();
                ServerPlayer p = player(h);
                p.moveTo(from.x, top, from.z, 0, 0);
                m[0] = AtMissileEntity.launch(level, p, HandheldKind.JAVELIN, from, new Vec3(1, 0.25, 0), grad[0].getId(), null);
                TestChunks.follow(m[0]);
                return;
            }
            if (!m[0].isRemoved()) {
                maxY[0] = Math.max(maxY[0], m[0].getY());
                lastVy[0] = m[0].getDeltaMovement().y;
                return;
            }
            h.assertTrue(maxY[0] > from.y + 12, "no top-attack climb: apex " + (maxY[0] - from.y) + " " + AtMissileEntity.lastDetonation);
            h.assertTrue(lastVy[0] < -0.5, "did not dive onto the target: vy " + lastVy[0] + " " + AtMissileEntity.lastDetonation);
            h.assertTrue(grad[0].isRemoved() || grad[0].hp() < hp0[0] * 0.6, "Javelin hit too weak: " + grad[0].hp() + "/" + hp0[0] + " " + AtMissileEntity.lastDetonation);
            grad[0].discard();
            h.succeed();
        });
    }

    /** The launcher's seeker picks the vehicle in the cone (Javelin) and ignores ground targets for Igla. */
    @GameTest(template = "arena", batch = "rw_hh", timeoutTicks = 40)
    public static void seeker_picks_targets_in_cone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        int top = floor(h, 15, 15, 14);
        BlockPos c = h.absolutePos(new BlockPos(15, 0, 2)).atY(top);
        MlrsEntity grad = (MlrsEntity) ModEntities.GRAD.get().create(level);
        grad.moveTo(c.getX() + 0.5, top, c.getZ() + 0.5 + 25, 0, 0);
        level.addFreshEntity(grad);
        ServerPlayer p = player(h);
        p.moveTo(c.getX() + 0.5, top, c.getZ() + 0.5, 0, 0);
        Vec3 eye = p.getEyePosition();
        Vec3 to = grad.getBoundingBox().getCenter().subtract(eye);
        p.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)))));
        LauncherWeaponItem jav = (LauncherWeaponItem) ModItems.JAVELIN.get();
        LauncherWeaponItem.Seek s = jav.seek(level, p);
        h.assertTrue(s != null && s.entity() == grad, "Javelin seeker did not pick the BM-21: " + (s == null ? "none" : String.valueOf(s.entity())));
        LauncherWeaponItem.Seek s2 = LauncherWeaponItem.seek(level, p, HandheldKind.IGLA);
        h.assertTrue(s2 == null || s2.entity() != grad, "Igla locked a ground vehicle");
        LauncherWeaponItem.Seek s3 = LauncherWeaponItem.seek(level, p, HandheldKind.STINGER);
        h.assertTrue(s3 == null || s3.entity() != grad, "Stinger locked a ground vehicle");
        grad.discard();
        h.succeed();
    }
}
