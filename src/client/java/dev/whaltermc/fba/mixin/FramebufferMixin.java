// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.nio.ByteBuffer;

@Mixin(targets = "com.moulberry.flashback.exporting.SaveableFramebuffer", remap = false)
public abstract class FramebufferMixin {

    // Fallback chain for the pixel-pack download: plain glMapBuffer returns
    // null on several mobile drivers instead of stalling. Sized from the
    // bound buffer itself, so renames cannot silently disable the redirect.

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

        // The GPU may still be reading the PBO: stall once and retry mapping.

        try {
            GL30C.glFinish();
        } catch (Throwable ignored) {
        }
        mapped = GL30C.glMapBufferRange(target, 0, size, GL30C.GL_MAP_READ_BIT);
        if (mapped != null) {
            return mapped;
        }

        // Last resort: synchronous read with no mapping. GC-managed buffer, so
        // no native leak; the driver's follow-up unmap error is ignored.
        try {
            ByteBuffer copy = ByteBuffer.allocateDirect((int) size);
            GL30C.glGetBufferSubData(target, 0, copy);
            return copy;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
