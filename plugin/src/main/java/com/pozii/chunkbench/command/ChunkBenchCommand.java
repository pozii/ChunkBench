package com.pozii.chunkbench.command;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.wizard.BenchWizard;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChunkBenchCommand implements CommandExecutor {

    private final ChunkBenchPlugin plugin;
    private final BenchWizard wizard;

    public ChunkBenchCommand(ChunkBenchPlugin plugin, BenchWizard wizard) {
        this.plugin = plugin;
        this.wizard = wizard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&cChunkBench must be started in-game (interactive wizard)."));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("chunkbench.run")) {
            player.sendMessage(prefix() + color("&cYou lack permission &fchunkbench.run&c."));
            return true;
        }
        if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
            wizard.cancel(player);
            player.sendMessage(prefix() + color("&eBench cancelled."));
            return true;
        }
        if (wizard.isBusy(player)) {
            player.sendMessage(prefix() + color("&eYou already have a bench in progress. Type &fcancel &eor &f/chunkbench cancel&e."));
            return true;
        }
        long cooldown = plugin.getConfig().getLong("cooldown-seconds", 60L) * 1000L;
        if (!wizard.tryCooldown(player, cooldown)) {
            player.sendMessage(prefix() + color("&cPlease wait before running another bench."));
            return true;
        }
        wizard.start(player);
        return true;
    }

    private String prefix() {
        return color(plugin.getConfig().getString("messages.prefix", "&8[&bChunkBench&8]&r "));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
