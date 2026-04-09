package com.rubixmod.bestiary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete list of all Hypixel Skyblock bestiary mobs, organized by category.
 * Used by BestiaryHudEditorScreen to populate the dropdown.
 * Note: The dropdown now primarily uses BestiaryData (actual scanned data),
 * but this list is kept as a reference/fallback.
 */
public class BestiaryMobList {

    // Main categories map: category name -> list of mob names
    public static final Map CATEGORIES = new LinkedHashMap();

    // Fishing subcategories (nested under "Fishing")
    public static final Map FISHING_SUBCATEGORIES = new LinkedHashMap();
    public static final List FISHING_SUBCATEGORY_KEYS = new ArrayList();

    static {
        // --- Your Island ---
        CATEGORIES.put("Your Island", Arrays.asList(
                "Bat", "Creeper", "Enderman", "Skeleton", "Slime",
                "Spider", "Witch", "Zombie"
        ));

        // --- Hub ---
        CATEGORIES.put("Hub", Arrays.asList(
                "Crypt Ghoul", "Golden Ghoul", "Graveyard Zombie", "Old Wolf",
                "Shiny Pig", "Wolf", "Zombie Villager"
        ));

        // --- The Farming Islands ---
        CATEGORIES.put("The Farming Islands", Arrays.asList(
                "Chicken", "Cow", "Mushroom Cow", "Pig", "Rabbit", "Sheep"
        ));

        // --- The Garden ---
        CATEGORIES.put("The Garden", Arrays.asList(
                "Beetle", "Cricket", "Dragonfly", "Earthworm", "Field Mouse",
                "Firefly", "Fly", "Locust", "Mite", "Mosquito", "Moth",
                "Praying Mantis", "Rat", "Slug", "Timestalk Clone", "Zombuddy"
        ));

        // --- Spider's Den ---
        CATEGORIES.put("Spider's Den", Arrays.asList(
                "Arachne", "Arachne's Brood", "Arachne's Keeper", "Broodmother",
                "Dasher Spider", "Flint Skeleton", "Rain Slime", "Silverfish",
                "Spider Jockey", "Splitter Spider", "Voracious Spider", "Weaver Spider"
        ));

        // --- The End ---
        CATEGORIES.put("The End", Arrays.asList(
                "Dragon", "End Stone Protector", "Enderman", "Endermite",
                "Obsidian Defender", "Seer", "Voidling Extremist",
                "Voidling Fanatic", "Zealot"
        ));

        // --- Crimson Isle ---
        CATEGORIES.put("Crimson Isle", Arrays.asList(
                "Ashfang", "Barbarian Duke", "Bladesoul", "Blaze",
                "Flaming Spider", "Flare", "Ghast", "Kada Knight",
                "Mage Outlaw", "Magma Boss", "Magma Cube", "Magma Cube Rider",
                "Matcho", "Millennia-Aged Blaze", "Mushroom Bull",
                "Smoldering Blaze", "Tentacle", "Vanquisher",
                "Wither Skeleton", "Wither Spectre"
        ));

        // --- Deep Caverns ---
        CATEGORIES.put("Deep Caverns", Arrays.asList(
                "Emerald Slime", "Lapis Zombie", "Miner Skeleton",
                "Miner Zombie", "Redstone Pigman", "Sneaky Creeper"
        ));

        // --- Dwarven Mines ---
        CATEGORIES.put("Dwarven Mines", Arrays.asList(
                "Diamond Goblin", "Ghost", "Glacite Bowman", "Glacite Caver",
                "Glacite Mage", "Glacite Mutt", "Glacite Walker",
                "Goblin", "Goblin Raiders", "Golden Goblin",
                "Littlefoot", "Powder Ghast", "Star Sentry", "Treasure Hoarder"
        ));

        // --- Crystal Hollows ---
        CATEGORIES.put("Crystal Hollows", Arrays.asList(
                "Automaton", "Bal", "Boss Corleone", "Butterfly",
                "Grunt", "Key Guardian", "Sludge", "Thyst", "Worm", "Yog"
        ));

        // --- The Park ---
        CATEGORIES.put("The Park", Arrays.asList(
                "Howling Spirit", "Pack Spirit", "Soul of the Alpha"
        ));

        // --- Galatea ---
        CATEGORIES.put("Galatea", Arrays.asList(
                "Bogged", "Chill", "Ent", "Nessie", "Stridersurfer",
                "Tadgang", "The Loch Emperor", "Tidetot", "Wetwing"
        ));

        // --- Spooky Festival ---
        CATEGORIES.put("Spooky Festival", Arrays.asList(
                "Crazy Witch", "Headless Horseman", "Phantom Spirit",
                "Scary Jerry", "Trick or Treater", "Wither Gourd", "Wraith"
        ));

        // --- The Catacombs ---
        CATEGORIES.put("The Catacombs", Arrays.asList(
                "Angry Archaeologist", "Bat", "Cellar Spider", "Crypt Dreadlord",
                "Crypt Lurker", "Crypt Souleater", "Fels", "Golem",
                "King Midas", "Lonely Spider", "Lost Adventurer", "Mimic",
                "Scared Skeleton", "Shadow Assassin", "Skeleton Grunt",
                "Skeleton Lord", "Skeleton Master", "Skeleton Soldier",
                "Skeletor", "Sniper", "Super Archer", "Super Tank Zombie",
                "Tank Zombie", "Terracotta", "Undead", "Undead Skeleton",
                "Wither Guard", "Wither Husk", "Wither Miner", "Withermancer",
                "Zombie Commander", "Zombie Grunt", "Zombie Knight",
                "Zombie Lord", "Zombie Soldier"
        ));

        // --- Fishing (parent placeholder) ---
        CATEGORIES.put("Fishing", new ArrayList());

        // --- Fishing Subcategories ---
        FISHING_SUBCATEGORY_KEYS.add("Fishing > Fishing");
        FISHING_SUBCATEGORIES.put("Fishing > Fishing", Arrays.asList(
                "Abyssal Miner", "Agarimoo", "Blue Ringed Octopus", "Carrot King",
                "Catfish", "Deep Sea Protector", "Frog Man", "Guardian Defender",
                "Mithril Grubber", "Night Squid", "Oasis Rabbit", "Oasis Sheep",
                "Poisoned Water Worm", "Rider of the Deep", "Sea Archer",
                "Sea Guardian", "Sea Leech", "Sea Walker", "Sea Witch",
                "Snapping Turtle", "Squid", "Water Hydra", "Water Worm", "Wiki Tiki"
        ));

        FISHING_SUBCATEGORY_KEYS.add("Fishing > Lava");
        FISHING_SUBCATEGORIES.put("Fishing > Lava", Arrays.asList(
                "Fiery Scuttler", "Fire Eel", "Fireproof Witch", "Flaming Worm",
                "Fried Chicken", "Lava Blaze", "Lava Flame", "Lava Leech",
                "Lava Pigman", "Lord Jawbus", "Magma Slug", "Moogma",
                "Plhlegblast", "Pyroclastic Worm", "Ragnarok", "Taurus", "Thunder"
        ));

        FISHING_SUBCATEGORY_KEYS.add("Fishing > Fishing Festival");
        FISHING_SUBCATEGORIES.put("Fishing > Fishing Festival", Arrays.asList(
                "Blue Shark", "Great White Shark", "Nurse Shark", "Tiger Shark"
        ));

        FISHING_SUBCATEGORY_KEYS.add("Fishing > Winter");
        FISHING_SUBCATEGORIES.put("Fishing > Winter", Arrays.asList(
                "Frosty", "Frozen Steve", "Grinch", "Nutcracker", "Reindrake", "Yeti"
        ));

        FISHING_SUBCATEGORY_KEYS.add("Fishing > Backwater Bayou");
        FISHING_SUBCATEGORIES.put("Fishing > Backwater Bayou", Arrays.asList(
                "Alligator", "Banshee", "Bayou Sludge", "Dumpster Diver",
                "Titanoboa", "Trash Gobbler"
        ));

        // --- Mythological Creatures ---
        CATEGORIES.put("Mythological Creatures", Arrays.asList(
                "Cretan Bull", "Gaia Construct", "Harpy", "King Minos",
                "Manticore", "Minos Champion", "Minos Hunter", "Minos Inquisitor",
                "Minotaur", "Siamese Lynx", "Sphinx", "Stranded Nymph"
        ));

        // --- Jerry ---
        CATEGORIES.put("Jerry", Arrays.asList(
                "Blue Jerry", "Golden Jerry", "Green Jerry", "Purple Jerry"
        ));

        // --- Kuudra ---
        CATEGORIES.put("Kuudra", Arrays.asList(
                "Blazing Golem", "Blight", "Dropship", "Explosive Imp",
                "Inferno Magma Cube", "Kuudra Berserker", "Kuudra Follower",
                "Kuudra Knocker", "Kuudra Landmine", "Kuudra Slasher",
                "Magma Follower", "Wandering Blaze", "Wither Sentry"
        ));
    }
}