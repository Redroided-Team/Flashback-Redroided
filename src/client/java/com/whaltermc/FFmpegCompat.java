package com.whaltermc;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;

import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_default;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_from_mask;
import static org.bytedeco.ffmpeg.global.avutil.av_channel_layout_uninit;
import static org.bytedeco.ffmpeg.global.swresample.swr_alloc_set_opts2;

public final class FFmpegCompat {

    private FFmpegCompat() {}

    public static AVCodecContext ctxSetChannels(AVCodecContext c, int channels) {
        av_channel_layout_default(c.ch_layout(), channels);
        return c;
    }

    public static int ctxGetChannels(AVCodecContext c) {
        return c.ch_layout().nb_channels();
    }

    public static AVCodecContext ctxSetLayout(AVCodecContext c, long mask) {
        if (mask != 0) {
            av_channel_layout_from_mask(c.ch_layout(), mask);
        }
        return c;
    }

    public static long ctxGetLayout(AVCodecContext c) {
        return c.ch_layout().u_mask();
    }

    public static AVFrame frameSetChannels(AVFrame f, int channels) {
        av_channel_layout_default(f.ch_layout(), channels);
        return f;
    }

    public static int frameGetChannels(AVFrame f) {
        return f.ch_layout().nb_channels();
    }

    public static AVFrame frameSetLayout(AVFrame f, long mask) {
        if (mask != 0) {
            av_channel_layout_from_mask(f.ch_layout(), mask);
        }
        return f;
    }

    public static long frameGetLayout(AVFrame f) {
        return f.ch_layout().u_mask();
    }

    public static long defaultLayout(int channels) {
        AVChannelLayout layout = new AVChannelLayout();
        try {
            av_channel_layout_default(layout, channels);
            return layout.u_mask();
        } finally {
            av_channel_layout_uninit(layout);
            layout.deallocate();
        }
    }

    public static int nbChannels(long mask) {
        return Long.bitCount(mask);
    }

    public static SwrContext swrAllocSetOpts(SwrContext s,
                                             long outLayout, int outFmt, int outRate,
                                             long inLayout, int inFmt, int inRate,
                                             int logOffset, Pointer logCtx) {
        AVChannelLayout out = new AVChannelLayout();
        AVChannelLayout in = new AVChannelLayout();
        try {
            av_channel_layout_from_mask(out, outLayout);
            av_channel_layout_from_mask(in, inLayout);

            SwrContext ctx = s != null ? s : new SwrContext();
            int ret = swr_alloc_set_opts2(ctx, out, outFmt, outRate, in, inFmt, inRate, logOffset, logCtx);
            return ret < 0 || ctx.isNull() ? null : ctx;
        } finally {
            av_channel_layout_uninit(out);
            av_channel_layout_uninit(in);
            out.deallocate();
            in.deallocate();
        }
    }
}
