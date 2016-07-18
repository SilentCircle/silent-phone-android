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

package com.silentcircle.silentphone2.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.support.v7.widget.SwitchCompat;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.messaging.views.RelativeLayout;
import com.silentcircle.silentphone2.R;

/**
 * Widget for a settings item with text, smaller description text and switch component.
 */
public class SettingsItem extends RelativeLayout implements Checkable {

    private TextView mItemText;
    private TextView mItemDescription;
    private ImageView mImage;
    private SwitchCompat mSwitch;

    private CharSequence mText;
    private CharSequence mDescription;
    private Drawable mImageDrawable;
    private Drawable mImageDrawableChecked;
    private boolean mIsCheckable;
    private int mImageMarginLeft;
    private int mImageMarginRight;
    private int mCheckboxMargin;
    private int mDescriptionMarginTop;
    private int mDescriptionMarginLeft;
    private int mTextSize;
    private int mDescriptionSize;

    private boolean mChecked;

    private int mTextColor;
    private int mDescriptionColor;

    private OnCheckedChangeListener mOnCheckedChangeListener;
    private OnClickListener mOnClickListener;

    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a options item has changed.
         *
         * @param settingsItem The item view whose state has changed.
         * @param isChecked  The new checked state of buttonView.
         */
        void onCheckedChanged(SettingsItem settingsItem, boolean isChecked);
    }


    public SettingsItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                com.silentcircle.silentphone2.R.styleable.OptionsItem, 0, 0);
        mText = typedArray.getText(R.styleable.OptionsItem_text);
        mDescription = typedArray.getText(R.styleable.OptionsItem_description);
        mImageDrawable = typedArray.getDrawable(com.silentcircle.silentphone2.R.styleable.OptionsItem_src);
        mImageDrawableChecked = typedArray.getDrawable(com.silentcircle.silentphone2.R.styleable.OptionsItem_srcChecked);
        mIsCheckable = typedArray.getBoolean(com.silentcircle.silentphone2.R.styleable.OptionsItem_isCheckable, true);
        mImageMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginLeft, 0);
        mImageMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginRight, 0);
        mCheckboxMargin = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_checkableMargin, 0);
        mDescriptionMarginTop = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginTop, 0);
        mDescriptionMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginLeft, 0);
        mTextSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_textSize, 0);
        mDescriptionSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionSize, 0);
        mTextColor = typedArray.getColor(R.styleable.OptionsItem_textColor, 0);
        mDescriptionColor = typedArray.getColor(R.styleable.OptionsItem_descriptionColor, 0);
        typedArray.recycle();

        inflate(context, R.layout.widget_settings_item, this);
        mImage = (ImageView) findViewById(R.id.widget_settings_item_image);
        mSwitch = (SwitchCompat) findViewById(R.id.widget_settings_item_switch);
        mItemText = (TextView) findViewById(R.id.widget_settings_item_text);
        mItemDescription = (TextView) findViewById(R.id.widget_settings_item_description);
        mImage.setImageDrawable(mImageDrawable);

        mChecked = false;
    }

    public SettingsItem(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public SettingsItem(Context context) {
        this(context, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSwitch.setVisibility(mIsCheckable ? View.VISIBLE : View.GONE);
        setText(mText);
        setDescription(mDescription);

        mItemText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        mItemDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDescriptionSize);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mImage.getLayoutParams();
        params.setMargins(mImageMarginLeft, params.topMargin, mImageMarginRight, params.bottomMargin);
        params = (LinearLayout.LayoutParams) mSwitch.getLayoutParams();
        params.setMargins(mCheckboxMargin, params.topMargin, params.rightMargin, params.bottomMargin);

        RelativeLayout.LayoutParams rparams = (RelativeLayout.LayoutParams) mItemDescription.getLayoutParams();
        rparams.setMargins(mDescriptionMarginLeft, mDescriptionMarginTop, rparams.rightMargin, rparams.bottomMargin);

        if (mTextColor != 0) {
            mItemText.setTextColor(mTextColor);
        }
        if (mDescriptionColor != 0) {
            mItemDescription.setTextColor(mDescriptionColor);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            mSwitch.setChecked(checked);
            mImage.setImageDrawable(checked ? mImageDrawableChecked : mImageDrawable);
            if (mOnCheckedChangeListener != null) {
                mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return mIsCheckable ? mChecked : false;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    public void setText(final CharSequence text) {
        mItemText.setText(text);
    }

    public void setDescription(final CharSequence text) {
        mItemDescription.setText(text);
        mItemDescription.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    /**
     * Register a callback to be invoked when the checked state of this button
     * changes.
     *
     * @param listener the callback to call on checked state change
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mOnCheckedChangeListener = listener;
    }

    /**
     * Register a callback to be invoked when this view is clicked.
     *
     * @param listener The callback that will run
     *
     * @see #setClickable(boolean)
     */
    public void setOnClickListener(OnClickListener listener) {
        mOnClickListener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            if (mOnClickListener != null) {
                mOnClickListener.onClick(this);
            }
        }
        return super.dispatchTouchEvent(event);
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            performClick();
            if (mOnClickListener != null) {
                mOnClickListener.onClick(this);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean performClick() {
        if (mIsCheckable) {
            toggle();
        }
        playSoundEffect(SoundEffectConstants.CLICK);
        return super.performClick();
    }

}
