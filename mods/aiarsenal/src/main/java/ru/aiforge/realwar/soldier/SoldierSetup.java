package ru.aiforge.realwar.soldier;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.registry.ModEntities;

/**
 * r27: Forge needs an attribute supplier for every custom living entity - without it
 * the soldier cannot be spawned at all ("this.supplier is null").  This is a MOD-bus
 * event in Forge 1.20.1 (EntityAttributeCreationEvent implements IModBusEvent).
 */
@Mod.EventBusSubscriber(modid = "aiarsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SoldierSetup {
    private SoldierSetup() {
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.SOLDIER.get(), SoldierEntity.createAttributes().build());
    }
}
