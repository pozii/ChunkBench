# ChunkBench

**ChunkBench** by **pozii** — a core Bukkit/Spigot/Paper plugin that audits **your own** Minecraft server (Java Edition **1.8.8 → 26.2**) and scores how well it can handle a target player count (chunk loading + overall speed), **without stress bots**.

## What it does

1. Install `ChunkBench-<version>.jar` into `plugins/`
2. Run `/chunkbench` in-game (OP / `chunkbench.run`)
3. Wizard asks for:
   - **Target players** — type a number, or `confirm` to use `max-players` from `server.properties`
   - **View distance (chunks)** — type `2–32`, or `confirm` to use `view-distance` (rejects unsupported values such as 100)
   - **RAM (GB)** — confirm detected `-Xmx` or type your allocation
   - **CPU** — confirm detected cores (optional `low` / `mid` / `high`)
4. Plugin scans the **server root** (world folders excluded), startup scripts (`.bat`/`.sh`/…), JVM flags (Aikar/G1), plugins, and live TPS when available
5. Builds an English report, saves it under `plugins/ChunkBench/reports/`, uploads to [mclo.gs](https://mclo.gs) as source **ChunkBench**, and gives you the link

## Scoring (out of 100 for YOUR player target)

The score answers: *for N players, with this RAM/CPU/version/view-distance/plugins/startup, how good are chunk load + overall speed?*

Minecraft **version is not a flat vanity bonus**. ChunkBench uses a research-backed **resource cost** table with a **score floor** (modern versions are penalized, never zeroed to wipe capacity).

**RAM is tiered**, not linear ~200MB×players: as population grows, chunk regions overlap, so marginal RAM per extra player drops — aligning closer to Paper guidance (~12–16G for ~100 players at view-distance ~10 on 1.21+), not 30G+ linear estimates.

**Expected capacity band** is independent of target failure: if you ask for 100 players on 4.5G, the verdict can still **FAIL** the target while the band reports a realistic ~15–25 players for that box (empirical floors + blended RAM/CPU), not 1–2.

## Build

```bash
gradlew jar
```

Output: `build/libs/ChunkBench-1.1.3.jar`

Requires JDK 8+ to build (bytecode targets Java 8 for wide server compatibility).
