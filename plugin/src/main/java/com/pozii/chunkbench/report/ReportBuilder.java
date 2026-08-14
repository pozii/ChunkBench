package com.pozii.chunkbench.report;

import com.pozii.chunkbench.estimate.BenchResult;
import com.pozii.chunkbench.scan.ServerRootScanner;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class ReportBuilder {

    public String build(BenchResult r) {
        StringBuilder sb = new StringBuilder(8192);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));

        sb.append("=== ChunkBench Report ===\n");
        sb.append("Author: pozii\n");
        sb.append("Generated: ").append(df.format(new Date())).append('\n');
        sb.append("License: PolyForm Shield 1.0.0 (contributions welcome; no competing products)\n");
        sb.append('\n');

        sb.append("--- Target ---\n");
        sb.append("Players: ").append(r.inputs.targetPlayers)
                .append(" (").append(r.inputs.playersSource).append(")\n");
        sb.append("View distance: ").append(r.inputs.viewDistance).append(" chunks (")
                .append(r.inputs.viewDistanceSource).append(")\n");
        sb.append("RAM allocated: ").append(fmt(r.inputs.ramGb)).append("G\n");
        sb.append("CPU: ").append(r.inputs.cpuCores).append(" cores (")
                .append(r.inputs.cpuClass).append(")\n");
        sb.append('\n');

        sb.append("--- Detected ---\n");
        sb.append("Minecraft: ").append(r.mcVersion).append(" [band ").append(r.versionProfile.band).append("]\n");
        sb.append("Version cost factor: ").append(fmt(r.versionProfile.costFactor))
                .append(" | baseline ").append(fmt(r.versionProfile.baselineRamGb))
                .append("G | tiered base ~").append((int) r.versionProfile.ramPerPlayerMb)
                .append("MB/player (diminishing)\n");
        sb.append("Notes: ").append(r.versionProfile.notes).append('\n');
        sb.append("Server: ").append(r.runtime.serverVersion).append('\n');
        sb.append("Bukkit: ").append(r.runtime.bukkitVersion).append('\n');
        sb.append("Runtime view-distance API: ").append(r.runtime.viewDistance).append('\n');
        sb.append("Online now: ").append(r.runtime.onlinePlayers).append('\n');
        if (r.runtime.tps > 0) {
            sb.append("TPS: ").append(fmt(r.runtime.tps));
            if (r.runtime.mspt > 0) {
                sb.append(" | MSPT~ ").append(fmt(r.runtime.mspt));
            }
            sb.append('\n');
        }
        sb.append("Heap used/max: ").append(r.runtime.usedHeapMb).append("M / ")
                .append(r.runtime.maxHeapMb).append("M\n");
        sb.append("Host RAM: ").append(r.hardware.hostRamGb > 0 ? fmt(r.hardware.hostRamGb) + "G" : "unknown")
                .append(" | CPU model: ")
                .append(r.hardware.cpuModel.isEmpty() ? "unknown" : r.hardware.cpuModel).append('\n');
        sb.append("OS: ").append(r.hardware.osName).append('\n');
        sb.append('\n');

        sb.append("--- Startup / JVM ---\n");
        if (r.startup.scriptFound) {
            sb.append("Script: ").append(r.startup.scriptPath).append('\n');
            sb.append("Command: ").append(r.startup.javaCommand).append('\n');
        } else {
            sb.append("Script: none (bare jar / panel launch)\n");
        }
        sb.append("G1GC: ").append(r.jvm.usesG1 ? "yes" : "no")
                .append(" | Aikar flags: ").append(r.jvm.aikarPresent).append('/')
                .append(r.jvm.aikarTotal).append('\n');
        sb.append("Live Xmx: ").append(fmt(r.jvm.liveXmxGb)).append("G");
        if (r.jvm.scriptXmxGb > 0) {
            sb.append(" | Script Xmx: ").append(fmt(r.jvm.scriptXmxGb)).append("G");
        }
        sb.append('\n');
        for (String f : r.jvm.findings) {
            sb.append(" - ").append(f).append('\n');
        }
        sb.append("Aikar checklist:\n");
        for (Map.Entry<String, Boolean> e : r.jvm.aikarChecklist.entrySet()) {
            sb.append("  [").append(e.getValue() ? "x" : " ").append("] ").append(e.getKey()).append('\n');
        }
        sb.append('\n');

        sb.append("--- Scan (worlds excluded) ---\n");
        sb.append("Root: ").append(r.scan.rootPath).append('\n');
        sb.append("Files seen: ").append(r.scan.filesSeen).append('\n');
        sb.append("Startup scripts: ").append(r.scan.startupScripts.size()).append('\n');
        sb.append("Server jars: ").append(join(r.scan.serverJars)).append('\n');
        sb.append("Paper/Spigot/Bukkit configs: ")
                .append(r.scan.hasPaperConfig).append('/')
                .append(r.scan.hasSpigotConfig).append('/')
                .append(r.scan.hasBukkitConfig).append('\n');
        sb.append("Plugins (").append(r.scan.plugins.size()).append("):\n");
        for (ServerRootScanner.PluginJar p : r.scan.plugins) {
            sb.append("  - ").append(p.fileName).append('\n');
        }
        for (String n : r.scan.notes) {
            sb.append("Note: ").append(n).append('\n');
        }
        sb.append('\n');

        sb.append("--- Scores for ").append(r.inputs.targetPlayers).append(" players (/100) ---\n");
        sb.append("OVERALL (chunk load + speed fit): ").append(r.overallScore).append("/100\n");
        sb.append("  Chunk loading:     ").append(r.chunkScore).append('\n');
        sb.append("  Overall speed:     ").append(r.overallSpeedScore).append('\n');
        sb.append("  RAM fit:           ").append(r.ramScore).append('\n');
        sb.append("  CPU fit:           ").append(r.cpuScore).append('\n');
        sb.append("  Version cost fit:  ").append(r.versionCostScore)
                .append("  (higher = version is less punishing on this box)\n");
        sb.append("  Plugin overhead:   ").append(r.pluginScore).append('\n');
        sb.append("  Startup/JVM:       ").append(r.startupScore).append('\n');
        sb.append("  Live headroom:     ").append(r.headroomScore).append('\n');
        sb.append("Required RAM estimate: ~").append(fmt(r.requiredRamGb)).append("G\n");
        sb.append("Expected capacity band: ")
                .append((int) Math.round(r.expectedPlayersLow)).append('-')
                .append((int) Math.round(r.expectedPlayersHigh)).append(" players\n");
        sb.append("Verdict: ").append(r.verdict).append('\n');
        sb.append('\n');

        sb.append("--- Recommendations ---\n");
        for (String rec : r.recommendations) {
            sb.append(" - ").append(rec).append('\n');
        }
        sb.append('\n');
        sb.append("Disclaimer: Worlds were not scanned. Estimates are model-based ");
        sb.append("(version resource research + RAM/CPU/plugins/startup). ");
        sb.append("Farms, redstone, and custom plugins can dominate real capacity.\n");
        sb.append("ChunkBench by pozii\n");
        return sb.toString();
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) {
            return String.valueOf((int) Math.rint(v));
        }
        return String.format(Locale.US, "%.2f", v);
    }

    private static String join(java.util.List<String> list) {
        if (list.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
