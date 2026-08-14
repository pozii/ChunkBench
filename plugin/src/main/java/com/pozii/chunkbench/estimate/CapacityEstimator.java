package com.pozii.chunkbench.estimate;

import com.pozii.chunkbench.catalog.PluginWeights;
import com.pozii.chunkbench.sample.HardwareDetector;
import com.pozii.chunkbench.sample.RuntimeSampler;
import com.pozii.chunkbench.scan.JvmFlagAnalyzer;
import com.pozii.chunkbench.scan.ScanResult;
import com.pozii.chunkbench.scan.ServerRootScanner;
import com.pozii.chunkbench.scan.StartupScriptParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scores target fit separately from hardware capacity band.
 * A FAIL for 100 players with 4.5G still reports a realistic ~15–25 band.
 */
public final class CapacityEstimator {

    private static final int VERSION_SCORE_FLOOR = 25;

    public BenchResult estimate(BenchInputs inputs,
                                ScanResult scan,
                                StartupScriptParser.StartupInfo startup,
                                JvmFlagAnalyzer.JvmAnalysis jvm,
                                RuntimeSampler.Sample runtime,
                                HardwareDetector.Snapshot hardware) {

        String mc = runtime.mcVersion;
        VersionResourceCatalog.Profile ver = VersionResourceCatalog.forVersion(mc);

        double pluginFactor = 1.0;
        int pluginCount = scan.plugins.size();
        for (ServerRootScanner.PluginJar pj : scan.plugins) {
            pluginFactor *= Math.pow(PluginWeights.weightFor(pj.name), 1.0 / Math.max(1, pluginCount));
        }
        if (pluginCount == 0) {
            pluginFactor = 0.95;
        } else {
            pluginFactor = 0.85 + (pluginFactor * 0.15) + Math.min(0.35, pluginCount * 0.008);
        }

        int view = Math.max(2, Math.min(32, inputs.viewDistance));
        boolean paperLike = scan.hasPaperConfig;

        double cpuClassMul = "high".equals(inputs.cpuClass) ? 1.20
                : "low".equals(inputs.cpuClass) ? 0.80 : 1.0;
        // i5-class 12-thread boxes: treat logical cores with soft diminishing returns
        double effectiveCores = Math.min(inputs.cpuCores, 8)
                + Math.max(0, inputs.cpuCores - 8) * 0.45;
        effectiveCores *= cpuClassMul;

        double requiredRam = RamModel.requiredRamGb(ver, inputs.targetPlayers, view, pluginFactor, paperLike);

        double ramCap = RamModel.estimatePlayersForRam(ver, inputs.ramGb, view, pluginFactor, paperLike);
        // ~2 players per strong core-equivalent on modern Paper after weight
        double cpuCap = Math.max(8, (effectiveCores / Math.max(0.75, ver.cpuWeight)) * 2.4 * (paperLike ? 1.15 : 1.0));

        double softMul = paperLike ? 1.10 : (scan.hasSpigotConfig ? 1.04 : 1.0);
        double tune = 0.82 + 0.18 * (jvm.startupScore / 100.0);

        // Capacity band: do NOT take a harsh min that collapses to 1–2 when target RAM fails.
        // Blend RAM & CPU with floors; band describes what the box can host, not the target gap.
        double blended = harmonicBlend(ramCap, cpuCap) * softMul * tune;
        double floor = RamModel.empiricalPlayerFloor(inputs.ramGb, paperLike);
        double expected = Math.max(blended, floor);
        // Keep CPU from being ignored: if CPU is the limiter, pull toward it gently
        expected = Math.min(expected, Math.max(cpuCap * softMul, floor));
        expected = Math.max(expected, floor);

        double low = Math.max(floor * 0.85, expected * 0.80);
        double high = Math.max(low + 2, expected * 1.25);
        // Widen band slightly for mid-size heaps (matches 15–25 style ranges)
        if (inputs.ramGb >= 3.5 && inputs.ramGb < 6.0) {
            low = Math.max(12, Math.min(low, 18));
            high = Math.max(high, 25);
            if (low > high) {
                low = high - 5;
            }
        }

        int ramScore = ratioScore(inputs.ramGb, requiredRam);
        int cpuNeeded = (int) Math.ceil(Math.max(1, inputs.targetPlayers / 16.0) * ver.cpuWeight);
        int cpuScore = ratioScore(effectiveCores, Math.max(1, cpuNeeded));

        double burden = ver.costFactor * (requiredRam / Math.max(0.75, inputs.ramGb));
        int versionCostScore = clamp(VERSION_SCORE_FLOOR
                + (int) Math.round((100 - VERSION_SCORE_FLOOR) * Math.max(0, Math.min(1, 1.35 - 0.35 * burden))));

        int pluginScore = clamp((int) Math.round(100 - (pluginFactor - 1.0) * 120 - Math.max(0, pluginCount - 15) * 1.5));
        int startupScore = jvm.startupScore;

        int headroomScore = 70;
        if (runtime.tps > 0) {
            headroomScore = clamp((int) Math.round((runtime.tps / 20.0) * 100));
            if (runtime.mspt > 0) {
                headroomScore = clamp((int) Math.round(100 - Math.max(0, runtime.mspt - 20) * 2.5));
            }
        }

        double chunkRaw = 0.32 * cpuScore
                + 0.22 * ramScore
                + 0.14 * startupScore
                + 0.14 * versionCostScore
                + 0.18 * (100 - Math.min(55, Math.max(0, view - 6) * 5));
        if (paperLike) {
            chunkRaw += 4;
        }
        int chunkScore = clamp((int) Math.round(chunkRaw));

        // Target fit uses capacity band, not a crushed linear ratio
        double loadRatio = inputs.targetPlayers / Math.max(1.0, expected);
        int targetFit = clamp((int) Math.round(100 - Math.max(0, loadRatio - 0.7) * 85));

        double overallRaw = 0.28 * chunkScore
                + 0.22 * targetFit
                + 0.16 * ramScore
                + 0.12 * cpuScore
                + 0.08 * versionCostScore
                + 0.08 * pluginScore
                + 0.06 * startupScore;
        if (runtime.tps > 0) {
            overallRaw = overallRaw * 0.9 + headroomScore * 0.1;
        }
        int overallSpeedScore = clamp((int) Math.round(overallRaw));
        int overallScore = overallSpeedScore;

        String verdict;
        if (loadRatio <= 0.85 && overallScore >= 72) {
            verdict = "PASS";
        } else if (loadRatio <= 1.15 && overallScore >= 52) {
            verdict = "TIGHT";
        } else if (loadRatio > 1.15 && expected >= 12) {
            // Target too high, but box can still host a meaningful population
            verdict = "FAIL (target) / OK capacity ~" + (int) Math.round(low)
                    + "-" + (int) Math.round(high);
        } else {
            verdict = "FAIL";
        }

        List<String> recs = new ArrayList<String>();
        if (inputs.ramGb + 0.15 < requiredRam) {
            recs.add(String.format(Locale.US,
                    "Target %d players wants ~%.1fG at VD %d, but only %.1fG is allocated — "
                            + "expected comfortable capacity is ~%d-%d players, not the target.",
                    inputs.targetPlayers, requiredRam, view, inputs.ramGb,
                    (int) Math.round(low), (int) Math.round(high)));
        }
        if (jvm.aikarPresent < 8) {
            recs.add("Apply Aikar-style G1 flags (or panel equivalent); bare/vanilla launches score poorly on startup.");
        }
        if (view > 10 && inputs.targetPlayers >= 40) {
            recs.add("For large populations, Paper guides often use view-distance 6–8 (simulation lower); VD "
                    + view + " raises chunk RAM/CPU pressure.");
        }
        if (pluginFactor > 1.25) {
            recs.add("Plugin set looks heavy — profile with spark; trim entity/NPC/map plugins if chunk lag appears.");
        }
        if (recs.isEmpty()) {
            recs.add("Stack looks balanced for the stated target — still validate under real peak play.");
        }

        String summary = String.format(Locale.US,
                "Chunk %d/100 · Speed %d/100 · Need ~%.1fG for target %d · Band ~%d-%d",
                chunkScore, overallSpeedScore, requiredRam, inputs.targetPlayers,
                (int) Math.round(low), (int) Math.round(high));

        return new BenchResult(inputs, mc, ver, overallScore, chunkScore, overallSpeedScore,
                ramScore, cpuScore, versionCostScore, pluginScore, startupScore, headroomScore,
                requiredRam, low, high, verdict, summary, recs,
                scan, startup, jvm, runtime, hardware);
    }

    /** Soft blend: neither dimension alone can zero the other out. */
    private static double harmonicBlend(double a, double b) {
        double x = Math.max(1, a);
        double y = Math.max(1, b);
        return 2.0 * x * y / (x + y);
    }

    private static int ratioScore(double have, double need) {
        if (need <= 0) {
            return 100;
        }
        double r = have / need;
        if (r >= 1.25) {
            return 100;
        }
        if (r >= 1.0) {
            return clamp((int) Math.round(78 + (r - 1.0) * 88));
        }
        if (r >= 0.75) {
            return clamp((int) Math.round(52 + (r - 0.75) * 104));
        }
        // Soft floor so 30% coverage is not a literal ~20/100 death spiral only
        return clamp((int) Math.round(18 + r * 50));
    }

    private static int clamp(int v) {
        if (v < 0) {
            return 0;
        }
        if (v > 100) {
            return 100;
        }
        return v;
    }
}
