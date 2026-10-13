// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package com.whaltermc;

import com.mojang.blaze3d.systems.RenderPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clamps the scissor rect Flashback's ImGui backend passes to MC 26.2/26.3,
 * which reject empty rects. Flashback drops clipped-away commands with
 * {@code <} instead of {@code <=}, so a fully-clipped command reaches
 * {@code enableScissor} with a zero size and crashes the frame; a 1px clamp
 * looks identical and cannot throw.
 *
 * <p>Routed here by {@link FlashbackTransformer}; the call-site stack shape is
 * unchanged, so no frame recomputation is needed.</p>
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
