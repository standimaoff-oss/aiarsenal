package ru.aiforge.bm21grad.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.network.ModNetwork;
import ru.aiforge.bm21grad.network.VehicleCommandPacket;

/**
 * Client side of the GUNNER mode: the crew member's view lays the launcher.
 * <ul>
 *   <li>Crosshair on the ground (within ~800 blocks of loaded terrain) = direct lay: the fire-control solution for that point
 *       is computed and the launcher follows it.</li>
 *   <li>Crosshair in the sky = indirect lay: traverse = view yaw, elevation = view pitch (0..55°); the impact point is predicted
 *       with the rocket's own integrator.</li>
 * </ul>
 * The laying command is streamed to the server (max 10/s, only on change); firing designates the predicted point and starts
 * the mission, so the normal laying / ripple / dispersion rules apply.
 */
public final class GunnerControl {
    public static final int MIN_DIRECT = 12;

    public static boolean zoom;
    public static Vec3 aimPoint;        // predicted / designated impact point (ground surface)
    public static boolean direct;       // true = crosshair on terrain
    public static float cmdTurret, cmdElev;
    public static Ballistics.Solution solution;
    public static String blockReason;   // why a shot at aimPoint would be refused (lang suffix) or null
    public static double range;
    private static int lastSent = Integer.MIN_VALUE;
    private static int sendCooldown;
    private static int age;

    private GunnerControl() {
    }

    public static boolean active(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof Bm21Entity v && v.isGunnerMode() && !v.isDestroyed();
    }

    /** First-person sight: 1.35 m left of the tube pack centre line, 1.6 m above the elevation pivot, turning with the turret. */
    public static Vec3 sightPos(Bm21Entity v, float pt) {
        float yaw = Mth.rotLerp(pt, v.yRotO, v.getYRot());
        float tur = v.prevTurret + Mth.wrapDegrees(v.getTurret() - v.prevTurret) * pt;
        Vec3 base = v.getPosition(pt);
        Vec3 pivot = new Vec3(0, 2.45, -2.62).yRot((float) Math.toRadians(-yaw)).add(base);
        return new Vec3(1.35, 1.6, 0.1).yRot((float) Math.toRadians(-(yaw + tur))).add(pivot);
    }

    public static void tick(Minecraft mc, Bm21Entity v) {
        age++;
        if (!v.isGunnerMode() || mc.player == null) {
            zoom = false;
            aimPoint = null;
            lastSent = Integer.MIN_VALUE;
            return;
        }
        var pl = mc.player;
        Vec3 eye = sightPos(v, 1f);
        Vec3 look = Vec3.directionFromRotation(pl.getXRot(), pl.getYRot());
        HitResult hr = mc.level.clip(new ClipContext(eye, eye.add(look.scale(800)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, pl));
        direct = false;
        solution = null;
        blockReason = null;
        RocketType type = v.fireType();
        Vec3 piv = v.launcherPivot();
        if (hr.getType() == HitResult.Type.BLOCK && hr.getLocation().distanceTo(eye) >= MIN_DIRECT) {
            // direct lay: the point under the reticle is the target; the launcher follows the fire-control solution
            BlockHitResult bh = (BlockHitResult) hr;
            Vec3 hit = bh.getLocation();
            BlockPos tp = BlockPos.containing(hit.x, hit.y - 0.01, hit.z);
            direct = true;
            aimPoint = new Vec3(hit.x, tp.getY() + 1.0, hit.z);
            solution = v.solve(tp, type);
            if (solution.ok()) {
                cmdTurret = Mth.wrapDegrees((float) (Ballistics.yawTo(piv, tp.getX() + 0.5, tp.getZ() + 0.5) - v.getYRot()));
                cmdElev = (float) solution.elevationDeg();
            } else {
                cmdTurret = Mth.wrapDegrees(pl.getYRot() - v.getYRot()); // keep traversing with the view, hold elevation
            }
        } else {
            // indirect lay: traverse = view yaw, elevation = view pitch; impact predicted by the rocket integrator
            cmdTurret = Mth.wrapDegrees(pl.getYRot() - v.getYRot());
            cmdElev = Mth.clamp(-pl.getXRot(), 0, 55);
            aimPoint = v.predictImpact(cmdTurret, cmdElev);
            solution = aimPoint == null ? null : v.solve(BlockPos.containing(aimPoint.x, aimPoint.y - 0.01, aimPoint.z), type);
        }
        range = aimPoint == null ? 0 : Math.sqrt(Math.pow(aimPoint.x - piv.x, 2) + Math.pow(aimPoint.z - piv.z, 2));
        if (aimPoint != null) {
            if (solution != null && !solution.ok()) blockReason = solution.reason();
            else if (Math.abs(cmdTurret) < 30 && cmdElev < 12) blockReason = "cab_sector";
        }
        if (v.getMissionPhase() == Bm21Entity.PH_IDLE) {
            int packed = Bm21Entity.packAim(cmdTurret, cmdElev);
            if (--sendCooldown <= 0 && (packed != lastSent || age % 20 == 0)) {
                ModNetwork.CHANNEL.sendToServer(VehicleCommandPacket.of(v, VehicleCommandPacket.AIM, packed));
                lastSent = packed;
                sendCooldown = 2;
            }
        } else lastSent = Integer.MIN_VALUE;
        // impact marker: red ring the size of the dispersion ellipse, visible through the sight
        if (aimPoint != null && age % 3 == 0 && aimPoint.distanceTo(eye) < 256) {
            double r = Math.max(2.0, range * 0.012);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(1f, 0.15f, 0.1f), 2.5f);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + age * 0.05;
                mc.level.addParticle(dust, aimPoint.x + Math.cos(a) * r, aimPoint.y + 0.15, aimPoint.z + Math.sin(a) * r, 0, 0, 0);
            }
            mc.level.addParticle(dust, aimPoint.x, aimPoint.y + 0.3, aimPoint.z, 0, 0, 0);
        }
    }

    /** LMB / fire key in gunner mode: designate the point under the reticle, then open fire. */
    public static void fire(Minecraft mc, Bm21Entity v) {
        if (aimPoint == null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.aiarsenal.no_aim"), true);
            return;
        }
        if (blockReason != null) { // the fire-control computer refuses: say why instead of designating a bad point
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.aiarsenal.reason." + blockReason)
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        BlockPos tp = BlockPos.containing(aimPoint.x, aimPoint.y - 0.01, aimPoint.z);
        ModNetwork.CHANNEL.sendToServer(VehicleCommandPacket.target(v, tp));
        ModNetwork.CHANNEL.sendToServer(VehicleCommandPacket.of(v, VehicleCommandPacket.FIRE, 0));
    }
}
