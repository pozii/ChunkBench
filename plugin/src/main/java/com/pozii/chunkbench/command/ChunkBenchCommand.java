package com.pozii.chunkbench.command;

import com.pozii.chunkbench.ChunkBenchPlugin;
import com.pozii.chunkbench.Defaults;
import com.pozii.chunkbench.wizard.BenchWizard;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ChunkBenchCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Collections.unmodifiableList(Arrays.asList("cancel", "reload"));

    private final ChunkBenchPlugin plugin;
    private final BenchWizard wizard;

    public ChunkBenchCommand(ChunkBenchPlugin plugin, BenchWizard wizard) {
        this.plugin = plugin;
        this.wizard = wizard;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("chunkbench.reload") && !sender.hasPermission("chunkbench.run")) {
                sender.sendMessage(plugin.lang().prefixedFormat("error.no-permission", "chunkbench.reload"));
                return true;
            }
            plugin.reloadAll();
            sender.sendMessage(plugin.lang().prefixedFormat("reload.ok", plugin.lang().getLanguage()));
            return true;
        }

        if (!(sender instanceof Player)) {
            if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
                sender.sendMessage(plugin.lang().prefixed("error.players-only"));
                return true;
            }
            sender.sendMessage(plugin.lang().get("error.players-only"));
            sender.sendMessage(plugin.lang().get("reload.usage"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("chunkbench.run")) {
            player.sendMessage(plugin.lang().prefixedFormat("error.no-permission", "chunkbench.run"));
            return true;
        }

        if (args.length > 0 && "cancel".equalsIgnoreCase(args[0])) {
            wizard.cancel(player);
            player.sendMessage(plugin.lang().prefixed("error.cancelled"));
            return true;
        }

        if (args.length > 0) {
            player.sendMessage(plugin.lang().prefixed("reload.usage"));
            return true;
        }

        if (wizard.isBusy(player)) {
            player.sendMessage(plugin.lang().prefixed("error.busy"));
            return true;
        }
        if (!wizard.tryCooldown(player, Defaults.COOLDOWN_MS)) {
            player.sendMessage(plugin.lang().prefixed("error.cooldown"));
            return true;
        }
        wizard.start(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.US);
            List<String> out = new ArrayList<String>();
            for (String sub : SUBS) {
                if (!sub.startsWith(partial)) {
                    continue;
                }
                if ("reload".equals(sub)
                        && !sender.hasPermission("chunkbench.reload")
                        && !sender.hasPermission("chunkbench.run")) {
                    continue;
                }
                out.add(sub);
            }
            return out;
        }
        return Collections.emptyList();
    }
}
