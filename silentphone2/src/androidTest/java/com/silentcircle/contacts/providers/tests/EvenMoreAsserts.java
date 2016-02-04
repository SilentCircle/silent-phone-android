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

package com.silentcircle.contacts.providers.tests;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import junit.framework.Assert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Contains additional assertion methods not found in Junit or MoreAsserts.
 */
public final class EvenMoreAsserts {
    // Non instantiable.
    private EvenMoreAsserts() { }

    public static <T extends Exception> void assertThrows(Class<T> exception, Runnable r) {
        assertThrows(null, exception, r);
    }

    public static <T extends Exception> void assertThrows(String message, Class<T> exception,
            Runnable r) {
        try {
            r.run();
           // Cannot invoke Assert.fail() here because it will be caught by the try/catch below
           // and, if we are expecting an AssertionError or AssertionFailedError (depending on
           // the platform), we might incorrectly identify that as a success.
        } catch (Exception caught) {
            if (!exception.isInstance(caught)) {
                Assert.fail(appendUserMessage("Exception " + exception + " expected but " +
                        caught +" thrown.", message));
            }
            return;
        }
        Assert.fail(appendUserMessage(
                "Exception " + exception + " expected but no exception was thrown.",
                message));
    }

    private static String appendUserMessage(String errorMsg, String userMsg) {
        return userMsg == null ? errorMsg : errorMsg + userMsg;
    }

    public static void assertImageRawData(Context context, byte[] expected, InputStream actualStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        final byte[] buffer = new byte[4096];
        int count;
        while ((count = actualStream.read(buffer)) != -1) {
            baos.write(buffer, 0, count);
        }
        actualStream.close();

        assertImageRawData(context, expected, baos.toByteArray());
    }

    public static void assertImageRawData(Context context, byte[] expected, byte[] actual) {
        String failReason = null;
        if (expected.length != actual.length) {
            failReason = "Different data lengths:" +
                    " expected=" + expected.length + " actual=" + actual.length;
        } else if (!Arrays.equals(expected, actual)) {
            failReason = "Different data:";
        }
        if (failReason == null) {
            return;
        }
        String expectedFile = TestUtils.dumpToCacheDir(context, "expected", ".jpg", expected);
        String actualFile = TestUtils.dumpToCacheDir(context, "actual", ".jpg", actual);

        // Length or hashCode is different.  We'll fail, but put the dimensions in the message.
        Assert.fail(failReason + ", expected dimentions=" + getImageDimensions(expected) +
                " actual dimentions=" + getImageDimensions(actual) +
                " Data written to " + expectedFile + " and " + actualFile);
    }

    private static final String getImageDimensions(byte[] imageData) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageData, 0, imageData.length, o);

        return "[" + o.outWidth + " x " + o.outHeight + "]";
    }

    public static void assertUnique(Object... values) {
        Set<Object> set = new HashSet<Object>();
        for (Object o : values) {
            Assert.assertFalse("Duplicate found: " + o, set.contains(o));
            set.add(o);
        }
    }
}
