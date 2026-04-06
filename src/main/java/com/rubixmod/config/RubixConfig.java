package com.rubixmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RubixConfig {

    public boolean hudEnabled = true;
    public boolean bestiaryAlertsEnabled = true;
    public boolean dungeonScoreEnabled = false;
    public boolean blazeSolverEnabled = false;
    public boolean batDeathAlertEnabled = true;
    public String hypixelApiKey = "";

    // Bestiary HUD position and scale
    public float bestiaryHudX = 5;
    public float bestiaryHudY = 5;
    public float bestiaryHudScale = 1.0f;

    // Bestiary Alerts (popups) position and scale
    public float alertsX = -1;
    public float alertsY = -1;
    public float alertsScale = 1.0f;

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

    public static void load() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("rubixmod.json");
            File file = path.toFile();
            if (file.exists()) {
                try (Reader reader = new FileReader(file)) {
                    instance = GSON.fromJson(reader, RubixConfig.class);
                    // Ensure lists are never null after load
                    if (instance.trackedMobs == null) instance.trackedMobs = new ArrayList<>();
                    if (instance.bestiaryKillData == null) instance.bestiaryKillData = new LinkedHashMap<>();
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
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("rubixmod.json");
            try (Writer writer = new FileWriter(path.toFile())) {
                GSON.toJson(get(), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean hasApiKey() {
        return hypixelApiKey != null && !hypixelApiKey.isBlank();
    }
}