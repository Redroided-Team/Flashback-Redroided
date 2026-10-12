// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class FbaPreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android");

    @Override
    public void onPreLaunch() {
        LOGGER.info("FBA preLaunch active");
        NativeLoader.init();

        try {
            installTransformer();
            LOGGER.info("Initializing FBA");
        } catch (Throwable t) {
            // Non-fatal: the class patcher is a best-effort patch.
            // Flashback will still load; UI may look wrong on some devices.
            LOGGER.warn("FBA class patcher could not be installed, continuing without it", t);
        }
    }

    private static void installTransformer() throws Exception {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // Tolerate different launcher implementations — just skip if not Knot.
        Class<?> knotClClass;
        try {
            knotClClass = Class.forName(
                    "net.fabricmc.loader.impl.launch.knot.KnotClassLoader", false, cl);
        } catch (ClassNotFoundException e) {
            return;
        }

        if (!knotClClass.isInstance(cl)) {
            return;
        }

        Object delegate = getFieldValueSilent(cl, "delegate");
        if (delegate == null) {
            return;
        }

        Object original = resolveTransformer(delegate);
        if (original == null) {
            return;
        }

        // Already wrapped (by us or another mod using the same proxy approach) — skip.
        if (Proxy.isProxyClass(original.getClass())) {
            return;
        }

        Class<?> iface;
        try {
            iface = Class.forName(
                    "org.spongepowered.asm.mixin.transformer.IMixinTransformer", false, cl);
        } catch (ClassNotFoundException e) {
            return;
        }

        final Object target = original;

        InvocationHandler handler = (proxy, method, args) -> {
            if ("transformClassBytes".equals(method.getName())
                    && args != null && args.length >= 3
                    && args[2] instanceof byte[]) {

                Object result = method.invoke(target, args);

                byte[] bytes = result instanceof byte[]
                        ? (byte[]) result
                        : (byte[]) args[2];

                String name = args[0] != null ? args[0].toString() : "";

                if (shouldTransform(name) && bytes != null && bytes.length > 0) {
                    try {
                        return ClassPatcher.transform(bytes, isFlashbackClass(name),
                                !isEngineClass(name));
                    } catch (Throwable ignored) {
                        // Never kill class loading — return original bytes on any failure.
                        return result;
                    }
                }

                return result;
            }
            return method.invoke(target, args);
        };

        Object wrapper = Proxy.newProxyInstance(cl, new Class<?>[]{iface}, handler);
        setFieldValue(delegate, "mixinTransformer", wrapper);
    }

    /** Read mixinTransformer field, initialising transformers first if needed. */
    private static Object resolveTransformer(Object delegate) {
        Object t = getFieldValueSilent(delegate, "mixinTransformer");
        if (t != null) return t;
        try {
            Method init = findMethod(delegate.getClass(), "initializeTransformers");
            init.setAccessible(true);
            init.invoke(delegate);
        } catch (Throwable ignored) {}
        return getFieldValueSilent(delegate, "mixinTransformer");
    }

    /**
     * The imgui.moulberry90 -> imgui.whaltermc90 rename is a plain package
     * rename, so it has to be applied to every class that mentions the package
     * or the JVM ends up with mismatched descriptors across the addon boundary.
     * Addon mods (flashback_studio, fbactors, lattice, ...) are compiled against
     * imgui.moulberry90 too, so restricting this to com.moulberry.flashback.*
     * left those classes pointing at a package that no longer exists.
     * ClassPatcher still no-ops for any class that does not match its content,
     * so this stays cheap. Never touch our own classes.
     */
    private static boolean shouldTransform(String className) {
        if (className == null || className.isEmpty()) return false;
        // Knot names use dots; be tolerant of slashes too.
        return !className.replace('/', '.').startsWith("dev.whaltermc.");
    }

    /**
     * Only Flashback's own classes get the GLFW call rewrites; those redirect
     * calls to our fallback stubs and must not be forced onto other mods that
     * happen to share the com.moulberry prefix.
     */
    private static boolean isFlashbackClass(String className) {
        return className.replace('/', '.').startsWith("com.moulberry.flashback.");
    }

    // The GLFW guard injects stubs that neutralise failed native calls. That is
    // right for Flashback and third party addons, which cannot work at all on
    // Android, but it must never touch Minecraft or the blaze3d platform: those
    // own the real window and event loop, and stubbing them crashed the game
    // natively on 26.x.
    private static boolean isEngineClass(String className) {
        String dotted = className.replace('/', '.');
        return dotted.startsWith("net.minecraft.")
                || dotted.startsWith("com.mojang.");
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static Object getFieldValueSilent(Object instance, String name) {
        try {
            return getFieldValue(instance, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object getFieldValue(Object instance, String name) throws Exception {
        Field f = findField(instance.getClass(), name);
        f.setAccessible(true);
        try {
            MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup());
            return lookup.unreflectGetter(f).invoke(instance);
        } catch (Throwable ignored) {
            return f.get(instance);
        }
    }

    private static void setFieldValue(Object instance, String name, Object value) throws Exception {
        Field f = findField(instance.getClass(), name);
        f.setAccessible(true);
        try {
            MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(f.getDeclaringClass(), MethodHandles.lookup());
            lookup.unreflectSetter(f).invoke(instance, value);
        } catch (Throwable ignored) {
            f.set(instance, value);
        }
    }

    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name + " in " + clazz.getName());
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try { return c.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name + " in " + clazz.getName());
    }
}
