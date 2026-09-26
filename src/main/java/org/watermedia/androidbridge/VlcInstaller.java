/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts the VLC-for-Android payload that ships inside this mod jar.
 *
 * <p>The payload is a monolithic {@code libvlc.so} (VLC 3.0.x built for Android,
 * with the whole module bank statically linked) plus the C++ runtime it links
 * against.  WATERMeDIA's {@code videolan4j} discovery only needs a directory that
 * contains {@code libvlc.so} and {@code libvlccore.so} plus a readable
 * {@code plugins/} folder, so this installer produces exactly that layout:
 *
 * <pre>
 * &lt;game&gt;/config/watermedia_android_bridge/vlc/&lt;abi&gt;/
 *     libvlc.so            copied from the bundled payload
 *     libvlccore.so        hard link / copy of libvlc.so (see {@link #ensureCoreAlias})
 *     libc++_shared.so     preloaded before libvlc is dlopen'ed
 *     plugins/             empty, required by videolan4j's plugin path probe
 *     jna/                 optional fallback JNA dispatch library
 *     bridge-payload.cfg   version marker, guards re-extraction
 * </pre>
 */
public final class VlcInstaller {
    public static final String RES_ROOT = "/watermedia_android";
    private static final String MANIFEST_RES = RES_ROOT + "/payload.cfg";
    static final String MARKER_NAME = "bridge-payload.cfg";
    private static final int BUFFER = 1 << 16;

    private static VlcPayload cachedPayload;

    private VlcInstaller() {}

    /** Outcome of an install attempt. */
    public static final class Result {
        public final boolean ok;
        public final boolean extracted;
        public final Path directory;
        public final String detail;

        Result(final boolean ok, final boolean extracted, final Path directory, final String detail) {
            this.ok = ok;
            this.extracted = extracted;
            this.directory = directory;
            this.detail = detail;
        }

        @Override
        public String toString() {
            return (this.ok ? "ok" : "failed") + (this.extracted ? "/extracted" : "/cached")
                    + " " + this.directory + " (" + this.detail + ')';
        }
    }

    public static synchronized VlcPayload payload() throws IOException {
        final VlcPayload cached = cachedPayload;
        if (cached != null) return cached;

        try (InputStream in = VlcInstaller.class.getResourceAsStream(MANIFEST_RES)) {
            if (in == null) throw new IOException("bundled payload manifest is missing: " + MANIFEST_RES);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String payloadVersion = null;
            String vlcVersion = null;
            String vlcSource = null;
            String jnaVersion = null;
            final List<String[]> files = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                final String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("file=")) {
                    // file=<abi>/<name>;<size>;<kind>
                    final String[] parts = trimmed.substring("file=".length()).split(";");
                    if (parts.length >= 3) files.add(new String[] { parts[2].trim(), parts[0].trim(), parts[1].trim() });
                    continue;
                }
                final int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                final String key = trimmed.substring(0, eq).trim();
                final String value = trimmed.substring(eq + 1).trim();
                switch (key) {
                    case "payloadVersion" -> payloadVersion = value;
                    case "vlcVersion" -> vlcVersion = value;
                    case "vlcSource" -> vlcSource = value;
                    case "jnaVersion" -> jnaVersion = value;
                    default -> { }
                }
            }
            final VlcPayload payload = new VlcPayload(
                    payloadVersion == null ? "unknown" : payloadVersion,
                    vlcVersion == null ? "unknown" : vlcVersion,
                    vlcSource == null ? "unknown" : vlcSource,
                    jnaVersion);
            for (final String[] file : files) {
                long size = -1L;
                try {
                    size = Long.parseLong(file[2]);
                } catch (final NumberFormatException ignored) {
                    // keep -1, meaning "size not verified"
                }
                payload.add(file[0], file[1], size);
            }
            cachedPayload = payload;
            return payload;
        }
    }

    /**
     * Installs (or reuses) the payload of the given ABI below the game directory.
     *
     * @param gameDir          the Minecraft instance directory
     * @param abi              Android ABI folder name, e.g. {@code arm64-v8a}
     * @param force            re-extract even when the version marker matches
     */
    public static Result install(final Path gameDir, final String abi, final boolean force) {
        return installAt(vlcDirectory(gameDir, abi), abi, force);
    }

    /** Installs (or reuses) the payload into an explicit directory. */
    public static Result installAt(final Path directory, final String abi, final boolean force) {
        try {
            final VlcPayload payload = payload();
            final List<String> nativeFiles = payload.filesFor(payload.natives, abi);
            if (nativeFiles.isEmpty()) {
                return new Result(false, false, directory,
                        "this build bundles no VLC payload for ABI '" + abi + "' (available: " + payload.abis() + ')');
            }

            final String marker = payload.payloadVersion + '|' + payload.vlcVersion + '|' + abi;
            if (!force && markerMatches(directory, marker) && verify(directory, payload.natives, abi, nativeFiles)) {
                return new Result(true, false, directory, "payload " + payload.payloadVersion + " already extracted");
            }

            deleteRecursively(directory);
            Files.createDirectories(directory);

            for (final String name : nativeFiles) {
                final String relative = abi + '/' + name;
                extract(RES_ROOT + "/natives/" + relative, directory.resolve(name), payload.natives.get(relative));
            }

            // videolan4j's NativeDiscovery#setPluginPath refuses a directory without a
            // readable "plugins" (or "vlc/plugins") folder.  The Android libvlc is a
            // static-module build, so an empty folder is all that is required.
            Files.createDirectories(directory.resolve("plugins"));
            ensureCoreAlias(directory);

            final List<String> jnaFiles = payload.filesFor(payload.jna, abi);
            for (final String name : jnaFiles) {
                final String relative = abi + '/' + name;
                extract(RES_ROOT + "/jna/" + relative, directory.resolve("jna").resolve(name), payload.jna.get(relative));
            }

            Files.writeString(directory.resolve(MARKER_NAME), marker + System.lineSeparator(), StandardCharsets.UTF_8);

            final long bytes = directorySize(directory);
            return new Result(true, true, directory,
                    "extracted " + nativeFiles.size() + " native file(s) for " + abi
                            + ", VLC " + payload.vlcVersion + ", " + (bytes / (1024L * 1024L)) + " MiB");
        } catch (final Throwable t) {
            return new Result(false, false, directory, "extraction failed: " + t);
        }
    }

    /** {@code <game>/config/watermedia_android_bridge/vlc/<abi>}. */
    public static Path vlcRoot(final Path gameDir) {
        return gameDir.resolve("config").resolve(BridgeLog.MOD_ID).resolve("vlc");
    }

    public static Path vlcDirectory(final Path gameDir, final String abi) {
        return vlcRoot(gameDir).resolve(abi);
    }

    /**
     * videolan4j refuses a payload directory whose {@code plugins} folder is not
     * readable and executable.  On Android the game directory frequently lives on
     * emulated (FUSE) storage where those POSIX checks are unreliable, so the
     * bridge verifies the layout itself and can relocate to app internal storage.
     */
    public static boolean layoutUsable(final Path directory) {
        try {
            final Path plugins = directory.resolve("plugins");
            if (!Files.isDirectory(plugins)) return false;
            if (!plugins.toFile().canRead() || !plugins.toFile().canExecute()) return false;
            final Path vlc = directory.resolve("libvlc.so");
            return Files.isRegularFile(vlc) && vlc.toFile().canRead();
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean markerMatches(final Path directory, final String expected) {
        final Path marker = directory.resolve(MARKER_NAME);
        try {
            if (!Files.isRegularFile(marker)) return false;
            return expected.equals(Files.readString(marker, StandardCharsets.UTF_8).trim());
        } catch (final Throwable ignored) {
            return false;
        }
    }

    private static boolean verify(final Path directory, final java.util.Map<String, Long> sizes,
                                  final String abi, final List<String> files) {
        try {
            for (final String name : files) {
                final Path file = directory.resolve(name);
                if (!Files.isRegularFile(file)) return false;
                final Long expected = sizes.get(abi + '/' + name);
                if (expected != null && expected > 0 && Files.size(file) != expected) return false;
            }
            if (!Files.isDirectory(directory.resolve("plugins"))) return false;
            return Files.exists(directory.resolve("libvlccore.so"));
        } catch (final Throwable ignored) {
            return false;
        }
    }

    /**
     * videolan4j's {@code Environment.LINUX} file patterns require both
     * {@code libvlc.so} and {@code libvlccore.so} to exist in the discovered
     * directory.  The Android build is monolithic (core + libvlc in one file,
     * SONAME {@code libvlc.so}), so an alias is enough: a hard link when the
     * filesystem allows it, otherwise a symlink or a plain copy.
     */
    private static void ensureCoreAlias(final Path directory) {
        final Path core = directory.resolve("libvlccore.so");
        final Path vlc = directory.resolve("libvlc.so");
        if (Files.exists(core)) return;
        try {
            Files.createLink(core, vlc);
            BridgeLog.debug("created hard link {} -> {}", core.getFileName(), vlc.getFileName());
            return;
        } catch (final Throwable ignored) {
            // sdcardfs / FUSE usually refuses hard links
        }
        try {
            Files.createSymbolicLink(core, vlc.getFileName());
            BridgeLog.debug("created symlink {} -> {}", core.getFileName(), vlc.getFileName());
            return;
        } catch (final Throwable ignored) {
            // symlinks are refused on most Android external storage
        }
        try {
            Files.copy(vlc, core, StandardCopyOption.REPLACE_EXISTING);
            BridgeLog.debug("copied {} to {} (hard links unsupported here)", vlc.getFileName(), core.getFileName());
            return;
        } catch (final Throwable ignored) {
            // fall through to the placeholder
        }
        try {
            Files.writeString(core, "libvlccore is part of the monolithic libvlc.so shipped by this mod\n",
                    StandardCharsets.UTF_8);
            BridgeLog.warn("could not alias libvlc.so as libvlccore.so, wrote a placeholder instead");
        } catch (final Throwable t) {
            BridgeLog.error("unable to create the libvlccore.so alias in {}", directory, t);
        }
    }

    private static void extract(final String resource, final Path target, final Long expectedSize) throws IOException {
        try (InputStream in = VlcInstaller.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("bundled resource is missing: " + resource);
            Files.createDirectories(target.getParent());
            long written = 0L;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE), BUFFER)) {
                final byte[] buffer = new byte[BUFFER];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    written += read;
                }
            }
            if (expectedSize != null && expectedSize > 0 && written != expectedSize) {
                throw new IOException("size mismatch for " + resource + ": wrote " + written + " bytes, expected " + expectedSize);
            }
        }
    }

    static void deleteRecursively(final Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            final List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (final Path path : paths) {
                try {
                    Files.deleteIfExists(path);
                } catch (final IOException ignored) {
                    // best effort, a locked file must not abort the installation
                }
            }
        }
    }

    private static long directorySize(final Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (final IOException ignored) {
                    return 0L;
                }
            }).sum();
        } catch (final Throwable ignored) {
            return 0L;
        }
    }
}
