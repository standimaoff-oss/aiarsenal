/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.DoubleArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.builder.RequiredArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  com.mojang.brigadier.exceptions.CommandSyntaxException
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.commands.arguments.EntityArgument
 *  net.minecraft.commands.arguments.coordinates.Vec3Argument
 *  net.minecraft.network.chat.Component
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.level.levelgen.Heightmap$Types
 *  net.minecraft.world.phys.Vec3
 *  net.minecraftforge.event.RegisterCommandsEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package ru.aiforge.realwar.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import ru.aiforge.realwar.RWConfig;
import ru.aiforge.realwar.blast.BlastManager;
import ru.aiforge.realwar.blast.ConventionalBlast;
import ru.aiforge.realwar.blast.NuclearBlast;
import ru.aiforge.realwar.blast.RadiationManager;
import ru.aiforge.realwar.registry.BombType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="aiarsenal")
public final class RealWarCommand {
    private RealWarCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher d = e.getDispatcher();
        ru.aiforge.realwar.damage.DamageCommand.register(e.getDispatcher());
        ru.aiforge.realwar.strike.StrikeCommand.register(e.getDispatcher());
        ru.aiforge.realwar.npc.NpcCommand.register(e.getDispatcher());
        d.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"realwar").requires(s -> s.hasPermission(2))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"nuke").then(((LiteralArgumentBuilder)Commands.literal((String)"little_boy").then(((LiteralArgumentBuilder)Commands.literal((String)"air").executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.LITTLE_BOY, false, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.LITTLE_BOY, false, Vec3Argument.getVec3((CommandContext)c, (String)"pos")))))).then(((LiteralArgumentBuilder)Commands.literal((String)"surface").executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.LITTLE_BOY, true, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.LITTLE_BOY, true, Vec3Argument.getVec3((CommandContext)c, (String)"pos"))))))).then(((LiteralArgumentBuilder)Commands.literal((String)"rds_4").then(((LiteralArgumentBuilder)Commands.literal((String)"air").executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.RDS_4, false, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.RDS_4, false, Vec3Argument.getVec3((CommandContext)c, (String)"pos")))))).then(((LiteralArgumentBuilder)Commands.literal((String)"surface").executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.RDS_4, true, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nukeType((CommandContext<CommandSourceStack>)c, BombType.RDS_4, true, Vec3Argument.getVec3((CommandContext)c, (String)"pos"))))))).then(((RequiredArgumentBuilder)Commands.argument((String)"kt", (ArgumentType)DoubleArgumentType.doubleArg((double)0.01, (double)100000.0)).then(((LiteralArgumentBuilder)Commands.literal((String)"air").executes(c -> RealWarCommand.nuke((CommandContext<CommandSourceStack>)c, false, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nuke((CommandContext<CommandSourceStack>)c, false, Vec3Argument.getVec3((CommandContext)c, (String)"pos")))))).then(((LiteralArgumentBuilder)Commands.literal((String)"surface").executes(c -> RealWarCommand.nuke((CommandContext<CommandSourceStack>)c, true, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.nuke((CommandContext<CommandSourceStack>)c, true, Vec3Argument.getVec3((CommandContext)c, (String)"pos")))))))).then(Commands.literal((String)"blast").then(((RequiredArgumentBuilder)Commands.argument((String)"kg", (ArgumentType)DoubleArgumentType.doubleArg((double)0.1, (double)1000000.0)).executes(c -> RealWarCommand.blast((CommandContext<CommandSourceStack>)c, null))).then(Commands.argument((String)"pos", (ArgumentType)Vec3Argument.vec3()).executes(c -> RealWarCommand.blast((CommandContext<CommandSourceStack>)c, Vec3Argument.getVec3((CommandContext)c, (String)"pos"))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal((String)"radiation").then(Commands.literal((String)"dose").then(Commands.argument((String)"player", (ArgumentType)EntityArgument.player()).executes(RealWarCommand::doseGet)))).then(Commands.literal((String)"set").then(Commands.argument((String)"player", (ArgumentType)EntityArgument.player()).then(Commands.argument((String)"rem", (ArgumentType)DoubleArgumentType.doubleArg((double)0.0, (double)100000.0)).executes(RealWarCommand::doseSet))))).then(Commands.literal((String)"clear").executes(RealWarCommand::clearPlumes))).then(Commands.literal((String)"here").executes(RealWarCommand::rateHere)))).then(Commands.literal((String)"status").executes(RealWarCommand::status)));
    }

    private static double optimalHeightBlocks(double kt) {
        return 220.0 * Math.cbrt(kt) / (Double)RWConfig.COMMON.metersPerBlock.get();
    }

    private static int nukeType(CommandContext<CommandSourceStack> c, BombType t, boolean surface, Vec3 pos) throws CommandSyntaxException {
        double h = t.burstHeightM / (Double)RWConfig.COMMON.metersPerBlock.get();
        return RealWarCommand.fire(c, t.yieldKt, surface, pos, h);
    }

    private static int nuke(CommandContext<CommandSourceStack> c, boolean surface, Vec3 pos) throws CommandSyntaxException {
        double kt = DoubleArgumentType.getDouble(c, (String)"kt");
        return RealWarCommand.fire(c, kt, surface, pos, RealWarCommand.optimalHeightBlocks(kt));
    }

    private static int fire(CommandContext<CommandSourceStack> c, double kt, boolean surface, Vec3 pos, double heightBlocks) {
        Vec3 burst;
        Vec3 p;
        int gy;
        CommandSourceStack src = (CommandSourceStack)c.getSource();
        ServerLevel level = src.getLevel();
        Vec3 vec3 = p = pos != null ? pos : src.getPosition();
        if (surface) {
            gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(p.x), (int)Math.floor(p.z));
            burst = new Vec3(p.x, pos != null ? p.y : (double)gy + 0.5, p.z);
        } else {
            gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(p.x), (int)Math.floor(p.z));
            double y = pos != null ? p.y : (double)gy + heightBlocks;
            burst = new Vec3(p.x, Math.min(y, (double)(level.getMaxBuildHeight() - 2)), p.z);
        }
        NuclearBlast nb = NuclearBlast.start(level, burst, kt, surface);
        if (nb == null) {
            src.sendFailure((Component)Component.translatable((String)"command.aiarsenal.rw.nuke.disabled"));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.nuke.ok", (Object[])new Object[]{String.format("%.1f", kt), surface ? Component.translatable((String)"command.aiarsenal.rw.surface") : Component.translatable((String)"command.aiarsenal.rw.air"), String.format("%.0f %.0f %.0f", burst.x, burst.y, burst.z)}), true);
        return 1;
    }

    private static int blast(CommandContext<CommandSourceStack> c, Vec3 pos) {
        CommandSourceStack src = (CommandSourceStack)c.getSource();
        double kg = DoubleArgumentType.getDouble(c, (String)"kg");
        Vec3 p = pos != null ? pos : src.getPosition();
        ConventionalBlast.explode(src.getLevel(), p, kg, 1.0);
        src.sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.blast.ok", (Object[])new Object[]{String.format("%.0f", kg)}), true);
        return 1;
    }

    private static int doseGet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, (String)"player");
        double dose = RadiationManager.getDose((Player)p);
        ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.dose", (Object[])new Object[]{p.getDisplayName(), String.format("%.1f", dose)}), false);
        return (int)dose;
    }

    private static int doseSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, (String)"player");
        double rem = DoubleArgumentType.getDouble(c, (String)"rem");
        RadiationManager.setDose((Player)p, rem);
        ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.dose", (Object[])new Object[]{p.getDisplayName(), String.format("%.1f", rem)}), true);
        return 1;
    }

    private static int clearPlumes(CommandContext<CommandSourceStack> c) {
        ServerLevel level = ((CommandSourceStack)c.getSource()).getLevel();
        RadiationManager.PlumeData d = RadiationManager.data(level);
        int n = d.plumes.size();
        d.plumes.clear();
        d.setDirty();
        ((CommandSourceStack)c.getSource()).sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.plumes_cleared", (Object[])new Object[]{n}), true);
        return n;
    }

    private static int rateHere(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = (CommandSourceStack)c.getSource();
        Vec3 p = src.getPosition();
        double r = RadiationManager.sampleRate(src.getLevel(), p.x, p.z);
        src.sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.rate", (Object[])new Object[]{String.format("%.2f", r)}), false);
        return (int)r;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = (CommandSourceStack)c.getSource();
        ServerLevel level = src.getLevel();
        int active = BlastManager.active(level);
        src.sendSuccess(() -> Component.translatable((String)"command.aiarsenal.rw.status", (Object[])new Object[]{active, String.format("%.1f", (double)BlastManager.lastTickNanos / 1000000.0), BlastManager.completedBlasts, BlastManager.totalBlocksChanged, RadiationManager.data((ServerLevel)level).plumes.size()}), false);
        for (String s : BlastManager.describe(level)) {
            src.sendSuccess(() -> Component.literal((String)(" \u2022 " + s)), false);
        }
        return active;
    }
}

