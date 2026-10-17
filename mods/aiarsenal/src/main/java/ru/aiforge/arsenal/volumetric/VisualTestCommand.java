package ru.aiforge.arsenal.volumetric;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import ru.aiforge.realwar.network.ModNetwork;
import ru.aiforge.realwar.network.ConventionalEffectPacket;
import ru.aiforge.realwar.network.NukeEffectPacket;

/** Operator-only visual QA through the SAME packets as actual bombs.
 * Explicitly non-destructive: no entities, terrain changes, radiation or damage. */
@Mod.EventBusSubscriber(modid = "aiarsenal")
public final class VisualTestCommand {
    private VisualTestCommand() {}

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        var root = Commands.literal("arsenal_visual_test").requires(s -> s.hasPermission(2));
        for (String kind : new String[]{"blast", "nuke", "legacy_nuke", "bm21", "bomber"}) {
            root.then(Commands.literal(kind).then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
                var level = c.getSource().getLevel();
                var p = Vec3Argument.getVec3(c, "pos");
                var target = PacketDistributor.DIMENSION.with(level::dimension);
                switch (kind) {
                    case "blast" -> ModNetwork.CHANNEL.send(target, new ConventionalEffectPacket(p.x, p.y, p.z, 120f, true, 27));
                    case "nuke" -> ModNetwork.CHANNEL.send(target, new NukeEffectPacket(p.x, p.y, p.z, 15f, true, 27, 1f, 100, (int)p.y, 10));
                    case "legacy_nuke" -> ru.aiforge.arsenal.network.ArsenalNetwork.CHANNEL.send(target,
                        new ru.aiforge.arsenal.network.NukeFxPacket(p.x, p.y, p.z, (int)p.y, 20f, 0));
                    case "bm21" -> ru.aiforge.bm21grad.network.ModNetwork.CHANNEL.send(target,
                        new ru.aiforge.bm21grad.network.FxPacket(p.x, p.y, p.z, 0, 6f));
                    case "bomber" -> ru.aiforge.tubombers.network.ModNetwork.CHANNEL.send(target,
                        new ru.aiforge.tubombers.network.FxPacket(p.x, p.y, p.z, 0, 6f));
                }
                c.getSource().sendSuccess(() -> Component.literal("Visual test sent: " + kind + " (no damage)"), false);
                return 1;
            })));
        }
        var client = Commands.literal("client");
        for (String effect : VisualQaPacket.EFFECTS) {
            client.then(Commands.literal(effect).then(Commands.argument("player",
                    net.minecraft.commands.arguments.EntityArgument.player()).executes(c -> {
                var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
                ru.aiforge.arsenal.network.ArsenalNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new VisualQaPacket(effect));
                c.getSource().sendSuccess(() -> Component.literal("Client QA sent: " + effect), false);
                return 1;
            })));
        }
        root.then(client);
        event.getDispatcher().register(root);
    }
}
