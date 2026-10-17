package ru.aiforge.tubombers;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.tubombers.command.TuCommand;
import ru.aiforge.tubombers.entity.BomberEntity;

/** Forge-bus events common to both sides. */
@Mod.EventBusSubscriber(modid = TuBombers.MODID)
public final class ModEvents {
    private ModEvents() {
    }

    /**
     * Sneak is the crew's "go down the ladder / stand up" key: the vanilla sneak-dismount is blocked; leaving the
     * aircraft happens only at the bottom of the entrance ladder (or by bailing out).
     */
    @SubscribeEvent
    public static void onMount(EntityMountEvent e) {
        if (e.isDismounting() && !e.getLevel().isClientSide && e.getEntityBeingMounted() instanceof BomberEntity b
                && !b.allowDismount && !b.isRemoved() && e.getEntityMounting().isAlive() && e.getEntityMounting().isShiftKeyDown())
            e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        TuCommand.register(e.getDispatcher());
    }
}
