package ru.aiforge.realwar.client.deco;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/** Extra baked OBJ parts drawn by block entity renderers (registered through ModelEvent.RegisterAdditional). */
public final class DecoModels {
    public static final ResourceLocation YOKE = new ResourceLocation("aiarsenal", "block/deco_searchlight_yoke");
    public static final ResourceLocation DRUM = new ResourceLocation("aiarsenal", "block/deco_searchlight_drum");
    public static final ResourceLocation DRUM_OFF = new ResourceLocation("aiarsenal", "block/deco_searchlight_drum_off");
    public static final ResourceLocation REFLECTOR = new ResourceLocation("aiarsenal", "block/deco_beacon_reflector");
    public static final ResourceLocation[] ALL;

    static {
        java.util.List<ResourceLocation> l = new java.util.ArrayList<>(java.util.List.of(YOKE, DRUM, DRUM_OFF, REFLECTOR));
        // r16: door leaves / hatch lids / handwheels / gate moving parts
        for (ru.aiforge.realwar.deco.DoorKind k : ru.aiforge.realwar.deco.DoorKind.values()) {
            l.add(rl(k.leaf(false)));
            if (!k.hatch) l.add(rl(k.leaf(true)));
            if (k.wheel != null) l.add(rl(k.wheel));
        }
        for (ru.aiforge.realwar.deco.GateKind g : ru.aiforge.realwar.deco.GateKind.values())
            for (int i = 0; i < g.parts.length; i++) l.add(rl(g.part(i)));
        // r17: crate lids
        for (ru.aiforge.realwar.deco.CrateKind c : ru.aiforge.realwar.deco.CrateKind.values()) l.add(rl(c.lid()));
        ALL = l.toArray(new ResourceLocation[0]);
    }

    private static final java.util.Map<String, ResourceLocation> NAMED = new java.util.HashMap<>();

    private static ResourceLocation rl(String name) {
        return new ResourceLocation("aiarsenal", "block/" + name);
    }

    /** r16: draw an extra model by block-model name, cutout or translucent (glazed door leaves). */
    static void drawNamed(String name, PoseStack ps, MultiBufferSource buf, BlockState st, int light, boolean translucent) {
        ResourceLocation r = NAMED.computeIfAbsent(name, DecoModels::rl);
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(r);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(),
                buf.getBuffer(translucent ? Sheets.translucentCullBlockSheet() : Sheets.cutoutBlockSheet()), st, m,
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    private DecoModels() {
    }

    static void draw(ResourceLocation rl, PoseStack ps, MultiBufferSource buf, BlockState st, int light) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(rl);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), st, m,
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }
}
