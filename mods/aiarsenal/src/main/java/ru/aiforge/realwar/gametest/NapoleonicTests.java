package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.handheld.*;
import ru.aiforge.realwar.registry.*;
import ru.aiforge.realwar.napoleonic.*;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class NapoleonicTests {
    @GameTest(template="arena",batch="rw_nap")
    public static void flintlocks_single_cartridge_no_magazine_duplication(GameTestHelper h) {
        for(GunKind kind:GunKind.values()) if(kind.flintlock()) {
            FakePlayer p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"nap-load"));
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("aiarsenal",kind.id));
            var ammo=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation("aiarsenal",kind.ammo));
            ItemStack gun=new ItemStack(item),rounds=new ItemStack(ammo,3);
            p.getInventory().setItem(1,rounds);
            h.assertTrue(kind.cap==1 && !kind.closedBolt() && !kind.auto,"single-shot flintlock traits");
            h.assertTrue(Firearms.reloadAllowed(p,gun,kind),"can load an empty musket/pistol");
            Firearms.finishReload(p,gun,kind);
            h.assertTrue(FirearmItem.ammo(gun)==1 && rounds.getCount()==2,"one cartridge per load");
            Firearms.finishReload(p,gun,kind);
            h.assertTrue(!Firearms.reloadAllowed(p,gun,kind) && rounds.getCount()==2,"no chamber+1 or spent magazine");
            FirearmItem.setAmmo(gun,0);p.getInventory().clearContent();Firearms.finishReload(p,gun,kind);
            h.assertTrue(FirearmItem.ammo(gun)==0,"no free ammunition");
        }
        h.succeed();
    }
    @GameTest(template="arena",batch="rw_nap",timeoutTicks=200)
    public static void cannons_load_wait_fire_once_and_reject_wrong_calibre(GameTestHelper h) {
        FakePlayer p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"nap-gunner"));
        var fr=ModEntities.CANNON_FR6.get().create(h.getLevel());var ri=ModEntities.CANNON_RI12.get().create(h.getLevel());
        var o=h.absolutePos(new BlockPos(4,2,4));fr.setPos(o.getX(),o.getY(),o.getZ());ri.setPos(o.getX()+5,o.getY(),o.getZ());
        fr.setNoGravity(true);ri.setNoGravity(true);h.getLevel().addFreshEntity(fr);h.getLevel().addFreshEntity(ri);
        ItemStack a=new ItemStack(ModItems.CHARGE_FR6.get(),2),b=new ItemStack(ModItems.CHARGE_RI12.get(),2);
        h.assertTrue(!fr.load(p,b) && !ri.load(p,a),"calibres are not interchangeable");
        h.assertTrue(fr.load(p,a) && ri.load(p,b),"load both armies");
        h.assertTrue(a.getCount()==1 && b.getCount()==1 && !fr.load(p,a),"consume once, reject double loading");
        h.assertTrue(!fr.fire(p) && !ri.fire(p),"cannot fire during ramming");
        h.runAfterDelay(165,()->{
            h.assertTrue(fr.loading()==0 && ri.loading()==0,"eight-second loading completes");
            h.assertTrue(fr.fire(p) && ri.fire(p),"both guns fire live projectiles");
            h.assertTrue(!fr.fire(p) && !ri.fire(p),"one ball per charge");
            var balls=h.getLevel().getEntitiesOfClass(CannonballEntity.class,fr.getBoundingBox().inflate(12));
            h.assertTrue(balls.size()>=2 && balls.stream().allMatch(e->e.getOwner()==p),"server projectiles retain gunner ownership");
            balls.forEach(e->e.discard());fr.discard();ri.discard();h.succeed();
        });
    }
    @GameTest(template="arena",batch="rw_nap")
    public static void cannon_saved_loading_and_muzzle_direction(GameTestHelper h) {
        var cannon=ModEntities.CANNON_RI12.get().create(h.getLevel());
        CompoundTag t=new CompoundTag();t.putInt("LoadTicks",73);t.putInt("Elevation",25);t.putFloat("Health",29);
        cannon.load(t);cannon.setYRot(90);var d=cannon.muzzle().subtract(cannon.position().add(0,1.05,0));
        h.assertTrue(cannon.loading()==73 && cannon.elevation()==25,"loading survives save/reload");
        h.assertTrue(d.x<0 && d.y>0 && Math.abs(d.z)<.001 && Math.abs(d.length()-1.7)<.001,"yaw/elevation originate at barrel muzzle");
        CompoundTag saved=new CompoundTag();cannon.saveWithoutId(saved);
        h.assertTrue(saved.getInt("LoadTicks")==73 && saved.getFloat("Health")==29,"saved health and load");h.succeed();
    }
    @GameTest(template="arena",batch="rw_nap")
    public static void both_armies_have_distinct_catalog_sections(GameTestHelper h) {
        h.assertTrue(ModTabs.content("napoleonic_weapons").size()==6,"four firearms and two blades");
        h.assertTrue(ModTabs.content("napoleonic_vehicles").size()==2,"two powder cannons");
        h.assertTrue(ModTabs.content("napoleonic_ammo").size()==5,"cartridges and solid-shot charges");
        h.assertTrue(ModTabs.content("napoleonic_clothing").size()==24,"all existing uniforms retained");
        h.assertTrue(!ModTabs.content("modern_weapons").contains(ModItems.MUSKET_1777.get()),"no era mixing");h.succeed();
    }
}
