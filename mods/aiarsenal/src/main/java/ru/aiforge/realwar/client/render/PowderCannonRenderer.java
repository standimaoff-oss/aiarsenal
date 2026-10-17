package ru.aiforge.realwar.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import ru.aiforge.realwar.napoleonic.PowderCannonEntity;
public final class PowderCannonRenderer extends EntityRenderer<PowderCannonEntity>{
    public PowderCannonRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=1;}
    @Override public ResourceLocation getTextureLocation(PowderCannonEntity e){return new ResourceLocation("minecraft","textures/misc/white.png");}
    @Override public void render(PowderCannonEntity e,float yaw,float pt,PoseStack ps,MultiBufferSource buf,int light){
        var lm=GlbAssets.get(new ResourceLocation("aiarsenal","models/glb/"+e.model()+".glb"));
        if(lm!=null){ps.pushPose();ps.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
            var pose=lm.scratch();pose.rotate(e.model()+".barrel",(float)Math.toRadians(-e.elevation()),1,0,0);
            ps.translate(0,0,-.03f*e.recoil(pt));pose.compute(); // the whole carriage kicks, not a modern sliding barrel
            GlbRenderer.render(lm,pose,ps,buf,light,OverlayTexture.NO_OVERLAY);ps.popPose();}
        super.render(e,yaw,pt,ps,buf,light);
    }
}
