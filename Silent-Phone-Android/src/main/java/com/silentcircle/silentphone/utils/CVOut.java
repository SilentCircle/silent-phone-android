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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.silentcircle.silentphone.TiviPhoneService;

import java.nio.IntBuffer;

//video output screen
public class CVOut {

    public static int[] sxy = new int[16];
    public static int[] idata = new int[176 * 144];
    public static IntBuffer ib;
    public static Bitmap bm = null;
    int iCanDraw = 0;
    public int w = 176;
    public int h = 144;
   int iAllocedWH=0;
    int iPrevID = 0;
    Rect rectSrc = null;

    int iBitsPerPix = 16;
    int iPrevFilled = -1;
   
    public void clear(){
        if(idata != null)
            return;
        int sz = w * h;
        for(int i=0;i<sz;i++)
            idata[i]=0;
        if (bm != null)
           bm.eraseColor(android.graphics.Color.BLACK);
    }

    public void fillNewBuf() {
        if (iBitsPerPix == 16 && iPrevFilled != iPrevID && iCanDraw != 0) {
            iPrevFilled = iPrevID;
            ib.rewind();
            bm.copyPixelsFromBuffer(ib);
            ib.rewind();
        }
    }

    public void draw(Canvas c, int ox, int oy, Rect r, Paint pa) {
        if (iCanDraw == 0)
            return;
        if (iBitsPerPix == 16) {
            if (bm == null) return;
            if (r != null) {
                c.drawBitmap(bm, rectSrc, r, pa);
            }
            else {
                c.drawBitmap(bm, ox, oy, pa);
            }
        }
        else {
            if (idata != null)
                c.drawBitmap(idata, 0, w, ox, oy, w, h, false, pa);
        }
    }

    public boolean hasNewFrame() {
        sxy[0] = w;
        sxy[1] = h;
        sxy[3] = iBitsPerPix;
        int id = TiviPhoneService.getVFrame(iPrevID, idata, sxy);
        if (sxy[0] != w || sxy[1] != h) {
            iCanDraw = 0;
            w = sxy[0];
            h = sxy[1];
            rectSrc = null;
            rectSrc = new Rect(0, 0, w, h);
            ib = null;
            if(iAllocedWH!=w*h){
               idata = null;
               iAllocedWH=w*h;
               idata = new int[iAllocedWH];
            }
            bm = null;
            System.gc();
            ib = IntBuffer.wrap(idata);
            bm = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);// ARGB_8888);
           
            if (id == -2) {
                id = TiviPhoneService.getVFrame(iPrevID, idata, sxy);
            }
            iCanDraw = 1;
        }
        if (id < 0)
            return false;
        if (id >= 0 && iPrevID != id) {
            iPrevID = id;
            return true;
        }
        return false;

    }
    // getVFrame
}
