package dev.shulkerporter;

import java.util.List;
import org.bukkit.Tag;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Keeps the indexes current and turns block updates into sends. */
final class PortalListener implements Listener {

    private final ShulkerporterPlugin plugin;
    private final PortalService service;

    PortalListener(ShulkerporterPlugin plugin, PortalService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlace(BlockPlaceEvent event) {
        markPlaced(event.getItemInHand(), event.getBlockPlaced());
    }

    private void markPlaced(ItemStack item, Block block) {
        if (ShulkerporterItem.is(item)) {
            service.markPlaced(block);
        } else if (ShulkerpopperItem.is(item)) {
            plugin.poppers().markPlaced(block, ShulkerpopperItem.mode(item.getPersistentDataContainer()));
        }
    }

    /** Dispensers place shulker boxes without a BlockPlaceEvent; mark the box once it exists. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem().clone();
        if (!(ShulkerporterItem.is(item) || ShulkerpopperItem.is(item))
                || !(event.getBlock().getBlockData() instanceof Directional directional)) {
            return;
        }
        Block target = event.getBlock().getRelative(directional.getFacing());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (target.getType() == item.getType() && !PortalService.isPortalBlock(target) && !Poppers.isPopperBlock(target)) {
                markPlaced(item, target);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        forget(event.getBlock());
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
            if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
                forget(block);
            }
        }
    }

    private void forget(Block block) {
        service.forget(block);
        plugin.poppers().forget(block);
        if (plugin.models() != null) {
            plugin.models().remove(PortalPos.of(block));
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
        plugin.reconcile(event.getChunk());
    }

    @EventHandler
    void onChunkUnload(ChunkUnloadEvent event) {
        service.chunkUnloaded(event.getChunk());
        plugin.poppers().chunkUnloaded(event.getChunk());
        if (plugin.models() != null) {
            plugin.models().removeChunk(event.getChunk());
        }
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
