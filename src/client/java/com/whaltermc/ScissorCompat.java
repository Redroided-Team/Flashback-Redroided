// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package com.whaltermc;

import com.mojang.blaze3d.systems.RenderPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps Flashback's ImGui backend from handing a render pass a scissor rect
 * that Minecraft 26.2/26.3 refuse to accept.
 *
 * <p>Flashback's {@code CustomImGuiImplB3D.renderDrawData} drops draw commands
 * whose clip rect is empty with {@code if (x2 < x1) skip} / {@code if (y2 < y1)
 * skip}. That should be {@code <=}: when a command is clipped down to nothing
 * both edges are equal, so it is not dropped, and {@code enableScissor(x, y, w,
 * 0)} is called. Minecraft validates its arguments and throws
 * {@code IllegalArgumentException: Scissor size must be >0}, which escapes as a
 * render-frame crash. Every official ImGui backend skips these commands;
 * without rewriting Flashback's loop, clipping the rect to a single pixel is the
 * same thing the user sees and cannot throw.</p>
 *
 * <p>Routed here by {@link FlashbackTransformer}, which swaps the
 * {@code enableScissor} call site for this static helper. The stack shape at the
 * call site is identical (receiver first, then the four ints), so no frame needs
 * to be recomputed.</p>
 */
public final class ScissorCompat {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android");

    private static boolean logged;

    private ScissorCompat() {}

    public static void enableScissor(
            RenderPass pass,
            int x,
            int y,
            int width,
            int height
    ) {
        int clampedWidth = width < 1 ? 1 : width;
        int clampedHeight = height < 1 ? 1 : height;

        if ((clampedWidth != width || clampedHeight != height) && !logged) {
            logged = true;
            LOGGER.info(
                    "FBA: clamped empty scissor {}x{} to {}x{} " +
                            "(Flashback clipped a draw command away)",
                    width, height, clampedWidth, clampedHeight
            );
        }

        pass.enableScissor(x, y, clampedWidth, clampedHeight);
    }
}
