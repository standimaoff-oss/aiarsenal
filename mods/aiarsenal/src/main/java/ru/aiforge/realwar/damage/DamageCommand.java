package ru.aiforge.realwar.damage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.realwar.blast.ModDamage;
import ru.aiforge.realwar.craft.CraftEntity;
import ru.aiforge.realwar.vehicle.LauncherVehicle;
import ru.aiforge.realwar.vehicle.VehicleEntity;

import java.util.Locale;

import static ru.aiforge.realwar.damage.DamageState.*;

/** r18: /realwar damage &lt;targets&gt; &lt;module|fire|kill|cookoff|hole|repair&gt; - test / scenario tool for module damage. */
public final class DamageCommand {
    private DamageCommand() {
    }

    static final String[] MODS = {"engine", "track_l", "track_r", "barrel", "turret", "ammo", "rotor", "tail_rotor", "wing_l", "wing_r", "tail"};
    static final String[] OTHER = {"fire", "kill", "cookoff", "hole", "repair", "crew_driver", "crew_gunner", "crew_commander", "crew_loader", "crew_radio", "gun0", "gun1", "gun2", "gun3", "gun4", "gun5", "gun6", "gun7", "gun8", "gun9"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("realwar").requires(s -> s.hasPermission(2))
                .then(Commands.literal("damage").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("what", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(concat(), b))
                                .executes(DamageCommand::run))))
                .then(Commands.literal("shot").then(Commands.argument("shooter", EntityArgument.entity())
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("ammo", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(ru.aiforge.realwar.ordnance.Ballistics.Profile.values())
                                                .filter(pr -> pr.kinetic() || pr.tntKg > 0).map(pr -> pr.name().toLowerCase(Locale.ROOT)), b))
                                        .executes(c -> shot(c, 0, Double.NaN, 0))
                                        .then(Commands.argument("lx", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("ly", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("lz", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                                .executes(c -> shot(c, com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "lx"),
                                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "ly"),
                                                                        com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "lz")))))))))));
    }

    /** r20: /realwar shot &lt;shooter&gt; &lt;target&gt; &lt;ammo&gt; [lx ly lz] - fires one round of the given ammo from the shooter's
     *  eyes (or its vehicle) at a point of the target (model frame, default = hull centre); the shooter gets the hit camera. */
    private static int shot(CommandContext<CommandSourceStack> c, double lx, double ly, double lz) throws CommandSyntaxException {
        Entity sh = EntityArgument.getEntity(c, "shooter");
        Entity tg = EntityArgument.getEntity(c, "target");
        String an = StringArgumentType.getString(c, "ammo").toUpperCase(Locale.ROOT);
        ru.aiforge.realwar.ordnance.Ballistics.Profile p;
        try {
            p = ru.aiforge.realwar.ordnance.Ballistics.Profile.valueOf(an);
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal("unknown ammo " + an));
            return 0;
        }
        if (!(tg instanceof VehicleEntity v)) {
            c.getSource().sendFailure(Component.literal("target is not a ground vehicle"));
            return 0;
        }
        ServerLevel sl = c.getSource().getLevel();
        if (Double.isNaN(ly)) {
            VehicleDamage.Geo g = VehicleDamage.geo(v);
            lx = g.cx();
            ly = g.cy();
            lz = g.cz();
        }
        Vec3 aim = v.localToWorld(lx, ly, lz);
        Vec3 from = sh.getEyePosition();
        Entity shooter = sh.getVehicle() != null ? sh.getVehicle() : sh;
        Vec3 dir = aim.subtract(from).normalize();
        Vec3 hit = ru.aiforge.realwar.damage.VehicleHit.clip(v, aim.subtract(dir.scale(40)), aim.add(dir.scale(10)));
        if (hit == null) hit = aim;
        ru.aiforge.realwar.damage.Armour.Ammo a = ru.aiforge.realwar.damage.Armour.Ammo.of(p);
        double sp = p.ejectSpeed;
        double ej = 0.5 * p.m0 * sp * sp;
        boolean he = !p.kinetic();
        float dmg = he ? (float) (p.tntKg < 0.1 ? 9.0 : 20.0 + p.tntKg * 6.0) : (float) Math.min(220.0, ej / 45000.0);
        v.invulnerableTime = 0;
        HitContext.hurt(v, ModDamage.source(sl, ModDamage.ORDNANCE), dmg, hit, dir, he ? HitContext.Kind.HE : HitContext.Kind.AP,
                he ? Math.max(0.3, p.tntKg) : dmg, new HitContext.Shot(shooter.getId(), a, sp, from));
        VehicleDamage.Cam cam = VehicleDamage.lastCam;
        String res = cam == null ? "-" : new String[]{"ricochet", "no_pen", "pen", "module"}[cam.res().result()] + " "
                + ru.aiforge.realwar.damage.Armour.ZONE_KEYS[Math.max(0, cam.res().zone())] + " pen " + Math.round(cam.res().pen()) + " eff " + Math.round(cam.res().eff())
                + " crewHit " + Integer.toBinaryString(cam.crewHit()) + " crewDead " + Integer.toBinaryString(cam.crewDead()) + " hp " + Math.round(v.hp())
                + (v.dmg.dead() || v.isRemoved() ? " DEAD" : "");
        c.getSource().sendSuccess(() -> Component.literal("shot " + an + ": " + res), true);
        return 1;
    }

    private static String[] concat() {
        String[] r = new String[MODS.length + OTHER.length];
        System.arraycopy(MODS, 0, r, 0, MODS.length);
        System.arraycopy(OTHER, 0, r, MODS.length, OTHER.length);
        return r;
    }

    private static int run(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String what = StringArgumentType.getString(c, "what").toLowerCase(Locale.ROOT);
        ServerLevel sl = c.getSource().getLevel();
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) {
            if (apply(sl, e, what)) n++;
        }
        int fn = n;
        c.getSource().sendSuccess(() -> Component.literal("damage " + what + ": " + fn), true);
        return n;
    }

    static int module(String what) {
        for (int i = 0; i < MODS.length; i++) if (MODS[i].equals(what)) return i;
        if (what.startsWith("gun") && what.length() == 4 && Character.isDigit(what.charAt(3))) return GUN0 + (what.charAt(3) - '0');
        return -1;
    }

    public static boolean apply(ServerLevel sl, Entity e, String what) {
        if (e instanceof VehicleEntity v) {
            DamageState s = v.dmg;
            switch (what) {
                case "fire" -> VehicleDamage.ignite(v, 400);
                case "kill" -> VehicleDamage.kill(sl, v, false);
                case "cookoff" -> {
                    if (v instanceof LauncherVehicle lv && lv.loadedCount() == 0) lv.fillMagazine();
                    VehicleDamage.detonate(sl, v);
                }
                case "repair" -> {
                    for (int i = 0; i < 2; i++) {
                        Entity d = s.trackDebris[i] >= 0 ? sl.getEntity(s.trackDebris[i]) : null;
                        if (d != null) d.discard();
                        s.trackDebris[i] = -1;
                    }
                    s.clearAll();
                    v.setHp(v.kind.hp);
                }
                default -> {
                    if (what.startsWith("crew_")) {
                        int role = java.util.Arrays.asList(Crew.KEYS).indexOf(what.substring(5));
                        int[] r = Crew.roles(v.kind);
                        for (int i = 0; i < r.length; i++) {
                            if (r[i] == role && s.crew[i] > 0) {
                                Crew.hurt(sl, v, i, 200f);
                                if (Crew.knockedOut(v)) VehicleDamage.knockOut(sl, v);
                                break;
                            }
                        }
                        break;
                    }
                    int m = module(what);
                    if (m < 0) return false;
                    if (m == AMMO) VehicleDamage.detonate(sl, v);
                    else VehicleDamage.module(sl, v, m, VehicleDamage.maxHp(v, m) * 1.05f);
                }
            }
            s.dirtyNow = true;
            v.publishDamage();
            return true;
        }
        if (e instanceof CraftEntity cr) {
            DamageState s = cr.dmg;
            switch (what) {
                case "fire" -> CraftDamage.ignite(cr, 400);
                case "kill", "cookoff" -> CraftDamage.kill(cr);
                case "repair" -> {
                    s.clearAll();
                    s.holes.clear();
                    cr.setHp(cr.kind.hp);
                }
                case "hole" -> {
                    if (cr.kind.water == null) return false;
                    float y = cr.kind.water.draft() * 0.45f;
                    float hb = cr.kind.water.beam();
                    double z = (sl.random.nextDouble() - 0.5) * cr.kind.water.length() * 0.5;
                    Vec3 a = cr.localToWorld(hb * 2, y, z), b = cr.localToWorld(-hb * 2, y, z);
                    double pw = cr.kind.water.length() > 60 ? 380 : 40;
                    HitContext.hurt(cr, ModDamage.source(sl, ModDamage.ORDNANCE), 1f, a, b.subtract(a).normalize(), HitContext.Kind.AP, pw);
                }
                default -> {
                    int m = module(what);
                    if (m < 0) return false;
                    CraftDamage.module(sl, cr, m, CraftDamage.maxHp(cr, m) * 1.05f);
                }
            }
            s.dirtyNow = true;
            cr.publishDamage();
            return true;
        }
        return false;
    }
}
