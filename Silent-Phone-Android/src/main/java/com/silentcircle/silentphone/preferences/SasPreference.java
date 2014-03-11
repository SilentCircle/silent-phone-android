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

package com.silentcircle.silentphone.preferences;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;

public final class SasPreference extends ListPreference {

    private static String B32 = "B32";      // SAS as 4 characters
    private static String B256 = "B256";    // SAS as two words

    private Context mContext;

    public SasPreference(Context context) {
        super(context);
        prepare();
    }

    public SasPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    String sasType;
    private void prepare() {
        mContext = getContext();
        setEntries(new String[]{
                mContext.getString(R.string.sas_char_mode),
                mContext.getString(R.string.sas_word_mode),
        });
        setEntryValues(new String[]{ B32, B256 });

        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iDisable256SAS");
        boolean sas256Enabled = "0".equals(result); // negative logic: if 0 then B256 is not disabled -> enabled :-)
        sasType = (sas256Enabled) ? B256 : B32;
        setValue(sasType);
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        if (B32.equals(sasType))
            return mContext.getString(R.string.sas_char_mode);
        else
            return mContext.getString(R.string.sas_word_mode);
    }

    @Override
    protected boolean persistString(String value) {

        if (!value.equals(sasType)) {
            if (B32.equals(value))
                TiviPhoneService.doCmd("set cfg.iDisable256SAS=1");
            else
                TiviPhoneService.doCmd("set cfg.iDisable256SAS=0");
            sasType = value;
            notifyChanged();
        }
        return true;
    }

    @Override
    // UX recommendation is not to show cancel button on such lists.
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null);
    }
}
