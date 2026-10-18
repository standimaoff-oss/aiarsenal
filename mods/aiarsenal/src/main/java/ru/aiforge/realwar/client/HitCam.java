package ru.aiforge.realwar.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import ru.aiforge.realwar.client.render.GlbAssets;
import ru.aiforge.realwar.client.render.GlbRenderer;
import ru.aiforge.realwar.client.render.ModuleProps;
import ru.aiforge.realwar.damage.Armour;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.damage.VehicleDamage;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.network.HitCamPacket;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * War Thunder style hit camera, top right (r20, reworked after msg 20):
 * <ul>
 *   <li>SMALL window (22 % of the GUI width, 100..140 px) - result header, one line with the round, penetration /
 *   effective armour, plate and range, then the X-ray and a short list of what was damaged;</li>
 *   <li>the hit parts are painted on the model itself: the GLB is drawn translucent and its barrel / turret /
 *   left or right track nodes turn orange (damaged) or red (destroyed); the internal modules are small Blender
 *   props ({@link ModuleProps}: V-12 engine, real rounds in the vehicle's ammo layout, turret drive, seated crew);</li>
 *   <li>several shots on the same target within the display time are collected in ONE camera (up to 6): every shell
 *   flies along its own true line, the header shows the best result and "xN";</li>
 *   <li>shell flight animation, ricochet off the plate normal, sparks on a non-penetration, spall cone inside on a
 *   penetration (the server's own rays, re-generated from the seed).</li>
 * </ul>
 */
public final class HitCam {
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/misc/white.png");
    private static final float DUR = 5.5f, T_HIT = 0.85f;
    private static final int MAX_SHOTS = 6;

    /** One shell of the current camera. */
    static final class Shot {
        final HitCamPacket m;
        final long t0;
        final Vector3d entry, dir, normal, start;
        final Vector3d[] spall;
        final double[] spallLen;
        final int crewHit, crewDead;

        Shot(HitCamPacket m, long t0, Vector3d entry, Vector3d dir, Vector3d normal, Vector3d start, Vector3d[] spall, double[] spallLen, int crewHit, int crewDead) {
            this.m = m;
            this.t0 = t0;
            this.entry = entry;
            this.dir = dir;
            this.normal = normal;
            this.start = start;
            this.spall = spall;
            this.spallLen = spallLen;
            this.crewHit = crewHit;
            this.crewDead = crewDead;
        }

        float t(long now) {
            return (now - this.t0) / 1000f;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static VehicleKind kind;
    private static VehicleDamage.Geo geo;
    private static float[] ang = {0, 0};
    private static int target = Integer.MIN_VALUE;

    private HitCam() {
    }

    // ===================================================================================== receive

    public static void receive(HitCamPacket m) {
        if (m.kind() < 0 || m.kind() >= VehicleKind.values().length) return;
        long now = Util.getMillis();
        boolean live = !SHOTS.isEmpty() && SHOTS.get(SHOTS.size() - 1).t(now) < DUR - 0.5f;
        if (!live || m.target() != target || m.kind() != (kind == null ? -1 : kind.ordinal())) SHOTS.clear();
        if (SHOTS.size() >= MAX_SHOTS) SHOTS.remove(0);
        target = m.target();
        kind = VehicleKind.values()[m.kind()];
        geo = VehicleDamage.geo(kind, (m.flags() & HitCamPacket.F_TRACKED) != 0, (m.flags() & HitCamPacket.F_TURRET) != 0);
        ang = new float[]{m.az(), m.el()};
        boolean tg = (m.flags() & HitCamPacket.F_TURRET_GONE) != 0;
        Vector3d entry = new Vector3d(m.entry()[0], m.entry()[1], m.entry()[2]);
        Vector3d dir = new Vector3d(m.dir()[0], m.dir()[1], m.dir()[2]).normalize();
        Vector3d normal = new Vector3d(m.normal()[0], m.normal()[1], m.normal()[2]);
        Vector3d start = new Vector3d(entry).sub(new Vector3d(dir).mul(Math.min(9.0, kind.spec.length() * 0.9 + 2.0)));
        Vector3d st = new Vector3d(entry).add(new Vector3d(dir).mul(0.08));
        Vector3d[] spall = m.spallN() > 0 ? Armour.spall(m.seed(), dir, m.spallAng(), m.spallN()) : new Vector3d[0];
        double[] spallLen = new double[spall.length];
        for (int i = 0; i < spall.length; i++) spallLen[i] = Armour.travel(geo, kind, tg, ang, st, spall[i], 4.5);
        // r21: the server reports exactly which crew members were hit / killed by this shot
        SHOTS.add(new Shot(m, now, entry, dir, normal, start, spall, spallLen, m.crewHit(), m.crewDead()));
    }

    private static double distToSeg(Vector3d p, Vector3d a, Vector3d b) {
        Vector3d ab = new Vector3d(b).sub(a);
        double l2 = ab.lengthSquared();
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, new Vector3d(p).sub(a).dot(ab) / l2));
        return new Vector3d(a).add(ab.mul(t)).distance(p);
    }

    // ===================================================================================== state

    private static String tr(String k, Object... a) {
        return Component.translatable(k, a).getString();
    }

    /** Module state over the shots that have already hit (0 ok, 1 damaged, 2 destroyed). */
    private static int state(int module, long now) {
        int st = 0;
        for (Shot sh : SHOTS) {
            if (sh.t(now) < T_HIT) continue;
            for (int i = 0; i < sh.m.mods().length; i++) if (sh.m.mods()[i] == module) st = Math.max(st, sh.m.states()[i]);
        }
        return st;
    }

    /** Seconds since the first shot that changed this module (for the colour fade / blink), or -1. */
    private static float since(int module, long now) {
        for (Shot sh : SHOTS) {
            float t = sh.t(now);
            if (t < T_HIT) continue;
            for (int i = 0; i < sh.m.mods().length; i++) if (sh.m.mods()[i] == module && sh.m.states()[i] > 0) return t - T_HIT;
        }
        return -1f;
    }

    /** Crew member state over the shots that have already hit: 0 untouched, 1 wounded, 2 killed. */
    private static int crewState(int i, long now) {
        int st = 0;
        for (Shot sh : SHOTS) {
            if (sh.t(now) < T_HIT) continue;
            if ((sh.crewDead >> i & 1) != 0) return 2;
            if ((sh.crewHit >> i & 1) != 0) st = 1;
        }
        return st;
    }

    private static int rank(Shot sh) {
        if ((sh.m.flags() & HitCamPacket.F_DESTROYED) != 0) return 5;
        return switch (sh.m.result()) {
            case Armour.PEN -> 4;
            case Armour.MODULE -> 3;
            case Armour.NO_PEN -> 2;
            default -> 1;
        };
    }

    /** base colour -> damaged orange / destroyed red, faded in over 0.35 s and blinking for 1.2 s. */
    private static float[] dmgColour(float[] base, int st, float since) {
        if (st <= 0 || since < 0) return base;
        float hk = Math.min(1f, since / 0.35f);
        float blink = since < 1.2f ? 0.78f + 0.22f * (float) Math.sin(since * 18) : 1f;
        float tr = st >= 2 ? 0.95f : 1.0f, tg = st >= 2 ? 0.16f : 0.60f, tb = st >= 2 ? 0.10f : 0.08f;
        return new float[]{(base[0] + (tr - base[0]) * hk) * blink, (base[1] + (tg - base[1]) * hk) * blink, (base[2] + (tb - base[2]) * hk) * blink};
    }

    // ===================================================================================== render

    private static void text(GuiGraphics g, Font f, String s, float x, float y, float sc, int col, boolean centre) {
        g.pose().pushPose();
        g.pose().translate(centre ? x - f.width(s) * sc / 2f : x, y, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(f, s, 0, 0, col, true);
        g.pose().popPose();
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (SHOTS.isEmpty() || mc.options.hideGui || mc.level == null || kind == null) return;
        long now = Util.getMillis();
        Shot last = SHOTS.get(SHOTS.size() - 1);
        float tl = last.t(now);
        if (tl > DUR || tl < 0) {
            SHOTS.clear();
            return;
        }
        Shot first = SHOTS.get(0);
        float t0 = first.t(now);
        float fade = tl > DUR - 0.4f ? (DUR - tl) / 0.4f : Math.min(1f, t0 / 0.12f);
        int a8 = (int) (fade * 255) << 24;
        Font f = mc.font;
        int W = Math.max(100, Math.min(140, (int) (w * 0.22f)));
        int VH = Math.round(W * 0.58f);
        int x0 = w - W - 4, y0 = 4;
        // ---------------------------------------------------------------- header: r21 = the LATEST shot (the one being
        // replayed bright in the view); earlier shots are dim in the view and listed as numbered chips
        Shot best = last;
        boolean hitYet = best.t(now) >= T_HIT;
        String head;
        int hc;
        switch (best.m.result()) {
            case Armour.RICOCHET -> { head = tr("hitcam.aiarsenal.ricochet"); hc = 0xFFD040; }
            case Armour.NO_PEN -> { head = tr("hitcam.aiarsenal.nopen"); hc = 0xD8D8D8; }
            case Armour.MODULE -> { head = tr("hitcam.aiarsenal.module"); hc = 0xFFA030; }
            default -> { head = tr("hitcam.aiarsenal.pen"); hc = 0xFF4A3A; }
        }
        if (!hitYet) {
            head = "\u2026";
            hc = 0xA8B0B8;
        }
        boolean destroyedAny = false;
        for (Shot sh : SHOTS) destroyedAny |= sh.t(now) >= T_HIT && (sh.m.flags() & HitCamPacket.F_DESTROYED) != 0;
        if (destroyedAny) {
            head = tr("hitcam.aiarsenal.destroyed");
            hc = 0xFF2A20;
        }
        // module rows (after the hit)
        List<Object[]> rows = new ArrayList<>();
        int[] mods = {DamageState.ENGINE, DamageState.AMMO, DamageState.BARREL, DamageState.TURRET, DamageState.TRACK_L, DamageState.TRACK_R, DamageState.FIRE, DamageState.TURRET_GONE};
        for (int mod : mods) {
            int st = state(mod, now);
            if (st == 0) continue;
            String nm = mod == DamageState.TURRET_GONE ? tr("hitcam.aiarsenal.turret_gone") : mod == DamageState.FIRE ? tr("hitcam.aiarsenal.fire")
                    : tr("hud.aiarsenal.rw.mod." + mod) + " (" + tr(st >= 2 ? "hitcam.aiarsenal.st.destroyed" : "hitcam.aiarsenal.st.damaged") + ")";
            rows.add(new Object[]{nm, st >= 2 || mod == DamageState.FIRE || mod == DamageState.TURRET_GONE ? 0xFF5040 : 0xFFB030});
        }
        int[] roles = ru.aiforge.realwar.damage.Crew.roles(kind);
        for (int i = 0; i < roles.length; i++) {
            int cs = crewState(i, now);
            if (cs == 0) continue;
            rows.add(new Object[]{ru.aiforge.realwar.damage.Crew.role(roles[i]).getString() + " (" + tr(cs >= 2 ? "hitcam.aiarsenal.crew.dead" : "hitcam.aiarsenal.crew.hurt") + ")",
                    cs >= 2 ? 0xFF5040 : 0xFFB030});
        }
        int headH = 21;
        int boxH = headH + VH + 2 + rows.size() * 5 + (rows.isEmpty() ? 0 : 2);
        g.fill(x0, y0, x0 + W, y0 + boxH, ((int) (fade * 0xD0)) << 24 | 0x0C0E10);
        g.fill(x0, y0, x0 + W, y0 + 1, ((int) (fade * 0xA0)) << 24 | (hc & 0xFFFFFF));
        text(g, f, head, x0 + W / 2f, y0 + 2.5f, 0.75f, hc | a8, true);
        // lines 2-3 (latest shot): penetration / effective armour, then plate, angle and range
        HitCamPacket m = last.m;
        String zn = tr("hitcam.aiarsenal.zone." + Armour.ZONE_KEYS[Math.max(0, Math.min(Armour.ZONE_KEYS.length - 1, m.zone()))]);
        String mm = tr("hitcam.aiarsenal.mm");
        String rng = m.dist() > 1 ? "  " + Math.round(m.dist()) + tr("hitcam.aiarsenal.m") : "";
        String l2, l3;
        if (m.result() == Armour.MODULE) {
            l2 = zn;
            l3 = rng.trim();
        } else {
            l2 = Math.round(m.pen()) + " / " + Math.round(m.eff()) + " " + mm + ((m.flags() & HitCamPacket.F_OVERMATCH) != 0 ? "  " + tr("hitcam.aiarsenal.overmatch") : "");
            l3 = zn + ((m.flags() & HitCamPacket.F_VIA_TRACK) != 0 ? "+" + tr("hitcam.aiarsenal.zone.track") : "") + " " + Math.round(m.nominal()) + mm
                    + " @" + Math.round(m.angle()) + "\u00b0" + rng;
        }
        if (!hitYet) l2 = l3 = "";
        text(g, f, l2, x0 + W / 2f, y0 + 10f, 0.62f, (m.result() == Armour.PEN ? 0xFFB0A0 : 0xD8D8D8) | a8, true);
        float s3 = Math.max(0.45f, Math.min(0.55f, (W - 4) / (float) Math.max(1, f.width(l3))));
        if (f.width(l3) * s3 > W - 4 && !rng.isEmpty() && l3.endsWith(rng)) l3 = l3.substring(0, l3.length() - rng.length()).trim();   // r21: drop the range first
        if (f.width(l3) * s3 > W - 4) l3 = f.plainSubstrByWidth(l3, (int) ((W - 4) / s3) - f.width("\u2026")) + "\u2026";
        text(g, f, l3, x0 + W / 2f, y0 + 16f, s3, 0xA8B0B8 | a8, true);
        // ---------------------------------------------------------------- 3D X-ray view
        int vx = x0 + 1, vy = y0 + headH, vw = W - 2;
        g.fillGradient(vx, vy, vx + vw, vy + VH, ((int) (fade * 0xE0)) << 24 | 0x262C33, ((int) (fade * 0xE0)) << 24 | 0x12161A);
        g.enableScissor(vx, vy, vx + vw, vy + VH);
        try {
            scene(g, mc, now, fade, vx, vy, vw, VH);
        } catch (Throwable ex) {
            ru.aiforge.realwar.RealWar.LOGGER.warn("hit camera render failed: {}", ex.toString());
            SHOTS.clear();
        } finally {
            GlbRenderer.XRAY = 0f;
            GlbRenderer.XRAY_RGB = null;
            GlbRenderer.NODE_TINT = null;
        }
        // r21: numbered chips of every shot in this camera (latest outlined), colour = its own result
        if (SHOTS.size() > 1) {
            int cxp = vx + 2;
            for (int k = 0; k < SHOTS.size(); k++) {
                Shot sh = SHOTS.get(k);
                int col = sh.t(now) < T_HIT ? 0x606870 : switch (sh.m.result()) {
                    case Armour.PEN -> 0xE03A2C;
                    case Armour.MODULE -> 0xE08A20;
                    case Armour.RICOCHET -> 0xD8B020;
                    default -> 0x8A9298;
                };
                if (sh == last) g.fill(cxp - 1, vy + 1, cxp + 8, vy + 10, a8 | 0xFFFFFF);
                g.fill(cxp, vy + 2, cxp + 7, vy + 9, a8 | col);
                text(g, f, Integer.toString(k + 1), cxp + 3.8f, vy + 3.2f, 0.55f, a8 | 0xFFFFFF, true);
                cxp += 10;
            }
        }
        g.disableScissor();
        // ---------------------------------------------------------------- damaged modules
        float ly = vy + VH + 2;
        for (Object[] r : rows) {
            text(g, f, (String) r[0], x0 + 3, ly, 0.5f, (Integer) r[1] | a8, false);
            ly += 5;
        }
    }

    private static void scene(GuiGraphics g, Minecraft mc, long now, float fade, int vx, int vy, int vw, int VH) {
        VehicleDamage.Geo ge = geo;
        Shot last = SHOTS.get(SHOTS.size() - 1);
        double cy = ge.yp() != null ? (ge.cy() + ge.yp()[1]) * 0.5 : ge.cy();
        Vector3d centre = new Vector3d(ge.cx(), cy, ge.cz());
        // camera: from the side of the (latest) shell path, a little towards the shooter, 24 deg above, slow orbit
        Vector3d dir = last.dir, entry = last.entry;
        double dhx = dir.x, dhz = dir.z;
        double hl = Math.hypot(dhx, dhz);
        double cx, cz;
        if (hl < 0.2) {
            cx = entry.x - centre.x;
            cz = entry.z - centre.z;
            if (Math.hypot(cx, cz) < 0.1) { cx = 1; cz = 1; }
        } else {
            dhx /= hl;
            dhz /= hl;
            // r21: the camera stands behind the shooter (the shell flies away from the viewer, as seen from the gun),
            // swung ~40 deg towards the side that was hit so the path is never hidden behind the hull
            double px = dhz, pz = -dhx;
            if (px * (entry.x - centre.x) + pz * (entry.z - centre.z) < 0) { px = -px; pz = -pz; }
            cx = px * 0.64 - dhx * 0.77;
            cz = pz * 0.64 - dhz * 0.77;
        }
        float tA = SHOTS.get(0).t(now);
        double alpha = Math.toDegrees(Math.atan2(cx, cz)) + 3.0 * Math.sin(tA * 0.6);
        float S = (float) (vw * 0.98 / Math.max(kind.spec.length() + (ge.turret() ? ge.tube() * 0.5 : 0), kind.spec.aabbW() * 1.4));
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(vx + vw / 2f, vy + VH / 2f + 2, 300);
        ps.mulPoseMatrix(new Matrix4f().scaling(S, -S, S));
        ps.mulPose(Axis.XP.rotationDegrees(28f));
        ps.mulPose(Axis.YP.rotationDegrees((float) -alpha));
        ps.translate(-centre.x, -centre.y, -centre.z);
        Lighting.setupForEntityInInventory();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        ground(bs, ps, ge, fade);
        // ---- internal modules: Blender props (engine, rounds, turret drive, crew) - pale, orange / red when hit
        int nCrew = ModuleProps.crewCount(kind);
        ModuleProps.draw(ps, bs, kind, ge, ang[0], id -> true, id -> {
            float[] base;
            int st;
            float since;
            if (id >= ModuleProps.CREW) {
                st = crewState(id - ModuleProps.CREW, now);
                base = new float[]{0.84f, 0.80f, 0.74f};
                since = st > 0 ? 0.5f : -1f;
            } else {
                int mod = id;
                st = state(mod, now);
                if (mod == DamageState.AMMO && state(DamageState.TURRET_GONE, now) > 0) st = 2;
                since = since(mod, now);
                if (since < 0 && st > 0) since = 1f;
                base = switch (mod) {
                    case DamageState.ENGINE -> new float[]{0.80f, 0.83f, 0.88f};
                    case DamageState.AMMO -> new float[]{0.92f, 0.82f, 0.52f};
                    default -> new float[]{0.74f, 0.80f, 0.90f};
                };
            }
            GlbRenderer.XRAY_RGB = dmgColour(base, st, since);
            GlbRenderer.XRAY = (st > 0 ? 0.92f : 0.62f) * fade;
        });
        bs.endBatch();
        GlbRenderer.XRAY_RGB = null;
        // ---- shells
        VertexConsumer vc = bs.getBuffer(RenderType.entityTranslucent(WHITE));
        Matrix4f pm = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        for (Shot sh : SHOTS) shell(vc, pm, nm, sh, sh.t(now), sh == last ? fade : fade * 0.35f);
        bs.endBatch();
        // ---- the vehicle itself, X-ray, hit parts painted on their nodes
        GlbAssets.Loaded lm = GlbAssets.get(kind.spec.model());
        if (lm != null) {
            Pose pose = lm.scratch();
            if (ge.turret()) {
                if (kind == ru.aiforge.realwar.vehicle.VehicleKind.MARK_I) {
                    float azR = kind.sponsonAz(0, ang[0]), azL = kind.sponsonAz(1, ang[0]), elS = kind.sponsonEl(ang[1]);
                    pose.rotate("launcher_yaw", (float) Math.toRadians(azR + 90f), 0f, 1f, 0f);
                    pose.rotate("launcher_yaw_left", (float) Math.toRadians(azL - 90f), 0f, 1f, 0f);
                    pose.rotate("launcher_pitch", (float) Math.toRadians(-kind.fireAxisZ() * elS), 1f, 0f, 0f);
                    pose.rotate("launcher_pitch_left", (float) Math.toRadians(-kind.fireAxisZ() * elS), 1f, 0f, 0f);
                } else {
                    pose.rotate("launcher_yaw", (float) Math.toRadians(ang[0]), 0f, 1f, 0f);
                    pose.rotate("launcher_pitch", (float) Math.toRadians(-kind.fireAxisZ() * ang[1]), 1f, 0f, 0f);
                }
            }
            int[] cat = ModuleProps.categories(lm.model);
            for (int i = 0; i < cat.length; i++) if (cat[i] == ModuleProps.C_INTERIOR) pose.hideIdx(i, true);
            pose.compute();
            float[] tint = new float[cat.length * 4];
            float[][] colour = new float[7][];
            float[] mul = new float[7];
            colour[ModuleProps.C_HULL] = new float[]{0.70f, 0.72f, 0.75f};
            colour[ModuleProps.C_TURRET] = dmgColour(new float[]{0.74f, 0.77f, 0.82f}, state(DamageState.TURRET_GONE, now) > 0 ? 2 : 0, since(DamageState.TURRET_GONE, now));
            colour[ModuleProps.C_BARREL] = dmgColour(new float[]{0.80f, 0.82f, 0.86f}, state(DamageState.BARREL, now), since(DamageState.BARREL, now));
            colour[ModuleProps.C_TRACK_L] = dmgColour(new float[]{0.56f, 0.58f, 0.60f}, state(DamageState.TRACK_L, now), since(DamageState.TRACK_L, now));
            colour[ModuleProps.C_TRACK_R] = dmgColour(new float[]{0.56f, 0.58f, 0.60f}, state(DamageState.TRACK_R, now), since(DamageState.TRACK_R, now));
            colour[ModuleProps.C_WHEEL] = new float[]{0.60f, 0.62f, 0.64f};
            colour[ModuleProps.C_INTERIOR] = new float[]{0.5f, 0.5f, 0.5f};
            java.util.Arrays.fill(mul, 1f);
            if (state(DamageState.BARREL, now) > 0) mul[ModuleProps.C_BARREL] = 2.7f;
            if (state(DamageState.TRACK_L, now) > 0) mul[ModuleProps.C_TRACK_L] = 2.7f;
            if (state(DamageState.TRACK_R, now) > 0) mul[ModuleProps.C_TRACK_R] = 2.7f;
            if (state(DamageState.TURRET_GONE, now) > 0) mul[ModuleProps.C_TURRET] = 2.0f;
            for (int i = 0; i < cat.length; i++) {
                float[] c = colour[cat[i]];
                tint[i * 4] = c[0];
                tint[i * 4 + 1] = c[1];
                tint[i * 4 + 2] = c[2];
                tint[i * 4 + 3] = mul[cat[i]];
            }
            GlbRenderer.NODE_TINT = tint;
            GlbRenderer.XRAY = 0.32f * fade;
            try {
                GlbRenderer.render(lm, pose, ps, bs, 0xF000F0, OverlayTexture.NO_OVERLAY);
            } finally {
                GlbRenderer.XRAY = 0f;
                GlbRenderer.NODE_TINT = null;
            }
            bs.endBatch();
        }
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    /** Flight, impact and result of one shell. */
    private static void shell(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Shot sh, float t, float fade) {
        if (t < 0) return;
        HitCamPacket m = sh.m;
        Vector3d start = sh.start, entry = sh.entry, dir = sh.dir;
        float sr = 1f, sg = 0.30f, sb = 0.12f;
        double L0 = start.distance(entry);
        if (t < T_HIT) {
            double k = t / T_HIT;
            Vector3d sp = new Vector3d(start).add(new Vector3d(dir).mul(L0 * k));
            seg(vc, pm, nm, start, sp, 0.025, 0.85f, 0.15f, 0.1f, 0.35f * fade);
            rod(vc, pm, nm, sp, dir, 0.55, 0.06, sr, sg, sb, fade);
            return;
        }
        double k = Math.min(1.0, (t - T_HIT) / 0.45);
        seg(vc, pm, nm, start, entry, 0.022, 0.85f, 0.15f, 0.1f, 0.30f * fade);
        boolean he = m.ammoType() == Armour.Type.HE.ordinal();
        switch (m.result()) {
            case Armour.RICOCHET -> {
                Vector3d rd = Armour.reflect(dir, sh.normal);
                Vector3d e2 = new Vector3d(entry).add(new Vector3d(rd).mul(3.2 * k));
                seg(vc, pm, nm, entry, e2, 0.022, 1f, 0.75f, 0.2f, 0.45f * fade);
                if (k < 1) rod(vc, pm, nm, e2, rd, 0.5, 0.055, sr, sg, sb, fade);
                sparks(vc, pm, nm, sh, 1f, 0.85f, 0.3f, t, fade);
                ring(vc, pm, nm, entry, sh.normal, dir, 0.12, 0.85f, 0.85f, 0.85f, fade * 0.8f);
            }
            case Armour.NO_PEN, Armour.MODULE -> {
                // r21: the shell stops ON the plate: grey dent, sparks back out, HE bursts outside
                if (he) {
                    burst(vc, pm, nm, sh, new Vector3d(entry).sub(new Vector3d(dir).mul(0.1)), t, fade, false);
                } else {
                    rod(vc, pm, nm, new Vector3d(entry).sub(new Vector3d(dir).mul(0.02)), dir, 0.45, 0.06, 0.55f, 0.2f, 0.15f, fade);
                    sparks(vc, pm, nm, sh, 1f, 0.8f, 0.35f, t, fade);
                }
                ring(vc, pm, nm, entry, sh.normal, dir, 0.14, 0.75f, 0.78f, 0.80f, fade);
            }
            default -> {
                // r21: a clear PENETRATION: red hole in the plate, the shell visibly goes on inside (>= 0.9 m),
                // spall cone / HE burst inside
                double inside = Math.max(he ? 0.35 : 0.9, m.tEnd());
                Vector3d e2 = new Vector3d(entry).add(new Vector3d(dir).mul(inside * k));
                seg(vc, pm, nm, entry, e2, 0.035, 1f, 0.35f, 0.1f, 0.9f * fade);
                if (k < 1) rod(vc, pm, nm, e2, dir, 0.4, 0.05, sr, sg, sb, fade);
                if (he) burst(vc, pm, nm, sh, new Vector3d(entry).add(new Vector3d(dir).mul(0.35)), t, fade, true);
                double ks = Math.min(1.0, Math.max(0, (t - T_HIT - 0.05) / 0.6));
                Vector3d st = new Vector3d(entry).add(new Vector3d(dir).mul(0.08));
                for (int i = 0; i < sh.spall.length; i++) {
                    Vector3d e3 = new Vector3d(st).add(new Vector3d(sh.spall[i]).mul(sh.spallLen[i] * ks));
                    float fa = (float) (t < T_HIT + 1.6 ? 0.9 : Math.max(0.35, 0.9 - (t - T_HIT - 1.6) * 0.4));
                    seg(vc, pm, nm, st, e3, 0.018, 1f, 0.72f, 0.18f, fa * fade);
                }
                ring(vc, pm, nm, entry, sh.normal, dir, 0.13, 1f, 0.15f, 0.1f, fade);
            }
        }
        cross(vc, pm, nm, entry, sh.normal, dir, 0.2, 1f, 0.12f, 0.08f, fade * 0.7f);
    }

    /** HE burst: orange star of fragments growing from c (inside = only into the hull side). */
    private static void burst(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Shot sh, Vector3d c, float t, float fade, boolean inside) {
        float k = (t - T_HIT) / 0.6f;
        if (k < 0) return;
        float al = (k < 1.4f ? 0.9f : Math.max(0.25f, 0.9f - (k - 1.4f) * 0.4f)) * fade;
        double R = 0.35 + 1.1 * Math.min(1f, k);
        Vector3d axis = inside ? new Vector3d(sh.dir) : (sh.normal.lengthSquared() > 1e-6 ? new Vector3d(sh.normal).normalize() : new Vector3d(sh.dir).negate());
        Vector3d[] dirs = Armour.spall(sh.m.seed() ^ 0x2545F491L, axis, inside ? 75 : 85, 14);
        for (Vector3d d : dirs) seg(vc, pm, nm, c, new Vector3d(c).add(new Vector3d(d).mul(R)), 0.02, 1f, 0.6f, 0.12f, al);
        float core = Math.max(0f, 1f - k) * fade;
        if (core > 0.02f) {
            Vector3d[] ab = basis(axis);
            double h = 0.12 + 0.25 * Math.min(1f, k);
            obox(vc, pm, nm, c, ab[0], ab[1], axis, h, h, h, 1f, 0.75f, 0.25f, core * 0.8f);
        }
    }

    /** Ring on the plate around the entry point (hole / dent marker). */
    private static void ring(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3d c, Vector3d n, Vector3d dir, double r, float cr, float cg, float cb, float a) {
        Vector3d nn = n.lengthSquared() > 1e-6 ? new Vector3d(n).normalize() : new Vector3d(dir).negate();
        Vector3d[] ab = basis(nn);
        Vector3d o = new Vector3d(c).add(new Vector3d(nn).mul(0.025));
        int N = 10;
        for (int i = 0; i < N; i++) {
            double a0 = i * Math.PI * 2 / N, a1 = (i + 1) * Math.PI * 2 / N;
            Vector3d p0 = new Vector3d(o).add(new Vector3d(ab[0]).mul(Math.cos(a0) * r)).add(new Vector3d(ab[1]).mul(Math.sin(a0) * r));
            Vector3d p1 = new Vector3d(o).add(new Vector3d(ab[0]).mul(Math.cos(a1) * r)).add(new Vector3d(ab[1]).mul(Math.sin(a1) * r));
            seg(vc, pm, nm, p0, p1, 0.018, cr, cg, cb, a);
        }
    }

    /** r21 orientation aids: ground grid with the hull's shadow, green arrow at the bow (= vehicle front). */
    private static void ground(MultiBufferSource.BufferSource bs, PoseStack ps, VehicleDamage.Geo ge, float fade) {
        VertexConsumer vc = bs.getBuffer(RenderType.entityTranslucent(WHITE));
        Matrix4f pm = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        double ex = Math.ceil(ge.hx() + 2.0), ez = Math.ceil(ge.hz() + 2.5);
        float y = 0.0f;
        quad(vc, pm, nm, new float[]{(float) (ge.cx() - ex), y, (float) (ge.cz() + ez), (float) (ge.cx() + ex), y, (float) (ge.cz() + ez),
                (float) (ge.cx() + ex), y, (float) (ge.cz() - ez), (float) (ge.cx() - ex), y, (float) (ge.cz() - ez)}, 0, 1, 0, 0.25f, 0.29f, 0.24f, 0.55f * fade);
        for (double gx = -ex; gx <= ex + 1e-6; gx += 1.0) {
            seg(vc, pm, nm, new Vector3d(ge.cx() + gx, 0.01, ge.cz() - ez), new Vector3d(ge.cx() + gx, 0.01, ge.cz() + ez), 0.008, 0.8f, 0.85f, 0.8f, 0.22f * fade);
        }
        for (double gz = -ez; gz <= ez + 1e-6; gz += 1.0) {
            seg(vc, pm, nm, new Vector3d(ge.cx() - ex, 0.01, ge.cz() + gz), new Vector3d(ge.cx() + ex, 0.01, ge.cz() + gz), 0.008, 0.8f, 0.85f, 0.8f, 0.22f * fade);
        }
        // hull shadow
        quad(vc, pm, nm, new float[]{ge.cx() - ge.hx(), 0.02f, ge.cz() + ge.hz(), ge.cx() + ge.hx(), 0.02f, ge.cz() + ge.hz(),
                ge.cx() + ge.hx(), 0.02f, ge.cz() - ge.hz(), ge.cx() - ge.hx(), 0.02f, ge.cz() - ge.hz()}, 0, 1, 0, 0.05f, 0.06f, 0.05f, 0.55f * fade);
        // bow arrow (chevron pointing forward)
        double z0 = ge.cz() + ge.hz() + 0.5, z1 = z0 + 1.1, w = Math.min(1.2, ge.hx() * 0.8);
        Vector3d tip = new Vector3d(ge.cx(), 0.04, z1);
        seg(vc, pm, nm, new Vector3d(ge.cx() - w, 0.04, z0), tip, 0.07, 0.35f, 0.95f, 0.35f, 0.9f * fade);
        seg(vc, pm, nm, new Vector3d(ge.cx() + w, 0.04, z0), tip, 0.07, 0.35f, 0.95f, 0.35f, 0.9f * fade);
        seg(vc, pm, nm, new Vector3d(ge.cx(), 0.04, ge.cz() + ge.hz() + 0.1), tip, 0.05, 0.35f, 0.95f, 0.35f, 0.7f * fade);
        bs.endBatch();
    }

    // ===================================================================================== primitives

    private static void quad(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float[] p, float nx, float ny, float nz, float r, float g, float b, float a) {
        for (int i = 0; i < 4; i++) {
            vc.vertex(pm, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(r, g, b, a).uv(0f, 0f).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(0xF000F0).normal(nm, nx, ny, nz).endVertex();
        }
    }

    /** One face of an axis-aligned cube (centre c, half size h). */
    private static void face(VertexConsumer vc, Matrix4f pm, Matrix3f nm, float x, float y, float z, float h, int f, float r, float g, float b, float a) {
        float x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        switch (f) {
            case 0 -> quad(vc, pm, nm, new float[]{x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, -1, 0, 0, r, g, b, a);
            case 1 -> quad(vc, pm, nm, new float[]{x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1}, 1, 0, 0, r, g, b, a);
            case 2 -> quad(vc, pm, nm, new float[]{x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, 0, -1, 0, r, g, b, a);
            case 3 -> quad(vc, pm, nm, new float[]{x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, 0, 1, 0, r, g, b, a);
            case 4 -> quad(vc, pm, nm, new float[]{x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, 0, 0, -1, r, g, b, a);
            default -> quad(vc, pm, nm, new float[]{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, 0, 0, 1, r, g, b, a);
        }
    }

    private static void box(VertexConsumer vc, Matrix4f pm, Matrix3f nm, double x, double y, double z, double hx, double hy, double hz,
                            float r, float g, float b, float a) {
        Vector3d c = new Vector3d(x, y, z);
        obox(vc, pm, nm, c, new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1), hx, hy, hz, r, g, b, a);
    }

    /** Oriented box: centre, unit axes u v w, half sizes. */
    private static void obox(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3d c, Vector3d u, Vector3d v, Vector3d w, double hu, double hv, double hw,
                             float r, float g, float b, float a) {
        Vector3d[] cn = new Vector3d[8];
        for (int i = 0; i < 8; i++) {
            double su = (i & 1) != 0 ? hu : -hu, sv = (i & 2) != 0 ? hv : -hv, sw = (i & 4) != 0 ? hw : -hw;
            cn[i] = new Vector3d(c).add(new Vector3d(u).mul(su)).add(new Vector3d(v).mul(sv)).add(new Vector3d(w).mul(sw));
        }
        int[][] fs = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};
        Vector3d[] ns = {new Vector3d(u).negate(), u, new Vector3d(v).negate(), v, new Vector3d(w).negate(), w};
        for (int k = 0; k < 6; k++) {
            float[] p = new float[12];
            for (int i = 0; i < 4; i++) {
                Vector3d q = cn[fs[k][i]];
                p[i * 3] = (float) q.x;
                p[i * 3 + 1] = (float) q.y;
                p[i * 3 + 2] = (float) q.z;
            }
            quad(vc, pm, nm, p, (float) ns[k].x, (float) ns[k].y, (float) ns[k].z, r, g, b, a);
        }
    }

    private static Vector3d[] basis(Vector3d d) {
        Vector3d up = Math.abs(d.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        Vector3d a = new Vector3d(d).cross(up).normalize();
        Vector3d b = new Vector3d(a).cross(d).normalize();
        return new Vector3d[]{a, b};
    }

    /** Thin beam from a to b. */
    private static void seg(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3d a, Vector3d b, double w, float r, float g, float bl, float al) {
        Vector3d d = new Vector3d(b).sub(a);
        double L = d.length();
        if (L < 1e-4) return;
        d.div(L);
        Vector3d[] ab = basis(d);
        obox(vc, pm, nm, new Vector3d(a).add(b).mul(0.5), ab[0], ab[1], d, w, w, L * 0.5, r, g, bl, al);
    }

    /** The shell body: a rod of length L ending at the tip p. */
    private static void rod(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3d tip, Vector3d d, double L, double w, float r, float g, float b, float a) {
        Vector3d[] ab = basis(d);
        obox(vc, pm, nm, new Vector3d(tip).sub(new Vector3d(d).mul(L * 0.5)), ab[0], ab[1], d, w, w, L * 0.5, r, g, b, a);
    }

    private static void cross(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Vector3d c, Vector3d n, Vector3d dir, double s, float r, float g, float b, float a) {
        Vector3d nn = n.lengthSquared() > 1e-6 ? new Vector3d(n).normalize() : new Vector3d(dir).negate();
        Vector3d[] ab = basis(nn);
        Vector3d p = new Vector3d(c).add(new Vector3d(nn).mul(0.03));
        Vector3d d1 = new Vector3d(ab[0]).add(ab[1]).normalize(), d2 = new Vector3d(ab[0]).sub(ab[1]).normalize();
        obox(vc, pm, nm, p, d1, new Vector3d(d1).cross(nn).normalize(), nn, s, 0.035, 0.02, r, g, b, a);
        obox(vc, pm, nm, p, d2, new Vector3d(d2).cross(nn).normalize(), nn, s, 0.035, 0.02, r, g, b, a);
    }

    private static void sparks(VertexConsumer vc, Matrix4f pm, Matrix3f nm, Shot sh, float r, float g, float b, float t, float fade) {
        float k = (t - T_HIT) / 0.5f;
        if (k > 1.6f || k < 0) return;
        float al = Math.max(0, 1 - k / 1.6f) * fade;
        Vector3d nn = sh.normal.lengthSquared() > 1e-6 ? new Vector3d(sh.normal).normalize() : new Vector3d(sh.dir).negate();
        Vector3d[] dirs = Armour.spall(sh.m.seed() ^ 0x5DEECE66DL, nn, 70, 8);
        for (Vector3d sd : dirs) {
            seg(vc, pm, nm, sh.entry, new Vector3d(sh.entry).add(new Vector3d(sd).mul(0.9 * Math.min(1, k))), 0.016, r, g, b, al);
        }
    }

    /** For the gametests / debug command: is a camera being shown? */
    public static boolean active() {
        return !SHOTS.isEmpty();
    }
}
