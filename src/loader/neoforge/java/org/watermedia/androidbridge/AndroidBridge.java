/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mod entry point.
 *
 * <p>The heavy lifting happens in {@link AndroidVlc#bootstrapEarly()} during mod
 * construction: because this mod declares {@code ordering = "BEFORE"} on its
 * WATERMeDIA dependency, the bundled VLC is extracted and registered before
 * WATERMeDIA's own {@code NeoFLoader} runs.  The client tick hook is a safety net
 * that covers the opposite load order (and a WATERMeDIA that already gave up on
 * VLC before this mod was constructed).
 */
@Mod(value = BridgeLog.MOD_ID, dist = Dist.CLIENT)
public final class AndroidBridge {
    public AndroidBridge() {
        AndroidVlc.bootstrapEarly();
        try {
            NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, AndroidBridge::onClientTick);
        } catch (final Throwable t) {
            BridgeLog.warn("could not install the client tick safety net: {}", t.toString());
        }
    }

    private static void onClientTick(final ClientTickEvent.Post event) {
        AndroidVlc.clientTick();
    }
}
