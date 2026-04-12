package com.rubixmod.bestiary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rubixmod.config.RubixConfig;
import net.minecraft.client.Minecraft;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HypixelApiFetcher {

    private static ScheduledExecutorService scheduler = null;

    private static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "RubixMod-API");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static void start() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = newScheduler();
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                fetchBestiary();
            }
        }, 2, 3, TimeUnit.MINUTES);
    }

    public static void fetchNow() {
        if (scheduler == null || scheduler.isShutdown()) return;
        scheduler.execute(new Runnable() {
            public void run() {
                fetchBestiary();
            }
        });
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void fetchBestiary() {
        try {
            if (!RubixConfig.get().hasApiKey()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            String uuid = client.player.getGameProfile().id().toString().replace("-", "");
            String apiKey = RubixConfig.get().hypixelApiKey;

            String profilesUrl = "https://api.hypixel.net/v2/skyblock/profiles?uuid=" + uuid;
            JsonObject profilesResponse = fetchJson(profilesUrl, apiKey);

            if (profilesResponse == null) return;

            if (!profilesResponse.has("success") || !profilesResponse.get("success").getAsBoolean()) return;

            if (!profilesResponse.has("profiles") || profilesResponse.get("profiles").isJsonNull()) return;

            // Find selected profile
            JsonObject selectedProfile = null;
            for (JsonElement profileEl : profilesResponse.getAsJsonArray("profiles")) {
                JsonObject profile = profileEl.getAsJsonObject();
                if (profile.has("selected") && profile.get("selected").getAsBoolean()) {
                    selectedProfile = profile;
                    break;
                }
            }

            if (selectedProfile == null) return;
            if (!selectedProfile.has("members")) return;

            JsonObject members = selectedProfile.getAsJsonObject("members");
            if (!members.has(uuid)) return;

            JsonObject memberData = members.getAsJsonObject(uuid);
            if (!memberData.has("bestiary")) return;

            JsonObject bestiary = memberData.getAsJsonObject("bestiary");
            if (!bestiary.has("kills")) return;

            JsonObject kills = bestiary.getAsJsonObject("kills");

            Set entries = kills.entrySet();
            for (Object obj : entries) {
                Map.Entry entry = (Map.Entry) obj;
                String mobKey = (String) entry.getKey();
                int killCount = ((JsonElement) entry.getValue()).getAsInt();
                String mobName = formatMobName(mobKey);
                // API data goes into an "API" category as a reference
                // The /bestiary GUI scan is the authoritative source for category-aware data
                BestiaryData.saveMob("API", mobName, killCount, 0);
            }

            BestiaryData.save();

        } catch (Exception ignored) {}
    }

    private static JsonObject fetchJson(String urlStr, String apiKey) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "RubixMod/1.0");
            conn.setRequestProperty("API-Key", apiKey);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            JsonObject result = JsonParser.parseReader(reader).getAsJsonObject();
            reader.close();
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String formatMobName(String key) {
        String stripped = key.replaceAll("_\\d+$", "");
        String[] parts = stripped.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
                if (i < parts.length - 1) sb.append(" ");
            }
        }
        return sb.toString();
    }
}