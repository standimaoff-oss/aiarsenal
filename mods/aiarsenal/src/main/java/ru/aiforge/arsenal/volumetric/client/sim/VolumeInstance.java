// Adapted from supplied VolPyro 1.5.6 (MIT), VolPyro Contributors. See THIRD_PARTY_NOTICES.md.
package ru.aiforge.arsenal.volumetric.client.sim;

import net.minecraft.world.phys.Vec3;

public final class VolumeInstance {
    public static final int SMOKE = 1;
    public static final int ARSENAL_SMOKE = 11;
    public static final int EXPLOSION = 2;
    public static final int ROCKET_SEGMENT = 3;
    public static final int ROCKET_JET = 4;
    public static final int DEBRIS = 5;
    /** Arsenal-only smoke profile; zero keeps donor nuclear and legacy simulation unchanged. */
    public int profile;
    public final int type;
    public Vec3 center;
    public float boundRadius;
    public float density;
    public float temperature;
    public float expansion;
    public final float maxExpansion;
    public float auxiliary;
    public Vec3 axis;
    public Vec3 velocity = Vec3.ZERO;
    public float halfLength;
    public float age;
    public float lifetime;
    public float seed;
    public long key;

    public VolumeInstance(int type, Vec3 center, float boundRadius, float density, float temperature, float expansion, float auxiliary, Vec3 axis, float halfLength, float lifetime, float seed, long key) {
        this.type = type;
        this.center = center;
        this.boundRadius = boundRadius;
        this.density = density;
        this.temperature = temperature;
        this.expansion = expansion;
        this.maxExpansion = expansion;
        this.auxiliary = auxiliary;
        this.axis = axis;
        this.halfLength = halfLength;
        this.lifetime = lifetime;
        this.seed = seed;
        this.key = key;
    }
}

