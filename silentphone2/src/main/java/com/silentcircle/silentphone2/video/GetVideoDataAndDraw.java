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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;

import java.nio.IntBuffer;

//video output screen
public class GetVideoDataAndDraw {

    @SuppressWarnings("unused")
    private static final String TAG = GetVideoDataAndDraw.class.getSimpleName();

    public static int[] sxy = new int[16];
    public static int[] videoData;
    public int mWidth;
    public int mHeight;

    private static IntBuffer ib;
    private  static Bitmap bm;
    private boolean mCanDraw;
    private int iPrevID;
    private Rect mRectSrc;

    // Current codec uses 16 bits per pixel. If this changes then we need to
    // adapt the fillNewBuffer (it works only for the 16 bits per pixel)
    // and the draw function. The current draw requires 16 bit per pixel.
    // It uses a RGB_565 bitmap
    private static final int iBitsPerPix = 16;
    private int iPrevFilled = -1;

    public void clear() {
        if (videoData != null)
            for (int i = 0; i < videoData.length; i++)
                videoData[i] = 0;
        if (bm != null)
            bm.eraseColor(android.graphics.Color.BLACK);
    }

    public void fillNewBuf() {
        if (iPrevFilled != iPrevID && mCanDraw) {
            iPrevFilled = iPrevID;
            ib.rewind();
            bm.copyPixelsFromBuffer(ib);
            ib.rewind();
        }
    }

    public void draw(Canvas c, int ox, int oy, Rect rectDestination, Paint pa) {
        if (!mCanDraw)
            return;

        if (bm == null)
            return;
        if (rectDestination != null) {
            c.drawBitmap(bm, mRectSrc, rectDestination, pa);
        }
        else {
            c.drawBitmap(bm, ox, oy, pa);
        }
    }

    public boolean hasNewFrame() {
        sxy[0] = mWidth;
        sxy[1] = mHeight;
        sxy[3] = iBitsPerPix;
        int id = TiviPhoneService.getVFrame(iPrevID, videoData, sxy);
        if (sxy[0] <= 0 || sxy[1] <= 0) {
            return false;
        }
        if (sxy[0] != mWidth || sxy[1] != mHeight) {
            mCanDraw = false;
            mWidth = sxy[0];
            mHeight = sxy[1];
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Receive video - w: " + mWidth + ", h: " + mHeight + ", id: " + id);
            mRectSrc = new Rect(0, 0, mWidth, mHeight);
            if (videoData == null || videoData.length != mWidth * mHeight) {
                videoData = new int[mWidth * mHeight];
            }
            ib = IntBuffer.wrap(videoData);
            bm = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.RGB_565);     // aRGB_8888

            if (id == -2) {
                id = TiviPhoneService.getVFrame(iPrevID, videoData, sxy);
            }
            mCanDraw = true;
        }
        if (id < 0)
            return false;
        if (id >= 0 && iPrevID != id) {
            iPrevID = id;
            return true;
        }
        return false;
    }
}
