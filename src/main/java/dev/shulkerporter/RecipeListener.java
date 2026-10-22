package dev.shulkerporter;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.jspecify.annotations.Nullable;

/**
 * Builds the crafting results: only an empty, unmarked shulker box may become a Shulkerporter
 * (black) or a Shulkerpopper (any colour, keeping the colour). A Shulkerporter can't be used in
 * any other recipe, e.g. dyeing it into another colour.
 */
final class RecipeListener implements Listener {

    private final ShulkerporterPlugin plugin;

    RecipeListener(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    void onPrepare(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        NamespacedKey key = key(event.getRecipe());
        if (Keys.isOurRecipe(key)) {
            String permission = key.equals(Keys.RECIPE) ? "shulkerporter.craft" : "shulkerporter.popper.craft";
            boolean allowed = event.getView().getPlayer().hasPermission(permission);
            event.getInventory().setResult(allowed ? result(key, matrix) : null);
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
        NamespacedKey key = key(event.getRecipe());
        if (Keys.isOurRecipe(key)) {
            ItemStack result = result(key, matrix);
            if (result == null) {
                event.setCancelled(true);
            } else {
                event.setResult(result);
            }
        } else if (containsShulkerporter(matrix)) {
            event.setCancelled(true);
        }
    }

    private static @Nullable NamespacedKey key(@Nullable Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey() : null;
    }

    /** The item to craft, keeping the box's colour and name, or null if the box isn't empty. */
    private @Nullable ItemStack result(NamespacedKey key, ItemStack[] matrix) {
        ItemStack box = null;
        for (ItemStack item : matrix) {
            if (item != null && Tag.SHULKER_BOXES.isTagged(item.getType())) {
                box = item;
            }
        }
        if (box == null || !ShulkerporterItem.isEmptyBox(box)) {
            return null;
        }
        ItemStack result;
        if (key.equals(Keys.RECIPE)) {
            if (box.getType() != ShulkerporterItem.MATERIAL) {
                return null;
            }
            result = ShulkerporterItem.create(plugin.settings().glint());
        } else {
            PopperMode mode = key.equals(Keys.popperRecipe(PopperMode.EXACT)) ? PopperMode.EXACT : PopperMode.TYPE;
            result = ShulkerpopperItem.create(box.getType(), mode, plugin.settings().glint());
        }
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
