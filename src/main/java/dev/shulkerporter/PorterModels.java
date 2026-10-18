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

/**
 * Draws the resource-pack model over each placed Shulkerporter: a base and a lid item display,
 * slightly larger than the vanilla box so they hide it. The lid copies the vanilla lid animation
 * when the box is opened. The displays are only shown to players who loaded the resource pack;
 * everyone else sees the plain black shulker box.
 */
final class PorterModels {

    /** Vanilla lid motion: up half a block and a -270° twist, linearly over 10 ticks. */
    private static final float LID_LIFT = 0.5f;
    private static final float LID_TWIST_DEGREES = -270f;
    private static final float STEP = 0.1f;

    private static final Key BASE_MODEL = Key.key(Keys.NAMESPACE, "porter_base");
    private static final Key LID_MODEL = Key.key(Keys.NAMESPACE, "porter_lid");

    private final ShulkerporterPlugin plugin;
    private final Map<PortalPos, Model> models = new HashMap<>();
    private final Set<Model> animating = new HashSet<>();
    /** Players whose client reported the Shulkerporter pack as loaded. */
    private final Set<UUID> viewers = new HashSet<>();

    private static final class Model {
        final ItemDisplay base;
        final ItemDisplay lid;
        final Quaternionf facing;
        float progress;
        boolean open;

        Model(ItemDisplay base, ItemDisplay lid, Quaternionf facing) {
            this.base = base;
            this.lid = lid;
            this.facing = facing;
        }

        boolean valid() {
            return base.isValid() && lid.isValid();
        }

        void remove() {
            base.remove();
            lid.remove();
        }
    }

    PorterModels(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawns the model for a Shulkerporter block, unless it already has a live one. */
    void ensure(Block block) {
        PortalPos pos = PortalPos.of(block);
        Model existing = models.get(pos);
        Quaternionf facing = facing(block);
        if (existing != null && existing.valid() && existing.facing.equals(facing)) {
            return;
        }
        remove(pos);
        Location center = pos.center(block.getWorld());
        Model model = new Model(
                spawn(center, BASE_MODEL, new Transformation(new Vector3f(), new Quaternionf(facing), new Vector3f(1), new Quaternionf())),
                spawn(center, LID_MODEL, lidTransformation(facing, 0)),
                facing);
        models.put(pos, model);
        for (UUID id : viewers) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.showEntity(plugin, model.base);
                player.showEntity(plugin, model.lid);
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
        UUID world = chunk.getWorld().getUID();
        for (PortalPos pos : List.copyOf(models.keySet())) {
            if (pos.world().equals(world) && pos.chunkX() == chunk.getX() && pos.chunkZ() == chunk.getZ()) {
                remove(pos);
            }
        }
    }

    List<PortalPos> inChunk(UUID world, int chunkX, int chunkZ) {
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
            model.lid.setInterpolationDelay(0);
            model.lid.setInterpolationDuration(1);
            model.lid.setTransformation(lidTransformation(model.facing, model.progress));
            if (model.progress == target) {
                it.remove();
            }
        }
    }

    /** Drops models whose block was removed without an event (e.g. by a piston or /setblock). */
    void prune() {
        for (Map.Entry<PortalPos, Model> entry : List.copyOf(models.entrySet())) {
            PortalPos pos = entry.getKey();
            World world = pos.world(plugin.getServer());
            if (world == null || !entry.getValue().valid()
                    || (world.isChunkLoaded(pos.chunkX(), pos.chunkZ())
                        && world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() != ShulkerporterItem.MATERIAL)) {
                remove(pos);
            }
        }
    }

    void showTo(Player player) {
        if (viewers.add(player.getUniqueId())) {
            for (Model model : models.values()) {
                player.showEntity(plugin, model.base);
                player.showEntity(plugin, model.lid);
            }
        }
    }

    void hideFrom(Player player) {
        if (viewers.remove(player.getUniqueId())) {
            for (Model model : models.values()) {
                player.hideEntity(plugin, model.base);
                player.hideEntity(plugin, model.lid);
            }
        }
    }

    void forget(Player player) {
        viewers.remove(player.getUniqueId());
    }

    private static Transformation lidTransformation(Quaternionf facing, float progress) {
        Vector3f lift = new Vector3f(0, LID_LIFT * progress, 0).rotate(facing);
        Quaternionf rotation = new Quaternionf(facing).rotateY((float) Math.toRadians(LID_TWIST_DEGREES * progress));
        return new Transformation(lift, rotation, new Vector3f(1), new Quaternionf());
    }

    /** Rotation that turns the model's up axis towards the box's facing. */
    private static Quaternionf facing(Block block) {
        BlockFace face = block.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.UP;
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
