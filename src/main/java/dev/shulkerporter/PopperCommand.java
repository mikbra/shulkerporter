package dev.shulkerporter;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * /shulkerpopper mode [type|exact]: shows or changes the filter mode of the Shulkerpopper you are
 * looking at, or else of the one in your main hand.
 */
final class PopperCommand {

    private static final int REACH = 5;

    private final ShulkerporterPlugin plugin;

    PopperCommand(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("shulkerpopper")
                .requires(source -> source.getSender().hasPermission("shulkerporter.popper.mode"))
                .then(Commands.literal("mode")
                        .executes(ctx -> mode(ctx, null))
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (PopperMode mode : PopperMode.values()) {
                                        builder.suggest(mode.id);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    PopperMode mode = PopperMode.byId(StringArgumentType.getString(ctx, "mode"));
                                    if (mode == null) {
                                        ctx.getSource().getSender().sendMessage(Component.text("Use type or exact.", NamedTextColor.RED));
                                        return 0;
                                    }
                                    return mode(ctx, mode);
                                })))
                .build();
    }

    private int mode(CommandContext<CommandSourceStack> ctx, @Nullable PopperMode newMode) {
        if (!(ctx.getSource().getExecutor() instanceof Player player)) {
            ctx.getSource().getSender().sendMessage(Component.text("Only players can use this.", NamedTextColor.RED));
            return 0;
        }
        Block block = player.getTargetBlockExact(REACH);
        if (block != null && Poppers.isPopperBlock(block)) {
            PopperMode current = ShulkerpopperItem.mode(((ShulkerBox) block.getState(false)).getPersistentDataContainer());
            if (newMode != null && newMode != current) {
                plugin.poppers().setMode(block, newMode);
            }
            report(player, newMode != null ? newMode : current, newMode != null);
            return 1;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (ShulkerpopperItem.is(hand)) {
            PopperMode current = ShulkerpopperItem.mode(hand.getPersistentDataContainer());
            if (newMode != null && newMode != current) {
                ShulkerpopperItem.apply(hand, newMode);
                player.getInventory().setItemInMainHand(hand);
            }
            report(player, newMode != null ? newMode : current, newMode != null);
            return 1;
        }
        player.sendMessage(Component.text("Look at a Shulkerpopper or hold one in your main hand.", NamedTextColor.RED));
        return 0;
    }

    private static void report(Player player, PopperMode mode, boolean changed) {
        String detail = mode == PopperMode.TYPE
                ? " (matches any item of the same type)"
                : " (enchantments, potion type and name must match too)";
        player.sendMessage(Component.text((changed ? "Shulkerpopper now filters by " : "Shulkerpopper filters by ")
                + mode.description + detail + ".", NamedTextColor.AQUA));
    }
}
