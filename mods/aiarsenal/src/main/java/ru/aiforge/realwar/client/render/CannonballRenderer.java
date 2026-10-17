package ru.aiforge.realwar.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.napoleonic.CannonballEntity;

/** Real small GLB roundshot, never a half-metre item billboard. */
public final class CannonballRenderer extends EntityRenderer<CannonballEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation("aiarsenal","models/glb/cannonball.glb");
    public CannonballRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(CannonballEntity entity) { return MODEL; }
    @Override public void render(CannonballEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var model=GlbAssets.get(MODEL);
        if(model!=null) {
            pose.pushPose();pose.scale(.77f,.77f,.77f); // 10cm visual diameter; calibre is carried by the charge.
            GlbRenderer.render(model,model.staticPose(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(entity,yaw,partial,pose,buffers,light);
    }
}
