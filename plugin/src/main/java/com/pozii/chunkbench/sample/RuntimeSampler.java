package com.pozii.chunkbench.sample;

import org.bukkit.Server;

import java.lang.reflect.Method;

public final class RuntimeSampler {

    public static final class Sample {
        public final double tps;
        public final double mspt;
        public final long usedHeapMb;
        public final long maxHeapMb;
        public final String serverVersion;
        public final String bukkitVersion;
        public final String mcVersion;
        public final int onlinePlayers;
        public final int viewDistance;

        public Sample(double tps, double mspt, long usedHeapMb, long maxHeapMb,
                      String serverVersion, String bukkitVersion, String mcVersion,
                      int onlinePlayers, int viewDistance) {
            this.tps = tps;
            this.mspt = mspt;
            this.usedHeapMb = usedHeapMb;
            this.maxHeapMb = maxHeapMb;
            this.serverVersion = serverVersion;
            this.bukkitVersion = bukkitVersion;
            this.mcVersion = mcVersion;
            this.onlinePlayers = onlinePlayers;
            this.viewDistance = viewDistance;
        }
    }

    private RuntimeSampler() {
    }

    public static Sample sample(Server server) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long max = rt.maxMemory() / (1024L * 1024L);
        double tps = readTps(server);
        double mspt = tps > 0.1 ? (1000.0 / Math.min(20.0, tps)) : -1;
        int view = 10;
        try {
            view = server.getViewDistance();
        } catch (Throwable ignored) {
        }
        String bukkit = server.getBukkitVersion();
        String mc = VersionParser.fromBukkitVersion(bukkit);
        if (mc.equals("unknown")) {
            mc = VersionParser.fromServerVersion(server.getVersion());
        }
        return new Sample(tps, mspt, used, max, server.getVersion(), bukkit, mc,
                onlineCount(server), view);
    }

    /** 1.8 returns Player[]; 1.9+ returns Collection — use reflection-safe sizing. */
    private static int onlineCount(Server server) {
        try {
            Object online = server.getClass().getMethod("getOnlinePlayers").invoke(server);
            if (online instanceof java.util.Collection) {
                return ((java.util.Collection<?>) online).size();
            }
            if (online instanceof Object[]) {
                return ((Object[]) online).length;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static double readTps(Server server) {
        try {
            Method m = server.getClass().getMethod("getTPS");
            Object result = m.invoke(server);
            if (result instanceof double[]) {
                double[] arr = (double[]) result;
                if (arr.length > 0) {
                    return arr[0];
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object console = server.getClass().getMethod("getServer").invoke(server);
            Object recent = console.getClass().getField("recentTps").get(console);
            if (recent instanceof double[]) {
                double[] arr = (double[]) recent;
                if (arr.length > 0) {
                    return arr[0];
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
