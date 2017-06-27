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
package com.silentcircle.messaging.activities;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.logs.Log;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class SoundRecorderActivity extends Activity {

    private static final String TAG = SoundRecorderActivity.class.getSimpleName();

    private static final String FILE_DESCRIPTOR_MODE = "rwt";

    private static final int MAX_DURATION_MS = 3 * 60 * 1000; // 3 minutes

    Uri mOutputUri;
    FileDescriptor mOutputFileDescriptor;

    LinearLayout mSoundRecorderLayout;

    // Views for recording
    ProgressBar mRecordAudioProgressBar;
    ImageButton mPlaybackPlayPauseButton;

    // Views for playback
    SeekBar mAudioSeekBar;
    Button mRecordAudioCancelButton;
    ImageButton mRecordAudioPlayButton;

    // Views for both
    Button mRecordAudioControlButton;
    TextView mCurrentRecordTimeCurrentTextView;
    TextView mCurrentRecordTimeMaxTextView;
    Timer mProgressTimer;

    // Recorder stuff
    MediaRecorder mMediaRecorder;
    RecordingState mCurrentRecordingState;

    // Playback stuff
    MediaPlayer mMediaPlayer;
    PlaybackState mCurrentPlaybackState;

    // Stuff for both
    View.OnClickListener mOnClickListener;

    enum RecordingState {
        NOT_STARTED,
        STARTED,
        STOPPED
    }

    enum PlaybackState {
        NOT_STARTED,
        STARTED,
        PAUSED,
        PAUSED_COMPLETED,
        STOPPED
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int theme = MessagingPreferences.getInstance(this).getMessageTheme();
        int selectedTheme = (theme == MessagingPreferences.INDEX_THEME_DARK
                ? R.style.SoundRecorderDialogBlack
                : R.style.SoundRecorderDialogLight);
        setTheme(selectedTheme);

        super.onCreate(savedInstanceState);

        if (TiviPhoneService.calls.getCallCount() > 0) {
            Log.e(TAG, "Sound recording is not supported during a call");

            Toast.makeText(this, R.string.record_currently_on_call, Toast.LENGTH_LONG).show();

            finish();
            return;
        }

        setContentView(R.layout.activity_sound_recorder);

        Intent recordIntent = getIntent();

        Uri outputUri;

        if (recordIntent == null) {
            Log.e(TAG, "Intent is null");
            finish();
            return;
        }

        outputUri = recordIntent.getData();

        if (outputUri == null) {
            Log.e(TAG, "Output URI is null");
            finish();
            return;
        }

        mOutputUri = outputUri;

        FileDescriptor fileDescriptor = null;
        try {
            // TODO: keep reference to ParcelFileDescriptor?
            fileDescriptor = getContentResolver().openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).getFileDescriptor();
        } catch (FileNotFoundException exception) {
            Log.e(TAG, "Output URI is an invalid file", exception);
        }

        if(fileDescriptor == null || !fileDescriptor.valid()) {
            finish();
            return;
        }

        mOutputFileDescriptor = fileDescriptor;

        setup();

        setFinishOnTouchOutside(false);
    }

    private void setup() {
        setupViews();
        setupRecorder();
    }

    private void setupViews() {
        mSoundRecorderLayout = (LinearLayout) findViewById(R.id.sound_recorder_layout);

        mRecordAudioProgressBar = (ProgressBar) findViewById(R.id.record_audio_progress);
        mAudioSeekBar = (SeekBar) findViewById(R.id.audio_seekbar);

        mPlaybackPlayPauseButton = (ImageButton) findViewById(R.id.record_audio_play_pause_button);
        mRecordAudioControlButton = (Button) findViewById(R.id.record_audio_control_button);
        mRecordAudioCancelButton = (Button) findViewById(R.id.record_audio_cancel_button);

        mRecordAudioPlayButton = (ImageButton) findViewById(R.id.record_audio_play_pause_button);

        mCurrentRecordTimeCurrentTextView = (TextView) findViewById(R.id.record_audio_time_current);
        mCurrentRecordTimeMaxTextView = (TextView) findViewById(R.id.record_audio_time_max);

        mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(v.getId()) {
                    case R.id.record_audio_control_button:
                        switch(mCurrentRecordingState) {
                            case NOT_STARTED:
                                startRecording();
                                break;
                            case STARTED:
                                tearDownRecording(false);
                                break;
                            case STOPPED:
                                completeRecording();
                                break;
                        }
                        break;

                    case R.id.record_audio_play_pause_button:
                        switch(mCurrentRecordingState) {
                            case STOPPED:
                                switch(mCurrentPlaybackState) {
                                    case NOT_STARTED:
                                        startPlayback();
                                        break;
                                    case STARTED:
                                        pausePlayback(false);
                                        break;
                                    case PAUSED:
                                        startPlayback();
                                        break;
                                    case PAUSED_COMPLETED:
                                        startPlayback();
                                        break;
                                }
                                break;
                        }
                        break;

                    case R.id.record_audio_cancel_button:
                        onCancel();
                        break;
                }
            }
        };

        mPlaybackPlayPauseButton.setOnClickListener(mOnClickListener);
        mRecordAudioControlButton.setOnClickListener(mOnClickListener);
        mRecordAudioCancelButton.setOnClickListener(mOnClickListener);
        mRecordAudioPlayButton.setOnClickListener(mOnClickListener);

        mAudioSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentRecordTimeCurrentTextView.setText(getTimeString(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(mCurrentPlaybackState == PlaybackState.STARTED) {
                    pausePlayback(false);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mMediaPlayer != null) {
                    mMediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });

        mRecordAudioProgressBar.setMax(MAX_DURATION_MS);
    }

    private void setCurrentRecordingState(RecordingState recordingState) {
        Log.i(TAG, "Recording state changed: " + recordingState);

        mCurrentRecordingState = recordingState;
    }

    private void setCurrentPlaybackState(PlaybackState playbackState) {
        Log.i(TAG, "Playback state changed: " + playbackState);

        mCurrentPlaybackState = playbackState;
    }

    private void setupProgressTimer() {
        mProgressTimer = new Timer();
    }

    private void startProgressTimer() {
        if(mProgressTimer == null) {
            setupProgressTimer();
        }

        mProgressTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int nextMilliSeconds;

                        if(mCurrentRecordingState == RecordingState.STARTED) {
                            if(mRecordAudioProgressBar.getProgress() < MAX_DURATION_MS) {
                                nextMilliSeconds = mRecordAudioProgressBar.getProgress() + getProgressIncrement(mRecordAudioProgressBar.getProgress());

                                Log.i(TAG, "Recording progress " + nextMilliSeconds);

                                mRecordAudioProgressBar.setProgress(nextMilliSeconds);
                                mCurrentRecordTimeCurrentTextView.setText(getTimeString(nextMilliSeconds));
                            } else {
                                tearDownRecording(false);
                            }
                        } else if(mCurrentRecordingState == RecordingState.STOPPED) {
                            if(mAudioSeekBar.getProgress() < MAX_DURATION_MS) {
                                nextMilliSeconds = mAudioSeekBar.getProgress() + getProgressIncrement(mAudioSeekBar.getProgress());

                                Log.i(TAG, "Recording progress " + nextMilliSeconds);

                                mAudioSeekBar.setProgress(nextMilliSeconds);
                                mCurrentRecordTimeCurrentTextView.setText(getTimeString(nextMilliSeconds));
                            }
                        }
                    }
                });
            }
        }, 0, 1000);
    }

    private void stopProgressTimer() {
        if(mProgressTimer != null) {
            mProgressTimer.cancel();
            mProgressTimer.purge();
        }

        mProgressTimer = null;
    }

    private void setupRecorder() {
        // Credit to wernerd :-)
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioChannels(1);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setAudioEncodingBitRate(32000);
        mMediaRecorder.setAudioSamplingRate(48000);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setMaxDuration(MAX_DURATION_MS);
        mMediaRecorder.setOutputFile(mOutputFileDescriptor);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        try {
            mMediaRecorder.prepare();

            onSetupRecorder();
        } catch (IOException exception) {
            Log.e(TAG, "Recording preparation failed", exception);

            //Toast.makeText(this, R.string.fail_reason_io_error, Toast.LENGTH_LONG).show();

            finish();
            return;
        }
    }

    private void onSetupRecorder() {
        setCurrentRecordingState(RecordingState.NOT_STARTED);
    }

    private void setupPlayer() {
        mMediaPlayer = new MediaPlayer();

        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                pausePlayback(true);
            }
        });

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                onSetupPlayer();
            }
        });

        try {
            mOutputFileDescriptor = getContentResolver().openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).getFileDescriptor();

            mMediaPlayer.setDataSource(mOutputFileDescriptor);
            mMediaPlayer.prepareAsync();
        } catch (IOException exception) {
            Log.e(TAG, "Playback I/O error", exception);

            //Toast.makeText(this, R.string.fail_reason_io_error, Toast.LENGTH_LONG).show();

            finish();
            return;
        }
    }

    private void onSetupPlayer() {
        setCurrentPlaybackState(PlaybackState.NOT_STARTED);

        Log.i(TAG, "Max seek duration set: " + mMediaPlayer.getDuration());

        mAudioSeekBar.setMax(getMaxDuration(mMediaPlayer.getDuration()));
        mCurrentRecordTimeCurrentTextView.setText("0:00");
        mCurrentRecordTimeMaxTextView.setText(getTimeString(getMaxDuration(mMediaPlayer.getDuration())));
    }

    private void startRecording() {
        try {
            mMediaRecorder.start();

            onStartRecording();
        } catch(IllegalStateException exception) {
            Log.e(TAG, "Bad state when starting recording", exception);
        }
    }

    private void onStartRecording() {
        Log.i(TAG, "Recording started");

        setCurrentRecordingState(RecordingState.STARTED);

        mRecordAudioControlButton.setText(R.string.stop_dialog);

        mPlaybackPlayPauseButton.setVisibility(View.GONE);
        mRecordAudioProgressBar.setVisibility(View.VISIBLE);
        mAudioSeekBar.setVisibility(View.INVISIBLE);

        startProgressTimer();
    }

    private void startPlayback() {
        mMediaPlayer.start();

        onStartPlayback();
    }

    private void onStartPlayback() {
        Log.i(TAG, "Playback started");

        setCurrentPlaybackState(PlaybackState.STARTED);

        mPlaybackPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause);

        startProgressTimer();
    }

    private void stopRecording(boolean fromCancellation) {
        try {
            mMediaRecorder.stop();

        } catch(IllegalStateException exception) {
            Log.e(TAG, "Bad state when stopping recording (ignoring)", exception);
        } catch(RuntimeException exception) {
            Log.e(TAG, "Exception when stopping recording (ignoring)", exception);
        }

        onStopRecording(fromCancellation);
    }

    private void onStopRecording(boolean fromCancellation) {
        Log.i(TAG, "Recording stopped");

        setCurrentRecordingState(RecordingState.STOPPED);

        stopProgressTimer();
    }

    private void stopPlayback(boolean fromCancellation) {
        try {
            mMediaPlayer.stop();
        } catch(IllegalStateException exception) {
            Log.e(TAG, "Bad state when stopping playback (ignoring)", exception);
        }

        onStopPlayback(fromCancellation);
    }

    private void onStopPlayback(boolean fromCancellation) {
        Log.i(TAG, "Playback stopped");

        setCurrentPlaybackState(PlaybackState.STOPPED);

        stopProgressTimer();

        if(!fromCancellation) {
            mPlaybackPlayPauseButton.setVisibility(View.GONE);
            mRecordAudioProgressBar.setVisibility(View.VISIBLE);
            mAudioSeekBar.setVisibility(View.INVISIBLE);
        }
    }

    private void pausePlayback(boolean fromCompletion) {
        if(mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }

        onPausePlayback(fromCompletion);
    }

    private void onPausePlayback(boolean fromCompletion) {
        Log.i(TAG, "Playback paused");

        if(!fromCompletion) {
            setCurrentPlaybackState(PlaybackState.PAUSED);
        } else {
            resetPlayback();

            setCurrentPlaybackState(PlaybackState.PAUSED_COMPLETED);
        }

        mPlaybackPlayPauseButton.setImageResource(android.R.drawable.ic_media_play);

        stopProgressTimer();
    }

    private void completeRecording() {
        tearDownPlayback(true);
        tearDownRecording(true);

        onCompleteRecording();
    }

    private void onCompleteRecording() {
        Intent returnIntent = new Intent();
        returnIntent.setData(mOutputUri);
        setResult(RESULT_OK, returnIntent);

        finish();
        return;
    }

    private void resetPlayback() {
        mCurrentRecordTimeCurrentTextView.setText("0:00");
        mAudioSeekBar.setProgress(0);
    }

    private void tearDownRecording(boolean fromCancellation) {
        Log.i(TAG, "Recording teardown");

        if(mCurrentRecordingState == RecordingState.STARTED) {
            stopRecording(fromCancellation);
        }

        if(mMediaRecorder != null) {
            mMediaRecorder.release();

            mMediaRecorder = null;

            try {
                getContentResolver().openFileDescriptor(mOutputUri, FILE_DESCRIPTOR_MODE).close();
            } catch (IOException exception) {
                Log.i(TAG, "Recording teardown exception (ignoring)", exception);
            }
        }

        if(!fromCancellation) {
            mRecordAudioControlButton.setText(R.string.messaging_send_message);

            mPlaybackPlayPauseButton.setVisibility(View.VISIBLE);
            mRecordAudioProgressBar.setVisibility(View.INVISIBLE);
            mAudioSeekBar.setVisibility(View.VISIBLE);
            mAudioSeekBar.setProgress(0);

            setupPlayer();
        }
    }

    private void tearDownPlayback(boolean fromCancellation) {
        Log.i(TAG, "Playback teardown");

        if(mCurrentPlaybackState == PlaybackState.STARTED) {
            stopPlayback(fromCancellation);
        }

        if(mMediaPlayer != null) {
            mMediaPlayer.release();

            mMediaPlayer = null;
        }
    }

    private void onCancel() {
        tearDownRecording(true);
        tearDownPlayback(true);

        finish();
        return;
    }

    private String getTimeString(int miliSeconds) {
        int minutes = (int) Math.floor((miliSeconds / 1000.0) / 60);
        int seconds = miliSeconds / 1000 - minutes * 60;

        return String.format("%01d:%02d", minutes, seconds);
    }

    private int getNumIncrements(int durationMiliSeconds) {
        int numIncrements = (int) Math.ceil(durationMiliSeconds / 1000.0);

        if(numIncrements == 0) {
            return 1;
        }

        return numIncrements;
    }

    private int getMaxDuration(int durationMiliSeconds) {
        int numIncrements = getNumIncrements(durationMiliSeconds) * 1000;

        if(numIncrements > MAX_DURATION_MS) {
            numIncrements = MAX_DURATION_MS;
        }

        return numIncrements;
    }

    private int getProgressIncrement(int durationMiliSeconds) {
        int numIncrements = getNumIncrements(durationMiliSeconds);

        if(numIncrements == 1) {
            return 1000;
        }

        return Math.round(durationMiliSeconds / numIncrements);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        onCancel();
    }
}
