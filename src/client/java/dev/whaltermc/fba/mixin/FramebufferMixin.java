// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import org.lwjgl.opengl.GL30C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.ByteBuffer;

@Mixin(targets = "com.moulberry.flashback.exporting.SaveableFramebuffer", remap = false)
public abstract class FramebufferMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android");
    private static volatile boolean loggedSyncRetry;
    private static volatile boolean loggedSubData;

    // Replaces the pixel-pack download with a fallback chain. Plain glMapBuffer
    // returns null on several mobile drivers (mapping a busy PBO fails instead
    // of stalling), which Flashback turns into a crash. The size comes from
    // the bound buffer itself, so no locals are captured and renames cannot
    // silently disable the redirect the way @Local captures did before.
    @Redirect(
            method = "finishDownload",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL30C;glMapBuffer(II)Ljava/nio/ByteBuffer;",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private ByteBuffer flashbackRedroided$mapPixelBuffer(int target, int access) {
        long size = 0;
        try {
            size = GL30C.glGetBufferParameteri(target, GL30C.GL_BUFFER_SIZE);
        } catch (Throwable ignored) {
        }
        if (size <= 0 || size > Integer.MAX_VALUE) {
            // Cannot size a replacement; run the original call untouched.
            return GL30C.glMapBuffer(target, access);
        }

        ByteBuffer mapped = GL30C.glMapBufferRange(target, 0, size, GL30C.GL_MAP_READ_BIT);
        if (mapped != null) {
            return mapped;
        }

        // The GPU may still be reading into the PBO; stall until it is done
        // and retry once before giving up on mapping.
        try {
            GL30C.glFinish();
        } catch (Throwable ignored) {
        }
        mapped = GL30C.glMapBufferRange(target, 0, size, GL30C.GL_MAP_READ_BIT);
        if (mapped != null) {
            if (!loggedSyncRetry) {
                loggedSyncRetry = true;
                LOGGER.debug("PBO map needed a glFinish stall; export continues");
            }
            return mapped;
        }

        // Last resort: synchronous read with no mapping involved. GC-managed
        // direct buffer, so no native leak if the export runs for thousands
        // of frames. The unmap Flashback issues afterwards is a no-op error
        // the driver ignores.
        try {
            ByteBuffer copy = ByteBuffer.allocateDirect((int) size);
            GL30C.glGetBufferSubData(target, 0, copy);
            if (!loggedSubData) {
                loggedSubData = true;
                LOGGER.debug("PBO unmappable; falling back to glGetBufferSubData");
            }
            return copy;
        } catch (Throwable t) {
            LOGGER.debug("PBO download failed entirely", t);
            return null;
        }
    }
}
