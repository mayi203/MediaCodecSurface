package com.hiscene.mediacodecsurface;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.hiscene.mediacodecsurface.gles.EglBase;
import com.hiscene.mediacodecsurface.gles.GlRectDrawer;
import com.hiscene.mediacodecsurface.gles.GlUtil;

public class MySurfaceView extends SurfaceView implements SurfaceHolder.Callback, SurfaceTexture.OnFrameAvailableListener {
    private final String TAG = getClass().getSimpleName();
    CameraInstance cameraInstance;
    MediaCodecEncode mediaCodecEncode;

    private EglBase mDummyContext = null;
    private GlRectDrawer mDummyDrawer = null;
    private int mInputTextureId = 0;
    private SurfaceTexture mInputSurfaceTexture = null;
    private float[] mInputMatrix = new float[16];
    private int mTextureId = 0;
    private int mFrameBufferId = 0;
    private float[] mIdentityMatrix = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f};

    private EglBase previewEglBase = null;
    private GlRectDrawer previewDrawer = null;
    private float[] mPreviewMatrix = new float[16];

    private int mCameraWidth = 1280;
    private int mCameraHeight = 720;

    private int mViewWidth;
    private int mViewHeight;

    public MySurfaceView(Context context) {
        super(context);
        init();
    }

    public MySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        cameraInstance = new CameraInstance();
        mediaCodecEncode = new MediaCodecEncode();
        mediaCodecEncode.init(640, 480);

        mDummyContext = EglBase.create(null, EglBase.CONFIG_PIXEL_BUFFER);
        mDummyContext.createDummyPbufferSurface();
        mDummyContext.makeCurrent();
        mDummyDrawer = new GlRectDrawer();

        mInputTextureId = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        mInputSurfaceTexture = new SurfaceTexture(mInputTextureId);
        mInputSurfaceTexture.setOnFrameAvailableListener(MySurfaceView.this);

        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        cameraInstance.open();
        cameraInstance.startPreview(mInputSurfaceTexture);
        mediaCodecEncode.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        cameraInstance.stopPreview();
        cameraInstance.closeCamera();
        mediaCodecEncode.stop();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.i(TAG, "onFrameAvailable");
        mDummyContext.makeCurrent();
        surfaceTexture.updateTexImage();
        long timestamp = surfaceTexture.getTimestamp();
        surfaceTexture.getTransformMatrix(mInputMatrix);

        // 1. correct DisplayOrientation
        int width = mCameraWidth;
        int height = mCameraHeight;

        if (mTextureId == 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            mTextureId = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

            mFrameBufferId = GlUtil.generateFrameBuffer(mTextureId);
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBufferId);
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        mDummyDrawer.drawOes(mInputTextureId, mInputMatrix,
                width, height, 0, 0, width, height);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        // 2. draw to capture
        mediaCodecEncode.drawToCapture(mTextureId, width, height, mIdentityMatrix, timestamp, mDummyContext);

        // 3. draw to preview
        drawToPreview(mTextureId, width, height, mIdentityMatrix);
    }

    private void drawToPreview(int textureId, int width, int height, float[] texMatrix) {
        if (previewEglBase == null) {
            previewEglBase = EglBase.create(mDummyContext.getEglBaseContext(), EglBase.CONFIG_RGBA);
        }

        attachSurfaceView();

        if (!previewEglBase.hasSurface()) {
            return;
        }

        if (previewDrawer == null) {
            previewDrawer = new GlRectDrawer();
        }

        try {
            previewEglBase.makeCurrent();

            int scaleWidth = mViewWidth;
            int scaleHeight = mViewHeight;
            System.arraycopy(texMatrix, 0, mPreviewMatrix, 0, 16);
            if (mViewHeight * width <= mViewWidth * height) {
                scaleWidth = mViewHeight * width / height;
            } else {
                scaleHeight = mViewWidth * height / width;
            }

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            previewDrawer.drawRgb(textureId, mPreviewMatrix, (int) width, (int) height,
                    (mViewWidth - scaleWidth) / 2,
                    (mViewHeight - scaleHeight) / 2,
                    scaleWidth, scaleHeight);
            previewEglBase.swapBuffers();
            previewEglBase.detachCurrent();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void attachSurfaceView() {
        if (previewEglBase.hasSurface()) {
            return;
        }

        mViewWidth = getWidth();
        mViewHeight = getHeight();
        try {
            previewEglBase.createSurface(getHolder().getSurface());
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

}
