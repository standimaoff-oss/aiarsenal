package ru.aiforge.realwar.npc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /realwar npc ...} - spawn AI squads and single soldiers.
 * <pre>
 * /realwar npc squad &lt;faction&gt; &lt;era&gt; [count]           raise a squad with crews and pilots for its own hardware
 * /realwar npc squad &lt;faction&gt; &lt;era&gt; [count] vehicle   also spawn the squad's own ground vehicle
 * /realwar npc spawn &lt;faction&gt; &lt;era&gt; &lt;rank&gt;           one soldier of the given rank
 * /realwar npc clear [radius]                          remove soldiers around the player
 * </pre>
 */
public final class NpcCommand {
    private static final String[] FACTIONS = {"nato", "russia", "germany", "france"};
    private static final String[] ERAS = {"modern", "ww2", "napoleonic"};
    private static final String[] RANKS = {"private", "sergeant", "officer", "crew", "pilot"};

    private NpcCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("realwar").requires(s -> s.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.literal("squad")
                                .then(Commands.argument("faction", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(FACTIONS, b))
                                        .then(Commands.argument("era", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ERAS, b))
                                                .executes(c -> squad(c, 3, false))
                                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 24))
                                                        .executes(c -> squad(c, IntegerArgumentType.getInteger(c, "count"), false))
                                                        .then(Commands.literal("vehicle")
                                                                .executes(c -> squad(c, IntegerArgumentType.getInteger(c, "count"), true)))))))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("faction", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(FACTIONS, b))
                                        .then(Commands.argument("era", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ERAS, b))
                                                .then(Commands.argument("rank", com.mojang.brigadier.arguments.StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(RANKS, b))
                                                        .executes(NpcCommand::one)))))
                        .then(Commands.literal("clear")
                                .executes(c -> clear(c, 128))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 512))
                                        .executes(c -> clear(c, IntegerArgumentType.getInteger(c, "radius")))))));
    }

    private static int squad(CommandContext<CommandSourceStack> c, int count, boolean vehicle) {
        Faction f = Faction.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "faction"));
        Era era = Era.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "era"));
        ServerLevel sl = c.getSource().getLevel();
        if (f == null || era == null) {
            c.getSource().sendFailure(Component.translatable("npc.aiarsenal.cmd.bad_args"));
            return 0;
        }
        if (!f.allows(era)) {
            c.getSource().sendFailure(Component.translatable("npc.aiarsenal.cmd.era_mismatch",
                    Component.translatable(f.langKey()), Component.translatable(era.langKey())));
            return 0;
        }
        Vec3 at = c.getSource().getPosition();
        var squad = SoldierSpawner.spawnSquad(sl, at, f, era, count, vehicle, true);
        c.getSource().sendSuccess(() -> Component.translatable("npc.aiarsenal.cmd.squad",
                squad.size(), Component.translatable(f.langKey()), Component.translatable(era.langKey())), true);
        return squad.size();
    }

    private static int one(CommandContext<CommandSourceStack> c) {
        Faction f = Faction.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "faction"));
        Era era = Era.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "era"));
        Rank rank = Rank.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "rank"));
        if (f == null || era == null || rank == null || !f.allows(era)) {
            c.getSource().sendFailure(Component.translatable("npc.aiarsenal.cmd.bad_args"));
            return 0;
        }
        SoldierEntity s = SoldierSpawner.spawnOne(c.getSource().getLevel(), c.getSource().getPosition(), f, era, rank);
        if (s == null) return 0;
        c.getSource().sendSuccess(() -> Component.translatable("npc.aiarsenal.cmd.one",
                Component.translatable(rank.langKey()), Component.translatable(f.langKey()),
                Component.translatable(era.langKey())), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> c, int radius) {
        int n = SoldierSpawner.clear(c.getSource().getLevel(), c.getSource().getPosition(), radius);
        c.getSource().sendSuccess(() -> Component.translatable("npc.aiarsenal.cmd.clear", n), true);
        return n;
    }

    /** Suggestions are lowercase ids; exposed for the tab-completion tests. */
    public static String[] factions() {
        String[] out = new String[FACTIONS.length];
        for (int i = 0; i < FACTIONS.length; i++) out[i] = FACTIONS[i].toLowerCase(Locale.ROOT);
        return out;
    }
}
