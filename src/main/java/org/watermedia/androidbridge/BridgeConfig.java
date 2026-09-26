/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Plain {@code .properties} configuration, written to
 * {@code config/watermedia_android_bridge.properties} on first launch.
 */
public final class BridgeConfig {
    private static final String FILE_NAME = BridgeLog.MOD_ID + ".properties";
    private static final String HEADER = """
            # WATERMeDIA: Android Bridge - configuration
            #
            # enabled               Activate the bridge. Set to false to fall back to the vanilla
            #                       WATERMeDIA behaviour (no bundled VLC, no patch at all).
            # abi                   Force a payload ABI (arm64-v8a, armeabi-v7a, x86_64) or "auto".
            # installLocation       "auto" or a base directory for the extracted VLC. Android refuses to
            #                       dlopen libraries from external/emulated storage (/storage/emulated/0),
            #                       so on Android the payload defaults to app internal storage and the
            #                       game directory is only used as a last resort.
            # forceInstall          Re-extract the bundled VLC on every launch. Only for debugging.
            # overrideFactory       Re-register WATERMeDIA's default VLC factories with the Android
            #                       argument set below (recommended on Android).
            # jnaFallback           Allow the bridge to point JNA at its bundled libjnidispatch.so
            #                       when the launcher does not provide one itself.
            # audioOutput           VLC audio output modules, tried in order.
            # extraVlcArguments     Extra libvlc switches, separated by commas or spaces.
            """;

    public boolean enabled = true;
    public String abi = "auto";
    public String installLocation = "auto";
    public boolean forceInstall = false;
    public boolean overrideFactory = true;
    public boolean jnaFallback = true;
    public String audioOutput = "opensles,audiotrack,any";
    public String extraVlcArguments = "";

    private final Path file;
    private final Properties properties = new Properties();

    private BridgeConfig(final Path file) {
        this.file = file;
    }

    public static BridgeConfig load(final Path configDir) {
        final Path file = configDir.resolve(FILE_NAME);
        final BridgeConfig config = new BridgeConfig(file);
        boolean created = false;
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    config.properties.load(in);
                }
            } else {
                created = true;
            }
        } catch (final Throwable t) {
            BridgeLog.warn("could not read {}, using defaults: {}", file, t.toString());
        }
        config.enabled = config.bool("enabled", config.enabled);
        config.abi = config.string("abi", config.abi).trim();
        config.installLocation = config.string("installLocation", config.installLocation).trim();
        config.forceInstall = config.bool("forceInstall", config.forceInstall);
        config.overrideFactory = config.bool("overrideFactory", config.overrideFactory);
        config.jnaFallback = config.bool("jnaFallback", config.jnaFallback);
        config.audioOutput = config.string("audioOutput", config.audioOutput).trim();
        config.extraVlcArguments = config.string("extraVlcArguments", config.extraVlcArguments).trim();
        if (created) config.save();
        return config;
    }

    private String string(final String key, final String fallback) {
        return this.properties.getProperty(key, fallback);
    }

    private boolean bool(final String key, final boolean fallback) {
        final String raw = this.properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }

    /** Persists the current values, creating the directory when needed. */
    public void save() {
        try {
            Files.createDirectories(this.file.getParent());
            this.properties.setProperty("enabled", Boolean.toString(this.enabled));
            this.properties.setProperty("abi", this.abi);
            this.properties.setProperty("installLocation", this.installLocation);
            this.properties.setProperty("forceInstall", Boolean.toString(this.forceInstall));
            this.properties.setProperty("overrideFactory", Boolean.toString(this.overrideFactory));
            this.properties.setProperty("jnaFallback", Boolean.toString(this.jnaFallback));
            this.properties.setProperty("audioOutput", this.audioOutput);
            this.properties.setProperty("extraVlcArguments", this.extraVlcArguments);
            try (OutputStream out = Files.newOutputStream(this.file)) {
                this.properties.store(out, HEADER);
            }
        } catch (final Throwable t) {
            BridgeLog.warn("could not write {}: {}", this.file, t.toString());
        }
    }

    public Path file() {
        return this.file;
    }

    /**
     * Builds the libvlc argument vector used for the Android default factories.
     * It mirrors WATERMeDIA's own {@code videolan/arguments_linux.json} (which is
     * what a non-Windows host gets) and adds the Android specific audio output.
     */
    public String[] vlcArguments() {
        final List<String> args = new ArrayList<>();
        args.add("--no-quiet");
        args.add("--network-synchronisation");
        args.add("--network-caching=1000");
        args.add("--live-caching=1000");
        args.add("--file-caching=1000");
        args.add("--no-metadata-network-access");
        args.add("--no-file-logging");
        args.add("--http-reconnect");
        args.add("--no-video-title-show");
        if (!this.audioOutput.isEmpty()) {
            // audiotrack would need the VLC Android Java layer, opensles is pure native.
            args.add("--aout=" + this.audioOutput);
        }
        for (final String extra : this.extraVlcArguments.split("[,\\s]+")) {
            if (!extra.isBlank()) args.add(extra.trim());
        }
        return args.toArray(new String[0]);
    }
}
