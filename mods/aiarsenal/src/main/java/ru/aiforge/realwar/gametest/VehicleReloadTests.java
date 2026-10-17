package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import ru.aiforge.realwar.registry.*;
import ru.aiforge.realwar.vehicle.*;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class VehicleReloadTests {
    private record Rig(GunVehicle gun, FakePlayer player) {}
    private static Rig rig(GameTestHelper h) {
        var level=h.getLevel();var at=h.absoluteVec(new Vec3(12.5,2,12.5));
        var gun=ModEntities.STURMTIGER.get().create(level);
        gun.moveTo(at.x,at.y,at.z,0,0);level.addFreshEntity(gun);gun.setLoadedMask(0);
        var p=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"reload-gap"));
        p.moveTo(at.x,at.y,at.z,0,0);
        p.getInventory().setItem(1,new ItemStack(ModItems.ROCKET_380.get(),2));
        h.assertTrue(gun.enterSeat(p,gun.operatorSeat()),"operator seat");
        return new Rig(gun,p);
    }
    private static void input(Rig r,boolean held) {r.gun.handleControl(r.player,0,0,false,held,0);}

    @GameTest(template="arena",batch="reload_network",timeoutTicks=1100)
    public static void long_reload_survives_short_packet_gaps_without_free_ammo(GameTestHelper h) {
        Rig r=rig(h);
        h.onEachTick(()->{h.getLevel().resetEmptyTime();if(r.gun.tickCount%8==0)input(r,true);});
        h.runAfterDelay(1000,()->{
            h.assertTrue(r.gun.loadedCount()==1,"long reload kept resetting across short packet gaps");
            h.assertTrue(r.player.getInventory().getItem(1).getCount()==1,"consume exactly one rocket");
            h.assertTrue(r.gun.firedSeq()==0,"reload must never fire");r.gun.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="reload_network",timeoutTicks=100)
    public static void explicit_reload_release_cancels_immediately(GameTestHelper h) {
        Rig r=rig(h);
        h.onEachTick(()->{h.getLevel().resetEmptyTime();if(r.gun.tickCount<30)input(r,true);else input(r,false);});
        h.runAfterDelay(20,()->h.assertTrue(r.gun.reloadTicks()>0,"reload started"));
        h.runAfterDelay(50,()->{
            h.assertTrue(r.gun.reloadTube()<0 && r.gun.reloadTicks()==0,"release did not cancel");
            h.assertTrue(r.gun.loadedCount()==0 && r.player.getInventory().getItem(1).getCount()==2,"no free/consumed ammo on cancel");
            r.gun.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="reload_network",timeoutTicks=160)
    public static void lost_reload_input_pauses_then_cancels(GameTestHelper h) {
        Rig r=rig(h);
        h.onEachTick(()->{h.getLevel().resetEmptyTime();if(r.gun.tickCount<25)input(r,true);});
        int[] held={-1};
        h.runAfterDelay(35,()->{held[0]=r.gun.reloadTicks();h.assertTrue(held[0]>0,"short gap erased progress");});
        h.runAfterDelay(90,()->h.assertTrue(r.gun.reloadTicks()==held[0],"progress advanced without fresh input"));
        h.runAfterDelay(130,()->{
            h.assertTrue(r.gun.reloadTube()<0 && r.gun.reloadTicks()==0,"long silence did not cancel");
            h.assertTrue(r.player.getInventory().getItem(1).getCount()==2,"silence consumed ammunition");
            r.gun.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="reload_network",timeoutTicks=4500)
    public static void measured_two_second_input_bursts_do_not_reset_long_reload(GameTestHelper h) {
        Rig r=rig(h);
        h.onEachTick(()->{h.getLevel().resetEmptyTime();if(r.gun.tickCount%40==0)input(r,true);});
        h.runAfterDelay(4300,()->{
            h.assertTrue(r.gun.loadedCount()==1,"measured 40-tick gaps still reset the long reload");
            h.assertTrue(r.player.getInventory().getItem(1).getCount()==1,"consume exactly one rocket");
            h.assertTrue(r.gun.firedSeq()==0,"reload must never fire");r.gun.discard();h.succeed();
        });
    }

}
