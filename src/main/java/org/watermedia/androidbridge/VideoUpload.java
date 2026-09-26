/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.lwjgl.opengl.GL11;

/**
 * Android safe replacement for {@code org.watermedia.api.render.RenderAPI.uploadBuffer}.
 *
 * <p>WATERMeDIA uploads every decoded video frame like this:</p>
 *
 * <pre>
 * GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, format, 0x8367, buffer);
 * GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, format, 0x8367, buffer);
 * </pre>
 *
 * <p>{@code 0x8367} is {@code GL_UNSIGNED_INT_8_8_8_8_REV}, a <b>desktop only</b> pixel
 * type.  OpenGL ES - what every Android driver speaks and what the GL translation layer
 * inside FCL/PojavLauncher forwards to - does not define it, so the call is rejected with
 * {@code GL_INVALID_ENUM} and the texture object ends up without storage.  Minecraft then
 * samples an incomplete texture, which is what produces a flat white/blank screen while
 * the audio keeps playing: the "video is stuck on the first frame" report from the device.</p>
 *
 * <p>For {@code format = GL_RGBA} both types describe the identical memory layout
 * (byte order R, G, B, A), so the substitution below is byte for byte equivalent and works
 * on desktop GL and GL ES alike.  On top of that this class</p>
 * <ul>
 *   <li>resets the unpack state (VLC hands us a tightly packed buffer),</li>
 *   <li>re-allocates the storage when the frame size changes instead of issuing a
 *       {@code glTexSubImage2D} that would be rejected as out of bounds,</li>
 *   <li>verifies every call with {@code glGetError()} and falls back to
 *       "allocate empty storage, then {@code glTexSubImage2D}",</li>
 *   <li><b>never throws</b>: WATERMeDIA's render task releases its frame semaphore only
 *       after {@code uploadBuffer} returns, so an exception here would leak the permit and
 *       wedge VLC's video output thread (video frozen forever, audio unaffected).</li>
 * </ul>
 */
public final class VideoUpload {
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_RGBA = 6408;
    private static final int GL_UNSIGNED_BYTE = 5121;
    private static final int GL_NO_ERROR = 0;

    /** texture id -> packed {@code (width << 32) | height} of the storage last allocated. */
    private static final Map<Integer, Long> ALLOCATED = new ConcurrentHashMap<>();

    private VideoUpload() {}

    /**
     * Uploads one VLC frame into {@code texture}; signature compatible with
     * {@code RenderAPI.uploadBuffer(ByteBuffer, int, int, int, int, boolean)}.
     */
    public static void upload(final ByteBuffer buffer, final int texture, final int format,
                              final int width, final int height, final boolean first) {
        try {
            if (buffer == null) {
                VideoDiagnostics.uploadFailed(texture, "VLC handed over no frame buffer");
                return;
            }
            if (texture <= 0) {
                VideoDiagnostics.uploadFailed(texture, "invalid texture id " + texture
                        + " (did WATERMeDIA's createTexture() run without a GL context?)");
                return;
            }
            if (width <= 0 || height <= 0) {
                VideoDiagnostics.uploadFailed(texture, "invalid frame size " + width + "x" + height);
                return;
            }

            final int pixelFormat = format == 0 ? GL_RGBA : format;
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);

            final long size = ((long) width << 32) | (height & 0xFFFFFFFFL);
            final Long known = ALLOCATED.get(texture);
            final boolean storageMissing = first || known == null || known.longValue() != size;

            if (!storageMissing) {
                clearErrors();
                GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        pixelFormat, GL_UNSIGNED_BYTE, buffer);
                final int error = firstError();
                if (error == GL_NO_ERROR) {
                    VideoDiagnostics.uploaded(texture, width, height, buffer.remaining(), false);
                    return;
                }
                VideoDiagnostics.glError("glTexSubImage2D", error, width, height);
                ALLOCATED.remove(texture);
            }

            if (allocate(buffer, texture, pixelFormat, width, height, size)) {
                VideoDiagnostics.uploaded(texture, width, height, buffer.remaining(), true);
                return;
            }
            VideoDiagnostics.uploadFailed(texture, String.format(
                    "no upload path worked for %dx%d (see the GL errors above)", width, height));
        } catch (final Throwable t) {
            // never propagate: WATERMeDIA releases its frame semaphore after this call returns
            VideoDiagnostics.uploadThrew(t);
        }
    }

    /**
     * Creates fresh storage for the frame.  Tries the one-shot upload first and, when the
     * driver refuses it, allocates empty storage and pushes the pixels with a sub-image
     * upload (the path most GL ES drivers actually implement).
     *
     * @return true when the frame reached the texture
     */
    private static boolean allocate(final ByteBuffer buffer, final int texture, final int format,
                                    final int width, final int height, final long size) {
        clearErrors();
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                format, GL_UNSIGNED_BYTE, buffer);
        int error = firstError();
        if (error == GL_NO_ERROR) {
            ALLOCATED.put(texture, Long.valueOf(size));
            return true;
        }
        VideoDiagnostics.glError("glTexImage2D", error, width, height);

        clearErrors();
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        error = firstError();
        if (error == GL_NO_ERROR) {
            clearErrors();
            GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                    format, GL_UNSIGNED_BYTE, buffer);
            error = firstError();
        }
        if (error == GL_NO_ERROR) {
            ALLOCATED.put(texture, Long.valueOf(size));
            return true;
        }
        VideoDiagnostics.glError("glTexImage2D(null)+glTexSubImage2D", error, width, height);
        return false;
    }

    private static void clearErrors() {
        try {
            for (int i = 0; i < 8 && GL11.glGetError() != GL_NO_ERROR; i++) {
                // drain the error queue so the next check only sees our own call
            }
        } catch (final Throwable ignored) {
            // no GL context on this thread - the upload below fails and is reported
        }
    }

    private static int firstError() {
        try {
            return GL11.glGetError();
        } catch (final Throwable t) {
            return -1;
        }
    }
}
