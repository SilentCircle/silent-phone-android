/*
 * https://github.com/google/ringdroid/blob/master/app/src/main/java/com/ringdroid/soundfile/SoundFile.java
 *
 * Copyright (C) 2015 Google Inc.
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
package com.silentcircle.common.waveform;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;

/**
 * A class that reads an audio file, stores its properties and calculates the SPL (sound pressure levels)
 * in dB scale for as many regions as the ones requested.
 *
 * SPL is calculated from the log of the rms (root mean square) sound pressure of a measured
 * sound related to the maximum possible value. dB Levels range from -120 dB to 0 dB,
 * where 0 dB is the loudest.
 *
 * Post processing like linear falloff is applied to the dB levels to make them look nicer.
 */
public class SoundFile {

    private static final String TAG = SoundFile.class.getSimpleName();

    private static final int MAX_VALUE = (int) Math.pow(2, 15);
    private static final int MIN_DB_VALUE = -120;
    private static final float FALLOFF_RATE = -2.5f; // how many dB to fall per level measurement
    private static final int OFFSET_START_MS = 70;  // the initial samples to ignore, because
                                                    // they usually have no sound data

    private File mInputFile = null;
    private Uri mInputUri = null;

    // Member variables representing audio data
    private String mFileType;
    private int mFileSize;
    private int mAvgBitRate;  // Average bit rate in kbps.
    private int mSampleRate;
    private int mChannels;
    private long mNumSamples;  // total number of samples per channel in audio file
    private long mDurationMS;

    private int mRequestedLevelsNum;
    private int mLevelsWindowDurationMS;
    private float[] mLevels;
    private int mLevelsLength;
    private float mMaxLevel = MIN_DB_VALUE;

    /**
     * Creates and return a SoundFile object using the specified audio file.
     *
     * The audio will be split in {@code levelsNumber} sections and the Sound Pressure Levels will be
     * calculated in dB for each section. The result can be retrieved from {@link #getLevels()}.
     *
     * Note that the actual number of levels may not be the same as the requested one here. You can
     * use {@link #getLevelsLength()} to get the actual number of dB levels.
     *
     * @param file the audio file to open
     * @param levelsNumber the requested number of db levels
     * @return a SoundFile object
     */
    public static SoundFile create(File file, int levelsNumber) {
        String name = file.getName().toLowerCase();
        String[] components = name.split("\\.");
        if (components.length < 2) {
            return null;
        }
        if (!Arrays.asList(getSupportedExtensions()).contains(components[components.length - 1])) {
            return null;
        }
        SoundFile soundFile = new SoundFile();
        boolean success = soundFile.ReadFile(file, levelsNumber);
        return (success) ? soundFile : null;
    }

    public static SoundFile create(Context context, Uri uri, int levelsNumber) {
        SoundFile soundFile = new SoundFile();
        boolean success = soundFile.ReadUri(context, uri, levelsNumber);
        return (success) ? soundFile : null;
    }

    public static String[] getSupportedExtensions() {
        return new String[] {"mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "ogg", "mp4"};
    }

    public String getFiletype() {
        return mFileType;
    }

    public int getFileSizeBytes() {
        return mFileSize;
    }

    public int getAvgBitrateKbps() {
        return mAvgBitRate;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannels() {
        return mChannels;
    }

    public long getNumSamples() {
        return mNumSamples;  // Number of samples per channel.
    }

    public float[] getLevels() {
        return mLevels;
    }

    public int getLevelsLength() {
        return mLevelsLength;
    }

    public float getMaxLevel() {
        return mMaxLevel;
    }

    public long getDurationMS() {
        return mDurationMS;
    }


    public boolean ReadUri(Context context, Uri inputUri, int levelsNumber) {
        MediaExtractor extractor = new MediaExtractor();

        mInputUri = inputUri;
        try {
            extractor.setDataSource(context, inputUri, null);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return ReadFromExtractor(extractor, levelsNumber);
    }

    //TODO: use the latest MediaCodec methods to test if decoding performance is improved
    private boolean ReadFile(File inputFile, int levelsNumber) {
        MediaExtractor extractor = new MediaExtractor();

        mInputFile = inputFile;
        String[] components = mInputFile.getPath().split("\\.");
        mFileType = components[components.length - 1];
        mFileSize = (int)mInputFile.length();
        try {
            extractor.setDataSource(mInputFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return ReadFromExtractor(extractor, levelsNumber);
    }

    /*
     * ReadFile() takes around 1,1 to 2 seconds to read a 30sec .m4a file in a Galaxy S7. Almost all of
     * this time, is used in the decoding part. The actual calculation of the dB levels, adds
     * almost no extra time. Perhaps, using the latest MediaCodec methods and not the deprecated ones,
     * could improve performance.
     */
    private boolean ReadFromExtractor(MediaExtractor extractor, int levelsNumber) {
        MediaFormat format = null;

        int numTracks = extractor.getTrackCount();
        // find and select the first audio track present in the file.
        for (int i = 0; i < numTracks; i++) {
            format = extractor.getTrackFormat(i);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                extractor.selectTrack(i);
                break;
            }
        }
        if (numTracks == 0) {
            String source = (mInputFile != null) ? mInputFile.toString() : mInputUri.toString();
            Log.e(TAG, "No audio track found in " + source);
            return false;
        }
        mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        // Expected total number of samples per channel.
        int expectedNumSamples =
                (int)((format.getLong(MediaFormat.KEY_DURATION) / 1000000.f) * mSampleRate + 0.5f);
        int offsetStartNumSamples = OFFSET_START_MS * mSampleRate / 1000;

        // subtract the first offsetStartNumSamples samples that will not be measured
        int expectedNumSamplesToMeasure = expectedNumSamples - offsetStartNumSamples;
        if (expectedNumSamples < 0) {
            expectedNumSamplesToMeasure = expectedNumSamples;
            offsetStartNumSamples = 0;
        }

        if (expectedNumSamplesToMeasure < mRequestedLevelsNum) {
            return false;
        }

        mRequestedLevelsNum = levelsNumber;
        // how many samples are needed for 1 level calculation
        int levelSampleSize = (expectedNumSamplesToMeasure) / mRequestedLevelsNum;
        mLevelsWindowDurationMS = levelSampleSize / mSampleRate * 1000;
        // make the array 1 second bigger to account for wrong estimation
        int estimatedLevelsLength = (expectedNumSamplesToMeasure + mSampleRate) / levelSampleSize;
        mLevels = new float[estimatedLevelsLength];
        int samplesMeasuredForCurrentLevel = 0;
        int samplesOffsetCount = 0;
        int levelsCalculated = 0;
        float meanSquare = 0;

        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        codec.configure(format, null, null, 0);
        codec.start();

        int decodedSamplesSize = 0;  // size of the output buffer containing decoded samples.
        byte[] decodedSamples = null;

        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        int sample_size;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long presentation_time;
        int tot_size_read = 0;
        long decodedNumSamplesTotal = 0;
        boolean done_reading = false;

        Boolean firstSampleData = true;
        while (true) {
            // read data from file and feed it to the decoder input buffers.
            int inputBufferIndex = codec.dequeueInputBuffer(100);
            if (!done_reading && inputBufferIndex >= 0) {
                sample_size = extractor.readSampleData(inputBuffers[inputBufferIndex], 0);
                if (firstSampleData
                        && format.getString(MediaFormat.KEY_MIME).equals("audio/mp4a-latm")
                        && sample_size == 2) {
                    // For some reasons on some devices (e.g. the Samsung S3) you should not
                    // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                    // crash. These two bytes do not contain music data but basic info on the
                    // stream (e.g. channel configuration and sampling frequency), and skipping them
                    // seems OK with other devices (MediaCodec has already been configured and
                    // already knows these parameters).
                    extractor.advance();
                    tot_size_read += sample_size;
                } else if (sample_size < 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    done_reading = true;
                } else {
                    presentation_time = extractor.getSampleTime();
                    codec.queueInputBuffer(inputBufferIndex, 0, sample_size, presentation_time, 0);
                    extractor.advance();
                    tot_size_read += sample_size;
                }
                firstSampleData = false;
            }

            // Get decoded stream from the decoder output buffers.
            int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
            if (outputBufferIndex >= 0 && info.size > 0) {
                if (decodedSamplesSize < info.size) {
                    decodedSamplesSize = info.size;
                    decodedSamples = new byte[decodedSamplesSize];
                }
                outputBuffers[outputBufferIndex].get(decodedSamples, 0, info.size);
                outputBuffers[outputBufferIndex].clear();

                int decodedNumSamples = info.size / (mChannels * 2); // 2 bytes per sample
                decodedNumSamplesTotal += decodedNumSamples;

                // samples has the following format:
                // {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
                // where sicj is the ith sample of the jth channel (a sample is a signed short)
                // M is the number of channels (e.g. 2 for stereo) and N is the number of samples per channel.
                ShortBuffer samples = outputBuffers[outputBufferIndex].order(ByteOrder.nativeOrder()).asShortBuffer();

                for (int sampleIdx = 0; sampleIdx < decodedNumSamples; ++sampleIdx) {
                    if (samplesOffsetCount < offsetStartNumSamples) {
                        samplesOffsetCount++;
                        continue;
                    }

                    float value = 0;
                    for (int channelIdx = 0; channelIdx < mChannels; channelIdx++) {
                        int sample = samples.get(sampleIdx * mChannels + channelIdx);
                        value += sample * sample;
                    }
                    value /= mChannels;
                    meanSquare += value / levelSampleSize; // accumulate the mean square

                    samplesMeasuredForCurrentLevel ++;

                    if (samplesMeasuredForCurrentLevel == levelSampleSize) {
                        samplesMeasuredForCurrentLevel = 0;
                        if (mLevels.length > levelsCalculated) {
                            // To get db from the accumulated mean square you have to calculate:
                            // 20 * log10( sqrt(meanSquare/MAX_VALUE) )
                            // However the following is equivalent and does not contain the square
                            // root calculation
                            float normalizedMeanSquare = meanSquare / (MAX_VALUE * MAX_VALUE);
                            float db = 10 * (float) Math.log10(normalizedMeanSquare);
                            db = Math.max(db, MIN_DB_VALUE);
                            if (db > mMaxLevel) {
                                mMaxLevel = db;
                            }
                            mLevels[levelsCalculated] = db;
                            levelsCalculated++;
                            meanSquare = 0;
                        }
                        else {
                            Log.w(TAG, "Level value could not be added to array");
                        }
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false);
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // We could check that codec.getOutputFormat(), which is the new output format,
                // is what we expect.
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    || decodedNumSamplesTotal >= expectedNumSamples) {
                // We got all the decoded data from the decoder. Stop here.
                // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                // calls to dequeueOutputBuffer may result in the application crashing, without
                // even an exception being thrown... Hence the second check.
                // (for mono AAC files, the S3 will actually double each sample, as if the stream
                // was stereo. The resulting stream is half what it's supposed to be and with a much
                // lower pitch.)
                break;
            }
        }
        mNumSamples = decodedNumSamplesTotal;
        mDurationMS = Math.round((double)mNumSamples / mSampleRate * 1000);
        mAvgBitRate = (int)((mFileSize * 8) * ((double)mSampleRate / mNumSamples) / 1000);
        mLevelsLength = levelsCalculated;

        float[] oldLevelsArray = mLevels;
        mLevels = new float[mLevelsLength];
        System.arraycopy(oldLevelsArray, 0, mLevels, 0, mLevelsLength);


        // Linear falloff
        float previousLevel = mLevels[0];
        for (int i = 1; i < mLevelsLength; i++) {
            float falloff = previousLevel + FALLOFF_RATE;
            if (falloff > mLevels[i]) {
                mLevels[i] = falloff;
            }
            previousLevel = mLevels[i];
        }


        extractor.release();
        codec.stop();
        codec.release();

        return true;

    }

    public void printInfo() {
        Log.d(TAG, " file size: " + mFileSize
                + " sample rate: " + mSampleRate
                + " channels: " + mChannels
                + " total samples: " + mNumSamples
                + " duration: " + mDurationMS
                + " average bitrate: " + mAvgBitRate
                + " levels calculated " + mLevelsLength);
    }
}
