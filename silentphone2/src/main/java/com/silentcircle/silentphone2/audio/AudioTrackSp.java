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

package com.silentcircle.silentphone2.audio;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * This class is a helper/wrapper to simplify handling audio playback from C++ .
 *
 * The methods are currently nit used by any Java code, only from C++, thus
 * mark them as 'unused'.
 *
 * Created by werner on 19.06.14.
 */
@SuppressWarnings("unused")
public class AudioTrackSp {
    private static final String TAG = AudioTrackSp.class.getSimpleName();

    private AudioTrack mTrack;

    @SuppressWarnings("unused")
    static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        return AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
    }

    @SuppressWarnings("unused")
    public AudioTrackSp() {
        Log.d(TAG, "Create empty Audio track helper");
    }

    @SuppressWarnings("unused")
    public AudioTrackSp(int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Create Audio track");
        mTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRateInHz, channelConfig, audioFormat,
                bufferSizeInBytes, AudioTrack.MODE_STREAM);
    }

    @SuppressWarnings("unused")
    void play() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Start audio playback, mTrack: " + mTrack);
        if (mTrack != null) {
            if (mTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Audio track not properly initialized");
                mTrack = null;
                return;
            }
            try {
                mTrack.play();
            } catch (Exception ex) {
                Log.e(TAG, "Cannot play audio", ex);
                mTrack = null;
            }
        }
    }

    @SuppressWarnings("unused")
    int	write(short[] audioData, int offsetInShorts, int sizeInShorts) {
        if (mTrack != null)
            return mTrack.write(audioData, offsetInShorts, sizeInShorts);
        return 0;
    }

    @SuppressWarnings("unused")
    void stop() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Stop audio playback");
        if (mTrack != null && mTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
            mTrack.stop();
            mTrack.release();
        }
        mTrack = null;
    }
}
