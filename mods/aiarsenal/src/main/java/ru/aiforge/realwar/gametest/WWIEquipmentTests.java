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
}
