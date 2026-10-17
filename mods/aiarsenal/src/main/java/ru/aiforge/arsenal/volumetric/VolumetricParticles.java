package ru.aiforge.arsenal.volumetric;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Only a visual particle ID. Safe on dedicated servers; no donor gameplay registrations. */
public final class VolumetricParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> TYPES =
        DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, "aiarsenal");
    public static final RegistryObject<SimpleParticleType> SMOKE = TYPES.register("volume_smoke", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> MUZZLE = TYPES.register("volume_muzzle", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> POWDER = TYPES.register("volume_powder", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> CANNON = TYPES.register("volume_cannon", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> EXHAUST = TYPES.register("volume_exhaust", () -> new SimpleParticleType(true));
    public static final RegistryObject<SimpleParticleType> IMPACT = TYPES.register("volume_impact", () -> new SimpleParticleType(true));
    private VolumetricParticles() {}
}
