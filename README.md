# ChunkBench

**ChunkBench** is a Minecraft plugin by [pozii](https://github.com/pozii) that audits **your own** server and scores how well it can handle a target player count — chunk loading and overall speed — **without stress bots**.

## Requirements

ChunkBench runs on **Bukkit / Spigot / Paper** (and compatible forks) for Minecraft Java **1.8.8 → 26.2**.

Use it only on servers you own or are authorized to manage.

## Download

Downloads and release notes are on the [releases page](https://github.com/pozii/ChunkBench/releases).

1. Place `ChunkBench-<version>.jar` in `plugins/`
2. Restart the server
3. Run `/chunkbench` in-game (OP / `chunkbench.run`)

Tab-complete offers `cancel` and `reload`. Reports are saved under `plugins/ChunkBench/reports/` and uploaded to [mclo.gs](https://mclo.gs).

## Scoring

The score is out of **100 for your stated player target**, based on RAM, CPU, Minecraft version, view distance, plugins, and startup/JVM flags.

Version is treated as a **resource cost**, not a vanity bonus. RAM is tiered (not a flat per-player linear model). Expected capacity is reported separately when the target itself fails.

## Contributing

Pull requests that improve ChunkBench are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) and [LICENSE](LICENSE) (PolyForm Shield 1.0.0 — no competing products / rebrand-integrate).

## Links

- [Issues](https://github.com/pozii/ChunkBench/issues)
- [Discussions](https://github.com/pozii/ChunkBench/discussions)
- [Releases](https://github.com/pozii/ChunkBench/releases)
