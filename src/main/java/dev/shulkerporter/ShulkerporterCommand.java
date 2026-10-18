package dev.shulkerporter;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /shulkerporter give | list [channel] | rescan */
final class ShulkerporterCommand {

    private final ShulkerporterPlugin plugin;

    ShulkerporterCommand(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shulkerporter")
                .requires(source -> source.getSender().hasPermission("shulkerporter.admin"))
                .then(Commands.literal("give")
                        .executes(ctx -> giveTo(ctx, ctx.getSource().getExecutor() instanceof Player p ? List.of(p) : List.of()))
                        .then(Commands.argument("targets", ArgumentTypes.players())
                                .executes(ctx -> giveTo(ctx, ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                                        .resolve(ctx.getSource())))))
                .then(Commands.literal("list")
                        .executes(this::listChannels)
                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                .executes(this::listMembers)))
                .then(Commands.literal("rescan")
                        .executes(this::rescan))
                .build();
    }

    private int giveTo(CommandContext<CommandSourceStack> ctx, List<Player> players) {
        if (players.isEmpty()) {
            ctx.getSource().getSender().sendMessage(Component.text("Name a player to give the Shulkerporter to.", NamedTextColor.RED));
            return 0;
        }
        for (Player player : players) {
            player.give(ShulkerporterItem.create(plugin.settings().glint()));
        }
        ctx.getSource().getSender().sendMessage(Component.text("Gave a Shulkerporter to " + players.size() + " player(s)."));
        return players.size();
    }

    private int listChannels(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int total = 0;
        for (World world : plugin.getServer().getWorlds()) {
            Map<String, Integer> channels = plugin.service().registry().channels(world.getUID());
            if (channels.isEmpty()) {
                continue;
            }
            sender.sendMessage(Component.text(world.getName() + ":", NamedTextColor.LIGHT_PURPLE));
            channels.forEach((channel, count) -> sender.sendMessage(Component.text("  " + channel + " (" + count + ")")));
            total += channels.size();
        }
        if (total == 0) {
            sender.sendMessage(Component.text("No named Shulkerporters are placed."));
        }
        return total;
    }

    private int listMembers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String channel = Channels.of(StringArgumentType.getString(ctx, "channel"));
        int total = 0;
        for (World world : plugin.getServer().getWorlds()) {
            Set<PortalPos> members = channel == null ? Set.of() : plugin.service().registry().members(world.getUID(), channel);
            for (PortalPos pos : members) {
                sender.sendMessage(Component.text(world.getName() + " " + pos));
                total++;
            }
        }
        if (total == 0) {
            sender.sendMessage(Component.text("No Shulkerporters on that channel."));
        }
        return total;
    }

    private int rescan(CommandContext<CommandSourceStack> ctx) {
        int chunks = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                plugin.service().reconcile(chunk);
                chunks++;
            }
        }
        ctx.getSource().getSender().sendMessage(Component.text("Rescanned " + chunks + " loaded chunks."));
        return chunks;
    }
}
