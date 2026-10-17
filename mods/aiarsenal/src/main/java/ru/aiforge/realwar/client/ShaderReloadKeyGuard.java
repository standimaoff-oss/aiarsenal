package ru.aiforge.realwar.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.realwar.vehicle.LauncherVehicle;

/** Oculus defaults its shader rebuild to R, also Arsenal's reload key. A held
 * vehicle reload must not rebuild the graphics pipeline on every key repeat.
 * Consume ONLY the conflicting shader-rebuild clicks in a weapon/launcher context;
 * no rebinding, no optional class linkage, no suppression in menus or empty-handed.
 * Forge emits InputEvent.Key after KeyMapping.click. Oculus also consumes its
 * click in this event at NORMAL priority, so arbitration MUST run at HIGHEST.
 */
@Mod.EventBusSubscriber(modid="aiarsenal", value=Dist.CLIENT)
public final class ShaderReloadKeyGuard {
    private static boolean reported;
    private ShaderReloadKeyGuard() {}

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void key(InputEvent.Key event) {
        Minecraft mc=Minecraft.getInstance();
        if (event.getAction()==0 || mc.screen!=null || mc.player==null) return;
        if (GunClient.held()==null && !(mc.player.getVehicle() instanceof LauncherVehicle)) return;
        if (!VehicleClient.KEY_RELOAD.matches(event.getKey(),event.getScanCode())) return;
        for (var binding:mc.options.keyMappings) {
            if (!binding.getName().equals("iris.keybind.reload")
                    || !binding.matches(event.getKey(),event.getScanCode())) continue;
            boolean consumed=false;
            while(binding.consumeClick()) consumed=true;
            if (consumed && !reported) {
                reported=true;
                ru.aiforge.arsenal.AiArsenal.LOGGER.info("Shader reload key conflict: Arsenal reload takes priority while armed/mounted; key bindings unchanged");
            }
        }
    }
}
