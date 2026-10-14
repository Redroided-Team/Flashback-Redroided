// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

/**
 * Display density of the Android device, read reflectively: launchers run in
 * an Android app process, so android.* classes are present there but not on a
 * desktop JVM. Used as the answer when the windower cannot report a content
 * scale, so Flashback scales its UI to the screen instead of to a broken
 * zero. The probe runs once per process and fails soft to 1.0.
 */
public final class AndroidDisplay {

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 4.0f;

    private static volatile boolean resolved;
    private static volatile float contentScale = 1.0f;

    private AndroidDisplay() {}

    /** Device density in mdpi units (1.0 on mdpi, ~3.0 on a 1080p phone). */
    public static float contentScale() {
        resolve();
        return contentScale;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> resources = Class.forName("android.content.res.Resources");
            Class<?> displayMetrics = Class.forName("android.util.DisplayMetrics");
            Object system = resources.getMethod("getSystem").invoke(null);
            Object metrics = resources.getMethod("getDisplayMetrics").invoke(system);
            float density = displayMetrics.getField("density").getFloat(metrics);
            if (density > 0.0f && !Float.isNaN(density) && !Float.isInfinite(density)) {
                contentScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, density));
            }
        } catch (Throwable notAndroid) {
            // Desktop dev run: no framework classes, keep the 1.0 default.
        }
    }
}
