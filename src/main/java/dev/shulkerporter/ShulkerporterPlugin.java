package dev.shulkerporter;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class ShulkerporterPlugin extends JavaPlugin {

    private Settings settings;
    private PortalService service;
    private ResourcePackSender packs;
    private @Nullable BoxModels models;
    private Poppers poppers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());

        PortalRegistry registry = new PortalRegistry(getDataPath().resolve("portals.yml"), getLogger());
        registry.load();
        models = settings.placedModel() ? new BoxModels(this) : null;
        service = new PortalService(this, registry, models);
        poppers = new Poppers(this, models);
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                reconcile(chunk);
            }
        }

        packs = new ResourcePackSender(this);
        packs.load(settings);

        getServer().getPluginManager().registerEvents(new PortalListener(this, service), this);
        getServer().getPluginManager().registerEvents(new RecipeListener(this), this);
        getServer().getPluginManager().registerEvents(new PopperListener(poppers), this);
        getServer().getPluginManager().registerEvents(packs, this);
        if (models != null) {
            getServer().getPluginManager().registerEvents(new BoxModelListener(models), this);
            getServer().getScheduler().runTaskTimer(this, models::tick, 1L, 1L);
            getServer().getScheduler().runTaskTimer(this, models::prune, 20L, 20L);
        }
        registerRecipes();
        getServer().getScheduler().runTaskTimer(this, poppers::tick, 1L, 1L);

        getServer().getScheduler().runTaskTimer(this, service::poll, 1L, settings.pollIntervalTicks());
        if (settings.ambientEffects()) {
            getServer().getScheduler().runTaskTimer(this, service::ambientParticles, 40L, 40L);
        }
        getServer().getScheduler().runTaskTimer(this, this::saveAsync, 600L, 600L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(new ShulkerporterCommand(this).build(), "Shulkerporter admin commands", List.of("sp"));
            event.registrar().register(new PopperCommand(this).build(), "Show or change a Shulkerpopper's filter mode");
        });
    }

    @Override
    public void onDisable() {
        getServer().removeRecipe(Keys.RECIPE);
        for (PopperMode mode : PopperMode.values()) {
            getServer().removeRecipe(Keys.popperRecipe(mode));
        }
        if (packs != null) {
            packs.close();
        }
        if (models != null) {
            models.removeAll();
        }
        if (service != null) {
            String contents = service.registry().serializeIfDirty();
            if (contents != null) {
                service.registry().write(contents);
            }
        }
    }

    private void registerRecipes() {
        ShapelessRecipe porter = new ShapelessRecipe(Keys.RECIPE, ShulkerporterItem.create(settings.glint()));
        porter.addIngredient(ShulkerporterItem.MATERIAL);
        porter.addIngredient(Material.BEACON);
        porter.setCategory(CraftingBookCategory.MISC);
        getServer().addRecipe(porter);

        // Any colour of shulker box; the result keeps the colour (see RecipeListener).
        for (PopperMode mode : PopperMode.values()) {
            ShapelessRecipe popper = new ShapelessRecipe(Keys.popperRecipe(mode),
                    ShulkerpopperItem.create(Material.LIGHT_BLUE_SHULKER_BOX, mode, settings.glint()));
            popper.addIngredient(new RecipeChoice.MaterialChoice(Tag.SHULKER_BOXES));
            popper.addIngredient(Material.HOPPER);
            popper.addIngredient(mode.craftedWith);
            popper.setCategory(CraftingBookCategory.REDSTONE);
            getServer().addRecipe(popper);
        }
    }

    /** Indexes the Shulkerporters and Shulkerpoppers in a loaded chunk and syncs their models. */
    void reconcile(Chunk chunk) {
        Map<Block, BoxModels.Look> boxes = new HashMap<>(service.reconcile(chunk));
        boxes.putAll(poppers.reconcile(chunk));
        if (models != null) {
            models.sync(chunk, boxes);
        }
    }

    private void saveAsync() {
        String contents = service.registry().serializeIfDirty();
        if (contents != null) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> service.registry().write(contents));
        }
    }

    Settings settings() {
        return settings;
    }

    PortalService service() {
        return service;
    }

    Poppers poppers() {
        return poppers;
    }

    @Nullable BoxModels models() {
        return models;
    }
}
