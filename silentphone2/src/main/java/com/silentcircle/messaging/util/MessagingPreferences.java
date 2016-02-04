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
package com.silentcircle.messaging.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Preferences wrapper for messaging part of application.
 */
public class MessagingPreferences {

    public static final String PREFERENCE_SHOW_BURN_ANIMATION = "sp_show_message_burn_animation";
    public static final String PREFERENCE_MESSAGE_SOUNDS_ENABLED = "sp_play_sound_for_new_message";
    public static final String PREFERENCE_WARN_WHEN_EXPORT_ATTACHMENT =
            "sp_warn_when_export_message_attachments";

    private static MessagingPreferences sInstance;

    private Context sContext;

    private final SharedPreferences sPreferences;
    private final SharedPreferences.Editor sEditor;

    @SuppressLint("CommitPrefEdits")
    private MessagingPreferences(Context context) {
        sContext = context.getApplicationContext();
        sPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sEditor = sPreferences.edit();
    }

    public static synchronized MessagingPreferences getInstance(final Context context) {
        if (sInstance == null) {
            sInstance = new MessagingPreferences(context);
        }
        return sInstance;
    }

    public synchronized void setMessageSoundsEnabled(boolean enabled) {
        sEditor.putBoolean(PREFERENCE_MESSAGE_SOUNDS_ENABLED, enabled);
        sEditor.commit();
    }

    public synchronized boolean getMessageSoundsEnabled() {
        return sPreferences.getBoolean(PREFERENCE_MESSAGE_SOUNDS_ENABLED, true);
    }

    public synchronized void setWarnWhenDecryptAttachment(boolean enabled) {
        sEditor.putBoolean(PREFERENCE_WARN_WHEN_EXPORT_ATTACHMENT, enabled);
        sEditor.commit();
    }

    public synchronized boolean getWarnWhenDecryptAttachment() {
        return sPreferences.getBoolean(PREFERENCE_WARN_WHEN_EXPORT_ATTACHMENT, true);
    }

    public synchronized void setShowBurnAnimation(boolean enabled) {
        sEditor.putBoolean(PREFERENCE_SHOW_BURN_ANIMATION, enabled);
        sEditor.commit();
    }

    public synchronized boolean getShowBurnAnimation() {
        return sPreferences.getBoolean(PREFERENCE_SHOW_BURN_ANIMATION, true);
    }
}
