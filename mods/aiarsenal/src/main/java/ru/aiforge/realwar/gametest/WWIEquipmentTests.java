package ru.aiforge.realwar.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.registry.ModEntities;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.wwi.GrenadeEntity;
import ru.aiforge.realwar.wwi.FlamethrowerItem;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class WWIEquipmentTests {
    @GameTest(template="arena", batch="rw_wwi_equipment", timeoutTicks=20)
    public static void grenade_fuse_survives_save_and_rejects_invalid_values(GameTestHelper h) {
        GrenadeEntity entity = ModEntities.WWI_GRENADE.get().create(h.getLevel());
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        saved.putInt("Fuse", 27);
        GrenadeEntity restored = ModEntities.WWI_GRENADE.get().create(h.getLevel());
        restored.load(saved);
        h.assertTrue(restored.fuseTicks()==27,"fuse time survives save/load");
        saved.putInt("Fuse",-900);restored.load(saved);
        h.assertTrue(restored.fuseTicks()==1,"invalid fuse cannot make a permanent explosive");
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_wwi_equipment", timeoutTicks=20)
    public static void empty_projector_refills_only_with_fuel(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"wwi-fuel"));
        ItemStack stack = new ItemStack(ModItems.WEX.get());
        stack.setDamageValue(stack.getMaxDamage()-1);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        FlamethrowerItem item = (FlamethrowerItem)stack.getItem();
        item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(!player.isUsingItem(),"empty weapon does not fire");
        player.setShiftKeyDown(true);
        item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(stack.getDamageValue()>0,"no free fuel");
        player.getInventory().add(new ItemStack(ModItems.WWI_FUEL.get()));
        item.use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(stack.getDamageValue()==0,"fuel restores the gauge");
        h.assertTrue(!player.getInventory().contains(new ItemStack(ModItems.WWI_FUEL.get())),"one fuel item consumed");
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_wwi_equipment", timeoutTicks=20)
    public static void grenade_throw_consumes_one_and_sets_owner(GameTestHelper h) {
        FakePlayer player = new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"wwi-throw"));
        var point = h.absolutePos(new net.minecraft.core.BlockPos(3,3,3));
        player.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,0f,0f);
        ItemStack stack = new ItemStack(ModItems.MILLS_BOMB.get(),3);
        player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        stack.getItem().use(h.getLevel(),player,InteractionHand.MAIN_HAND);
        h.assertTrue(stack.getCount()==2,"exactly one grenade consumed");
        var grenades = h.getLevel().getEntitiesOfClass(GrenadeEntity.class,player.getBoundingBox().inflate(2));
        h.assertTrue(grenades.size()==1 && grenades.get(0).getOwner()==player,"one owned server projectile");
        h.assertTrue(grenades.get(0).fuseTicks()==80,"fuse starts on throw, not in inventory");
        grenades.forEach(net.minecraft.world.entity.Entity::discard);
        h.succeed();
    }
    @GameTest(template="arena", batch="rw_wwi_equipment", timeoutTicks=20)
    public static void projector_cannot_damage_through_solid_wall(GameTestHelper h) {
        var level = h.getLevel();
        var point = h.absolutePos(new net.minecraft.core.BlockPos(3,2,3));
        FakePlayer player = new FakePlayer(level,new GameProfile(UUID.randomUUID(),"wwi-wall"));
        player.moveTo(point.getX()+.5,point.getY(),point.getZ()+.5,0f,0f);
        var target = net.minecraft.world.entity.EntityType.HUSK.create(level);
        target.moveTo(point.getX()+.5,point.getY()+.6,point.getZ()+4.5,0f,0f);
        level.addFreshEntity(target);
        for (int x=-1;x<=1;x++) for (int y=0;y<4;y++)
            level.setBlockAndUpdate(point.offset(x,y,2),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        ItemStack stack = new ItemStack(ModItems.WEX.get());
        float health = target.getHealth();
        ((FlamethrowerItem)stack.getItem()).onUseTick(level,player,stack,72000);
        h.assertTrue(target.getHealth()==health && !target.isOnFire(),"wall blocks flame damage and ignition");
        h.assertTrue(stack.getDamageValue()==1,"fuel is still consumed when spraying a wall");
        target.discard();
        h.succeed();
    }
}
