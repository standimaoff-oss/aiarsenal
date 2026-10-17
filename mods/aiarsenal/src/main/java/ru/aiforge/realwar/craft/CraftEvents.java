package ru.aiforge.realwar.craft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** No stepping out of a flying helicopter (land first) - the pilot would just fall. */
@Mod.EventBusSubscriber(modid = "aiarsenal", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CraftEvents {
    private CraftEvents() {}

    @SubscribeEvent
    public static void mount(EntityMountEvent e) {
        if (!e.isDismounting() || !(e.getEntityBeingMounted() instanceof HeliEntity h)) return;
        if (h.isRemoved() || h.hp() <= 0 || !h.isAlive()) return;
        if (h.heightAbove(h.position().add(0, 0.3, 0), 6) > 4.0 && !(e.getEntityMounting() instanceof Player pl && pl.isCreative() && pl.isSpectator())) {
            e.setCanceled(true);
            if (e.getEntityMounting() instanceof Player pl && !pl.level().isClientSide && h.tickCount % 2 == 0)
                pl.displayClientMessage(Component.translatable("craft.aiarsenal.no_exit_air"), true);
        }
    }
}
