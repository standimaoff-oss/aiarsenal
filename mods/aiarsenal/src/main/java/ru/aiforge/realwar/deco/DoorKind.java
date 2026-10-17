package ru.aiforge.realwar.deco;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** r16: per-type data of the animated doors and hatches (model names, hinge pivot, timing, optional handwheel).
 *  Pivots are in canonical model space (tools/blender/door_blocks.py): doors hinge at (px, pz) on the left, hatches at (py, pz). */
public enum DoorKind {
    STEEL("steel", false, 0.08f, 0.085f, 10, 0f, null, 0, 0, 0, false, false),
    BUNKER("bunker", false, 0.08f, 0.10f, 44, 0.42f, "r16_bunker_wheel", 0.5f, 1.0f, 0.10f, false, false),
    BARS("bars", false, 0.08f, 0.09f, 9, 0f, null, 0, 0, 0, false, false),
    WOOD("wood", false, 0.08f, 0.08f, 8, 0f, null, 0, 0, 0, true, true),
    LAB("lab", false, 0.08f, 0.085f, 9, 0f, null, 0, 0, 0, true, false),
    HATCH_STEEL("hatch_steel", true, 0.05f, 0.05f, 10, 0f, null, 0, 0, 0, false, false),
    HATCH_BUNKER("hatch_bunker", true, 0.05f, 0.05f, 36, 0.45f, "r16_hatch_bunker_wheel", 0.5f, 0.06f, 0.5f, false, false),
    HATCH_GRATE("hatch_grate", true, 0.05f, 0.05f, 8, 0f, null, 0, 0, 0, false, false);

    public final String key;
    public final boolean hatch;
    public final float pa, pz;            // door: hinge x / z (left leaf); hatch: hinge y / z
    public final int duration;            // ticks for a full open
    public final float wheelFrac;         // share of the motion spent turning the handwheel before the leaf moves
    public final String wheel;            // block model of the handwheel or null
    public final float wx, wy, wz;        // wheel centre (door: axis Z, hatch: axis Y) in leaf / lid space
    public final boolean translucent;     // glazed leaves go to the translucent sheet
    public final boolean wooden;

    DoorKind(String key, boolean hatch, float pa, float pz, int duration, float wheelFrac, String wheel, float wx, float wy, float wz,
             boolean translucent, boolean wooden) {
        this.key = key;
        this.hatch = hatch;
        this.pa = pa;
        this.pz = pz;
        this.duration = duration;
        this.wheelFrac = wheelFrac;
        this.wheel = wheel;
        this.wx = wx;
        this.wy = wy;
        this.wz = wz;
        this.translucent = translucent;
        this.wooden = wooden;
    }

    public String leaf(boolean right) {
        return hatch ? "r16_" + key + "_lid" : "r16_" + key + (right ? "_leaf_r" : "_leaf_l");
    }

    public BlockSetType setType() {
        if (wooden) {
            return new BlockSetType("aiarsenal_" + key, true, SoundType.WOOD, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.WOODEN_DOOR_OPEN,
                    SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF,
                    SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON, SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundEvents.WOODEN_BUTTON_CLICK_ON);
        }
        return new BlockSetType("aiarsenal_" + key, true, SoundType.METAL, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
                SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
                SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);
    }

    /** Smooth ease-in-out of the leaf (0..1) for the eased progress p, after the handwheel phase. */
    public float leafT(float p) {
        float t = wheelFrac > 0 ? Math.max(0f, Math.min(1f, (p - wheelFrac) / (1f - wheelFrac))) : p;
        return t * t * (3f - 2f * t);
    }

    public float wheelT(float p) {
        return wheelFrac > 0 ? Math.max(0f, Math.min(1f, p / wheelFrac)) : 0f;
    }
}
