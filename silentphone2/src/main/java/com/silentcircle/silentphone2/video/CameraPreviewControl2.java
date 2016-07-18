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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This camera preview controller uses the new camera2 interface available
 * in Android Lollipop and above.
 *
 * Created by werner on 05.03.16.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraPreviewControl2 implements CameraPreviewController, TextureView.SurfaceTextureListener {

    private static final String TAG = "CameraPreviewControl2";

    // Proposed video-call resolution
    private static final int WIDTH = 352;
    private static final int HEIGHT = 288;

    private static boolean sSizesAvailable = true;

    private String mFrontCameraId;
    private String mBackCameraId;

    private boolean mUseFrontCamera = true;

    private int mNumberOfCameras;
    final private TouchListener mTouchListener;
    final private TextureView mTextureView;

    private int mHeight;

    int[] mRgbBuffer;
    final private Context mContext;
    private int mDegrees;
    private int mNativeRotation;
    private boolean mOpenInProgress;

    /**
     * Camera preview.
     */
    private CaptureRequest.Builder mPreviewBuilder;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private ImageReader mImageReader;

    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (mTextureView != null) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
            mOpenInProgress = false;
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mOpenInProgress = false;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            mOpenInProgress = false;
        }
    };

    /**
     * Instantiate the preview control for camera2 API.
     *
     * The constructor performs some checks if cameras are available and usable. For
     * example some cameras with camera2 API return a zero length sizes array. In this
     * case the app cannot really use this class and should fallback to the legacy
     * camera API. The app should call {@link CameraPreviewControl2#isCamera2Usable()}
     * to check if it can use this class or not.
     *
     * @param ctx the application context
     * @param textureView The texture view of the preview window
     * @param container The container (FrameLayout) that hosts the texture view
     * @param listener TouchListener which gets the touch events to move the preview window
     */
    public CameraPreviewControl2(Context ctx, TextureView textureView, FrameLayout container, TouchListener listener) {
        mTouchListener = listener;
        container.setOnTouchListener(onTouchListener);
        mTextureView = textureView;
        mContext = ctx;

        CameraManager manager = (CameraManager)ctx.getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIds;
        try {
            cameraIds = manager.getCameraIdList();
            mNumberOfCameras = cameraIds.length;
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null || map.getOutputSizes(ImageReader.class) == null ||
                        map.getOutputSizes(ImageReader.class).length == 0) {
                    sSizesAvailable = false;
                }
                Integer I = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (I != null) {
                    if (I == CameraCharacteristics.LENS_FACING_FRONT)
                        mFrontCameraId = id;
                    else if (I == CameraCharacteristics.LENS_FACING_BACK)
                        mBackCameraId = id;
                    else {
                        Log.d(TAG, "No appropriate Camera found.");
                    }
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            sSizesAvailable = false;
        }
        if (mFrontCameraId == null && mBackCameraId == null)
            sSizesAvailable = false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {}


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onSurfaceTextureAvailable");
        openCamera(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Surface texture changed: width: " + width + ", height: " + height);
        configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onSurfaceTextureDestroyed");
        stop();
        return true;
    }

    @Override
    public int getNumCameras() {
        return mNumberOfCameras;
    }

    @Override
    public boolean isCapturing() {
        return mCameraDevice != null;
    }

    @Override
    public void setFrontCamera(boolean useFront) {
        mUseFrontCamera = useFront;
    }

    @Override
    public synchronized boolean start(int orientation) {
        if (mOpenInProgress || mCameraDevice != null)
            return false;
        mOpenInProgress = true;
        mDegrees = orientation;
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(this);
        }
        return true;
    }

    @Override
    public synchronized void stop() {
        closeCamera();
        stopBackgroundThread();
    }

    /**
     * Check if this device supports the required camera2 functions for video call.
     *
     * @return {@code true} if all functions are available.
     */
    @Override
    public boolean isCamera2Usable() {
        return  sSizesAvailable && StreamConfigurationMap.isOutputSupportedFor(ImageReader.class);
    }

    ImageReader.OnImageAvailableListener mImageAvailable = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null)
                return;

            // RowStride of planes may differ from width set to image reader, depends
            // on device and camera hardware, for example on Nexus 6P the rowStride is
            // 384 and the image width is 352.
            final Image.Plane[] planes = image.getPlanes();
            final int total = planes[0].getRowStride() * mHeight;
            if (mRgbBuffer == null || mRgbBuffer.length < total)
                mRgbBuffer = new int[total];

            getRGBIntFromPlanes(planes);

            image.close();
        }
    };

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mImageReader.setOnImageAvailableListener(null, null);
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    // Called if the TextureView is ready and has the correct width and height. Get and set
    // the necessary configuration for the selected camera.
    private boolean openCamera(int width, int height) {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Time out waiting to lock camera opening.");
                return false;
            }
            String cameraId = mUseFrontCamera ? mFrontCameraId : mBackCameraId;

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            Integer co = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            final int cameraOrientation = co != null ? co : 0;

            // Compute the rotation that the native code needs to perform to compensate for
            // front/back facing camera and mirroring
            int rotation;
            if (mUseFrontCamera) {
                rotation = (cameraOrientation + mDegrees) % 360;
                rotation = (360 - rotation) % 360;  // compensate the mirror
            }
            else {
                rotation = (cameraOrientation - mDegrees + 360) % 360;
            }
            mNativeRotation = (mUseFrontCamera) ? 360 - rotation : rotation;
            mNativeRotation = (mNativeRotation == 360) ? 0 : mNativeRotation;

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                Log.e(TAG, "No StreamConfigurationMap available");
                return false;
            }
            // Compute and set the optimal size for the ImageReader, starting with proposed
            // resolution. Do this after we selected the camera. Front/back facing camera may
            // support different sizes. Some cameras return no sizes for ImageReader, thus
            // no video call possible. The app should check with isCamera2Usable() and switch
            // to legacy camera interface if necessary. This length check is just to make sure
            // we don't crash if the app does not behave.
            Size[] sizes = map.getOutputSizes(ImageReader.class);
            if (sizes.length == 0)
                return false;
            Size videoSize = chooseOptimalSize(sizes, WIDTH, HEIGHT);
            int videoWidth = videoSize.getWidth();
            mHeight = videoSize.getHeight();
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Video width: " + videoWidth + ", height: " + mHeight);

            mImageReader = ImageReader.newInstance(videoWidth, mHeight, ImageFormat.YUV_420_888, 3);
            mImageReader.setOnImageAvailableListener(mImageAvailable, mBackgroundHandler);

            // Get all available size for the textureSurface preview window
            sizes = map.getOutputSizes(SurfaceTexture.class);

            // Get the optimal size for the little preview window
            mPreviewSize = chooseOptimalSize(sizes, width, height);
            configureTransform(width, height);
            manager.openCamera(cameraId, mStateCallback, null);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access the camera.", e);
            return false;
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera opening.");
            return false;
        } catch (SecurityException e) {
            Log.e(TAG, "No access to camera device", e);
            return false;
        }
        return true;
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface readerSurface = mImageReader.getSurface();
            surfaces.add(readerSurface);
            mPreviewBuilder.addTarget(readerSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Log.w(TAG, "Create capture session failed");
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        }
        else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    final View.OnTouchListener onTouchListener = new View.OnTouchListener() {
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

    private void getRGBIntFromPlanes(Image.Plane[] planes) {
        ByteBuffer yPlane = planes[0].getBuffer();
        ByteBuffer uPlane = planes[1].getBuffer();       // The U (Cr) plane
        ByteBuffer vPlane = planes[2].getBuffer();       // the V (Cb) plane

        int bufferIndex = 0;
        final int total = yPlane.capacity();
        final int uvCapacity = uPlane.capacity();
        final int width = planes[0].getRowStride();

        int yPos = 0;
        for (int i = 0; i < mHeight; i++) {
            int uvPos = (i >> 1) * width;

            for (int j = 0; j < width; j++) {
                if (uvPos >= uvCapacity-1)
                    break;
                if (yPos >= total)
                    break;

                final int y1 = yPlane.get(yPos++) & 0xff;

                /*
                  The ordering of the u (Cb) and v (Cr) bytes inside the planes is a bit strange.
                  The _first_ byte of the u-plane and the _second_ byte of the v-plane build the
                  u/v pair and belong to the first two pixels (y-bytes), thus usual YUV 420 behavior.
                  What the Android devs did here: just copy the interleaved NV21 data to two planes
                  but keep the offset of the interleaving.
                 */
                final int u = (uPlane.get(uvPos) & 0xff) - 128;
                final int v = (vPlane.get(uvPos+1) & 0xff) - 128;
                if ((j & 1) == 1) {
                    uvPos += 2;
                }

                // This is the integer variant to convert YCbCr to RGB, NTSC values.
                // formulae found at
                // https://software.intel.com/en-us/android/articles/trusted-tools-in-the-new-android-world-optimization-techniques-from-intel-sse-intrinsics-to
                // and on StackOverflow etc.
                final int y1192 = 1192 * y1;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);

                r = (r < 0) ? 0 : ((r > 262143) ? 262143 : r);
                g = (g < 0) ? 0 : ((g > 262143) ? 262143 : g);
                b = (b < 0) ? 0 : ((b > 262143) ? 262143 : b);

                mRgbBuffer[bufferIndex++] = ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
        TiviPhoneService.nv21ToRGB32(null, mRgbBuffer, null, width, mHeight, mNativeRotation);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = Surface.ROTATION_0;
        if (mDegrees == 90)
            rotation = Surface.ROTATION_90;
        else if (mDegrees == 180)
            rotation = Surface.ROTATION_180;
        else if (mDegrees == 270)
            rotation = Surface.ROTATION_270;

        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
