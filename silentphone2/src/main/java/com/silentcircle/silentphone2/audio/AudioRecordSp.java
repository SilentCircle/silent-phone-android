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

import android.annotation.TargetApi;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.util.Log;

import com.silentcircle.silentphone2.util.ConfigurationUtilities;

/**
 * This class is a helper/wrapper to simplify handling audio recording from C++ .
 *
 * Most methods are currently not used by any Java code, only from C++, thus
 * mark them as 'unused'.
 *
 * Created by werner on 19.06.14.
 */
public class AudioRecordSp {

    private static final String TAG = AudioRecordSp.class.getSimpleName();

    private static boolean mUseInternalAec;
    private AudioRecord mRecorder;
    private AcousticEchoCanceler mAec;
    private NoiseSuppressor mNoise;
    private AutomaticGainControl mGain;
    

    public static void setUseInternalAec(boolean use) {
        mUseInternalAec = use;
    }

    @SuppressWarnings("unused")
    public AudioRecordSp() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Create empty Audio recorder helper");
    }

    @SuppressWarnings("unused")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public AudioRecordSp(int sampleRateInHz, int channelConfig, int audioFormat, int bufferSizeInBytes) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Create Audio recorder");
        mRecorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRateInHz, channelConfig,
                audioFormat, bufferSizeInBytes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (mUseInternalAec) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "AEC is available and usage requested");
                mAec = AcousticEchoCanceler.create(mRecorder.getAudioSessionId());
                if (mAec != null)
                    mAec.setEnabled(true);
            }
            if (NoiseSuppressor.isAvailable()) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "NoiseSuppressor is available");
                mNoise = NoiseSuppressor.create(mRecorder.getAudioSessionId());
                if (mNoise != null)
                    mNoise.setEnabled(true);
            }
            if (AutomaticGainControl.isAvailable()) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "AutomaticGainControl is available");
                mGain = AutomaticGainControl.create(mRecorder.getAudioSessionId());
                if (mGain != null)
                    mGain.setEnabled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    public static int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        return AudioRecord.getMinBufferSize (sampleRateInHz, channelConfig, audioFormat);
    }

    @SuppressWarnings("unused")
    public int read(short[] audioData, int offsetInShorts, int sizeInShorts) {
        if (mRecorder != null) {
            return mRecorder.read(audioData, offsetInShorts, sizeInShorts);
        }
        return 0;
    }

    @SuppressWarnings("unused")
    public void startRecording() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Start recording, mRecorder: " + mRecorder);
        if (mRecorder != null) {
            if (mRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Audio recorder not properly initialized");
                mRecorder = null;
                return;
            }
            try {
                mRecorder.startRecording();
            } catch (Exception ex) {
                Log.e(TAG, "Cannot record audio", ex);
                mRecorder = null;
            }
        }
    }

    @SuppressWarnings("unused")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stop() {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Stop recording");
        if (mAec != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mAec.release();
        if (mNoise != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mNoise.release();
        if (mGain != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            mGain.release();
        if (mRecorder != null && mRecorder.getState() != AudioRecord.STATE_UNINITIALIZED) {
            mRecorder.stop();
            mRecorder.release();
        }
        mRecorder = null;
    }
}
