// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import com.moulberry.flashback.Flashback;
import dev.whaltermc.fba.NfdCancelled;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

// Native dialogs, chained: NFD first, then a hardcoded path. SDL's dialog
// reports success on Android and then never calls back, so it is skipped
// entirely; NFD either shows a real picker or fails fast into the fallback,
// and exports land in game_dir/flashback/exports/ instead of failing.
// A user-cancelled NFD dialog completes with null and never falls through:
// cancelling must stay a cancel.
@Mixin(targets = "com.moulberry.flashback.utils.AsyncFileDialogs")
public class FileDialogMixin {

    private static final ExecutorService DIALOG_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "FbaDialogs");
        worker.setDaemon(true);
        return worker;
    });

    // A blocking NFD call with no display backend may never return. Without
    // this the CompletableFuture is never settled and the export does nothing
    // at all, forever, with nothing in the log. Every dialog gets a deadline
    // that settles it with the fallback instead.
    private static final java.util.concurrent.ScheduledExecutorService DIALOG_TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread timer = new Thread(task, "FbaDialogTimeout");
                timer.setDaemon(true);
                return timer;
            });

    private static final long DIALOG_TIMEOUT_SECONDS = 20;

    // One dialog at a time, mirroring vanilla hasDialog.
    private static volatile CompletableFuture<String> ongoingDialog;

    // NFD_Init runs once; a missing native library fails there first.
    private static volatile boolean nfdReady;

    private static File flashbackRedroided$getDefaultExportDir() {
        File dir = new File(
                Minecraft.getInstance().gameDirectory,
                "flashback/exports"
        );

        if (!dir.exists() && !dir.mkdirs()) {
            Flashback.LOGGER.warn(
                    "Could not create default export directory: {}",
                    dir.getAbsolutePath()
            );
        }

        return dir;
    }

    // Claims the dialog slot, or null when another dialog is already open.
    private static CompletableFuture<String> flashbackRedroided$claimDialog() {
        synchronized (FileDialogMixin.class) {
            if (ongoingDialog != null
                    || com.moulberry.flashback.utils.AsyncFileDialogs.hasDialog()) {
                return null;
            }
            var future = new CompletableFuture<String>();
            ongoingDialog = future;
            return future;
        }
    }

    // Releases the slot and completes the future.
    private static void flashbackRedroided$settleDialog(CompletableFuture<String> future, String value) {
        synchronized (FileDialogMixin.class) {
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
            java.util.function.Supplier<String> fallback
    ) {
        DIALOG_TIMEOUT.schedule(() -> {
            if (future.isDone()) {
                return;
            }
            try {
                String value = fallback.get();
                if (value != null && !value.isEmpty()) {
                    Flashback.LOGGER.warn(
                            "FBA: {} dialog produced no answer after {}s, using {}",
                            kind, DIALOG_TIMEOUT_SECONDS, value);
                } else {
                    Flashback.LOGGER.warn(
                            "FBA: {} dialog produced no answer after {}s, cancelling",
                            kind, DIALOG_TIMEOUT_SECONDS);
                }
                flashbackRedroided$settleDialog(future, value);
            } catch (Throwable t) {
                Flashback.LOGGER.warn("FBA: {} dialog timeout failed", kind, t);
                flashbackRedroided$settleDialog(future, null);
            }
        }, DIALOG_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    // Loads the NFD natives once; failure surfaces at the dialog call instead.
    private static void flashbackRedroided$ensureNfd() {
        if (!nfdReady) {
            synchronized (FileDialogMixin.class) {
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
    // the hardcoded fallback. A cancelled NFD dialog settles null directly;
    // failures fall through to the fallback. Plain JDK functional types on
    // purpose: helper types inside a mixin package cannot be referenced and
    // crash loading with IllegalClassLoadError.
    private static void flashbackRedroided$nfdThenFallback(
            CompletableFuture<String> future,
            String kind,
            Function<String, String> fix,
            Callable<String> nfd,
            Callable<String> fallback
    ) {
        DIALOG_WORKER.execute(() -> {
            String value = null;
            try {
                value = fix.apply(nfd.call());
            } catch (NfdCancelled cancelled) {
                Flashback.LOGGER.info("FBA: {} dialog cancelled by the user", kind);
                flashbackRedroided$settleDialog(future, null);
                return;
            } catch (Throwable t) {
                Flashback.LOGGER.warn("FBA: NFD {} dialog failed, using the fallback", kind, t);
            }
            if (value != null && value.isEmpty()) {
                value = null;
            }
            if (value == null) {
                try {
                    value = fallback.call();
                } catch (Throwable t) {
                    Flashback.LOGGER.warn("FBA: {} fallback path failed", kind, t);
                    value = null;
                }
            } else {
                Flashback.LOGGER.info("FBA: NFD {} dialog returned {}", kind, value);
            }
            flashbackRedroided$settleDialog(future, value);
        });
    }

    // Hardcoded game-dir export path; exports must land somewhere.
    private static String flashbackRedroided$fallbackExportPath(String defaultName, String autoExtension) {
        String name = defaultName;
        if (name != null && autoExtension != null && name.indexOf('.') < 0) {
            name = name + "." + autoExtension;
        }
        File fallback = new File(
                flashbackRedroided$getDefaultExportDir(),
                name != null ? name : "export"
        );
        Flashback.LOGGER.warn(
                "Using default export path: {}",
                fallback.getAbsolutePath()
        );
        return fallback.getAbsolutePath();
    }

    // Hardcoded game-dir export folder.
    private static String flashbackRedroided$fallbackExportFolder() {
        File fallback = flashbackRedroided$getDefaultExportDir();
        Flashback.LOGGER.warn(
                "Using default export folder: {}",
                fallback.getAbsolutePath()
        );
        return fallback.getAbsolutePath();
    }

    @Overwrite
    public static CompletableFuture<String> saveFileDialog(
            String defaultPath,
            String defaultName,
            String filterDescription,
            String... filters
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }

        String autoExtension =
                filters.length == 1 ? filters[0] : null;

        Flashback.LOGGER.info(
                "FBA: save dialog requested (name={}, filters={})",
                defaultName, filters == null ? 0 : filters.length);

        flashbackRedroided$armTimeout(future, "save",
                () -> flashbackRedroided$fallbackExportPath(defaultName, autoExtension));

        flashbackRedroided$nfdThenFallback(
                future,
                "save",
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
                () -> flashbackRedroided$fallbackExportPath(defaultName, autoExtension));
        return future;
    }

    @Overwrite
    public static CompletableFuture<String> openFileDialog(
            String defaultPath,
            String filterDescription,
            String... filters
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }

        Flashback.LOGGER.info(
                "FBA: open dialog requested (path={})", defaultPath);

        // Imports have no hardcoded fallback: null means the user gets no file.
        flashbackRedroided$armTimeout(future, "open", () -> null);

        flashbackRedroided$nfdThenFallback(
                future,
                "open",
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
                () -> null);
        return future;
    }

    @Overwrite
    public static CompletableFuture<String> openFolderDialog(
            String defaultPath
    ) {
        var future = flashbackRedroided$claimDialog();
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }

        Flashback.LOGGER.info(
                "FBA: folder dialog requested (path={})", defaultPath);

        flashbackRedroided$armTimeout(future, "folder",
                FileDialogMixin::flashbackRedroided$fallbackExportFolder);

        flashbackRedroided$nfdThenFallback(
                future,
                "folder",
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
                FileDialogMixin::flashbackRedroided$fallbackExportFolder);
        return future;
    }
}
