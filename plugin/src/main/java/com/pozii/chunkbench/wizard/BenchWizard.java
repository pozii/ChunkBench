package com.pozii.chunkbench.wizard;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BenchWizard implements Listener {

    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;

    private enum Step {
        PLAYERS, VIEW, RAM, CPU
    }

    private static final class Session {
        Step step = Step.PLAYERS;
        int targetPlayers;
        String playersSource;
        int viewDistance;
        String viewDistanceSource;
        double ramGb;
        int cpuCores;
        String cpuClass = "mid";
    }

    private final ChunkBenchPlugin plugin;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();
    private final ConcurrentHashMap<UUID, Long> lastRun = new ConcurrentHashMap<UUID, Long>();

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
        msgKey(player, "wizard.intro");
        msgKey(player, "wizard.step.players");
        msgKey(player, "wizard.step.players.hint");
        msgKey(player, "wizard.cancel-hint");
        if (hw.detectedXmxGb > 0) {
            msgFmt(player, "wizard.detected-xmx", formatGb(hw.detectedXmxGb));
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
            msgKey(player, "error.cancelled");
            return;
        }

        if (session.step == Step.PLAYERS) {
            handlePlayers(player, session, raw);
        } else if (session.step == Step.VIEW) {
            handleView(player, session, raw);
        } else if (session.step == Step.RAM) {
            handleRam(player, session, raw);
        } else if (session.step == Step.CPU) {
            handleCpu(player, session, raw);
        }
    }

    private void handlePlayers(Player player, Session session, String raw) {
        if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
            Integer max = readIntProperty("max-players", plugin.getServer().getMaxPlayers());
            if (max == null || max <= 0) {
                msgKey(player, "wizard.players.no-props");
                return;
            }
            session.targetPlayers = max;
            session.playersSource = "server.properties max-players=" + max;
        } else {
            try {
                int n = Integer.parseInt(raw);
                if (n < 1 || n > 10000) {
                    msgKey(player, "wizard.players.bad-range");
                    return;
                }
                session.targetPlayers = n;
                session.playersSource = "chat input";
            } catch (NumberFormatException ex) {
                msgKey(player, "wizard.players.bad-number");
                return;
            }
        }
        session.step = Step.VIEW;
        msgFmt(player, "wizard.players.ok", Integer.valueOf(session.targetPlayers), session.playersSource);
        promptViewDistance(player);
    }

    private void promptViewDistance(Player player) {
        Integer propsVd = readIntProperty("view-distance", -1);
        msgKey(player, "wizard.step.view");
        msgFmt(player, "wizard.view.range", Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
        if (propsVd != null && propsVd > 0) {
            if (isValidViewDistance(propsVd)) {
                msgFmt(player, "wizard.view.props-ok", Integer.valueOf(propsVd));
            } else {
                msgFmt(player, "wizard.view.props-bad", Integer.valueOf(propsVd), Integer.valueOf(MAX_VIEW_DISTANCE));
                msgFmt(player, "wizard.view.props-bad-hint",
                        Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
            }
        } else {
            msgFmt(player, "wizard.view.no-props",
                    Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
        }
        msgKey(player, "wizard.view.hint");
    }

    private void handleView(Player player, Session session, String raw) {
        Integer propsVd = readIntProperty("view-distance", -1);
        if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
            if (propsVd == null || propsVd <= 0) {
                msgFmt(player, "wizard.view.nothing",
                        Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
                return;
            }
            if (!isValidViewDistance(propsVd)) {
                msgFmt(player, "wizard.view.unsupported",
                        Integer.valueOf(propsVd),
                        Integer.valueOf(MIN_VIEW_DISTANCE),
                        Integer.valueOf(MAX_VIEW_DISTANCE));
                return;
            }
            session.viewDistance = propsVd;
            session.viewDistanceSource = "server.properties view-distance=" + propsVd;
        } else {
            try {
                int n = Integer.parseInt(raw);
                if (!isValidViewDistance(n)) {
                    msgFmt(player, "wizard.view.invalid",
                            Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
                    return;
                }
                session.viewDistance = n;
                session.viewDistanceSource = "chat input";
            } catch (NumberFormatException ex) {
                msgFmt(player, "wizard.view.invalid-or-confirm",
                        Integer.valueOf(MIN_VIEW_DISTANCE), Integer.valueOf(MAX_VIEW_DISTANCE));
                return;
            }
        }
        session.step = Step.RAM;
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        msgFmt(player, "wizard.view.ok", Integer.valueOf(session.viewDistance), session.viewDistanceSource);
        msgKey(player, "wizard.step.ram");
        String host = hw.hostRamGb > 0 ? formatGb(hw.hostRamGb) + "G" : "unknown";
        String xmx = hw.detectedXmxGb > 0 ? formatGb(hw.detectedXmxGb) + "G" : "unknown";
        msgFmt(player, "wizard.ram.detected", xmx, host);
        msgKey(player, "wizard.ram.hint");
    }

    private void handleRam(Player player, Session session, String raw) {
        HardwareDetector.Snapshot hw = HardwareDetector.detect();
        if (raw.equalsIgnoreCase("confirm") || raw.isEmpty()) {
            if (hw.detectedXmxGb <= 0) {
                msgKey(player, "wizard.ram.no-xmx");
                return;
            }
            session.ramGb = hw.detectedXmxGb;
        } else {
            try {
                double gb = Double.parseDouble(raw.replace("G", "").replace("g", "").trim());
                if (gb < 0.5 || gb > 1024) {
                    msgKey(player, "wizard.ram.bad-range");
                    return;
                }
                session.ramGb = gb;
            } catch (NumberFormatException ex) {
                msgKey(player, "wizard.ram.bad-number");
                return;
            }
        }
        session.step = Step.CPU;
        msgFmt(player, "wizard.ram.ok", formatGb(session.ramGb));
        msgKey(player, "wizard.step.cpu");
        String model = hw.cpuModel.isEmpty() ? "" : " &7(" + hw.cpuModel + ")";
        msgFmt(player, "wizard.cpu.detected", Integer.valueOf(hw.cpuCores), model);
        msgKey(player, "wizard.cpu.hint");
        msgKey(player, "wizard.cpu.examples");
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
                    msgKey(player, "wizard.cpu.bad-range");
                    return;
                }
                if (parts.length > 1 && isClass(parts[1])) {
                    session.cpuClass = parts[1].toLowerCase();
                }
            }
        } catch (NumberFormatException ex) {
            msgKey(player, "wizard.cpu.bad-input");
            return;
        }

        sessions.remove(player.getUniqueId());
        lastRun.put(player.getUniqueId(), System.currentTimeMillis());
        msgFmt(player, "wizard.cpu.ok", Integer.valueOf(session.cpuCores), session.cpuClass);

        final BenchInputs inputs = new BenchInputs(
                session.targetPlayers,
                session.playersSource,
                session.viewDistance,
                session.viewDistanceSource,
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
                    msgFmt(player, "error.bench-failed", String.valueOf(t.getMessage()));
                }
            }
        });
    }

    private void runBench(Player player, BenchInputs inputs) throws Exception {
        File serverRoot = plugin.getServer().getWorldContainer().getCanonicalFile();
        File root = serverRoot;
        if (!(new File(serverRoot, "server.properties").exists() || new File(serverRoot, "plugins").exists())) {
            if (serverRoot.getParentFile() != null
                    && (new File(serverRoot.getParentFile(), "server.properties").exists()
                    || new File(serverRoot.getParentFile(), "plugins").exists())) {
                root = serverRoot.getParentFile();
            }
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
        if (Defaults.MCLOGS_ENABLED) {
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
                msgKey(player, "result.complete");
                msgFmt(player, "result.score",
                        Integer.valueOf(inputs.targetPlayers),
                        Integer.valueOf(inputs.viewDistance),
                        Integer.valueOf(score));
                msgFmt(player, "result.verdict", verdict);
                msgFmt(player, "result.summary", summary);
                msgFmt(player, "result.saved", reportPath);
                if (finalUrl != null) {
                    msgFmt(player, "result.url", finalUrl);
                } else if (Defaults.MCLOGS_ENABLED) {
                    msgKey(player, "result.upload-failed");
                }
            }
        });
    }

    private static boolean isValidViewDistance(int n) {
        return n >= MIN_VIEW_DISTANCE && n <= MAX_VIEW_DISTANCE;
    }

    private Integer readIntProperty(String key, int fallback) {
        try {
            File props = locateServerProperties();
            if (props == null || !props.exists()) {
                return fallback > 0 ? Integer.valueOf(fallback) : null;
            }
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(props);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            String v = p.getProperty(key);
            if (v == null || v.trim().isEmpty()) {
                return fallback > 0 ? Integer.valueOf(fallback) : null;
            }
            return Integer.valueOf(Integer.parseInt(v.trim()));
        } catch (Exception e) {
            return fallback > 0 ? Integer.valueOf(fallback) : null;
        }
    }

    private File locateServerProperties() {
        File a = new File(plugin.getServer().getWorldContainer(), "server.properties");
        if (a.exists()) {
            return a;
        }
        File b = new File("server.properties");
        if (b.exists()) {
            return b;
        }
        return a;
    }

    private static boolean isClass(String s) {
        return "low".equalsIgnoreCase(s) || "mid".equalsIgnoreCase(s) || "high".equalsIgnoreCase(s);
    }

    private void msgKey(Player player, String key) {
        send(player, plugin.lang().prefixed(key));
    }

    private void msgFmt(Player player, String key, Object... args) {
        send(player, plugin.lang().prefixedFormat(key, args));
    }

    private void send(Player player, final String out) {
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
}
