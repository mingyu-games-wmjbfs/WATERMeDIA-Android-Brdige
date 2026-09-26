/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Central logging entry point.  Everything the bridge prints goes through the
 * normal log4j logger WATERMeDIA already uses, so a single {@code latest.log}
 * is enough to diagnose an Android installation.
 */
public final class BridgeLog {
    public static final String MOD_ID = "watermedia_android_bridge";
    public static final String MOD_NAME = "WATERMeDIA: Android Bridge";
    public static final Logger LOG = LogManager.getLogger(MOD_NAME);

    private BridgeLog() {}

    public static void info(final String message, final Object... args) {
        LOG.info(message, args);
    }

    public static void warn(final String message, final Object... args) {
        LOG.warn(message, args);
    }

    public static void error(final String message, final Object... args) {
        LOG.error(message, args);
    }

    public static void debug(final String message, final Object... args) {
        LOG.debug(message, args);
    }
}
