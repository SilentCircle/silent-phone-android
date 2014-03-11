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

package com.silentcircle.silentphone.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.silentcircle.silentphone.R;

/**
 * Created by werner on 22.09.13.
 */
public class SpRingtone extends RingtonePreference {

    // This string must match the res/xml/preference_options, for the ringtone
    // definitions.
    public static String RINGTONE_KEY = "sp_ringtone";

    private Context mContext;
    private Uri tone;

    public SpRingtone(Context context) {
        super(context);
        prepare();
    }

    public SpRingtone(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    SharedPreferences prefs;
    private void prepare() {
        mContext = getContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        String toneString = prefs.getString(RINGTONE_KEY, null);
        if (TextUtils.isEmpty(toneString))
            tone = null;
        else
            tone = Uri.parse(toneString);
    }

    @Override
    public CharSequence getSummary() {
        if (tone == null)
            return mContext.getString(R.string.no_ringtone);

        Ringtone ringtone = RingtoneManager.getRingtone(mContext, tone);
        if (ringtone != null)
            return ringtone.getTitle(mContext);
        else
            return mContext.getString(R.string.no_ringtone);
    }

    @Override
    protected void onSaveRingtone(Uri uri) {
        super.onSaveRingtone(uri);
        if (uri == null) {
            if (tone != null)
                notifyChanged();
            tone = null;
            return;
        }
        if (!uri.equals(tone)) {
            tone = uri;
            notifyChanged();
        }
    }
}
