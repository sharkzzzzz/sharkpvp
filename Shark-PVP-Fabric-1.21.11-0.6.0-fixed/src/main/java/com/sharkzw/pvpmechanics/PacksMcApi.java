package com.sharkzw.pvpmechanics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Small read-only client for the official PacksMC catalog API. */
public final class PacksMcApi {
    private static final String BASE = "https://packsmc.com/api/v1";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Gson GSON = new Gson();

    private PacksMcApi() {}

    public record Pack(String id, String slug, String name, String description, String resolution,
                       List<String> gamemodes, List<String> versions, String thumbnailUrl,
                       long downloads, long likes, String author, boolean verified, String downloadUrl) {}

    public record Page(List<Pack> packs, String nextCursor) {}

    public static CompletableFuture<Page> list(String apiKey, String query, String resolution,
                                               String sort, int limit, String cursor) {
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Add a PacksMC API key in Shark PVP settings."));
        }
        StringBuilder q = new StringBuilder(BASE).append("/packs?limit=").append(Math.max(1, Math.min(50, limit)));
        append(q, "sort", sort);
        append(q, "q", query);
        append(q, "resolution", resolution);
        append(q, "cursor", cursor);
        return request(apiKey, q.toString()).thenApply(body -> parsePage(body));
    }

    public static CompletableFuture<Pack> details(String apiKey, String idOrSlug) {
        if (apiKey == null || apiKey.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException("Add a PacksMC API key in settings."));
        return request(apiKey, BASE + "/packs/" + URLEncoder.encode(idOrSlug, StandardCharsets.UTF_8))
                .thenApply(PacksMcApi::parsePack);
    }

    private static CompletableFuture<String> request(String key, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + key.trim())
                .header("Accept", "application/json")
                .header("User-Agent", "Shark-PVP/0.6.0 Minecraft")
                .GET().build();
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("PacksMC API returned HTTP " + response.statusCode());
                }
                return response.body();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    private static Page parsePage(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<Pack> packs = new ArrayList<>();
        JsonArray data = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : new JsonArray();
        data.forEach(e -> packs.add(parsePack(e.getAsJsonObject())));
        String cursor = root.has("next_cursor") && !root.get("next_cursor").isJsonNull() ? root.get("next_cursor").getAsString() : "";
        return new Page(List.copyOf(packs), cursor);
    }

    private static Pack parsePack(String body) { return parsePack(JsonParser.parseString(body).getAsJsonObject()); }

    private static Pack parsePack(JsonObject o) {
        return new Pack(
                str(o, "id"), str(o, "slug"), str(o, "name"), str(o, "description"), str(o, "resolution"),
                strings(o, "gamemodes"), strings(o, "mc_versions"), str(o, "thumbnail_url"),
                num(o, "downloads"), num(o, "likes"), nestedStr(o, "author", "display_name", "username"),
                nestedBool(o, "author", "verified"), str(o, "download_url")
        );
    }

    private static String str(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : ""; }
    private static long num(JsonObject o, String key) { return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : 0L; }
    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) o.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        return List.copyOf(out);
    }
    private static String nestedStr(JsonObject o, String parent, String first, String fallback) {
        if (!o.has(parent) || !o.get(parent).isJsonObject()) return "";
        JsonObject n = o.getAsJsonObject(parent);
        String a = str(n, first); return a.isBlank() ? str(n, fallback) : a;
    }
    private static boolean nestedBool(JsonObject o, String parent, String key) {
        return o.has(parent) && o.get(parent).isJsonObject() && o.getAsJsonObject(parent).has(key) && o.getAsJsonObject(parent).get(key).getAsBoolean();
    }
    private static void append(StringBuilder b, String key, String value) {
        if (value != null && !value.isBlank()) b.append('&').append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
