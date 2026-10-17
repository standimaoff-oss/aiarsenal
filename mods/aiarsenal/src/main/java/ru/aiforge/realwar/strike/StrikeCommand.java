package ru.aiforge.realwar.strike;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec2Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import ru.aiforge.realwar.RealWar;
import ru.aiforge.arsenal.nuke.ChargeRegistry;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * r19: /realwar strike &lt;artillery|fab500|fab1000|fab5000|nuclear&gt; &lt;x z&gt; [heading] - operator / map-maker tool that
 * schedules the same strike as the radio, without the radio, range limit, cooldown or RDS-1 charge. Nuclear = air burst.
 */
public final class StrikeCommand {
    private StrikeCommand() {
    }

    private static final UUID SERVER = new UUID(0x5157_7269_6b65L, 0x0000_0000_0000_0019L);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("realwar").requires(s -> s.hasPermission(2))
                .then(Commands.literal("strike").then(Commands.argument("type", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(StrikeType.values()).map(StrikeType::key), b))
                        .then(Commands.argument("target", Vec2Argument.vec2())
                                .executes(c -> run(c, c.getSource().getRotation().y))
                                .then(Commands.argument("heading", FloatArgumentType.floatArg(-360f, 360f))
                                        .executes(c -> run(c, FloatArgumentType.getFloat(c, "heading"))))))));
    }

    private static int run(CommandContext<CommandSourceStack> c, float heading) {
        String k = StringArgumentType.getString(c, "type").toLowerCase(Locale.ROOT);
        StrikeType t = Arrays.stream(StrikeType.values()).filter(v -> v.key().equals(k)).findFirst().orElse(null);
        if (t == null) {
            c.getSource().sendFailure(Component.literal("unknown strike type " + k));
            return 0;
        }
        Vec2 v = Vec2Argument.getVec2(c, "target");
        UUID owner = c.getSource().getEntity() != null ? c.getSource().getEntity().getUUID() : SERVER;
        StrikeManager.Strike s = StrikeManager.plan(c.getSource().getLevel(), owner, t, v.x, v.y, heading, ChargeRegistry.FUZE_AIR);
        int eta = Math.max(1, s.firstImpact / 20);
        c.getSource().sendSuccess(() -> Component.translatable("msg.aiarsenal.radio.ack." + t.key(), (int) v.x, (int) v.y, eta), true);
        RealWar.LOGGER.info("command strike {} at {} {} heading {} eta {} s", t, (int) v.x, (int) v.y, heading, eta);
        return 1;
    }
}
