/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.os;

import java.io.File;

/**
 * Minimal stand-in for Android's framework class of the same name.
 *
 * <p><strong>Why this exists.</strong> The Android build of VLC's {@code libvlc.so}
 * contains a JNI initialiser that the JVM only runs when the library is loaded
 * through {@code System.load}/{@code System.loadLibrary} (see VLC's
 * {@code src/android/specific.c}):</p>
 *
 * <pre>
 * static JavaVM *s_jvm = NULL;                                  // specific.c:36
 *
 * JNI_OnLoad(JavaVM *vm, void *reserved) {                      // specific.c:100
 *     s_jvm = vm;                                               // specific.c:103
 *     jclass clazz = FindClass(env, "android/os/Environment");   // specific.c:109
 *     if (ExceptionCheck(env)) return -1;                        // specific.c:110
 *     ... reads DIRECTORY_DOWNLOADS/DOCUMENTS/MUSIC/PICTURES/MOVIES ...
 *     fields.Environment.getExternalStoragePublicDirectory =
 *         GetStaticMethodID(clazz, "getExternalStoragePublicDirectory",
 *                           "(Ljava/lang/String;)Ljava/io/File;");  // specific.c:131
 *     ...
 * }
 *
 * void system_Configure(libvlc_int_t *p_libvlc, ...) {           // specific.c:170
 *     assert(s_jvm != NULL);                                     // specific.c:174  &lt;-- aborts
 * }
 * </pre>
 *
 * <p>Minecraft's JVM has no Android framework on its class path, so that
 * {@code FindClass} would fail, {@code JNI_OnLoad} would return {@code -1}, and
 * the JVM would report a bad JNI version and (depending on the VM) unload the
 * library again - leaving {@code s_jvm} null and making {@code libvlc_new} abort
 * the whole process.  This class supplies exactly the shape VLC looks up, so
 * {@code JNI_OnLoad} completes and {@code s_jvm} stays valid.</p>
 *
 * <p>JNI resolves {@code FindClass} with the class loader of the class that
 * called {@code System.load}, which is the mod's own loader - hence the stub
 * ships inside this jar.  The members must keep their exact names, modifiers and
 * descriptor: {@code GetStaticFieldID(..., "Ljava/lang/String;")} and
 * {@code GetStaticMethodID(..., "(Ljava/lang/String;)Ljava/io/File;")}.</p>
 *
 * <p>On a real desktop JVM nothing references this class, and without
 * {@code android.*} on the class path it can never shadow a genuine Android
 * implementation.</p>
 */
public final class Environment {

    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static final String DIRECTORY_DOCUMENTS = "Documents";
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_MOVIES = "Movies";

    private Environment() {}

    /**
     * VLC only needs the absolute path of a per-type media folder; it uses the
     * returned value for its default media directories.  A non-existent path is
     * harmless - VLC logs it and carries on with its own defaults.
     */
    public static File getExternalStoragePublicDirectory(final String type) {
        return new File(externalStorage(), type);
    }

    private static String externalStorage() {
        String root = null;
        try {
            root = System.getenv("EXTERNAL_STORAGE");
        } catch (final Throwable ignored) {
            // some environments refuse getenv
        }
        if (root == null || root.isEmpty() || "/storage".equals(root) || "/sdcard".equals(root)) {
            return "/storage/emulated/0";
        }
        return root;
    }
}
