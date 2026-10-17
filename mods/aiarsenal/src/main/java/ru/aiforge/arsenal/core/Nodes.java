package ru.aiforge.arsenal.core;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.aiforge.tubombers.glb.GlbModel;
import ru.aiforge.tubombers.glb.GlbModels;
import ru.aiforge.tubombers.glb.GlbPose;

/**
 * Server-safe access to GLB node positions (launch points, muzzles, camera balls) and the model-to-world transform
 * shared with the renderers: world = Ry(-yaw) * Rx(-pitchUp) * Rz(roll) * local (glTF +Z = nose / launch direction).
 */
public final class Nodes {
    private Nodes() {
    }

    public static GlbModel model(Kind k) {
        return GlbModels.get(k.glb);
    }

    /**
     * Node position in model space after sampling {@code anim} at {@code time} seconds (null anim = rest pose).
     * {@code axis} (optional) receives the node's local +Z axis in model space.
     */
    public static synchronized Vector3f node(Kind k, String node, String anim, float time, Vector3f axis) {
        GlbModel m = model(k);
        int i = m.node(node);
        if (i < 0) {
            if (axis != null) axis.set(0, 0, 1);
            return new Vector3f();
        }
        GlbPose p = new GlbPose(m);
        p.reset();
        if (anim != null && m.anim(anim) != null) p.apply(anim, time, false, 1f);
        p.computeGlobals();
        Vector3f out = p.nodePos(i, new Vector3f());
        if (axis != null) p.nodeAxis(i, 0, 0, 1, axis);
        return out;
    }

    /** Shows only the root node {@code root} of a multi-model GLB. */
    public static void isolate(GlbPose pose, String root) {
        for (int r : pose.model.roots) pose.setVisible(r, root.equals(pose.model.nodes.get(r).name));
    }

    public static Quaternionf rot(float yaw, float pitchUp, float roll) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(-pitchUp))
                .rotateZ((float) Math.toRadians(roll));
    }

    /** Model-space point to world offset (relative to the entity position). */
    public static Vec3 toWorld(Vector3f local, float yaw, float pitchUp, float roll) {
        Vector3f v = rot(yaw, pitchUp, roll).transform(new Vector3f(local));
        return new Vec3(v.x, v.y, v.z);
    }
}
