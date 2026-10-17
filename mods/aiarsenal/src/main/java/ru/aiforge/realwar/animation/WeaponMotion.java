package ru.aiforge.realwar.animation;

/** Tick-driven, renderer-independent motion. No wall clock and no shared state between players. */
public final class WeaponMotion {
    public enum Mode { IDLE, SLOW, WALK, SPRINT, CROUCH }
    public record Frame(float x, float y, float z, float pitch, float yaw, float roll) {
        public static final Frame ZERO = new Frame(0, 0, 0, 0, 0, 0);
    }
    private Mode mode = Mode.IDLE;
    private float move, oldMove, run, oldRun, crouch, oldCrouch;
    private double phase, oldPhase, breath, oldBreath;
    public Mode mode() { return mode; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float value, float target) { return lerp(value, target, .28f); }
    private static float clamp(float v) { return Math.max(0, Math.min(1, Float.isFinite(v) ? v : 0)); }

    public void tick(double blocksPerTick, boolean sprinting, boolean sneaking, boolean grounded) {
        double speed = Double.isFinite(blocksPerTick) ? Math.max(0, Math.min(.4, blocksPerTick)) : 0;
        // Teleports are filtered by the client adapter; airborne players don't keep a walking cycle.
        boolean moving = grounded && speed > .008;
        mode = sneaking ? Mode.CROUCH : !moving ? Mode.IDLE : sprinting ? Mode.SPRINT
                : speed < .12 ? Mode.SLOW : Mode.WALK;
        oldMove = move; oldRun = run; oldCrouch = crouch;
        oldPhase = phase; oldBreath = breath;
        move = smooth(move, moving ? (float)Math.min(1, speed / .22) : 0);
        run = smooth(run, mode == Mode.SPRINT ? 1 : 0);
        crouch = smooth(crouch, sneaking ? 1 : 0);
        phase += moving ? speed * 3.0 : 0;
        breath += .085;
        // Rebase both ends together, preserving interpolation through the cycle boundary.
        if (phase > Math.PI * 2) { phase -= Math.PI * 2; oldPhase -= Math.PI * 2; }
        if (breath > Math.PI * 2) { breath -= Math.PI * 2; oldBreath -= Math.PI * 2; }
    }

    public Frame sample(float partialTick, float ads, boolean reloading, boolean pistol) {
        float pt = clamp(partialTick), a = clamp(ads);
        float weight = (1 - a) * (1 - a) * (reloading ? .12f : 1);
        if (weight == 0) return Frame.ZERO; // settled iron sights stay exactly on the camera ray
        float m = lerp(oldMove, move, pt), r = lerp(oldRun, run, pt), c = lerp(oldCrouch, crouch, pt);
        double p = oldPhase + (phase - oldPhase) * pt, b = oldBreath + (breath - oldBreath) * pt;
        float side = (float)Math.sin(p), step = (float)Math.cos(p * 2), breathing = (float)Math.sin(b);
        float amp = m * (1 - .65f * c);
        return new Frame(weight * (.009f * side * amp + .025f * r),
                weight * (.003f * breathing + .009f * step * amp + .025f * r + .018f * c),
                weight * (.035f * r + .015f * c),
                weight * (.5f * breathing + 1.4f * step * amp + (pistol ? 4 : 3) * r),
                weight * (.8f * side * amp - 5 * r),
                weight * (1.6f * side * amp + (pistol ? 12 : 8) * r));
    }
}
