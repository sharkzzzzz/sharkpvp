package com.sharkzw.pvpmechanics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Lightweight runtime index for data-driven PvP content. */
public final class PvPContentRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("SharkPVP/Content");
    private static final Gson GSON = new Gson();
    private static volatile Snapshot snapshot = Snapshot.empty();

    private PvPContentRegistry() {}

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ReloadListener());
    }

    public static Snapshot snapshot() {
        return snapshot;
    }

    public record Content(String id, String name, String description, List<String> gamemodes, List<String> tags) {}

    public record Snapshot(List<Content> mechanics, List<String> errors) {
        static Snapshot empty() { return new Snapshot(List.of(), List.of()); }
    }

    private static final class ReloadListener implements net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener {
        @Override
        public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("sharkpvp", "content");
        }

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            List<Content> loaded = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (Map.Entry<Identifier, Resource> entry : manager.listResources("sharkpvp/mechanics", id -> id.getPath().endsWith(".json")).entrySet()) {
                Identifier resourceId = entry.getKey();
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json == null || !json.has("id") || !json.has("name")) {
                        errors.add(resourceId + ": missing id or name");
                        continue;
                    }
                    List<String> modes = json.has("gamemodes") ? GSON.fromJson(json.get("gamemodes"), List.class) : List.of();
                    List<String> tags = json.has("tags") ? GSON.fromJson(json.get("tags"), List.class) : List.of();
                    loaded.add(new Content(
                            json.get("id").getAsString(),
                            json.get("name").getAsString(),
                            json.has("description") ? json.get("description").getAsString() : "",
                            List.copyOf(modes), List.copyOf(tags)));
                } catch (Exception ex) {
                    errors.add(resourceId + ": invalid JSON/content (" + ex.getClass().getSimpleName() + ")");
                }
            }

            loaded.sort(Comparator.comparing(Content::name, String.CASE_INSENSITIVE_ORDER));
            snapshot = new Snapshot(List.copyOf(loaded), List.copyOf(errors));
            if (!errors.isEmpty()) {
                LOGGER.warn("Loaded {} PvP mechanics with {} content errors", loaded.size(), errors.size());
            } else {
                LOGGER.info("Loaded {} PvP mechanics", loaded.size());
            }
        }
    }
}
