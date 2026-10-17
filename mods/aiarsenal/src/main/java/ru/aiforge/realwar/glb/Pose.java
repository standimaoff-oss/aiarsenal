/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionf
 *  org.joml.Quaternionfc
 *  org.joml.Vector3f
 *  org.joml.Vector3fc
 */
package ru.aiforge.realwar.glb;

import ru.aiforge.realwar.glb.GlbModel;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Pose {
    private final GlbModel model;
    private final Vector3f[] t;
    private final Quaternionf[] r;
    private final Vector3f[] s;
    public final Matrix4f[] world;
    private final boolean[] hidden;
    private final boolean[] hiddenEff;
    private final Matrix4f tmp = new Matrix4f();

    public Pose(GlbModel model) {
        this.model = model;
        int n = model.nodes.size();
        this.t = new Vector3f[n];
        this.r = new Quaternionf[n];
        this.s = new Vector3f[n];
        this.world = new Matrix4f[n];
        this.hidden = new boolean[n];
        this.hiddenEff = new boolean[n];
        for (int i = 0; i < n; ++i) {
            this.t[i] = new Vector3f();
            this.r[i] = new Quaternionf();
            this.s[i] = new Vector3f();
            this.world[i] = new Matrix4f();
        }
        this.reset();
    }

    public GlbModel model() {
        return this.model;
    }

    public void reset() {
        Arrays.fill(this.hidden, false);
        this.mask = null;
        for (int i = 0; i < this.t.length; ++i) {
            GlbModel.Node n = this.model.nodes.get(i);
            this.t[i].set((Vector3fc)n.t);
            this.r[i].set((Quaternionfc)n.r);
            this.s[i].set((Vector3fc)n.s);
        }
    }

    public void sample(GlbModel.Animation anim, float time) {
        if (anim == null || anim.duration <= 0.0f) {
            return;
        }
        float tt = time % anim.duration;
        if (tt < 0.0f) {
            tt += anim.duration;
        }
        for (GlbModel.Channel ch : anim.channels) {
            int hi;
            float[] ts = ch.times;
            if (ts.length == 0) continue;
            for (hi = 0; hi < ts.length - 1 && ts[hi] < tt; ++hi) {
            }
            int lo = Math.max(0, hi - 1);
            float f = 0.0f;
            if (hi != lo && ts[hi] > ts[lo]) {
                f = (tt - ts[lo]) / (ts[hi] - ts[lo]);
            }
            if (ch.step) {
                f = 0.0f;
            }
            switch (ch.path) {
                case "translation": {
                    this.t[ch.node].set(ch.values[lo * 3], ch.values[lo * 3 + 1], ch.values[lo * 3 + 2]);
                    if (!(f > 0.0f)) break;
                    this.t[ch.node].lerp((Vector3fc)new Vector3f(ch.values[hi * 3], ch.values[hi * 3 + 1], ch.values[hi * 3 + 2]), f);
                    break;
                }
                case "scale": {
                    this.s[ch.node].set(ch.values[lo * 3], ch.values[lo * 3 + 1], ch.values[lo * 3 + 2]);
                    if (!(f > 0.0f)) break;
                    this.s[ch.node].lerp((Vector3fc)new Vector3f(ch.values[hi * 3], ch.values[hi * 3 + 1], ch.values[hi * 3 + 2]), f);
                    break;
                }
                case "rotation": {
                    this.r[ch.node].set(ch.values[lo * 4], ch.values[lo * 4 + 1], ch.values[lo * 4 + 2], ch.values[lo * 4 + 3]);
                    if (!(f > 0.0f)) break;
                    this.r[ch.node].slerp((Quaternionfc)new Quaternionf(ch.values[hi * 4], ch.values[hi * 4 + 1], ch.values[hi * 4 + 2], ch.values[hi * 4 + 3]), f);
                    break;
                }
            }
        }
    }

    public void rotate(String node, float rad, float ax, float ay, float az) {
        int i = this.model.nodeIndex(node);
        if (i < 0) {
            return;
        }
        this.r[i].mul((Quaternionfc)new Quaternionf().fromAxisAngleRad(ax, ay, az, rad));
    }

    public void translate(String node, float x, float y, float z) {
        int i = this.model.nodeIndex(node);
        if (i < 0) {
            return;
        }
        this.t[i].add(x, y, z);
    }

    public void hide(String node, boolean value) {
        int i = this.model.nodeIndex(node);
        if (i >= 0) {
            this.hidden[i] = value;
        }
    }

    public boolean isHidden(int node) {
        return this.hiddenEff[node] || (this.mask != null && !this.mask[node]);
    }

    /** r18: only nodes whose mask entry is true are drawn (debris pieces); cleared by {@link #reset()}. */
    private boolean[] mask;

    public void restrictTo(boolean[] m) {
        this.mask = m;
    }

    public void hideIdx(int i, boolean value) {
        if (i >= 0 && i < this.hidden.length) this.hidden[i] = value;
    }

    public void scale(String node, float x, float y, float z) {
        int i = this.model.nodeIndex(node);
        if (i >= 0) this.s[i].mul(x, y, z);
    }

    public void rotateIdx(int i, float rad, float ax, float ay, float az) {
        if (i >= 0) this.r[i].mul(new Quaternionf().fromAxisAngleRad(ax, ay, az, rad));
    }

    public void compute() {
        for (int root : this.model.roots) {
            this.computeNode(root, null, false);
        }
    }

    private void computeNode(int i, Matrix4f parent, boolean parentHidden) {
        this.hiddenEff[i] = parentHidden || this.hidden[i];
        Matrix4f w = this.world[i];
        if (parent == null) {
            w.identity();
        } else {
            w.set((Matrix4fc)parent);
        }
        w.translate((Vector3fc)this.t[i]).rotate((Quaternionfc)this.r[i]).scale((Vector3fc)this.s[i]);
        for (int c : this.model.nodes.get((int)i).children) {
            this.computeNode(c, w, this.hiddenEff[i]);
        }
    }
}

