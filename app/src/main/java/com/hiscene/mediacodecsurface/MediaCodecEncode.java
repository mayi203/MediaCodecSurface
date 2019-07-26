package com.hiscene.mediacodecsurface;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.hiscene.mediacodecsurface.gles.EglBase;
import com.hiscene.mediacodecsurface.gles.EglBase14;
import com.hiscene.mediacodecsurface.gles.GlRectDrawer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaCodecEncode {
    private final String TAG = getClass().getSimpleName();
    private MediaCodec mediaCodec;
    private static final String MIME_TYPE = "video/avc";
    private int color_format_ = 0;

    private EglBase captureEglBase = null;
    private GlRectDrawer captureDrawer = null;
    private float[] mCaptureMatrix = new float[16];
    private int mCaptureWidth = 640;
    private int mCaptureHeight = 480;
    private Surface mInputSurface;

    private FileOutputStream fileOutputStream;

    public void init(int width, int height) {
        mCaptureHeight = width;
        mCaptureHeight = height;
        MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
        color_format_ = selectColorFormat(codecInfo, MIME_TYPE);
        if (color_format_ == 0) {
            Log.e(TAG, "supported color format is NOT found");
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
        mInputSurface = mediaCodec.createInputSurface();
    }

    public void start() {
        mediaCodec.start();
        try {
            fileOutputStream = new FileOutputStream(new File("/sdcard/h264_test.h264"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        mediaCodec.stop();
        try {
            fileOutputStream.flush();
            fileOutputStream.close();
            fileOutputStream = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    byte[] outData;

    protected void output() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int encoderStatus = mediaCodec.dequeueOutputBuffer(info, 1000);
        while (encoderStatus >= 0) {
            ByteBuffer encodedData = mediaCodec.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                Log.e(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
            }

            if (outData == null || outData.length < info.size) {
                outData = new byte[info.size];
            }
            encodedData.position(info.offset);
            encodedData.get(outData, 0, info.size);
            try {
                fileOutputStream.write(outData, 0, info.size);
                fileOutputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
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

    public void drawToCapture(int textureId, int width, int height, float[] texMatrix, long timestamp_ns, EglBase mDummyContext) {
        if (captureEglBase == null) {
            captureEglBase = EglBase.create(mDummyContext.getEglBaseContext(), EglBase.CONFIG_RECORDABLE);
        }
        if (!captureEglBase.hasSurface()) {
            try {
                captureEglBase.createSurface(mInputSurface);
                captureEglBase.makeCurrent();
                captureDrawer = new GlRectDrawer();
            } catch (RuntimeException e) {
                e.printStackTrace();
                captureEglBase.releaseSurface();
                return;
            }
        }

        try {
            captureEglBase.makeCurrent();

            // support crop only
            int scaleWidth = mCaptureWidth;
            int scaleHeight = mCaptureHeight;
            System.arraycopy(texMatrix, 0, mCaptureMatrix, 0, 16);
            if (mCaptureHeight * width <= mCaptureWidth * height) {
                scaleHeight = mCaptureWidth * height / width;
            } else {
                scaleWidth = mCaptureHeight * width / height;
            }
            float fWidthScale = (float) mCaptureWidth / (float) scaleWidth;
            float fHeightScale = (float) mCaptureHeight / (float) scaleHeight;
            Matrix.scaleM(mCaptureMatrix, 0, fWidthScale, fHeightScale, 1.0f);
            Matrix.translateM(mCaptureMatrix, 0, (1.0f - fWidthScale) / 2.0f, (1.0f - fHeightScale) / 2.0f, 1.0f);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            captureDrawer.drawRgb(textureId, mCaptureMatrix, width, height,
                    0, 0,
                    mCaptureWidth, mCaptureHeight);
            ((EglBase14) captureEglBase).swapBuffers(timestamp_ns);

            output();
            captureEglBase.detachCurrent();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

}
