package dev.shulkerporter;

import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** How a Shulkerpopper decides whether an item matches its filter. */
enum PopperMode {

    /** Same item type; enchantments, potion type, name etc. are ignored. Crafted with a repeater. */
    TYPE("type", "item type", Material.REPEATER),

    /** Same item including all its components. Crafted with a comparator. */
    EXACT("exact", "exact item", Material.COMPARATOR);

    final String id;
    final String description;
    final Material craftedWith;

    PopperMode(String id, String description, Material craftedWith) {
        this.id = id;
        this.description = description;
        this.craftedWith = craftedWith;
    }

    boolean matches(ItemStack filter, ItemStack item) {
        return this == TYPE ? filter.getType() == item.getType() : filter.isSimilar(item);
    }

    /** custom_model_data string the resource pack selects on. */
    String model() {
        return "shulkerpopper_" + id;
    }

    static @Nullable PopperMode byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (PopperMode mode : values()) {
            if (mode.id.equals(id.toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        return null;
    }
}
