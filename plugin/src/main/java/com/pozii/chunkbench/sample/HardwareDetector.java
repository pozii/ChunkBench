package com.pozii.chunkbench.sample;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

public final class HardwareDetector {

    public static final class Snapshot {
        public final double detectedXmxGb;
        public final double hostRamGb;
        public final int cpuCores;
        public final String cpuModel;
        public final String osName;

        public Snapshot(double detectedXmxGb, double hostRamGb, int cpuCores, String cpuModel, String osName) {
            this.detectedXmxGb = detectedXmxGb;
            this.hostRamGb = hostRamGb;
            this.cpuCores = cpuCores;
            this.cpuModel = cpuModel == null ? "" : cpuModel;
            this.osName = osName == null ? "" : osName;
        }
    }

    private HardwareDetector() {
    }

    public static Snapshot detect() {
        double xmx = Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0 * 1024.0);
        // Prefer explicit -Xmx from process if present
        double fromArgs = parseXmxFromInputArgs();
        if (fromArgs > 0) {
            xmx = fromArgs;
        }
        return new Snapshot(
                xmx,
                detectHostRamGb(),
                Math.max(1, Runtime.getRuntime().availableProcessors()),
                detectCpuModel(),
                System.getProperty("os.name", "unknown")
        );
    }

    private static double parseXmxFromInputArgs() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (int i = 0; i < args.size(); i++) {
                String a = args.get(i);
                if (a.regionMatches(true, 0, "-Xmx", 0, 4)) {
                    return parseMem(a.substring(4));
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    public static double parseMem(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        String s = raw.trim().toLowerCase(Locale.US);
        try {
            if (s.endsWith("g")) {
                return Double.parseDouble(s.substring(0, s.length() - 1));
            }
            if (s.endsWith("m")) {
                return Double.parseDouble(s.substring(0, s.length() - 1)) / 1024.0;
            }
            if (s.endsWith("k")) {
                return Double.parseDouble(s.substring(0, s.length() - 1)) / (1024.0 * 1024.0);
            }
            // bytes
            long bytes = Long.parseLong(s);
            return bytes / (1024.0 * 1024.0 * 1024.0);
        } catch (Exception e) {
            return -1;
        }
    }

    private static double detectHostRamGb() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
            if (os.contains("linux")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        new java.io.FileInputStream(new File("/proc/meminfo")), "UTF-8"));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("MemTotal:")) {
                            String[] p = line.split("\\s+");
                            long kb = Long.parseLong(p[1]);
                            return kb / (1024.0 * 1024.0);
                        }
                    }
                } finally {
                    br.close();
                }
            } else if (os.contains("win")) {
                Process proc = new ProcessBuilder("wmic", "ComputerSystem", "get", "TotalPhysicalMemory")
                        .redirectErrorStream(true).start();
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.matches("\\d+")) {
                            return Long.parseLong(line) / (1024.0 * 1024.0 * 1024.0);
                        }
                    }
                } finally {
                    br.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static String detectCpuModel() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.US);
            if (os.contains("linux")) {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        new java.io.FileInputStream(new File("/proc/cpuinfo")), "UTF-8"));
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("model name") || line.startsWith("Hardware")) {
                            int idx = line.indexOf(':');
                            if (idx >= 0) {
                                return line.substring(idx + 1).trim();
                            }
                        }
                    }
                } finally {
                    br.close();
                }
            } else if (os.contains("win")) {
                Process proc = new ProcessBuilder("wmic", "cpu", "get", "Name")
                        .redirectErrorStream(true).start();
                BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
                try {
                    String line;
                    boolean header = true;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (header) {
                            header = false;
                            continue;
                        }
                        return line;
                    }
                } finally {
                    br.close();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
