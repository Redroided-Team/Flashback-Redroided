package com.whaltermc;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

public final class GlfwCompat {

    private static volatile long lastPressMs;

    private GlfwCompat() {}

    static boolean recentPress() {
        return System.currentTimeMillis() - lastPressMs < 500;
    }

    public static void glfwSetInputMode(long window, int mode, int value) {
        // CURSOR changes run the touch state machine; calling real GLFW here
        // leaves that layer dead and touch input flapping.
        if (mode == GLFW.GLFW_CURSOR) {
            MobileCompat.requestCursorMode(window, value);
            return;
        }
        try {
            GLFW.glfwSetInputMode(window, mode, value);
        } catch (LinkageError | RuntimeException ignored) {
        }
    }

    public static void glfwSetCursorPos(long window, double x, double y) {
        MobileCompat.setCursorPos(window, x, y);
    }

    public static void glfwSetCursor(long window, long cursor) {
        MobileCompat.setCursorShape(window, cursor);
    }

    public static int glfwGetWindowAttrib(long window, int attrib) {
        int value = 0;
        try {
            value = GLFW.glfwGetWindowAttrib(window, attrib);
        } catch (LinkageError | RuntimeException ignored) {
        }
        if (attrib == GLFW.GLFW_FOCUSED) {
            boolean forced = value == 0 && MobileCompat.isMobile();
            if (forced) {
                return GLFW.GLFW_TRUE;
            }
        }
        return value;
    }

    public static int glfwGetMouseButton(long window, int button) {
        int state = 0;
        try {
            state = GLFW.glfwGetMouseButton(window, button);
        } catch (LinkageError | RuntimeException ignored) {
        }
        state = MobileCompat.mouseButton(button, state);
        if (state != 0) {
            lastPressMs = System.currentTimeMillis();
        }
        return state;
    }

    public static int glfwGetInputMode(long window, int mode) {
        int value;
        try {
            value = GLFW.glfwGetInputMode(window, mode);
        } catch (LinkageError | RuntimeException ignored) {
            if (mode != GLFW.GLFW_CURSOR) {
                return 0;
            }
            int recorded = MobileCompat.recordedCursorMode(window);
            value = recorded != -1 ? recorded : GLFW.GLFW_CURSOR_NORMAL;
        }
        if (mode == GLFW.GLFW_CURSOR) {
            value = MobileCompat.reportedCursorMode(window, value);
        }
        return value;
    }

    public static void glfwGetCursorPos(long window, double[] x, double[] y) {
        MobileCompat.frame();
        try {
            GLFW.glfwGetCursorPos(window, x, y);
        } catch (LinkageError | RuntimeException first) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                DoubleBuffer bx = stack.mallocDouble(1);
                DoubleBuffer by = stack.mallocDouble(1);
                GLFW.glfwGetCursorPos(window, bx, by);
                x[0] = bx.get(0);
                y[0] = by.get(0);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
        MobileCompat.motion(x[0], y[0]);
        // Raw reads: compensated warps read back stale and snap the cursor.
    }

    public static void glfwGetCursorPos(long window, DoubleBuffer x, DoubleBuffer y) {
        MobileCompat.frame();
        try {
            GLFW.glfwGetCursorPos(window, x, y);
        } catch (LinkageError | RuntimeException first) {
            try {
                double[] ax = new double[1];
                double[] ay = new double[1];
                GLFW.glfwGetCursorPos(window, ax, ay);
                x.put(0, ax[0]);
                y.put(0, ay[0]);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }
        MobileCompat.motion(x.get(0), y.get(0));
        // Raw reads: see above.
    }

    /**
     * Backs the {@code glfwGetMonitorName(long)} shim the transformer installs
     * into GLFW when that descriptor is missing. The name is cosmetic, so any
     * failure resolves to an empty string instead of a crash.
     */
    public static String glfwGetMonitorName(long monitor) {
        try {
            long name = GLFW.nglfwGetMonitorName(monitor);
            return name == 0L ? "" : org.lwjgl.system.MemoryUtil.memUTF8(name);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
