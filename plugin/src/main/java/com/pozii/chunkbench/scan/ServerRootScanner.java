package com.pozii.chunkbench.scan;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ServerRootScanner {

    public static final class PluginJar {
        public final String fileName;
        public final String name;

        public PluginJar(String fileName, String name) {
            this.fileName = fileName;
            this.name = name;
        }
    }

    private final ChunkBenchPlugin plugin;

    public ServerRootScanner(ChunkBenchPlugin plugin) {
        this.plugin = plugin;
    }

    public ScanResult scan(File root) {
        int maxDepth = Defaults.SCAN_MAX_DEPTH;
        int maxFiles = Defaults.SCAN_MAX_FILES;
        Set<String> exclude = new HashSet<String>();
        for (String s : Defaults.SCAN_EXCLUDE_DIRS) {
            exclude.add(s.toLowerCase(Locale.US));
        }
        List<String> markers = new ArrayList<String>(Defaults.SCAN_WORLD_MARKERS);

        List<String> startupScripts = new ArrayList<String>();
        List<String> serverJars = new ArrayList<String>();
        List<String> configs = new ArrayList<String>();
        List<PluginJar> plugins = new ArrayList<PluginJar>();
        List<String> notes = new ArrayList<String>();
        int[] counter = new int[]{0};

        walk(root, root, 0, maxDepth, maxFiles, exclude, markers, startupScripts, serverJars, configs, plugins, notes, counter);

        boolean hasProps = new File(root, "server.properties").exists();
        boolean hasPaper = containsIgnoreCase(configs, "paper") || fileExists(root, "paper.yml")
                || fileExists(root, "config/paper-global.yml");
        boolean hasSpigot = new File(root, "spigot.yml").exists();
        boolean hasBukkit = new File(root, "bukkit.yml").exists();

        return new ScanResult(root.getAbsolutePath(), startupScripts, serverJars, configs, plugins,
                hasProps, hasPaper, hasSpigot, hasBukkit, notes, counter[0]);
    }

    private void walk(File root, File dir, int depth, int maxDepth, int maxFiles,
                      Set<String> exclude, List<String> markers,
                      List<String> startupScripts, List<String> serverJars, List<String> configs,
                      List<PluginJar> plugins, List<String> notes, int[] counter) {
        if (depth > maxDepth || counter[0] >= maxFiles) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (counter[0] >= maxFiles) {
                notes.add("File scan capped at " + maxFiles);
                return;
            }
            String name = f.getName();
            String lower = name.toLowerCase(Locale.US);
            if (f.isDirectory()) {
                if (exclude.contains(lower) || looksLikeWorld(f, markers)) {
                    continue;
                }
                // always enter plugins/
                walk(root, f, depth + 1, maxDepth, maxFiles, exclude, markers,
                        startupScripts, serverJars, configs, plugins, notes, counter);
                continue;
            }
            counter[0]++;
            String rel = relativize(root, f);
            if (lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".sh") || lower.endsWith(".ps1")) {
                startupScripts.add(rel);
            } else if (lower.endsWith(".jar") && depth <= 1) {
                serverJars.add(rel);
            } else if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.equals("server.properties")) {
                if (depth <= 3) {
                    configs.add(rel);
                }
            } else if (lower.endsWith(".jar") && rel.toLowerCase(Locale.US).replace('\\', '/').contains("plugins/")) {
                String pname = name.replaceAll("(?i)\\.jar$", "");
                plugins.add(new PluginJar(name, pname));
            }
        }
    }

    private static boolean looksLikeWorld(File dir, List<String> markers) {
        for (String m : markers) {
            if (new File(dir, m).exists()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(List<String> list, String needle) {
        for (String s : list) {
            if (s.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static boolean fileExists(File root, String rel) {
        return new File(root, rel).exists();
    }

    private static String relativize(File root, File file) {
        try {
            return root.toURI().relativize(file.toURI()).getPath();
        } catch (Exception e) {
            return file.getName();
        }
    }
}
