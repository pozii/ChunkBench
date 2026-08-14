# Contributing to ChunkBench

Thanks for helping improve **ChunkBench**.

By submitting a pull request, you agree to the terms in [LICENSE](LICENSE).

## How to contribute

1. Open an issue describing the change when possible
2. Fork and create a branch
3. Keep the plugin compatible with **1.8.8 through 26.2** (no hard NMS; use reflection/fallbacks)
4. User-facing messages stay in **English**
5. Open a pull request against this repository

## Development

```bash
gradlew jar
```

Place the jar in a test server's `plugins/` folder and run `/chunkbench`.
