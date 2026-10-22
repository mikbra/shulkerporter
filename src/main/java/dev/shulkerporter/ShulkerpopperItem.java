package dev.shulkerporter;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.persistence.PersistentDataContainerView;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/**
 * Creates and recognises the Shulkerpopper item: a marked shulker box of any colour that works
 * like a filtering hopper.
 */
final class ShulkerpopperItem {

    private ShulkerpopperItem() {
    }

    static ItemStack create(Material box, PopperMode mode, boolean glint) {
        ItemStack item = ItemStack.of(box);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Shulkerpopper", NamedTextColor.AQUA));
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        apply(item, mode);
        return item;
    }

    /** Sets the mode and everything derived from it: marker, look and lore. */
    static void apply(ItemStack item, PopperMode mode) {
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.POPPER, PersistentDataType.BOOLEAN, true);
            pdc.set(Keys.POPPER_MODE, PersistentDataType.STRING, mode.id);
        });
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(mode.model()).build());
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                lore("Filter: " + mode.description, NamedTextColor.AQUA),
                lore("Put one of each item it should collect inside.", NamedTextColor.GRAY),
                lore("Collects from above, passes items on through", NamedTextColor.GRAY),
                lore("its base and always keeps one of each.", NamedTextColor.GRAY))));
    }

    /**
     * Makes the look and lore match the stored mode. A broken box gets its marker from the block,
     * where /shulkerpopper mode may have changed it, but its look and lore from the original item.
     */
    static void normalize(ItemStack item) {
        PopperMode mode = mode(item);
        CustomModelData model = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (mode != null && (model == null || !model.strings().equals(List.of(mode.model())))) {
            apply(item, mode);
        }
    }

    static boolean is(@Nullable ItemStack item) {
        return item != null
                && Tag.SHULKER_BOXES.isTagged(item.getType())
                && item.getPersistentDataContainer().has(Keys.POPPER);
    }

    static @Nullable PopperMode mode(ItemStack item) {
        return is(item) ? mode(item.getPersistentDataContainer()) : null;
    }

    static PopperMode mode(PersistentDataContainerView pdc) {
        PopperMode mode = PopperMode.byId(pdc.get(Keys.POPPER_MODE, PersistentDataType.STRING));
        return mode != null ? mode : PopperMode.TYPE;
    }

    private static Component lore(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
