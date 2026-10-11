// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.DoubleBuffer;

public final class GlfwWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android/input");
    private static final boolean DEBUG = Boolean.getBoolean("flashback.debug.input");
    private static final long[] LAST_LOG = new long[8];

    private static volatile long lastPressMs;

    private GlfwWrapper() {}

    static boolean recentPress() {
        return System.currentTimeMillis() - lastPressMs < 500;
    }

    public static void glfwSetInputMode(long window, int mode, int value) {
        if (mode == GLFW.GLFW_CURSOR) {
            AndroidInput.requestCursorMode(window, value);
            return;
        }
        try {
            GLFW.glfwSetInputMode(window, mode, value);
        } catch (LinkageError | RuntimeException ignored) {
        }
    }

    public static void glfwSetCursorPos(long window, double x, double y) {
        AndroidInput.setCursorPos(window, x, y);
    }

    public static void glfwSetCursor(long window, long cursor) {
        AndroidInput.setCursorShape(window, cursor);
    }

    public static int glfwGetWindowAttrib(long window, int attrib) {
        int value = 0;
        try {
            value = GLFW.glfwGetWindowAttrib(window, attrib);
        } catch (LinkageError | RuntimeException ignored) {
        }
        if (attrib == GLFW.GLFW_FOCUSED) {
            boolean forced = value == 0 && AndroidInput.isMobile();
            debug(0, "GLFW_FOCUSED raw=" + value + (forced ? " -> forced to 1 (mobile)" : ""));
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
        state = AndroidInput.mouseButton(button, state);
        if (state != 0) {
            lastPressMs = System.currentTimeMillis();
            debug(1, "mouse button " + button + " state=" + state);
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
            int recorded = AndroidInput.recordedCursorMode(window);
            value = recorded != -1 ? recorded : GLFW.GLFW_CURSOR_NORMAL;
        }
        if (mode == GLFW.GLFW_CURSOR) {
            value = AndroidInput.reportedCursorMode(window, value);
            debug(2, "cursor mode=" + value + " (212993 normal, 212994 hidden, 212995 disabled)");
        }
        return value;
    }

    public static void glfwGetCursorPos(long window, double[] x, double[] y) {
        AndroidInput.frame();
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
        AndroidInput.motion(x[0], y[0]);
        double[] v = AndroidInput.adjustCursor(window, x[0], y[0]);
        x[0] = v[0];
        y[0] = v[1];
        debug(3, "cursor pos " + x[0] + ", " + y[0]);
    }

    public static void glfwGetCursorPos(long window, DoubleBuffer x, DoubleBuffer y) {
        AndroidInput.frame();
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
        AndroidInput.motion(x.get(0), y.get(0));
        double[] v = AndroidInput.adjustCursor(window, x.get(0), y.get(0));
        x.put(0, v[0]);
        y.put(0, v[1]);
        debug(3, "cursor pos " + x.get(0) + ", " + y.get(0));
    }

    private static void debug(int slot, String message) {
        if (!DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - LAST_LOG[slot] >= 1000) {
            LAST_LOG[slot] = now;
            LOGGER.info(message);
        }
    }
}
