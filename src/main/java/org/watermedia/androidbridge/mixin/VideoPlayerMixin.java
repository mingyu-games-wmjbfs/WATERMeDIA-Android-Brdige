/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge.mixin;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.watermedia.androidbridge.VideoDiagnostics;
import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.videolan4j.factory.MediaPlayerFactory;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.embedded.videosurface.callback.BufferFormat;

/**
 * Observes WATERMeDIA's video players so that the log alone tells whether libvlc delivered
 * frames at all.
 *
 * <pre>
 * video player #1 created    -&gt; a VideoPlayer exists, libvlc is decoding
 * first video frame from VLC -&gt; the video output module produced a picture
 * video texture upload works -&gt; the frame reached the OpenGL texture
 * </pre>
 *
 * <p>When the first line appears without the second one, the blank screen is a libvlc
 * problem (no video output/decoder result) and not an OpenGL one - exactly the case that
 * is indistinguishable from the outside.  These hooks only read values; they never change
 * playback behaviour.</p>
 */
@Mixin(value = VideoPlayer.class, remap = false)
public abstract class VideoPlayerMixin {

    @Inject(method = "<init>(Lorg/watermedia/videolan4j/factory/MediaPlayerFactory;Ljava/util/concurrent/Executor;)V",
            at = @At(value = "RETURN"),
            require = 0,
            remap = false)
    private void bridge$onPlayerCreated(final MediaPlayerFactory factory, final Executor renderExecutor,
                                        final CallbackInfo ci) {
        VideoDiagnostics.playerCreated();
    }

    @Inject(method = "display(Lorg/watermedia/videolan4j/player/base/MediaPlayer;[Ljava/nio/ByteBuffer;"
                    + "Lorg/watermedia/videolan4j/player/embedded/videosurface/callback/BufferFormat;)V",
            at = @At(value = "HEAD"),
            require = 0,
            remap = false)
    private void bridge$onFrame(final MediaPlayer mediaPlayer, final ByteBuffer[] buffers,
                                final BufferFormat bufferFormat, final CallbackInfo ci) {
        if (bufferFormat == null) {
            VideoDiagnostics.frameDelivered(buffers == null ? 0 : buffers.length, -1, -1, "?");
        } else {
            VideoDiagnostics.frameDelivered(buffers == null ? 0 : buffers.length,
                    bufferFormat.getWidth(), bufferFormat.getHeight(), bufferFormat.getChroma());
        }
    }
}
