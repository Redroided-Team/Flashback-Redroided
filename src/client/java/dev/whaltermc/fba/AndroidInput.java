// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AndroidInput {

    private static final boolean MOBILE = detect();
    private static final boolean MOBILE_GLUES = detectMobileGlues();
    private static final Map<Long, Integer> CURSOR_MODES = new HashMap<>();
    private static final Map<Long, Long> CURSOR_SHAPES = new HashMap<>();

    private static final Map<Long, double[]> LAST_POS = new HashMap<>();
    private static final Map<Long, double[]> GRAB_OFFSET = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android");
    private static int logBudget = 400;

    private AndroidInput() {
    }

    private static void trace(String what) {
        if (logBudget <= 0) {
            return;
        }
        logBudget--;
        String caller = StackWalker.getInstance().walk(frames -> frames
                .filter(f -> !f.getClassName().startsWith("dev.whaltermc.fba"))
                .findFirst()
                .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                .orElse("?"));
        LOGGER.info("[mobile={}] {} {} <- {}{}", MOBILE, what, state(), caller,
                logBudget == 0 ? " (log budget exhausted)" : "");
    }

    private static String state() {
        try {
            return "{mcGrabbed=" + Minecraft.getInstance().mouseHandler.isMouseGrabbed()
                    + " press=" + GlfwWrapper.recentPress() + "}";
        } catch (Throwable t) {
            return "{state?}";
        }
    }

    private static int posLogs;
    private static int autoLogs;

    private static String modeName(int m) {
        return switch (m) {
            case GLFW.GLFW_CURSOR_NORMAL -> "NORMAL";
            case GLFW.GLFW_CURSOR_HIDDEN -> "HIDDEN";
            case GLFW.GLFW_CURSOR_DISABLED -> "DISABLED";
            default -> "mode" + m;
        };
    }

    private static void noteMode(long window, int mode) {
        Integer prev = CURSOR_MODES.get(window);
        if (prev == null || prev != mode) {
            GRAB_OFFSET.remove(window);
        }
    }

    public static double[] adjustCursor(long window, double rx, double ry) {
        Integer mode = CURSOR_MODES.get(window);
        boolean grabbed = MOBILE && mode != null && mode == GLFW.GLFW_CURSOR_DISABLED;
        double[] out;
        if (!grabbed) {
            out = new double[]{rx, ry};
        } else {
            double[] off = GRAB_OFFSET.get(window);
            if (off == null) {
                double[] last = LAST_POS.get(window);
                off = last == null ? new double[]{0, 0} : new double[]{last[0] - rx, last[1] - ry};
                GRAB_OFFSET.put(window, off);
            }
            out = new double[]{rx + off[0], ry + off[1]};
        }
        LAST_POS.put(window, out);
        return out;
    }

    public static boolean isMobile() {
        return MOBILE;
    }

    public static boolean isMobileGlues() {
        return MOBILE_GLUES;
    }

    private static boolean detectMobileGlues() {
        String override = System.getProperty("flashback.redroided.mobileglues");
        if (override == null) {
            override = System.getProperty("flashback.mobileglues");
        }
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        try {
            if (System.getenv("MG_DIR_PATH") != null || System.getenv("MOBILEGLUES_PATH") != null) {
                return true;
            }
            String libgl = System.getenv("LIBGL_NAME");
            if (libgl != null && libgl.toLowerCase(Locale.ROOT).contains("mobileglues")) {
                return true;
            }
            String[] rendererVars = {"POJAV_RENDERER", "FCL_RENDERER", "ZALITH_RENDERER"};
            for (String v : rendererVars) {
                String val = System.getenv(v);
                if (val != null && val.toLowerCase(Locale.ROOT).contains("mobileglues")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean detect() {
        String override = System.getProperty("flashback.mobile");
        if (override != null) {
            return Boolean.parseBoolean(override);
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        String gameDir = System.getProperty("user.dir", "");
        if (os.contains("android") || vendor.contains("android")
                || home.startsWith("/data/")
                || gameDir.startsWith("/storage/emulated/")
                || gameDir.startsWith("/sdcard/")) {
            return true;
        }

        String[] launcherVariables = {
                "POJAV_ENVIRON", "POJAV_RENDERER", "POJAV_NATIVEDIR", "POJAV_GAME_DIR",
                "FCL_NATIVEDIR", "FCL_RENDERER", "ZALITH_RENDERER", "MG_DIR_PATH",
                "MOBILEGLUES_PATH"
        };
        for (String variable : launcherVariables) {
            if (System.getenv(variable) != null) {
                return true;
            }
        }

        String renderer = System.getenv("LIBGL_NAME");
        return renderer != null && renderer.toLowerCase(Locale.ROOT).contains("mobileglues");
    }

    public static void setCursorPos(long window, double x, double y) {
        if (posLogs++ < 8) {
            trace("setCursorPos(" + x + ", " + y + ")" + (MOBILE ? " suppressed" : ""));
        }
        if (!MOBILE) {
            try {
                GLFW.glfwSetCursorPos(window, x, y);
            } catch (LinkageError ignored) {
            }
        }
    }

    public static void setCursorMode(long window, int mode) {
        Integer before = CURSOR_MODES.get(window);
        if (before == null || before != mode) {
            trace("MODE " + (before == null ? "?" : modeName(before)) + " -> " + modeName(mode));
        }
        // Always issue the real call: vanilla Minecraft changes the actual
        // cursor mode behind this map (screen open/close grabs and releases),
        // so skipping "redundant" writes desyncs the map and the grab that
        // follows never reaches GLFW. Vanilla calls through every time.
        noteMode(window, mode);
        CURSOR_MODES.put(window, mode);
        try {
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, mode);
        } catch (LinkageError ignored) {
        }
    }

    public static void setCursorModeIfChanged(long window, int mode) {
        if (MOBILE) {
            Integer current = CURSOR_MODES.get(window);
            if (current != null && current == GLFW.GLFW_CURSOR_DISABLED
                    && mode != GLFW.GLFW_CURSOR_DISABLED) {
                return;
            }
        }
        // No equality shortcut here either: see setCursorMode.
        setCursorMode(window, mode);
    }

    private static boolean synthHold;
    private static int synthButton;
    private static long lastMotionMs;
    private static double lastRawX = Double.NaN;
    private static double lastRawY = Double.NaN;

    private static final long GRACE_MS = 150;
    private static final long IDLE_MS = 200;

    private static long synthWindow;
    private static final boolean[] STUCK = new boolean[8];

    public static int effective(int button, int real) {
        if (button >= 0 && button < STUCK.length && STUCK[button]) {
            if (real == 0) {
                STUCK[button] = false;
            } else {
                return 0;
            }
        }
        return real;
    }

    private static int heldButton(long window) {
        try {
            for (int b = 0; b < 3; b++) {
                if (effective(b, GLFW.glfwGetMouseButton(window, b)) != 0) {
                    return b;
                }
            }
        } catch (LinkageError ignored) {
        }
        return -1;
    }

    public static int mouseButton(int button, int real) {
        if (!MOBILE) {
            return real;
        }
        if (synthHold && button == synthButton) {
            return 1;
        }
        return effective(button, real);
    }

    public static void motion(double x, double y) {
        if (!MOBILE) {
            return;
        }
        if (!synthHold) {
            return;
        }
        if (x != lastRawX || y != lastRawY) {
            lastMotionMs = System.currentTimeMillis();
            lastRawX = x;
            lastRawY = y;
        }
    }

    private static int pendingMode = -1;
    private static long pendingWindow;
    private static long pendingSince;

    private static boolean buttonHeld(long window) {
        try {
            for (int b = 0; b < 3; b++) {
                if (effective(b, GLFW.glfwGetMouseButton(window, b)) != 0) {
                    return true;
                }
            }
        } catch (LinkageError ignored) {
        }
        return false;
    }

    public static int reportedCursorMode(long window, int real) {
        if (MOBILE && pendingMode != -1 && pendingWindow == window) {
            return pendingMode;
        }
        syncCursorMode(window, real);
        return real;
    }

    public static void frame() {
        if (MOBILE && synthHold && System.currentTimeMillis() - lastMotionMs > IDLE_MS) {
            synthHold = false;
            boolean stuck = false;
            try {
                stuck = GLFW.glfwGetMouseButton(synthWindow, synthButton) != 0;
            } catch (LinkageError ignored) {
            }
            if (synthButton >= 0 && synthButton < STUCK.length) {
                STUCK[synthButton] = stuck;
            }
            trace("synthetic hold ended (finger lifted), stuck button masked=" + stuck);
        }
        if (!MOBILE || pendingMode == -1) {
            return;
        }
        long now = System.currentTimeMillis();
        if (buttonHeld(pendingWindow) && now - pendingSince < 3000) {
            return;
        }
        int mode = pendingMode;
        long window = pendingWindow;
        pendingMode = -1;
        trace("applying deferred mode " + modeName(mode) + " (touch released)");
        setCursorMode(window, mode);
    }

    public static void syncCursorMode(long window, int real) {
        Integer cur = CURSOR_MODES.get(window);
        if (cur == null || cur != real) {
            noteMode(window, real);
            CURSOR_MODES.put(window, real);
        }
    }

    private static boolean calledFromAutoUpdate() {
        return StackWalker.getInstance().walk(frames -> frames
                .anyMatch(f -> f.getMethodName().equals("updateMouseCursor")));
    }

    public static void requestCursorMode(long window, int mode) {
        if (!MOBILE) {
            try {
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, mode);
            } catch (LinkageError ignored) {
            }
            return;
        }
        Integer cur = CURSOR_MODES.get(window);
        if (cur == null) {
            try {
                cur = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR);
                CURSOR_MODES.put(window, cur);
            } catch (LinkageError ignored) {
            }
        }
        if (cur != null && cur == GLFW.GLFW_CURSOR_DISABLED && mode != GLFW.GLFW_CURSOR_DISABLED) {
            if (calledFromAutoUpdate()) {
                if (autoLogs++ < 5) {
                    trace("auto-update release " + modeName(mode) + " ignored");
                }
                return;
            }
            trace("release while grabbed -> " + modeName(mode) + " (allowed)");
        }
        if (cur != null && mode == GLFW.GLFW_CURSOR_DISABLED && cur != mode) {
            int held = heldButton(window);
            if (held != -1) {
                synthButton = held;
                synthWindow = window;
                synthHold = true;
                lastMotionMs = System.currentTimeMillis() + GRACE_MS;
                lastRawX = Double.NaN;
                lastRawY = Double.NaN;
                trace("GRAB with touch held -> real DISABLED + synthetic button " + held);
                pendingMode = -1;
                setCursorMode(window, mode);
                return;
            }
        }
        if (cur != null && cur != mode && buttonHeld(window)) {
            pendingMode = mode;
            pendingWindow = window;
            pendingSince = System.currentTimeMillis();
            trace("DEFER " + modeName(cur) + " -> " + modeName(mode) + " (touch held, keeping the hold alive)");
            return;
        }
        pendingMode = -1;
        setCursorMode(window, mode);
    }

    public static int recordedCursorMode(long window) {
        Integer m = MOBILE ? CURSOR_MODES.get(window) : null;
        return m == null ? -1 : m;
    }

    public static void setCursorShape(long window, long cursor) {
        if (MOBILE) {
            Long previous = CURSOR_SHAPES.get(window);
            if (previous != null && previous == cursor) {
                return;
            }
        }
        CURSOR_SHAPES.put(window, cursor);
        try {
            GLFW.glfwSetCursor(window, cursor);
        } catch (LinkageError ignored) {
        }
    }
}