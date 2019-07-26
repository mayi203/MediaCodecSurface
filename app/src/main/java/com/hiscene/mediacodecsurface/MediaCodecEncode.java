package com.hiscene.mediacodecsurface;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaCodecEncode {
    private final String TAG = getClass().getSimpleName();
    private MediaCodec mediaCodec;
    private static final String MIME_TYPE = "video/avc";
    private int color_format_ = 0;

    public Surface init(int width, int height) {
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        color_format_ = selectColorFormat(codecInfo, MIME_TYPE);
        if (color_format_ == 0) {
            Log.e(TAG, "supported color format is NOT found");
            return null;
        } else {
            Log.i(TAG, "supported color format is found : " + color_format_);
        }
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        int bitrate = 2000000;
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, color_format_);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        Log.d(TAG, "format: " + format);
        try {
            mediaCodec = MediaCodec.createByCodecName(codecInfo.getName());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        return mediaCodec.createInputSurface();
    }

    public void start() {
        mediaCodec.start();
    }

    public void stop() {
        mediaCodec.stop();
    }

    public void output() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int encoderStatus = mediaCodec.dequeueOutputBuffer(info, 1000);
        while (encoderStatus >= 0) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
            }

            Log.d(TAG, "encoded output " + info.size);

            mediaCodec.releaseOutputBuffer(encoderStatus, false);
            encoderStatus = mediaCodec.dequeueOutputBuffer(info, 0);
        }
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            Log.d(TAG, "colorformat: " + colorFormat);
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;   // not reached
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle
//            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
//            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            //case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface:
                return true;
            default:
                return false;
        }
    }
}
