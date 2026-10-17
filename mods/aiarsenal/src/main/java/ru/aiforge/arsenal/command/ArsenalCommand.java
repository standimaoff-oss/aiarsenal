package ru.aiforge.arsenal.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.arsenal.control.RemoteControl;
import ru.aiforge.arsenal.core.Kind;
import ru.aiforge.arsenal.core.Warheads;
import ru.aiforge.arsenal.entity.ArsEntity;
import ru.aiforge.arsenal.entity.FpvDrone;
import ru.aiforge.arsenal.entity.IcbmEntity;
import ru.aiforge.arsenal.entity.LauncherEntity;
import ru.aiforge.arsenal.entity.MissileEntity;
import ru.aiforge.arsenal.entity.NukeEntity;
import ru.aiforge.arsenal.entity.ReconDrone;
import ru.aiforge.arsenal.entity.ShahedDrone;
import ru.aiforge.arsenal.item.DetonatorItem;
import ru.aiforge.arsenal.item.TabletItem;
import ru.aiforge.arsenal.nuke.ChargeRegistry;
import ru.aiforge.arsenal.nuke.NuclearBlast;
import ru.aiforge.arsenal.registry.ArsenalRegistry;

import java.util.Arrays;

/** /arsenal spawn|launch|nuke|charges|detonate|fuze|target|status (operators). */
public final class ArsenalCommand {
    private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Kind.values()).map(k -> k.id), b);

    private ArsenalCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("arsenal").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                        .executes(c -> spawn(c, c.getSource().getPosition(), c.getSource().getRotation().y))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> spawn(c, Vec3Argument.getVec3(c, "pos"), c.getSource().getRotation().y))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                                        .executes(c -> spawn(c, Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "yaw")))))))
                .then(Commands.literal("launch").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                        .then(Commands.argument("from", Vec3Argument.vec3()).then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(ArsenalCommand::launch)))))
                .then(Commands.literal("nuke").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                        .then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
                            Kind k = kind(c);
                            if (!k.nuclear()) throw new IllegalArgumentException("not nuclear");
                            NuclearBlast.detonate(c.getSource().getLevel(), Vec3Argument.getVec3(c, "pos"), k, null, c.getSource().getEntity());
                            return 1;
                        }))))
                .then(Commands.literal("charges").executes(ArsenalCommand::charges))
                .then(Commands.literal("detonate")
                        .then(Commands.literal("all").executes(c -> detonate(c, -1)))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(c -> detonate(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(Commands.literal("fuze").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .then(Commands.literal("remote").executes(c -> fuze(c, ChargeRegistry.FUZE_REMOTE)))
                        .then(Commands.literal("contact").executes(c -> fuze(c, ChargeRegistry.FUZE_CONTACT)))
                        .then(Commands.literal("air").executes(c -> fuze(c, ChargeRegistry.FUZE_AIR)))))
                .then(Commands.literal("target").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    BlockPos t = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    TabletItem.storeTarget(p, t);
                    c.getSource().sendSuccess(() -> Component.literal("tablet target " + t.toShortString()), false);
                    return 1;
                })))
                .then(Commands.literal("status").executes(ArsenalCommand::status)));
    }

    private static Kind kind(CommandContext<CommandSourceStack> c) {
        Kind k = Kind.byId(StringArgumentType.getString(c, "kind"));
        if (k == null) throw new IllegalArgumentException("unknown kind");
        return k;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Vec3 pos, float yaw) {
        Kind k = kind(c);
        ServerLevel sl = c.getSource().getLevel();
        Entity src = c.getSource().getEntity();
        java.util.UUID owner = src == null ? null : src.getUUID();
        Entity e = switch (k.cat) {
            case RECON -> ReconDrone.create(sl, k, pos, yaw, owner);
            case SHAHED -> ShahedDrone.create(sl, k, pos, yaw, 0, null, owner, ShahedDrone.GROUND);
            case FPV -> {
                FpvDrone d = new FpvDrone(ArsenalRegistry.FPV.get(), sl);
                d.setKind(k);
                d.setPos(pos);
                d.setYRot(yaw);
                d.owner = owner;
                sl.addFreshEntity(d);
                yield d;
            }
            case LAUNCHER -> LauncherEntity.create(sl, k, pos, yaw);
            case NUKE -> NukeEntity.create(sl, k, pos, Vec3.ZERO, yaw, ChargeRegistry.FUZE_REMOTE, owner, false);
            default -> null;
        };
        if (e == null) {
            c.getSource().sendFailure(Component.literal("use /arsenal launch for " + k.id));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("spawned " + k.id + " id=" + e.getId()
                + (e instanceof NukeEntity n ? " charge #" + n.charge : "")), true);
        return 1;
    }

    private static int launch(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Kind k = kind(c);
        ServerLevel sl = c.getSource().getLevel();
        Vec3 from = Vec3Argument.getVec3(c, "from");
        BlockPos to = BlockPosArgument.getBlockPos(c, "to");
        Entity src = c.getSource().getEntity();
        java.util.UUID owner = src == null ? null : src.getUUID();
        Vec3 d = Vec3.atCenterOf(to).subtract(from);
        float yaw = ArsEntity.yawOf(d);
        Entity e;
        switch (k) {
            case S136, S131, S238 -> {
                ShahedDrone s = ShahedDrone.create(sl, k, from, yaw, 15f, to, owner, ShahedDrone.BOOST);
                s.setDeltaMovement(ArsEntity.dir(yaw, 15f).scale(0.2));
                e = s;
            }
            case LIUTYI -> {
                ShahedDrone s = ShahedDrone.create(sl, k, from, yaw, 0, null, owner, ShahedDrone.GROUND);
                s.launch(to);
                e = s;
            }
            case KALIBR, TOMAHAWK -> e = MissileEntity.create(sl, k, from, ArsEntity.dir(yaw, 45f).scale(0.6), to, null, owner, MissileEntity.BOOST);
            case KH101, KH55 -> e = MissileEntity.create(sl, k, from, ArsEntity.dir(yaw, 0).scale(ArsEntity.bpt(200)), to, null, owner, MissileEntity.DROP);
            case HELLFIRE, KAB50 -> e = MissileEntity.create(sl, k, from, d.normalize().scale(k == Kind.KAB50 ? 1.5 : 0.5), to, null, owner, MissileEntity.CRUISE);
            case YARS -> e = IcbmEntity.launch(sl, from, Vec3.atCenterOf(to), owner);
            case RDS1, AN602 -> e = NukeEntity.create(sl, k, from, ArsEntity.dir(yaw, 0).scale(ArsEntity.bpt(220)), yaw, ChargeRegistry.FUZE_AIR, owner, true);
            default -> {
                c.getSource().sendFailure(Component.literal("cannot launch " + k.id));
                return 0;
            }
        }
        Entity fe = e;
        c.getSource().sendSuccess(() -> Component.literal("launched " + k.id + " id=" + fe.getId() + " -> " + to.toShortString()), true);
        return 1;
    }

    private static int charges(CommandContext<CommandSourceStack> c) {
        ChargeRegistry reg = ChargeRegistry.get(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.literal(reg.charges.size() + " charges"), false);
        for (ChargeRegistry.Charge ch : reg.charges)
            c.getSource().sendSuccess(() -> Component.literal("#" + ch.id + " " + ch.kind.id + " fuze=" + ch.fuze + " status=" + ch.status + " at "
                    + BlockPos.containing(ch.pos).toShortString() + " " + ch.dim.location()), false);
        return reg.charges.size();
    }

    private static int detonate(CommandContext<CommandSourceStack> c, int id) {
        ChargeRegistry reg = ChargeRegistry.get(c.getSource().getServer());
        ServerPlayer p = c.getSource().getPlayer();
        int n = 0;
        for (ChargeRegistry.Charge ch : reg.charges.toArray(new ChargeRegistry.Charge[0])) {
            if (id >= 0 && ch.id != id) continue;
            if (p != null) DetonatorItem.detonate(p, ch);
            else {
                ServerLevel sl = c.getSource().getServer().getLevel(ch.dim);
                if (sl == null) continue;
                Entity e = ch.entity == null ? null : sl.getEntity(ch.entity);
                if (e instanceof NukeEntity ne) ne.detonate(sl);
                else if (e instanceof IcbmEntity ie) ie.detonate(sl);
                else {
                    reg.remove(ch);
                    NuclearBlast.detonate(sl, ch.pos, ch.kind, null, null);
                }
            }
            n++;
        }
        int fn = n;
        c.getSource().sendSuccess(() -> Component.literal("detonated " + fn), true);
        return n;
    }

    private static int fuze(CommandContext<CommandSourceStack> c, int f) {
        ChargeRegistry reg = ChargeRegistry.get(c.getSource().getServer());
        ChargeRegistry.Charge ch = reg.byId(IntegerArgumentType.getInteger(c, "id"));
        if (ch == null) {
            c.getSource().sendFailure(Component.literal("no such charge"));
            return 0;
        }
        ch.fuze = f;
        reg.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("charge #" + ch.id + " fuze=" + f), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        ServerLevel sl = c.getSource().getLevel();
        int[] n = new int[Kind.Cat.values().length];
        StringBuilder sb = new StringBuilder();
        for (Entity e : sl.getAllEntities())
            if (e instanceof ArsEntity a) {
                n[a.kind().cat.ordinal()]++;
                if (sb.length() < 900)
                    sb.append(String.format("%n %s id=%d st=%d hp=%.0f at %d %d %d v=%.2f", a.kind().id, a.getId(), a.state(), a.hp(), (int) a.getX(),
                            (int) a.getY(), (int) a.getZ(), a.getDeltaMovement().length()));
            }
        StringBuilder head = new StringBuilder("warheads=" + Warheads.count + " nukes=" + NuclearBlast.detonations + " blastBusy=" + NuclearBlast.busy()
                + " lastBlocks=" + NuclearBlast.lastBlocks + " charges=" + ChargeRegistry.get(c.getSource().getServer()).charges.size()
                + " fallout=" + ChargeRegistry.get(c.getSource().getServer()).fallout.size() + " |");
        for (Kind.Cat cat : Kind.Cat.values()) head.append(' ').append(cat.name().toLowerCase()).append('=').append(n[cat.ordinal()]);
        String out = head + sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    static boolean session(ServerPlayer p) {
        return RemoteControl.inSession(p);
    }
}
