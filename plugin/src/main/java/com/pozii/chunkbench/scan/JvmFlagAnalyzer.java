package com.pozii.chunkbench.scan;

import com.pozii.chunkbench.sample.HardwareDetector;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class JvmFlagAnalyzer {

    /** Well-known Aikar / modern MC tuning flags (presence checklist). */
    private static final List<String> AIKAR_FLAGS = Arrays.asList(
            "UseG1GC",
            "ParallelRefProcEnabled",
            "MaxGCPauseMillis",
            "UnlockExperimentalVMOptions",
            "DisableExplicitGC",
            "AlwaysPreTouch",
            "G1NewSizePercent",
            "G1MaxNewSizePercent",
            "G1HeapRegionSize",
            "G1ReservePercent",
            "G1HeapWastePercent",
            "G1MixedGCCountTarget",
            "InitiatingHeapOccupancyPercent",
            "G1MixedGCLiveThresholdPercent",
            "G1RSetUpdatingPauseTimePercent",
            "UsingFastUnorderedTimeStamps"
    );

    public static final class JvmAnalysis {
        public final List<String> liveArgs;
        public final String scriptCommand;
        public final boolean bareJarLaunch;
        public final boolean usesG1;
        public final double scriptXmxGb;
        public final double scriptXmsGb;
        public final double liveXmxGb;
        public final int aikarPresent;
        public final int aikarTotal;
        public final int startupScore;
        public final List<String> findings;
        public final Map<String, Boolean> aikarChecklist;

        public JvmAnalysis(List<String> liveArgs, String scriptCommand, boolean bareJarLaunch,
                           boolean usesG1, double scriptXmxGb, double scriptXmsGb, double liveXmxGb,
                           int aikarPresent, int aikarTotal, int startupScore,
                           List<String> findings, Map<String, Boolean> aikarChecklist) {
            this.liveArgs = liveArgs;
            this.scriptCommand = scriptCommand;
            this.bareJarLaunch = bareJarLaunch;
            this.usesG1 = usesG1;
            this.scriptXmxGb = scriptXmxGb;
            this.scriptXmsGb = scriptXmsGb;
            this.liveXmxGb = liveXmxGb;
            this.aikarPresent = aikarPresent;
            this.aikarTotal = aikarTotal;
            this.startupScore = startupScore;
            this.findings = findings;
            this.aikarChecklist = aikarChecklist;
        }
    }

    public JvmAnalysis analyze(StartupScriptParser.StartupInfo startup) {
        List<String> live = new ArrayList<String>(ManagementFactory.getRuntimeMXBean().getInputArguments());
        String joinedLive = join(live);
        String script = startup.javaCommand;
        String joinedAll = (script == null ? "" : script) + " " + joinedLive;

        Map<String, Boolean> checklist = new LinkedHashMap<String, Boolean>();
        int present = 0;
        for (String flag : AIKAR_FLAGS) {
            boolean ok = containsFlag(joinedAll, flag);
            checklist.put(flag, Boolean.valueOf(ok));
            if (ok) {
                present++;
            }
        }
        boolean g1 = containsFlag(joinedAll, "UseG1GC") || joinedAll.contains("+UseG1GC");
        double scriptXmx = HardwareDetector.parseMem(StartupScriptParser.findXmx(script));
        double scriptXms = HardwareDetector.parseMem(StartupScriptParser.findXms(script));
        double liveXmx = HardwareDetector.parseMem(findLiveXmx(live));
        if (liveXmx <= 0) {
            liveXmx = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0 * 1024.0);
        }

        List<String> findings = new ArrayList<String>();
        if (startup.bareJarLaunch) {
            findings.add("No start.bat/.sh detected — treating launch as bare jar / panel-managed (vanilla-style flags likely).");
        }
        if (!g1) {
            findings.add("G1GC not detected — recommended for Minecraft servers.");
        }
        if (scriptXmx > 0 && liveXmx > 0 && Math.abs(scriptXmx - liveXmx) > 0.4) {
            findings.add(String.format(Locale.US,
                    "Script Xmx (%.1fG) differs from live heap (%.1fG).", scriptXmx, liveXmx));
        }
        if (scriptXmx > 0 && scriptXms > 0 && Math.abs(scriptXmx - scriptXms) > 0.1) {
            findings.add("Xms != Xmx — heap resizing can cause GC stutter; prefer equal values.");
        }
        if (present <= 3 && !startup.bareJarLaunch) {
            findings.add("Few Aikar-class flags present — startup tuning is weak.");
        }
        if (present >= 10) {
            findings.add("Strong Aikar-style flag set detected.");
        }

        int score = 40;
        score += (int) Math.round(40.0 * present / (double) AIKAR_FLAGS.size());
        if (g1) {
            score += 10;
        }
        if (scriptXmx > 0 && scriptXms > 0 && Math.abs(scriptXmx - scriptXms) < 0.15) {
            score += 5;
        }
        if (startup.bareJarLaunch && present < 4) {
            score = Math.min(score, 45);
        }
        if (score > 100) {
            score = 100;
        }
        if (score < 0) {
            score = 0;
        }

        return new JvmAnalysis(live, script, startup.bareJarLaunch, g1, scriptXmx, scriptXms, liveXmx,
                present, AIKAR_FLAGS.size(), score, findings, checklist);
    }

    private static String findLiveXmx(List<String> args) {
        for (String a : args) {
            if (a.regionMatches(true, 0, "-Xmx", 0, 4)) {
                return a.substring(4);
            }
        }
        return null;
    }

    private static boolean containsFlag(String hay, String flag) {
        return hay.toLowerCase(Locale.US).contains(flag.toLowerCase(Locale.US));
    }

    private static String join(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(args.get(i));
        }
        return sb.toString();
    }
}
