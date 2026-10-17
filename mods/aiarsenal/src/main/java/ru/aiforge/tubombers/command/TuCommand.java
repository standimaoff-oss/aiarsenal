package ru.aiforge.tubombers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ru.aiforge.tubombers.aircraft.AircraftType;
import ru.aiforge.tubombers.aircraft.BombType;
import ru.aiforge.tubombers.entity.BomberEntity;
import ru.aiforge.tubombers.entity.Crew;
import ru.aiforge.tubombers.fx.ImpactHandler;
import ru.aiforge.tubombers.item.AircraftItem;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/** /tubombers ... : spawning, loading, targeting and status (also used by the RCON test scripts). */
public final class TuCommand {
    private TuCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tubombers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests((c, b) -> b.suggest("tu95").suggest("tu160").buildFuture())
                                .executes(c -> spawn(c, false, 0, 0))
                                .then(Commands.literal("air")
                                        .then(Commands.argument("height", DoubleArgumentType.doubleArg(10, 400))
                                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(60, 400))
                                                        .executes(c -> spawn(c, true, DoubleArgumentType.getDouble(c, "height"), DoubleArgumentType.getDouble(c, "speed"))))))))
                .then(Commands.literal("load")
                        .then(Commands.argument("bomb", StringArgumentType.word()).suggests((c, b) -> {
                                    for (BombType t : BombType.values()) b.suggest(t.id);
                                    return b.buildFuture();
                                })
                                .executes(c -> load(c, -1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(0, 100)).executes(c -> load(c, IntegerArgumentType.getInteger(c, "count"))))))
                .then(Commands.literal("target")
                        .then(Commands.literal("clear").executes(c -> {
                            BomberEntity b = nearest(c);
                            if (b != null) b.setTarget(null);
                            return b == null ? 0 : 1;
                        }))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                            BomberEntity b = nearest(c);
                            if (b == null) return 0;
                            BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
                            b.setTarget(p);
                            c.getSource().sendSuccess(() -> Component.literal("target " + p.toShortString()), false);
                            return 1;
                        })))
                .then(Commands.literal("board")
                        .then(Commands.argument("role", StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BomberEntity b = nearest(c);
                            if (b == null) return 0;
                            String role = StringArgumentType.getString(c, "role").toUpperCase(Locale.ROOT);
                            if (p.getVehicle() == b) {
                                b.allowDismount = true;
                                p.stopRiding();
                                b.allowDismount = false;
                            }
                            boolean ok = b.board(p, 0, role.equals("LADDER") ? null : role);
                            c.getSource().sendSuccess(() -> Component.literal("board " + role + " " + ok), false);
                            return ok ? 1 : 0;
                        })))
                .then(Commands.literal("set")
                        .then(Commands.argument("what", StringArgumentType.word())
                                .then(Commands.argument("value", FloatArgumentType.floatArg(-1, 2)).executes(TuCommand::set))))
                .then(Commands.literal("release").executes(c -> {
                    BomberEntity b = nearest(c);
                    if (b == null) return 0;
                    b.crewActionAsServer(BomberEntity.A_RELEASE);
                    return 1;
                }))
                .then(Commands.literal("status").executes(TuCommand::status)));
    }

    private static BomberEntity nearest(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        if (s.getEntity() != null && s.getEntity().getVehicle() instanceof BomberEntity b) return b;
        Vec3 p = s.getPosition();
        ServerLevel sl = s.getLevel();
        BomberEntity b = sl.getEntitiesOfClass(BomberEntity.class, new AABB(p, p).inflate(600)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
        if (b == null) s.sendFailure(Component.literal("no bomber within 600 blocks"));
        return b;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, boolean air, double h, double speed) {
        String t = StringArgumentType.getString(c, "type");
        AircraftType type = t.contains("160") ? AircraftType.TU160 : AircraftType.TU95;
        CommandSourceStack s = c.getSource();
        Vec3 p = s.getPosition();
        float yaw = s.getRotation().y;
        BomberEntity b = AircraftItem.spawn(s.getLevel(), type, p.x, p.y + (air ? h : 0), p.z, yaw);
        if (b == null) return 0;
        if (air) b.startAirborne(speed);
        s.sendSuccess(() -> Component.literal("spawned " + type.id + " id=" + b.getId() + " at " + b.blockPosition().toShortString()), true);
        return b.getId();
    }

    private static int load(CommandContext<CommandSourceStack> c, int count) {
        BomberEntity b = nearest(c);
        if (b == null) return 0;
        String id = StringArgumentType.getString(c, "bomb").toLowerCase(Locale.ROOT);
        BombType t = BombType.FAB250;
        for (BombType bt : BombType.values()) if (bt.id.equals(id)) t = bt;
        b.setLoad(t, count < 0 ? b.kind.capacity[t.ordinal()] : count);
        BombType ft = t;
        c.getSource().sendSuccess(() -> Component.literal("loaded " + b.bombCount() + " x " + ft.displayName()), false);
        return b.bombCount();
    }

    private static int set(CommandContext<CommandSourceStack> c) {
        BomberEntity b = nearest(c);
        if (b == null) return 0;
        String w = StringArgumentType.getString(c, "what");
        float v = FloatArgumentType.getFloat(c, "value");
        b.setControl(w, v);
        c.getSource().sendSuccess(() -> Component.literal("set " + w + " = " + v), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        BomberEntity b = nearest(c);
        if (b == null) return 0;
        StringBuilder sb = new StringBuilder();
        var st = b.st;
        sb.append(String.format(Locale.ROOT, "%s id=%d pos=(%.1f %.1f %.1f) yaw=%.1f pitch=%.1f roll=%.1f V=%.1f m/s IAS=%.0f km/h M=%.2f vs=%.1f agl=%.1f ground=%s",
                b.kind.id, b.getId(), b.getX(), b.getY(), b.getZ(), st.yawDeg(), st.pitchDeg(), st.rollDeg(), st.vel.length(), st.ias * 3.6, st.mach,
                st.vel.y, st.agl, st.onGround));
        sb.append(String.format(Locale.ROOT, " | thr=%.2f/%.2f gear=%.2f flaps=%.2f bay=%.2f sweep=%.2f door=%.2f hp=%.0f bombs=%d x %s mode=%d released=%d detonations=%d crater=%d",
                b.throttle(), st.thrust, b.gear, b.flaps, b.bay, b.sweep, b.door, b.health(), b.bombCount(), b.bombType().displayName(), b.releaseMode(),
                b.releasedTotal, ImpactHandler.detonations, ImpactHandler.lastCraterBlocks));
        if (ImpactHandler.lastImpact != null)
            sb.append(String.format(Locale.ROOT, " lastImpact=(%.1f %.1f %.1f)", ImpactHandler.lastImpact.x, ImpactHandler.lastImpact.y, ImpactHandler.lastImpact.z));
        b.target().ifPresent(t -> sb.append(" target=").append(t.toShortString()));
        for (Map.Entry<Integer, Crew> e : b.crew.entrySet()) {
            Entity en = b.level().getEntity(e.getKey());
            Crew cr = e.getValue();
            sb.append(String.format(Locale.ROOT, " | crew %s %s#%d (%.2f %.2f %.2f)", en == null ? "?" : en.getName().getString(), cr.modeName(), cr.index, cr.x, cr.y, cr.z));
        }
        String out = sb.toString();
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }
}
