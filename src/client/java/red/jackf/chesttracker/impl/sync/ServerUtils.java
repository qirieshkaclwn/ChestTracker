package red.jackf.chesttracker.impl.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public class ServerUtils {
    /**
     * Get a unique identifier for the current server or local world.
     */
    public static String getServerId() {
        Minecraft client = Minecraft.getInstance();
        if (client.getCurrentServer() != null) {
            ServerData serverData = client.getCurrentServer();
            // Use server IP/address as the ID
            return serverData.ip;
        } else if (client.isLocalServer()) {
            // For local worlds, use the level name
            return "local_" + client.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "unknown";
    }

    /**
     * Get the current dimension/world ID.
     */
    public static String getWorldId() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            return client.level.dimension().identifier().toString();
        }
        return "unknown";
    }
}
