// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class NativeLoader {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/natives");

    private static final String MOD_ID = "flashback_android";
    private static final String RES_DIR = "lib/arm64-v8a/";

    private static final String[] LIBS = {
            // C++ runtime first: the bundled ImGui native links against our
            // renamed copy (libc++_fba.so, SONAME-bumped so the game JVM's own
            // libc++ can't shadow it). Pre-loading it makes the later ImGui
            // dlopen resolve against our copy and succeed.
            "libc++_fba.so",
            "libjnijavacpp.so",
            "libavutil.so", "libjniavutil.so",
            "libswresample.so", "libjniswresample.so",
            "libavcodec.so", "libjniavcodec.so",
            "libavformat.so", "libjniavformat.so",
            "libswscale.so", "libjniswscale.so",
            "libavfilter.so", "libjniavfilter.so",
            "libavdevice.so", "libjniavdevice.so"
    };

    private static boolean done;

    private NativeLoader() {}

    public static synchronized void init() {
        if (done) return;
        done = true;

        // Permanently disable JavaCPP's own native loader on Android — we load
        // the bionic-built android-arm64 libraries ourselves below.
        // JavaCPP detects this OS as linux-arm64, so if left enabled its
        // Loader.load() (triggered e.g. by VideoContainer when the export
        // screen opens) extracts glibc-linked linux-arm64 .so files from
        // upstream jars into a cache dir the runtime linker namespace cannot
        // access ("... is not accessible for the namespace" +
        // UnsatisfiedLinkError). Loader.isLoadLibraries() reads this property
        // dynamically on every call, so leaving it set keeps all future
        // Loader.load() calls as no-ops and every native call resolves
        // against the preloaded libraries. Do NOT clear it.
        System.setProperty("org.bytedeco.javacpp.loadlibraries", "false");

        loadSystemLib("mediandk");
        loadSystemLib("android");

        try {
            ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .orElseThrow(() -> new IllegalStateException("mod container not found: " + MOD_ID));

            Path dir = launcherExtractedDir(mod);
            String source = "launcher-extracted";
            if (dir == null) {
                dir = Path.of(System.getProperty("java.io.tmpdir"))
                        .resolve("flashback-android-natives");
                Files.createDirectories(dir);
                extract(mod, dir);
                source = "extracted from jar";
            }

            int loaded = 0;
            int missing = 0;
            for (String lib : LIBS) {
                Path p = dir.resolve(lib);
                if (!Files.isRegularFile(p)) {
                    LOGGER.warn("Native missing: {}", p);
                    missing++;
                    continue;
                }
                System.load(p.toAbsolutePath().toString());
                loaded++;
            }

            if (missing > 0) {
                LOGGER.warn("Loaded {}/{} Android natives from {} ({} missing)",
                        loaded, LIBS.length, source, missing);
            } else {
                LOGGER.info("All {} Android natives loaded from {}", loaded, source);
            }

        } catch (Throwable t) {
            LOGGER.error("Failed to load Android natives", t);
        }
    }

    private static Path launcherExtractedDir(ModContainer mod) {
        try {
            String javaHome = System.getProperty("java.home");
            if (javaHome == null) return null;

            for (Path origin : mod.getOrigin().getPaths()) {
                Path name = origin.getFileName();
                if (name == null) continue;
                Path dir = Path.of(javaHome, "lib", "jli", "javacpp", name.toString(), "lib", "arm64-v8a");
                boolean all = true;
                for (String lib : LIBS) {
                    if (!Files.isRegularFile(dir.resolve(lib))) { all = false; break; }
                }
                if (all) return dir;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void extract(ModContainer mod, Path dir) throws Exception {
        for (String lib : LIBS) {
            Optional<Path> src = mod.findPath(RES_DIR + lib);
            if (src.isEmpty()) {
                continue;
            }
            Path dst = dir.resolve(lib);
            if (!Files.exists(dst) || Files.size(dst) != Files.size(src.get())) {
                try (InputStream in = Files.newInputStream(src.get())) {
                    Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void loadSystemLib(String name) {
        try {
            System.loadLibrary(name);
            // loaded
            return;
        } catch (Throwable ignored) {
        }
        String[] paths = {
            "/system/lib64/lib" + name + ".so",
            "/system/lib/lib" + name + ".so",
            "/apex/com.android.media/lib64/lib" + name + ".so",
            "/apex/com.android.media.swcodec/lib64/lib" + name + ".so"
        };
        for (String path : paths) {
            try {
                System.load(path);
                // loaded
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}