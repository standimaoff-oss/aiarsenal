package ru.aiforge.realwar.npc;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import ru.aiforge.realwar.craft.CraftKind;
import ru.aiforge.realwar.handheld.FirearmItem;
import ru.aiforge.realwar.handheld.GunKind;
import ru.aiforge.realwar.vehicle.VehicleEntity;
import ru.aiforge.realwar.vehicle.VehicleKind;

/**
 * Equipment lists, ammunition, aim helpers and the faction checks shared by all AI soldiers.
 * Items are looked up by registry name so a missing optional item degrades to an empty stack instead of crashing.
 */
public final class NpcGear {
    private static final String NS = "aiarsenal";
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOUR_NAMES = {"head", "chest", "legs", "feet"};

    private NpcGear() {
    }

    public static Item item(String id) {
        if (id == null || id.isEmpty()) return Items.AIR;
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(NS, id));
        return it == null ? Items.AIR : it;
    }

    public static ItemStack stack(String id, int count) {
        Item it = item(id);
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it, count);
    }

    public static ItemStack uniform(String set, EquipmentSlot slot) {
        int i = 0;
        for (; i < ARMOUR.length; i++) if (ARMOUR[i] == slot) break;
        if (i >= ARMOUR.length) return ItemStack.EMPTY;
        return stack("uniform_" + set + "_" + ARMOUR_NAMES[i], 1);
    }

    public static ItemStack weapon(Faction f, Era era, Rank rank) {
        ItemStack st = stack(f.weaponId(era, rank), 1);
        if (st.getItem() instanceof FirearmItem fa) {
            FirearmItem.setAmmo(st, fa.kind.cap);
            FirearmItem.setMode(st, fa.kind.auto ? 1 : 0);
        }
        return st;
    }

    /** Fills the main hand, off hand (sabre for 1812) and the four uniform pieces. */
    public static void equip(SoldierEntity s) {
        Faction f = s.faction();
        Era era = s.era();
        Rank rank = s.rank();
        s.setItemSlot(EquipmentSlot.MAINHAND, weapon(f, era, rank));
        s.setItemSlot(EquipmentSlot.OFFHAND, stack(f.meleeId(era), 1));
        String set = f.uniformSet(era, rank);
        for (int i = 0; i < ARMOUR.length; i++) {
            s.setItemSlot(ARMOUR[i], uniform(set, ARMOUR[i]));
        }
    }

    public static Item ammoItem(GunKind k) {
        return k == null ? Items.AIR : item(k.ammo);
    }

    // ------------------------------------------------------------------ factions

    public static boolean canUseVehicle(Faction f, Era era, VehicleKind k) {
        if (k == null) return false;
        for (VehicleKind v : f.vehicles(era)) if (v == k) return true;
        return false;
    }

    public static boolean canUseCraft(Faction f, Era era, CraftKind k) {
        if (k == null) return false;
        for (CraftKind c : f.aircraft(era)) if (c == k) return true;
        return false;
    }

    // ------------------------------------------------------------------ ballistics / aiming

    /** Cone displacement in radians; a gaussian keeps most rounds close and a few far off. */
    public static Vec3 jitter(Vec3 dir, double spreadRad, RandomSource rnd) {
        Vec3 d = dir.normalize();
        Vec3 helper = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = d.cross(helper).normalize();
        Vec3 v = d.cross(u).normalize();
        double a = rnd.nextGaussian() * spreadRad;
        double b = rnd.nextGaussian() * spreadRad;
        return d.add(u.scale(a)).add(v.scale(b)).normalize();
    }

    /**
     * Where an AI gunner aims: centre mass by default. With a skill-dependent chance it picks a real weak zone of a
     * vehicle (lower side hull, rear quarter, running gear); the angular error afterwards makes even a good gunner
     * miss a moving target now and then.
     */
    public static Vec3 aimPoint(Vec3 from, Entity target, double skill, RandomSource rnd) {
        Vec3 centre = target.position().add(0, target.getBbHeight() * 0.55, 0);
        if (target instanceof VehicleEntity v) {
            double chance = 0.15 + 0.55 * skill;
            if (rnd.nextDouble() < chance) {
                Vec3 fwd = v.forward();
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                double sign = rnd.nextBoolean() ? 1 : -1;
                centre = centre.add(fwd.scale(-v.spec.length() * 0.22))
                        .add(side.scale(sign * v.spec.aabbW() * 0.28));
                centre = new Vec3(centre.x, v.getY() + 0.55 + rnd.nextDouble() * 0.9, centre.z);
            }
        }
        Vec3 lead = target.getDeltaMovement().scale(Math.min(20.0, from.distanceTo(centre) / 8.0));
        centre = centre.add(lead.scale(0.55));
        Vec3 d = centre.subtract(from);
        double err = Math.toRadians(0.30 + (1.0 - skill) * 1.9);
        return centre.add(jitter(d, err, rnd).scale(d.length()).subtract(d));
    }

    /** Tank / gun aim point with a lead and a weak-spot roll. */
    public static Vec3 vehicleAimPoint(Vec3 from, Entity target, double skill, RandomSource rnd) {
        return aimPoint(from, target, Math.min(0.98, skill + 0.04), rnd);
    }

    public static Vec3 eye(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getEyeHeight() - 0.12, e.getZ());
    }

    /** Smallest horizontal distance to the given point, used by the squad leash. */
    public static double horizontalDistance(Entity e, Vec3 p) {
        double dx = e.getX() - p.x, dz = e.getZ() - p.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
