// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharModsCallback;
import org.lwjgl.glfw.GLFWCharModsCallbackI;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWCursorEnterCallback;
import org.lwjgl.glfw.GLFWCursorEnterCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMonitorCallback;
import org.lwjgl.glfw.GLFWMonitorCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallbackI;
import org.lwjgl.glfw.GLFWWindowFocusCallback;
import org.lwjgl.glfw.GLFWWindowFocusCallbackI;
import org.lwjgl.glfw.GLFWWindowPosCallback;
import org.lwjgl.glfw.GLFWWindowPosCallbackI;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallbackI;
import org.lwjgl.system.MemoryUtil;

// Central GLFW safety net. Every static GLFW call Flashback makes is rewritten
// to the same-named method here (see ClassPatcher). On launchers with a full
// modern LWJGL3 each wrapper is a straight delegation with no behavior change;
// on launchers whose GLFW is stubbed or missing functions, the call is caught
// and a safe default is returned instead of crashing the game.
//
// Cursor and input-mode calls are NOT mirrored here: those stay routed to
// GlfwWrapper, which applies the mobile cursor logic on top.
public final class GlfwFallback {

    // Zeroed video mode returned when the real query is unavailable. Backed by
    // one never-freed calloc allocation, created lazily so class loading never
    // touches natives: callers only read width/height from it.
    private static final class EmptyVideoMode {
        static final GLFWVidMode INSTANCE = GLFWVidMode.create(
                MemoryUtil.memAddress(MemoryUtil.memCalloc(GLFWVidMode.SIZEOF)));
    }

    private GlfwFallback() {}

    public static void missing(String function, Throwable cause) {
    }

    // ---- Queries with safe defaults ----

    public static int glfwGetKey(long window, int key) {
        try {
            return GLFW.glfwGetKey(window, key);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetKey", e);
            return GLFW.GLFW_RELEASE;
        }
    }

    public static int glfwGetKeyScancode(int key) {
        try {
            return GLFW.glfwGetKeyScancode(key);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetKeyScancode", e);
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    public static String glfwGetKeyName(int key, int scancode) {
        try {
            return GLFW.glfwGetKeyName(key, scancode);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetKeyName", e);
            return "";
        }
    }

    public static double glfwGetTime() {
        try {
            return GLFW.glfwGetTime();
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetTime", e);
            return System.nanoTime() / 1_000_000_000.0;
        }
    }

    public static long glfwGetCurrentContext() {
        try {
            return GLFW.glfwGetCurrentContext();
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetCurrentContext", e);
            return 0L;
        }
    }

    public static long glfwCreateWindow(int width, int height, CharSequence title, long monitor, long share) {
        try {
            return GLFW.glfwCreateWindow(width, height, title, monitor, share);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwCreateWindow", e);
            return 0L;
        }
    }

    public static long glfwCreateStandardCursor(int shape) {
        try {
            return GLFW.glfwCreateStandardCursor(shape);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwCreateStandardCursor", e);
            return 0L;
        }
    }

    public static boolean glfwGetGamepadState(int jid, GLFWGamepadState state) {
        try {
            return GLFW.glfwGetGamepadState(jid, state);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetGamepadState", e);
            return false;
        }
    }

    public static PointerBuffer glfwGetMonitors() {
        try {
            return GLFW.glfwGetMonitors();
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetMonitors", e);
            return PointerBuffer.allocateDirect(0);
        }
    }

    public static GLFWVidMode glfwGetVideoMode(long monitor) {
        try {
            return GLFW.glfwGetVideoMode(monitor);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetVideoMode", e);
            return EmptyVideoMode.INSTANCE;
        }
    }

    // ---- Array-out queries: output arrays stay zero-filled on failure ----

    public static void glfwGetFramebufferSize(long window, int[] width, int[] height) {
        try {
            GLFW.glfwGetFramebufferSize(window, width, height);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetFramebufferSize", e);
        }
    }

    public static void glfwGetWindowSize(long window, int[] width, int[] height) {
        try {
            GLFW.glfwGetWindowSize(window, width, height);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetWindowSize", e);
        }
    }

    public static void glfwGetWindowPos(long window, int[] xpos, int[] ypos) {
        try {
            GLFW.glfwGetWindowPos(window, xpos, ypos);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetWindowPos", e);
        }
    }

    public static void glfwGetWindowContentScale(long window, float[] xscale, float[] yscale) {
        try {
            GLFW.glfwGetWindowContentScale(window, xscale, yscale);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetWindowContentScale", e);
        }
    }

    public static void glfwGetMonitorPos(long monitor, int[] xpos, int[] ypos) {
        try {
            GLFW.glfwGetMonitorPos(monitor, xpos, ypos);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetMonitorPos", e);
        }
    }

    public static void glfwGetMonitorWorkarea(long monitor, int[] xpos, int[] ypos, int[] width, int[] height) {
        try {
            GLFW.glfwGetMonitorWorkarea(monitor, xpos, ypos, width, height);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetMonitorWorkarea", e);
        }
    }

    public static void glfwGetMonitorContentScale(long monitor, float[] xscale, float[] yscale) {
        try {
            GLFW.glfwGetMonitorContentScale(monitor, xscale, yscale);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetMonitorContentScale", e);
        }
    }

    // ---- Fire-and-forget calls: no-ops on failure ----

    public static void glfwWindowHint(int hint, int value) {
        try {
            GLFW.glfwWindowHint(hint, value);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwWindowHint", e);
        }
    }

    public static void glfwSwapInterval(int interval) {
        try {
            GLFW.glfwSwapInterval(interval);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSwapInterval", e);
        }
    }

    public static void glfwSwapBuffers(long window) {
        try {
            GLFW.glfwSwapBuffers(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSwapBuffers", e);
        }
    }

    public static void glfwSetWindowTitle(long window, CharSequence title) {
        try {
            GLFW.glfwSetWindowTitle(window, title);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowTitle", e);
        }
    }

    public static void glfwSetWindowSize(long window, int width, int height) {
        try {
            GLFW.glfwSetWindowSize(window, width, height);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowSize", e);
        }
    }

    public static void glfwSetWindowPos(long window, int xpos, int ypos) {
        try {
            GLFW.glfwSetWindowPos(window, xpos, ypos);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowPos", e);
        }
    }

    public static void glfwSetWindowOpacity(long window, float opacity) {
        try {
            GLFW.glfwSetWindowOpacity(window, opacity);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowOpacity", e);
        }
    }

    public static void glfwFocusWindow(long window) {
        try {
            GLFW.glfwFocusWindow(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwFocusWindow", e);
        }
    }

    public static void glfwHideWindow(long window) {
        try {
            GLFW.glfwHideWindow(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwHideWindow", e);
        }
    }

    public static void glfwShowWindow(long window) {
        try {
            GLFW.glfwShowWindow(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwShowWindow", e);
        }
    }

    public static void glfwDestroyWindow(long window) {
        try {
            GLFW.glfwDestroyWindow(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwDestroyWindow", e);
        }
    }

    public static void glfwMakeContextCurrent(long window) {
        try {
            GLFW.glfwMakeContextCurrent(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwMakeContextCurrent", e);
        }
    }

    // ---- Callback setters: the previous callback, or null on failure ----

    public static GLFWWindowSizeCallback glfwSetWindowSizeCallback(long window, GLFWWindowSizeCallbackI callback) {
        try {
            return GLFW.glfwSetWindowSizeCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowSizeCallback", e);
            return null;
        }
    }

    public static GLFWWindowPosCallback glfwSetWindowPosCallback(long window, GLFWWindowPosCallbackI callback) {
        try {
            return GLFW.glfwSetWindowPosCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowPosCallback", e);
            return null;
        }
    }

    public static GLFWWindowFocusCallback glfwSetWindowFocusCallback(long window, GLFWWindowFocusCallbackI callback) {
        try {
            return GLFW.glfwSetWindowFocusCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowFocusCallback", e);
            return null;
        }
    }

    public static GLFWWindowCloseCallback glfwSetWindowCloseCallback(long window, GLFWWindowCloseCallbackI callback) {
        try {
            return GLFW.glfwSetWindowCloseCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowCloseCallback", e);
            return null;
        }
    }

    public static GLFWMonitorCallback glfwSetMonitorCallback(GLFWMonitorCallbackI callback) {
        try {
            return GLFW.glfwSetMonitorCallback(callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetMonitorCallback", e);
            return null;
        }
    }

    public static GLFWErrorCallback glfwSetErrorCallback(GLFWErrorCallbackI callback) {
        try {
            return GLFW.glfwSetErrorCallback(callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetErrorCallback", e);
            return null;
        }
    }

    public static GLFWCursorPosCallback glfwSetCursorPosCallback(long window, GLFWCursorPosCallbackI callback) {
        try {
            return GLFW.glfwSetCursorPosCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetCursorPosCallback", e);
            return null;
        }
    }

    public static GLFWCursorEnterCallback glfwSetCursorEnterCallback(long window, GLFWCursorEnterCallbackI callback) {
        try {
            return GLFW.glfwSetCursorEnterCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetCursorEnterCallback", e);
            return null;
        }
    }

    public static GLFWKeyCallback glfwSetKeyCallback(long window, GLFWKeyCallbackI callback) {
        try {
            return GLFW.glfwSetKeyCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetKeyCallback", e);
            return null;
        }
    }

    public static GLFWMouseButtonCallback glfwSetMouseButtonCallback(long window, GLFWMouseButtonCallbackI callback) {
        try {
            return GLFW.glfwSetMouseButtonCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetMouseButtonCallback", e);
            return null;
        }
    }

    public static GLFWScrollCallback glfwSetScrollCallback(long window, GLFWScrollCallbackI callback) {
        try {
            return GLFW.glfwSetScrollCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetScrollCallback", e);
            return null;
        }
    }

    public static GLFWCharModsCallback glfwSetCharModsCallback(long window, GLFWCharModsCallbackI callback) {
        try {
            return GLFW.glfwSetCharModsCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetCharModsCallback", e);
            return null;
        }
    }

    // ---- Calls used by third party addons (flashback_studio, FlashbackSettings) ----

    public static GLFWCharCallback glfwSetCharCallback(long window, GLFWCharCallbackI callback) {
        try {
            return GLFW.glfwSetCharCallback(window, callback);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetCharCallback", e);
            return null;
        }
    }

    public static long glfwGetWindowMonitor(long window) {
        try {
            return GLFW.glfwGetWindowMonitor(window);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwGetWindowMonitor", e);
            return 0L;
        }
    }

    public static void glfwSetWindowMonitor(long window, long monitor, int xpos, int ypos,
                                            int width, int height, int refreshRate) {
        try {
            GLFW.glfwSetWindowMonitor(window, monitor, xpos, ypos, width, height, refreshRate);
        } catch (LinkageError | RuntimeException e) {
            missing("glfwSetWindowMonitor", e);
        }
    }

    public static void glfwPollEvents() {
        try {
            GLFW.glfwPollEvents();
        } catch (LinkageError | RuntimeException e) {
            missing("glfwPollEvents", e);
        }
    }
}
