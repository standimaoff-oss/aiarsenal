/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.world.damagesource.DamageSource
 *  net.minecraft.world.damagesource.DamageType
 *  net.minecraft.world.level.Level
 */
package ru.aiforge.realwar.blast;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class ModDamage {
    public static final ResourceKey<DamageType> NUCLEAR_BLAST = ModDamage.key("nuclear_blast");
    public static final ResourceKey<DamageType> NUCLEAR_THERMAL = ModDamage.key("nuclear_thermal");
    public static final ResourceKey<DamageType> BLAST = ModDamage.key("blast");
    public static final ResourceKey<DamageType> FRAGMENTS = ModDamage.key("fragments");
    public static final ResourceKey<DamageType> RADIATION = ModDamage.key("radiation");
    public static final ResourceKey<DamageType> ORDNANCE = ModDamage.key("ordnance");
    public static final ResourceKey<DamageType> BULLET = ModDamage.key("bullet");

    private ModDamage() {
    }

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create((ResourceKey)Registries.DAMAGE_TYPE, (ResourceLocation)new ResourceLocation("aiarsenal", name));
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> key) {
        return new DamageSource((Holder)level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key));
    }
}

