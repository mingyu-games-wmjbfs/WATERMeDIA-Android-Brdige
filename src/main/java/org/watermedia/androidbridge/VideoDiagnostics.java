/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.watermedia.androidbridge;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters and log lines for the video path, so a single {@code latest.log} from a phone
 * is enough to tell <i>where</i> a white/blank screen comes from:
 *
 * <ul>
 *   <li>{@code video player #N created} but no {@code first video frame from VLC} line
 *       = the frame never left libvlc (video output module / decoder problem).</li>
 *   <li>{@code first video frame from VLC} but no {@code video texture upload path active}
 *       = the GL upload is refused (see the {@code GL error} lines).</li>
 *   <li>both lines present and the screen still stays blank = the problem is on the
 *       renderer side, not on VLC's.</li>
 * </ul>
 *
 * <p>Every entry point swallows its own failures: diagnostics must never influence
 * playback.</p>
 */
public final class VideoDiagnostics {
    /** How long a created player may stay silent before the bridge complains. */
    private static final long FRAME_TIMEOUT_MS = 20_000L;
    private static final long HEARTBEAT_MS = 30_000L;
    private static final int GL_ERROR_DETAIL_LIMIT = 3;

    private static final AtomicLong PLAYERS = new AtomicLong();
    private static final AtomicLong FRAMES = new AtomicLong();
    private static final AtomicLong UPLOADS = new AtomicLong();
    private static final AtomicLong GL_ERRORS = new AtomicLong();
    private static final AtomicLong UPLOAD_FAILURES = new AtomicLong();
    private static final AtomicBoolean FIRST_FRAME_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FIRST_UPLOAD_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean SILENT_PLAYER_WARNED = new AtomicBoolean();

    private static volatile long firstPlayerAt;
    private static volatile long lastFrameAt;
    private static volatile long nextHeartbeatAt;
    private static volatile int lastWidth = -1;
    private static volatile int lastHeight = -1;
    private static volatile String lastFormat = "?";

    private VideoDiagnostics() {}

    /** Called from the {@code VideoPlayer} constructor. */
    public static void playerCreated() {
        try {
            final long now = System.currentTimeMillis();
            final long count = PLAYERS.incrementAndGet();
            if (count == 1L) firstPlayerAt = now;
            if (nextHeartbeatAt == 0L) nextHeartbeatAt = now + HEARTBEAT_MS;
            BridgeLog.info("video player #{} created; waiting for the first decoded frame "
                    + "(a flat white screen from here on means libvlc delivered no picture)", count);
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Called from {@code VideoPlayer.display}, i.e. once per frame libvlc hands over. */
    public static void frameDelivered(final int planes, final int width, final int height, final Object chroma) {
        try {
            final long now = System.currentTimeMillis();
            FRAMES.incrementAndGet();
            lastFrameAt = now;
            lastWidth = width;
            lastHeight = height;
            if (FIRST_FRAME_LOGGED.compareAndSet(false, true)) {
                lastFormat = String.valueOf(chroma);
                BridgeLog.info("first video frame from VLC: {}x{}, {} plane(s), chroma {} "
                        + "- the decoder and the video output work; the bridge now only has to get "
                        + "this into the OpenGL texture", width, height, planes, lastFormat);
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Called after a frame reached the texture. */
    public static void uploaded(final int texture, final int width, final int height,
                                final int bytes, final boolean allocated) {
        try {
            UPLOADS.incrementAndGet();
            lastWidth = width;
            lastHeight = height;
            if (FIRST_UPLOAD_LOGGED.compareAndSet(false, true)) {
                BridgeLog.info("video texture upload works: texture {} <- {}x{} ({} bytes, {}, "
                        + "pixel type GL_UNSIGNED_BYTE - the Android safe replacement for "
                        + "GL_UNSIGNED_INT_8_8_8_8_REV)",
                        texture, width, height, bytes, allocated ? "fresh storage" : "sub-image");
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Called when an OpenGL call in the upload path returned an error. */
    public static void glError(final String call, final int error, final int width, final int height) {
        try {
            final long count = GL_ERRORS.incrementAndGet();
            if (count <= GL_ERROR_DETAIL_LIMIT) {
                BridgeLog.warn("{} failed for {}x{} with {}; trying the next upload path",
                        call, width, height, describeGlError(error));
            } else if (count == GL_ERROR_DETAIL_LIMIT + 1) {
                BridgeLog.warn("further OpenGL upload errors are only counted ({} so far)", count);
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Called when the frame could not be uploaded at all. */
    public static void uploadFailed(final int texture, final String reason) {
        try {
            final long count = UPLOAD_FAILURES.incrementAndGet();
            if (count <= GL_ERROR_DETAIL_LIMIT) {
                BridgeLog.error("could not upload a video frame to texture {}: {}", texture, reason);
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Called when the upload path threw; swallowed so WATERMeDIA's semaphore stays balanced. */
    public static void uploadThrew(final Throwable t) {
        try {
            final long count = UPLOAD_FAILURES.incrementAndGet();
            if (count <= GL_ERROR_DETAIL_LIMIT) {
                BridgeLog.error("the video frame upload threw and was swallowed to keep VLC's frame "
                        + "semaphore balanced (video may stall instead of freezing the whole player)", t);
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /**
     * Called once per client tick: warns when a player never produced a frame and prints a
     * periodic one line summary while frames are flowing.
     */
    public static void tick() {
        try {
            final long now = System.currentTimeMillis();
            if (PLAYERS.get() > 0L && FRAMES.get() == 0L
                    && firstPlayerAt > 0L && now - firstPlayerAt > FRAME_TIMEOUT_MS
                    && SILENT_PLAYER_WARNED.compareAndSet(false, true)) {
                BridgeLog.warn("no video frame from VLC in the {} s after the first player was created: "
                                + "the video output or decoder produced nothing, so there is nothing the "
                                + "bridge can upload (audio is a separate pipeline and keeps working). "
                                + "The libvlc messages above name the module it picked.",
                        (now - firstPlayerAt) / 1000L);
            }
            if (FRAMES.get() > 0L && now >= nextHeartbeatAt) {
                nextHeartbeatAt = now + HEARTBEAT_MS;
                BridgeLog.info("video: {} frame(s), {} texture upload(s), {} GL error(s), "
                                + "{} upload failure(s), last {}x{} chroma {}",
                        FRAMES.get(), UPLOADS.get(), GL_ERRORS.get(), UPLOAD_FAILURES.get(),
                        lastWidth, lastHeight, lastFormat);
            }
        } catch (final Throwable ignored) {
            // diagnostics only
        }
    }

    /** Human readable name for the GL error codes the upload path can hit. */
    public static String describeGlError(final int error) {
        switch (error) {
            case 0x0500: return "GL_INVALID_ENUM (0x0500) - the pixel type/format is not accepted by this GL implementation";
            case 0x0501: return "GL_INVALID_VALUE (0x0501) - the frame does not fit the texture storage";
            case 0x0502: return "GL_INVALID_OPERATION (0x0502) - the texture object or the unpack state is wrong";
            case 0x0503: return "GL_STACK_OVERFLOW (0x0503)";
            case 0x0504: return "GL_STACK_UNDERFLOW (0x0504)";
            case 0x0505: return "GL_OUT_OF_MEMORY (0x0505) - the driver ran out of GPU memory";
            case 0x0506: return "GL_INVALID_FRAMEBUFFER_OPERATION (0x0506)";
            case -1: return "no GL context on this thread (-1)";
            case 0: return "no error (0)";
            default: return String.format("0x%04X", error);
        }
    }

    // ------------------------------------------------------------------ accessors (harness)

    public static long frames() {
        return FRAMES.get();
    }

    public static long uploads() {
        return UPLOADS.get();
    }

    public static long glErrors() {
        return GL_ERRORS.get();
    }

    public static long players() {
        return PLAYERS.get();
    }
}
