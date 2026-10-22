package dev.shulkerporter;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws a resource-pack model over each placed Shulkerporter and Shulkerpopper: a base and a lid
 * item display, slightly larger than the vanilla box so they hide it, plus an optional intake
 * plate on the box's top face. The lid copies the vanilla lid animation when the box is opened.
 * The displays are only shown to players who loaded the resource pack; everyone else sees the
 * plain shulker box.
 */
final class BoxModels {

    /** Which models to draw for a box. The intake plate, if any, always sits on the world-up face. */
    record Look(Key base, Key lid, @Nullable Key intake) {

        static final Look PORTER = new Look(model("porter_base"), model("porter_lid"), null);

        static Key model(String name) {
            return Key.key(Keys.NAMESPACE, name);
        }
    }

    /** Vanilla lid motion: up half a block and a -270° twist, linearly over 10 ticks. */
    private static final float LID_LIFT = 0.5f;
    private static final float LID_TWIST_DEGREES = -270f;
    private static final float STEP = 0.1f;

    private final ShulkerporterPlugin plugin;
    private final Map<PortalPos, Model> models = new HashMap<>();
    private final Set<Model> animating = new HashSet<>();
    /** Players whose client reported the Shulkerporter pack as loaded. */
    private final Set<UUID> viewers = new HashSet<>();

    private static final class Model {
        final Look look;
        final BlockFace facing;
        final Quaternionf rotation;
        final List<ItemDisplay> displays = new ArrayList<>();
        final ItemDisplay lid;
        final @Nullable ItemDisplay intake;
        float progress;
        boolean open;

        Model(Look look, BlockFace facing, Quaternionf rotation, ItemDisplay base, ItemDisplay lid, @Nullable ItemDisplay intake) {
            this.look = look;
            this.facing = facing;
            this.rotation = rotation;
            this.lid = lid;
            this.intake = intake;
            displays.add(base);
            displays.add(lid);
            if (intake != null) {
                displays.add(intake);
            }
        }

        boolean valid() {
            return displays.stream().allMatch(ItemDisplay::isValid);
        }

        void remove() {
            displays.forEach(ItemDisplay::remove);
        }
    }

    BoxModels(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawns the model for a box, unless it already has a live one with the same look and facing. */
    void ensure(Block block, Look look) {
        PortalPos pos = PortalPos.of(block);
        BlockFace facing = block.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.UP;
        Model existing = models.get(pos);
        if (existing != null && existing.valid() && existing.look.equals(look) && existing.facing == facing) {
            return;
        }
        remove(pos);
        Location center = pos.center(block.getWorld());
        Quaternionf rotation = rotation(facing);
        // No intake when the base (the output side) faces up: then the top face is the output.
        ItemDisplay intake = look.intake() != null && facing != BlockFace.DOWN
                ? spawn(center, look.intake(), identity())
                : null;
        Model model = new Model(look, facing, rotation,
                spawn(center, look.base(), new Transformation(new Vector3f(), new Quaternionf(rotation), new Vector3f(1), new Quaternionf())),
                spawn(center, look.lid(), lidTransformation(rotation, 0)),
                intake);
        models.put(pos, model);
        for (UUID id : viewers) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                model.displays.forEach(display -> player.showEntity(plugin, display));
            }
        }
    }

    private ItemDisplay spawn(Location location, Key model, Transformation transformation) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            ItemStack item = ItemStack.of(Material.PAPER);
            item.setData(DataComponentTypes.ITEM_MODEL, model);
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(transformation);
            display.setPersistent(false);
            display.setVisibleByDefault(false);
            display.setShadowRadius(0);
            display.setBillboard(Display.Billboard.FIXED);
        });
    }

    void remove(PortalPos pos) {
        Model model = models.remove(pos);
        if (model != null) {
            animating.remove(model);
            model.remove();
        }
    }

    /** Removes the models in a chunk that is unloading; they are recreated when it loads again. */
    void removeChunk(Chunk chunk) {
        for (PortalPos pos : inChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            remove(pos);
        }
    }

    /**
     * Makes a loaded chunk's models match the boxes found in it. Spawning entities while a chunk
     * is still loading is unsafe, so new models are created on the next tick.
     */
    void sync(Chunk chunk, Map<Block, Look> present) {
        Set<PortalPos> keep = new HashSet<>();
        present.keySet().forEach(block -> keep.add(PortalPos.of(block)));
        for (PortalPos pos : inChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            if (!keep.contains(pos)) {
                remove(pos);
            }
        }
        if (present.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) {
                present.forEach((block, look) -> {
                    if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
                        ensure(block, look);
                    }
                });
            }
        });
    }

    private List<PortalPos> inChunk(UUID world, int chunkX, int chunkZ) {
        List<PortalPos> result = new ArrayList<>();
        for (PortalPos pos : models.keySet()) {
            if (pos.world().equals(world) && pos.chunkX() == chunkX && pos.chunkZ() == chunkZ) {
                result.add(pos);
            }
        }
        return result;
    }

    void removeAll() {
        models.values().forEach(Model::remove);
        models.clear();
        animating.clear();
    }

    void setOpen(PortalPos pos, boolean open) {
        Model model = models.get(pos);
        if (model != null && model.open != open) {
            model.open = open;
            animating.add(model);
        }
    }

    /** Advances lid animations; runs every tick. */
    void tick() {
        for (Iterator<Model> it = animating.iterator(); it.hasNext(); ) {
            Model model = it.next();
            if (!model.valid()) {
                it.remove();
                continue;
            }
            float target = model.open ? 1f : 0f;
            model.progress = model.open
                    ? Math.min(target, model.progress + STEP)
                    : Math.max(target, model.progress - STEP);
            Transformation lid = lidTransformation(model.rotation, model.progress);
            animate(model.lid, lid);
            if (model.intake != null) {
                // On an upward box the intake plate lies on the lid and moves with it; on a sideways
                // box it would float over the opening, so it shrinks away instead.
                animate(model.intake, model.facing == BlockFace.UP
                        ? lid
                        : new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1 - model.progress), new Quaternionf()));
            }
            if (model.progress == target) {
                it.remove();
            }
        }
    }

    private static void animate(ItemDisplay display, Transformation transformation) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTransformation(transformation);
    }

    /** Drops models whose block was removed without an event (e.g. by a piston or /setblock). */
    void prune() {
        for (Map.Entry<PortalPos, Model> entry : List.copyOf(models.entrySet())) {
            PortalPos pos = entry.getKey();
            World world = pos.world(plugin.getServer());
            if (world == null || !entry.getValue().valid()
                    || (world.isChunkLoaded(pos.chunkX(), pos.chunkZ())
                        && !Tag.SHULKER_BOXES.isTagged(world.getBlockAt(pos.x(), pos.y(), pos.z()).getType()))) {
                remove(pos);
            }
        }
    }

    void showTo(Player player) {
        if (viewers.add(player.getUniqueId())) {
            for (Model model : models.values()) {
                model.displays.forEach(display -> player.showEntity(plugin, display));
            }
        }
    }

    void hideFrom(Player player) {
        if (viewers.remove(player.getUniqueId())) {
            for (Model model : models.values()) {
                model.displays.forEach(display -> player.hideEntity(plugin, display));
            }
        }
    }

    void forget(Player player) {
        viewers.remove(player.getUniqueId());
    }

    private static Transformation identity() {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1), new Quaternionf());
    }

    private static Transformation lidTransformation(Quaternionf facing, float progress) {
        Vector3f lift = new Vector3f(0, LID_LIFT * progress, 0).rotate(facing);
        Quaternionf rotation = new Quaternionf(facing).rotateY((float) Math.toRadians(LID_TWIST_DEGREES * progress));
        return new Transformation(lift, rotation, new Vector3f(1), new Quaternionf());
    }

    /** Rotation that turns the model's up axis towards the box's facing. */
    private static Quaternionf rotation(BlockFace face) {
        float quarter = (float) (Math.PI / 2);
        return switch (face) {
            case DOWN -> new Quaternionf().rotationX((float) Math.PI);
            case NORTH -> new Quaternionf().rotationX(-quarter);
            case SOUTH -> new Quaternionf().rotationX(quarter);
            case EAST -> new Quaternionf().rotationZ(-quarter);
            case WEST -> new Quaternionf().rotationZ(quarter);
            default -> new Quaternionf();
        };
    }
}
