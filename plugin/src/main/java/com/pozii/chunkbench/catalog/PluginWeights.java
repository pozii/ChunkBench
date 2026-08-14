package com.pozii.chunkbench.catalog;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Relative plugin overhead weights (1.0 = neutral). */
public final class PluginWeights {

    private static final Map<String, Double> MAP = new HashMap<String, Double>();

    static {
        put("LuckPerms", 0.95);
        put("Vault", 0.9);
        put("PlaceholderAPI", 1.05);
        put("WorldGuard", 1.15);
        put("WorldEdit", 1.1);
        put("CoreProtect", 1.35);
        put("LogBlock", 1.3);
        put("Citizens", 1.45);
        put("MythicMobs", 1.55);
        put("ModelEngine", 1.5);
        put("ItemsAdder", 1.45);
        put("Oraxen", 1.4);
        put("ProtocolLib", 1.1);
        put("ViaVersion", 1.15);
        put("ViaBackwards", 1.1);
        put("Essentials", 1.2);
        put("EssentialsX", 1.2);
        put("ClearLag", 1.05);
        put("Spark", 0.95);
        put("Plan", 1.1);
        put("Multiverse-Core", 1.15);
        put("GriefPrevention", 1.15);
        put("Towny", 1.35);
        put("Factions", 1.3);
        put("SuperiorSkyblock2", 1.4);
        put("Bentobox", 1.35);
        put("Slimefun", 1.6);
        put("ExecutableItems", 1.25);
        put("DecentHolograms", 1.15);
        put("FancyHolograms", 1.1);
        put("TAB", 1.1);
        put("AuctionHouse", 1.2);
        put("ChestShop", 1.1);
        put("ShopGUIPlus", 1.2);
        put("LiteBans", 1.05);
        put("AdvancedBan", 1.05);
        put("DiscordSRV", 1.1);
        put("dynmap", 1.4);
        put("BlueMap", 1.35);
        put("Geyser-Spigot", 1.35);
        put("floodgate", 1.1);
        put("Chunky", 1.2);
        put("ChunkBench", 0.9);
    }

    private PluginWeights() {
    }

    private static void put(String name, double w) {
        MAP.put(name.toLowerCase(Locale.US), Double.valueOf(w));
    }

    public static double weightFor(String pluginName) {
        if (pluginName == null) {
            return 1.15;
        }
        String key = pluginName.toLowerCase(Locale.US);
        Double exact = MAP.get(key);
        if (exact != null) {
            return exact.doubleValue();
        }
        for (Map.Entry<String, Double> e : MAP.entrySet()) {
            if (key.startsWith(e.getKey()) || key.contains(e.getKey())) {
                return e.getValue().doubleValue();
            }
        }
        return 1.15; // unknown default medium
    }
}
