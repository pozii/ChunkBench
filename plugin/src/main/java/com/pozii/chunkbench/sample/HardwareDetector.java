package com.pozii.chunkbench.sample;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/**
 * Best-effort host hardware detection. On Windows 11, WMIC is often missing —
 * fall back to PowerShell CIM queries and Runtime MXBean hints.
 */
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
                return readLinuxMemTotalGb();
            }
            if (os.contains("win")) {
                double ps = readWindowsCimDouble(
                        "(Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory");
                if (ps > 0) {
                    return ps / (1024.0 * 1024.0 * 1024.0);
                }
                double wmic = readWmicBytes("ComputerSystem", "TotalPhysicalMemory");
                if (wmic > 0) {
                    return wmic / (1024.0 * 1024.0 * 1024.0);
                }
            }
            // Last resort: committed virtual memory hint (not host physical)
            try {
                Object mx = ManagementFactory.getOperatingSystemMXBean();
                java.lang.reflect.Method m = mx.getClass().getMethod("getTotalPhysicalMemorySize");
                m.setAccessible(true);
                Object v = m.invoke(mx);
                if (v instanceof Number) {
                    long bytes = ((Number) v).longValue();
                    if (bytes > 0) {
                        return bytes / (1024.0 * 1024.0 * 1024.0);
                    }
                }
            } catch (Throwable ignored) {
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
                String ps = readWindowsCimString("(Get-CimInstance Win32_Processor | Select-Object -First 1).Name");
                if (ps != null && !ps.isEmpty()) {
                    return ps;
                }
                String wmic = readWmicString("cpu", "Name");
                if (wmic != null && !wmic.isEmpty()) {
                    return wmic;
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static double readLinuxMemTotalGb() throws Exception {
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
        return -1;
    }

    private static double readWindowsCimDouble(String expression) {
        String out = runPowerShell(expression);
        if (out == null) {
            return -1;
        }
        out = out.trim();
        try {
            return Double.parseDouble(out.replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String readWindowsCimString(String expression) {
        String out = runPowerShell(expression);
        if (out == null) {
            return "";
        }
        out = out.trim();
        if (out.isEmpty() || out.equalsIgnoreCase("null")) {
            return "";
        }
        return out;
    }

    private static String runPowerShell(String expression) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-Command",
                    expression
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"));
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(line.trim());
                }
                proc.waitFor();
                return sb.toString();
            } finally {
                br.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static double readWmicBytes(String alias, String property) {
        String s = readWmicString(alias, property);
        if (s == null || s.isEmpty()) {
            return -1;
        }
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String readWmicString(String alias, String property) {
        try {
            Process proc = new ProcessBuilder("wmic", alias, "get", property)
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
        } catch (Throwable ignored) {
        }
        return "";
    }
}
