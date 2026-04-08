package com.rubixmod.bestiary;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.rubixmod.RubixMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages bestiary kill data stored in bestiary_data.json.
 * Structure: { "Category Name": { "Mob Name": [currentKills, maxKills] } }
 */
public class BestiaryData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File dataFile() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("rubixmod");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir.resolve("bestiary_data.json").toFile();
    }

    // In-memory store: category -> mob -> [current, max]
    private static Map data = new LinkedHashMap();

    public static void load() {
        File DATA_FILE = dataFile();
        if (!DATA_FILE.exists()) {
            data = new LinkedHashMap();
            return;
        }
        try (Reader r = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<LinkedHashMap<String, LinkedHashMap<String, List<Number>>>>(){}.getType();
            Map loaded = GSON.fromJson(r, type);
            if (loaded != null) data = loaded;
            else data = new LinkedHashMap();
            RubixMod.LOGGER.info("RubixMod: Loaded bestiary_data.json ({} categories)", data.size());
        } catch (Exception e) {
            RubixMod.LOGGER.error("RubixMod: Failed to load bestiary_data.json", e);
            data = new LinkedHashMap();
        }
    }

    public static void save() {
        try (Writer w = new FileWriter(dataFile())) {
            GSON.toJson(data, w);
        } catch (Exception e) {
            RubixMod.LOGGER.error("RubixMod: Failed to save bestiary_data.json", e);
        }
    }

    /**
     * Save mob data for a specific category.
     */
    public static void saveMob(String category, String mobName, long current, long max) {
        Map catData = (Map) data.computeIfAbsent(category, k -> new LinkedHashMap());
        List kills = new ArrayList();
        kills.add(current);
        kills.add(max);
        catData.put(mobName, kills);
    }

    /**
     * Get kill data for a mob in a specific category.
     * Returns [current, max] or null if not found.
     */
    public static long[] getKills(String category, String mobName) {
        Map catData = (Map) data.get(category);
        if (catData == null) return null;
        List kills = (List) catData.get(mobName);
        if (kills == null || kills.size() < 2) return null;
        try {
            long current = ((Number) kills.get(0)).longValue();
            long max = ((Number) kills.get(1)).longValue();
            return new long[]{current, max};
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all categories that have data.
     */
    public static Set getCategories() {
        return data.keySet();
    }

    /**
     * Get all mob names in a category.
     */
    public static Set getMobsInCategory(String category) {
        Map catData = (Map) data.get(category);
        if (catData == null) return new LinkedHashSet();
        return catData.keySet();
    }

    /**
     * Parse a tracked mob string like "The End > Enderman" into [category, mobName].
     * Returns null if the format is wrong.
     */
    public static String[] parseTrackedKey(String key) {
        int idx = key.lastIndexOf(" > ");
        if (idx < 0) return null;
        String cat = key.substring(0, idx).trim();
        String mob = key.substring(idx + 3).trim();
        return new String[]{cat, mob};
    }
}