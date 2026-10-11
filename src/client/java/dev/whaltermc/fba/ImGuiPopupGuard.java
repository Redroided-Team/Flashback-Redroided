package dev.whaltermc.fba;

import imgui.whaltermc90.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes frame-dependent ImGui calls safe when they arrive outside a frame.
 *
 * <p>{@code ImGui::IsPopupOpen} dereferences {@code GImGui->CurrentWindow},
 * which is only valid between {@code NewFrame()} and {@code Render()}. A few
 * addons probe ImGui state from input callbacks instead of from their draw
 * code, so the call can land while no frame is open. In a debug build ImGui
 * would assert; in release it is a null dereference and takes the whole game
 * down with a SIGSEGV inside libimgui.</p>
 *
 * <p>Flashback Studio's actor transform gizmo does exactly this from
 * {@code MouseHandler.onButton}. Rather than touch addon code, every
 * {@code isPopupOpen} call is routed here and answered with "no popup open"
 * whenever no frame is active. Inside a frame the real call is forwarded
 * untouched, so normal behaviour is unchanged.</p>
 */
public final class ImGuiPopupGuard {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/imgui");

    private static volatile boolean frameActive;

    private static int guarded;

    private ImGuiPopupGuard() {}

    /**
     * Wraps {@code ImGui.newFrame()}. Routed here by the class transformer so
     * the frame window is tracked from ImGui's own lifecycle rather than from a
     * Flashback backend method, which is not called once per frame.
     */
    public static void beginFrame() {
        frameActive = true;
        ImGui.newFrame();
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
     * Guarded entry for Flashback Studio's gizmo click handler.
     *
     * <p>Same stack shape as the original
     * {@code interceptMouseButton(MouseButtonInfo, int)}, so the transformer
     * can swap the call one-for-one without touching max_stack or frames.
     * Off-frame it declines (no viewport is showing, and the real method would
     * crash inside ImGui natives); on-frame it delegates reflectively so the
     * addon keeps working untouched.</p>
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

    /** Log the first few offenders only; these can fire on every mouse event. */
    private static void flashbackRedroided$note(String name) {
        if (guarded++ < 5) {
            LOGGER.info(
                    "FBA: blocked isPopupOpen(\"{}\") outside an ImGui frame "
                            + "(would crash natively)",
                    name);
        }
    }
}
