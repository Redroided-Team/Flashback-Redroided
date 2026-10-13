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

    private NativeLoader() {}

    public static synchronized void init() {
        if (done) return;
        done = true;

        // Disables JavaCPP's own loader: it detects Android as linux-arm64 and
        // would dlopen unusable upstream .so files. Never clear this flag.
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
                try {
                    Path p = dir.resolve(lib);
                    if (!Files.isRegularFile(p)) {
                        LOGGER.warn("Native missing: {}", p);
                        continue;
                    }
                    System.load(p.toAbsolutePath().toString());
                    loaded++;
                } catch (Throwable t) {
                    // One broken lib must not skip the rest (or the ImGui pin
                    // below): keep loading and report at the end.
                    LOGGER.warn("FBA: failed to load native, continuing", t);
                }
            }

            LOGGER.info("Loaded {} FFmpeg/JavaCPP natives from {} ({})", loaded, dir, source);

            pinImGuiNative(mod, dir);

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
        } catch (Throwable ignored) {
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
}
