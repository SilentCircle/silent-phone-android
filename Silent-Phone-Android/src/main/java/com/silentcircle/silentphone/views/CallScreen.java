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

package com.silentcircle.silentphone.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.silentcircle.silentphone.activities.TMActivity;
import com.silentcircle.silentphone.utils.CTCamera;
import com.silentcircle.silentphone.utils.CVOut;


public class CallScreen extends SurfaceView implements SurfaceHolder.Callback {

    private static String LOG_TAG = "CallScreen";

    static CVOut vout = new CVOut();
    Surface surf = null;
    SurfaceHolder mHolder;
    int iSurfOk = 0;
    int sw = 0, sh = 0;
    public static boolean bDbg = false;
    private boolean firstPack = true;
 
    private static Paint paL = new Paint(Paint.ANTI_ALIAS_FLAG);

    private SurfaceView previewVideo;
    private static CTCamera camera = null;

    static void checkCamera(SurfaceView v){
        if(camera == null)
            camera = new CTCamera();
        camera.setView(v);
    } 

    public static boolean cameraCapturing() {
        return camera != null && camera.isCapturing();
    }

    public static void stopCamera() {
        if (camera != null && camera.isCapturing())
            camera.stop();
    }

    public void startStopCamera() {
        checkCamera(previewVideo);
        if (camera.isCapturing())
            camera.stop();
        else
            camera.start();
    }

    public void setFrontCamera(boolean yesNo) {
        camera.setCameraFacing(yesNo);
    }

    public int getNumCameras() {
        return CTCamera.getNumCameras();
    }

    int getCameraH() {
        if (camera != null && camera.isCapturing())
            return camera.bmp.getHeight();
        return 0;
    }

    Rect rdst = new Rect(0, 0, 1, 1);
    Rect rsrc = new Rect(0, 0, 1, 1);

    int drawCameraVideo(Canvas c, boolean hor, boolean bResizeOut) {
        if (cameraCapturing() && iSurfOk != 0) {
        }
        return 0;
    }

    public CallScreen(Context context, SurfaceView preview) {
        super(context);
       
        previewVideo = preview;
       
        checkCamera(preview);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        paL.setTextSize(15);
        paL.setTextAlign(Paint.Align.LEFT);
        paL.setARGB(255, 0, 0, 0);
        paL.setARGB(255, 200, 200, 200);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        surf = mHolder.getSurface();
        iSurfOk = 2;
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        iSurfOk = 0;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        iSurfOk = 1;
        sw = w;
        sh = h;
        rect.set(0, 0, w, h);
        surf = mHolder.getSurface();
        if (TMActivity.SP_DEBUG) Log.d(LOG_TAG, "surfaceChanged (" + w + " " + h + " f=" + format + ")");
        iPrevCameraH = -2;
       // MTView.pr.passSurf(holder);
    }
   
    public void clear(){
       vout.clear();
       firstPack=true;
    }

    int iPrevID;
    int iPrevFrDraw = 0;
    boolean bCallIsActive = false;

    public void check(boolean bActive) {
        bCallIsActive = bActive;
        if (!bCallIsActive)
            return;
        if (surf == null || iSurfOk == 0)
            return;

        vout.hasNewFrame();
        boolean dr = false;
       
       if(firstPack){//clearing prev video img
          try {
             Canvas c = mHolder.lockCanvas(null);
             c.drawColor (Color.BLACK);
             mHolder.unlockCanvasAndPost(c);
          }catch (IllegalArgumentException o) {
             
          }
          iPrevID = CVOut.sxy[2];
          firstPack =false;
          return;
       }


        if (CVOut.sxy[2] != iPrevID) {
            vout.fillNewBuf();
            iPrevID = CVOut.sxy[2];
            dr = true;
        }
        if (dr) {
            try {
                Canvas c = mHolder.lockCanvas(null);
                long startT=0;
                if(bDbg)
                    startT= System.currentTimeMillis(); // only to measure time to draw
               
                drawIt(c, true);
                if(bDbg){
                    long sp = System.currentTimeMillis() - startT;
                    c.drawText("sp=" + sp, sw / 4, sh / 2, paL);
                }
                mHolder.unlockCanvasAndPost(c);
            }
            // catch(Surface.OutOfResourcesException o){}
            catch (IllegalArgumentException o) {
                Log.w("tivi", "err " + o.toString());
            }

        }
    }

    @Override
    protected void onDraw(Canvas c) {
    }

    /*
     * IntBuffer
     */
    int iClearScr = 0;
    int iPrevW = 0, iPrevH = 0;
    int iPrevCameraH = -1;
    Rect rect = new Rect(0, 0, 240, 160);
    Rect rectFS = new Rect(0, 0, 720, 480);

    protected void drawIt(Canvas canvas, boolean bnew) {
        int w = sw; // getWidth();
        int h = sh; // getHeight();
       
        if (iSurfOk <= 0 || h<=0 || w<=0 || vout.w<=0 || vout.h<=0 )
            return;
       
        if (iPrevW != vout.w || iPrevH != vout.h) {
            iPrevW = vout.w;
            iPrevH = vout.h;
            iClearScr = 10; //?? why does not with =1;
        }
       
        if (iClearScr > 0){
           iClearScr--;
           canvas.drawColor(Color.BLACK);
        }

        if (bCallIsActive) {
            boolean hor = sw > sh;

            boolean bResizeOut = false;
            int mult = 2;

            if (CVOut.idata != null) {
                boolean bFullScreen=true;
                int ox=0,oy=0;
                int wshow = vout.w * mult;
                int hshow = vout.h * mult;
                if(bFullScreen){
                   
                   float fw=(float)w/(float)vout.w;
                   float fh=(float)h/(float)vout.h;
                   
                   float fmult= fw<fh ? fw : fh;
                   
                   wshow = (int)((float)vout.w * fmult);
                   hshow = (int)((float)vout.h * fmult);

                }
               
                  
                ox = (w - wshow) >> 1;
                if (ox < 0) {
                    ox = 0;
                    bResizeOut = true;
                }
               
                oy = (h - hshow) >> 1;
                if (oy < 0) {
                    oy = 0;
                    bResizeOut = true;
                }
                rectFS.set(ox, oy, ox + wshow, oy + hshow);
               
                vout.draw(canvas, ox, oy, rectFS, paL);         // slow //fullscr
            }
        }
    }
}
