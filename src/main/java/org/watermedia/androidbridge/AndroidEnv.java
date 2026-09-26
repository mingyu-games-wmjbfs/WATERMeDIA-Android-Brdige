/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import java.io.File;
import java.util.Locale;

/**
 * Runtime environment probes.  Everything here must stay free of Minecraft and
 * WATERMeDIA types so that it can be unit tested on a desktop JVM.
 */
public final class AndroidEnv {
    /** {@code -Dwatermedia.androidbridge.forceAndroid=true} makes the bridge run its Android path anywhere. */
    public static final String PROP_FORCE_ANDROID = "watermedia.androidbridge.forceAndroid";
    /** {@code -Dwatermedia.androidbridge.gameDir=<path>} overrides the game directory lookup. */
    public static final String PROP_GAME_DIR = "watermedia.androidbridge.gameDir";

    private static Boolean android;

    private AndroidEnv() {}

    /**
     * Heuristic Android detection.
     * <p>
     * PojavLauncher runs a normal OpenJDK build on top of bionic, so
     * {@code os.name} is {@code "Linux"} and cannot be used.  We therefore look at
     * the environment Android's zygote always exports, the JVM name and a couple of
     * well known system paths.
     */
    public static boolean isAndroid() {
        final Boolean cached = android;
        if (cached != null) return cached;
        final boolean detected = detect();
        android = detected;
        return detected;
    }

    private static boolean detect() {
        final String forced = System.getProperty(PROP_FORCE_ANDROID);
        if (forced != null && !forced.isEmpty()) return Boolean.parseBoolean(forced);
        try {
            if (System.getenv("ANDROID_ROOT") != null) return true;
            if (System.getenv("ANDROID_DATA") != null) return true;
            if (System.getenv("ANDROID_ASSETS") != null) return true;
            if (System.getenv("POJAV_NATIVEDIR") != null) return true;
            if (System.getenv("POJAV_HOME") != null) return true;
        } catch (final Throwable ignored) {
            // SecurityManager or a JVM that refuses getenv - keep probing.
        }
        try {
            final String vm = (String.valueOf(System.getProperty("java.vm.name", "")) + ' '
                    + String.valueOf(System.getProperty("java.runtime.name", ""))).toLowerCase(Locale.ROOT);
            if (vm.contains("dalvik") || vm.contains("android runtime") || vm.contains("art ")) return true;
        } catch (final Throwable ignored) {
            // ignore
        }
        return exists("/system/build.prop") || exists("/system/bin/app_process") || exists("/system/bin/app_process64");
    }

    private static boolean exists(final String path) {
        try {
            return new File(path).exists();
        } catch (final Throwable ignored) {
            return false;
        }
    }

    /**
     * Maps {@code os.arch} onto the Android ABI directory name, or returns
     * {@code null} when the architecture has no bundled payload.
     */
    public static String abi() {
        return abiOf(System.getProperty("os.arch", ""));
    }

    static String abiOf(final String rawArch) {
        final String arch = String.valueOf(rawArch).toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64-v8a";
        if (arch.startsWith("arm")) return "armeabi-v7a";
        if (arch.contains("x86_64") || arch.contains("amd64")) return "x86_64";
        if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) return "x86";
        return null;
    }

    /** Human readable description used in log lines. */
    public static String describe() {
        return System.getProperty("os.name") + '/' + System.getProperty("os.arch")
                + " java=" + System.getProperty("java.version")
                + " vm=" + System.getProperty("java.vm.name");
    }
}
