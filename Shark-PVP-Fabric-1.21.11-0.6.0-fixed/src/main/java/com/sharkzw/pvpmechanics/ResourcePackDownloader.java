package com.sharkzw.pvpmechanics;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Downloads a resource-pack archive into Minecraft's normal resourcepacks directory. */
public final class ResourcePackDownloader {
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private ResourcePackDownloader() {}

    public static CompletableFuture<String> downloadAndInstall(String rawUrl) {
        final URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid URL."));
        }
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Only HTTP and HTTPS URLs are supported."));
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("The URL has no host."));
        }

        Minecraft minecraft = Minecraft.getInstance();
        Path directory = minecraft.getResourcePackDirectory();
        String filename = safeFilename(uri.getPath());
        Path target = uniqueTarget(directory, filename);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Files.createDirectories(directory);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60))
                        .header("User-Agent", "Shark-PVP/0.6.0 Minecraft-Resource-Pack-Installer")
                        .GET()
                        .build();
                HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new IOException("Download failed with HTTP " + response.statusCode());
                }
                long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (declared > MAX_DOWNLOAD_BYTES) {
                    response.body().close();
                    throw new IOException("Resource pack is larger than 512 MB.");
                }

                try (InputStream in = response.body(); var out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[64 * 1024];
                    long total = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_DOWNLOAD_BYTES) {
                            throw new IOException("Resource pack is larger than 512 MB.");
                        }
                        out.write(buffer, 0, read);
                    }
                } catch (Throwable failure) {
                    Files.deleteIfExists(target);
                    throw failure;
                }
                return target.toString();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenCompose(path -> runOnClientThread(() -> {
            PackRepository repository = minecraft.getResourcePackRepository();
            repository.reload();
            String id = targetId(Path.of(path));
            Collection<String> selected = repository.getSelectedIds();
            List<String> next = new ArrayList<>(selected);
            if (!next.contains(id) && repository.isAvailable(id)) next.add(id);
            repository.setSelected(next);
            return id;
        })).thenCompose(id -> runOnClientThread(() -> {
            minecraft.reloadResourcePacks();
            return id;
        }));
    }

    private static <T> CompletableFuture<T> runOnClientThread(java.util.concurrent.Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private static String safeFilename(String path) {
        String raw = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        if (raw.isBlank()) raw = "downloaded_resource_pack.zip";
        raw = raw.replaceAll("[^A-Za-z0-9._-]", "_");
        String lower = raw.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".zip")) raw += ".zip";
        if (raw.length() > 96) raw = raw.substring(0, 92) + ".zip";
        return raw;
    }

    private static Path uniqueTarget(Path directory, String filename) {
        Path candidate = directory.resolve(filename);
        if (!Files.exists(candidate)) return candidate;
        String base = filename.endsWith(".zip") ? filename.substring(0, filename.length() - 4) : filename;
        for (int i = 2; i < 10000; i++) {
            candidate = directory.resolve(base + "-" + i + ".zip");
            if (!Files.exists(candidate)) return candidate;
        }
        return directory.resolve("shark-pvp-pack-" + System.currentTimeMillis() + ".zip");
    }

    private static String targetId(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }
}
