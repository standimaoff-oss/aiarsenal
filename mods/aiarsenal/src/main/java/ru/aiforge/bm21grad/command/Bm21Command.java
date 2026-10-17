package ru.aiforge.bm21grad.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.aiforge.bm21grad.Bm21Grad;
import ru.aiforge.bm21grad.ammo.RocketType;
import ru.aiforge.bm21grad.ballistics.Ballistics;
import ru.aiforge.bm21grad.entity.Bm21Entity;
import ru.aiforge.bm21grad.entity.RocketEntity;
import ru.aiforge.bm21grad.fx.GhostManager;
import ru.aiforge.bm21grad.fx.ImpactHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * /bm21 ... - every feature is reachable from the console/RCON (no getPlayerOrException).
 * Vehicles are chosen with an entity selector, e.g. {@code @e[type=bm21grad:bm21,limit=1,sort=nearest]}.
 */
@Mod.EventBusSubscriber(modid = Bm21Grad.MODID)
public final class Bm21Command {
    private static final SimpleCommandExceptionType NO_VEHICLE = new SimpleCommandExceptionType(Component.translatable("cmd.aiarsenal.no_vehicle"));
    private static final SimpleCommandExceptionType BAD_TYPE = new SimpleCommandExceptionType(Component.translatable("cmd.aiarsenal.bad_type"));
    private static final SuggestionProvider<CommandSourceStack> TYPES = (c, b) -> SharedSuggestionProvider.suggest(
            Arrays.stream(RocketType.values()).filter(t -> t != RocketType.NONE).map(t -> t.id), b);

    private Bm21Command() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("bm21").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> spawn(c, 0f))
                                .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360))
                                        .executes(c -> spawn(c, FloatArgumentType.getFloat(c, "yaw"))))))
                .then(Commands.literal("target").then(vehicles()
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> {
                                    BlockPos p = BlockPosArgument.getBlockPos(c, "pos");
                                    return each(c, v -> v.setTarget(p));
                                }))
                        .then(Commands.literal("ground").then(Commands.argument("column", ColumnPosArgument.columnPos())
                                .executes(c -> {
                                    ColumnPos col = ColumnPosArgument.getColumnPos(c, "column");
                                    return each(c, v -> v.setTarget(v.resolveGround(new BlockPos(col.x(), 0, col.z()))));
                                })))
                        .then(Commands.literal("clear").executes(c -> each(c, v -> v.setTarget(null))))))
                .then(Commands.literal("fire").then(vehicles()
                        .executes(c -> each(c, v -> v.startFire(null)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, Bm21Entity.TUBES))
                                .executes(c -> each(c, v -> {
                                    v.setFireMode(IntegerArgumentType.getInteger(c, "count"));
                                    return v.startFire(null);
                                })))))
                .then(Commands.literal("cease").then(vehicles().executes(c -> each(c, Bm21Entity::ceaseFire))))
                .then(Commands.literal("reload").then(vehicles().executes(c -> each(c, v -> v.startReload(true)))))
                .then(Commands.literal("load").then(vehicles().then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                        .executes(c -> {
                            RocketType t = type(c);
                            return each(c, v -> {
                                v.loadAll(t);
                                return Component.translatable("cmd.aiarsenal.loaded", Bm21Entity.TUBES, Component.translatable(t.translationKey()));
                            });
                        }))))
                .then(Commands.literal("ammo").then(vehicles().then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                        .executes(c -> {
                            RocketType t = type(c);
                            return each(c, v -> v.setAmmoSelection(t));
                        }))))
                .then(Commands.literal("mode").then(vehicles().then(Commands.argument("count", IntegerArgumentType.integer(1, Bm21Entity.TUBES))
                        .executes(c -> each(c, v -> v.setFireMode(IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("aim").then(vehicles().then(Commands.argument("turret", FloatArgumentType.floatArg(-180, 180))
                        .then(Commands.argument("elevation", FloatArgumentType.floatArg(0, 55))
                                .executes(c -> each(c, v -> v.aim(FloatArgumentType.getFloat(c, "turret"), FloatArgumentType.getFloat(c, "elevation"))))))))
                .then(Commands.literal("stow").then(vehicles().executes(c -> each(c, v -> {
                    if (v.hasState(Bm21Entity.ST_SHIELDS)) return v.toggleShields();
                    return Component.translatable("msg.aiarsenal.travel_position");
                }))))
                .then(Commands.literal("damage").then(vehicles().then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                        .executes(c -> each(c, v -> {
                            v.hurt(v.damageSources().explosion(null, null), FloatArgumentType.getFloat(c, "amount"));
                            return Component.literal(v.statusLine());
                        })))))
                .then(Commands.literal("status").then(vehicles().executes(Bm21Command::status)))
                .then(Commands.literal("strike").then(Commands.argument("type", StringArgumentType.word()).suggests(TYPES)
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> strike(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 40))
                                        .executes(c -> strike(c, IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("solve").then(Commands.argument("distance", FloatArgumentType.floatArg(1, 20000))
                        .executes(c -> solve(c, 0))
                        .then(Commands.argument("dy", FloatArgumentType.floatArg(-300, 300))
                                .executes(c -> solve(c, FloatArgumentType.getFloat(c, "dy"))))))
                .then(Commands.literal("stats").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("detonations=" + ImpactHandler.detonations + " ghosts=" + GhostManager.count()), false);
                    return ImpactHandler.detonations;
                })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> vehicles() {
        return Commands.argument("vehicles", EntityArgument.entities());
    }

    private static RocketType type(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        RocketType t = RocketType.byId(StringArgumentType.getString(c, "type"));
        if (t == null || t == RocketType.NONE) throw BAD_TYPE.create();
        return t;
    }

    private static List<Bm21Entity> list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<? extends Entity> es = EntityArgument.getEntities(c, "vehicles");
        List<Bm21Entity> out = new ArrayList<>();
        for (Entity e : es) if (e instanceof Bm21Entity v) out.add(v);
        if (out.isEmpty()) throw NO_VEHICLE.create();
        return out;
    }

    private static int each(CommandContext<CommandSourceStack> c, Function<Bm21Entity, Component> f) throws CommandSyntaxException {
        int n = 0;
        for (Bm21Entity v : list(c)) {
            Component msg = f.apply(v);
            c.getSource().sendSuccess(() -> Component.literal("[BM-21 #" + v.getId() + "] ").append(msg), true);
            n++;
        }
        return n;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, float yaw) throws CommandSyntaxException {
        BlockPos p = BlockPosArgument.getSpawnablePos(c, "pos");
        ServerLevel sl = c.getSource().getLevel();
        Bm21Entity e = Bm21Entity.create(sl, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw);
        sl.addFreshEntity(e);
        c.getSource().sendSuccess(() -> Component.translatable("cmd.aiarsenal.spawned", e.getId(), p.getX(), p.getY(), p.getZ()), true);
        return e.getId();
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int loaded = 0;
        for (Bm21Entity v : list(c)) {
            loaded += v.loadedCount();
            c.getSource().sendSuccess(() -> Component.literal("[BM-21 #" + v.getId() + "] " + v.statusLine()), false);
        }
        return loaded;
    }

    /** Test helper: rockets arrive from 60 blocks above the position (no launcher needed). */
    private static int strike(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        RocketType t = type(c);
        BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
        ServerLevel sl = c.getSource().getLevel();
        for (int i = 0; i < count; i++) {
            double ox = count == 1 ? 0 : (sl.random.nextDouble() - 0.5) * 24, oz = count == 1 ? 0 : (sl.random.nextDouble() - 0.5) * 24;
            Vec3 at = new Vec3(p.getX() + 0.5 + ox - 20, p.getY() + 60, p.getZ() + 0.5 + oz);
            RocketEntity r = RocketEntity.launch(sl, t, at, new Vec3(0.9, -1.8, 0), 0, c.getSource().getEntity(), null);
            r.age = 200;
            r.roared = true;
            r.groundHint = p.getY();
            sl.addFreshEntity(r);
        }
        c.getSource().sendSuccess(() -> Component.translatable("cmd.aiarsenal.strike", count, Component.translatable(t.translationKey()), p.getX(), p.getY(), p.getZ()), true);
        return count;
    }

    private static int solve(CommandContext<CommandSourceStack> c, float dy) {
        float dist = FloatArgumentType.getFloat(c, "distance");
        double thrust = Ballistics.thrust(ru.aiforge.bm21grad.Config.RANGE_SCALE.get(), 1);
        Ballistics.Solution s = Ballistics.solve(dist, dy, thrust, 0);
        double max = Ballistics.maxRange(thrust, 0, dy);
        c.getSource().sendSuccess(() -> Component.literal(String.format("solve d=%.0f dy=%.0f -> ok=%s elev=%.2f flight=%.1fs apex=%.0f maxRange=%.0f",
                dist, dy, s.ok(), s.elevationDeg(), s.flightTicks() / 20.0, s.apex(), max)), false);
        return s.ok() ? (int) Math.round(s.elevationDeg() * 100) : 0;
    }
}
