/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.calllognew;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import com.google.common.collect.Lists;
import com.silentcircle.common.testing.NeededForTesting;
import com.silentcircle.contacts.ScCallLog.ScCalls;
import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * View that draws one or more symbols for different types of calls (missed calls, outgoing etc).
 * The symbols are set up horizontally. As this view doesn't create subviews, it is better suited
 * for ListView-recycling that a regular LinearLayout using ImageViews.
 */
public class CallTypeIconsView extends View {
    private List<Integer> mCallTypes = Lists.newArrayListWithCapacity(3);
    private boolean mShowVideo = false;
    private ResourcesLogView mResources;
    private int mWidth;
    private int mHeight;

    public CallTypeIconsView(Context context) {
        this(context, null);
    }

    public CallTypeIconsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mResources = new ResourcesLogView(context);
    }

    public void clear() {
        mCallTypes.clear();
        mWidth = 0;
        mHeight = 0;
        invalidate();
    }

    public void add(int callType) {
        mCallTypes.add(callType);

        final Drawable drawable = getCallTypeDrawable(callType);
        mWidth += drawable.getIntrinsicWidth() + mResources.iconMargin;
        mHeight = Math.max(mHeight, drawable.getIntrinsicHeight());
        invalidate();
    }

    /**
     * Determines whether the video call icon will be shown.
     *
     * @param showVideo True where the video icon should be shown.
     */
    public void setShowVideo(boolean showVideo) {
//        mShowVideo = showVideo;
//        if (showVideo) {
//            mWidth += mResources.videoCall.getIntrinsicWidth();
//            mHeight = Math.max(mHeight, mResources.videoCall.getIntrinsicHeight());
//            invalidate();
//        }
    }

    /**
     * Determines if the video icon should be shown.
     *
     * @return True if the video icon should be shown.
     */
    public boolean isVideoShown() {
        return mShowVideo;
    }

    @NeededForTesting
    public int getCount() {
        return mCallTypes.size();
    }

    @NeededForTesting
    public int getCallType(int index) {
        return mCallTypes.get(index);
    }

    private Drawable getCallTypeDrawable(int callType) {
        switch (callType) {
            case ScCalls.INCOMING_TYPE:
                return mResources.incoming;
            case ScCalls.OUTGOING_TYPE:
                return mResources.outgoing;
            case ScCalls.MISSED_TYPE:
                return mResources.missed;
            default:
                // It is possible for users to end up with calls with unknown call types in their
                // call history, possibly due to 3rd party call log implementations (e.g. to
                // distinguish between rejected and missed calls). Instead of crashing, just
                // assume that all unknown call types are missed calls.
                return mResources.missed;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int left = 0;
        for (Integer callType : mCallTypes) {
            final Drawable drawable = getCallTypeDrawable(callType);
            final int right = left + drawable.getIntrinsicWidth();
            drawable.setBounds(left, 0, right, drawable.getIntrinsicHeight());
            drawable.draw(canvas);
            left = right + mResources.iconMargin;
        }

        // If showing the video call icon, draw it scaled appropriately.
//        if (mShowVideo) {
//            final Drawable drawable = mResources.videoCall;
//            final int right = left + mResources.videoCall.getIntrinsicWidth();
//            drawable.setBounds(left, 0, right, mResources.videoCall.getIntrinsicHeight());
//            drawable.draw(canvas);
//        }
    }

    private static class ResourcesLogView {

        /**
         * Drawable representing an incoming answered call.
         */
        public final Drawable incoming;

        /**
         * Drawable representing an outgoing call.
         */
        public final Drawable outgoing;

        /**
         * Drawable representing an incoming missed call.
         */
        public final Drawable missed;

        /**
         * Drawable representing a video call.
         */
//        public final Drawable videoCall;

        /**
         * The margin to use for icons.
         */
        public final int iconMargin;

        /**
         * Configures the call icon drawables.
         * A single white call arrow which points down and left is used as a basis for all of the
         * call arrow icons, applying rotation and colors as needed.
         *
         * @param context The current context.
         */
        public ResourcesLogView(Context context) {
            final Resources r = context.getResources();

            incoming = ContextCompat.getDrawable(context, R.drawable.ic_call_incoming_holo_dark);
            outgoing = ContextCompat.getDrawable(context, R.drawable.ic_call_outgoing_holo_dark);
            missed = ContextCompat.getDrawable(context, R.drawable.ic_call_missed_holo_dark);
            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);

            /* Original code -- maybe we can use it later */
            
//            incoming = r.getDrawable(R.drawable.ic_call_arrow);
//            incoming.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);
//
//            // Create a rotated instance of the call arrow for outgoing calls.
//            outgoing = BitmapUtil.getRotatedDrawable(r, R.drawable.ic_call_arrow, 180f);
//            outgoing.setColorFilter(r.getColor(R.color.answered_call), PorterDuff.Mode.MULTIPLY);
//
//            // Need to make a copy of the arrow drawable, otherwise the same instance colored
//            // above will be recolored here.
//            missed = r.getDrawable(R.drawable.ic_call_arrow).mutate();
//            missed.setColorFilter(r.getColor(R.color.missed_call), PorterDuff.Mode.MULTIPLY);
//
//
//            // Get the video call icon, scaled to match the height of the call arrows.
//            // We want the video call icon to be the same height as the call arrows, while keeping
//            // the same width aspect ratio.
//            Bitmap videoIcon = BitmapFactory.decodeResource(context.getResources(),
//                    R.drawable.ic_videocam_wht_24dp);
//            int scaledHeight = missed.getIntrinsicHeight();
//            int scaledWidth = (int) ((float) videoIcon.getWidth() *
//                    ((float) missed.getIntrinsicHeight() /
//                            (float) videoIcon.getHeight()));
//            Bitmap scaled = Bitmap.createScaledBitmap(videoIcon, scaledWidth, scaledHeight, false);
//            videoCall = new BitmapDrawable(context.getResources(), scaled);
//            videoCall.setColorFilter(r.getColor(R.color.dialtacts_secondary_text_color),
//                    PorterDuff.Mode.MULTIPLY);
//
//            iconMargin = r.getDimensionPixelSize(R.dimen.call_log_icon_margin);
        }
    }
}
