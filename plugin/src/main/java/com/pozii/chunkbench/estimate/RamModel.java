package com.pozii.chunkbench.estimate;

/**
 * Tiered / diminishing-returns RAM model for Paper-class servers.
 *
 * Linear ~200MB/player overestimates large populations because chunk regions
 * overlap as player count grows. Calibrated toward community guidance that
 * ~100 players on Paper 1.21+ with view-distance ~10 often sit near 12–16G
 * (not 30G+).
 */
public final class RamModel {

    private RamModel() {
    }

    /**
     * Marginal MB for the n-th player (1-based), before view-distance scaling.
     * Tiered so later players cost less than the first cohort.
     */
    public static double marginalMbForPlayer(int playerIndex, double baseMbPerPlayer) {
        if (playerIndex <= 10) {
            return baseMbPerPlayer;
        }
        if (playerIndex <= 30) {
            return baseMbPerPlayer * 0.70;
        }
        if (playerIndex <= 60) {
            return baseMbPerPlayer * 0.48;
        }
        if (playerIndex <= 100) {
            return baseMbPerPlayer * 0.34;
        }
        return baseMbPerPlayer * 0.26;
    }

    public static double viewFactor(int viewDistance) {
        int vd = Math.max(2, Math.min(32, viewDistance));
        // Quadratic-ish chunk area around players vs reference VD 8
        return Math.pow(vd / 8.0, 1.25);
    }

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
        double soft = paperLike ? 0.88 : 1.0;
        double vf = viewFactor(viewDistance);
        double costSoft = 0.90 + 0.10 * ver.costFactor;
        double gb = (ver.baselineRamGb + sumMb / 1024.0) * vf * pluginFactor * soft * costSoft;
        // Floor / ceiling sanity
        if (gb < ver.baselineRamGb) {
            gb = ver.baselineRamGb;
        }
        return gb;
    }

    /** Invert the tiered model roughly: how many players fit in allocated RAM. */
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
        return Math.max(1, best);
    }
}
