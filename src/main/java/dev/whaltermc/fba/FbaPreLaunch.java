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
        LOGGER.info("FBA preLaunch active (build {})", flashbackRedroided$buildHash());
        NativeLoader.init();

        try {
            installTransformer();

            LOGGER.info("FBA loaded native libs");

        } catch (Throwable t) {
            LOGGER.error(
                    "FBA native libs could not be loaded, continuing without it",
                    t
            );
        }
    }

    private static void installTransformer() throws Exception {
        ClassLoader cl =
                Thread.currentThread().getContextClassLoader();

        Class<?> knotClClass = Class.forName(
                "net.fabricmc.loader.impl.launch.knot.KnotClassLoader",
                false,
                cl
        );

        if (!knotClClass.isInstance(cl)) {
            LOGGER.warn(
                    "ClassLoader is not KnotClassLoader ({}), " +
                    "skipping remapper",
                    cl.getClass().getName()
            );
            return;
        }

        Object delegate =
                getFieldValue(cl, "delegate");

        Object original = null;

        try {
            original =
                    getFieldValue(delegate, "mixinTransformer");
        } catch (Throwable ignored) {
        }

        if (original == null) {
            Method init =
                    findMethod(
                            delegate.getClass(),
                            "initializeTransformers"
                    );

            init.setAccessible(true);

            try {
                init.invoke(delegate);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (!(e.getCause()
                        instanceof IllegalStateException)) {
                    throw e;
                }
            }

            original =
                    getFieldValue(
                            delegate,
                            "mixinTransformer"
                    );
        }

        if (original == null) {
            throw new IllegalStateException(
                    "mixinTransformer is still null after init attempt"
            );
        }

        if (Proxy.isProxyClass(original.getClass())) {
            LOGGER.info(
                    "FBA already initialized, skipping"
            );
            return;
        }

        Class<?> iface = Class.forName(
                "org.spongepowered.asm.mixin.transformer.IMixinTransformer",
                false,
                cl
        );

        final Object target = original;

        InvocationHandler handler = (proxy, method, args) -> {

            if ("transformClassBytes".equals(method.getName())
                    && args != null
                    && args.length >= 3
                    && args[2] instanceof byte[]) {

                byte[] input = (byte[]) args[2];
                String name =
                        args[0] != null
                                ? args[0].toString()
                                : "";

                Object result;
                try {
                    result = method.invoke(target, args);
                } catch (Throwable t) {
                    // Everything downstream of us (mixin, and whatever other
                    // transformer mods have wrapped around it) runs inside this
                    // call. One of those failing is not our bug, but letting it
                    // escape turns a single unreadable class into a hard crash
                    // of the whole game. Hand the class back untouched instead:
                    // the game keeps running with that class simply untransformed.
                    Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite
                            && ite.getTargetException() != null
                            ? ite.getTargetException()
                            : t;
                    LOGGER.error(
                            "Downstream transformer failed for {}, loading it untransformed",
                            name,
                            cause);
                    return input;
                }

                byte[] bytes =
                        result instanceof byte[]
                                ? (byte[]) result
                                : input;

                if (shouldTransform(name)
                        && bytes != null
                        && bytes.length > 0) {

                    return ClassPatcher.transform(bytes, isFlashbackClass(name));
                }

                return result;
            }

            return method.invoke(target, args);
        };

        Object wrapper =
                Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{iface},
                        handler
                );

        setFieldValue(
                delegate,
                "mixinTransformer",
                wrapper
        );
    }

    // The imgui.moulberry90 -> imgui.whaltermc90 rename is a plain package
    // rename, so it has to be applied to every class that mentions the package
    // or the JVM ends up with mismatched descriptors across the addon boundary.
    // Addon mods (flashback_studio, fbactors, lattice, ...) are compiled against
    // imgui.moulberry90 too, so restricting this to com.moulberry.* left those
    // classes pointing at a package that no longer exists.
    // ClassPatcher still no-ops for any class that does not match its content,
    // so this stays cheap. Never touch our own classes.
    private static boolean shouldTransform(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }

        // Knot names use dots; be tolerant of slashes too.
        String dotted = className.replace('/', '.');

        // LWJGL's own classes never need the imgui rename or the GLFW guard:
        // they are the library being guarded, not callers of it. Rewriting
        // them only ever added stub methods to GLFW itself, which is how a
        // missing LWJGL entry point turned into a NoSuchMethodError for
        // everybody instead of just for the caller.
        if (dotted.startsWith("org.lwjgl.")) {
            return false;
        }

        return !dotted.startsWith("dev.whaltermc.");
    }

    // Only Flashback's own classes get the GLFW call rewrites; those redirect
    // calls to our fallback stubs and must not be forced onto other mods.
    private static boolean isFlashbackClass(String className) {
        String dotted = className.replace('/', '.');
        return dotted.startsWith("com.moulberry.flashback")
                || dotted.startsWith("com.moulberry.");
    }

    private static Object getFieldValue(
            Object instance,
            String name
    ) throws Exception {

        Field f =
                findField(
                        instance.getClass(),
                        name
                );

        f.setAccessible(true);

        try {
            MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(
                            f.getDeclaringClass(),
                            MethodHandles.lookup()
                    );

            return lookup
                    .unreflectGetter(f)
                    .invoke(instance);

        } catch (Throwable ignored) {
            return f.get(instance);
        }
    }

    private static void setFieldValue(
            Object instance,
            String name,
            Object value
    ) throws Exception {

        Field f =
                findField(
                        instance.getClass(),
                        name
                );

        f.setAccessible(true);

        try {
            MethodHandles.Lookup lookup =
                    MethodHandles.privateLookupIn(
                            f.getDeclaringClass(),
                            MethodHandles.lookup()
                    );

            lookup
                    .unreflectSetter(f)
                    .invoke(instance, value);

        } catch (Throwable ignored) {
            f.set(instance, value);
        }
    }

    private static Field findField(
            Class<?> clazz,
            String name
    ) throws NoSuchFieldException {

        for (Class<?> c = clazz;
             c != null;
             c = c.getSuperclass()) {

            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }

        throw new NoSuchFieldException(
                name + " in " + clazz.getName()
        );
    }

    private static Method findMethod(
            Class<?> clazz,
            String name,
            Class<?>... params
    ) throws NoSuchMethodException {

        for (Class<?> c = clazz;
             c != null;
             c = c.getSuperclass()) {

            try {
                return c.getDeclaredMethod(
                        name,
                        params
                );
            } catch (NoSuchMethodException ignored) {
            }
        }

        throw new NoSuchMethodException(
                name + " in " + clazz.getName()
        );
    }

    // Which jar is actually running. Launchers install whatever they last
    // downloaded, so a log without this is a guess about the build.
    private static String flashbackRedroided$buildHash() {
        try (java.io.InputStream in = FbaPreLaunch.class.getResourceAsStream(
                "/fba-build.txt")) {
            if (in == null) {
                return "dev";
            }
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                if (line.startsWith("fba-build=")) {
                    String hash = line.substring("fba-build=".length()).trim();
                    return hash.isEmpty() ? "dev" : hash;
                }
            }
        } catch (Throwable ignored) {
        }
        return "dev";
    }
}