package dev.shulkerporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Index of every placed, named Shulkerporter, so finding the receiving box never needs a world
 * scan. Saved to portals.yml so boxes in unloaded chunks are still known.
 */
final class PortalRegistry {

    private final Map<PortalPos, String> channelByPos = new HashMap<>();
    private final Map<UUID, Map<String, Set<PortalPos>>> byWorld = new HashMap<>();
    private final Path file;
    private final Logger logger;
    private volatile boolean dirty;

    PortalRegistry(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    boolean isEmpty() {
        return channelByPos.isEmpty();
    }

    boolean contains(PortalPos pos) {
        return channelByPos.containsKey(pos);
    }

    @Nullable String channelAt(PortalPos pos) {
        return channelByPos.get(pos);
    }

    void register(PortalPos pos, String channel) {
        String previous = channelByPos.put(pos, channel);
        if (channel.equals(previous)) {
            return;
        }
        if (previous != null) {
            removeFromChannel(pos, previous);
        }
        byWorld.computeIfAbsent(pos.world(), w -> new HashMap<>())
                .computeIfAbsent(channel, c -> new HashSet<>())
                .add(pos);
        dirty = true;
    }

    boolean unregister(PortalPos pos) {
        String previous = channelByPos.remove(pos);
        if (previous == null) {
            return false;
        }
        removeFromChannel(pos, previous);
        dirty = true;
        return true;
    }

    /** Nearest other box on the channel in the same world, by straight-line distance. */
    Optional<PortalPos> nearest(PortalPos from, String channel, Set<PortalPos> exclude, long maxDistanceSquared) {
        Set<PortalPos> candidates = byWorld.getOrDefault(from.world(), Map.of()).getOrDefault(channel, Set.of());
        PortalPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (PortalPos candidate : candidates) {
            if (candidate.equals(from) || exclude.contains(candidate)) {
                continue;
            }
            long distance = from.distanceSquared(candidate);
            if (distance < bestDistance && (maxDistanceSquared <= 0 || distance <= maxDistanceSquared)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    List<PortalPos> inChunk(UUID world, int chunkX, int chunkZ) {
        List<PortalPos> result = new ArrayList<>();
        for (PortalPos pos : channelByPos.keySet()) {
            if (pos.world().equals(world) && pos.chunkX() == chunkX && pos.chunkZ() == chunkZ) {
                result.add(pos);
            }
        }
        return result;
    }

    List<PortalPos> snapshot() {
        return List.copyOf(channelByPos.keySet());
    }

    /** Channel name to box count for one world, sorted by name. */
    Map<String, Integer> channels(UUID world) {
        Map<String, Integer> result = new TreeMap<>();
        byWorld.getOrDefault(world, Map.of()).forEach((channel, positions) -> result.put(channel, positions.size()));
        return result;
    }

    Set<PortalPos> members(UUID world, String channel) {
        return Collections.unmodifiableSet(byWorld.getOrDefault(world, Map.of()).getOrDefault(channel, Set.of()));
    }

    private void removeFromChannel(PortalPos pos, String channel) {
        Map<String, Set<PortalPos>> channels = byWorld.get(pos.world());
        if (channels == null) {
            return;
        }
        Set<PortalPos> members = channels.get(channel);
        if (members != null) {
            members.remove(pos);
            if (members.isEmpty()) {
                channels.remove(channel);
            }
        }
    }

    void load() {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.SEVERE, "Could not read " + file + "; starting with an empty index", e);
            return;
        }
        for (Map<?, ?> entry : yaml.getMapList("portals")) {
            try {
                PortalPos pos = new PortalPos(
                        UUID.fromString(String.valueOf(entry.get("world"))),
                        ((Number) entry.get("x")).intValue(),
                        ((Number) entry.get("y")).intValue(),
                        ((Number) entry.get("z")).intValue());
                register(pos, String.valueOf(entry.get("channel")));
            } catch (RuntimeException e) {
                logger.warning("Skipping malformed portal entry " + entry + ": " + e.getMessage());
            }
        }
        dirty = false;
    }

    /** Serialises on the calling (main) thread; returns null when nothing changed. */
    @Nullable String serializeIfDirty() {
        if (!dirty) {
            return null;
        }
        dirty = false;
        List<Map<String, Object>> entries = new ArrayList<>();
        channelByPos.forEach((pos, channel) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", pos.world().toString());
            entry.put("x", pos.x());
            entry.put("y", pos.y());
            entry.put("z", pos.z());
            entry.put("channel", channel);
            entries.add(entry);
        });
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("portals", entries);
        return yaml.saveToString();
    }

    void write(String contents) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, contents);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save " + file, e);
            dirty = true;
        }
    }
}
