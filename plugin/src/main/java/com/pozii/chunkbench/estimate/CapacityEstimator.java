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
 * Scores how well THIS machine can serve the wizard target player count.
 * Uses tiered RAM (overlapping chunks) and a floored version-cost score.
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

        double cpuClassMul = "high".equals(inputs.cpuClass) ? 1.15
                : "low".equals(inputs.cpuClass) ? 0.80 : 1.0;
        double effectiveCores = inputs.cpuCores * cpuClassMul;

        double requiredRam = RamModel.requiredRamGb(ver, inputs.targetPlayers, view, pluginFactor, paperLike);

        double ramCap = RamModel.estimatePlayersForRam(ver, inputs.ramGb, view, pluginFactor, paperLike);
        // Single-thread sensitive: ~8–14 players per effective core depending on version weight
        double cpuCap = Math.max(1, (effectiveCores / Math.max(0.7, ver.cpuWeight)) * 11.0);
        double softMul = paperLike ? 1.12 : (scan.hasSpigotConfig ? 1.05 : 1.0);
        double expected = Math.min(ramCap, cpuCap) * softMul * (0.78 + 0.22 * (jvm.startupScore / 100.0));
        double low = Math.max(1, expected * 0.78);
        double high = Math.max(low + 1, expected * 1.22);

        int ramScore = ratioScore(inputs.ramGb, requiredRam);
        int cpuNeeded = (int) Math.ceil(Math.max(1, inputs.targetPlayers / 14.0) * ver.cpuWeight);
        int cpuScore = ratioScore(effectiveCores, Math.max(1, cpuNeeded));

        // Version score: base floor + soft penalty (never wipe capacity to 0)
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
        } else if (loadRatio <= 1.20 && overallScore >= 52) {
            verdict = "TIGHT";
        } else {
            verdict = "FAIL";
        }

        List<String> recs = new ArrayList<String>();
        if (inputs.ramGb + 0.15 < requiredRam) {
            recs.add(String.format(Locale.US,
                    "Raise heap toward ~%.1fG for %d players at view-distance %d on %s (tiered model).",
                    requiredRam, inputs.targetPlayers, view, ver.band));
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
        if (ver.costFactor >= 1.45 && inputs.ramGb < 6 && inputs.targetPlayers >= 20) {
            recs.add("Modern versions (" + ver.band + ") still need more baseline RAM than 1.8–1.12, but not a linear 200MB×players tax.");
        }
        if (recs.isEmpty()) {
            recs.add("Stack looks balanced for the stated target — still validate under real peak play.");
        }

        String summary = String.format(Locale.US,
                "Chunk %d/100 · Speed %d/100 · Need ~%.1fG for %d @ VD %d on %s",
                chunkScore, overallSpeedScore, requiredRam, inputs.targetPlayers, view, mc);

        return new BenchResult(inputs, mc, ver, overallScore, chunkScore, overallSpeedScore,
                ramScore, cpuScore, versionCostScore, pluginScore, startupScore, headroomScore,
                requiredRam, low, high, verdict, summary, recs,
                scan, startup, jvm, runtime, hardware);
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
        return clamp((int) Math.round(r * 68));
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
