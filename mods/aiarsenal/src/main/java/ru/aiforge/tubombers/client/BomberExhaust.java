package ru.aiforge.tubombers.client;

import org.joml.Vector3f;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.arsenal.volumetric.VolumetricParticles;

/** Nozzle coordinates from tools/blender/t95_01_body.py and t160_01_body.py.
 * Blender (x,y,z) -> glTF (x,z,-y). Rotated with the aircraft, not world-aligned.
 */
public final class BomberExhaust {
    private BomberExhaust() {}
    public static void tick(BomberEntity b) {
        if (!b.hasFlag(BomberEntity.F_ENGINES) || b.isDestroyed() || b.tickCount % 4 != 0) return;
        if (b.kind.swingWing) {
            for(float x:new float[]{5.76f,4.24f,-4.24f,-5.76f}) emit(b,x,3f,-9.8f,0,0,-1);
        } else {
            for(float x:new float[]{13.6f,6.3f,-6.3f,-13.6f}) {
                float sign=Math.signum(x), height=Math.abs(x)>10?3.15f:3.30f;
                float le=-7.6f+(Math.abs(x)-1.4f)*.754f;
                for(float dy:new float[]{0,.55f}) emit(b,x+sign*.82f,height+.64f,-(le-.04f+dy),sign*.12f,.22f,-.85f);
            }
        }
    }
    private static void emit(BomberEntity b,float x,float y,float z,float dx,float dy,float dz) {
        Vector3f p=b.qCur.transform(new Vector3f(x,y,z)),v=b.qCur.transform(new Vector3f(dx,dy,dz));
        b.level().addParticle(VolumetricParticles.EXHAUST.get(),b.getX()+p.x,b.getY()+p.y,b.getZ()+p.z,v.x,v.y,v.z);
    }
}
