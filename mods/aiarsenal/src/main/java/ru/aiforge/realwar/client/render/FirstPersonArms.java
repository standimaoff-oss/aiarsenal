package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import ru.aiforge.realwar.item.UniformItem;
import ru.aiforge.realwar.item.HistoricalUniforms;

/** Bent two-segment arms and a separate palm. Gun anchors remain the source of hand contact. */
final class FirstPersonArms {
    private static final java.util.Map<String, ModelPart> PARTS = new java.util.HashMap<>();
    private static ModelPart part(boolean right, boolean slim, boolean lower, boolean palm) {
        String key = right + ":" + slim + ":" + lower + ":" + palm;
        return PARTS.computeIfAbsent(key, unused -> {
            var mesh = new MeshDefinition(); int w = slim ? 3 : 4;
            mesh.getRoot().addOrReplaceChild("arm", CubeListBuilder.create()
                .texOffs(right ? 40 : 32, (right ? 16 : 48) + (palm ? 8 : lower ? 6 : 0))
                .addBox(-w / 2f, 0, -2, w, palm ? 4 : 6, 4), PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("arm");
        });
    }
    static void draw(LocalPlayer player, PoseStack ps, MultiBufferSource buf, int light, Vector3f hand, boolean right) {
        boolean slim = "slim".equals(player.getModelName());
        float side = right ? 1 : -1;
        Vector3f shoulder = new Vector3f(side * .34f, -.63f, .15f);
        Vector3f elbow = new Vector3f(side * .29f, -.36f, -.10f);
        Vector3f direction = new Vector3f(hand).sub(elbow).normalize();
        Vector3f wrist = new Vector3f(hand).sub(new Vector3f(direction).mul(.045f));
        Vector3f tip = new Vector3f(hand).add(new Vector3f(direction).mul(.045f));
        ResourceLocation cloth = player.getSkinTextureLocation();
        if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof UniformItem u && HistoricalUniforms.contains(u.set))
            cloth = new ResourceLocation("aiarsenal", "textures/models/historical/" + u.set + ".png");
        segment(ps, buf, light, part(right, slim, false, false), shoulder, elbow, .34f, cloth, .375f);
        segment(ps, buf, light, part(right, slim, true, false), elbow, wrist, .34f, cloth, .375f);
        segment(ps, buf, light, part(right, slim, true, true), wrist, tip, .36f, player.getSkinTextureLocation(), .25f);
    }
    private static void segment(PoseStack ps, MultiBufferSource buf, int light, ModelPart part,
                                Vector3f a, Vector3f b, float width, ResourceLocation texture, float nativeLength) {
        Vector3f axis = new Vector3f(b).sub(a); float length = axis.length(); if (length < .001f) return;
        ps.pushPose(); ps.translate(a.x, a.y, a.z);
        ps.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), axis.div(length)));
        ps.scale(width, length / nativeLength, width);
        part.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
