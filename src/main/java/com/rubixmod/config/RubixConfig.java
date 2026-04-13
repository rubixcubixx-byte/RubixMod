package com.rubixmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RubixConfig {

    public boolean hudEnabled = true;
    public boolean bestiaryAlertsEnabled = true;
    public boolean hudAutoTrack = false;
    public boolean hudPerTierMode = false;
    public boolean dungeonScoreEnabled = false;
    public boolean blazeSolverEnabled = false;
    public boolean batDeathAlertEnabled = true;
    public boolean littlefootTrackerEnabled = true;
    public String hypixelApiKey = "";

    // Bestiary HUD position and scale
    public float bestiaryHudX = 5;
    public float bestiaryHudY = 5;
    public float bestiaryHudScale = 1.0f;

    // Bestiary Alerts (popups) position and scale
    public float alertsX = -1;
    public float alertsY = -1;
    public float alertsScale = 1.0f;

    // Bestiary HUD max mobs shown at once (1–15)
    public int hudMaxMobs = 5;

    // Bestiary UI: hide mobs that are fully maxed
    public boolean bestiaryHideMax = false;

    // Bestiary UI: set of category keys that are currently collapsed
    public java.util.HashSet<String> collapsedCategories = new java.util.HashSet<>();

    // Bestiary UI: set of category keys hidden by the icon filter bar (empty = all visible)
    public java.util.HashSet<String> hiddenCategories = new java.util.HashSet<>();

    // Bestiary UI: user-defined ordering of category pills (empty = alphabetical)
    public List<String> categoryOrder = new ArrayList<>();

    // Persists tier bar start positions across sessions.
    // key = trackedKey ("Category > Mob"), value = "tierNum,tierStart"
    public Map<String, String> tierStartCache = new LinkedHashMap<>();

    // Tracked mobs for the Bestiary HUD (ordered list of mob names)
    public List trackedMobs = new ArrayList<>();

    // Bestiary kill data: mob name -> [currentKills, maxKills]
    // Populated from /bestiary menu reads
    public Map bestiaryKillData = new LinkedHashMap<>();

    private static RubixConfig instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static RubixConfig get() {
        if (instance == null) instance = new RubixConfig();
        return instance;
    }

    private static Path configFile() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("rubixmod");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir.resolve("rubixmod.json");
    }

    public static void load() {
        try {
            File file = configFile().toFile();
            if (file.exists()) {
                try (Reader reader = new FileReader(file)) {
                    instance = GSON.fromJson(reader, RubixConfig.class);
                    if (instance.trackedMobs == null) instance.trackedMobs = new ArrayList<>();
                    if (instance.bestiaryKillData == null) instance.bestiaryKillData = new LinkedHashMap<>();
                    if (instance.collapsedCategories == null) instance.collapsedCategories = new java.util.HashSet<>();
                    if (instance.hiddenCategories == null)   instance.hiddenCategories   = new java.util.HashSet<>();
                    if (instance.categoryOrder == null)     instance.categoryOrder      = new ArrayList<>();
                    if (instance.tierStartCache == null)     instance.tierStartCache     = new LinkedHashMap<>();
                    if (instance.hudMaxMobs <= 0) instance.hudMaxMobs = 5;
                }
            } else {
                instance = new RubixConfig();
                save();
            }
        } catch (Exception e) {
            instance = new RubixConfig();
        }
    }

    public static void save() {
        try (Writer writer = new FileWriter(configFile().toFile())) {
            GSON.toJson(get(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasApiKey() {
        return hypixelApiKey != null && !hypixelApiKey.isBlank();
    }
}