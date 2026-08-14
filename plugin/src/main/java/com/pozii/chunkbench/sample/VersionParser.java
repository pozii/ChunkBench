package com.pozii.chunkbench.sample;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses legacy (1.x.y) and modern year.drop (26.2) Minecraft versions.
 */
public final class VersionParser {

    private static final Pattern LEGACY = Pattern.compile("(?i)(?:MC|Minecraft)?\\s*:?\\s*v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final Pattern YEAR_DROP = Pattern.compile("(?i)\\b(2[0-9])\\.(\\d+)(?:\\.(\\d+))?\\b");

    private VersionParser() {
    }

    public static String fromBukkitVersion(String bukkitVersion) {
        if (bukkitVersion == null) {
            return "unknown";
        }
        // e.g. 1.20.4-R0.1-SNAPSHOT or 26.2-R0.1-SNAPSHOT
        String base = bukkitVersion.split("-")[0].trim();
        if (looksLikeVersion(base)) {
            return normalize(base);
        }
        return fromServerVersion(bukkitVersion);
    }

    public static String fromServerVersion(String serverVersion) {
        if (serverVersion == null) {
            return "unknown";
        }
        Matcher y = YEAR_DROP.matcher(serverVersion);
        // Prefer year.drop when major >= 20 and not classic 1.x
        Matcher legacy = LEGACY.matcher(serverVersion);
        String legacyHit = null;
        if (legacy.find()) {
            String major = legacy.group(1);
            if ("1".equals(major)) {
                legacyHit = legacy.group(1) + "." + legacy.group(2)
                        + (legacy.group(3) != null ? "." + legacy.group(3) : "");
            }
        }
        if (legacyHit != null) {
            return normalize(legacyHit);
        }
        if (y.find()) {
            return normalize(y.group(1) + "." + y.group(2)
                    + (y.group(3) != null ? "." + y.group(3) : ""));
        }
        return "unknown";
    }

    private static boolean looksLikeVersion(String s) {
        return s.matches("1\\.\\d+(?:\\.\\d+)?") || s.matches("2[0-9]\\.\\d+(?:\\.\\d+)?");
    }

    public static String normalize(String v) {
        return v.trim().toLowerCase(Locale.US);
    }

    /**
     * Ordered index for scoring curves. 1.8.8 = 0 ... 26.2 ~= max.
     */
    public static double versionIndex(String version) {
        Parsed p = parse(version);
        if (p == null) {
            return 50; // neutral unknown
        }
        if (p.major == 1) {
            // 1.8.8 -> 0, 1.12 -> ~8, 1.16 -> 16, 1.20 -> 24, 1.21 -> 26
            return (p.minor - 8) + (p.patch / 10.0);
        }
        // year.drop: 26.2 -> map above 1.21
        // treat 25.x as ~28, 26.x as ~30+
        return 27.0 + (p.major - 25) * 2.0 + p.minor * 0.5 + p.patch * 0.05;
    }

    public static Parsed parse(String version) {
        if (version == null || version.equals("unknown")) {
            return null;
        }
        String[] parts = version.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new Parsed(major, minor, patch);
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Parsed {
        public final int major;
        public final int minor;
        public final int patch;

        public Parsed(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }
    }
}
