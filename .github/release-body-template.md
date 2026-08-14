# ChunkBench __VERSION__
Supports: Bukkit / Spigot / Paper **1.8.8 - 26.2**

Today we are releasing **ChunkBench __VERSION__**, a bot-less capacity audit plugin for your own Minecraft Java servers.

Download **`__JAR__`**, drop it into `plugins/`, restart, then run `/chunkbench`.

As always, you can report issues on our [issue tracker](https://github.com/pozii/ChunkBench/issues).

Happy benchmarking!

## Changelog

Changes included in this release are listed below (auto-generated from commits and pull requests since the previous tag).

## Install

1. Download `__JAR__` from the assets below
2. Place it in your server `plugins/` folder
3. Restart the server
4. Run `/chunkbench` in-game (permission `chunkbench.run`, default: OP)
5. Complete the wizard (target players -> RAM -> CPU)

Reports are saved under `plugins/ChunkBench/reports/` and can be uploaded to [mclo.gs](https://mclo.gs) as source **ChunkBench**.

## Notices

### Compatibility
ChunkBench is a **core** plugin intended to load from **1.8.8 through 26.2**. Prefer Paper/Purpur when available; Spigot and compatible forks are supported. Only audit servers you own or have permission to inspect.

### Scoring model
Scores are **out of 100 for your stated player target**, focusing on chunk-load fitness and overall speed. Minecraft version is modeled as a research-backed **resource cost** (not a vanity bonus). Worlds are never scanned; farms, redstone, and custom plugins can dominate real capacity.

### License
Released under **[PolyForm Shield 1.0.0](https://github.com/pozii/ChunkBench/blob/main/LICENSE)**. Contributions via pull request are welcome. Building a competing product or integrating this codebase into another application as a substitute is not permitted.

### Community
- Issues: https://github.com/pozii/ChunkBench/issues
- Discussions: https://github.com/pozii/ChunkBench/discussions
- Contribute: https://github.com/pozii/ChunkBench/blob/main/CONTRIBUTING.md

## Thank You

Special thanks to everyone contributing ideas, reports, and pull requests to ChunkBench.

Built by **[pozii](https://github.com/pozii)**.

If you encounter issues or have suggestions, please open a ticket at https://github.com/pozii/ChunkBench/issues.
