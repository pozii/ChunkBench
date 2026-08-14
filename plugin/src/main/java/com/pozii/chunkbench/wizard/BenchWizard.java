package com.pozii.chunkbench.wizard;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.estimate.BenchInputs;
import com.pozii.chunkbench.estimate.BenchResult;
import com.pozii.chunkbench.estimate.CapacityEstimator;
import com.pozii.chunkbench.mclogs.McLogsClient;
import com.pozii.chunkbench.report.ReportBuilder;
import com.pozii.chunkbench.sample.HardwareDetector;
import com.pozii.chunkbench.sample.RuntimeSampler;
import com.pozii.chunkbench.scan.JvmFlagAnalyzer;
import com.pozii.chunkbench.scan.ScanResult;
import com.pozii.chunkbench.scan.ServerRootScanner;
import com.pozii.chunkbench.scan.StartupScriptParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BenchWizard implements Listener {

    private enum Step {
        PLAYERS, RAM, CPU
    }

    private static final class Session {
        Step step = Step.PLAYERS;
        int targetPlayers;
        String playersSource;
        double ramGb;
        int cpuCores;
        String cpuClass = "mid";
    }

    private final ChunkBenchPlugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();
    private final Map<UUID, Long> lastRun = new ConcurrentHashMap<UUID, Long>();

    public BenchWizard(ChunkBenchPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBusy(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean tryCooldown(Player player, long cooldownMs) {
        Long last = lastRun.get(player.getUniqueId());
        return last == null || System.currentTimeMillis() - last >= cooldownMs;
    }

    public void start(Player player) {
        sessions.put(player.getUniqueId(), new Session());
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        msg(player, "&7ChunkBench by &bpozii&7 — auditing &fthis server only&7.");
        msg(player, "&eStep 1/3 — Target players");
        msg(player, "&7Type a &fnumber&7, or &fconfirm &7to use &fmax-players &7from server.properties.");
        msg(player, "&8(Type &7cancel &8to abort.)");
        if (hw.detectedXmxGb > 0) {
            msg(player, "&8Detected JVM heap: &7" + formatGb(hw.detectedXmxGb) + "G");
        }
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void cancelAll() {
        sessions.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String raw = event.getMessage() == null ? "" : event.getMessage().trim();
        if (raw.equalsIgnoreCase("cancel")) {
            sessions.remove(player.getUniqueId());
            msg(player, "&eBench cancelled.");
            return;
        }

        if (session.step == Step.PLAYERS) {
            handlePlayers(player, session, raw);
        } else if (session.step == Step.RAM) {
            handleRam(player, session, raw);
        } else if (session.step == Step.CPU) {
            handleCpu(player, session, raw);
        }
    }

    private void handlePlayers(Player player, Session session, String raw) {
        if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
            Integer max = readMaxPlayers();
            if (max == null || max <= 0) {
                msg(player, "&cCould not read max-players. Type a number.");
                return;
            }
            session.targetPlayers = max;
            session.playersSource = "server.properties max-players=" + max;
        } else {
            try {
                int n = Integer.parseInt(raw);
                if (n < 1 || n > 10000) {
                    msg(player, "&cEnter a player count between 1 and 10000.");
                    return;
                }
                session.targetPlayers = n;
                session.playersSource = "chat input";
            } catch (NumberFormatException ex) {
                msg(player, "&cInvalid number. Type a number or &fconfirm&c.");
                return;
            }
        }
        session.step = Step.RAM;
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        msg(player, "&aTarget players: &f" + session.targetPlayers + " &7(" + session.playersSource + ")");
        msg(player, "&eStep 2/3 — RAM allocated to this server (GB)");
        String host = hw.hostRamGb > 0 ? formatGb(hw.hostRamGb) + "G" : "unknown";
        String xmx = hw.detectedXmxGb > 0 ? formatGb(hw.detectedXmxGb) + "G" : "unknown";
        msg(player, "&7Detected JVM heap: &f" + xmx + " &7| Host RAM: &f" + host);
        msg(player, "&7Type GB (e.g. &f8&7) or &fconfirm &7to use detected Xmx.");
    }

    private void handleRam(Player player, Session session, String raw) {
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
            if (hw.detectedXmxGb <= 0) {
                msg(player, "&cNo Xmx detected. Type RAM in GB (e.g. 8).");
                return;
            }
            session.ramGb = hw.detectedXmxGb;
        } else {
            try {
                double gb = Double.parseDouble(raw.replace("G", "").replace("g", "").trim());
                if (gb < 0.5 || gb > 1024) {
                    msg(player, "&cEnter RAM between 0.5 and 1024 GB.");
                    return;
                }
                session.ramGb = gb;
            } catch (NumberFormatException ex) {
                msg(player, "&cInvalid RAM. Type a number or &fconfirm&c.");
                return;
            }
        }
        session.step = Step.CPU;
        msg(player, "&aRAM: &f" + formatGb(session.ramGb) + "G");
        msg(player, "&eStep 3/3 — CPU");
        msg(player, "&7Detected cores: &f" + hw.cpuCores
                + (hw.cpuModel.isEmpty() ? "" : " &7(" + hw.cpuModel + ")"));
        msg(player, "&7Type core count, or &fconfirm&7. Optional class: &flow &7/ &fmid &7/ &fhigh");
        msg(player, "&8Examples: &7confirm &8| &78 &8| &78 high");
    }

    private void handleCpu(final Player player, final Session session, String raw) {
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        String[] parts = raw.split("\\s+");
        try {
            if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
                session.cpuCores = Math.max(1, hw.cpuCores);
            } else if (parts.length == 1 && isClass(parts[0])) {
                session.cpuCores = Math.max(1, hw.cpuCores);
                session.cpuClass = parts[0].toLowerCase();
            } else {
                session.cpuCores = Integer.parseInt(parts[0]);
                if (session.cpuCores < 1 || session.cpuCores > 512) {
                    msg(player, "&cEnter cores between 1 and 512.");
                    return;
                }
                if (parts.length > 1 && isClass(parts[1])) {
                    session.cpuClass = parts[1].toLowerCase();
                }
            }
        } catch (NumberFormatException ex) {
            msg(player, "&cInvalid CPU input. Try &fconfirm&c, &f8&c, or &f8 high&c.");
            return;
        }

        sessions.remove(player.getUniqueId());
        lastRun.put(player.getUniqueId(), System.currentTimeMillis());
        msg(player, "&aCPU: &f" + session.cpuCores + " cores &7(" + session.cpuClass + ")");
        msg(player, "&bScanning this server (worlds excluded)...");

        final BenchInputs inputs = new BenchInputs(
                session.targetPlayers,
                session.playersSource,
                session.ramGb,
                session.cpuCores,
                session.cpuClass
        );

        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    runBench(player, inputs);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Bench failed: " + t.getMessage());
                    msg(player, "&cBench failed: &f" + String.valueOf(t.getMessage()));
                }
            }
        });
    }

    private void runBench(Player player, BenchInputs inputs) throws Exception {
        File root = plugin.getServer().getWorldContainer().getCanonicalFile().getParentFile();
        if (root == null) {
            root = new File(".").getCanonicalFile();
        }
        // worldContainer is often the root itself on Spigot
        File serverRoot = plugin.getServer().getWorldContainer().getCanonicalFile();
        if (new File(serverRoot, "server.properties").exists() || new File(serverRoot, "plugins").exists()) {
            root = serverRoot;
        } else if (serverRoot.getParentFile() != null
                && (new File(serverRoot.getParentFile(), "server.properties").exists()
                || new File(serverRoot.getParentFile(), "plugins").exists())) {
            root = serverRoot.getParentFile();
        }

        ScanResult scan = new ServerRootScanner(plugin).scan(root);
        StartupScriptParser.StartupInfo startup = new StartupScriptParser().parse(root);
        JvmFlagAnalyzer.JvmAnalysis jvm = new JvmFlagAnalyzer().analyze(startup);
        RuntimeSampler.Sample runtime = RuntimeSampler.sample(plugin.getServer());
        HardwareDetector.Snapshot hw = HardwareDetector.detect();

        BenchResult result = new CapacityEstimator().estimate(inputs, scan, startup, jvm, runtime, hw);
        String report = new ReportBuilder().build(result);
        File reportDir = new File(plugin.getDataFolder(), "reports");
        if (!reportDir.exists()) {
            reportDir.mkdirs();
        }
        File reportFile = new File(reportDir, "chunkbench-" + System.currentTimeMillis() + ".log");
        java.nio.file.Files.write(reportFile.toPath(), report.getBytes("UTF-8"));

        String url = null;
        if (plugin.getConfig().getBoolean("mclogs.enabled", true)) {
            url = new McLogsClient(plugin).upload(report, result);
        }

        final String finalUrl = url;
        final int score = result.overallScore;
        final String verdict = result.verdict;
        final String summary = result.chatSummary;
        final String reportPath = reportFile.getAbsolutePath();

        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }
                msg(player, "&aAudit complete.");
                msg(player, "&7Score for &f" + inputs.targetPlayers + " &7players: &b" + score + "/100");
                msg(player, "&7Verdict: &f" + verdict);
                msg(player, "&7" + summary);
                msg(player, "&7Saved: &f" + reportPath);
                if (finalUrl != null) {
                    msg(player, "&aReport: &f" + finalUrl);
                } else if (plugin.getConfig().getBoolean("mclogs.enabled", true)) {
                    msg(player, "&eUpload to mclo.gs failed — share the local report file.");
                }
            }
        });
    }

    private Integer readMaxPlayers() {
        try {
            File props = new File(plugin.getServer().getWorldContainer(), "server.properties");
            if (!props.exists()) {
                props = new File("server.properties");
            }
            if (!props.exists()) {
                return plugin.getServer().getMaxPlayers();
            }
            java.util.Properties p = new java.util.Properties();
            java.io.FileInputStream in = new java.io.FileInputStream(props);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            String v = p.getProperty("max-players");
            if (v == null) {
                return plugin.getServer().getMaxPlayers();
            }
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return plugin.getServer().getMaxPlayers();
        }
    }

    private static boolean isClass(String s) {
        return "low".equalsIgnoreCase(s) || "mid".equalsIgnoreCase(s) || "high".equalsIgnoreCase(s);
    }

    private void msg(Player player, String message) {
        final String out = color(plugin.getConfig().getString("messages.prefix", "&8[&bChunkBench&8]&r ") + message);
        if (Bukkit.isPrimaryThread()) {
            player.sendMessage(out);
        } else {
            final Player p = player;
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (p.isOnline()) {
                        p.sendMessage(out);
                    }
                }
            });
        }
    }

    private static String formatGb(double gb) {
        if (Math.abs(gb - Math.rint(gb)) < 0.05) {
            return String.valueOf((int) Math.rint(gb));
        }
        return String.format(java.util.Locale.US, "%.1f", gb);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
