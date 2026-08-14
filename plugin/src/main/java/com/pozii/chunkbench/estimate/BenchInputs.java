package com.pozii.chunkbench.estimate;

public final class BenchInputs {
    public final int targetPlayers;
    public final String playersSource;
    public final int viewDistance;
    public final String viewDistanceSource;
    public final double ramGb;
    public final int cpuCores;
    public final String cpuClass;

    public BenchInputs(int targetPlayers, String playersSource,
                       int viewDistance, String viewDistanceSource,
                       double ramGb, int cpuCores, String cpuClass) {
        this.targetPlayers = targetPlayers;
        this.playersSource = playersSource;
        this.viewDistance = viewDistance;
        this.viewDistanceSource = viewDistanceSource;
        this.ramGb = ramGb;
        this.cpuCores = cpuCores;
        this.cpuClass = cpuClass;
    }
}
