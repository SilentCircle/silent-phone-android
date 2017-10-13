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
package com.silentcircle.silentphone2.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
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

    private static final int[] ATTRIBUTES = {
            android.R.attr.textStyle,
            android.R.attr.textAppearance,
            android.R.attr.textColor,
            android.R.attr.checked,
            android.R.attr.text};

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
    private int mCheckboxMarginLeft;
    private int mCheckboxMarginRight;
    private int mDescriptionMarginTop;
    private int mDescriptionMarginLeft;
    private int mDescriptionMarginRight;
    private int mTextSize;
    private int mDescriptionSize;
    private int mTextStyleIndex = Typeface.BOLD;
    private int mTextAppearance;
    private int mDescriptionStyleIndex = Typeface.NORMAL;
    private int mDescriptionAppearance;
    private boolean mTrackingGesture;

    private boolean mChecked;

    private ColorStateList mTextColor;
    private ColorStateList mDescriptionColor;

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
         * @param settingsItem The item view whose state has changed.
         * @param isChecked  The new checked state of buttonView.
         */
        void onCheckedChanged(SettingsItem settingsItem, boolean isChecked);
    }

    @SuppressWarnings("ResourceType")
    public SettingsItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, ATTRIBUTES);
        mTextStyleIndex = typedArray.getInt(0, Typeface.NORMAL);
        mTextAppearance = typedArray.getResourceId(1, 0);
        mTextColor = typedArray.getColorStateList(2);
        mChecked = typedArray.getBoolean(3, false);
        mText = typedArray.getText(4);
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
        mDescriptionStyleIndex = typedArray.getInt(R.styleable.OptionsItem_descriptionStyle, Typeface.NORMAL);
        mDescriptionAppearance = typedArray.getResourceId(R.styleable.OptionsItem_descriptionAppearance, 0);
        mTextSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_textSize, 0);
        mDescriptionSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionSize, 0);
        mDescriptionColor = typedArray.getColorStateList(R.styleable.OptionsItem_descriptionColor);
        typedArray.recycle();

        inflate(context, R.layout.widget_settings_item, this);
        mImage = (ImageView) findViewById(R.id.widget_settings_item_image);
        mSwitch = (SwitchCompat) findViewById(R.id.widget_settings_item_switch);
        mItemText = (TextView) findViewById(R.id.widget_settings_item_text);
        mItemDescription = (TextView) findViewById(R.id.widget_settings_item_description);
        mImage.setImageDrawable(mImageDrawable);
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
        mSwitch.setChecked(mChecked);
        setText(mText);
        setDescription(mDescription);

        if (mTextAppearance != 0) {
            mItemText.setTextAppearance(getContext(), mTextAppearance);
        }
        mItemText.setTypeface(mItemText.getTypeface(), mTextStyleIndex);
        mItemText.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);

        if (mDescriptionAppearance != 0) {
            mItemDescription.setTextAppearance(getContext(), mDescriptionAppearance);
        }
        mItemDescription.setTypeface(mItemDescription.getTypeface(), mDescriptionStyleIndex);
        mItemDescription.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDescriptionSize);

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mImage.getLayoutParams();
        params.setMargins(mImageMarginLeft, params.topMargin, mImageMarginRight, params.bottomMargin);
        params = (LinearLayout.LayoutParams) mSwitch.getLayoutParams();
        params.setMargins(mCheckboxMarginLeft, params.topMargin, mCheckboxMarginRight, params.bottomMargin);

        RelativeLayout.LayoutParams rparams = (RelativeLayout.LayoutParams) mItemDescription.getLayoutParams();
        rparams.setMargins(mDescriptionMarginLeft, mDescriptionMarginTop, mDescriptionMarginRight, rparams.bottomMargin);

        if (mTextColor != null) {
            mItemText.setTextColor(mTextColor);
        }
        if (mDescriptionColor != null) {
            mItemDescription.setTextColor(mDescriptionColor);
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
        mSwitch.setChecked(checked);
        mImage.setImageDrawable(checked ? mImageDrawableChecked : mImageDrawable);
    }

    @Override
    public boolean isChecked() {
        return mChecked;
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
        mSwitch.setEnabled(enabled);
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

    @Override
    @SuppressWarnings("unchecked")
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SparseArray states = new SparseArray();
        /*
         * Only switch state and item state interesting at the moment,
         * save those two.
         */
        mSwitch.saveHierarchyState(states);
        states.put(R.id.settings_item_checked, isChecked());
        return new SavedState(superState, states);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Parcelable state) {
        try {
            SavedState savedState = (SavedState) state;
            super.onRestoreInstanceState(savedState.getSuperState());
            SparseArray states = savedState.getSavedStates();
            mSwitch.restoreHierarchyState(states);
            setChecked((boolean) states.get(R.id.settings_item_checked, false));
        } catch (Throwable t) {
            Log.w("SettingsItem", "Could not restore from saved state for item #" + getId());
        }
    }

    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    static class SavedState extends BaseSavedState {

        private SparseArray mStates;

        public SavedState(Parcelable superState, SparseArray states) {
            super(superState);
            mStates = states.clone();
        }


        private SavedState(Parcel in, ClassLoader classLoader) {
            super(in);
            mStates = in.readSparseArray(classLoader);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeSparseArray(mStates);
        }

        public SparseArray getSavedStates() {
            return mStates;
        }

        public static final ClassLoaderCreator<SavedState> CREATOR
                = new ClassLoaderCreator<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return createFromParcel(in, ClassLoader.getSystemClassLoader() /* or null */);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}
