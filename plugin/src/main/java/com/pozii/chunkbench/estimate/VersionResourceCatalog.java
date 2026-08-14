package com.pozii.chunkbench.estimate;

import com.pozii.chunkbench.sample.VersionParser;

/**
 * Research-backed per-version server resource cost model.
 *
 * Sources informing these multipliers (server-side focus):
 * - Mojira MC-190258: idle/server RAM jump after 1.13 (DFU) —
 *   ~0.45G (1.12.2) → ~2.5G (1.13.2) → ~3.0G (1.15–1.16)
 * - Mojira MC-188163: related DFU / CPU pressure notes on modern versions
 * - Community Paper hosting guides (2024–2026): ~200MB/player vanilla-ish on modern;
 *   Paper 1.20+/1.21+/26.x baselines often 4G for small SMPs
 * - 1.17+ world height / caves; 1.18+ worldgen cost; 26.x Java 25 + feature drops
 *
 * Cost factor scales baseline RAM + per-player RAM + CPU weight for the estimator.
 * Higher cost = more resources needed for the SAME player count (harder to score well).
 */
public final class VersionResourceCatalog {

    public static final class Profile {
        public final String band;
        public final double costFactor;
        public final double baselineRamGb;
        public final double ramPerPlayerMb;
        public final double cpuWeight;
        public final String notes;

        public Profile(String band, double costFactor, double baselineRamGb,
                       double ramPerPlayerMb, double cpuWeight, String notes) {
            this.band = band;
            this.costFactor = costFactor;
            this.baselineRamGb = baselineRamGb;
            this.ramPerPlayerMb = ramPerPlayerMb;
            this.cpuWeight = cpuWeight;
            this.notes = notes;
        }
    }

    private VersionResourceCatalog() {
    }

    public static Profile forVersion(String mcVersion) {
        VersionParser.Parsed p = VersionParser.parse(mcVersion);
        if (p == null) {
            return new Profile("unknown", 1.35, 2.5, 180, 1.2,
                    "Unknown version — using mid-modern defaults.");
        }

        // Legacy 1.x line
        if (p.major == 1) {
            if (p.minor <= 8) {
                return new Profile("1.8.x", 0.55, 0.5, 80, 0.70,
                        "1.8.x is light on RAM/CPU vs modern; still common for PvP.");
            }
            if (p.minor <= 12) {
                return new Profile("1.9-1.12", 0.65, 0.6, 90, 0.80,
                        "Pre-1.13 servers avoid DFU memory spike (MC-190258).");
            }
            if (p.minor == 13) {
                return new Profile("1.13", 1.00, 2.2, 140, 1.00,
                        "1.13 DFU introduced large baseline RAM (~2.5G class).");
            }
            if (p.minor <= 15) {
                return new Profile("1.14-1.15", 1.15, 2.6, 155, 1.15,
                        "Post-flattening; higher idle RAM (~2.9–3.0G class).");
            }
            if (p.minor == 16) {
                return new Profile("1.16", 1.25, 2.8, 165, 1.25,
                        "1.16 increases CPU pressure; ~3.1G idle class on unconstrained heaps.");
            }
            if (p.minor == 17) {
                return new Profile("1.17", 1.35, 3.0, 175, 1.30,
                        "1.17+ caves/height increase memory & gen cost.");
            }
            if (p.minor == 18) {
                return new Profile("1.18", 1.40, 3.2, 185, 1.35,
                        "1.18 worldgen rewrite — heavier chunk generation.");
            }
            if (p.minor == 19) {
                return new Profile("1.19", 1.45, 3.3, 190, 1.40,
                        "1.19+ feature/deep dark overhead on top of 1.18 gen.");
            }
            if (p.minor == 20) {
                return new Profile("1.20", 1.50, 3.2, 160, 1.45,
                        "Modern Paper baseline; per-player RAM is tiered (overlap), not flat 200MB.");
            }
            if (p.minor >= 21) {
                return new Profile("1.21+", 1.55, 3.4, 155, 1.50,
                        "1.21+ denser content; Paper ~100 players often ~12–16G at VD~10 (tiered model).");
            }
        }

        // Year.drop scheme (25.x / 26.x …)
        if (p.major >= 25) {
            double drop = p.minor + p.patch * 0.1;
            double cost = 1.60 + Math.min(0.25, drop * 0.03);
            return new Profile(p.major + "." + p.minor, cost, 3.5 + drop * 0.05,
                    150 + drop, 1.55 + Math.min(0.2, drop * 0.02),
                    "Year.drop releases need modern Java; RAM uses tiered per-player costs.");
        }

        return new Profile("other", 1.40, 3.0, 180, 1.30, "Unmapped version family — conservative mid cost.");
    }
}
