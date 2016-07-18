/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.silentphone2.video;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.IOException;
import java.util.List;

public class CameraPreviewControllerHw implements CameraPreviewController, PreviewCallback, TextureView.SurfaceTextureListener {

    private static final String TAG = CameraPreviewControllerHw.class.getSimpleName();

    Camera camera = null;

    private SurfaceTexture mPreviewSurface;

    private int mWidth;
    private int mHeight;

    private boolean mIsStarted;

    private boolean bUseFrontCamera = true;

    public int iFrameId = 0;
    private boolean mIsSurfaceOk;
    private int mDegrees;
    private int mCameraOrientation;
    private boolean mRgbProcessing;
    private boolean mPreviewStarted;

    private byte[] mBuffer1, mBuffer2, mBuffer3;

    final private TouchListener mTouchListener;

    public CameraPreviewControllerHw(TextureView textureView, FrameLayout container, TouchListener listener) {
        mTouchListener = listener;
        container.setOnTouchListener(onTouchListener);
        textureView.setSurfaceTextureListener(this);
        sizeChanged(176 * 2, 144 * 2);       // We try to use this or a slightly smaller camera resolution
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    @Override
    public void onPreviewFrame(byte[] data, Camera arg1) {
        if (data == null || arg1 == null || !mIsStarted) {
            if (data == null)
                Log.w(TAG, "Missed a preview frame");
            return;
        }
        mRgbProcessing = true;
        yuv2rgb32(data);
        mRgbProcessing = false;
        camera.addCallbackBuffer(data);
    }

    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mPreviewSurface = surface;
        mIsSurfaceOk = true;
        if (mPreviewSurface != null) {
            passSurf();
        }
        setupCamera();
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onSurfaceTextureAvailable, camera: " + camera);
        if (camera != null) {
            sizeChanged(176 * 2, 144 * 2);       // We try to use this or a slightly smaller camera resolution
            startCameraPreview();
        }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, Camera does all the work for us
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Surface texture changed: width: " + width + ", height: " + height);
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onSurfaceTextureDestroyed, camera: " + camera + ", started: " + mIsStarted);
        mIsSurfaceOk = false;
        if (camera != null) {
            stop();
        }
        return true;
    }

    @Override
    public void setFrontCamera(boolean yesNo) {
        setCameraFacing(yesNo);
    }

    @Override
    synchronized public void stop() {
        if (camera == null) {
            mIsStarted = false;
            return;
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "stop camera");

        mIsStarted = false;

        Utilities.Sleep(50);                // encode time, do not destroy buffer
        int waitCounter = 0;
        while (mRgbProcessing && waitCounter < 3) {
            Log.w(TAG, "Long wait for encoding: " + waitCounter);
            Utilities.Sleep(50);
            waitCounter++;
        }
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        mPreviewStarted = false;
        camera = null;
    }

    @Override
    public boolean isCapturing() {
        return mIsStarted;
    }

    @Override
    synchronized public boolean start(int orientation) {

        if (mIsStarted)
            return false;
        mIsStarted = true;
        mDegrees = orientation;
        setupCamera();
        if (camera != null && mIsSurfaceOk)
            startCameraPreview();
        return true;
    }

    @Override
    public int getNumCameras() {
        return Camera.getNumberOfCameras();
    }

    public void setCameraFacing(boolean bFront) {
        bUseFrontCamera = bFront;
    }

    @Override
    public boolean isCamera2Usable() {
        return false;
    }

    private int getCameraID(boolean bFront) {
        int numCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo ci = new Camera.CameraInfo();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, ci);
            if (ConfigurationUtilities.mTrace)
                Log.i(TAG, "getCameraInfo f= " + ci.facing + ", o= " + ci.orientation + ", front: " + bFront);
            mCameraOrientation = ci.orientation;
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && bFront) {
                if (ConfigurationUtilities.mTrace) Log.i(TAG, "getCameraInfo: returning front facing: " + i);
                return i;
            }
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK && !bFront) {
                if (ConfigurationUtilities.mTrace) Log.i(TAG, "getCameraInfo: returning back facing: " + i);
                return i;
            }
        }
        return 0;                                    //return default

    }

    synchronized private int setupCamera() {
        if (camera == null) {
            try {
                camera = Camera.open(getCameraID(bUseFrontCamera));
            } catch (Exception e) {
                Log.w(TAG, "Cannot open camera", e);
                return -1;
            }
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "setup camera: " + camera);

        if (camera == null)
            return -1;
        camera.setPreviewCallbackWithBuffer(this);

        camera.setErrorCallback(new Camera.ErrorCallback() {
            @Override
            public void onError(int error, Camera camera) {
                Log.w(TAG, "Camera onError : error= " + error);
            }
        });

        if (ConfigurationUtilities.mTrace) Log.i(TAG, "setup Camera: surface holder: " + mPreviewSurface);
        if (mPreviewSurface != null) {
            passSurf();
        }
        return 0;
    }

    private void passSurf() {
        if (camera == null || !mIsStarted || !mIsSurfaceOk)
            return;
        try {
            camera.setPreviewTexture(mPreviewSurface);
        } catch (IOException ignored) {}
    }

    void yuv2rgb32(byte[] b) {
        if (!mIsStarted)
            return;

        int rotation = (bUseFrontCamera) ? 360 - mDisplayRotation : mDisplayRotation;
        rotation = (rotation == 360) ? 0 : rotation;
        TiviPhoneService.nv21ToRGB32(b, mIntData, null, mWidth, mHeight, rotation);
        iFrameId++;
    }

    private int[] mIntData;
    private int mDisplayRotation;

    public void sizeChanged(int width, int height) {

        if (camera != null) {
            Camera.Parameters p = camera.getParameters();
            int d = width * height;
            int md = d;
            List<Camera.Size> sizes = p.getSupportedPreviewSizes();
            if (sizes == null)
                return;
            for (Camera.Size s : sizes) {
                // In this instance, simply use the first available
                // preview size; could be refined to find the closest
                // values to the surface size
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "CameraSize: width: " + s.width + ", height: " + s.height);
                int sd = s.width * s.height;
                int dif = sd - d;
                if (dif < 0)
                    dif = -dif;
                if (dif < md) {
                    md = dif;
                    width = s.width;
                    height = s.height;
                }
            }
        }

        if (mWidth != width || mHeight != height) {
            mWidth = width;
            mHeight = height;
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Camera mWidth: " + mWidth + ", mHeight: " + mHeight);

            mIntData = new int[(mWidth + 4) * (mHeight + 4)];
            mBuffer1 = new byte[mWidth * mHeight * 2];          // for NV21 this is big enough
            mBuffer2 = new byte[mWidth * mHeight * 2];
            mBuffer3 = new byte[mWidth * mHeight * 2];
        }
    }

    synchronized private void startCameraPreview() {
        if (mPreviewStarted)
            return;

        mPreviewStarted = true;
        camera.addCallbackBuffer(mBuffer1);
        camera.addCallbackBuffer(mBuffer2);
        camera.addCallbackBuffer(mBuffer3);

        if (bUseFrontCamera) {
            mDisplayRotation = (mCameraOrientation + mDegrees) % 360;
            mDisplayRotation = (360 - mDisplayRotation) % 360;  // compensate the mirror
        }
        else {
            mDisplayRotation = (mCameraOrientation - mDegrees + 360) % 360;
        }
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "device view orientation: " + mDegrees + ", display rotation: " + mDisplayRotation);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "front: " + bUseFrontCamera);
        camera.setDisplayOrientation(mDisplayRotation);

        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(mWidth, mHeight);
        parameters.setPreviewFormat(ImageFormat.NV21);

        try {
            camera.setParameters(parameters);
            camera.startPreview();
        } catch (Exception e) {
            Log.w(TAG, "Cannot start preview - exception thrown by runtime.", e);
        }
    }

    final View.OnTouchListener onTouchListener = new View.OnTouchListener()
    {
        private static final int INVALID_POINTER_ID = -1;
        private float lastX, lastY;
        private float absX = (float)0.0, absY = (float)0.0;
        private int mActivePointerId = INVALID_POINTER_ID;

        private void reset() {
            mActivePointerId = INVALID_POINTER_ID;
            absX = absY = 0;
            lastX = lastY = 0;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event)
        {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: {
                    final float x = event.getX();
                    final float y = event.getY();

                    // Where the user started the drag
                    lastX = x;
                    lastY = y;
                    mActivePointerId = event.getPointerId(0);

                    // Get initial position in absolute x/y pixels
                    if (absX == 0)
                        absX = v.getX();
                    if (absY == 0)
                        absY = v.getY();
                    mTouchListener.onTouchDown();
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    // Where the user's finger is during the drag
                    final int pointerIndex = event.findPointerIndex(mActivePointerId);
                    final float x = event.getX(pointerIndex);
                    final float y = event.getY(pointerIndex);

                    // Calculate x and y delta
                    final float dx = x - lastX;
                    final float dy = y - lastY;

                    // Compute new absolute position in pixels and set it to the view
                    absX += dx;
                    absY += dy;
                    v.setX(absX);
                    v.setY(absY);
                    v.invalidate();
                    break;
                }

                case MotionEvent.ACTION_UP: {
                    mTouchListener.onTouchUp(v);
                    reset();
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    reset();
                    break;
                }

                case MotionEvent.ACTION_POINTER_UP: {
                    // Extract the index of the pointer that left the touch sensor
                    final int pointerIndex = (action & MotionEvent.ACTION_POINTER_INDEX_MASK)
                            >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    final int pointerId = event.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                        lastX = event.getX(newPointerIndex);
                        lastY = event.getY(newPointerIndex);
                        mActivePointerId = event.getPointerId(newPointerIndex);
                    }
                    break;
                }
            }
            return true;
        }
    };
}
