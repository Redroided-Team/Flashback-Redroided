// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;

/**
 * Routes Flashback's MediaCodec encoder through {@code AMediaCodec} ({@code ndk_codec=1}).
 *
 * <p>The JNI bridge needs {@code android.media.MediaCodec}, invisible to the Knot
 * classloader. Leaves {@code video_c.pix_fmt()} untouched to match Flashback's
 * rescale contract.</p>
 */
@Mixin(
        targets = "com.moulberry.flashback.exporting.FlashbackFFmpegFrameRecorder",
        remap = false
)
public abstract class FlashbackFFmpegFrameRecorderMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android/mediacodec");

    private static final AtomicBoolean LOGGED_NDK = new AtomicBoolean(false);

    private static boolean flashbackRedroided$isMediaCodec(AVCodec codec) {
        try {
            return codec != null
                    && !codec.isNull()
                    && codec.name() != null
                    && codec.name().getString().endsWith("_mediacodec");
        } catch (Throwable t) {
            return false;
        }
    }

    @WrapOperation(
            method = "startUnsafe",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_open2(" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodec;" +
                            "Lorg/bytedeco/ffmpeg/avutil/AVDictionary;)I",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private int flashbackRedroided$forceNdkCodec(
            AVCodecContext ctx,
            AVCodec codec,
            AVDictionary options,
            Operation<Integer> original
    ) {
        if (flashbackRedroided$isMediaCodec(codec)) {
            try {
                // A null-address AVDictionary wrapper is still valid for av_dict_set.
                if (options != null) {
                    av_dict_set(options, "ndk_codec", "1", 0);
                }
                if (LOGGED_NDK.compareAndSet(false, true)) {
                    LOGGER.info("FBA: forcing ndk_codec=1 for {}", codec.name().getString());
                }
            } catch (Throwable t) {
                LOGGER.warn("FBA: failed to set ndk_codec, continuing with default", t);
            }
        }

        return original.call(ctx, codec, options);
    }
}
