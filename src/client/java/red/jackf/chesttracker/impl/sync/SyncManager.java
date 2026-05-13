package red.jackf.chesttracker.impl.sync;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.impl.util.ModCodecs;
import red.jackf.chesttracker.impl.config.ChestTrackerConfig;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class SyncManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new Gson();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private ChestTrackerConfig.Sync getConfig() {
        return ChestTrackerConfig.INSTANCE.instance().sync;
    }

    public void sendChestUpdate(String serverId, String worldId, Identifier keyId, BlockPos pos, Memory memory) {
        sendChestUpdate(serverId, worldId, keyId, pos, memory, null);
    }

    public void sendChestUpdate(String serverId, String worldId, Identifier keyId, BlockPos pos, Memory memory, @Nullable Memory oldMemory) {
        var config = getConfig();
        if (!config.enabled || config.apiUrl == null || config.apiUrl.isEmpty()) return;
        if (serverId == null) return;

        JsonObject payload = new JsonObject();
        payload.addProperty("server_id", serverId);
        payload.addProperty("world_id", worldId);
        payload.addProperty("key_id", keyId.toString());
        payload.addProperty("pos_x", pos.getX());
        payload.addProperty("pos_y", pos.getY());
        payload.addProperty("pos_z", pos.getZ());
        payload.addProperty("updated_by", Minecraft.getInstance().getUser().getProfileId().toString());

        if (oldMemory != null && oldMemory.fullItems().size() == memory.fullItems().size()) {
            JsonObject deltas = new JsonObject();
            List<ItemStack> oldItems = oldMemory.fullItems();
            List<ItemStack> newItems = memory.fullItems();
            boolean anyChanged = false;

            for (int i = 0; i < newItems.size(); i++) {
                if (!ItemStack.matches(oldItems.get(i), newItems.get(i))) {
                    deltas.add(String.valueOf(i), ModCodecs.OPTIONAL_ITEMSTACK_UNCAPPED_SIZE.encodeStart(JsonOps.INSTANCE, newItems.get(i)).getOrThrow());
                    anyChanged = true;
                }
            }
            
            if (!anyChanged && Objects.equals(oldMemory.savedName(), memory.savedName())) return;
            
            payload.add("deltas", deltas);
        }

        if (!payload.has("deltas")) {
            JsonElement itemsJson = Memory.CODEC.encodeStart(JsonOps.INSTANCE, memory).getOrThrow();
            payload.add("items_data", itemsJson);
        }

        String json = GSON.toJson(payload);
        LOGGER.debug("Sending sync update: " + json);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.apiUrl.replaceAll("/$", "") + "/sync/update"))
                .header("Content-Type", "application/json")
                .header("X-Token", config.apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("Failed to sync chest: " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error syncing chest", ex);
                    return null;
                });
    }

    public void sendChestDelete(String serverId, String worldId, BlockPos pos) {
        var config = getConfig();
        if (!config.enabled || config.apiUrl == null || config.apiUrl.isEmpty()) return;
        if (serverId == null) return;

        String encodedServerId = URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        String encodedWorldId = URLEncoder.encode(worldId, StandardCharsets.UTF_8);
        
        String url = String.format("%s/sync/delete/%s/%s/%d/%d/%d",
                config.apiUrl.replaceAll("/$", ""),
                encodedServerId,
                encodedWorldId,
                pos.getX(),
                pos.getY(),
                pos.getZ());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Token", config.apiToken)
                .DELETE()
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 404) {
                        LOGGER.error("Failed to delete chest: " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error deleting chest", ex);
                    return null;
                });
    }

    public void sendClear(String serverId) {
        var config = getConfig();
        if (!config.enabled || config.apiUrl == null || config.apiUrl.isEmpty()) return;
        if (serverId == null) return;

        String encodedServerId = URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        String url = config.apiUrl.replaceAll("/$", "") + "/sync/clear/" + encodedServerId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-Token", config.apiToken)
                .DELETE()
                .build();

        CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOGGER.error("Failed to clear chests: " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error clearing chests", ex);
                    return null;
                });
    }

    public CompletableFuture<Void> fetchChests(String serverId) {
        var config = getConfig();
        if (!config.enabled || config.apiUrl == null || config.apiUrl.isEmpty()) return CompletableFuture.completedFuture(null);
        if (serverId == null) return CompletableFuture.completedFuture(null);

        String encodedId = URLEncoder.encode(serverId, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.apiUrl.replaceAll("/$", "") + "/sync/fetch/" + encodedId))
                .header("X-Token", config.apiToken)
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonArray chests = GSON.fromJson(response.body(), JsonArray.class);
                        MemoryBankAccessImpl.INSTANCE.getLoadedInternal().ifPresent(bank -> {
                            int count = 0;
                            for (JsonElement element : chests) {
                                JsonObject obj = element.getAsJsonObject();
                                int x = obj.get("pos_x").getAsInt();
                                int y = obj.get("pos_y").getAsInt();
                                int z = obj.get("pos_z").getAsInt();
                                JsonElement itemsData = obj.get("items_data");
                                String keyIdStr = obj.has("key_id") ? obj.get("key_id").getAsString() : "minecraft:chest";

                                try {
                                    Memory memory = Memory.CODEC.parse(JsonOps.INSTANCE, itemsData).getOrThrow();
                                    Identifier keyId = Identifier.parse(keyIdStr); 
                                    bank.addMemory(keyId, new BlockPos(x, y, z), memory, false);
                                    count++;
                                } catch (Exception e) {
                                    LOGGER.error("Failed to parse remote memory", e);
                                }
                            }
                            LOGGER.info("Fetched {} chests from sync API", count);
                        });
                    } else {
                        LOGGER.error("Failed to fetch chests: Status " + response.statusCode() + " - " + response.body());
                    }
                })
                .exceptionally(ex -> {
                    LOGGER.error("Error fetching chests", ex);
                    return null;
                });
    }
}
