// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package com.whaltermc.mixin;

import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs which containers the export window will offer, and per-container
 * detail when none qualify (an empty offer list means no encoder opened).
 *
 * <p>Kept in its own mixin class so a bad injection here cannot disable
 * anything else.</p>
 */
@Mixin(targets = "com.moulberry.flashback.combo_options.VideoContainer", remap = false)
public abstract class VideoContainerDiagMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android");

    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    @Inject(
            method = "findSupportedContainers",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void flashbackRedroided$logContainers(
            boolean withTransparency,
            CallbackInfoReturnable<VideoContainer[]> cir
    ) {
        if (!LOGGED.compareAndSet(false, true)) {
            return;
        }

        try {
            VideoContainer[] supported = cir.getReturnValue();
            VideoContainer[] all = VideoContainer.values();

            String supportedNames = supported == null || supported.length == 0
                    ? "<none>"
                    : Arrays.toString(supported);

            LOGGER.info(
                    "FBA: export options: {} of {} container(s) supported{}: {}",
                    supported == null ? 0 : supported.length,
                    all.length,
                    withTransparency ? " (transparency)" : "",
                    supportedNames
            );

            if (supported == null || supported.length == 0) {
                StringBuilder detail = new StringBuilder();
                for (VideoContainer container : all) {
                    VideoCodec[] codecs = container.getSupportedVideoCodecs(withTransparency);
                    detail.append('\n').append("    ").append(container)
                            .append(" -> ")
                            .append(codecs == null || codecs.length == 0
                                    ? "no codecs"
                                    : Arrays.toString(codecs));
                }
                LOGGER.info("FBA: no container had a working encoder:{}", detail);
            }
        } catch (Throwable t) {
            LOGGER.info("FBA: export option logging failed: {}", t.toString());
        }
    }
}
