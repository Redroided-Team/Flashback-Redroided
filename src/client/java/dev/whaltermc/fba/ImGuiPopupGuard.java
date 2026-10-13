package dev.whaltermc.fba;

import imgui.whaltermc90.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers frame-dependent ImGui calls safely outside a frame.
 *
 * <p>{@code IsPopupOpen} dereferences the current window, valid only between
 * {@code NewFrame()} and {@code Render()}. Addons probing it from input
 * callbacks would segfault natively, so off-frame calls are answered with
 * "no popup open" while on-frame calls forward untouched.</p>
 */
public final class ImGuiPopupGuard {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/imgui");

    private static volatile boolean frameActive;

    private static int guarded;

    private ImGuiPopupGuard() {}

    /**
     * Wraps {@code ImGui.newFrame()} to track the frame window from ImGui's
     * own lifecycle.
     */
    public static void beginFrame() {
        ImGui.newFrame();
        frameActive = true;
    }

    /**
     * Wraps {@code ImGui.render()}. The real call runs first so a throwing
     * render cannot leave the flag stuck open.
     */
    public static void endFrame() {
        try {
            ImGui.render();
        } finally {
            frameActive = false;
        }
    }

    public static boolean isFrameActive() {
        return frameActive;
    }

    /**
     * Guarded entry for Flashback Studio's gizmo click handler. Same stack
     * shape as the original, so the transformer swaps it one-for-one.
     * Off-frame it declines; on-frame it delegates to the addon untouched.
     */
    public static boolean interceptGizmoClick(Object info, int action) {
        if (!frameActive) {
            flashbackRedroided$note("gizmo-click");
            return false;
        }
        try {
            java.lang.reflect.Method target = gizmoClickTarget();
            if (target == null) {
                return false;
            }
            Object result = target.invoke(null, info, action);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            LOGGER.warn("FBA: gizmo click delegation failed", t);
            return false;
        }
    }

    private static volatile java.lang.reflect.Method gizmoClickTarget;

    private static java.lang.reflect.Method gizmoClickTarget() {
        java.lang.reflect.Method cached = gizmoClickTarget;
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> gizmo = Class.forName(
                    "com.wsmoothie.flashbackstudio.ui.ActorTransformGizmo");
            Class<?> buttonInfo = Class.forName(
                    "net.minecraft.client.input.MouseButtonInfo");
            java.lang.reflect.Method resolved = gizmo.getMethod(
                    "interceptMouseButton", buttonInfo, int.class);
            resolved.setAccessible(true);
            gizmoClickTarget = resolved;
            return resolved;
        } catch (Throwable t) {
            LOGGER.warn("FBA: cannot resolve gizmo click handler", t);
            return null;
        }
    }

    public static boolean isPopupOpen(String name) {
        if (!frameActive) {
            flashbackRedroided$note(name);
            return false;
        }
        return ImGui.isPopupOpen(name);
    }

    public static boolean isPopupOpen(String name, int cond) {
        if (!frameActive) {
            flashbackRedroided$note(name);
            return false;
        }
        return ImGui.isPopupOpen(name, cond);
    }

    /** Logs the first few offenders only; these can fire on every mouse event. */
    private static void flashbackRedroided$note(String name) {
        if (guarded++ < 5) {
            LOGGER.info(
                    "FBA: blocked isPopupOpen(\"{}\") outside an ImGui frame "
                            + "(would crash natively)",
                    name);
        }
    }
}
