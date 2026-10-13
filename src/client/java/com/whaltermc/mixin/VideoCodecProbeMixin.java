// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package com.whaltermc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.whaltermc.MobileCompat;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.IntPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NONE;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;

@Mixin(targets = "com.moulberry.flashback.combo_options.VideoCodec", remap = false, priority = 1000)
public abstract class VideoCodecProbeMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("flashback_android");

    private static final java.util.concurrent.atomic.AtomicBoolean PROBED =
            new java.util.concurrent.atomic.AtomicBoolean();

    // Software encoders already reported unavailable, so the skip is logged
    // once per encoder instead of on every export-window open.
    private static final Set<String> SKIPPED =
            ConcurrentHashMap.newKeySet();

    /**
     * Pixel format fallback order for MediaCodec probing (device support varies).
     */
    private static final int[] MEDIACODEC_PIX_FMT_FALLBACKS = {
            AV_PIX_FMT_NV12,
            AV_PIX_FMT_YUV420P,
            24, // AV_PIX_FMT_NV21
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
            // Bounded: an unterminated native list must not read out of bounds.
            for (long i = 0; i < 64; i++) {
                int f = fmts.get(i);
                if (f == AV_PIX_FMT_NONE) break;
                if (f == fmt) return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // can't read list, let FFmpeg decide
        }
    }

    /**
     * Probes MediaCodec encoders with pixel-format fallbacks, forcing the NDK
     * path. Retries use a fresh context (a failed open dirties the old one).
     * require=0: inert if Flashback removes avcodec_open2 from doesEncoderWork.
     */
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
            // Software encoders are never opened natively on Android: some
            // (e.g. SVT-AV1) execute instructions missing on budget SoCs and
            // kill the process with SIGILL, which no catch can survive. Report
            // them as unavailable instead; MediaCodec encoders below still
            // probe for real.
            if (MobileCompat.isMobile()) {
                String name = "<unknown>";
                try {
                    if (codec != null && !codec.isNull() && codec.name() != null) {
                        name = codec.name().getString();
                    }
                } catch (Throwable ignored) {
                }
                if (SKIPPED.add(name)) {
                    LOGGER.info("FBA: skipping software encoder probe on Android: {}", name);
                }
                return -22; // EINVAL: unavailable without a native open.
            }
            return original.call(ctx, codec, options);
        }

        if (PROBED.compareAndSet(false, true)) {
            // Logs once that the MediaCodec probe path is active.
            String first = "<unknown>";
            try { first = codec.name().getString(); } catch (Throwable ignored) {}
            LOGGER.info("FBA: probing MediaCodec encoders with ndk_codec=1 (first: {})", first);
        }

        // Force NDK path for all MediaCodec encoders.
        try {
            // A null-address AVDictionary wrapper is still valid for av_dict_set.
            if (options != null) {
                av_dict_set(options, "ndk_codec", "1", 0);
            }
        } catch (Throwable ignored) {
        }

        String codecName = "<unknown>";
        try { codecName = codec.name().getString(); } catch (Throwable ignored) {}

        int lastResult = -1;

        for (int fmt : MEDIACODEC_PIX_FMT_FALLBACKS) {
            if (ctx == null || ctx.isNull()) break;

            if (!codecSupportsFmt(codec, fmt)) continue;

            // Retries get a fresh context with Flashback's fields copied over.
            // Never free the old one here: Flashback owns it (double-free).
            // Superseded contexts leak one AVCodecContext per retry.
            if (lastResult < 0 && lastResult != -1) {
                // Previous attempt failed — get a clean context.
                AVCodecContext fresh = null;
                try {
                    fresh = avcodec_alloc_context3(codec);
                    if (fresh == null || fresh.isNull()) break;

                    // Copy fields Flashback sets before avcodec_open2.
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
                } catch (Throwable ignoredReset) {
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
            if (lastResult >= 0) {
                LOGGER.info("FBA: probe opened {} with pix_fmt={} (ndk_codec=1)",
                        codecName, fmt);
                return lastResult;
            }
        }

        // Logs when no pixel format opened.
        if (lastResult < 0) {
            LOGGER.warn("FBA: probe could not open {} with any of pix_fmt={} (last result {})",
                    codecName, java.util.Arrays.toString(MEDIACODEC_PIX_FMT_FALLBACKS), lastResult);
        }

        return lastResult;
    }

    /**
     * No-ops the removed {@code avcodec_close} call: {@code doesEncoderWork}
     * releases the context right after via {@code close()} anyway, so this is
     * equivalent with no double-free risk. require=0: inert if Flashback
     * migrates the call site.
     */
    @Redirect(
            method = "doesEncoderWork",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_close(" +
                            "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;)I",
                    remap = false
            ),
            remap = false,
            require = 0
    )
    private static int flashbackRedroided$closeCodecCompat(AVCodecContext ctx) {
        return 0;
    }
}
