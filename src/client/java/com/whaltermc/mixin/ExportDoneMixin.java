package com.whaltermc.mixin;

import com.whaltermc.LongToast;
import com.whaltermc.MobileCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

@Mixin(
        targets = "com.moulberry.flashback.editor.ui.windows.ExportDoneWindow",
        remap = false
)
public abstract class ExportDoneMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/export");

    /**
     * Redirects the platform open call: on some MC builds the callee does not
     * exist yet, and matching the call site removes it regardless. Both the
     * {@code Blaze3D} and {@code Util$OS} spellings are declared; require=0
     * keeps the mixin inert on whichever call is absent.
     */
    @Redirect(
            method = "renderFinishedExports",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/Blaze3D;" +
                            "openPath(Ljava/nio/file/Path;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$openPathBlaze3D(Path path) {
        // Blaze3D.openPath (26.3) just forwards to the platform opener, which is
        // available on every version, so the real open is still attempted first.
        try {
            Util.getPlatform().openPath(path);
        } catch (Throwable ignored) {
        }
        flashbackRedroided$handleOpenedPath(path);
    }

    @Redirect(
            method = "renderFinishedExports",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Util$OS;" +
                            "openPath(Ljava/nio/file/Path;)V",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$openExpectedPath(Util.OS os, Path path) {
        try {
            os.openPath(path);
        } catch (Throwable ignored) {
        }
        flashbackRedroided$handleOpenedPath(path);
    }

    /**
     * On mobile the platform opener lands in launcher cache instead of the
     * export folder, so copy the real path to the clipboard, toast it and
     * log it. Desktop behavior is untouched.
     */
    private static void flashbackRedroided$handleOpenedPath(Path path) {
        try {
            if (!MobileCompat.isMobile() || path == null) {
                return;
            }

            String abs;
            try {
                abs = path.toAbsolutePath().toString();
            } catch (Throwable t) {
                abs = path.toString();
            }

            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(abs);
            } catch (Throwable ignored) {
            }

            LOGGER.info("FBA: export path {}", abs);

            try {
                String name = abs;
                try {
                    Path fileName = path.getFileName();
                    if (fileName != null) {
                        name = fileName.toString();
                    }
                } catch (Throwable ignored) {
                }

                Minecraft.getInstance().gui.toastManager().addToast(new LongToast(
                        SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Export saved"),
                        Component.literal(name),
                        30_000L
                ));
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }
}
