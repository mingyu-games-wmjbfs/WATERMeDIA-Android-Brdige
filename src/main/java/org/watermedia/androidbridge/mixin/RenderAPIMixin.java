/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.watermedia.androidbridge.BridgeLog;
import org.watermedia.api.render.RenderAPI;

/**
 * Makes WATERMeDIA's image buffers survive a failing aligned native allocation.
 *
 * <p>WATERMeDIA allocates every decoded image frame through
 * {@code RenderAPI.createByteBuffer}, which prefers LWJGL's
 * {@code MemoryUtil.memAlignedAlloc}:</p>
 *
 * <pre>
 * public static ByteBuffer createByteBuffer(int alignment, int size) {
 *     if (ADVANCED_LWJGL) {
 *         return MemoryUtil.memAlignedAlloc(alignment, size);   // &lt;-- redirected here
 *     } else {
 *         return ByteBuffer.allocateDirect(size);               // WATERMeDIA's own fallback
 *     }
 * }
 * </pre>
 *
 * <p>On Android that aligned allocation can return {@code NULL} (LWJGL then throws
 * {@code OutOfMemoryError}) even for tiny requests - on the FCL report the failing
 * request was 160,000 bytes while the Java heap sat at 239 MiB of 2152 MiB, i.e. the
 * process simply had no native memory left.  Because the emitters are
 * {@code ImageAPI.start} during {@code NeoFLoader}'s constructor and that constructor
 * only catches {@code Exception}, such an {@code Error} turns into
 * "Mod loading has failed" and the game never starts.</p>
 *
 * <p>This redirect retries with WATERMeDIA's own non-LWJGL path
 * ({@code ByteBuffer.allocateDirect}, also a direct buffer, so later
 * {@code MemoryUtil.memAddress}/{@code glTexImage2D} uploads keep working) and only
 * rethrows when that fails as well.  It cannot create memory - if the process is
 * genuinely out of native memory the boot still fails - which is why the log line it
 * emits names the actual cure: reduce {@code -Xms}/{@code -Xmx} for the instance.</p>
 */
@Mixin(value = RenderAPI.class, remap = false)
public abstract class RenderAPIMixin {

    @Redirect(
            method = "createByteBuffer(II)Ljava/nio/ByteBuffer;",
            at = @At(value = "INVOKE",
                    target = "Lorg/lwjgl/system/MemoryUtil;memAlignedAlloc(II)Ljava/nio/ByteBuffer;"),
            require = 0,
            remap = false)
    private static ByteBuffer bridge$memAlignedAllocOrDirect(final int alignment, final int size) {
        try {
            return org.lwjgl.system.MemoryUtil.memAlignedAlloc(alignment, size);
        } catch (final Throwable alignedFailure) {
            BridgeLog.warn("LWJGL's aligned native allocation of {} bytes failed ({}); "
                            + "retrying with a plain direct buffer. If this repeats, the JVM has no native memory "
                            + "left: lower -Xms/-Xmx for this instance (for example -Xms512m -Xmx1536m) "
                            + "and close other apps.",
                    size, alignedFailure);
            try {
                return ByteBuffer.allocateDirect(size);
            } catch (final Throwable directFailure) {
                BridgeLog.error("the direct buffer fallback of {} bytes failed too ({}); "
                        + "WATERMeDIA needs native memory to start - reduce -Xms (the launcher fixing -Xms2048m "
                        + "commits 2 GiB up front) or remove memory hungry mods", size, directFailure);
                throw alignedFailure;
            }
        }
    }
}
