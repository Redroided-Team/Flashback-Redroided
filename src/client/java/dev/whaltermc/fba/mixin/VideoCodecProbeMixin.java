// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.IntPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;

/**
 * Probes MediaCodec encoders the way they actually have to be opened on Android.
 *
 * <p>Flashback's own probe asks {@code avcodec_open2} with the JNI bridge's
 * default options. On Android that path needs {@code android.media.MediaCodec},
 * which the Knot classloader cannot see, so every MediaCodec encoder is reported
 * as broken, the export window has nothing to offer, and pressing Export does
 * nothing at all. Setting {@code ndk_codec=1} routes the probe through
 * {@code AMediaCodec} instead, which is the same path the real export uses.</p>
 *
 * <p>Pixel-format fallbacks matter here too: MediaCodec encoders reject formats
 * their codec profile does not accept, so a single-attempt probe is not a fair
 * test. Each retry gets a fresh {@code AVCodecContext} because a failed open can
 * leave the previous one dirty. The old contexts are deliberately leaked —
 * Flashback owns and frees the context it passed in, and freeing it here would
 * double-free on the success path.</p>
 */
@Mixin(targets = "com.moulberry.flashback.combo_options.VideoCodec", remap = false, priority = 1000)
public abstract class VideoCodecProbeMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android");

    private static final java.util.concurrent.atomic.AtomicBoolean PROBED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Pixel format fallback order for MediaCodec encoder probing.
     * Devices vary: some only accept NV12, others YUV420P, a few prefer NV21.
     */
    private static final int[] MEDIACODEC_PIX_FMT_FALLBACKS = {
            AV_PIX_FMT_NV12,
            AV_PIX_FMT_YUV420P,
            24,                    // AV_PIX_FMT_NV21
    };

    private static boolean isMediaCodecEncoder(AVCodec codec) {
        try {
            return codec != null && !codec.isNull()
                    && codec.name() != null
                    && codec.name().getString().endsWith("_mediacodec");
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean codecSupportsFmt(AVCodec codec, int fmt) {
        try {
            IntPointer fmts = codec.pix_fmts();
            if (fmts == null || fmts.isNull()) return true; // no static constraint
            for (long i = 0; ; i++) {
                int f = fmts.get(i);
                if (f == AV_PIX_FMT_NONE) break;
                if (f == fmt) return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // can't read list, let FFmpeg decide
        }
    }

    @WrapOperation(
            method = "doesEncoderWork",
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
    private static int flashbackRedroided$probeWithPixFmtFallbacks(
            AVCodecContext ctx,
            AVCodec codec,
            AVDictionary options,
            Operation<Integer> original
    ) {
        if (!isMediaCodecEncoder(codec)) {
            return original.call(ctx, codec, options);
        }

        if (PROBED.compareAndSet(false, true)) {
            // Its absence from a log is the real signal: it means this mixin
            // never applied, so the probe is still running FFmpeg's JNI bridge
            // and every MediaCodec encoder will be reported as broken.
            String first = "<unknown>";
            try { first = codec.name().getString(); } catch (Throwable ignored) {}
            LOGGER.info("FBA: probing MediaCodec encoders with ndk_codec=1 (first: {})", first);
        }

        try {
            // No !options.isNull() guard: Flashback builds its options with
            // `new AVDictionary(null)` (address 0). av_dict_set takes
            // AVDictionary**, so JavaCPP passes &address and a null-address
            // wrapper is valid -- guarding on isNull() skipped the option
            // entirely and made the probe fall back to the broken JNI path.
            if (options != null) {
                av_dict_set(options, "ndk_codec", "1", 0);
            }
        } catch (Throwable ignored) {
        }

        String codecName = "<unknown>";
        try { codecName = codec.name().getString(); } catch (Throwable ignored) {}

        int lastResult = -1;
        boolean retried = false;

        for (int fmt : MEDIACODEC_PIX_FMT_FALLBACKS) {
            if (ctx == null || ctx.isNull()) break;

            if (!codecSupportsFmt(codec, fmt)) continue;

            // Flashback owns ctx and frees it in its own finally block. After a
            // failed open the context is dirty, so retries get a fresh one with
            // the fields Flashback already set copied over. Never free the old
            // one here: that would double-free on the success path.
            if (retried) {
                AVCodecContext fresh = null;
                try {
                    fresh = avcodec_alloc_context3(codec);
                    if (fresh == null || fresh.isNull()) break;

                    fresh.codec_id(ctx.codec_id());
                    fresh.codec_type(ctx.codec_type());
                    fresh.bit_rate(ctx.bit_rate());
                    fresh.width(ctx.width());
                    fresh.height(ctx.height());
                    fresh.time_base(ctx.time_base());
                    if ((codec.capabilities() & 512) != 0) {
                        fresh.strict_std_compliance(-2);
                    }

                    ctx = fresh;
                } catch (Throwable ignored) {
                    if (fresh != null && !fresh.isNull()) {
                        try { avcodec_free_context(fresh); } catch (Throwable ignored) {}
                    }
                    break;
                }
            }

            try {
                ctx.pix_fmt(fmt);
            } catch (Throwable ignored) {
                continue;
            }

            lastResult = original.call(ctx, codec, options);
            retried = true;
            if (lastResult >= 0) {
                LOGGER.info("FBA: probe opened {} with pix_fmt={} (ndk_codec=1)",
                        codecName, fmt);
                return lastResult;
            }
        }

        // A negative result here is why the export window ends up with nothing
        // to offer, so record it rather than leaving the empty list unexplained.
        if (lastResult < 0) {
            LOGGER.warn("FBA: probe could not open {} with any of pix_fmt={} (last result {})",
                    codecName, java.util.Arrays.toString(MEDIACODEC_PIX_FMT_FALLBACKS), lastResult);
        }

        return lastResult;
    }
}
