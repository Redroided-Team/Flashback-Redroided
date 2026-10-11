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
 * Makes Flashback's MediaCodec encoder use the NDK path instead of the JNI one.
 *
 * <p>FFmpeg's MediaCodec encoder defaults to bridging through
 * {@code android.media.MediaCodec}. That class is not visible to the Knot/Fabric
 * classloader on Android, so {@code avcodec_open2} dies with
 * {@code NoClassDefFoundError: android/media/MediaCodec} and the export fails
 * before a single frame is recorded. The {@code ndk_codec=1} option switches the
 * encoder to {@code AMediaCodec}, which needs no Java classes at all.</p>
 *
 * <p>Deliberately does <em>not</em> touch {@code video_c.pix_fmt()}. Flashback
 * drives its own pixel-format contract: {@code tryStart} computes a best format
 * (yuv420p), passes it to {@code setPixelFormat}, and the swscale rescale thread
 * converts every frame into exactly that format. Overriding the encoder to NV12
 * desynchronizes the two sides and {@code recordImage} rejects every frame with
 * "Pixel format doesn't match encoder pixel format". Verified on-device:
 * {@code h264_mediacodec} with {@code ndk_codec=1} accepts YUV420P input and
 * encodes it natively.</p>
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
                // Flashback builds its options with `new AVDictionary(null)`, whose
                // address is 0. That is still valid here: av_dict_set takes
                // AVDictionary** and JavaCPP passes the object's address, so a null
                // wrapper is simply an empty dict that av_dict_set allocates and
                // writes back into the Java object.
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
