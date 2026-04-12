package com.rubixmod.bestiary;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatListener {

    // Matches "Grim Reaper XIV ➡ XV" (tier upgrade)
    private static final Pattern TIER_UP_PATTERN = Pattern.compile(
            "^(.+?)\\s+([IVXLCDM]+)\\s+➡\\s+([IVXLCDM]+)$"
    );

    // Matches "Grim Reaper ➡ I" (first tier unlock)
    private static final Pattern FIRST_TIER_PATTERN = Pattern.compile(
            "^(.+?)\\s+➡\\s+([IVXLCDM]+)$"
    );

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;

            String text = message.getString().replaceAll("§.", "").trim();

            // On Hypixel, ALL player chat messages follow the format:
            // "[RANK] PlayerName: message"
            // So if the text contains ": " it's a player message — skip it
            if (text.contains(": ")) return;

            // Try tier upgrade pattern first (e.g. "Zombie XIV ➡ XV")
            Matcher matcher = TIER_UP_PATTERN.matcher(text);
            if (matcher.find()) {
                String mobName = matcher.group(1).trim();
                int oldTier = fromRoman(matcher.group(2).trim());
                int newTier = fromRoman(matcher.group(3).trim());

                if (newTier > 0 && newTier > oldTier) {
                    BestiaryTierUpHandler.onTierUp(mobName, newTier, newTier - oldTier);
                }
                return;
            }

            // Try first tier pattern (e.g. "Zombie ➡ I")
            Matcher firstMatcher = FIRST_TIER_PATTERN.matcher(text);
            if (firstMatcher.find()) {
                String mobName = firstMatcher.group(1).trim();
                int newTier = fromRoman(firstMatcher.group(2).trim());

                if (newTier > 0) {
                    BestiaryTierUpHandler.onTierUp(mobName, newTier, 1);
                }
            }
        });
    }

    private static int fromRoman(String roman) {
        int result = 0;
        int prev = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int curr = romanVal(roman.charAt(i));
            if (curr == 0) return 0;
            if (curr < prev) {
                result -= curr;
            } else {
                result += curr;
            }
            prev = curr;
        }
        return result;
    }

    private static int romanVal(char c) {
        switch (c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default:  return 0;
        }
    }
}