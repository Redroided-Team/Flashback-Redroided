// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

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
 * Reports which containers the export window will actually offer.
 *
 * <p>{@code StartExportWindow} keeps a container only when
 * {@code getSupportedVideoCodecs} returns something for it, so a device where
 * every encoder fails to open yields an empty list and the Export button has
 * nothing to offer -- it silently does nothing. "Button does nothing" and "no
 * encoder opened" are the same bug from two sides, and this line is what tells
 * them apart.</p>
 *
 * <p>In its own mixin class: Mixin treats a failed injection-point validation
 * as fatal for the whole mixin, and one bad annotation here must never be able
 * to disable anything else.</p>
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
                // Name what failed rather than leaving an empty list to puzzle
                // over: each container's codecs are probed the same way, so an
                // empty result everywhere means no encoder opened at all.
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
