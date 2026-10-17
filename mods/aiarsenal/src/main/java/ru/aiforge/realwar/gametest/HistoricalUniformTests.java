package ru.aiforge.realwar.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ru.aiforge.realwar.item.HistoricalUniforms;
import ru.aiforge.realwar.item.UniformItem;
import ru.aiforge.realwar.registry.ModItems;
import ru.aiforge.realwar.registry.ModTabs;

@GameTestHolder("aiarsenal")
@PrefixGameTestTemplate(false)
public final class HistoricalUniformTests {
    @GameTest(template="arena", batch="rw_deco")
    public static void all_four_armies_wearable_and_correct_era(GameTestHelper h) {
        var wearer = h.spawn(EntityType.ZOMBIE, new BlockPos(2,2,2)); wearer.setNoAi(true);
        h.assertTrue(HistoricalUniforms.SETS.length == 16, "sixteen new sets");
        var unique = new java.util.HashSet<net.minecraft.world.item.Item>();
        for (String set : HistoricalUniforms.SETS) {
            for (String slot : new String[]{"head","chest","legs","feet"}) {
                String id = "uniform_" + set + "_" + slot;
                var item = (UniformItem)ModItems.UNIFORMS.get(id).get();
                h.assertTrue(unique.add(item), "duplicate piece " + id);
                h.assertTrue(item.set.equals(set), "wrong visual set " + id);
                wearer.setItemSlot(item.getEquipmentSlot(), new ItemStack(item));
                h.assertTrue(wearer.getItemBySlot(item.getEquipmentSlot()).is(item), "cannot wear " + id);
                h.assertTrue(ModTabs.sectionOf(id).equals(set.startsWith("ww2_") ? "ww2_clothing" : "napoleonic_clothing"), "wrong era " + id);
            }
        }
        h.assertTrue(unique.size()==64, "64 distinct pieces"); h.succeed();
    }
    @GameTest(template="arena", batch="rw_deco")
    public static void uniforms_mix_slots_without_replacing_old_sets(GameTestHelper h) {
        var wearer=h.spawn(EntityType.ZOMBIE,new BlockPos(2,2,2)); wearer.setNoAi(true);
        String[] sets={"ww2_su_officer","ww2_de_tanker","nap_fr_infantry","nap_ri_general"};
        String[] slots={"head","chest","legs","feet"};
        for(int i=0;i<4;i++) {
            var item=(UniformItem)ModItems.UNIFORMS.get("uniform_"+sets[i]+"_"+slots[i]).get();
            wearer.setItemSlot(item.getEquipmentSlot(),new ItemStack(item));
        }
        for(int i=0;i<4;i++) {
            var item=(UniformItem)ModItems.UNIFORMS.get("uniform_"+sets[i]+"_"+slots[i]).get();
            h.assertTrue(wearer.getItemBySlot(item.getEquipmentSlot()).is(item),"mixed slot lost");
        }
        h.assertTrue(ModItems.UNIFORMS.size()==128,"old 64 plus new 64");
        h.assertTrue(ModTabs.sectionOf("uniform_su_afghan_chest").equals("modern_clothing"),"Afghan uniform not WWII");
        h.succeed();
    }
}
