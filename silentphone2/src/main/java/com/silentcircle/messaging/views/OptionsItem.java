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

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

/**
 * Widget for conversation options menu item.
 */
public class OptionsItem extends RelativeLayout implements Checkable {

    private static final int[] ATTRIBUTES = {android.R.attr.textColor,
            android.R.attr.text};

    private TextView mItemText;
    private TextView mItemDescription;
    private ImageView mImage;
    private CheckBox mCheckBox;

    private ViewGroup mContainer;
    private CharSequence mText;
    private CharSequence mDescription;
    private Drawable mImageDrawable;
    private Drawable mImageDrawableChecked;
    private boolean mIsCheckable;
    private int mPaddingLeft;
    private int mPaddingTop;
    private int mPaddingRight;
    private int mPaddingBottom;
    private int mImageMarginLeft;
    private int mImageMarginRight;
    private int mCheckboxMarginLeft;
    private int mCheckboxMarginRight;
    private int mDescriptionMarginTop;
    private int mDescriptionMarginLeft;
    private int mDescriptionMarginRight;
    private int mTextSize;
    private int mDescriptionSize;
    private boolean mTrackingGesture;

    private boolean mChecked;

    private ColorStateList mTextColor;
    private ColorStateList mDescriptionColor;
    private int mTintColor;

    private OnCheckedChangeListener mOnCheckedChangeListener;
    private OnClickListener mOnClickListener;
    private PerformClick performClick = new PerformClick();

    /**
     * Interface definition for a callback to be invoked when the checked state
     * of a compound button changed.
     */
    public interface OnCheckedChangeListener {
        /**
         * Called when the checked state of a options item has changed.
         *
         * @param optionsItemView The item view whose state has changed.
         * @param isChecked  The new checked state of buttonView.
         */
        void onCheckedChanged(OptionsItem optionsItemView, boolean isChecked);
    }

    @SuppressWarnings("ResourceType")
    public OptionsItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, ATTRIBUTES);
        mTextColor = typedArray.getColorStateList(0);
        mText = typedArray.getText(1);
        typedArray.recycle();

        typedArray = context.obtainStyledAttributes(attrs,
                com.silentcircle.silentphone2.R.styleable.OptionsItem, 0, 0);
        if (TextUtils.isEmpty(mText)) {
            mText = typedArray.getText(R.styleable.OptionsItem_text);
        }
        mDescription = typedArray.getText(R.styleable.OptionsItem_description);
        mImageDrawable = typedArray.getDrawable(com.silentcircle.silentphone2.R.styleable.OptionsItem_src);
        mImageDrawableChecked = typedArray.getDrawable(com.silentcircle.silentphone2.R.styleable.OptionsItem_srcChecked);
        mIsCheckable = typedArray.getBoolean(com.silentcircle.silentphone2.R.styleable.OptionsItem_isCheckable, true);
        mImageMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginLeft, 0);
        mImageMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginRight, 0);
        mCheckboxMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_checkableMarginLeft, 0);
        mCheckboxMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_checkableMarginRight, 0);
        mDescriptionMarginTop = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginTop, 0);
        mDescriptionMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginLeft, 0);
        mDescriptionMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginRight, 0);
        mTextSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_textSize, 0);
        mDescriptionSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionSize, 0);
        mDescriptionColor = typedArray.getColorStateList(R.styleable.OptionsItem_descriptionColor);
        mTintColor = typedArray.getColor(R.styleable.OptionsItem_tintColor, 0);
        mPaddingLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingLeft, 0);
        mPaddingTop = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingTop, 0);
        mPaddingRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingRight, 0);
        mPaddingBottom = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingBottom, 0);
        typedArray.recycle();

        inflate(context, R.layout.widget_options_item, this);
        mContainer = (ViewGroup) findViewById(R.id.options_item_container);
        mImage = (ImageView) findViewById(R.id.widget_optitem_image);
        mCheckBox = (CheckBox) findViewById(R.id.widget_optitem_checkbox);
        mItemText = (TextView) findViewById(R.id.widget_optitem_text);
        mItemDescription = (TextView) findViewById(R.id.widget_optitem_description);
        mImage.setImageDrawable(mImageDrawable);

        mChecked = false;
    }

    public OptionsItem(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public OptionsItem(Context context) {
        this(context, null);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContainer.setPadding(mPaddingLeft, mPaddingTop, mPaddingRight, mPaddingBottom);

        mCheckBox.setVisibility(mIsCheckable ? View.VISIBLE : View.GONE);
        setText(mText);
        setDescription(mDescription);

        mItemText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
        mItemDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDescriptionSize);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mImage.getLayoutParams();
        params.setMargins(mImageMarginLeft, params.topMargin, mImageMarginRight, params.bottomMargin);
        params = (LinearLayout.LayoutParams) mCheckBox.getLayoutParams();
        params.setMargins(mCheckboxMarginLeft, params.topMargin, mCheckboxMarginRight, params.bottomMargin);

        RelativeLayout.LayoutParams rparams = (RelativeLayout.LayoutParams) mItemDescription.getLayoutParams();
        rparams.setMargins(mDescriptionMarginLeft, mDescriptionMarginTop, mDescriptionMarginRight, rparams.bottomMargin);

        if (mTextColor != null) {
            mItemText.setTextColor(mTextColor);
            mItemText.setLinkTextColor(mTextColor);
            mItemText.setAutoLinkMask(Linkify.ALL);
        }
        if (mDescriptionColor != null) {
            mItemDescription.setTextColor(mDescriptionColor);
        }
        if (mTintColor != 0) {
            mImage.setColorFilter(mTintColor, android.graphics.PorterDuff.Mode.MULTIPLY);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (mChecked != checked) {
            mChecked = checked;
            if (mOnCheckedChangeListener != null) {
                mOnCheckedChangeListener.onCheckedChanged(this, mChecked);
            }
        }
        mCheckBox.setChecked(checked);
        mImage.setImageDrawable(checked ? mImageDrawableChecked : mImageDrawable);
    }

    @Override
    public boolean isChecked() {
        return mIsCheckable ? mChecked : false;
    }

    @Override
    public void toggle() {
        setChecked(!mChecked);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mItemText.setEnabled(enabled);
        mItemDescription.setEnabled(enabled);
        mImage.setEnabled(enabled);
        mCheckBox.setEnabled(enabled);
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
        if (!isEnabled()) {
            return false;
        }

        final int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            mTrackingGesture = true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (mTrackingGesture) {
                getHandler().post(performClick);
            }
        }
        else if (action == MotionEvent.ACTION_MOVE) {
            if (!isPointInView(x, y)) {
                mTrackingGesture = false;
            }
        }
        return super.dispatchTouchEvent(event);
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isEnabled()) {
            return false;
        }

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

    boolean isPointInView(float localX, float localY) {
        return isPointInView(localX, localY, 0);
    }

    public boolean isPointInView(float localX, float localY, float slop) {
        return localX >= -slop && localY >= -slop && localX < ((getRight() - getLeft()) + slop) &&
                localY < ((getBottom() - getTop()) + slop);
    }

    private final class PerformClick implements Runnable {
        @Override
        public void run() {
            performClick();
        }
    }

    @Override
    public boolean performClick() {
        if (mIsCheckable) {
            toggle();
        }
        playSoundEffect(SoundEffectConstants.CLICK);
        if (mOnClickListener != null) {
            mOnClickListener.onClick(this);
        }
        return super.performClick();
    }

}
