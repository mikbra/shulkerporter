package dev.shulkerporter;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/** Values from config.yml. */
record Settings(
        int cooldownTicks,
        int pollIntervalTicks,
        long maxDistance,
        boolean sendEffects,
        boolean ambientEffects,
        boolean glint,
        boolean placedModel,
        String packMode,
        int packPort,
        String packPublicAddress,
        String packUrl,
        String packSha1,
        boolean packRequired,
        String packPrompt) {

    static Settings from(FileConfiguration config) {
        return new Settings(
                Math.max(0, config.getInt("cooldown-ticks", 10)),
                Math.max(1, config.getInt("poll-interval-ticks", 2)),
                Math.max(0, config.getLong("max-distance", 0)),
                config.getBoolean("effects.send", true),
                config.getBoolean("effects.ambient", true),
                config.getBoolean("item.glint", true),
                config.getBoolean("placed-model", true),
                config.getString("resource-pack.mode", "auto").trim().toLowerCase(Locale.ROOT),
                config.getInt("resource-pack.builtin.port", 25580),
                config.getString("resource-pack.builtin.public-address", "").trim(),
                config.getString("resource-pack.url", "").trim(),
                config.getString("resource-pack.sha1", "").trim(),
                config.getBoolean("resource-pack.required", false),
                config.getString("resource-pack.prompt", ""));
    }
}
