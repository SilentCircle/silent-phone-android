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
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.silentcircle.contacts.ContactPhotoManager;
import com.silentcircle.silentphone2.R;

/**
 * Provides static functions to extract summary information for aggregate contacts
 */
public class ContactBadgeUtil {
    private static final String TAG = "ContactBadgeUtil";

    /**
     * Returns the social snippet attribution for the given stream item entry, including the date.
     */
    public static CharSequence getSocialDate(StreamItemEntry streamItem, Context context) {
        final CharSequence timestampDisplayValue;
        final Long statusTimestamp = streamItem.getTimestamp();
        if (statusTimestamp != null) {
            // Set the date/time field by mixing relative and absolute
            // times.
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

            timestampDisplayValue = DateUtils.getRelativeTimeSpanString(statusTimestamp.longValue(), System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, flags);
        }
        else {
            timestampDisplayValue = null;
        }

        String labelDisplayValue = null;

        final String statusLabelRes = streamItem.getLabelRes();
        final String statusResPackage = streamItem.getResPackage();

        // Package name used for resources.getIdentifier()
        String identiferPackage = statusResPackage;
        if (statusLabelRes != null) {
            Resources resources;
            if (TextUtils.isEmpty(statusResPackage)) {
                resources = context.getResources();
                // In this case, we're using the framework resources.
                identiferPackage = "android";
            }
            else {
                PackageManager pm = context.getPackageManager();
                try {
                    resources = pm.getResourcesForApplication(statusResPackage);
                }
                catch (NameNotFoundException e) {
                    Log.w(TAG, "Contact status update resource package not found: " + statusResPackage);
                    resources = null;
                }
            }

            if (resources != null) {
                final int resId = resources.getIdentifier(statusLabelRes, "string", identiferPackage);
                if (resId == 0) {
                    Log.w(TAG, "Contact status update resource not found: " + statusLabelRes + " in " + statusResPackage);
                }
                else {
                    labelDisplayValue = resources.getString(resId);
                }
            }
        }

        final CharSequence attribution;
        if (timestampDisplayValue != null && labelDisplayValue != null) {
            attribution = context.getString(R.string.contact_status_update_attribution_with_date, timestampDisplayValue,
                    labelDisplayValue);
        }
        else if (timestampDisplayValue == null && labelDisplayValue != null) {
            attribution = context.getString(R.string.contact_status_update_attribution, labelDisplayValue);
        }
        else if (timestampDisplayValue != null) {
            attribution = timestampDisplayValue;
        }
        else {
            attribution = null;
        }
        return attribution;
    }

    public static Bitmap loadDefaultAvatarPhoto(Context context, boolean hires, boolean darkTheme) {
        return BitmapFactory.decodeResource(context.getResources(),
                ContactPhotoManager.getDefaultAvatarResId(hires, darkTheme));
    }
}
