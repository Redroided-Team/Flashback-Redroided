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
        // the bionic-built android-arm64 libraries ourselves below. JavaCPP
        // detects this OS as linux-arm64 and would otherwise extract +
        // dlopen unusable .so files from upstream jars (linker namespace
        // UnsatisfiedLinkError). Loader.isLoadLibraries() reads this property
        // dynamically, so leaving it set keeps all future Loader.load() calls
        // as no-ops. Do NOT clear it, not even on failure.
        System.setProperty("org.bytedeco.javacpp.loadlibraries", "false");

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
            for (String lib : LIBS) {
                Path p = dir.resolve(lib);
                if (!Files.isRegularFile(p)) {
                    LOGGER.warn("Native missing: {}", p);
                    continue;
                }
                System.load(p.toAbsolutePath().toString());
                loaded++;
            }

            LOGGER.info("Loaded {} FFmpeg/JavaCPP natives from {} ({})", loaded, dir, source);

        } catch (Throwable t) {
            LOGGER.error("Failed to load bundled FFmpeg natives", t);
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
        } catch (Throwable t) {
            LOGGER.debug("Launcher dir lookup failed", t);
        }
        return null;
    }

    private static void extract(ModContainer mod, Path dir) throws Exception {
        for (String lib : LIBS) {
            Optional<Path> src = mod.findPath(RES_DIR + lib);
            if (src.isEmpty()) {
                LOGGER.warn("Bundled native missing from jar: {}{}", RES_DIR, lib);
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
}
