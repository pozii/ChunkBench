# ChunkBench

**ChunkBench** is a Minecraft plugin that audits **your own** Java Edition server and scores how well it can handle a target player count — focusing on chunk loading and overall speed — **without stress bots**.

Drop the jar in, run `/chunkbench`, answer a short wizard, and get a scored report you can share via [mclo.gs](https://mclo.gs).

## Requirements

- **Platform:** Bukkit, Spigot, Paper, Purpur, and compatible forks  
- **Minecraft:** Java Edition **1.8.8 → 26.2**  
- **Permission:** `chunkbench.run` (default: OP) to run audits  

Use ChunkBench only on servers you own or are authorized to manage.

## Download

Get the latest build from the [releases page](https://github.com/pozii/ChunkBench/releases).

1. Place `ChunkBench-<version>.jar` into your server `plugins/` folder  
2. Restart the server  
3. Run `/chunkbench` in-game  

On startup (when enabled), ChunkBench checks GitHub for newer releases and warns OPs with a download link.

## Commands

| Command | Description |
|--------|-------------|
| `/chunkbench` | Start the capacity audit wizard |
| `/chunkbench cancel` | Abort your current wizard |
| `/chunkbench reload` | Reload `config.yml` and language files |

Aliases: `/cb`, `/cbench`  
Tab-complete suggests `cancel` and `reload`.

## Wizard

The in-game wizard asks for:

1. **Target players** — type a number, or `confirm` to use `max-players` from `server.properties`  
2. **View distance** — `2–32` chunks, or `confirm` to use `view-distance`  
3. **RAM (GB)** — confirm detected `-Xmx` or type your allocation  
4. **CPU** — confirm detected cores; optional class `low` / `mid` / `high`  

ChunkBench then scans the **server root** (world folders excluded), startup scripts, JVM flags (including Aikar/G1 hints), installed plugins, and live TPS when available. It writes a report under `plugins/ChunkBench/reports/` and uploads it to mclo.gs as source **ChunkBench**.

## Scoring

Scores are **out of 100 for your stated player target**, not a vanity “server rank.”

- **Minecraft version** is modeled as a **resource cost** (modern versions cost more; older versions are not treated as free capacity).  
- **RAM is tiered** — not a naive linear “~200MB × players” formula — so estimates stay closer to real Paper-scale guidance.  
- **Expected capacity** is reported separately from target pass/fail. Asking for 100 players on a small box can **FAIL** the target while still showing a realistic band (e.g. ~15–25) for that hardware.

## Translation partners

Want ChunkBench in your language? We’re looking for translation partners.

Messages live in `plugins/ChunkBench/lang/` (`en_US.json` by default). Copy that file, translate it, open an issue or PR, and set `language:` in `config.yml` (then `/chunkbench reload`).

## Updates

When `update-check` is enabled, ChunkBench compares the installed version to the latest [GitHub release](https://github.com/pozii/ChunkBench/releases). If a newer version exists, you’ll get a notice with the download link.

## Contributing

Pull requests that improve ChunkBench are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR.

## Links

- [Releases](https://github.com/pozii/ChunkBench/releases)  
- [Issues](https://github.com/pozii/ChunkBench/issues)  
- [Discussions](https://github.com/pozii/ChunkBench/discussions)  
