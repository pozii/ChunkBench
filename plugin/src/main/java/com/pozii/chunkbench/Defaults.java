package com.pozii.chunkbench;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Hardcoded plugin defaults (not exposed in config.yml). */
public final class Defaults {

    public static final boolean MCLOGS_ENABLED = true;
    public static final String MCLOGS_SOURCE = "ChunkBench";
    public static final String MCLOGS_URL = "https://api.mclo.gs/1/log";

    public static final long COOLDOWN_MS = 60_000L;

    public static final int SCAN_MAX_DEPTH = 6;
    public static final int SCAN_MAX_FILES = 2500;

    public static final List<String> SCAN_EXCLUDE_DIRS = Collections.unmodifiableList(Arrays.asList(
            "world", "world_nether", "world_the_end", "cache", "libraries", "versions", ".git"
    ));

    public static final List<String> SCAN_WORLD_MARKERS = Collections.unmodifiableList(Arrays.asList(
            "level.dat", "region", "DIM-1", "DIM1", "dimensions"
    ));

    public static final String DEFAULT_LANGUAGE = "en_US";
    public static final String DEFAULT_PREFIX = "&8[&bChunkBench&8]&r ";

    private Defaults() {
    }
}
