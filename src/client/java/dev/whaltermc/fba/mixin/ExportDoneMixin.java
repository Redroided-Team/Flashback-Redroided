// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import dev.whaltermc.fba.AndroidInput;
import dev.whaltermc.fba.LongToast;
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
     * The export-done window opens the entry path through the platform
     * opener, which has nowhere useful to go on Android launchers (it lands
     * in launcher cache instead of the selected export folder). On mobile,
     * copy the real path to the clipboard, toast it and log it instead of
     * opening the wrong place. Desktop behavior is untouched.
     * require=0: inert if Flashback reworks the window.
     */
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
    private static void fba$openExpectedPath(Util.OS os, Path path) {
        // Always attempt the real open first: on launchers whose platform
        // handler resolves absolute paths correctly this just works.
        try {
            os.openPath(path);
        } catch (Throwable ignored) {
        }
        // On mobile the platform opener may land in launcher cache instead,
        // so additionally hand the user the real path: clipboard + toast + log.
        try {
            if (AndroidInput.isMobile() && path != null) {
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
                LOGGER.info("FBA: export saved at {}", abs);
                try {
                    String name = abs;
                    try {
                        Path fileName = path.getFileName();
                        if (fileName != null) {
                            name = fileName.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    Minecraft.getInstance().getToastManager().addToast(new LongToast(
                            SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                            Component.literal("Export saved"),
                            Component.literal(name),
                            30_000L
                    ));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
