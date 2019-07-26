package com.hiscene.mediacodecsurface;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

public class MyView extends TextureView {
    private VideoCaptureFromCamera videoCaptureFromCamera;
    private MediaCodecEncode mediaCodecEncode;
    private Surface surface;

    public MyView(Context context) {
        super(context);
        init();
    }

    public MyView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        videoCaptureFromCamera = new VideoCaptureFromCamera();
        videoCaptureFromCamera.allocateAndStart();
        videoCaptureFromCamera.setView(this);
        videoCaptureFromCamera.setResolution(1280, 720);
        mediaCodecEncode = new MediaCodecEncode();
        surface = mediaCodecEncode.init(640, 480);
        videoCaptureFromCamera.setSurface(surface);
        videoCaptureFromCamera.setEncode(mediaCodecEncode);
        mediaCodecEncode.start();

        videoCaptureFromCamera.startCapture();
        videoCaptureFromCamera.startPreview();
    }

}
