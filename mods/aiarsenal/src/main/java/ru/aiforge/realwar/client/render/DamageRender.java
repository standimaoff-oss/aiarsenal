package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import ru.aiforge.realwar.craft.BattleshipEntity;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.damage.DamageState;
import ru.aiforge.realwar.glb.GlbModel;
import ru.aiforge.realwar.glb.Pose;
import ru.aiforge.realwar.vehicle.VehicleEntity;

import static ru.aiforge.realwar.damage.DamageState.*;

/** r18: draws module damage on the vehicle models - missing parts hidden, broken barrel, shell holes (Blender decals). */
public final class DamageRender {
    public static final ResourceLocation FX = new ResourceLocation("aiarsenal", "models/glb/damage_fx.glb");
    private static final Map<GlbModel, Map<String, int[]>> MATCH = new WeakHashMap<>();

    private DamageRender() {
    }

    public static int[] matching(GlbModel m, String regex) {
        return MATCH.computeIfAbsent(m, k -> new HashMap<>()).computeIfAbsent(regex, r -> {
            Pattern p = Pattern.compile(r);
            java.util.List<int[]> l = new java.util.ArrayList<>();
            for (int i = 0; i < m.nodes.size(); i++) {
                java.util.regex.Matcher mt = p.matcher(m.nodes.get(i).name);
                if (mt.matches()) l.add(new int[]{i, num(m.nodes.get(i).name)});
            }
            l.sort((a, b) -> Integer.compare(a[1], b[1]));
            int[] out = new int[l.size()];
            for (int i = 0; i < out.length; i++) out[i] = l.get(i)[0];
            return out;
        });
    }

    private static int num(String s) {
        int e = s.length();
        int b = e;
        while (b > 0 && Character.isDigit(s.charAt(b - 1))) b--;
        try {
            return b < e ? Integer.parseInt(s.substring(b, e)) : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public static void vehicle(VehicleEntity v, Pose pose) {
        DamageState s = v.dmg;
        if (!s.any()) return;
        GlbModel m = pose.model();
        if (s.broken(TRACK_L)) for (int i : matching(m, ".*_link_L_\\d+")) pose.hideIdx(i, true);
        if (s.broken(TRACK_R)) for (int i : matching(m, ".*_link_R_\\d+")) pose.hideIdx(i, true);
        if (s.broken(TURRET_GONE)) {
            pose.hide("launcher_yaw", true);
        } else if (s.broken(BARREL)) {
            pose.scale("barrel", 1f, 1f, 0.5f);
        }
    }

    public static void craft(CraftEntity c, Pose pose) {
        DamageState s = c.dmg;
        if (!s.any()) return;
        if (c.kind.isPlane()) {
            if (s.broken(WING_L)) {
                pose.hide("wing_L", true);
                pose.hide("ail_L", true);
            }
            if (s.broken(WING_R)) {
                pose.hide("wing_R", true);
                pose.hide("ail_R", true);
            }
        } else if (c.kind.isHeli()) {
            if (s.broken(ROTOR)) {
                pose.hide("rotor_main", true);
                pose.hide("rotor_upper", true);
                pose.hide("rotor_lower", true);
                pose.hide("rotor_disc", true);
                pose.hide("rotor_disc2", true);
            }
            if (s.broken(TAIL_ROTOR)) {
                pose.hide("rotor_tail", true);
                pose.hide("tail_disc", true);
            }
        } else if (c instanceof BattleshipEntity) {
            for (int t = 0; t < BattleshipEntity.N_MAIN; t++) {
                for (int g = 0; g < 2; g++) if ((s.lost >>> (t * 2 + g) & 1) != 0) pose.hide(BattleshipEntity.M[t].tag() + "_g" + g, true);
            }
            for (int i = 0; i < BattleshipEntity.N_SEC; i++) {
                if ((s.lost >>> (8 + i) & 1) != 0) pose.hide(BattleshipEntity.M[BattleshipEntity.I_SEC + i].tag() + "_pitch", true);
            }
        } else if ((c.kind == CraftKind.CB90 || c.kind == CraftKind.RAPTOR) && (s.lost & 1) != 0) {
            pose.hide("turret_pitch", true);
        }
    }

    /** Shell holes in the model frame (current pose stack = the vehicle's model frame). */
    public static void holes(DamageState s, PoseStack ps, MultiBufferSource buf, int light) {
        if (s.holes.isEmpty()) return;
        GlbAssets.Loaded fx = GlbAssets.get(FX);
        if (fx == null) return;
        int big = fx.model.nodeIndex("hole_large"), small = fx.model.nodeIndex("hole_small");
        for (int k = 0; k < s.holes.size(); k++) {
            DamageState.Hole h = s.holes.get(k);
            boolean lg = h.r > 0.3f;
            float sc = h.r / (lg ? 0.85f : 0.16f);
            ps.pushPose();
            ps.translate(h.x, h.y, h.z);
            Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, h.nx, h.ny, h.nz);
            q.rotateY((float) (((k * 2654435761L + (long) (h.z * 97)) & 1023) / 1023.0 * Math.PI * 2));
            ps.mulPose(q);
            ps.scale(sc, sc, sc);
            GlbRenderer.renderNode(fx, lg ? big : small, ps, buf, light, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
