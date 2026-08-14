package com.pozii.chunkbench.update;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.logging.Level;

/**
 * Checks GitHub Releases for a newer ChunkBench version and warns OPs / permitted players.
 */
public final class UpdateChecker implements Listener {

    private final ChunkBenchPlugin plugin;
    private volatile boolean updateAvailable;
    private volatile String latestVersion = "";
    private volatile String releaseUrl = Defaults.UPDATE_RELEASES_URL;
    private volatile String jarUrl = "";

    public UpdateChecker(ChunkBenchPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("update-check", true)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                checkNow();
            }
        });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseUrl() {
        return releaseUrl;
    }

    private void checkNow() {
        try {
            String json = httpGet(Defaults.UPDATE_API_LATEST);
            if (json == null || json.isEmpty()) {
                return;
            }
            String tag = extractJsonString(json, "tag_name");
            if (tag == null || tag.isEmpty()) {
                return;
            }
            if (tag.startsWith("v") || tag.startsWith("V")) {
                tag = tag.substring(1);
            }
            String html = extractJsonString(json, "html_url");
            String asset = extractJarAssetUrl(json);

            String current = plugin.getDescription().getVersion();
            if (current == null) {
                current = "0";
            }
            if (compareVersions(tag, current) <= 0) {
                updateAvailable = false;
                return;
            }

            latestVersion = tag;
            if (html != null && !html.isEmpty()) {
                releaseUrl = html;
            }
            jarUrl = asset != null ? asset : "";
            updateAvailable = true;

            plugin.getLogger().warning(plugin.lang().raw("update.console")
                    .replace("%1$s", latestVersion)
                    .replace("%2$s", current)
                    .replace("%3$s", preferredLink()));

            plugin.getServer().getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    notifyOnline();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Update check failed: " + e.getMessage());
        }
    }

    private String preferredLink() {
        if (jarUrl != null && !jarUrl.isEmpty()) {
            return jarUrl;
        }
        return releaseUrl;
    }

    private void notifyOnline() {
        if (!updateAvailable) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldNotify(player)) {
                sendUpdateMessage(player);
            }
        }
    }

    private void sendUpdateMessage(Player player) {
        String current = plugin.getDescription().getVersion();
        player.sendMessage(plugin.lang().prefixedFormat("update.available", latestVersion, current));
        player.sendMessage(plugin.lang().prefixedFormat("update.link", preferredLink()));
    }

    private static boolean shouldNotify(Player player) {
        return player.isOp() || player.hasPermission("chunkbench.run");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable) {
            return;
        }
        final Player player = event.getPlayer();
        if (!shouldNotify(player)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && updateAvailable) {
                    sendUpdateMessage(player);
                }
            }
        }, 40L);
    }

    private static String httpGet(String endpoint) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "ChunkBench-UpdateChecker");
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                Charset.forName("UTF-8")));
        StringBuilder resp = new StringBuilder();
        try {
            String line;
            while ((line = br.readLine()) != null) {
                resp.append(line);
            }
        } finally {
            br.close();
        }
        if (code < 200 || code >= 300) {
            return null;
        }
        return resp.toString();
    }

    static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                sb.append(json.charAt(i++));
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Prefer assets[*].browser_download_url ending with .jar */
    static String extractJarAssetUrl(String json) {
        int from = 0;
        String found = null;
        while (true) {
            int idx = json.indexOf("\"browser_download_url\"", from);
            if (idx < 0) {
                break;
            }
            String url = extractJsonString(json.substring(idx), "browser_download_url");
            from = idx + 20;
            if (url == null) {
                continue;
            }
            String lower = url.toLowerCase();
            if (lower.endsWith(".jar") && lower.contains("chunkbench")) {
                return url;
            }
            if (lower.endsWith(".jar") && found == null) {
                found = url;
            }
        }
        return found;
    }

    /** @return positive if a > b, 0 if equal, negative if a < b */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("[^0-9]+");
        String[] pb = b.split("[^0-9]+");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length && !pa[i].isEmpty() ? parseIntSafe(pa[i]) : 0;
            int vb = i < pb.length && !pb[i].isEmpty() ? parseIntSafe(pb[i]) : 0;
            if (va != vb) {
                return va - vb;
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
