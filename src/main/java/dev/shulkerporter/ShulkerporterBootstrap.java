package dev.shulkerporter;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Registers the bundled datapack. It overrides the black shulker box loot table so a broken
 * Shulkerporter drops with its marker, look and lore intact.
 */
public final class ShulkerporterBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            URL url = getClass().getResource("/datapack");
            if (url == null) {
                throw new IllegalStateException("Bundled datapack is missing from the plugin jar");
            }
            try {
                URI uri = url.toURI();
                event.registrar().discoverPack(uri, "provided");
            } catch (URISyntaxException | IOException e) {
                throw new IllegalStateException("Could not register the Shulkerporter datapack", e);
            }
        });
    }
}
