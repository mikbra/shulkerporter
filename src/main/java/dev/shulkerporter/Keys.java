package dev.shulkerporter;

import org.bukkit.NamespacedKey;

/** Identifiers shared by the item, the placed block and the recipe. */
final class Keys {

    static final String NAMESPACE = "shulkerporter";

    /** Marker stored on the item and on the placed block entity. */
    static final NamespacedKey PORTAL = new NamespacedKey(NAMESPACE, "portal");

    static final NamespacedKey RECIPE = new NamespacedKey(NAMESPACE, "shulkerporter");

    /** custom_model_data string the resource pack selects on. */
    static final String MODEL = "shulkerporter";

    private Keys() {
    }
}
