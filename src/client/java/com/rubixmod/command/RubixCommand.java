package com.rubixmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.rubixmod.bestiary.BestiaryTierUpHandler;
import com.rubixmod.config.RubixConfig;
import com.rubixmod.gui.BestiaryViewScreen;
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
                        .then(ClientCommandManager.literal("bestiary")
                                .executes(context -> {
                                    Minecraft client = Minecraft.getInstance();
                                    client.execute(() -> client.setScreen(new BestiaryViewScreen()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("be")
                                .executes(context -> {
                                    Minecraft client = Minecraft.getInstance();
                                    client.execute(() -> client.setScreen(new BestiaryViewScreen()));
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("testpopup")
                                .executes(context -> {
                                    BestiaryTierUpHandler.onTierUp("Zombie", 3, 1);
                                    BestiaryTierUpHandler.onTierUp("Skeleton", 5, 1);
                                    BestiaryTierUpHandler.onTierUp("Spider", 2, 1);
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
                        .then(ClientCommandManager.literal("autotrack")
                                .executes(context -> {
                                    RubixConfig cfg = RubixConfig.get();
                                    cfg.hudAutoTrack = !cfg.hudAutoTrack;
                                    RubixConfig.save();
                                    context.getSource().sendFeedback(
                                            Component.literal("§aRubixMod: Auto Track is now " +
                                                    (cfg.hudAutoTrack ? "§aON §7— HUD will show mobs you're actively killing."
                                                                      : "§cOFF §7— HUD shows your pinned mob list."))
                                    );
                                    return 1;
                                })
                        )
        );
    }
}