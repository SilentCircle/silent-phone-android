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
 * Copyright (C) 2012 The Android Open Source Project
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
import android.content.Intent;

/**
 * Utilities for managing CallerInfoCache.
 *
 * The cache lives in Phone package and is used as fallback storage when database lookup is slower
 * than expected. It remembers some information necessary for responding to incoming calls
 * (e.g. custom ringtone settings, send-to-voicemail).
 *
 * Even though the cache will be updated periodically, Contacts app can request the cache update
 * via broadcast Intent. This class provides that mechanism, and possibly other misc utilities
 * for the update mechanism.
 */
public final class CallerInfoCacheUtils {
    private static final String UPDATE_CALLER_INFO_CACHE =
            "com.android.phone.UPDATE_CALLER_INFO_CACHE";

    private CallerInfoCacheUtils() {
    }

    /**
     * Sends an Intent, notifying CallerInfo cache should be updated.
     *
     * Note: CallerInfo is *not* part of public API, and no guarantee is available around its
     * specific behavior. In practice this will only be used by Phone package, but may change
     * in the future.
     *
     * See also CallerInfoCache in Phone package for more information.
     */
    public static void sendUpdateCallerInfoCacheIntent(Context context) {
        context.sendBroadcast(new Intent(UPDATE_CALLER_INFO_CACHE));
    }
}
