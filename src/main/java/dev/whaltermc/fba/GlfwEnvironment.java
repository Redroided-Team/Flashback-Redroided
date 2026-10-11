// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import java.util.Locale;

// Heuristic detector for stubbed GLFW builds shipped by some launchers.
// A stub looks like LWJGL API-wise but has no working natives behind it,
// so the first real call throws instead of doing anything.
public final class GlfwEnvironment {

    private GlfwEnvironment() {}

    public static boolean isGlfwStub() {
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            String origin = String.valueOf(glfw.getProtectionDomain().getCodeSource()).toLowerCase(Locale.ROOT);
            // A hint, not definitive proof.
            if (origin.contains("stub")) {
                return true;
            }
            // Newer LWJGL reports its loaded native directly.
            try {
                Class<?> library = Class.forName("org.lwjgl.system.Library");
                var method = library.getDeclaredMethod("getLoadedNative");
                method.setAccessible(true);
                Object nativeLibrary = method.invoke(null);
                return nativeLibrary == null;
            } catch (NoSuchMethodException notThere) {
                // Older LWJGL: probe a side-effect-free function instead.
                return !nativesPresent();
            }
        } catch (Throwable ignored) {
            return false; // Unknown: don't assume it's a stub.
        }
    }

    // True when a native GLFW call actually answers. Read-only query with no
    // state change, safe to run anywhere including pre-launch. Fully
    // reflective so this class loads even where LWJGL ships no GLFW binding.
    private static boolean nativesPresent() {
        try {
            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            var probe = glfw.getDeclaredMethod("glfwGetVersionString");
            probe.setAccessible(true);
            probe.invoke(null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
