# ChunkBench

**ChunkBench** by **pozii** — a core Bukkit/Spigot/Paper plugin that audits **your own** Minecraft server (Java Edition **1.8.8 → 26.2**) and scores how well it can handle a target player count (chunk loading + overall speed), **without stress bots**.

## What it does

1. Install `chunkbench.jar` into `plugins/`
2. Run `/chunkbench` in-game (OP / `chunkbench.run`)
3. Wizard asks for:
   - **Target players** — type a number, or `confirm` to use `max-players` from `server.properties`
   - **RAM (GB)** — confirm detected `-Xmx` or type your allocation
   - **CPU** — confirm detected cores (optional `low` / `mid` / `high`)
4. Plugin scans the **server root** (world folders excluded), startup scripts (`.bat`/`.sh`/…), JVM flags (Aikar/G1), plugins, and live TPS when available
5. Builds an English report, saves it under `plugins/ChunkBench/reports/`, uploads to [mclo.gs](https://mclo.gs) as source **ChunkBench**, and gives you the link

## Scoring (out of 100 for YOUR player target)

The score answers: *for N players, with this RAM/CPU/version/plugins/startup, how good are chunk load + overall speed?*

Minecraft **version is not a flat vanity bonus**. ChunkBench uses a research-backed **resource cost** table (e.g. DFU RAM jump after 1.13 from Mojira MC-190258, higher gen cost on 1.18+, modern 1.21/26.x baselines). Heavier versions need more RAM/CPU for the same player count.

## Build

```bash
gradlew jar
```

Output: `build/libs/chunkbench-1.0.0.jar`

Requires JDK 8+ to build (bytecode targets Java 8 for wide server compatibility).

## CI / Releases

GitHub Actions:

- **CI** — builds the jar on every push/PR to `main` and uploads a build **artifact**
- **Release** — on tag `v*` (or manual **workflow_dispatch**), builds the jar, creates a GitHub Release with install notes + **auto-generated release notes** from commits, and attaches:
  - `chunkbench.jar`
  - `chunkbench-<version>.jar`

Publish a release:

```bash
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin v1.0.1
```

Or run **Actions → Release → Run workflow** and enter `1.0.1`.

## License

**PolyForm Shield 1.0.0** — see [LICENSE](LICENSE).

- You may run ChunkBench on your servers
- Pull requests / contributions to **this** project are welcome (see [CONTRIBUTING.md](CONTRIBUTING.md))
- You may **not** provide a competing product, or take the code to build/integrate a substitute into your own application/product

```
Required Notice: Copyright pozii (ChunkBench)
Licensor Line of Business: ChunkBench Minecraft server capacity audit tooling
```

## Disclaimer

Estimates are model-based. Worlds are never scanned. Farms, redstone, and custom plugins can dominate real capacity. Only use on servers you own or have permission to audit.
