/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod entry point for Minecraft 1.20.1 / Forge.
 *
 * <p>Identical in behaviour to the NeoForge entry point of the same name in
 * {@code src/loader/neoforge}; only the loader API differs (Forge's {@code @Mod} has no
 * {@code dist} member, so the client-only restriction lives on the tick subscriber).</p>
 *
 * <p>The heavy lifting happens in {@link AndroidVlc#bootstrapEarly()} during mod
 * construction: because this mod declares {@code ordering = "BEFORE"} on its WATERMeDIA
 * dependency, the bundled VLC is extracted and registered before WATERMeDIA's own
 * {@code ForgeLoader} runs.  The client tick hook is a safety net that covers the opposite
 * load order (and a WATERMeDIA that already gave up on VLC before this mod was
 * constructed).</p>
 */
@Mod(BridgeLog.MOD_ID)
public final class AndroidBridge {
    public AndroidBridge() {
        AndroidVlc.bootstrapEarly();
    }

    /** Client tick safety net; {@code Dist.CLIENT} keeps it off dedicated servers. */
    @Mod.EventBusSubscriber(modid = BridgeLog.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientTick {
        @SubscribeEvent
        public static void onClientTick(final TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) AndroidVlc.clientTick();
        }
    }
}
