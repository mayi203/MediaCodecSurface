package com.hiscene.mediacodecsurface;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.io.IOException;

public class CameraInstance {
    private Camera mCamera;

    public void open() {
        mCamera = Camera.open();
    }

    public void startPreview(SurfaceTexture surfaceTexture) {
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(1280, 720);
            mCamera.setPreviewTexture(surfaceTexture);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    public void stopPreview() {
        mCamera.stopPreview();
    }

    public void closeCamera() {
        mCamera.release();
    }
}
