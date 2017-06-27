/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.support.annotation.NonNull;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.silentphone2.R;

/**
 * Sound effects for messaging.
 */
public class SoundNotifications {

    protected static SoundPool sSoundPool;
    protected static int sReceivedSoundId;
    protected static int sBurnedSoundId;
    protected static int sSentSoundId;

    public static SoundPool getSoundPool() {
        if (sSoundPool == null) {
            sSoundPool = createSoundPool(SilentPhoneApplication.getAppContext());
        }
        return sSoundPool;
    }

    public static void playReceiveMessageSound() {
        playSound(sReceivedSoundId);
    }

    public static void playBurnMessageSound() {
        playSound(sBurnedSoundId);
    }

    public static void playSentMessageSound() {
        playSound(sSentSoundId);
    }

    private static void playSound(int soundId) {
        final Context context = SilentPhoneApplication.getAppContext();
        final boolean soundsEnabled = MessagingPreferences.getInstance(context).getMessageSoundsEnabled();
        if (!soundsEnabled) {
            return;
        }

        SoundPool soundPool = getSoundPool();
        if (soundPool != null) {
            soundPool.play(soundId, 1f, 1f, 0, 0, 1);
        }
    }

    @SuppressWarnings("deprecation")
    private static SoundPool createSoundPool(@NonNull final Context context) {
        SoundPool soundPool;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            soundPool = new SoundPool.Builder()
                    .setAudioAttributes(attributes)
                    .build();
        }
        else {
            soundPool = new SoundPool(2, AudioManager.STREAM_NOTIFICATION, 0);
        }
        sReceivedSoundId = soundPool.load(context, R.raw.received, 1);
        sBurnedSoundId = soundPool.load(context, R.raw.poof, 1);
        sSentSoundId = soundPool.load(context, R.raw.sent, 1);

        return soundPool;
    }
}
