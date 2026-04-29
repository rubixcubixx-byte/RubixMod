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
import net.minecraft.world.entity.Entity;

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
                                    if (!isDev()) {
                                        context.getSource().sendFeedback(
                                                Component.literal("§cThis command can only be used by Devs.")
                                        );
                                        return 0;
                                    }
                                    BestiaryTierUpHandler.onTierUp("Zombie", 3, 1);
                                    BestiaryTierUpHandler.onTierUp("Skeleton", 5, 1);
                                    BestiaryTierUpHandler.onTierUp("Spider", 2, 1);
                                    context.getSource().sendFeedback(
                                            Component.literal("§aRubixMod: Test popups fired!")
                                    );
                                    return 1;
                                })
                        )
                        .then(ClientCommandManager.literal("testmaxtier")
                                .executes(context -> {
                                    if (!isDev()) {
                                        context.getSource().sendFeedback(
                                                Component.literal("§cThis command can only be used by Devs.")
                                        );
                                        return 0;
                                    }
                                    BestiaryTierUpHandler.onMobMaxed("Zombie");
                                    context.getSource().sendFeedback(
                                            Component.literal("§aRubixMod: Max tier test popup fired!")
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
                        .then(ClientCommandManager.literal("entdebug")
                                .executes(context -> {
                                    Minecraft client = Minecraft.getInstance();
                                    if (client.level == null || client.player == null) return 0;
                                    context.getSource().sendFeedback(Component.literal("§e--- Nearby entities (<=16 blocks) ---"));
                                    int count = 0;
                                    for (Entity e : client.level.entitiesForRendering()) {
                                        if (e.distanceTo(client.player) > 16) continue;
                                        if (e == client.player) continue;
                                        String type = e.getType().toShortString();
                                        String custom = e.hasCustomName()
                                                ? e.getCustomName().getString() : "(none)";
                                        String display = e.getDisplayName().getString();
                                        boolean invisible = e.isInvisible();
                                        context.getSource().sendFeedback(Component.literal(
                                                "§7[" + type + "] §fcustom=§a" + custom
                                                + " §fdisplay=§b" + display
                                                + (invisible ? " §c(invis)" : "")));
                                        if (++count >= 20) {
                                            context.getSource().sendFeedback(Component.literal("§c(truncated at 20)"));
                                            break;
                                        }
                                    }
                                    if (count == 0) context.getSource().sendFeedback(Component.literal("§cNo entities found."));
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

    private static boolean isDev() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null &&
                mc.player.getGameProfile().name().equalsIgnoreCase("Rubixcubixx");
    }
}