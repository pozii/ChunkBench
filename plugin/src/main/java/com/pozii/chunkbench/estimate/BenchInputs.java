package com.pozii.chunkbench.estimate;

public final class BenchInputs {
    public final int targetPlayers;
    public final String playersSource;
    public final double ramGb;
    public final int cpuCores;
    public final String cpuClass;

    public BenchInputs(int targetPlayers, String playersSource, double ramGb, int cpuCores, String cpuClass) {
        this.targetPlayers = targetPlayers;
        this.playersSource = playersSource;
        this.ramGb = ramGb;
        this.cpuCores = cpuCores;
        this.cpuClass = cpuClass;
    }
}
