// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.utils.AsyncFileDialogs;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// Native dialogs, SDL-only: MC 26.3 no longer ships lwjgl-tinyfd, and
// Flashback drives its dialogs through LWJGL's SDL bindings
// (SDL_ShowSave/Open/FolderDialog), so NFD and tinyfd are gone from this
// chain entirely. SDL_Show* is async: it returns immediately and settles
// through a native callback. That callback is retained (see LIVE_CALLBACKS)
// until it fires, because a garbage-collected closure would crash native
// code. SDL's dialog reports success on some Android launchers and then
// never calls back, so every dialog keeps the deadline below: an unanswered
// dialog settles with the game-dir fallback instead of hanging the export
// forever. SDL3 cannot reliably distinguish user-cancel from backend
// failure through this callback (both arrive as a null file list), so a
// null list with an SDL error falls back to game_dir/flashback/exports/
// while a clean null stays a cancel.
@Mixin(targets = "com.moulberry.flashback.utils.AsyncFileDialogs")
public class FileDialogMixin {

    // A dialog callback that never fires must stay alive anyway: freeing its
    // closure while native code may still call it is a use-after-free. Hung
    // dialogs therefore leak one tiny closure instead of risking a crash.
    private static final Set<SDL_DialogFileCallback> LIVE_CALLBACKS =
            ConcurrentHashMap.newKeySet();

    // An SDL dialog that never answers still has to settle, or the export
    // does nothing at all, forever, with nothing in the log. Every dialog
    // gets a deadline that settles it with the fallback instead.
    private static final ScheduledExecutorService DIALOG_TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread timer = new Thread(task, "FbaDialogTimeout");
                timer.setDaemon(true);
                return timer;
            });

    private static final long DIALOG_TIMEOUT_SECONDS = 20;

    // One dialog at a time, mirroring vanilla hasDialog.
    private static volatile CompletableFuture<String> ongoingDialog;

    // The three native allocations behind one SDL filter item. Freed in the
    // dialog callback (mirroring Flashback: valid from Show until callback),
    // or immediately when the Show call itself throws and no callback can
    // ever arrive. Nested inside the mixin on purpose: nothing outside this
    // class references it.
    private static final class SdlFilter {
        private SDL_DialogFileFilter.Buffer buffer;
        private ByteBuffer name;
        private ByteBuffer pattern;

        private void free() {
            try {
                if (buffer != null) {
                    buffer.free();
                }
            } catch (Throwable ignored) {
            }
            try {
                if (name != null) {
                    MemoryUtil.memFree(name);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (pattern != null) {
                    MemoryUtil.memFree(pattern);
                }
            } catch (Throwable ignored) {
            }
            buffer = null;
            name = null;
            pattern = null;
        }
    }

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

    // Single SDL filter item, mirroring Flashback: filters joined with ';',
    // each scrubbed through AsyncFileDialogs.filter like vanilla.
    private static SdlFilter flashbackRedroided$sdlFilter(
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
                    joined.append(';');
                }
                joined.append(AsyncFileDialogs.filter(entry));
            }
        }
        SdlFilter filter = new SdlFilter();
        filter.buffer = SDL_DialogFileFilter.calloc(1);
        filter.name = MemoryUtil.memUTF8(
                AsyncFileDialogs.filter(filterDescription != null ? filterDescription : ""),
                true);
        filter.pattern = MemoryUtil.memUTF8(joined.toString(), true);
        filter.buffer.get(0).name(filter.name).pattern(filter.pattern);
        return filter;
    }

    // SDL has no reliable cancel-vs-error signal (both are a null file
    // list), so a null list with no SDL error is treated as a user cancel
    // while a null list with an error falls through to the fallback. If the
    // error query itself fails, assume failure: exports must land somewhere.
    private static boolean flashbackRedroided$sdlCancelled() {
        try {
            String error = SDLError.SDL_GetError();
            return error == null || error.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long flashbackRedroided$sdlWindow() {
        return Minecraft.getInstance().getWindow().handle();
    }

    private static void flashbackRedroided$releaseCallback(SDL_DialogFileCallback callback) {
        if (callback == null) {
            return;
        }
        LIVE_CALLBACKS.remove(callback);
        try {
            callback.free();
        } catch (Throwable ignored) {
        }
    }

    // Appends the single-filter extension when the picked name has none.
    private static String flashbackRedroided$withExtension(String path, String autoExtension) {
        if (path != null && autoExtension != null && path.indexOf('.') < 0) {
            return path + "." + autoExtension;
        }
        return path;
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

        SdlFilter filter = null;
        final SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
        try {
            filter = flashbackRedroided$sdlFilter(filterDescription, filters);
            final SdlFilter shown = filter;
            String defaultLocation = AsyncFileDialogs.filter(
                    (defaultPath != null ? defaultPath : "")
                            + (defaultName != null ? defaultName : "export"));
            callback[0] = SDL_DialogFileCallback.create((userdata, filelist, filterIndex) -> {
                try {
                    if (filelist == 0L) {
                        if (flashbackRedroided$sdlCancelled()) {
                            Flashback.LOGGER.info("FBA: save dialog cancelled by the user");
                            flashbackRedroided$settleDialog(future, null);
                        } else {
                            Flashback.LOGGER.warn("FBA: SDL save dialog failed, using the fallback");
                            flashbackRedroided$settleDialog(future,
                                    flashbackRedroided$fallbackExportPath(defaultName, autoExtension));
                        }
                        return;
                    }
                    String path = MemoryUtil.memUTF8Safe(MemoryUtil.memGetAddress(filelist));
                    if (path == null || path.isEmpty()) {
                        flashbackRedroided$settleDialog(future,
                                flashbackRedroided$fallbackExportPath(defaultName, autoExtension));
                    } else {
                        Flashback.LOGGER.info("FBA: SDL save dialog returned {}", path);
                        flashbackRedroided$settleDialog(future,
                                flashbackRedroided$withExtension(path, autoExtension));
                    }
                } finally {
                    shown.free();
                    flashbackRedroided$releaseCallback(callback[0]);
                }
            });
            LIVE_CALLBACKS.add(callback[0]);
            SDLDialog.SDL_ShowSaveFileDialog(
                    callback[0], 0L, flashbackRedroided$sdlWindow(),
                    filter.buffer, defaultLocation);
        } catch (Throwable t) {
            // The Show call itself failed (missing natives/classes): no
            // callback can ever arrive, so release everything now and fall
            // back instead of waiting out the timeout.
            Flashback.LOGGER.warn("FBA: SDL save dialog failed, using the fallback", t);
            if (filter != null) {
                filter.free();
            }
            flashbackRedroided$releaseCallback(callback[0]);
            flashbackRedroided$settleDialog(future,
                    flashbackRedroided$fallbackExportPath(defaultName, autoExtension));
        }
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

        SdlFilter filter = null;
        final SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
        try {
            filter = flashbackRedroided$sdlFilter(filterDescription, filters);
            final SdlFilter shown = filter;
            String defaultLocation = AsyncFileDialogs.filter(
                    defaultPath != null ? defaultPath : "");
            callback[0] = SDL_DialogFileCallback.create((userdata, filelist, filterIndex) -> {
                try {
                    if (filelist == 0L) {
                        if (flashbackRedroided$sdlCancelled()) {
                            Flashback.LOGGER.info("FBA: open dialog cancelled by the user");
                        } else {
                            Flashback.LOGGER.warn("FBA: SDL open dialog failed");
                        }
                        flashbackRedroided$settleDialog(future, null);
                        return;
                    }
                    String path = MemoryUtil.memUTF8Safe(MemoryUtil.memGetAddress(filelist));
                    Flashback.LOGGER.info("FBA: SDL open dialog returned {}", path);
                    flashbackRedroided$settleDialog(future, path);
                } finally {
                    shown.free();
                    flashbackRedroided$releaseCallback(callback[0]);
                }
            });
            LIVE_CALLBACKS.add(callback[0]);
            SDLDialog.SDL_ShowOpenFileDialog(
                    callback[0], 0L, flashbackRedroided$sdlWindow(),
                    filter.buffer, defaultLocation, false);
        } catch (Throwable t) {
            Flashback.LOGGER.warn("FBA: SDL open dialog failed", t);
            if (filter != null) {
                filter.free();
            }
            flashbackRedroided$releaseCallback(callback[0]);
            flashbackRedroided$settleDialog(future, null);
        }
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

        final SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
        try {
            String defaultLocation = AsyncFileDialogs.filter(
                    defaultPath != null ? defaultPath : "");
            callback[0] = SDL_DialogFileCallback.create((userdata, filelist, filterIndex) -> {
                try {
                    if (filelist == 0L) {
                        if (flashbackRedroided$sdlCancelled()) {
                            Flashback.LOGGER.info("FBA: folder dialog cancelled by the user");
                            flashbackRedroided$settleDialog(future, null);
                        } else {
                            Flashback.LOGGER.warn("FBA: SDL folder dialog failed, using the fallback");
                            flashbackRedroided$settleDialog(future,
                                    flashbackRedroided$fallbackExportFolder());
                        }
                        return;
                    }
                    String path = MemoryUtil.memUTF8Safe(MemoryUtil.memGetAddress(filelist));
                    if (path == null || path.isEmpty()) {
                        flashbackRedroided$settleDialog(future,
                                flashbackRedroided$fallbackExportFolder());
                    } else {
                        Flashback.LOGGER.info("FBA: SDL folder dialog returned {}", path);
                        flashbackRedroided$settleDialog(future, path);
                    }
                } finally {
                    flashbackRedroided$releaseCallback(callback[0]);
                }
            });
            LIVE_CALLBACKS.add(callback[0]);
            SDLDialog.SDL_ShowOpenFolderDialog(
                    callback[0], 0L, flashbackRedroided$sdlWindow(),
                    defaultLocation, false);
        } catch (Throwable t) {
            Flashback.LOGGER.warn("FBA: SDL folder dialog failed, using the fallback", t);
            flashbackRedroided$releaseCallback(callback[0]);
            flashbackRedroided$settleDialog(future,
                    flashbackRedroided$fallbackExportFolder());
        }
        return future;
    }
}
