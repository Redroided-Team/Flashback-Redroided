// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package com.whaltermc.mixin;

import com.whaltermc.NfdCancelled;
import com.whaltermc.MobileCompat;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

// Native dialogs, chained: NFD first, then tinyfd, then a hardcoded path.
// Flashback's own dialog on this build goes through LWJGL SDL, which has no
// Android backend and leaves a future parked forever, so it is always
// replaced. Both native legs here block, so they run on a dialog worker
// thread. When neither backend exists on the launcher, exports still land in
// Downloads or the game directory instead of failing. A cancelled native
// dialog completes with null and never cascades: cancelling must stay a cancel.
@Mixin(
        // Verified against the Flashback build that actually ships for MC 26.2
        // (Flashback-0.43.6-for-MC26.2.jar). The 26.3 build of the same version
        // moves this class to .utils, so the two must not be treated as alike.
        targets = "com.moulberry.flashback.exporting.AsyncFileDialogs",
        remap = false,
        priority = 1000
)
public class AsyncFileDialogsMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android");

    private static final ExecutorService DIALOG_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "FbaDialogs");
        worker.setDaemon(true);
        return worker;
    });

    // Neither NFD nor tinyfd has a display on Android, so a dialog can simply
    // never return. Without this the CompletableFuture is never settled and the
    // export does nothing at all, forever, with nothing in the log. Every dialog
    // gets a deadline that settles it with the fallback instead.
    private static final java.util.concurrent.ScheduledExecutorService DIALOG_TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread timer = new Thread(task, "FbaDialogTimeout");
                timer.setDaemon(true);
                return timer;
            });

    private static final long DIALOG_TIMEOUT_SECONDS = 20;

    // Zalith/Pojav set os.version=Android-* and ANDROID_ROOT, but neither NFD
    // nor tinyfd has a dialog backend there: the native call reports success and
    // then never returns, so nothing is ever shown. Skipping both makes Export
    // start immediately instead of after the timeout.
    private static boolean flashbackRedroided$isAndroid() {
        String version = System.getProperty("os.version", "");
        if (version != null && version.toLowerCase().contains("android")) {
            return true;
        }
        return System.getenv("ANDROID_ROOT") != null
                || System.getenv("ZALITH_VERSION_CODE") != null;
    }

    // Runs the hardcoded fallback off-thread and settles the dialog at once.
    private static void flashbackRedroided$useFallback(
            CompletableFuture<String> future,
            String kind,
            Callable<String> fallback
    ) {
        DIALOG_WORKER.execute(() -> {
            String value = null;
            try {
                value = fallback.call();
                if (value != null && value.isEmpty()) {
                    value = null;
                }
            } catch (Throwable t) {
                LOGGER.warn("FBA: {} fallback path failed", kind, t);
            }
            if (value != null) {
                LOGGER.info("FBA: {} -> {}", kind, value);
            }
            flashbackRedroided$settleDialog(future, value);
        });
    }

    // One dialog at a time, mirroring vanilla hasDialog.
    private static volatile CompletableFuture<String> ongoingDialog;

    // NFD_Init runs once; a missing native library fails there first.
    private static volatile boolean nfdReady;

    private static final String DOWNLOADS_PATH = "/storage/emulated/0/Downloads";

    // Cached probe result; -1 unknown, 1 usable, 0 not.
    private static int downloadsUsable = -1;

    // Shared Downloads is the friendliest destination -- the file shows up in
    // any file manager and most gallery apps. It is not reliably writable,
    // though: from Android 10 scoped storage blocks direct writes outside the
    // app's own directories unless the launcher was granted broad storage
    // access, and some launchers run the game in a sandbox that cannot reach it
    // at all. So probe it for real once instead of assuming, and fall back to
    // the game directory when it is not usable.
    private static synchronized boolean flashbackRedroided$canUseDownloads() {
        if (downloadsUsable >= 0) {
            return downloadsUsable == 1;
        }

        boolean usable = false;
        File dir = new File(DOWNLOADS_PATH);
        if (dir.isDirectory() && dir.canWrite()) {
            File probe = null;
            try {
                probe = File.createTempFile("fba-write-probe", ".tmp", dir);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(probe)) {
                    out.write(0);
                }
                usable = true;
            } catch (Throwable ignored) {
            } finally {
                if (probe != null) {
                    try {
                        probe.delete();
                    } catch (Throwable ignored) {
                        // best effort
                    }
                }
            }
        }

        downloadsUsable = usable ? 1 : 0;
        LOGGER.info("Export directory: {}",
                usable ? DOWNLOADS_PATH : "<game dir>/flashback/exports");
        return usable;
    }

    private static File flashbackRedroided$getExportDir() {
        if (MobileCompat.isMobile() && flashbackRedroided$canUseDownloads()) {
            return new File(DOWNLOADS_PATH);
        }

        File dir = new File(
                Minecraft.getInstance().gameDirectory,
                "flashback/exports"
        );
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static String flashbackRedroided$addExtension(
            String name,
            String... filters
    ) {
        name = flashbackRedroided$sanitizeFileName(name);
        if (name == null || name.isEmpty()) {
            name = "export";
        }

        if (filters != null && filters.length > 0
                && filters[0] != null && !filters[0].isEmpty()
                && name.lastIndexOf('.') <= 0) {
            String extension = filters[0].split(",", 2)[0].trim()
                    .replaceFirst("^\\*?\\.", "")
                    .replaceAll("[^A-Za-z0-9_-]", "");
            if (!extension.isEmpty()) {
                name += "." + extension;
            }
        }

        return name;
    }

    private static String flashbackRedroided$sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }

        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[<>:\"|?*\\x00-\\x1f]", "_").trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        return cleaned;
    }

    private static File flashbackRedroided$uniqueOutput(File directory, String name) {
        Path root = directory.toPath().toAbsolutePath().normalize();
        Path candidate = root.resolve(name).normalize();
        if (!candidate.startsWith(root)) {
            candidate = root.resolve("export");
        }

        if (Files.exists(candidate)) {
            String fileName = candidate.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String extension = dot > 0 ? fileName.substring(dot) : "";
            for (int suffix = 1; suffix < 10_000 && Files.exists(candidate); suffix++) {
                candidate = root.resolve(base + " (" + suffix + ")" + extension);
            }
        }
        return candidate.toFile();
    }

    // Claims the dialog slot, or null when another dialog is already open.
    private static CompletableFuture<String> flashbackRedroided$claimDialog() {
        synchronized (AsyncFileDialogsMixin.class) {
            if (ongoingDialog != null
                    || com.moulberry.flashback.exporting.AsyncFileDialogs.hasDialog()) {
                return null;
            }
            var future = new CompletableFuture<String>();
            ongoingDialog = future;
            return future;
        }
    }

    // Releases the slot and completes the future.
    private static void flashbackRedroided$settleDialog(CompletableFuture<String> future, String value) {
        synchronized (AsyncFileDialogsMixin.class) {
            if (ongoingDialog == future) {
                ongoingDialog = null;
            }
        }
        future.complete(value);
    }

    // Settles the dialog with the fallback once nothing has answered for a
    // while. Uses settleDialog rather than future.completeOnTimeout so the
    // dialog slot is released too; a late native result then finds the future
    // already done and harmlessly does nothing.
    private static void flashbackRedroided$armTimeout(
            CompletableFuture<String> future,
            String kind,
            Callable<String> fallback
    ) {
        DIALOG_TIMEOUT.schedule(() -> {
            if (future.isDone()) {
                return;
            }
            try {
                String value = fallback.call();
                if (value != null && !value.isEmpty()) {
                    LOGGER.warn(
                            "FBA: {} dialog produced no answer after {}s, using {}",
                            kind, DIALOG_TIMEOUT_SECONDS, value);
                } else {
                    LOGGER.warn(
                            "FBA: {} dialog produced no answer after {}s, cancelling",
                            kind, DIALOG_TIMEOUT_SECONDS);
                }
                flashbackRedroided$settleDialog(future, value);
            } catch (Throwable t) {
                LOGGER.warn("FBA: {} dialog timeout failed", kind, t);
                flashbackRedroided$settleDialog(future, null);
            }
        }, DIALOG_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    // Loads the NFD natives once; failure surfaces at the dialog call instead.
    private static void flashbackRedroided$ensureNfd() {
        if (!nfdReady) {
            synchronized (AsyncFileDialogsMixin.class) {
                if (!nfdReady) {
                    try {
                        NativeFileDialog.NFD_Init();
                    } catch (Throwable ignored) {
                    }
                    nfdReady = true;
                }
            }
        }
    }

    // Single NFD filter item like vanilla: description plus comma-joined raw
    // filters, or null for no filtering.
    private static NFDFilterItem.Buffer flashbackRedroided$nfdFilter(
            MemoryStack stack,
            String filterDescription,
            String[] filters
    ) {
        var joined = new StringBuilder();
        if (filters != null) {
            for (String entry : filters) {
                if (entry == null || entry.isEmpty()) {
                    continue;
                }
                if (joined.length() > 0) {
                    joined.append(',');
                }
                joined.append(entry);
            }
        }
        if (joined.length() == 0) {
            return null;
        }
        NFDFilterItem.Buffer item = NFDFilterItem.malloc(1);
        item.get(0)
                .name(stack.UTF8(filterDescription != null ? filterDescription : ""))
                .spec(stack.UTF8(joined.toString()));
        return item;
    }

    // Reads the picked path out of an NFD result pointer, freeing it after.
    private static String flashbackRedroided$nfdPath(PointerBuffer outPath) {
        try {
            return outPath.getStringUTF8(0);
        } finally {
            try {
                NativeFileDialog.NFD_FreePath(outPath.get(0));
            } catch (Throwable ignored) {
            }
        }
    }

    // Appends the single-filter extension when the picked name has none.
    private static String flashbackRedroided$withExtension(String path, String autoExtension) {
        if (path != null && autoExtension != null && path.indexOf('.') < 0) {
            return path + "." + autoExtension;
        }
        return path;
    }

    // Shared chain used by all three dialogs, always off-thread: NFD, then
    // tinyfd, then the hardcoded fallback. Cancelled natives settle null
    // directly; failures fall through to the next leg. Plain JDK functional
    // types on purpose: helper types inside a mixin package cannot be
    // referenced and crash loading with IllegalClassLoadError.
    private static void flashbackRedroided$dialogChain(
            String kind,
            CompletableFuture<String> future,
            Function<String, String> fix,
            Callable<String> nfd,
            Callable<String> tiny,
            Callable<String> fallback
    ) {
        DIALOG_WORKER.execute(() -> {
            String value = null;
            try {
                value = fix.apply(nfd.call());
            } catch (NfdCancelled cancelled) {
                LOGGER.info("FBA: {} dialog cancelled by the user", kind);
                flashbackRedroided$settleDialog(future, null);
                return;
            } catch (Throwable t) {
                LOGGER.warn("FBA: NFD {} dialog failed, trying tinyfd", kind, t);
            }
            if (value == null) {
                try {
                    value = fix.apply(tiny.call());
                } catch (Throwable t) {
                    LOGGER.warn("FBA: tinyfd {} dialog failed, using the fallback", kind, t);
                }
            }
            if (value != null && value.isEmpty()) {
                value = null;
            }
            if (value == null) {
                LOGGER.warn(
                        "FBA: no native {} dialog answered, using the fallback", kind);
                try {
                    value = fallback.call();
                } catch (Throwable t) {
                    LOGGER.warn("FBA: {} fallback path failed", kind, t);
                    value = null;
                }
            } else {
                LOGGER.info("FBA: {} dialog returned {}", kind, value);
            }
            flashbackRedroided$settleDialog(future, value);
        });
    }

    // Bare extensions for tinyfd: "mp4", ".png" and "*.replay" all become "mp4".
    private static String[] flashbackRedroided$tinyExtensions(String[] filters) {
        var cleaned = new ArrayList<String>();
        if (filters != null) {
            for (String entry : filters) {
                if (entry == null) {
                    continue;
                }
                for (String part : entry.split("[,;\\s]+")) {
                    String ext = part.strip();
                    while (ext.startsWith("*")) {
                        ext = ext.substring(1);
                    }
                    while (ext.startsWith(".")) {
                        ext = ext.substring(1);
                    }
                    if (!ext.isEmpty()) {
                        cleaned.add(ext);
                    }
                }
            }
        }
        return cleaned.toArray(new String[0]);
    }

    // tinyfd wants "*.ext" entries in a pointer buffer; empty means no filtering.
    private static PointerBuffer flashbackRedroided$tinyPatterns(MemoryStack stack, String[] filters) {
        String[] exts = flashbackRedroided$tinyExtensions(filters);
        if (exts.length == 0) {
            return null;
        }
        PointerBuffer pointers = stack.mallocPointer(exts.length);
        for (String ext : exts) {
            pointers.put(stack.UTF8("*." + ext));
        }
        pointers.flip();
        return pointers;
    }

    // Hardcoded export path in Downloads or the game directory; exports must land somewhere.
    private static String flashbackRedroided$fallbackExportPath(String defaultName, String[] filters) {
        File exportDir = flashbackRedroided$getExportDir();
        String fileName = flashbackRedroided$addExtension(defaultName, filters);
        File output = flashbackRedroided$uniqueOutput(exportDir, fileName);
        LOGGER.warn("Using default export path: {}", output.getAbsolutePath());
        return output.getAbsolutePath();
    }

    @Inject(
            method = "saveFileDialog(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$saveFileDialog(
            String defaultPath,
            String defaultName,
            String filterDescription,
            String[] filters,
            CallbackInfoReturnable<CompletableFuture<String>> cir
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }
        cir.setReturnValue(future);

        String autoExtension =
                filters.length == 1 ? filters[0] : null;

        LOGGER.info(
                "FBA: save dialog requested (name={}, filters={})",
                defaultName, filters == null ? 0 : filters.length);

        flashbackRedroided$armTimeout(future, "save",
                () -> flashbackRedroided$fallbackExportPath(defaultName, filters));

        if (flashbackRedroided$isAndroid()) {
            flashbackRedroided$useFallback(future, "save",
                    () -> flashbackRedroided$fallbackExportPath(defaultName, filters));
            return;
        }

        flashbackRedroided$dialogChain(
                "save",
                future,
                path -> flashbackRedroided$withExtension(path, autoExtension),
                () -> {
                    flashbackRedroided$ensureNfd();
                    try (var stack = MemoryStack.stackPush()) {
                        PointerBuffer outPath = stack.callocPointer(1);
                        int result = NativeFileDialog.NFD_SaveDialog(
                                outPath,
                                flashbackRedroided$nfdFilter(stack, filterDescription, filters),
                                defaultPath != null ? defaultPath : "",
                                defaultName != null ? defaultName : "export");
                        if (result == NativeFileDialog.NFD_CANCEL) {
                            throw new NfdCancelled();
                        }
                        if (result != NativeFileDialog.NFD_OKAY) {
                            return null;
                        }
                        return flashbackRedroided$nfdPath(outPath);
                    }
                },
                () -> {
                    try (var stack = MemoryStack.stackPush()) {
                        return TinyFileDialogs.tinyfd_saveFileDialog(
                                filterDescription != null ? filterDescription : "Save file",
                                defaultName != null && !defaultName.isEmpty() ? defaultName : "export",
                                flashbackRedroided$tinyPatterns(stack, filters),
                                filterDescription);
                    }
                },
                () -> flashbackRedroided$fallbackExportPath(defaultName, filters));
    }

    @Inject(
            method = "openFileDialog(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$openFileDialog(
            String defaultPath,
            String filterDescription,
            String[] filters,
            CallbackInfoReturnable<CompletableFuture<String>> cir
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }
        cir.setReturnValue(future);

        // Imports have no hardcoded fallback: null means the user gets no file.
        LOGGER.info("FBA: open dialog requested (path={})", defaultPath);

        flashbackRedroided$armTimeout(future, "open", () -> null);

        if (flashbackRedroided$isAndroid()) {
            flashbackRedroided$useFallback(future, "open", () -> null);
            return;
        }

        flashbackRedroided$dialogChain(
                "open",
                future,
                path -> path,
                () -> {
                    flashbackRedroided$ensureNfd();
                    try (var stack = MemoryStack.stackPush()) {
                        PointerBuffer outPath = stack.callocPointer(1);
                        int result = NativeFileDialog.NFD_OpenDialog(
                                outPath,
                                flashbackRedroided$nfdFilter(stack, filterDescription, filters),
                                defaultPath != null ? defaultPath : "");
                        if (result == NativeFileDialog.NFD_CANCEL) {
                            throw new NfdCancelled();
                        }
                        if (result != NativeFileDialog.NFD_OKAY) {
                            return null;
                        }
                        return flashbackRedroided$nfdPath(outPath);
                    }
                },
                () -> {
                    try (var stack = MemoryStack.stackPush()) {
                        return TinyFileDialogs.tinyfd_openFileDialog(
                                filterDescription != null ? filterDescription : "Open file",
                                defaultPath != null ? defaultPath : "",
                                flashbackRedroided$tinyPatterns(stack, filters),
                                filterDescription,
                                false);
                    }
                },
                () -> null);
    }

    @Inject(
            method = "openFolderDialog(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$openFolderDialog(
            String defaultPath,
            CallbackInfoReturnable<CompletableFuture<String>> cir
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            cir.setReturnValue(CompletableFuture.completedFuture(null));
            return;
        }
        cir.setReturnValue(future);

        LOGGER.info("FBA: folder dialog requested (path={})", defaultPath);

        Callable<String> fallback = () -> {
            File exportDir = flashbackRedroided$getExportDir();
            LOGGER.warn("Using default export folder: {}", exportDir.getAbsolutePath());
            return exportDir.getAbsolutePath();
        };

        flashbackRedroided$armTimeout(future, "folder", fallback);

        if (flashbackRedroided$isAndroid()) {
            flashbackRedroided$useFallback(future, "folder", fallback);
            return;
        }

        flashbackRedroided$dialogChain(
                "folder",
                future,
                path -> path,
                () -> {
                    flashbackRedroided$ensureNfd();
                    try (var stack = MemoryStack.stackPush()) {
                        PointerBuffer outPath = stack.callocPointer(1);
                        int result = NativeFileDialog.NFD_PickFolder(
                                outPath,
                                defaultPath != null ? defaultPath : "");
                        if (result == NativeFileDialog.NFD_CANCEL) {
                            throw new NfdCancelled();
                        }
                        if (result != NativeFileDialog.NFD_OKAY) {
                            return null;
                        }
                        return flashbackRedroided$nfdPath(outPath);
                    }
                },
                () -> TinyFileDialogs.tinyfd_selectFolderDialog(
                        "Select folder",
                        defaultPath != null ? defaultPath : ""),
                fallback);
    }
}
