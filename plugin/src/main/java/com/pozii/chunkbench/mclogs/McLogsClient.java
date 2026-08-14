package com.pozii.chunkbench.mclogs;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;
import com.pozii.chunkbench.estimate.BenchResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

public final class McLogsClient {

    private final ChunkBenchPlugin plugin;

    public McLogsClient(ChunkBenchPlugin plugin) {
        this.plugin = plugin;
    }

    public String upload(String content, BenchResult result) {
        try {
            // Prefer JSON API with source + metadata; fall back to form body if needed.
            String endpoint = Defaults.MCLOGS_URL;
            String source = Defaults.MCLOGS_SOURCE;

            String payload = buildJson(content, source, result);
            String response = post(endpoint, payload, "application/json; charset=utf-8");
            String url = extractUrl(response);
            if (url != null) {
                return url;
            }

            // Fallback: classic form upload
            String form = "content=" + urlEncode(content);
            response = post(endpoint, form, "application/x-www-form-urlencoded; charset=utf-8");
            return extractUrl(response);
        } catch (Exception e) {
            plugin.getLogger().warning("mclo.gs upload failed: " + e.getMessage());
            return null;
        }
    }

    private static String buildJson(String content, String source, BenchResult result) {
        // Minimal JSON without requiring Gson (Java 8). Use simple hand-built JSON for portability.
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"content\":").append(jsonString(content)).append(',');
        sb.append("\"source\":").append(jsonString(source)).append(',');
        sb.append("\"metadata\":[");
        sb.append(meta("author", "pozii", "Author", true)).append(',');
        sb.append(meta("score", String.valueOf(result.overallScore), "Server Score", true)).append(',');
        sb.append(meta("chunk_score", String.valueOf(result.chunkScore), "Chunk Score", true)).append(',');
        sb.append(meta("target_players", String.valueOf(result.inputs.targetPlayers), "Target Players", true)).append(',');
        sb.append(meta("mc_version", result.mcVersion, "Minecraft", true)).append(',');
        sb.append(meta("verdict", result.verdict, "Verdict", true));
        sb.append("]}");
        return sb.toString();
    }

    private static String meta(String key, String value, String label, boolean visible) {
        return "{\"key\":" + jsonString(key)
                + ",\"value\":" + jsonString(value)
                + ",\"label\":" + jsonString(label)
                + ",\"visible\":" + visible + "}";
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String post(String endpoint, String body, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("User-Agent", "ChunkBench/1.0 (pozii)");
        byte[] bytes = body.getBytes("UTF-8");
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        OutputStream out = conn.getOutputStream();
        try {
            out.write(bytes);
        } finally {
            out.close();
        }
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
        return resp.toString();
    }

    private static String extractUrl(String response) {
        if (response == null) {
            return null;
        }
        // Very small parse: look for "url":"https://mclo.gs/..."
        int idx = response.indexOf("\"url\"");
        if (idx < 0) {
            return null;
        }
        int colon = response.indexOf(':', idx);
        int q1 = response.indexOf('"', colon + 1);
        int q2 = response.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return null;
        }
        return response.substring(q1 + 1, q2);
    }

    private static String urlEncode(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8");
    }
}
