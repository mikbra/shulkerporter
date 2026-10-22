package dev.shulkerporter;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /shulkerporter give (porter | popper type|exact) [players] | list [channel] | rescan */
final class ShulkerporterCommand {

    private final ShulkerporterPlugin plugin;

    ShulkerporterCommand(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shulkerporter")
                .requires(source -> source.getSender().hasPermission("shulkerporter.admin"))
                .then(Commands.literal("give")
                        .then(giveCommand("porter", () -> ShulkerporterItem.create(plugin.settings().glint())))
                        .then(Commands.literal("popper")
                                .then(giveCommand("type", () -> popper(PopperMode.TYPE)))
                                .then(giveCommand("exact", () -> popper(PopperMode.EXACT)))))
                .then(Commands.literal("list")
                        .executes(this::listChannels)
                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                .executes(this::listMembers)))
                .then(Commands.literal("rescan")
                        .executes(this::rescan))
                .build();
    }

    private ItemStack popper(PopperMode mode) {
        return ShulkerpopperItem.create(Material.LIGHT_BLUE_SHULKER_BOX, mode, plugin.settings().glint());
    }

    private LiteralArgumentBuilder<CommandSourceStack> giveCommand(String name, Supplier<ItemStack> item) {
        return Commands.literal(name)
                .executes(ctx -> giveTo(ctx, ctx.getSource().getExecutor() instanceof Player p ? List.of(p) : List.of(), item))
                .then(Commands.argument("targets", ArgumentTypes.players())
                        .executes(ctx -> giveTo(ctx, ctx.getArgument("targets", PlayerSelectorArgumentResolver.class)
                                .resolve(ctx.getSource()), item)));
    }

    private int giveTo(CommandContext<CommandSourceStack> ctx, List<Player> players, Supplier<ItemStack> item) {
        if (players.isEmpty()) {
            ctx.getSource().getSender().sendMessage(Component.text("Name a player to give it to.", NamedTextColor.RED));
            return 0;
        }
        for (Player player : players) {
            player.give(item.get());
        }
        ctx.getSource().getSender().sendMessage(Component.text("Gave it to " + players.size() + " player(s)."));
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
                plugin.reconcile(chunk);
                chunks++;
            }
        }
        ctx.getSource().getSender().sendMessage(Component.text("Rescanned " + chunks + " loaded chunks."));
        return chunks;
    }
}
