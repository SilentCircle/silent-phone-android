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
package com.silentcircle.common.widget;


import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.silentcircle.common.util.ViewUtil;

public class KeyboardNotifierLinearLayout extends LinearLayout{

    @SuppressWarnings("unused")
    private static final String TAG = KeyboardNotifierLinearLayout.class.getSimpleName();

    public interface KeyboardListener {
        public void onKeyboardHeightChanged(boolean isVisible, int keyboardHeight);
    }

    private KeyboardListener mListener;
    private Rect mRect = new Rect();
    private int mKeyboardHeightSent;
    private int mBottomInsetCached = -1;


    public void setListener(KeyboardListener listener) {
        this.mListener = listener;
    }

    public KeyboardNotifierLinearLayout(Context context) {
        super(context);
    }

    public KeyboardNotifierLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyboardNotifierLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public KeyboardNotifierLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mBottomInsetCached == -1) {
            Rect inset = ViewUtil.getViewInset(getRootView());
            mBottomInsetCached = (inset != null) ? inset.bottom : 0;
        }

        notifyKeyboardAppeared(View.MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void notifyKeyboardAppeared(int height) {
        if (mListener == null) {
            return;
        }
        int keyboardHeight = getKeyboardHeight(height);
        if (mListener != null) {
            if (mKeyboardHeightSent == keyboardHeight) {
                return;
            }
            mKeyboardHeightSent = keyboardHeight;
            mListener.onKeyboardHeightChanged(keyboardHeight != 0, keyboardHeight);
        }
    }


    /*
     * This method makes use of getWindowVisibleDisplayFrame() which works nice but is quite
     * costly and performance bad in low-end devices even when scrolling. The advantage of this method
     * is that this view does not need to have its height reduced by the keyboard in order to measure it.
     */
//    public int getKeyboardHeight() {
//        View rootView = getRootView();
//        getWindowVisibleDisplayFrame(mRect);
//        int statusBarHeight = (mRect.top != 0 ? ViewUtil.getStatusBarHeight(getContext()) : 0);
//
//        Rect inset = ViewUtil.getViewInset(rootView);
//        int bottomInset = (inset != null) ? inset.bottom : 0;
//
//        int usableViewHeight = rootView.getHeight() - statusBarHeight - bottomInset;
//        return usableViewHeight - (mRect.bottom - mRect.top);
//    }


    /*
     * This method requires the view's height to me reduced when the keyboard appears. It then calculates the
     * usable view height using the root view and the status bar and navigation bar heights and subtracts
     * those 2 to get the keyboard height.
     */
    public int getKeyboardHeight(int height) {
        View rootView = getRootView();
        int statusBarHeight = ViewUtil.getStatusBarHeight(getContext());

        int usableViewHeight = rootView.getMeasuredHeight() - statusBarHeight - mBottomInsetCached;

        if (rootView.getMeasuredHeight() == 0 || height == 0) {
            return 0;
        }
        int keyboardHeight = usableViewHeight - height;
//        Log.d("notifier", rootView.getHeight() + " "
//                + rootView.getMeasuredHeight() + " " + height + " " + statusBarHeight + " " + mBottomInsetCached);

        // filter out common miscalculations
        return (keyboardHeight <= mBottomInsetCached) ? 0 : keyboardHeight;
    }


    public boolean isKeyboardVisible() {
        return getKeyboardHeight(getMeasuredHeight()) != 0;
    }
}
