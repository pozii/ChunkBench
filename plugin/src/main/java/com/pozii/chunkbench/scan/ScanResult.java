package com.pozii.chunkbench.scan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScanResult {
    public final String rootPath;
    public final List<String> startupScripts;
    public final List<String> serverJars;
    public final List<String> configs;
    public final List<ServerRootScanner.PluginJar> plugins;
    public final boolean hasServerProperties;
    public final boolean hasPaperConfig;
    public final boolean hasSpigotConfig;
    public final boolean hasBukkitConfig;
    public final List<String> notes;
    public final int filesSeen;

    public ScanResult(String rootPath,
                      List<String> startupScripts,
                      List<String> serverJars,
                      List<String> configs,
                      List<ServerRootScanner.PluginJar> plugins,
                      boolean hasServerProperties,
                      boolean hasPaperConfig,
                      boolean hasSpigotConfig,
                      boolean hasBukkitConfig,
                      List<String> notes,
                      int filesSeen) {
        this.rootPath = rootPath;
        this.startupScripts = Collections.unmodifiableList(new ArrayList<String>(startupScripts));
        this.serverJars = Collections.unmodifiableList(new ArrayList<String>(serverJars));
        this.configs = Collections.unmodifiableList(new ArrayList<String>(configs));
        this.plugins = Collections.unmodifiableList(new ArrayList<ServerRootScanner.PluginJar>(plugins));
        this.hasServerProperties = hasServerProperties;
        this.hasPaperConfig = hasPaperConfig;
        this.hasSpigotConfig = hasSpigotConfig;
        this.hasBukkitConfig = hasBukkitConfig;
        this.notes = Collections.unmodifiableList(new ArrayList<String>(notes));
        this.filesSeen = filesSeen;
    }
}
