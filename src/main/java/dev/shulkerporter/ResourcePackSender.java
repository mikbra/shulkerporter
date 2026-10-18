package dev.shulkerporter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jspecify.annotations.Nullable;

/**
 * Offers the Shulkerporter resource pack to players as they join. By default this is the official
 * pack published on GitHub Pages, whose URL and SHA-1 are baked into the jar at build time. Jars
 * built without a published pack serve the bundled copy from a small built-in HTTP server instead.
 */
final class ResourcePackSender implements Listener {

    private static final String BUNDLED_PACK = "/resourcepack/shulkerporter-pack.zip";
    private static final String OFFICIAL_PACK = "/shulkerporter-pack.properties";
    static final UUID PACK_ID = UUID.nameUUIDFromBytes("shulkerporter".getBytes(StandardCharsets.UTF_8));

    private final ShulkerporterPlugin plugin;
    private volatile @Nullable ResourcePackInfo pack;
    private @Nullable HttpServer httpServer;
    private @Nullable ExecutorService httpExecutor;

    ResourcePackSender(ShulkerporterPlugin plugin) {
        this.plugin = plugin;
    }

    void load(Settings settings) {
        pack = null;
        switch (settings.packMode()) {
            case "auto" -> {
                if (!useOfficialPack()) {
                    serveBundledPack(settings);
                }
            }
            case "official" -> {
                if (!useOfficialPack()) {
                    plugin.getLogger().warning("This jar was built without a published resource pack; not sending one. "
                            + "Use resource-pack.mode auto or builtin to serve the bundled pack.");
                }
            }
            case "builtin" -> serveBundledPack(settings);
            case "url" -> useExternalUrl(settings);
            case "off" -> { }
            default -> plugin.getLogger().warning("Unknown resource-pack.mode '" + settings.packMode()
                    + "'; use auto, official, builtin, url or off. Not sending a resource pack.");
        }
    }

    /** Uses the GitHub Pages URL baked in at build time; false if this jar has none. */
    private boolean useOfficialPack() {
        Properties official = new Properties();
        try (InputStream in = getClass().getResourceAsStream(OFFICIAL_PACK)) {
            if (in != null) {
                official.load(in);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + OFFICIAL_PACK, e);
        }
        String url = official.getProperty("url", "").trim();
        String sha1 = official.getProperty("sha1", "").trim();
        if (url.isEmpty() || sha1.length() != 40) {
            return false;
        }
        pack = ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(URI.create(url)).hash(sha1).build();
        plugin.getLogger().info("Sending the official resource pack from " + url);
        return true;
    }

    private void serveBundledPack(Settings settings) {
        byte[] zip;
        try (InputStream in = getClass().getResourceAsStream(BUNDLED_PACK)) {
            if (in == null) {
                plugin.getLogger().warning("The resource pack is missing from the plugin jar; not sending it.");
                return;
            }
            zip = in.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled resource pack", e);
            return;
        }
        String sha1 = sha1(zip);
        // The hash in the path makes clients download the pack again after it changes.
        String path = "/shulkerporter-" + sha1 + ".zip";
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(settings.packPort()), 0);
            server.createContext("/", exchange -> respond(exchange, path, zip));
            httpExecutor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("Shulkerporter-pack-http").daemon().factory());
            server.setExecutor(httpExecutor);
            server.start();
            httpServer = server;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not serve the resource pack on port " + settings.packPort()
                    + "; change resource-pack.builtin.port", e);
            return;
        }
        String host = settings.packPublicAddress();
        if (host.isEmpty()) {
            host = plugin.getServer().getIp().isEmpty() ? "localhost" : plugin.getServer().getIp();
        }
        URI uri = URI.create("http://" + host + ":" + settings.packPort() + path);
        pack = ResourcePackInfo.resourcePackInfo().id(PACK_ID).uri(uri).hash(sha1).build();
        plugin.getLogger().info("Serving the resource pack at " + uri
                + (settings.packPublicAddress().isEmpty() ? " (set resource-pack.builtin.public-address for remote players)" : ""));
    }

    private static void respond(HttpExchange exchange, String path, byte[] zip) throws IOException {
        try (exchange) {
            boolean found = exchange.getRequestURI().getPath().equals(path);
            if (!found || !(exchange.getRequestMethod().equals("GET") || exchange.getRequestMethod().equals("HEAD"))) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            boolean head = exchange.getRequestMethod().equals("HEAD");
            exchange.sendResponseHeaders(200, head ? -1 : zip.length);
            if (!head) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(zip);
                }
            }
        }
    }

    private void useExternalUrl(Settings settings) {
        if (settings.packUrl().isEmpty()) {
            plugin.getLogger().warning("resource-pack.mode is url but resource-pack.url is empty; not sending a pack.");
            return;
        }
        ResourcePackInfo.Builder builder = ResourcePackInfo.resourcePackInfo()
                .id(PACK_ID)
                .uri(URI.create(settings.packUrl()));
        if (!settings.packSha1().isEmpty()) {
            pack = builder.hash(settings.packSha1()).build();
            return;
        }
        builder.computeHashAndBuild().whenComplete((info, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not download the resource pack to compute its SHA-1", error);
            } else {
                plugin.getLogger().info("Resource pack SHA-1: " + info.hash());
                pack = info;
            }
        });
    }

    void close() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
            httpExecutor = null;
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        ResourcePackInfo info = pack;
        if (info == null) {
            return;
        }
        Settings settings = plugin.settings();
        ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                .packs(info)
                .replace(false)
                .required(settings.packRequired());
        if (!settings.packPrompt().isEmpty()) {
            request.prompt(Component.text(settings.packPrompt()));
        }
        event.getPlayer().sendResourcePacks(request.build());
    }

    private static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
