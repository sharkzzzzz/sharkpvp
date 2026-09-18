package com.sharkzw.pvpmechanics;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

/** Small client-only config. Kept deliberately simple so it has no runtime dependency. */
public final class PvPMechanicsConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("pvpmechanics.properties");
    private static final Properties PROPERTIES = new Properties();

    public static boolean compactCards;
    public static boolean showContentStatus;
    public static String packsMcApiKey = "";
    public static boolean reducedMotion = true;
    public static String lastGamemode = "sword";
    public static String lastSearch = "";
    public static final Set<String> favorites = new LinkedHashSet<>();
    public static final Set<String> recent = new LinkedHashSet<>();

    private PvPMechanicsConfig() {}

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                PROPERTIES.clear();
                PROPERTIES.load(reader);
            } catch (IOException ignored) {
                // Defaults are safe if the file cannot be read.
            }
        }
        compactCards = Boolean.parseBoolean(PROPERTIES.getProperty("compactCards", "false"));
        showContentStatus = Boolean.parseBoolean(PROPERTIES.getProperty("showContentStatus", "false"));
        packsMcApiKey = PROPERTIES.getProperty("packsMcApiKey", "");
        reducedMotion = Boolean.parseBoolean(PROPERTIES.getProperty("reducedMotion", "true"));
        lastGamemode = PROPERTIES.getProperty("lastGamemode", "sword");
        lastSearch = PROPERTIES.getProperty("lastSearch", "");
        favorites.clear();
        recent.clear();
        String stored = PROPERTIES.getProperty("favorites", "");
        if (!stored.isBlank()) {
            for (String id : stored.split(",")) {
                if (!id.isBlank()) favorites.add(id.trim());
            }
        }
        String recentStored = PROPERTIES.getProperty("recent", "");
        if (!recentStored.isBlank()) {
            for (String id : recentStored.split(",")) {
                if (!id.isBlank()) recent.add(id.trim());
            }
        }
    }

    public static void save() {
        PROPERTIES.setProperty("compactCards", Boolean.toString(compactCards));
        PROPERTIES.setProperty("showContentStatus", Boolean.toString(showContentStatus));
        PROPERTIES.setProperty("packsMcApiKey", packsMcApiKey);
        PROPERTIES.setProperty("reducedMotion", Boolean.toString(reducedMotion));
        PROPERTIES.setProperty("lastGamemode", lastGamemode);
        PROPERTIES.setProperty("lastSearch", lastSearch);
        PROPERTIES.setProperty("favorites", String.join(",", favorites));
        PROPERTIES.setProperty("recent", String.join(",", recent));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                PROPERTIES.store(writer, "SharkzW PvP Mechanics client settings");
            }
        } catch (IOException ignored) {
            // A settings write failure must never affect gameplay.
        }
    }
}
