package dev.shulkerporter;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

public final class ShulkerporterPlugin extends JavaPlugin {

    private Settings settings;
    private PortalService service;
    private ResourcePackSender packs;
    private @Nullable PorterModels models;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());

        PortalRegistry registry = new PortalRegistry(getDataPath().resolve("portals.yml"), getLogger());
        registry.load();
        models = settings.placedModel() ? new PorterModels(this) : null;
        service = new PortalService(this, registry, models);
        for (World world : getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                service.reconcile(chunk);
            }
        }

        packs = new ResourcePackSender(this);
        packs.load(settings);

        getServer().getPluginManager().registerEvents(new PortalListener(this, service), this);
        getServer().getPluginManager().registerEvents(new RecipeListener(this), this);
        getServer().getPluginManager().registerEvents(packs, this);
        if (models != null) {
            getServer().getPluginManager().registerEvents(new PorterModelListener(models), this);
            getServer().getScheduler().runTaskTimer(this, models::tick, 1L, 1L);
            getServer().getScheduler().runTaskTimer(this, models::prune, 20L, 20L);
        }
        registerRecipe();

        getServer().getScheduler().runTaskTimer(this, service::poll, 1L, settings.pollIntervalTicks());
        if (settings.ambientEffects()) {
            getServer().getScheduler().runTaskTimer(this, service::ambientParticles, 40L, 40L);
        }
        getServer().getScheduler().runTaskTimer(this, this::saveAsync, 600L, 600L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(new ShulkerporterCommand(this).build(), "Shulkerporter admin commands", List.of("sp")));
    }

    @Override
    public void onDisable() {
        getServer().removeRecipe(Keys.RECIPE);
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

    private void registerRecipe() {
        ShapelessRecipe recipe = new ShapelessRecipe(Keys.RECIPE, ShulkerporterItem.create(settings.glint()));
        recipe.addIngredient(ShulkerporterItem.MATERIAL);
        recipe.addIngredient(Material.BEACON);
        recipe.setCategory(CraftingBookCategory.MISC);
        getServer().addRecipe(recipe);
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
}
