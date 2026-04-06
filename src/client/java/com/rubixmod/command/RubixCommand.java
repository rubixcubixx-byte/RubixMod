package com.rubixmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.rubixmod.bestiary.BestiaryData;
import com.rubixmod.bestiary.BestiaryTierUpHandler;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.gui.RubixScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class RubixCommand {

    public static void register(CommandDispatcher dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("rubix")
                        .executes(context -> {
                            Minecraft client = Minecraft.getInstance();
                            client.execute(() -> client.setScreen(new RubixScreen()));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("testpopup")
                                .executes(context -> {
                                    BestiaryTierUpHandler.onTierUp("Zombie", 3, 1);
                                    BestiaryTierUpHandler.onTierUp("Skeleton", 5, 1);
                                    BestiaryTierUpHandler.onTierUp("Spider", 2, 1);
                                    // Save some test data into "Test" category so popups have data to show
                                    BestiaryData.saveMob("Test", "Zombie", 100, 200);
                                    BestiaryData.saveMob("Test", "Skeleton", 500, 500);
                                    BestiaryData.saveMob("Test", "Spider", 10, 200);
                                    BestiaryData.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§aRubixMod: Test popups fired!")
                                    );
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("hud")
                                .executes(context -> {
                                    context.getSource().sendFeedback(
                                            Component.literal("§aRubixMod: HUD is " +
                                                    (RubixConfig.get().hudEnabled ? "§aENABLED" : "§cDISABLED"))
                                    );
                                    return 1;
                                })
                        )
        );
    }
}