package ru.aiforge.tubombers.glb;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Mutable, per-instance pose of a {@link GlbModel}: local TRS of every node, visibility flags,
 * additive procedural offsets and the resulting global matrices. Animation clips are sampled into it
 * with optional blend weight (layering: later calls override / blend over earlier ones).
 */
public final class GlbPose {
    public final GlbModel model;
    public final Vector3f[] t, s;
    public final Quaternionf[] r;
    public final boolean[] visible;
    public final Matrix4f[] global;
    private final Vector3f tmpV = new Vector3f();
    private final Quaternionf tmpQ = new Quaternionf();
    private final Quaternionf tmpQ2 = new Quaternionf();

    public GlbPose(GlbModel model) {
        this.model = model;
        int n = model.nodes.size();
        t = new Vector3f[n];
        s = new Vector3f[n];
        r = new Quaternionf[n];
        global = new Matrix4f[n];
        visible = new boolean[n];
        for (int i = 0; i < n; i++) {
            t[i] = new Vector3f();
            s[i] = new Vector3f();
            r[i] = new Quaternionf();
            global[i] = new Matrix4f();
        }
        reset();
    }

    /** Back to the rest pose stored in the file; everything visible. */
    public GlbPose reset() {
        for (int i = 0; i < t.length; i++) {
            GlbModel.Node n = model.nodes.get(i);
            t[i].set(n.t);
            r[i].set(n.r);
            s[i].set(n.s);
        }
        Arrays.fill(visible, true);
        return this;
    }

    public GlbPose setVisible(int node, boolean v) {
        if (node >= 0) visible[node] = v;
        return this;
    }

    /** Adds a local translation (in the node's parent space) on top of the current pose. */
    public GlbPose translate(int node, float x, float y, float z) {
        if (node >= 0) t[node].add(x, y, z);
        return this;
    }

    /** Post-multiplies a local rotation (axis-angle, radians). */
    public GlbPose rotate(int node, float angle, float ax, float ay, float az) {
        if (node >= 0) r[node].mul(tmpQ.identity().rotateAxis(angle, ax, ay, az));
        return this;
    }

    /**
     * Samples a clip at {@code time} seconds and writes it into the pose.
     *
     * @param loop   wrap time into [0,duration) when true, clamp otherwise
     * @param weight 1 = replace, (0,1) = blend from the current pose
     */
    public GlbPose apply(GlbModel.Animation anim, float time, boolean loop, float weight) {
        if (anim == null || weight <= 0f) return this;
        float d = anim.duration;
        if (d > 0) {
            if (loop) {
                time = time % d;
                if (time < 0) time += d;
            } else time = Math.max(0, Math.min(d, time));
        } else time = 0;
        for (GlbModel.Channel ch : anim.channels) {
            int n = ch.node;
            switch (ch.path) {
                case TRANSLATION -> {
                    sampleVec(ch, time, tmpV);
                    if (weight >= 1f) t[n].set(tmpV);
                    else t[n].lerp(tmpV, weight);
                }
                case SCALE -> {
                    sampleVec(ch, time, tmpV);
                    if (weight >= 1f) s[n].set(tmpV);
                    else s[n].lerp(tmpV, weight);
                }
                case ROTATION -> {
                    sampleQuat(ch, time, tmpQ2);
                    if (weight >= 1f) r[n].set(tmpQ2);
                    else r[n].slerp(tmpQ2, weight);
                }
                default -> {
                }
            }
        }
        return this;
    }

    public GlbPose apply(String anim, float time, boolean loop, float weight) {
        return apply(model.anim(anim), time, loop, weight);
    }

    // ---------------------------------------------------------------- sampling
    private static int findKey(float[] times, float t) {
        int lo = 0, hi = times.length - 1;
        if (t <= times[0]) return 0;
        if (t >= times[hi]) return hi;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (times[mid] <= t) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private static void sampleVec(GlbModel.Channel ch, float time, Vector3f out) {
        float[] tm = ch.times, v = ch.values;
        int k = findKey(tm, time);
        boolean cubic = ch.interp == GlbModel.Interp.CUBICSPLINE;
        int stride = cubic ? 9 : 3;
        int valOff = cubic ? 3 : 0;
        if (k >= tm.length - 1 || time <= tm[0]) {
            int b = k * stride + valOff;
            out.set(v[b], v[b + 1], v[b + 2]);
            return;
        }
        float t0 = tm[k], t1 = tm[k + 1], dt = t1 - t0;
        float u = dt > 0 ? (time - t0) / dt : 0;
        if (ch.interp == GlbModel.Interp.STEP) {
            int b = k * stride;
            out.set(v[b], v[b + 1], v[b + 2]);
        } else if (cubic) {
            float[] o = new float[3];
            hermite(v, k, u, dt, 3, o);
            out.set(o[0], o[1], o[2]);
        } else {
            int a = k * 3, b = (k + 1) * 3;
            out.set(v[a] + (v[b] - v[a]) * u, v[a + 1] + (v[b + 1] - v[a + 1]) * u, v[a + 2] + (v[b + 2] - v[a + 2]) * u);
        }
    }

    private static void sampleQuat(GlbModel.Channel ch, float time, Quaternionf out) {
        float[] tm = ch.times, v = ch.values;
        int k = findKey(tm, time);
        boolean cubic = ch.interp == GlbModel.Interp.CUBICSPLINE;
        int stride = cubic ? 12 : 4;
        int valOff = cubic ? 4 : 0;
        if (k >= tm.length - 1 || time <= tm[0]) {
            int b = k * stride + valOff;
            out.set(v[b], v[b + 1], v[b + 2], v[b + 3]).normalize();
            return;
        }
        float t0 = tm[k], t1 = tm[k + 1], dt = t1 - t0;
        float u = dt > 0 ? (time - t0) / dt : 0;
        if (ch.interp == GlbModel.Interp.STEP) {
            int b = k * 4;
            out.set(v[b], v[b + 1], v[b + 2], v[b + 3]).normalize();
        } else if (cubic) {
            float[] o = new float[4];
            hermite(v, k, u, dt, 4, o);
            out.set(o[0], o[1], o[2], o[3]).normalize();
        } else {
            int a = k * 4, b = (k + 1) * 4;
            Quaternionf qa = new Quaternionf(v[a], v[a + 1], v[a + 2], v[a + 3]);
            Quaternionf qb = new Quaternionf(v[b], v[b + 1], v[b + 2], v[b + 3]);
            qa.slerp(qb, u, out);
        }
    }

    /** glTF cubic spline: values are [inTangent, value, outTangent] per key. */
    private static void hermite(float[] v, int k, float u, float dt, int n, float[] out) {
        int s = 3 * n;
        int p0 = k * s + n, m0 = k * s + 2 * n;          // value_k, out_k
        int p1 = (k + 1) * s + n, m1 = (k + 1) * s;      // value_k+1, in_k+1
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;
        for (int c = 0; c < n; c++)
            out[c] = h00 * v[p0 + c] + h10 * dt * v[m0 + c] + h01 * v[p1 + c] + h11 * dt * v[m1 + c];
    }

    // ---------------------------------------------------------------- matrices
    public GlbPose computeGlobals() {
        for (int root : model.roots) walk(root, null);
        return this;
    }

    private void walk(int i, Matrix4f parent) {
        Matrix4f m = global[i];
        if (parent == null) m.identity();
        else m.set(parent);
        m.translate(t[i]).rotate(r[i]).scale(s[i]);
        for (int c : model.nodes.get(i).children) walk(c, m);
    }

    /** True if the node and all its ancestors are visible. */
    public boolean effectivelyVisible(int i) {
        while (i >= 0) {
            if (!visible[i]) return false;
            i = model.nodes.get(i).parent;
        }
        return true;
    }

    /** World (model-space) position of a node after {@link #computeGlobals()}. */
    public Vector3f nodePos(int i, Vector3f out) {
        return global[i].getTranslation(out);
    }

    /** Model-space direction of a node's local axis. */
    public Vector3f nodeAxis(int i, float x, float y, float z, Vector3f out) {
        return global[i].transformDirection(out.set(x, y, z)).normalize();
    }
}
