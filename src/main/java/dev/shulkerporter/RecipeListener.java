package dev.shulkerporter;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * Only an empty black shulker box may become a Shulkerporter, and a Shulkerporter can't be used
 * in any other recipe (e.g. dyeing it into another colour).
 */
final class RecipeListener implements Listener {

    private final ShulkerporterPlugin plugin;

    RecipeListener(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    void onPrepare(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        if (isOurs(event.getRecipe())) {
            boolean allowed = event.getView().getPlayer().hasPermission("shulkerporter.craft");
            event.getInventory().setResult(allowed ? result(matrix) : null);
        } else if (containsShulkerporter(matrix)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onCrafter(CrafterCraftEvent event) {
        if (!(event.getBlock().getState() instanceof Crafter crafter)) {
            return;
        }
        ItemStack[] matrix = crafter.getInventory().getContents();
        if (isOurs(event.getRecipe())) {
            ItemStack result = result(matrix);
            if (result == null) {
                event.setCancelled(true);
            } else {
                event.setResult(result);
            }
        } else if (containsShulkerporter(matrix)) {
            event.setCancelled(true);
        }
    }

    private static boolean isOurs(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().equals(Keys.RECIPE);
    }

    /** The Shulkerporter to craft, keeping the box's name, or null if the box isn't empty. */
    private @Nullable ItemStack result(ItemStack[] matrix) {
        ItemStack box = null;
        for (ItemStack item : matrix) {
            if (item != null && item.getType() == ShulkerporterItem.MATERIAL) {
                box = item;
            }
        }
        if (box == null || !ShulkerporterItem.isEmptyBox(box)) {
            return null;
        }
        ItemStack result = ShulkerporterItem.create(plugin.settings().glint());
        Component name = box.getData(DataComponentTypes.CUSTOM_NAME);
        if (name != null) {
            result.setData(DataComponentTypes.CUSTOM_NAME, name);
        }
        return result;
    }

    private static boolean containsShulkerporter(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (ShulkerporterItem.is(item)) {
                return true;
            }
        }
        return false;
    }
}
