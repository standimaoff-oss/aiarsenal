package ru.aiforge.realwar.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import ru.aiforge.arsenal.AiArsenal;
import ru.aiforge.realwar.registry.ModTabs;

/** Fixed, opt-in CI checks, never active for an ordinary player. No arbitrary code/file/network input. */
public final class WeaponViewQa {
    private WeaponViewQa() {}
    public static String run(String action) {
        if (!Boolean.getBoolean("aiarsenal.visualQa")) return "disabled";
        Minecraft mc = Minecraft.getInstance();
        switch (action) {
            case "view_index" -> AiArsenal.LOGGER.info(GlbRenderer.qaIndex());
            case "view_perf_old" -> FrameTimingQa.start(true);
            case "view_perf_new" -> FrameTimingQa.start(false);
            case "view_perf_end" -> FrameTimingQa.stop();
            case "view_motion" -> {
                if (mc.level != null) for (var player : mc.level.players())
                    AiArsenal.LOGGER.info("MOTION QA {}", ru.aiforge.realwar.client.WeaponMotionClient.status(player));
            }
            case "view_move_idle", "view_move_walk", "view_move_slow", "view_move_run", "view_move_crouch" -> {
                mc.setScreen(null);
                mc.options.keyUp.setDown(!action.equals("view_move_idle"));
                mc.options.keySprint.setDown(action.equals("view_move_run"));
                mc.options.keyShift.setDown(action.equals("view_move_crouch"));
                ru.aiforge.realwar.client.WeaponMotionClient.SLOW.setDown(action.equals("view_move_slow"));
            }
            case "view_shaderfail" -> ru.aiforge.arsenal.volumetric.client.render.VolumeGlRenderer.failNextShaderForQa();
            case "view_first" -> { mc.setScreen(null); mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); }
            case "view_front" -> { mc.setScreen(null); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT); }
            case "view_back" -> { mc.setScreen(null); mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); }
            case "view_left" -> { mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT); mc.options.broadcastOptions(); }
            case "view_right" -> { mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT); mc.options.broadcastOptions(); }
            case "view_ready" -> {
                if (mc.screen != null || mc.getOverlay() != null)
                    throw new IllegalStateException("visual capture blocked by menu/loading overlay");
            }
            case "view_fov45" -> mc.options.fov().set(45);
            case "view_fov90" -> mc.options.fov().set(90);
            case "view_fov70" -> mc.options.fov().set(70);
            case "view_scale3" -> {
                mc.options.guiScale().set(3); mc.resizeDisplay();
                if (mc.getWindow().getGuiScale() != 3) throw new IllegalStateException("effective GUI scale is not 3");
                AiArsenal.LOGGER.info("VIEW EFFECTIVE GUI SCALE 3 PASS");
            }
            case "view_scale2" -> { mc.options.guiScale().set(2); mc.resizeDisplay(); }
            case "view_reload" -> mc.reloadResourcePacks().whenComplete((unused, error) -> mc.execute(() -> {
                if (error == null) AiArsenal.LOGGER.info("VIEW RELOAD PASS");
                else AiArsenal.LOGGER.error("VIEW RELOAD FAILED", error);
            }));
            case "view_tabs", "view_modern", "view_ww2wear", "view_napwear", "view_naparms", "view_napguns", "view_napammo", "view_units" -> {
                var screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                        mc.options.operatorItemsTab().get());
                mc.setScreen(screen);
                String section = switch (action) {
                    case "view_tabs" -> "ww2_weapons";
                    case "view_ww2wear" -> "ww2_clothing";
                    case "view_napwear" -> "napoleonic_clothing";
                    case "view_naparms" -> "napoleonic_weapons";
                    case "view_napguns" -> "napoleonic_vehicles";
                    case "view_napammo" -> "napoleonic_ammo";
                    case "view_units" -> "ai_units";
                    default -> "modern_vehicles";
                };
                CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation("aiarsenal", section));
                // selectTab is private in vanilla; discover by its sole CreativeModeTab parameter, not an obfuscated name.
                try {
                    var methods = java.util.Arrays.stream(CreativeModeInventoryScreen.class.getDeclaredMethods())
                            .filter(m -> !m.isSynthetic() && m.getParameterCount() == 1 && m.getParameterTypes()[0] == CreativeModeTab.class
                                    && m.getReturnType() == void.class).toList();
                    if (methods.size() != 1) throw new IllegalStateException("ambiguous selectTab: " + methods);
                    methods.get(0).setAccessible(true);
                    methods.get(0).invoke(screen, tab);
                    // Rebuild Forge's page selection around the selected tab, not just its item grid.
                    screen.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
                } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
                var order = net.minecraftforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs().stream()
                        .map(BuiltInRegistries.CREATIVE_MODE_TAB::getKey)
                        .filter(id -> id.getNamespace().equals("aiarsenal")).map(ResourceLocation::getPath).toList();
                if (!order.equals(ModTabs.SECTIONS)) throw new IllegalStateException("era tab order: " + order);
                AiArsenal.LOGGER.info("VIEW TAB ORDER PASS {}", order);
                for (var id : ModTabs.SECTIONS) AiArsenal.LOGGER.info("VIEW TAB {} items={}", id, ModTabs.content(id).size());
            }
            default -> throw new IllegalArgumentException(action);
        }
        String result = "VIEW QA " + action + " PASS";
        AiArsenal.LOGGER.info(result);
        return result;
    }
}
