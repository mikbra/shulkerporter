package dev.shulkerporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Tracks redstone power on Shulkerporters and moves items between them. */
final class PortalService {

    private final ShulkerporterPlugin plugin;
    private final PortalRegistry registry;
    /** Last observed power state, for rising-edge detection. Missing means not observed yet. */
    private final Map<PortalPos, Boolean> lastPower = new HashMap<>();
    private final Map<PortalPos, Integer> lastSendTick = new HashMap<>();
    /** Sources waiting for the receiving chunk to load. */
    private final Set<PortalPos> inFlight = new HashSet<>();
    private final @Nullable PorterModels models;

    PortalService(ShulkerporterPlugin plugin, PortalRegistry registry, @Nullable PorterModels models) {
        this.plugin = plugin;
        this.registry = registry;
        this.models = models;
    }

    PortalRegistry registry() {
        return registry;
    }

    static boolean isPortalBlock(Block block) {
        return block.getType() == ShulkerporterItem.MATERIAL
                && block.getState(false) instanceof ShulkerBox box
                && box.getPersistentDataContainer().has(Keys.PORTAL);
    }

    /** Marks a freshly placed box as a Shulkerporter and indexes it under its name. */
    void markPlaced(Block block) {
        if (!(block.getState() instanceof ShulkerBox box)) {
            return;
        }
        box.getPersistentDataContainer().set(Keys.PORTAL, PersistentDataType.BOOLEAN, true);
        box.update(true, false);
        PortalPos pos = PortalPos.of(block);
        String channel = Channels.of(box.customName());
        if (channel != null) {
            registry.register(pos, channel);
        } else {
            registry.unregister(pos);
        }
        if (models != null) {
            models.ensure(block);
        }
        if (plugin.settings().sendEffects()) {
            block.getWorld().spawnParticle(Particle.REVERSE_PORTAL, pos.center(block.getWorld()), 30, 0.3, 0.3, 0.3, 0.05);
        }
    }

    void forget(Block block) {
        PortalPos pos = PortalPos.of(block);
        registry.unregister(pos);
        lastPower.remove(pos);
        lastSendTick.remove(pos);
        if (models != null) {
            models.remove(pos);
        }
    }

    /**
     * Brings the index and the models in line with the Shulkerporters actually present in a
     * loaded chunk.
     */
    void reconcile(Chunk chunk) {
        Set<PortalPos> named = new HashSet<>();
        List<Block> marked = new ArrayList<>();
        for (BlockState state : chunk.getTileEntities(block -> block.getType() == ShulkerporterItem.MATERIAL, false)) {
            if (state instanceof ShulkerBox box && box.getPersistentDataContainer().has(Keys.PORTAL)) {
                marked.add(state.getBlock());
                PortalPos pos = PortalPos.of(state.getBlock());
                String channel = Channels.of(box.customName());
                if (channel != null) {
                    registry.register(pos, channel);
                    named.add(pos);
                }
            }
        }
        UUID world = chunk.getWorld().getUID();
        for (PortalPos pos : registry.inChunk(world, chunk.getX(), chunk.getZ())) {
            if (!named.contains(pos)) {
                registry.unregister(pos);
            }
        }
        if (models != null) {
            Set<PortalPos> present = new HashSet<>();
            marked.forEach(block -> present.add(PortalPos.of(block)));
            for (PortalPos pos : models.inChunk(world, chunk.getX(), chunk.getZ())) {
                if (!present.contains(pos)) {
                    models.remove(pos);
                }
            }
            // Spawning entities while the chunk is still loading is unsafe; do it next tick.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (chunk.isLoaded()) {
                    marked.stream().filter(PortalService::isPortalBlock).forEach(models::ensure);
                }
            });
        }
    }

    void chunkUnloaded(Chunk chunk) {
        for (PortalPos pos : registry.inChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            lastPower.remove(pos);
        }
        if (models != null) {
            models.removeChunk(chunk);
        }
    }

    /**
     * Records the power state of a box and sends on a rising edge. The poller's first look at a
     * box only records it, so an already powered box doesn't fire on chunk load or startup.
     */
    void observe(PortalPos pos, Block block, boolean fromBlockUpdate) {
        boolean powered = block.isBlockIndirectlyPowered();
        Boolean previous = lastPower.put(pos, powered);
        boolean wasPowered = previous != null ? previous : !fromBlockUpdate;
        if (powered && !wasPowered) {
            trigger(pos, block);
        }
    }

    void poll() {
        for (PortalPos pos : registry.snapshot()) {
            World world = pos.world(plugin.getServer());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                continue;
            }
            observe(pos, world.getBlockAt(pos.x(), pos.y(), pos.z()), false);
        }
    }

    void ambientParticles() {
        for (PortalPos pos : registry.snapshot()) {
            World world = pos.world(plugin.getServer());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                continue;
            }
            Location center = pos.center(world);
            if (!center.getNearbyPlayers(24).isEmpty()) {
                world.spawnParticle(Particle.PORTAL, center, 6, 0.4, 0.4, 0.4, 0.2);
            }
        }
    }

    private void trigger(PortalPos source, Block block) {
        int now = Bukkit.getCurrentTick();
        Integer last = lastSendTick.get(source);
        if (last != null && now - last < plugin.settings().cooldownTicks()) {
            return;
        }
        if (inFlight.contains(source)) {
            return;
        }
        if (!isPortalBlock(block)) {
            forget(block);
            return;
        }
        String channel = registry.channelAt(source);
        if (channel == null || inventory(block).isEmpty()) {
            return;
        }
        lastSendTick.put(source, now);
        inFlight.add(source);
        dispatch(source, block.getWorld(), channel, new HashSet<>());
    }

    /** Sends to the nearest valid receiver, loading its chunk first and skipping stale entries. */
    private void dispatch(PortalPos source, World world, String channel, Set<PortalPos> tried) {
        long maxDistance = plugin.settings().maxDistance();
        Optional<PortalPos> target = registry.nearest(source, channel, tried, maxDistance * maxDistance);
        if (target.isEmpty()) {
            inFlight.remove(source);
            return;
        }
        PortalPos destination = target.get();
        world.getChunkAtAsync(destination.chunkX(), destination.chunkZ()).whenComplete((chunk, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not load the chunk of Shulkerporter at " + destination, error);
                inFlight.remove(source);
                return;
            }
            Block destinationBlock = world.getBlockAt(destination.x(), destination.y(), destination.z());
            if (!isPortalBlock(destinationBlock) || !channel.equals(channelOf(destinationBlock))) {
                // The receiver was removed or changed while its chunk was unloaded.
                reconcile(chunk);
                tried.add(destination);
                dispatch(source, world, channel, tried);
                return;
            }
            inFlight.remove(source);
            Block sourceBlock = world.getBlockAt(source.x(), source.y(), source.z());
            if (!isPortalBlock(sourceBlock)) {
                return;
            }
            int moved = transfer(inventory(sourceBlock), inventory(destinationBlock));
            if (moved > 0 && plugin.settings().sendEffects()) {
                effects(sourceBlock, destinationBlock);
            }
        });
    }

    /** Moves as much as fits; whatever doesn't fit stays in the source. Returns items moved. */
    static int transfer(Inventory from, Inventory to) {
        int moved = 0;
        for (int slot = 0; slot < from.getSize(); slot++) {
            ItemStack stack = from.getItem(slot);
            if (stack == null || stack.isEmpty() || Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                continue;
            }
            int amount = stack.getAmount();
            Map<Integer, ItemStack> leftover = to.addItem(stack.clone());
            ItemStack rest = leftover.isEmpty() ? null : leftover.values().iterator().next();
            from.setItem(slot, rest);
            moved += amount - (rest == null ? 0 : rest.getAmount());
        }
        return moved;
    }

    private static Inventory inventory(Block block) {
        return ((ShulkerBox) block.getState(false)).getInventory();
    }

    private static void effects(Block source, Block destination) {
        play(source, Particle.REVERSE_PORTAL);
        play(destination, Particle.PORTAL);
    }

    private static void play(Block block, Particle particle) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        world.spawnParticle(particle, center, 60, 0.4, 0.4, 0.4, 0.3);
        world.playSound(center, Sound.ENTITY_SHULKER_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    static @Nullable String channelOf(Block block) {
        return block.getState(false) instanceof ShulkerBox box ? Channels.of(box.customName()) : null;
    }
}
