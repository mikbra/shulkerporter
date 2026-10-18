package dev.shulkerporter;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jspecify.annotations.Nullable;

/** Block position of a placed Shulkerporter. */
record PortalPos(UUID world, int x, int y, int z) {

    static PortalPos of(Block block) {
        return new PortalPos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    int chunkX() {
        return x >> 4;
    }

    int chunkZ() {
        return z >> 4;
    }

    long distanceSquared(PortalPos other) {
        long dx = x - other.x;
        long dy = y - other.y;
        long dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Nullable World world(Server server) {
        return server.getWorld(world);
    }

    Location center(World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    @Override
    public String toString() {
        return x + " " + y + " " + z;
    }
}
