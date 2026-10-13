// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs the two pixel formats {@code recordImage} compares (frame vs encoder)
 * for the first frames of an export.
 *
 * <p>Kept in its own mixin class so a bad injection here cannot disable the
 * encoder fix in {@link FlashbackFFmpegFrameRecorderMixin}.</p>
 */
@Mixin(
        targets = "com.moulberry.flashback.exporting.FlashbackFFmpegFrameRecorder",
        remap = false
)
public abstract class FFmpegRecordImageDiagMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/mediacodec");

    private static final AtomicInteger FRAMES_LOGGED = new AtomicInteger();

    @Shadow(remap = false)
    private AVCodecContext video_c;

    @Inject(
            method = "recordImage",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private void flashbackRedroided$logFormats(
            int width, int height, int pixelFormat, ByteBuffer image, CallbackInfo ci) {
        if (FRAMES_LOGGED.getAndIncrement() >= 2) {
            return;
        }
        try {
            int encoderFormat = (video_c == null || video_c.isNull()) ? -999 : video_c.pix_fmt();
            int capacity = (image == null) ? -1 : image.capacity();
            LOGGER.info("FBA: recordImage frame={} w={} h={} frameFmt={} encoderFmt={} capacity={}",
                    FRAMES_LOGGED.get(), width, height, pixelFormat, encoderFormat, capacity);
        } catch (Throwable t) {
            LOGGER.info("FBA: recordImage frame format log failed: {}", t.toString());
        }
    }
}
