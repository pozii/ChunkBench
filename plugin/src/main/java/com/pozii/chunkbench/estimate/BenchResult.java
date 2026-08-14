package com.pozii.chunkbench.estimate;

import com.pozii.chunkbench.sample.HardwareDetector;
import com.pozii.chunkbench.sample.RuntimeSampler;
import com.pozii.chunkbench.scan.JvmFlagAnalyzer;
import com.pozii.chunkbench.scan.ScanResult;
import com.pozii.chunkbench.scan.StartupScriptParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BenchResult {
    public final BenchInputs inputs;
    public final String mcVersion;
    public final VersionResourceCatalog.Profile versionProfile;
    public final int overallScore;
    public final int chunkScore;
    public final int overallSpeedScore;
    public final int ramScore;
    public final int cpuScore;
    public final int versionCostScore;
    public final int pluginScore;
    public final int startupScore;
    public final int headroomScore;
    public final double requiredRamGb;
    public final double expectedPlayersLow;
    public final double expectedPlayersHigh;
    public final String verdict;
    public final String chatSummary;
    public final List<String> recommendations;
    public final ScanResult scan;
    public final StartupScriptParser.StartupInfo startup;
    public final JvmFlagAnalyzer.JvmAnalysis jvm;
    public final RuntimeSampler.Sample runtime;
    public final HardwareDetector.Snapshot hardware;

    public BenchResult(BenchInputs inputs, String mcVersion, VersionResourceCatalog.Profile versionProfile,
                       int overallScore, int chunkScore, int overallSpeedScore,
                       int ramScore, int cpuScore, int versionCostScore,
                       int pluginScore, int startupScore, int headroomScore,
                       double requiredRamGb, double expectedPlayersLow, double expectedPlayersHigh,
                       String verdict, String chatSummary, List<String> recommendations,
                       ScanResult scan, StartupScriptParser.StartupInfo startup,
                       JvmFlagAnalyzer.JvmAnalysis jvm, RuntimeSampler.Sample runtime,
                       HardwareDetector.Snapshot hardware) {
        this.inputs = inputs;
        this.mcVersion = mcVersion;
        this.versionProfile = versionProfile;
        this.overallScore = overallScore;
        this.chunkScore = chunkScore;
        this.overallSpeedScore = overallSpeedScore;
        this.ramScore = ramScore;
        this.cpuScore = cpuScore;
        this.versionCostScore = versionCostScore;
        this.pluginScore = pluginScore;
        this.startupScore = startupScore;
        this.headroomScore = headroomScore;
        this.requiredRamGb = requiredRamGb;
        this.expectedPlayersLow = expectedPlayersLow;
        this.expectedPlayersHigh = expectedPlayersHigh;
        this.verdict = verdict;
        this.chatSummary = chatSummary;
        this.recommendations = recommendations;
        this.scan = scan;
        this.startup = startup;
        this.jvm = jvm;
        this.runtime = runtime;
        this.hardware = hardware;
    }
}
