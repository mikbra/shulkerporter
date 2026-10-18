package dev.shulkerporter;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.PlayerInventory;

/** Keeps the index current and turns block updates into sends. */
final class PortalListener implements Listener {

    private final ShulkerporterPlugin plugin;
    private final PortalService service;

    PortalListener(ShulkerporterPlugin plugin, PortalService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        if (ShulkerporterItem.is(event.getItemInHand())) {
            service.markPlaced(event.getBlockPlaced());
        }
    }

    /** Dispensers place shulker boxes without a BlockPlaceEvent; mark the box once it exists. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDispense(BlockDispenseEvent event) {
        if (!ShulkerporterItem.is(event.getItem()) || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        Block target = event.getBlock().getRelative(directional.getFacing());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (target.getType() == ShulkerporterItem.MATERIAL && !PortalService.isPortalBlock(target)) {
                service.markPlaced(target);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        service.forget(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onEntityExplode(EntityExplodeEvent event) {
        forgetAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBlockExplode(BlockExplodeEvent event) {
        forgetAll(event.blockList());
    }

    private void forgetAll(List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == ShulkerporterItem.MATERIAL) {
                service.forget(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onPhysics(BlockPhysicsEvent event) {
        if (service.registry().isEmpty()) {
            return;
        }
        Block block = event.getBlock();
        PortalPos pos = PortalPos.of(block);
        if (service.registry().contains(pos)) {
            service.observe(pos, block, true);
        }
    }

    @EventHandler
    void onChunkLoad(ChunkLoadEvent event) {
        service.reconcile(event.getChunk());
    }

    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event) {
        service.chunkUnloaded(event.getChunk());
    }

    /** Washing a black box in a cauldron would turn a Shulkerporter into a plain-coloured one. */
    @EventHandler(ignoreCancelled = true)
    void onCauldronWash(CauldronLevelChangeEvent event) {
        if (event.getReason() == CauldronLevelChangeEvent.ChangeReason.SHULKER_WASH
                && event.getEntity() instanceof Player player) {
            PlayerInventory inventory = player.getInventory();
            if (ShulkerporterItem.is(inventory.getItemInMainHand()) || ShulkerporterItem.is(inventory.getItemInOffHand())) {
                event.setCancelled(true);
            }
        }
    }
}
