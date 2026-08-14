package com.pozii.chunkbench;

import com.pozii.chunkbench.command.ChunkBenchCommand;
import com.pozii.chunkbench.lang.Lang;
import com.pozii.chunkbench.update.UpdateChecker;
import com.pozii.chunkbench.wizard.BenchWizard;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkBenchPlugin extends JavaPlugin {

    private BenchWizard wizard;
    private Lang lang;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.lang = new Lang(this);
        this.lang.reload();
        this.wizard = new BenchWizard(this);
        this.updateChecker = new UpdateChecker(this);
        getServer().getPluginManager().registerEvents(wizard, this);
        getServer().getPluginManager().registerEvents(updateChecker, this);
        PluginCommand cmd = getCommand("chunkbench");
        ChunkBenchCommand executor = new ChunkBenchCommand(this, wizard);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
        getLogger().info("ChunkBench enabled (" + lang.getLanguage() + ").");
        updateChecker.start();
    }

    @Override
    public void onDisable() {
        if (wizard != null) {
            wizard.cancelAll();
        }
        getLogger().info("ChunkBench disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        if (lang == null) {
            lang = new Lang(this);
        }
        lang.reload();
        if (updateChecker != null) {
            updateChecker.start();
        }
    }

    public Lang lang() {
        return lang;
    }

    public BenchWizard getWizard() {
        return wizard;
    }

    public UpdateChecker updateChecker() {
        return updateChecker;
    }
}
