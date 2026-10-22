package dev.shulkerporter;

import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

/** Identifiers shared by the item, the placed block and the recipe. */
final class Keys {

    static final String NAMESPACE = "shulkerporter";

    /** Marker stored on the item and on the placed block entity. */
    static final NamespacedKey PORTAL = new NamespacedKey(NAMESPACE, "portal");

    static final NamespacedKey RECIPE = new NamespacedKey(NAMESPACE, "shulkerporter");

    /** custom_model_data string the resource pack selects on. */
    static final String MODEL = "shulkerporter";

    /** Marker stored on a Shulkerpopper item and its placed block entity. */
    static final NamespacedKey POPPER = new NamespacedKey(NAMESPACE, "popper");

    /** The Shulkerpopper's filter mode, see {@link PopperMode}. */
    static final NamespacedKey POPPER_MODE = new NamespacedKey(NAMESPACE, "popper_mode");

    static NamespacedKey popperRecipe(PopperMode mode) {
        return new NamespacedKey(NAMESPACE, "shulkerpopper_" + mode.id);
    }

    static boolean isOurRecipe(@Nullable NamespacedKey key) {
        return key != null && key.getNamespace().equals(NAMESPACE);
    }

    private Keys() {
    }
}
