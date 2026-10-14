package com.whaltermc;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlfwSafety {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android/glfw");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private GlfwSafety() {}

    public static void missing(String function, Throwable cause) {
        if (REPORTED.add(function)) {
            LOGGER.warn("Launcher GLFW is missing or cannot run {} ({}); using a default result",
                    function, cause.toString());
        }
    }

    // ---- Content scale: display density when the launcher cannot answer ----
    // The 3.3-era content-scale functions are missing from older launcher
    // LWJGL builds (NoSuchMethodError) and generated stubs answer with zeros.
    // Flashback feeds the result straight into its UI scale, where a zero
    // shrinks the editor to an unreadable minimum, so anything that is not a
    // positive scale becomes the device density instead.

    public static void glfwGetWindowContentScale(long window, float[] xscale, float[] yscale) {
        try {
            GLFW.glfwGetWindowContentScale(window, xscale, yscale);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetWindowContentScale", e);
        }
        defaultContentScale(xscale);
        defaultContentScale(yscale);
    }

    public static void glfwGetMonitorContentScale(long monitor, float[] xscale, float[] yscale) {
        try {
            GLFW.glfwGetMonitorContentScale(monitor, xscale, yscale);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetMonitorContentScale", e);
        }
        defaultContentScale(xscale);
        defaultContentScale(yscale);
    }

    // !(v > 0) also catches NaN, which a driver-less stub can hand back.
    private static void defaultContentScale(float[] scale) {
        if (scale != null && scale.length > 0 && !(scale[0] > 0.0f)) {
            scale[0] = AndroidDisplay.contentScale();
        }
    }
}
