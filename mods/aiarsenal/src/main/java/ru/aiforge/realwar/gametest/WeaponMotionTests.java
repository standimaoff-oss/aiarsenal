package ru.aiforge.realwar.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.animation.WeaponMotion;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class WeaponMotionTests {
    private static WeaponMotion settled(double speed, boolean sprint, boolean crouch) {
        WeaponMotion m = new WeaponMotion();
        for (int i=0;i<40;i++) m.tick(speed, sprint, crouch, true);
        return m;
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void remote_packet_gaps_do_not_reset_walking(GameTestHelper h) {
        var speed = new ru.aiforge.realwar.animation.MovementSpeedWindow();
        var motion = new WeaponMotion();
        // 10 Hz remote rendering/network bursts on a 20 Hz client tick loop.
        for (int i=0;i<40;i++) {
            motion.tick(speed.tick(i % 2 == 0 ? .43 : 0), false, false, true);
            if (i>=8) h.assertTrue(motion.mode()==WeaponMotion.Mode.WALK,"walk survives inter-packet zero ticks");
        }
        for (int i=0;i<16;i++) motion.tick(speed.tick(i % 2 == 0 ? .172 : 0),false,false,true);
        h.assertTrue(motion.mode()==WeaponMotion.Mode.SLOW,"slow walk stays distinct");
        for (int i=0;i<8;i++) motion.tick(speed.tick(0),false,false,true);
        h.assertTrue(motion.mode()==WeaponMotion.Mode.IDLE,"stop settles within 400 ms");
        h.assertTrue(speed.tick(50)==0 && speed.tick(Double.NaN)==0,"teleport/malformed sample clears history");
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void five_states_and_airborne(GameTestHelper h) {
        h.assertTrue(settled(0,false,false).mode()==WeaponMotion.Mode.IDLE,"standing");
        h.assertTrue(settled(.06,false,false).mode()==WeaponMotion.Mode.SLOW,"slow walk");
        h.assertTrue(settled(.215,false,false).mode()==WeaponMotion.Mode.WALK,"walking");
        h.assertTrue(settled(.28,true,false).mode()==WeaponMotion.Mode.SPRINT,"sprinting");
        h.assertTrue(settled(.06,true,true).mode()==WeaponMotion.Mode.CROUCH,"crouch takes priority");
        var m=settled(.28,true,false);m.tick(.28,true,false,false);
        h.assertTrue(m.mode()==WeaponMotion.Mode.IDLE,"no running cycle in air");h.succeed();
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void ads_preserves_exact_sight_alignment(GameTestHelper h) {
        var m=settled(.28,true,false);
        for(float pt:new float[]{0,.25f,.5f,1}) for(boolean pistol:new boolean[]{false,true})
            h.assertTrue(m.sample(pt,1,false,pistol).equals(WeaponMotion.Frame.ZERO),"ADS must be exact zero");
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void transition_is_continuous_and_tick_driven(GameTestHelper h) {
        var m=settled(0,false,false);var before=m.sample(1,0,false,true);
        m.tick(.28,true,false,true);var start=m.sample(0,0,false,true);var end=m.sample(1,0,false,true);
        h.assertTrue(before.equals(start),"no pop at tick boundary");
        h.assertTrue(Math.abs(end.y()-start.y())<.04,"bounded first step");
        h.assertTrue(m.sample(.5f,0,false,true).equals(m.sample(.5f,0,false,true)),"render FPS cannot advance state");
        for(int i=0;i<300;i++){var old=m.sample(1,0,false,false);m.tick(.215,false,false,true);
            var next=m.sample(0,0,false,false);h.assertTrue(Math.abs(old.x()-next.x())<.00001,"phase wrap continuity");}
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void reload_attenuation_and_entity_isolation(GameTestHelper h) {
        var running=settled(.28,true,false);var idle=settled(0,false,false);
        var a=running.sample(.5f,0,false,false);var b=running.sample(.5f,0,true,false);
        h.assertTrue(Math.abs(b.y()-a.y()*.12f)<.00001,"reload overrides gait");
        h.assertTrue(Math.abs(idle.sample(.5f,0,false,false).y())<.004,"no state leak to stationary player");
        // Previous 18-degree + 11cm drop removed the entire pistol from the FOV70 frame.
        var pistol=running.sample(.5f,0,false,true);
        h.assertTrue(pistol.y()>-.05f && Math.abs(pistol.pitch())<9,"sprint framing stays bounded");
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_motion")
    public static void malformed_input_stays_finite(GameTestHelper h) {
        var m=new WeaponMotion();
        for(double speed:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,1000}) {
            m.tick(speed,true,false,true);var f=m.sample(Float.NaN,Float.NaN,false,false);
            h.assertTrue(Float.isFinite(f.x()+f.y()+f.z()+f.pitch()+f.yaw()+f.roll()),"finite transform");
        }
        h.succeed();
    }
}
