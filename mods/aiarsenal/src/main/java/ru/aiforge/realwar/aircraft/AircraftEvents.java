/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Entity
 *  net.minecraftforge.event.entity.EntityMountEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 */
package ru.aiforge.realwar.aircraft;

import ru.aiforge.realwar.aircraft.AircraftEntity;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AircraftEvents {
    private AircraftEvents() {
    }

    @SubscribeEvent
    public static void mount(EntityMountEvent e) {
        Entity entity;
        if (e.isDismounting() && (entity = e.getEntityBeingMounted()) instanceof AircraftEntity) {
            AircraftEntity a = (AircraftEntity)entity;
            if (!a.allowDismount && a.isAlive()) {
                e.setCanceled(true);
            }
        }
    }
}

