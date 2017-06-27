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
package com.silentcircle.messaging.views;

import android.media.MediaPlayer;
import android.widget.MediaController;

/**
 *
 */
public class MediaPlayerWrapper implements MediaController.MediaPlayerControl, MediaPlayer.OnBufferingUpdateListener {

    private final MediaPlayer player;
    private int buffered;

    public interface OnEventListener {
        void onStart();
        void onPause();
    }

    private OnEventListener mOnEventListener;

    public MediaPlayerWrapper(MediaPlayer player) {
        this.player = player;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return player.getAudioSessionId();
    }

    @Override
    public int getBufferPercentage() {
        return buffered;
    }

    @Override
    public int getCurrentPosition() {
        try {
            return player.getCurrentPosition();
        } catch (IllegalStateException exception) {
            // There is no way to prevent this; MediaPlayer does not provide adequate state
            // detection to determine whether #getCurrentPosition is being called in a valid state.
        }
        return 0;
    }

    @Override
    public int getDuration() {
        try {
            return player.getDuration();
        } catch (IllegalStateException exception) {
            // There is no way to prevent this; MediaPlayer does not provide adequate state
            // detection to determine whether #getCurrentPosition is being called in a valid state.
        }
        return 0;
    }

    @Override
    public boolean isPlaying() {
        try {
            return player.isPlaying();
        } catch (IllegalStateException exception) {
            // There is no way to prevent this; MediaPlayer does not provide adequate state
            // detection to determine whether #getCurrentPosition is being called in a valid state.
        }
        return false;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer player, int percent) {
        if (player == this.player) {
            buffered = percent;
        }
    }

    @Override
    public void pause() {
        player.pause();

        if (mOnEventListener != null) {
            mOnEventListener.onPause();
        }
    }

    @Override
    public void seekTo(int ms) {
        player.seekTo(ms);
    }

    @Override
    public void start() {
        player.start();

        if (mOnEventListener != null) {
            mOnEventListener.onStart();
        }
    }

    public void setOnEventListener(OnEventListener listener) {
        mOnEventListener = listener;
    }
}

