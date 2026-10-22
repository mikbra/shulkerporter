package dev.shulkerporter;

import org.bukkit.GameMode;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/** Opens and closes the model lid with the box, and shows models to players with the pack. */
final class BoxModelListener implements Listener {

    private final BoxModels models;

    BoxModelListener(BoxModels models) {
        this.models = models;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onOpen(InventoryOpenEvent event) {
        // Spectators open boxes without moving the lid.
        if (event.getPlayer().getGameMode() != GameMode.SPECTATOR
                && event.getInventory().getHolder(false) instanceof ShulkerBox box) {
            models.setOpen(PortalPos.of(box.getBlock()), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof ShulkerBox box)) {
            return;
        }
        for (HumanEntity viewer : event.getInventory().getViewers()) {
            if (viewer != event.getPlayer() && viewer.getGameMode() != GameMode.SPECTATOR) {
                return;
            }
        }
        models.setOpen(PortalPos.of(box.getBlock()), false);
    }

    @EventHandler
    void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(ResourcePackSender.PACK_ID)) {
            return;
        }
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> models.showTo(event.getPlayer());
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> models.hideFrom(event.getPlayer());
            default -> { }
        }
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        models.forget(event.getPlayer());
    }
}
