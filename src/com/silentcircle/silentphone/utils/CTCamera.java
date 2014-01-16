/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.utils;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.activities.TMActivity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ShortBuffer;

public class CTCamera implements PreviewCallback, SurfaceHolder.Callback {

    private static String LOG_TAG = "CTCamera";

    @Override
    public void onPreviewFrame(byte[] data, Camera arg1) {
        if (arg1 == null || iStarted == 0)
            return;
        CTCamera.this.yuv2rgb32(data);
    }

    private SurfaceView preview = null;

    Camera camera = null;
    
    public int w = 0;
    public int h = 0;

    int iStarted = 0;
   
    public boolean bUseFrontCamera=true;

    public int iFrameId = 0;

    public CTCamera() {
        // sizeChanged(176,144);
        sizeChanged(176 * 2, 144 * 2);
        // sizeChanged(480,320);
        // sizeChanged(240,320);
        // sizeChanged(320,240);
        // sizeChanged(240,180);

    }

    int iSurfOk = 0;
    SurfaceHolder mHolder = null;
    SurfaceHolder displayHolder = null;

    public void setView(SurfaceView v) {

        if (mHolder == null || preview != v || iStarted == 0 || camera == null) {
            // TODO removeCallback
            displayHolder = null;
            mHolder = v.getHolder();
            mHolder.addCallback(this);
        }
        preview = v;
    }

    public void surfaceCreated(SurfaceHolder holder) {

        iSurfOk = 2;
        displayHolder = holder;
        if (iStarted != 0) {
            if (camera == null)
                setupCamera();
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {

        if (displayHolder == holder)
            displayHolder = null;
        iSurfOk = 0;
        if (camera != null && iStarted==0) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        // s_h=holder;
        displayHolder = holder;
        iSurfOk = 1;
        if (iStarted != 0) {
            setupCamera();
        }
    }

    synchronized public void stop() {
        // Log.d(TAG, "stop");
        if (camera == null || iStarted == 0) {
            iStarted = 0;
            return;
        }
        iStarted = 0;

        Utilities.Sleep(50);//max encode time do not destroy buffer
        camera.setPreviewCallback(null);
        camera.stopPreview();
        camera.release();
        camera = null;

    }

    public boolean isCapturing() {
        return iStarted == 1;
    }

    synchronized public int start() {

        if (iStarted != 0)
            return -1;
        iStarted = 1;
        setupCamera();
        return 0;
    }
   
   
    public static int getNumCameras() {
        return Camera.getNumberOfCameras();
    }

    public void setCameraFacing(boolean bFront) {
      //TODO if bUseFrontCamera!=bFront && isCapturing restart
       bUseFrontCamera = bFront;
    }
   
    
    private int getCameraID(boolean bFront){
       int numCameras = Camera.getNumberOfCameras();
       Camera.CameraInfo ci = new Camera.CameraInfo();
       for(int i = 0; i < numCameras; i++){
          Camera.getCameraInfo(i, ci);
          if(TMActivity.SP_DEBUG) Log.i(LOG_TAG, "getCameraInfo f= " + ci.facing + ", o= " +ci.orientation + ", front: " + bFront);
          if(ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT && bFront) {
              if(TMActivity.SP_DEBUG) Log.i(LOG_TAG, "getCameraInfo: returning front facing: " + i);
              return i;
          }
          if(ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK && !bFront) {
              if(TMActivity.SP_DEBUG) Log.i(LOG_TAG, "getCameraInfo: returning back facing: " + i);
              return i;
          }
       }
       return 0;                                    //return default
       
    }

    synchronized private int setupCamera() {
       //bUseFrontCamera=false;
        // startSurf();
        if (camera == null)
            camera = Camera.open(getCameraID(bUseFrontCamera));
       
        if(camera == null) return -1;

        if(TMActivity.SP_DEBUG)Log.d(LOG_TAG, "startC " + camera);

        camera.setPreviewCallback(this);

        camera.setErrorCallback(new Camera.ErrorCallback() {
            @Override
            public void onError(int error, Camera camera) {
                Log.d(LOG_TAG, "Camera onError : error= " + error);
            }
        });

        if(TMActivity.SP_DEBUG) Log.i(LOG_TAG, "setupCamera: surface holder: " + displayHolder);
        if (displayHolder != null) {
            passSurf(displayHolder);
        }
        sizeChanged(w, h);

        return 0;
    }

    private void passSurf(SurfaceHolder s) {
        if (camera == null || iStarted == 0 || iSurfOk == 0)
            return;
        try {
            camera.setPreviewDisplay(s);
        }
        catch (IOException exception) {
            // camera.release();
            // camera = null;
            // TODO: add more exception handling logic here
        }
    }

    void yuv2rgb32(byte[] b) {
        if (iStarted == 0)
            return; // Log.d(TAG, "onPreviewFrame - wrote bytes: "+ b.length+"w"+w+"h"+h);
        TiviPhoneService.nv21ToRGB32(b, idata, null, w, h, bUseFrontCamera ? 270 : 90);// null - don't convert preview pic
        iFrameId++;
    }

    int[] idata = null;
    short[] sdata = null;
    ShortBuffer ib;
    public Bitmap bmp = null;
    int iFrameIdFilled = -1;

    protected void setDisplayOrientation(Camera camera, int angle) {
        Method downPolymorphic;
        try {
            downPolymorphic = camera.getClass().getMethod("setDisplayOrientation", new Class[] { int.class });
            if (downPolymorphic != null)
                downPolymorphic.invoke(camera, new Object[] { angle });
        }
        catch (Exception ignored) {}
    }

    public void sizeChanged(int nw, int nh) {

        if (camera != null) {
            Camera.Parameters p = camera.getParameters();
            int d = nw * nh;
            int md = d;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                // In this instance, simply use the first available
                // preview size; could be refined to find the closest
                // values to the surface size
                if(TMActivity.SP_DEBUG) Log.d(LOG_TAG, "CameraSize w: " + s.width + ", h: " + s.height);
                int sd = s.width * s.height;
                int dif = sd - d;
                if (dif < 0)
                    dif = -dif;
                if (dif < md) {
                    md = dif;
                    // p.setPreviewSize(s.width, s.height);
                    nw = s.width;
                    nh = s.height;
                }
            }
        }

        if(TMActivity.SP_DEBUG) Log.d(LOG_TAG, "Camera w: " + w + ", h: " + h);

        if (w != nw || h != nh) {
            iFrameIdFilled = -1;
            w = nw;
            h = nh;

            idata = null;
            idata = new int[(w + 4) * (h + 4)];
           
            sdata = null;
            sdata = new short[(w + 4) * (h + 4)];
            ib = ShortBuffer.wrap(sdata);
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);// ARGB_8888);
        }

        if (camera == null)
            return;

        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(nw, nh);
        setDisplayOrientation(camera, 90);

        parameters.setPreviewFormat(17);                    // nv21

        camera.setParameters(parameters);
        try {
            camera.startPreview();
        } catch (Exception ignored) {
            Log.w(LOG_TAG, "Cannot start preview - exception thrown by runtime.");
        }
    }
}
