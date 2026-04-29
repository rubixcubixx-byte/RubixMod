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
            // Remove legacy "Test" category written by the old /rubix testpopup command
            data.remove("Test");
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
     * Store the cap tier for a mob (the tier number at which the mob is fully maxed,
     * e.g. 15 for "Capped at Tier XV").  Stored as a 3rd element in the kills list.
     * Must be called after saveMob so the list already exists.
     */
    public static void saveCapTier(String category, String mobName, int capTier) {
        Map catData = (Map) data.get(category);
        if (catData == null) return;
        List kills = (List) catData.get(mobName);
        if (kills == null) return;
        if (kills.size() < 3) {
            kills.add((long) capTier);
        } else {
            kills.set(2, (long) capTier);
        }
    }

    /**
     * Get the stored cap tier for a mob.
     * Returns -1 if the cap tier has not been stored yet (mob not yet seen in /bestiary menu).
     */
    public static int getCapTier(String category, String mobName) {
        Map catData = (Map) data.get(category);
        if (catData == null) return -1;
        List kills = (List) catData.get(mobName);
        if (kills == null || kills.size() < 3) return -1;
        try {
            return ((Number) kills.get(2)).intValue();
        } catch (Exception e) {
            return -1;
        }
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