package dev.shulkerporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * Runs the Shulkerpoppers in loaded chunks: shulker boxes that work like a filtering hopper.
 *
 * <ul>
 *   <li>The item types inside are the filter. It only collects matching items: from the container
 *       above, or dropped items lying on top. Hoppers may also push matching items in from any side.
 *   <li>It passes items on through its base (the side placed against a block), always keeping one
 *       of each type. A hopper or Shulkerpopper underneath takes precedence while it has room.
 *       Nothing else may pull items out of it.
 *   <li>Like a hopper it moves one item every 8 ticks and is paused by a redstone signal.
 * </ul>
 */
final class Poppers {

    private static final int TRANSFER_COOLDOWN = 8;

    private final ShulkerporterPlugin plugin;
    private final @Nullable BoxModels models;
    private final Map<PortalPos, State> loaded = new HashMap<>();

    static final class State {
        PopperMode mode;
        int cooldown;

        State(PopperMode mode) {
            this.mode = mode;
        }
    }

    Poppers(ShulkerporterPlugin plugin, @Nullable BoxModels models) {
        this.plugin = plugin;
        this.models = models;
    }

    static BoxModels.Look look(PopperMode mode) {
        return new BoxModels.Look(
                BoxModels.Look.model("popper_base"),
                BoxModels.Look.model("popper_lid_" + mode.id),
                BoxModels.Look.model("popper_intake_" + mode.id));
    }

    static boolean isPopperBlock(Block block) {
        return Tag.SHULKER_BOXES.isTagged(block.getType())
                && block.getState(false) instanceof ShulkerBox box
                && box.getPersistentDataContainer().has(Keys.POPPER);
    }

    boolean isEmpty() {
        return loaded.isEmpty();
    }

    @Nullable State state(PortalPos pos) {
        return loaded.get(pos);
    }

    /** The Shulkerpopper an inventory belongs to, if it is one. */
    @Nullable State state(Inventory inventory) {
        if (loaded.isEmpty() || !(inventory.getHolder(false) instanceof ShulkerBox)) {
            return null;
        }
        Location location = inventory.getLocation();
        return location == null ? null : loaded.get(PortalPos.of(location.getBlock()));
    }

    /** Marks a freshly placed box as a Shulkerpopper with the given mode. */
    void markPlaced(Block block, PopperMode mode) {
        if (!(block.getState() instanceof ShulkerBox box)) {
            return;
        }
        box.getPersistentDataContainer().set(Keys.POPPER, PersistentDataType.BOOLEAN, true);
        box.getPersistentDataContainer().set(Keys.POPPER_MODE, PersistentDataType.STRING, mode.id);
        box.update(true, false);
        loaded.put(PortalPos.of(block), new State(mode));
        if (models != null) {
            models.ensure(block, look(mode));
        }
    }

    void setMode(Block block, PopperMode mode) {
        if (!(block.getState() instanceof ShulkerBox box)) {
            return;
        }
        box.getPersistentDataContainer().set(Keys.POPPER_MODE, PersistentDataType.STRING, mode.id);
        box.update(true, false);
        State state = loaded.computeIfAbsent(PortalPos.of(block), pos -> new State(mode));
        state.mode = mode;
        if (models != null) {
            models.ensure(block, look(mode));
        }
    }

    void forget(Block block) {
        loaded.remove(PortalPos.of(block));
    }

    /** Starts tracking the Shulkerpoppers in a loaded chunk; returns them with their look. */
    Map<Block, BoxModels.Look> reconcile(Chunk chunk) {
        Map<Block, BoxModels.Look> found = new HashMap<>();
        for (BlockState state : chunk.getTileEntities(block -> Tag.SHULKER_BOXES.isTagged(block.getType()), false)) {
            if (state instanceof ShulkerBox box && box.getPersistentDataContainer().has(Keys.POPPER)) {
                PopperMode mode = ShulkerpopperItem.mode(box.getPersistentDataContainer());
                PortalPos pos = PortalPos.of(state.getBlock());
                loaded.computeIfAbsent(pos, p -> new State(mode)).mode = mode;
                found.put(state.getBlock(), look(mode));
            }
        }
        return found;
    }

    void chunkUnloaded(Chunk chunk) {
        loaded.keySet().removeIf(pos -> pos.world().equals(chunk.getWorld().getUID())
                && pos.chunkX() == chunk.getX() && pos.chunkZ() == chunk.getZ());
    }

    void tick() {
        List<PortalPos> gone = new ArrayList<>();
        for (Map.Entry<PortalPos, State> entry : loaded.entrySet()) {
            State state = entry.getValue();
            if (state.cooldown > 0) {
                state.cooldown--;
                continue;
            }
            PortalPos pos = entry.getKey();
            World world = pos.world(plugin.getServer());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
                continue;
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (!(block.getState(false) instanceof ShulkerBox box) || !box.getPersistentDataContainer().has(Keys.POPPER)) {
                gone.add(pos);
                continue;
            }
            if (block.isBlockIndirectlyPowered()) {
                continue;
            }
            Inventory inventory = box.getInventory();
            BlockFace output = block.getBlockData() instanceof Directional directional
                    ? directional.getFacing().getOppositeFace()
                    : BlockFace.DOWN;
            // A hopper (or Shulkerpopper) underneath takes precedence over the output while it has room.
            boolean moved = (output != BlockFace.DOWN && feedsBelow(block) && push(block, inventory, state.mode, BlockFace.DOWN))
                    || push(block, inventory, state.mode, output);
            moved |= output != BlockFace.UP && pull(block, inventory, state.mode);
            if (moved) {
                state.cooldown = TRANSFER_COOLDOWN - 1;
            }
        }
        gone.forEach(loaded::remove);
    }

    /** True if the filter has an item matching this one; shulker boxes never match. */
    static boolean accepts(Inventory popper, PopperMode mode, ItemStack item) {
        if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
            return false;
        }
        for (ItemStack filter : popper.getStorageContents()) {
            if (filter != null && !filter.isEmpty() && mode.matches(filter, item)) {
                return true;
            }
        }
        return false;
    }

    /** How many matching items the popper holds, across all slots. */
    private static int count(Inventory popper, PopperMode mode, ItemStack item) {
        int count = 0;
        for (ItemStack other : popper.getStorageContents()) {
            if (other != null && !other.isEmpty() && mode.matches(item, other)) {
                count += other.getAmount();
            }
        }
        return count;
    }

    private static boolean feedsBelow(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return below.getState(false) instanceof Hopper || Poppers.isPopperBlock(below);
    }

    /** Passes one item into the block on the given side, keeping one of each type behind. */
    private boolean push(Block block, Inventory inventory, PopperMode mode, BlockFace output) {
        Block target = block.getRelative(output);
        Inventory destination = inventoryAt(target);
        if (destination == null) {
            return false;
        }
        State targetPopper = state(destination);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.isEmpty() || count(inventory, mode, stack) <= 1) {
                continue;
            }
            if (targetPopper != null && !accepts(destination, targetPopper.mode, stack)) {
                continue;
            }
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inventory, stack.asOne(), destination, true);
            if (!event.callEvent()) {
                continue;
            }
            if (insert(destination, event.getItem().clone(), output)) {
                stack.subtract();
                inventory.setItem(slot, stack.isEmpty() ? null : stack);
                return true;
            }
        }
        return false;
    }

    /** Collects one matching item from the container above, or matching dropped items on top. */
    private boolean pull(Block block, Inventory inventory, PopperMode mode) {
        Block above = block.getRelative(BlockFace.UP);
        Inventory source = inventoryAt(above);
        if (source != null) {
            if (state(source) != null) {
                // A Shulkerpopper above pushes into this one itself; nothing pulls from a popper.
                return false;
            }
            int[] slots = source instanceof FurnaceInventory ? new int[] {2} : allSlots(source);
            for (int slot : slots) {
                ItemStack stack = source.getItem(slot);
                if (stack == null || stack.isEmpty() || !accepts(inventory, mode, stack)) {
                    continue;
                }
                InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, stack.asOne(), inventory, false);
                if (!event.callEvent() || !inventory.addItem(event.getItem().clone()).isEmpty()) {
                    continue;
                }
                stack.subtract();
                source.setItem(slot, stack.isEmpty() ? null : stack);
                return true;
            }
            return false;
        }
        if (above.getType().isOccluding()) {
            return false;
        }
        BoundingBox area = new BoundingBox(
                above.getX(), above.getY(), above.getZ(), above.getX() + 1, above.getY() + 0.75, above.getZ() + 1);
        for (var entity : block.getWorld().getNearbyEntities(area, e -> e instanceof Item)) {
            Item item = (Item) entity;
            ItemStack stack = item.getItemStack();
            if (!item.isValid() || !accepts(inventory, mode, stack)) {
                continue;
            }
            if (!new InventoryPickupItemEvent(inventory, item).callEvent()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = inventory.addItem(stack.clone());
            int left = leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
            if (left == stack.getAmount()) {
                continue;
            }
            if (left == 0) {
                item.remove();
            } else {
                stack.setAmount(left);
                item.setItemStack(stack);
            }
            return true;
        }
        return false;
    }

    /** Inserts a single item, honouring furnace sides like a hopper does. */
    private static boolean insert(Inventory destination, ItemStack item, BlockFace direction) {
        if (destination instanceof FurnaceInventory furnace) {
            // From above into the input slot; from the side into the fuel slot, fuel only.
            int slot = direction == BlockFace.DOWN ? 0 : 1;
            if (slot == 1 && !furnace.isFuel(item)) {
                return false;
            }
            ItemStack current = furnace.getItem(slot);
            if (current == null || current.isEmpty()) {
                furnace.setItem(slot, item);
                return true;
            }
            if (current.isSimilar(item) && current.getAmount() < current.getMaxStackSize()) {
                current.add();
                furnace.setItem(slot, current);
                return true;
            }
            return false;
        }
        if (destination.getHolder(false) instanceof ShulkerBox && Tag.SHULKER_BOXES.isTagged(item.getType())) {
            return false;
        }
        return destination.addItem(item).isEmpty();
    }

    /** The containers a Shulkerpopper exchanges items with, like a hopper. */
    private static @Nullable Inventory inventoryAt(Block block) {
        return switch (block.getState(false)) {
            case Chest chest -> chest.getInventory();
            case Barrel barrel -> barrel.getInventory();
            case Hopper hopper -> hopper.getInventory();
            case ShulkerBox box -> box.getInventory();
            case Dispenser dispenser -> dispenser.getInventory();
            case Dropper dropper -> dropper.getInventory();
            case Furnace furnace -> furnace.getInventory();
            default -> null;
        };
    }

    private static int[] allSlots(Inventory inventory) {
        int[] slots = new int[inventory.getSize()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }
}
