package com.rubixmod;

import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryMenuReader;
import com.rubixmod.bestiary.HypixelApiFetcher;
import com.rubixmod.bestiary.KillTracker;
import com.rubixmod.command.RubixCommand;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.dungeon.BatDeathDetector;
import com.rubixmod.hud.BestiaryHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RubixMod implements ClientModInitializer {

    public static final String MOD_ID = "rubixmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("RubixMod is loading... Let's go!");

        // Load config and bestiary data
        RubixConfig.load();
        BestiaryData.load();

        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RubixCommand.register(dispatcher);
        });

        // Register bestiary menu reader (reads /bestiary GUI)
        BestiaryMenuReader.register();

        // Register kill tracker (increments kills in-game)
        KillTracker.register();

        // Register bat death detector
        BatDeathDetector.register();

        // Register HUD rendering (handles both bestiary HUD and tier-up popups)
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            BestiaryHud.render(graphics, RubixConfig.get().bestiaryHudScale);
        });

        // Connect to Hypixel: start API fetcher
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HypixelApiFetcher.start();
            HypixelApiFetcher.fetchNow();
        });

        // Disconnect: stop API fetcher
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HypixelApiFetcher.stop();
        });
    }
}