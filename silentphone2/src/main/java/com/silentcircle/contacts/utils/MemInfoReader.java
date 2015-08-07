/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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

/*
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.silentcircle.contacts.utils;

import android.os.StrictMode;

import java.io.FileInputStream;

public class MemInfoReader {
    byte[] mBuffer = new byte[1024];

    private long mTotalSize;
    private long mFreeSize;
    private long mCachedSize;

    private boolean matchText(byte[] buffer, int index, String text) {
        int N = text.length();
        if ((index+N) >= buffer.length) {
            return false;
        }
        for (int i=0; i<N; i++) {
            if (buffer[index+i] != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private long extractMemValue(byte[] buffer, int index) {
        while (index < buffer.length && buffer[index] != '\n') {
            if (buffer[index] >= '0' && buffer[index] <= '9') {
                int start = index;
                index++;
                while (index < buffer.length && buffer[index] >= '0'
                    && buffer[index] <= '9') {
                    index++;
                }
                String str = new String(buffer, 0, start, index-start);
                return ((long)Integer.parseInt(str)) * 1024;
            }
            index++;
        }
        return 0;
    }

    public void readMemInfo() {
        // Permit disk reads here, as /proc/meminfo isn't really "on
        // disk" and should be fast.  TODO: make BlockGuard ignore
        // /proc/ and /sys/ files perhaps?
        StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskReads();
        try {
            mTotalSize = 0;
            mFreeSize = 0;
            mCachedSize = 0;
            FileInputStream is = new FileInputStream("/proc/meminfo");
            int len = is.read(mBuffer);
            is.close();
            final int BUFLEN = mBuffer.length;
            int count = 0;
            for (int i=0; i<len && count < 3; i++) {
                if (matchText(mBuffer, i, "MemTotal")) {
                    i += 8;
                    mTotalSize = extractMemValue(mBuffer, i);
                    count++;
                } else if (matchText(mBuffer, i, "MemFree")) {
                    i += 7;
                    mFreeSize = extractMemValue(mBuffer, i);
                    count++;
                } else if (matchText(mBuffer, i, "Cached")) {
                    i += 6;
                    mCachedSize = extractMemValue(mBuffer, i);
                    count++;
                }
                while (i < BUFLEN && mBuffer[i] != '\n') {
                    i++;
                }
            }
        } catch (java.io.FileNotFoundException e) {
        } catch (java.io.IOException e) {
        } finally {
            StrictMode.setThreadPolicy(savedPolicy);
        }
    }

    public long getTotalSize() {
        return mTotalSize;
    }

    public long getFreeSize() {
        return mFreeSize;
    }

    public long getCachedSize() {
        return mCachedSize;
    }
}
