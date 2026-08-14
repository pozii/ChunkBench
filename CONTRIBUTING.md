# Contributing to ChunkBench

Thanks for helping improve **ChunkBench** (author: **pozii**).

## License agreement

By submitting a pull request or other contribution, you agree that your contribution is licensed under the **PolyForm Shield 1.0.0** terms in [LICENSE](LICENSE), and that you have the right to submit it under those terms.

ChunkBench is source-available with a **noncompete** clause: do not use this codebase to provide a competing product or to integrate a practical substitute into another application.

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
