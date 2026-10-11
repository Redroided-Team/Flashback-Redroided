// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_NV12;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_from_mask;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;
import static org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2;

// String target (not class literal) so other mods / different JavaCV versions
// never break at mixin-config time. All handlers are require=0 + WrapOperation
// so they chain with other mods instead of conflicting like Redirect.
@Mixin(targets = "org.bytedeco.javacv.FFmpegFrameRecorder", remap = false, priority = 1000)
public abstract class FFmpegRecorderMixin {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("flashback_android");

    private static final long AV_CH_LAYOUT_MONO     = 0x4L;
    private static final long AV_CH_LAYOUT_STEREO   = 0x3L;
    private static final long AV_CH_LAYOUT_SURROUND = 0x7L;
    private static final long AV_CH_LAYOUT_QUAD     = 0x33L;
    private static final long AV_CH_LAYOUT_5POINT0  = 0x607L;
    private static final long AV_CH_LAYOUT_5POINT1  = 0x60FL;
    private static final long AV_CH_LAYOUT_6POINT1  = 0x70FL;
    private static final long AV_CH_LAYOUT_7POINT1  = 0x63FL;

    private static long defaultMask(int nbChannels) {
        return switch (nbChannels) {
            case 1  -> AV_CH_LAYOUT_MONO;
            case 2  -> AV_CH_LAYOUT_STEREO;
            case 3  -> AV_CH_LAYOUT_SURROUND;
            case 4  -> AV_CH_LAYOUT_QUAD;
            case 5  -> AV_CH_LAYOUT_5POINT0;
            case 6  -> AV_CH_LAYOUT_5POINT1;
            case 7  -> AV_CH_LAYOUT_6POINT1;
            case 8  -> AV_CH_LAYOUT_7POINT1;
            default -> nbChannels > 0 ? ((1L << nbChannels) - 1) : 0L;
        };
    }

    private static void applyChLayout(AVCodecContext ctx, int channels) {
        if (ctx == null) return;
        AVChannelLayout layout = new AVChannelLayout();
        av_channel_layout_default(layout, channels);
        ctx.ch_layout(layout);
    }

    private static void applyChLayoutMask(AVCodecContext ctx, long mask) {
        if (ctx == null) return;
        AVChannelLayout layout = new AVChannelLayout();
        if (mask != 0) av_channel_layout_from_mask(layout, mask);
        else av_channel_layout_default(layout, 2);
        ctx.ch_layout(layout);
    }

    private static void applyFrameChLayout(AVFrame frame, int channels) {
        if (frame == null) return;
        AVChannelLayout layout = new AVChannelLayout();
        av_channel_layout_default(layout, channels);
        frame.ch_layout(layout);
    }

    private static void applyFrameChLayoutMask(AVFrame frame, long mask) {
        if (frame == null) return;
        AVChannelLayout layout = new AVChannelLayout();
        if (mask != 0) av_channel_layout_from_mask(layout, mask);
        else av_channel_layout_default(layout, 2);
        frame.ch_layout(layout);
    }

    // NOTE: method="*" is intentional. JavaCV 1.5.10 calls the deprecated
    // channels()/channel_layout() APIs from startUnsafe AND from the audio
    // encode path (recordSamples/writeSamples/writeFrame). Scoping handlers
    // to start* only left recordSamples unpatched, crashing audio export with
    // UnsatisfiedLinkError on FFmpeg 7+ where those natives no longer exist.
    // "*" covers all current and future call sites; each handler is require=0
    // so it is skipped if the target invoke disappears.
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/global/avutil;av_get_default_channel_layout(I)J", remap = false), remap = false, require = 0)
    private long flashbackRedroided$defaultChannelLayout(int nbChannels, Operation<Long> original) {
        return defaultMask(nbChannels);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;channels(I)Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;", remap = false), remap = false, require = 0)
    private AVCodecContext flashbackRedroided$ctxChannelsSet(AVCodecContext ctx, int channels, Operation<AVCodecContext> original) {
        applyChLayout(ctx, channels);
        return ctx;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;channel_layout(J)Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;", remap = false), remap = false, require = 0)
    private AVCodecContext flashbackRedroided$ctxChannelLayoutSet(AVCodecContext ctx, long mask, Operation<AVCodecContext> original) {
        applyChLayoutMask(ctx, mask);
        return ctx;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;channels()I", remap = false), remap = false, require = 0)
    private int flashbackRedroided$ctxChannelsGet(AVCodecContext ctx, Operation<Integer> original) {
        if (ctx == null || ctx.isNull()) return 0;
        AVChannelLayout layout = ctx.ch_layout();
        if (layout == null || layout.isNull()) return 0;
        return layout.nb_channels();
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;channel_layout()J", remap = false), remap = false, require = 0)
    private long flashbackRedroided$ctxChannelLayoutGet(AVCodecContext ctx, Operation<Long> original) {
        int nb = 0;
        if (ctx != null && !ctx.isNull()) {
            AVChannelLayout layout = ctx.ch_layout();
            if (layout != null && !layout.isNull()) nb = layout.nb_channels();
        }
        return defaultMask(nb);
    }

    // AVCodecParameters uses the same deprecated layout fields (FFmpeg 6).
    // startUnsafe reads inpAudioStream.codecpar().channels() when remuxing
    // audio; without this the same UnsatisfiedLinkError hits on FFmpeg 7+.
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;channels()I", remap = false), remap = false, require = 0)
    private int flashbackRedroided$paramsChannelsGet(AVCodecParameters params, Operation<Integer> original) {
        if (params == null || params.isNull()) return 0;
        AVChannelLayout layout = params.ch_layout();
        if (layout == null || layout.isNull()) return 0;
        return layout.nb_channels();
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;channel_layout()J", remap = false), remap = false, require = 0)
    private long flashbackRedroided$paramsChannelLayoutGet(AVCodecParameters params, Operation<Long> original) {
        int nb = 0;
        if (params != null && !params.isNull()) {
            AVChannelLayout layout = params.ch_layout();
            if (layout != null && !layout.isNull()) nb = layout.nb_channels();
        }
        return defaultMask(nb);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;channels(I)Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;", remap = false), remap = false, require = 0)
    private AVCodecParameters flashbackRedroided$paramsChannelsSet(AVCodecParameters params, int channels, Operation<AVCodecParameters> original) {
        if (params != null && !params.isNull()) {
            AVChannelLayout layout = new AVChannelLayout();
            av_channel_layout_default(layout, channels);
            params.ch_layout(layout);
        }
        return params;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;channel_layout(J)Lorg/bytedeco/ffmpeg/avcodec/AVCodecParameters;", remap = false), remap = false, require = 0)
    private AVCodecParameters flashbackRedroided$paramsChannelLayoutSet(AVCodecParameters params, long mask, Operation<AVCodecParameters> original) {
        if (params != null && !params.isNull()) {
            AVChannelLayout layout = new AVChannelLayout();
            if (mask != 0) av_channel_layout_from_mask(layout, mask);
            else av_channel_layout_default(layout, 2);
            params.ch_layout(layout);
        }
        return params;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;channels(I)Lorg/bytedeco/ffmpeg/avutil/AVFrame;", remap = false), remap = false, require = 0)
    private AVFrame flashbackRedroided$frameChannelsSet(AVFrame frame, int channels, Operation<AVFrame> original) {
        applyFrameChLayout(frame, channels);
        return frame;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;channel_layout(J)Lorg/bytedeco/ffmpeg/avutil/AVFrame;", remap = false), remap = false, require = 0)
    private AVFrame flashbackRedroided$frameChannelLayoutSet(AVFrame frame, long mask, Operation<AVFrame> original) {
        applyFrameChLayoutMask(frame, mask);
        return frame;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;channels()I", remap = false), remap = false, require = 0)
    private int flashbackRedroided$frameChannelsGet(AVFrame frame, Operation<Integer> original) {
        if (frame == null || frame.isNull()) return 0;
        AVChannelLayout layout = frame.ch_layout();
        if (layout == null || layout.isNull()) return 0;
        return layout.nb_channels();
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;channel_layout()J", remap = false), remap = false, require = 0)
    private long flashbackRedroided$frameChannelLayoutGet(AVFrame frame, Operation<Long> original) {
        int nb = 0;
        if (frame != null && !frame.isNull()) {
            AVChannelLayout layout = frame.ch_layout();
            if (layout != null && !layout.isNull()) nb = layout.nb_channels();
        }
        return defaultMask(nb);
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/global/swresample;swr_alloc_set_opts(Lorg/bytedeco/ffmpeg/swresample/SwrContext;JIIJIIILorg/bytedeco/javacpp/Pointer;)Lorg/bytedeco/ffmpeg/swresample/SwrContext;", remap = false), remap = false, require = 0)
    private SwrContext flashbackRedroided$swrAllocSetOpts(
            SwrContext s,
            long outChLayout, int outSampleFmt, int outSampleRate,
            long inChLayout,  int inSampleFmt,  int inSampleRate,
            int logOffset, Pointer logCtx,
            Operation<SwrContext> original) {

        if (s == null || s.isNull()) s = new SwrContext();

        AVChannelLayout outLayout = new AVChannelLayout();
        AVChannelLayout inLayout  = new AVChannelLayout();

        if (outChLayout != 0) av_channel_layout_from_mask(outLayout, outChLayout);
        else av_channel_layout_default(outLayout, 2);

        if (inChLayout != 0) av_channel_layout_from_mask(inLayout, inChLayout);
        else av_channel_layout_default(inLayout, 2);

        int ret = swr_alloc_set_opts2(s, outLayout, outSampleFmt, outSampleRate, inLayout, inSampleFmt, inSampleRate, logOffset, logCtx);
        if (ret < 0) return null;
        return s;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;key_frame()I", remap = false), remap = false, require = 0)
    private int flashbackRedroided$getKeyFrame(AVFrame frame, Operation<Integer> original) {
        if (frame == null) return 0;
        return (frame.flags() & AVFrame.AV_FRAME_FLAG_KEY) != 0 ? 1 : 0;
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/avutil/AVFrame;key_frame(I)Lorg/bytedeco/ffmpeg/avutil/AVFrame;", remap = false), remap = false, require = 0)
    private AVFrame flashbackRedroided$setKeyFrame(AVFrame frame, int key, Operation<AVFrame> original) {
        if (frame == null) return null;
        int flags = frame.flags();
        int keyFlag = AVFrame.AV_FRAME_FLAG_KEY;
        if (key != 0) frame.flags(flags | keyFlag);
        else frame.flags(flags & (keyFlag ^ -1));
        return frame;
    }

    /**
     * FFmpeg 7 removed avcodec_close; contexts are released with
     * avcodec_free_context / Pointer.close() instead. Any remaining
     * avcodec_close call (e.g. recorder stop paths) would die with
     * UnsatisfiedLinkError, so replace it with a no-op success. A no-op
     * (rather than free_context) can never double-free: callers always
     * release the context themselves afterwards.
     */
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_close(Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;)I", remap = false), remap = false, require = 0)
    private static int flashbackRedroided$closeCodecCompat(AVCodecContext ctx, Operation<Integer> original) {
        return 0;
    }

    /**
     * Force NV12 + ndk_codec for Android MediaCodec encoders.
     * WrapOperation (not Inject+@Local) so Flashback/JavaCV renames never break us.
     */
    @WrapOperation(
        method = "startUnsafe",
        at = @At(
            value = "INVOKE",
            target = "Lorg/bytedeco/ffmpeg/global/avcodec;avcodec_open2(Lorg/bytedeco/ffmpeg/avcodec/AVCodecContext;Lorg/bytedeco/ffmpeg/avcodec/AVCodec;Lorg/bytedeco/ffmpeg/avutil/AVDictionary;)I",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private int flashbackRedroided$alwaysNdkMediaCodec(
            AVCodecContext ctx,
            AVCodec codec,
            AVDictionary options,
            Operation<Integer> original
    ) {
        try {
            if (codec != null && codec.name() != null && !codec.isNull()
                    && codec.name().getString().endsWith("_mediacodec")
                    && ctx != null && !ctx.isNull()) {
                // Do NOT gate this on !options.isNull(). JavaCV's startUnsafe
                // builds its options with `new AVDictionary(null)`, whose address
                // is 0, so the guard silently skipped av_dict_set and ndk_codec
                // was never applied. av_dict_set takes AVDictionary**; JavaCPP
                // hands it &address, so a null-address wrapper is valid and
                // av_dict_set allocates and writes the pointer back.
                if (options != null) {
                    av_dict_set(options, "ndk_codec", "1", 0);
                }
                ctx.pix_fmt(AV_PIX_FMT_NV12);
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to force NV12 for MediaCodec, continuing with default", t);
        }
        return original.call(ctx, codec, options);
    }
}
