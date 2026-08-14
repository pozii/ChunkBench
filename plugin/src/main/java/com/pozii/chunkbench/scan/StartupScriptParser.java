package com.pozii.chunkbench.scan;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StartupScriptParser {

    private static final Pattern JAVA_LINE = Pattern.compile("(?i).*\\bjava(?:w)?\\b.*");

    public static final class StartupInfo {
        public final boolean scriptFound;
        public final String scriptPath;
        public final String javaCommand;
        public final boolean bareJarLaunch;
        public final List<String> scriptCandidates;

        public StartupInfo(boolean scriptFound, String scriptPath, String javaCommand,
                           boolean bareJarLaunch, List<String> scriptCandidates) {
            this.scriptFound = scriptFound;
            this.scriptPath = scriptPath;
            this.javaCommand = javaCommand;
            this.bareJarLaunch = bareJarLaunch;
            this.scriptCandidates = scriptCandidates;
        }
    }

    public StartupInfo parse(File root) {
        List<String> candidates = new ArrayList<String>();
        collectScripts(root, candidates, 0);
        String bestPath = null;
        String bestCmd = null;
        for (String rel : candidates) {
            File f = new File(root, rel);
            String cmd = extractJavaCommand(f);
            if (cmd != null) {
                // Prefer names like start.*
                String lower = rel.toLowerCase(Locale.US);
                if (bestCmd == null || lower.contains("start") || lower.contains("launch") || lower.contains("run")) {
                    bestPath = rel;
                    bestCmd = cmd;
                    if (lower.contains("start")) {
                        break;
                    }
                }
            }
        }
        if (bestCmd != null) {
            return new StartupInfo(true, bestPath, bestCmd, false, candidates);
        }
        return new StartupInfo(false, null, null, true, candidates);
    }

    private void collectScripts(File dir, List<String> out, int depth) {
        if (depth > 2) {
            return;
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File f : kids) {
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.equals("plugins") || n.equals("world") || n.equals("logs") || n.equals("libraries")) {
                    continue;
                }
                collectScripts(f, out, depth + 1);
            } else {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.endsWith(".bat") || n.endsWith(".cmd") || n.endsWith(".sh") || n.endsWith(".ps1")) {
                    out.add(f.getName());
                }
            }
        }
    }

    private String extractJavaCommand(File file) {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            StringBuilder continued = new StringBuilder();
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("::") || trimmed.startsWith("rem ") || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.endsWith("^") || trimmed.endsWith("\\")) {
                    continued.append(trimmed, 0, trimmed.length() - 1).append(' ');
                    continue;
                }
                String full = continued.length() == 0 ? trimmed : continued.append(trimmed).toString();
                continued.setLength(0);
                if (JAVA_LINE.matcher(full).matches() && full.toLowerCase(Locale.US).contains("-jar")) {
                    return full;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static String findXmx(String cmd) {
        if (cmd == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)-Xmx(\\S+)").matcher(cmd);
        return m.find() ? m.group(1) : null;
    }

    public static String findXms(String cmd) {
        if (cmd == null) {
            return null;
        }
        Matcher m = Pattern.compile("(?i)-Xms(\\S+)").matcher(cmd);
        return m.find() ? m.group(1) : null;
    }
}
