package com.pozii.chunkbench.lang;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;
import org.bukkit.ChatColor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads flat/nested JSON language files from plugins/ChunkBench/lang/.
 * Config key: language (e.g. en_US). Missing keys fall back to embedded en_US.
 */
public final class Lang {

    private final ChunkBenchPlugin plugin;
    private Map<String, String> messages = Collections.emptyMap();
    private Map<String, String> fallback = Collections.emptyMap();
    private String language = Defaults.DEFAULT_LANGUAGE;

    public Lang(ChunkBenchPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        fallback = loadEmbedded("lang/en_US.json");
        language = plugin.getConfig().getString("language", Defaults.DEFAULT_LANGUAGE);
        if (language == null || language.trim().isEmpty()) {
            language = Defaults.DEFAULT_LANGUAGE;
        }
        language = language.trim();

        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        plugin.saveResource("lang/en_US.json", false);

        File file = new File(langDir, language + ".json");
        if (!file.exists() && !Defaults.DEFAULT_LANGUAGE.equals(language)) {
            plugin.getLogger().warning("Language file not found: lang/" + language + ".json — using en_US");
            language = Defaults.DEFAULT_LANGUAGE;
            file = new File(langDir, language + ".json");
        }
        if (file.exists()) {
            messages = loadFile(file);
        } else {
            messages = new HashMap<String, String>(fallback);
        }
    }

    public String getLanguage() {
        return language;
    }

    public String raw(String key) {
        String v = messages.get(key);
        if (v == null) {
            v = fallback.get(key);
        }
        if (v == null) {
            return key;
        }
        return v;
    }

    public String get(String key) {
        return color(raw(key));
    }

    public String format(String key, Object... args) {
        String template = raw(key);
        if (args != null && args.length > 0) {
            try {
                template = String.format(java.util.Locale.US, template, args);
            } catch (Exception ignored) {
            }
        }
        return color(template);
    }

    public String prefix() {
        String p = raw("prefix");
        if (p == null || p.isEmpty() || p.equals("prefix")) {
            p = Defaults.DEFAULT_PREFIX;
        }
        return color(p);
    }

    public String prefixed(String key) {
        return prefix() + get(key);
    }

    public String prefixedFormat(String key, Object... args) {
        return prefix() + format(key, args);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private Map<String, String> loadEmbedded(String path) {
        InputStream in = plugin.getResource(path);
        if (in == null) {
            return Collections.emptyMap();
        }
        try {
            return parseJsonObject(readFully(in));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load embedded " + path + ": " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, String> loadFile(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return parseJsonObject(readFully(in));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load " + file.getName() + ": " + e.getMessage());
            return new HashMap<String, String>(fallback);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String readFully(InputStream in) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            br.close();
        }
        return sb.toString();
    }

    /** Minimal JSON object reader -> flat dotted keys. */
    static Map<String, String> parseJsonObject(String json) {
        Map<String, String> out = new HashMap<String, String>();
        String s = json.trim();
        if (s.startsWith("\uFEFF")) {
            s = s.substring(1);
        }
        parseObject(s, 0, "", out);
        return out;
    }

    private static int parseObject(String s, int i, String prefix, Map<String, String> out) {
        i = skipWs(s, i);
        if (i >= s.length() || s.charAt(i) != '{') {
            return i;
        }
        i++;
        while (true) {
            i = skipWs(s, i);
            if (i < s.length() && s.charAt(i) == '}') {
                return i + 1;
            }
            if (i >= s.length()) {
                return i;
            }
            String[] keyHold = new String[1];
            i = parseString(s, i, keyHold);
            i = skipWs(s, i);
            if (i < s.length() && s.charAt(i) == ':') {
                i++;
            }
            i = skipWs(s, i);
            String fullKey = prefix.isEmpty() ? keyHold[0] : prefix + "." + keyHold[0];
            if (i < s.length() && s.charAt(i) == '{') {
                i = parseObject(s, i, fullKey, out);
            } else if (i < s.length() && s.charAt(i) == '"') {
                String[] valHold = new String[1];
                i = parseString(s, i, valHold);
                out.put(fullKey, valHold[0]);
            } else {
                // skip primitives we don't need
                while (i < s.length() && ",}".indexOf(s.charAt(i)) < 0) {
                    i++;
                }
            }
            i = skipWs(s, i);
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
            }
        }
    }

    private static int parseString(String s, int i, String[] out) {
        i = skipWs(s, i);
        if (i >= s.length() || s.charAt(i) != '"') {
            out[0] = "";
            return i;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i < s.length()) {
                char n = s.charAt(i++);
                switch (n) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(n);
                        break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (Exception e) {
                                sb.append('u');
                            }
                            i += 4;
                        }
                        break;
                    default:
                        sb.append(n);
                }
            } else {
                sb.append(c);
            }
        }
        out[0] = sb.toString();
        return i;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
        return i;
    }
}
