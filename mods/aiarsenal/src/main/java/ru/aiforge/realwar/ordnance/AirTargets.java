/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.registries.Registries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.tags.TagKey
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.EntityType
 *  net.minecraft.world.entity.player.Player
 */
package ru.aiforge.realwar.ordnance;

import ru.aiforge.realwar.aircraft.AircraftEntity;
import ru.aiforge.realwar.missile.MissileEntity;
import ru.aiforge.realwar.ordnance.RocketEntity;
import ru.aiforge.realwar.ordnance.SamMissileEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

public final class AirTargets {
    public static final TagKey<EntityType<?>> TAG = TagKey.create((ResourceKey)Registries.ENTITY_TYPE, (ResourceLocation)new ResourceLocation("aiarsenal", "air_targets"));

    private AirTargets() {
    }

    public static boolean isAirTarget(Entity e) {
        if (!e.isAlive() || e.isSpectator()) {
            return false;
        }
        if (e instanceof AircraftEntity) {
            AircraftEntity a = (AircraftEntity)e;
            return !a.onGround() && (double)a.speed() > 6.0;
        }
        if (e instanceof ru.aiforge.realwar.craft.HeliEntity h) {
            return h.heightAbove(h.position().add(0, 0.3, 0), 4) > 1.5;   // airborne helicopter
        }
        if (e instanceof ru.aiforge.realwar.craft.PlaneEntity pl) {
            return pl.airborne();
        }
        if (e instanceof ru.aiforge.realwar.craft.AradoEntity ar) {
            return ar.airborne();
        }
        if (e instanceof ru.aiforge.realwar.craft.CraftOrdnance o) {
            return false;
        }
        if (e instanceof ShellEntity) {
            return false;   // gun projectiles are not engaged by flak / SAM
        }
        if (e instanceof MissileEntity || e instanceof RocketEntity || e instanceof SamMissileEntity) {
            return true;
        }
        if (e instanceof Player) {
            Player p = (Player)e;
            return p.isFallFlying();
        }
        return e.getType().is(TAG);
    }
}

