package ru.aiforge.realwar.deco;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** r16: multi-block gates. Canonical layout (facing north): the master block is the left-bottom corner, the gate extends W
 *  blocks to +X (the placer's right) and H blocks up, gate plane at z = 0.5. Moving parts are posed by GateRenderer. */
public enum GateKind {
    SLIDING("sliding", 5, 2, 60, new String[]{"leaf"}),
    SWING("swing", 4, 3, 34, new String[]{"leaf_l", "leaf_r"}),
    BOOM("boom", 5, 1, 30, new String[]{"arm"}),
    BLAST("blast", 3, 3, 80, new String[]{"leaf_l", "leaf_r"});

    public final String key;
    public final int w, h, duration;
    public final String[] parts;

    GateKind(String key, int w, int h, int duration, String[] parts) {
        this.key = key;
        this.w = w;
        this.h = h;
        this.duration = duration;
        this.parts = parts;
    }

    public String part(int i) {
        return "r16_gate_" + key + "_" + parts[i];
    }

    public SoundEvent sound(boolean open) {
        return switch (this) {
            case SLIDING, BLAST -> open ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT;
            case SWING -> open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE;
            case BOOM -> open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE;
        };
    }

    /** Canonical (facing north) boxes in pixels {x0,y0,z0,x1,y1,z1} of the part at (ox, oy). closed=true: gate shut. */
    double[][] boxes(int ox, int oy, boolean closed) {
        java.util.List<double[]> l = new java.util.ArrayList<>();
        switch (this) {
            case SLIDING -> {
                if (ox == 0) l.add(new double[]{0, 0, 9, 3.2, 16, 13.2});
                if (ox == w - 1) l.add(new double[]{13, 0, 6, 16, 16, 10});
                if (closed) l.add(new double[]{ox == 0 ? 3.2 : 0, 0, 7, ox == w - 1 ? 13 : 16, 16, 9});
            }
            case SWING -> {
                if (ox == 0) l.add(new double[]{0, 0, 5.4, 5.2, 16, 10.6});
                if (ox == w - 1) l.add(new double[]{10.8, 0, 5.4, 16, 16, 10.6});
                if (closed) l.add(new double[]{ox == 0 ? 5.2 : 0, 0, 7, ox == w - 1 ? 10.8 : 16, oy == 2 ? 11 : 16, 9});
            }
            case BOOM -> {
                if (ox == 0) l.add(new double[]{4.8, 0, 4.8, 11.2, 17, 11.2});
                if (closed) l.add(new double[]{ox == 0 ? 11.2 : 0, 0, 2.4, 16, 24, 5.6});
                else if (ox == 0) l.add(new double[]{6, 0, 2.4, 10, 24, 5.6});
            }
            case BLAST -> {
                if (oy == 2) l.add(new double[]{0, 13, 3.5, 16, 16, 12.5});
                if (closed) l.add(new double[]{0, 0, 4.8, 16, oy == 2 ? 13 : 16, 11.2});
            }
        }
        return l.toArray(new double[0][]);
    }
}
