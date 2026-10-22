package dev.shulkerporter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Applies the Shulkerpopper rules to vanilla hoppers and to dropped Shulkerpopper items. */
final class PopperListener implements Listener {

    private final Poppers poppers;

    PopperListener(Poppers poppers) {
        this.poppers = poppers;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onMove(InventoryMoveItemEvent event) {
        if (poppers.isEmpty()) {
            return;
        }
        // Only a Shulkerpopper moves its own items out; hoppers below can't pull from it.
        if (poppers.state(event.getSource()) != null && event.getInitiator() != event.getSource()) {
            event.setCancelled(true);
            return;
        }
        // Hoppers may push into a Shulkerpopper from any side, but only items its filter accepts.
        Poppers.State destination = poppers.state(event.getDestination());
        if (destination != null && !Poppers.accepts(event.getDestination(), destination.mode, event.getItem())) {
            event.setCancelled(true);
            if (event.getInitiator() == event.getSource()) {
                pushAccepted(event.getSource(), event.getDestination(), destination.mode);
            }
        }
    }

    /**
     * Paper stops a hopper's push at the first cancelled item, while a vanilla hopper would go on
     * to its next slot. So move the hopper's first accepted item instead, one per push like a
     * hopper. The rejected item's slot is restored by Paper and is never touched here.
     */
    private static void pushAccepted(Inventory hopper, Inventory popper, PopperMode mode) {
        for (int slot = 0; slot < hopper.getSize(); slot++) {
            ItemStack stack = hopper.getItem(slot);
            if (stack == null || stack.isEmpty() || !Poppers.accepts(popper, mode, stack)) {
                continue;
            }
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(hopper, stack.asOne(), popper, true);
            if (event.callEvent() && popper.addItem(event.getItem().clone()).isEmpty()) {
                stack.subtract();
                hopper.setItem(slot, stack.isEmpty() ? null : stack);
                return;
            }
        }
    }

    /** A broken Shulkerpopper drops with its current mode; bring its look and lore up to date. */
    @EventHandler(ignoreCancelled = true)
    void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (ShulkerpopperItem.is(item)) {
            ShulkerpopperItem.normalize(item);
            event.getEntity().setItemStack(item);
        }
    }
}
