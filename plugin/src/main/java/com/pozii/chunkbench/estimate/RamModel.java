package com.pozii.chunkbench.estimate;

/**
 * Tiered / diminishing-returns RAM model for Paper-class servers.
 *
 * Calibrated so ~4.5G Paper 1.21+ at view-distance ~8–10 lands near ~15–25
 * players, and ~12–16G near ~100 players — not linear 200MB×N.
 */
public final class RamModel {

    private RamModel() {
    }

    public static double marginalMbForPlayer(int playerIndex, double baseMbPerPlayer) {
        if (playerIndex <= 10) {
            return baseMbPerPlayer * 0.85;
        }
        if (playerIndex <= 25) {
            return baseMbPerPlayer * 0.55;
        }
        if (playerIndex <= 50) {
            return baseMbPerPlayer * 0.38;
        }
        if (playerIndex <= 100) {
            return baseMbPerPlayer * 0.28;
        }
        return baseMbPerPlayer * 0.22;
    }

    public static double viewFactor(int viewDistance) {
        int vd = Math.max(2, Math.min(32, viewDistance));
        // Milder than pure area scaling — Paper no-tick / send distance softens cost
        return Math.pow(vd / 8.0, 0.85);
    }

    /**
     * Baseline is only lightly affected by view-distance; player chunk load scales more.
     */
    public static double requiredRamGb(VersionResourceCatalog.Profile ver,
                                      int players,
                                      int viewDistance,
                                      double pluginFactor,
                                      boolean paperLike) {
        double sumMb = 0;
        int n = Math.max(0, players);
        for (int i = 1; i <= n; i++) {
            sumMb += marginalMbForPlayer(i, ver.ramPerPlayerMb);
        }
        double vf = viewFactor(viewDistance);
        double soft = paperLike ? 0.82 : 1.0;
        double costSoft = 0.92 + 0.08 * ver.costFactor;
        // Paper/Purpur effective idle heap is well below unconstrained DFU anecdotes
        double baseline = ver.baselineRamGb * (paperLike ? 0.48 : 0.70);
        baseline *= (0.90 + 0.10 * vf);
        double variable = (sumMb / 1024.0) * vf * pluginFactor * soft * costSoft;
        double gb = baseline + variable;
        if (gb < 1.0) {
            gb = 1.0;
        }
        return gb;
    }

    public static double estimatePlayersForRam(VersionResourceCatalog.Profile ver,
                                               double ramGb,
                                               int viewDistance,
                                               double pluginFactor,
                                               boolean paperLike) {
        if (ramGb <= 0) {
            return 1;
        }
        int lo = 1;
        int hi = 500;
        int best = 1;
        while (lo <= hi) {
            int mid = (lo + hi) / 2;
            double need = requiredRamGb(ver, mid, viewDistance, pluginFactor, paperLike);
            if (need <= ramGb) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        // Empirical floor: small heaps still host more than binary search may imply
        // when plugins/VD are mild (matches hosting guides for 4G Paper SMPs).
        double floor = empiricalPlayerFloor(ramGb, paperLike);
        return Math.max(best, floor);
    }

    /**
     * Min expected players from allocated heap alone (Paper-oriented).
     * 4.5G → ~18, 8G → ~40, 12G → ~70, 16G → ~100 class.
     */
    public static double empiricalPlayerFloor(double ramGb, boolean paperLike) {
        double mul = paperLike ? 1.0 : 0.75;
        if (ramGb < 2.0) {
            return 3 * mul;
        }
        if (ramGb < 3.5) {
            return 8 * mul;
        }
        if (ramGb < 5.0) {
            return 18 * mul;
        }
        if (ramGb < 7.0) {
            return 28 * mul;
        }
        if (ramGb < 9.0) {
            return 42 * mul;
        }
        if (ramGb < 12.0) {
            return 60 * mul;
        }
        if (ramGb < 16.0) {
            return 85 * mul;
        }
        return 110 * mul;
    }
}
