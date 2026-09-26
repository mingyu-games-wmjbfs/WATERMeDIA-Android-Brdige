/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import org.watermedia.videolan4j.discovery.providers.IProvider;

/**
 * Secondary path into WATERMeDIA's native discovery: it is registered through
 * {@code META-INF/services} exactly like WATERMeDIA's own providers, so the
 * bundled VLC is found even when a user removed or overwrote
 * {@code config/watermedia/custom_vlc_path.txt}.
 */
public final class VlcDirectoryProvider implements IProvider {
    @Override
    public String name() {
        return "WATERMeDIA Android Bridge";
    }

    @Override
    public Priority priority() {
        return Priority.HIGHEST;
    }

    @Override
    public boolean supported() {
        return AndroidVlc.isInstalled();
    }

    @Override
    public String[] directories() {
        if (!AndroidVlc.isInstalled()) return new String[0];
        return new String[] { AndroidVlc.vlcDirectory().toAbsolutePath().toString() };
    }
}
