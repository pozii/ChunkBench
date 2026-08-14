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
 * Scores how well THIS machine (RAM/CPU/version/plugins/startup) can serve the
 * wizard target player count — focusing on chunk load capacity and overall speed.
 *
 * Overall score is out of 100 for that player target (not a vanity "newer = better").
 * Heavier MC versions raise required resources (research-backed cost factors).
 */
public final class CapacityEstimator {

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
            // Geometric mean already applied via pow; dampen extreme stacks
            pluginFactor = 0.85 + (pluginFactor * 0.15) + Math.min(0.35, pluginCount * 0.008);
        }

        double view = Math.max(4, runtime.viewDistance);
        double viewFactor = Math.pow(view / 8.0, 1.15);

        double cpuClassMul = "high".equals(inputs.cpuClass) ? 1.15
                : "low".equals(inputs.cpuClass) ? 0.80 : 1.0;
        double effectiveCores = inputs.cpuCores * cpuClassMul;

        // Required RAM for target players on this version
        double requiredRam = (ver.baselineRamGb + (inputs.targetPlayers * ver.ramPerPlayerMb) / 1024.0)
                * pluginFactor * Math.max(0.85, Math.min(1.6, viewFactor / 1.0));
        requiredRam *= (0.85 + 0.15 * ver.costFactor);

        // Soft player capacity from RAM / CPU given version cost
        double ramCap = Math.max(1, (inputs.ramGb / Math.max(0.5, ver.baselineRamGb) - 1.0)
                * (1024.0 / ver.ramPerPlayerMb) / pluginFactor / viewFactor);
        double cpuCap = Math.max(1, (effectiveCores / ver.cpuWeight) * (10.0 / Math.max(0.7, ver.costFactor)));
        // Paper-ish software bonus
        double softMul = scan.hasPaperConfig ? 1.12 : (scan.hasSpigotConfig ? 1.05 : 1.0);
        double expected = Math.min(ramCap, cpuCap) * softMul * (0.75 + 0.25 * (jvm.startupScore / 100.0));
        double low = expected * 0.75;
        double high = expected * 1.20;

        int ramScore = ratioScore(inputs.ramGb, requiredRam);
        int cpuNeeded = (int) Math.ceil(inputs.targetPlayers / 12.0 * ver.cpuWeight * ver.costFactor);
        int cpuScore = ratioScore(effectiveCores, Math.max(1, cpuNeeded));

        // Version cost score: how punishing is this version for the given box?
        // Lower cost versions score higher for same hardware/players.
        double versionBurden = ver.costFactor * (requiredRam / Math.max(0.5, inputs.ramGb));
        int versionCostScore = clamp((int) Math.round(100 - (versionBurden - 0.8) * 55));

        int pluginScore = clamp((int) Math.round(100 - (pluginFactor - 1.0) * 120 - Math.max(0, pluginCount - 15) * 1.5));
        int startupScore = jvm.startupScore;

        int headroomScore = 70;
        if (runtime.tps > 0) {
            headroomScore = clamp((int) Math.round((runtime.tps / 20.0) * 100));
            if (runtime.mspt > 0) {
                headroomScore = clamp((int) Math.round(100 - Math.max(0, runtime.mspt - 20) * 2.5));
            }
        }

        // Chunk score: CPU + startup async friendliness + view distance + version gen cost + RAM headroom
        double chunkRaw = 0.35 * cpuScore
                + 0.20 * ramScore
                + 0.15 * startupScore
                + 0.15 * versionCostScore
                + 0.15 * (100 - Math.min(60, (view - 6) * 6));
        if (scan.hasPaperConfig) {
            chunkRaw += 4;
        }
        int chunkScore = clamp((int) Math.round(chunkRaw));

        // Overall speed for THIS target player count
        double loadRatio = inputs.targetPlayers / Math.max(1.0, expected);
        int targetFit = clamp((int) Math.round(100 - Math.max(0, loadRatio - 0.7) * 90));

        double overallRaw = 0.28 * chunkScore
                + 0.22 * targetFit
                + 0.15 * ramScore
                + 0.12 * cpuScore
                + 0.10 * versionCostScore
                + 0.08 * pluginScore
                + 0.05 * startupScore;
        // Blend a bit of live headroom when available
        if (runtime.tps > 0) {
            overallRaw = overallRaw * 0.9 + headroomScore * 0.1;
        }
        int overallSpeedScore = clamp((int) Math.round(overallRaw));
        int overallScore = overallSpeedScore;

        String verdict;
        if (loadRatio <= 0.85 && overallScore >= 75) {
            verdict = "PASS";
        } else if (loadRatio <= 1.15 && overallScore >= 55) {
            verdict = "TIGHT";
        } else {
            verdict = "FAIL";
        }

        List<String> recs = new ArrayList<String>();
        if (inputs.ramGb < requiredRam) {
            recs.add(String.format(Locale.US,
                    "Raise heap toward ~%.1fG for %d players on %s (cost factor %.2f).",
                    requiredRam, inputs.targetPlayers, ver.band, ver.costFactor));
        }
        if (jvm.aikarPresent < 8) {
            recs.add("Apply Aikar-style G1 flags (or panel equivalent); bare/vanilla launches score poorly on startup.");
        }
        if (view > 10) {
            recs.add("Lower view-/simulation-distance to improve chunk throughput for the target player count.");
        }
        if (pluginFactor > 1.25) {
            recs.add("Plugin set looks heavy — profile with spark; trim entity/NPC/map plugins if chunk lag appears.");
        }
        if (ver.costFactor >= 1.45 && inputs.ramGb < 6 && inputs.targetPlayers >= 20) {
            recs.add("Modern versions (" + ver.band + ") need more baseline RAM than 1.8–1.12 for the same player count.");
        }
        if (recs.isEmpty()) {
            recs.add("Stack looks balanced for the stated target — still validate under real peak play.");
        }

        String summary = String.format(Locale.US,
                "Chunk %d/100 · Speed %d/100 · Need ~%.1fG for %d on %s",
                chunkScore, overallSpeedScore, requiredRam, inputs.targetPlayers, mc);

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
        if (r >= 1.35) {
            return 100;
        }
        if (r >= 1.0) {
            return clamp((int) Math.round(75 + (r - 1.0) * 70));
        }
        if (r >= 0.7) {
            return clamp((int) Math.round(45 + (r - 0.7) * 100));
        }
        return clamp((int) Math.round(r * 60));
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
