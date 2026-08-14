package com.pozii.chunkbench;

import com.pozii.chunkbench.command.ChunkBenchCommand;
import com.pozii.chunkbench.wizard.BenchWizard;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBenchPlugin extends JavaPlugin {

    private BenchWizard wizard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.wizard = new BenchWizard(this);
        getServer().getPluginManager().registerEvents(wizard, this);
        getCommand("chunkbench").setExecutor(new ChunkBenchCommand(this, wizard));
        getLogger().info("ChunkBench by pozii enabled. Use /chunkbench to audit this server.");
    }

    @Override
    public void onDisable() {
        if (wizard != null) {
            wizard.cancelAll();
        }
        getLogger().info("ChunkBench disabled.");
    }

    public BenchWizard getWizard() {
        return wizard;
    }
}
