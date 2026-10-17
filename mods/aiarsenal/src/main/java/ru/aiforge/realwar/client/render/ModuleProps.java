package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3d;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * r20 (msg 20): the simulated internal modules drawn as small Blender props (models/hitcam_modules.glb, built by
 * tools/blender/hitcam_modules_r20.py) at the places where the server's damage zones are
 * ({@link VehicleDamage#zone}): engine block in the engine compartment, the ammunition as real rounds in the layout of
 * the vehicle (carousel / turret bustle / sponson racks / launcher pack), the turret traverse drive at the ring and
 * the crew on their seats. Used by the hit camera (X-ray) and the top-down damage card. Also classifies the vehicle
 * GLB nodes (hull / turret / barrel / tracks / wheels / interior) so hit parts can be painted on the model itself.
 */
public final class ModuleProps {
    public static final ResourceLocation RL = new ResourceLocation("aiarsenal", "models/glb/hitcam_modules.glb");
    /** paint/show ids: DamageState.ENGINE, AMMO, TURRET (= turret drive), and CREW + seat index. */
    public static final int CREW = 100;

    public static final int C_HULL = 0, C_TURRET = 1, C_BARREL = 2, C_TRACK_L = 3, C_TRACK_R = 4, C_WHEEL = 5, C_INTERIOR = 6;
    private static final Map<GlbModel, int[]> CATS = new WeakHashMap<>();

    private ModuleProps() {
    }

    // ------------------------------------------------------------------------------------------ node categories

    /** Category of every node of a vehicle model (walks up the parents: barrel before turret before hull). */
    public static int[] categories(GlbModel m) {
        return CATS.computeIfAbsent(m, mm -> {
            int n = mm.nodes.size();
            int[] c = new int[n];
            for (int i = 0; i < n; i++) {
                int cat = C_HULL;
                for (int k = i, guard = 0; k >= 0 && guard < 64; k = mm.nodes.get(k).parent, guard++) {
                    int got = own(mm.nodes.get(k).name);
                    if (got >= 0) {
                        cat = got;
                        break;
                    }
                }
                c[i] = cat;
            }
            return c;
        });
    }

    private static int own(String n) {
        if (n == null) return -1;
        if (n.startsWith("barrel")) return C_BARREL;
        if (n.contains("link_") || n.startsWith("spk") || n.startsWith("idl")) {
            return n.contains("_R") ? C_TRACK_R : C_TRACK_L;      // glTF +X = left: "_L" = left track
        }
        if (n.startsWith("rw_") || n.startsWith("rr_") || n.startsWith("wheel_")) return C_WHEEL;
        if (n.startsWith("interior")) return C_INTERIOR;
        if (n.startsWith("launcher_yaw") || n.startsWith("launcher_pitch") || n.startsWith("sec_") || n.startsWith("flak_")) return C_TURRET;
        return -1;
    }

    // ------------------------------------------------------------------------------------------ crew

    public static int crewCount(VehicleKind k) {
        return ru.aiforge.realwar.damage.Crew.count(k);
    }

    /** r21: seat i of {@link ru.aiforge.realwar.damage.Crew#roles} (shared with the server hit resolution). */
    public static Vector3d crewPos(VehicleKind k, VehicleDamage.Geo g, float az, int i) {
        return ru.aiforge.realwar.damage.Crew.pos(k, g, az, i);
    }

    public static Vector3d turretPoint(VehicleDamage.Geo g, float az, double lx, double y, double lz) {
        return ru.aiforge.realwar.damage.Crew.turretPoint(g, az, lx, y, lz);
    }

    // ------------------------------------------------------------------------------------------ drawing

    /**
     * Draws the props in the current (hull-frame) pose stack. {@code show} filters the ids, {@code paint} is called
     * before each prop and sets GlbRenderer.XRAY_RGB / XRAY (hit camera) or FLAT_RGB (card).
     */
    public static void draw(PoseStack ps, MultiBufferSource bs, VehicleKind k, VehicleDamage.Geo g, float az, IntPredicate show, IntConsumer paint) {
        GlbAssets.Loaded lm = GlbAssets.get(RL);
        if (lm == null) return;
        int nEngine = node(lm, "mod_engine"), nRound = node(lm, "mod_round"), nCrew = node(lm, "mod_crew"), nDrive = node(lm, "mod_drive");
        double fz = k.fireAxisZ();
        // ---- engine: in the engine compartment, sitting on the floor
        if (k.drivable() && show.test(DamageState.ENGINE) && nEngine >= 0) {
            double tw = g.tracked() ? g.trackW() : 0.1;
            double compL = g.hz() * 0.64, compW = 2 * Math.max(0.3, g.hx() - tw), compH = 2 * g.hy();
            double ez = g.engineFront() ? g.cz() + g.hz() * 0.68 : g.cz() - g.hz() * 0.68;
            boolean transverse = k == VehicleKind.T90M || k == VehicleKind.TOS1A || k == VehicleKind.MSTA || k == VehicleKind.BMP3;
            double s = transverse ? Math.min(compW * 0.75 / 1.12, Math.min(compL * 0.85 / 0.75, compH * 0.7 / 0.70))
                    : Math.min(compL * 0.8 / 1.12, Math.min(compW * 0.7 / 0.75, compH * 0.7 / 0.70));
            paint.accept(DamageState.ENGINE);
            ps.pushPose();
            ps.translate(g.cx(), g.cy() - g.hy() + 0.36 * s + 0.05, ez);
            if (transverse) ps.mulPose(Axis.YP.rotationDegrees(90f));
            ps.scale((float) s, (float) s, (float) s);
            GlbRenderer.renderNode(lm, nEngine, ps, bs, 0xF000F0, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        // ---- turret traverse drive at the ring
        if (g.turret() && g.yp() != null && show.test(DamageState.TURRET) && nDrive >= 0) {
            double s = Math.max(0.8, g.hy() / 0.8);
            Vector3d p = turretPoint(g, az, 0.62 * s, g.yp()[1] - 0.12 * s, 0.25 * s * fz);
            paint.accept(DamageState.TURRET);
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.mulPose(Axis.YP.rotationDegrees(az));
            ps.scale((float) s, (float) s, (float) s);
            GlbRenderer.renderNode(lm, nDrive, ps, bs, 0xF000F0, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        // ---- ammunition in the vehicle's layout
        if (show.test(DamageState.AMMO) && nRound >= 0) {
            paint.accept(DamageState.AMMO);
            float big = (float) Math.max(1.0, g.hy() / 0.85);
            switch (g.ammo()) {
                case VehicleDamage.BUSTLE -> {
                    if (g.yp() != null) for (int r = 0; r < 3; r++) for (int c = 0; c < 6; c++) {
                        Vector3d p = turretPoint(g, az, -0.85 + c * 0.34, g.yp()[1] + 0.2 + r * 0.21, -1.95 * fz);
                        round(ps, bs, lm, nRound, p, az, 0f, 0.9f);
                    }
                }
                case VehicleDamage.PACK -> {
                    if (g.yp() != null) for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) {
                        Vector3d p = turretPoint(g, az, -0.6 + c * 0.4, g.yp()[1] + 0.45 + r * 0.32, 1.0 * fz);
                        round(ps, bs, lm, nRound, p, az, 0f, 2.2f);
                    }
                }
                case VehicleDamage.SPONSON -> {
                    double zc = g.yp() != null ? g.yp()[2] : g.cz();
                    double tw = g.tracked() ? g.trackW() : 0.1;
                    for (int side = -1; side <= 1; side += 2) for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) {
                        double x = g.cx() + side * (g.hx() - tw - 0.25 * big - c * 0.2 * big);
                        Vector3d p = new Vector3d(x, g.cy() + (0.12 + r * 0.2) * big, zc);
                        round(ps, bs, lm, nRound, p, 0f, 0f, 0.95f * big);
                    }
                }
                default -> {                          // CAROUSEL: 22 rounds lying radially under the turret ring
                    double x0 = g.yp() != null ? g.yp()[0] : g.cx(), z0 = g.yp() != null ? g.yp()[2] : g.cz();
                    double y = g.cy() - g.hy() + 0.12;
                    for (int i = 0; i < 22; i++) {
                        float a = (float) (360.0 * i / 22.0) + az;
                        double ar = Math.toRadians(a);
                        Vector3d p = new Vector3d(x0 + Math.sin(ar) * 0.55, y, z0 + Math.cos(ar) * 0.55);
                        round(ps, bs, lm, nRound, p, a, 0f, 0.55f);
                    }
                }
            }
        }
        // ---- crew
        if (nCrew >= 0) {
            int n = crewCount(k);
            float s = (float) Math.min(1.0, 0.82 * Math.max(0.85, g.hy() / 0.8));
            for (int i = 0; i < n; i++) {
                if (!show.test(CREW + i)) continue;
                Vector3d p = crewPos(k, g, az, i);
                boolean inTurret = ru.aiforge.realwar.damage.Crew.inTurret(k, g, i);
                paint.accept(CREW + i);
                ps.pushPose();
                ps.translate(p.x, p.y + 0.12 * s, p.z);
                ps.mulPose(Axis.YP.rotationDegrees(inTurret ? az : 0f));
                if (fz < 0) ps.mulPose(Axis.YP.rotationDegrees(180f));
                ps.scale(s, s, s);
                GlbRenderer.renderNode(lm, nCrew, ps, bs, 0xF000F0, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
        }
    }

    private static void round(PoseStack ps, MultiBufferSource bs, GlbAssets.Loaded lm, int node, Vector3d p, float yawDeg, float pitchDeg, float len) {
        ps.pushPose();
        ps.translate(p.x, p.y, p.z);
        ps.mulPose(Axis.YP.rotationDegrees(yawDeg));
        if (pitchDeg != 0f) ps.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        ps.scale(len, len, len);
        GlbRenderer.renderNode(lm, node, ps, bs, 0xF000F0, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    private static int node(GlbAssets.Loaded lm, String name) {
        for (int i = 0; i < lm.model.nodes.size(); i++) if (name.equals(lm.model.nodes.get(i).name)) return i;
        return -1;
    }
}
