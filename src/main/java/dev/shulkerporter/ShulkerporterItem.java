package dev.shulkerporter;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Creates and recognises the Shulkerporter item, a marked black shulker box. */
final class ShulkerporterItem {

    static final Material MATERIAL = Material.BLACK_SHULKER_BOX;

    private ShulkerporterItem() {
    }

    static ItemStack create(boolean glint) {
        ItemStack item = ItemStack.of(MATERIAL);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text("Shulkerporter", NamedTextColor.LIGHT_PURPLE));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                lore("Rename in an anvil to choose its channel."),
                lore("Power it with redstone to send its items to"),
                lore("the nearest Shulkerporter with the same name."))));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(Keys.MODEL).build());
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        item.editPersistentDataContainer(pdc -> pdc.set(Keys.PORTAL, PersistentDataType.BOOLEAN, true));
        return item;
    }

    static boolean is(@Nullable ItemStack item) {
        return item != null
                && item.getType() == MATERIAL
                && item.getPersistentDataContainer().has(Keys.PORTAL);
    }

    /**
     * True for an empty shulker box of any colour that isn't already a Shulkerporter or
     * Shulkerpopper: the only kind the recipes accept.
     */
    static boolean isEmptyBox(ItemStack item) {
        if (!Tag.SHULKER_BOXES.isTagged(item.getType()) || is(item) || ShulkerpopperItem.is(item)
                || item.hasData(DataComponentTypes.CONTAINER_LOOT)) {
            return false;
        }
        ItemContainerContents contents = item.getData(DataComponentTypes.CONTAINER);
        return contents == null || contents.contents().stream().allMatch(ItemStack::isEmpty);
    }

    private static Component lore(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
