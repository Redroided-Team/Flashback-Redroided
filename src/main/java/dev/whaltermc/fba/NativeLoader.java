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

    // ImGui native inside our own jar (whaltermc90 JNI symbols).
    private static final String IMGUI_RES = "io/imgui/java/native-bin/libimgui-java64.so";
    private static final String IMGUI_FILE = "libimgui-java64.so";

    // Preset classes touched while the natives below load (JavaCPP resolves
    // struct offsets through them). Warmed up front, single-threaded, so an
    // unreadable jar entry fails here with a clear name instead of surfacing
    // mid-load as a confusing ZipException.
    private static final String[] WARM_CLASSES = {
            "org.bytedeco.javacpp.Pointer",
            "org.bytedeco.javacpp.Loader",
            "org.bytedeco.ffmpeg.avcodec.AVCodec",
            "org.bytedeco.ffmpeg.avcodec.AVCodecContext",
            "org.bytedeco.ffmpeg.avutil.AVFrame",
            "org.bytedeco.ffmpeg.avutil.AVDictionary",
            "org.bytedeco.ffmpeg.avformat.AVFormatContext",
            "org.bytedeco.ffmpeg.swresample.SwrContext",
            "org.bytedeco.ffmpeg.swscale.SwsContext",
            "org.bytedeco.ffmpeg.presets.avutil",
            "org.bytedeco.ffmpeg.presets.avcodec",
            "org.bytedeco.ffmpeg.presets.avformat",
            "org.bytedeco.ffmpeg.presets.swresample",
            "org.bytedeco.ffmpeg.presets.swscale",
    };

    private static final String[] LIBS = {
            // C++ runtime first, so the ImGui native binds our SONAME-bumped
            // copy instead of the game runtime's own libc++.
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
    private static boolean systemLibsDone;

    private NativeLoader() {}

    public static synchronized void init() {
        if (done) return;
        done = true;

        // Disables JavaCPP's own loader (it would dlopen unusable
        // linux-arm64 .so files on Android). Never clear this flag.
        System.setProperty("org.bytedeco.javacpp.loadlibraries", "false");

        // Diagnosis escape hatch: -Dfba.skipNatives=true loads nothing.
        // Game boots without FFmpeg/ImGui natives (export/UI degraded).
        if (Boolean.getBoolean("fba.skipNatives")) {
            LOGGER.warn("FBA: fba.skipNatives set, skipping all native loads");
            return;
        }

        warmupPresetClasses();

        // Do NOT eagerly System.load mediandk/android here. On Samsung
        // Android 15/16 (S25 Ultra) an explicit load triggers framework JNI
        // registration for com/android/internal/protolog/ProtoLogNative,
        // which is absent from the launcher JVM classpath -> SIGABRT
        // ("Native registration unable to find class ...; aborting...").
        // An abort kills the process and cannot be caught with try/catch,
        // so it must never run at preLaunch. The linker resolves DT_NEEDED
        // system deps automatically when the bundled FFmpeg libs load.
        // Opt-in only for devices that prove they need explicit preload:
        // -Dfba.loadSystemLibs=true
        if (Boolean.getBoolean("fba.loadSystemLibs")) {
            ensureSystemLibs();
        }

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
            if (Boolean.getBoolean("fba.skipFFmpeg")) {
                LOGGER.warn("FBA: fba.skipFFmpeg set, skipping FFmpeg natives (export disabled)");
                missing = LIBS.length;
            } else for (String lib : LIBS) {
                try {
                    Path p = dir.resolve(lib);
                    if (!Files.isRegularFile(p)) {
                        LOGGER.warn("Native missing: {}", p);
                        missing++;
                        continue;
                    }
                    System.load(p.toAbsolutePath().toString());
                    loaded++;
                } catch (Throwable t) {
                    // One broken lib must not skip the rest (or the ImGui pin
                    // below): keep loading and report at the end.
                    LOGGER.warn("FBA: failed to load native, continuing", t);
                    missing++;
                }
            }

            if (missing > 0) {
                LOGGER.warn("Loaded {}/{} Android natives from {} ({} missing)",
                        loaded, LIBS.length, source, missing);
            } else {
                LOGGER.info("All {} Android natives loaded from {}", loaded, source);
            }

            pinImGuiNative(mod, dir);

        } catch (Throwable t) {
            LOGGER.error("Failed to load Android natives", t);
        }
    }

    private static void warmupPresetClasses() {
        ClassLoader cl = NativeLoader.class.getClassLoader();
        for (String name : WARM_CLASSES) {
            try {
                Class.forName(name, false, cl);
            } catch (Throwable t) {
                LOGGER.warn("FBA: preset class unreadable: {} ({})", name, t.toString());
            }
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

    // Pins imgui-java's loader at our own native: it honors
    // imgui.library.path/name, and some launchers (Zalith v2 native plugins)
    // point those at a stock library whose JNI symbols do not match our
    // renamed imgui.whaltermc90 classes (UnsatisfiedLinkError in
    // ImGui.<clinit>). Our copy carries the matching symbols, so the binding
    // always loads the library it was built against. Failures keep the old
    // behavior (launcher default applies).
    private static void pinImGuiNative(ModContainer mod, Path dir) {
        try {
            Optional<Path> src = mod.findPath(IMGUI_RES);
            if (src.isEmpty()) {
                return;
            }
            Path dst = dir.resolve(IMGUI_FILE);
            if (!Files.exists(dst) || Files.size(dst) != Files.size(src.get())) {
                try (InputStream in = Files.newInputStream(src.get())) {
                    Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            System.setProperty("imgui.library.path", dir.toAbsolutePath().toString());
            System.setProperty("imgui.library.name", IMGUI_FILE);
            LOGGER.info("FBA: pinned ImGui native to {}", dst);
        } catch (Throwable t) {
            LOGGER.warn("FBA: could not pin ImGui native, launcher default applies", t);
        }
    }

    /**
     * Lazy, opt-in explicit preload of NDK system libs. Only runs when
     * -Dfba.loadSystemLibs=true is set. Called from init() (opt-in path) and
     * available to the MediaCodec export path if a device ever proves it
     * needs an explicit preload instead of DT_NEEDED resolution. Never call
     * this unconditionally at preLaunch: on some firmware an explicit
     * System.load of these libs SIGABRTs (ProtoLogNative) and try/catch
     * cannot save the process.
     */
    public static synchronized void ensureSystemLibs() {
        if (systemLibsDone) return;
        systemLibsDone = true;
        if (Boolean.getBoolean("fba.skipNatives")
                || Boolean.getBoolean("fba.skipSystemLibs")) {
            return;
        }
        loadSystemLib("mediandk");
        loadSystemLib("android");
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