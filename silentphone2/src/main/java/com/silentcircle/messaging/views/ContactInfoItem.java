/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.silentcircle.silentphone2.R;

public class ContactInfoItem extends RelativeLayout {

    private static final int[] ATTRIBUTES = {
            android.R.attr.textStyle,
            android.R.attr.textAppearance,
            android.R.attr.textColor,
            android.R.attr.text};

    private android.widget.ImageView mImage;
    private android.widget.TextView mLabel;
    private android.widget.TextView mInfo;

    private CharSequence mText;
    private CharSequence mDescription;
    private Drawable mImageDrawable;
    private int mPaddingLeft;
    private int mPaddingTop;
    private int mPaddingRight;
    private int mPaddingBottom;
    private int mImageMarginLeft;
    private int mImageMarginRight;
    private int mDescriptionMarginTop;
    private int mDescriptionMarginLeft;
    private int mDescriptionMarginRight;
    private int mTextSize;
    private int mDescriptionSize;
    private int mTextStyleIndex = Typeface.BOLD;
    private int mTextAppearance;
    private int mDescriptionStyleIndex = Typeface.NORMAL;
    private int mDescriptionAppearance;

    private ColorStateList mTextColor;
    private ColorStateList mDescriptionColor;

    @SuppressWarnings("ResourceType")
    public ContactInfoItem(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray typedArray = context.obtainStyledAttributes(attrs, ATTRIBUTES);
        mTextStyleIndex = typedArray.getInt(0, Typeface.NORMAL);
        mTextAppearance = typedArray.getResourceId(1, 0);
        mTextColor = typedArray.getColorStateList(2);
        mText = typedArray.getText(3);
        typedArray.recycle();

        typedArray = context.obtainStyledAttributes(attrs,
                com.silentcircle.silentphone2.R.styleable.OptionsItem, 0, 0);
        if (TextUtils.isEmpty(mText)) {
            mText = typedArray.getText(R.styleable.OptionsItem_text);
        }
        mDescription = typedArray.getText(R.styleable.OptionsItem_description);
        mImageDrawable = typedArray.getDrawable(com.silentcircle.silentphone2.R.styleable.OptionsItem_src);
        mImageMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginLeft, 0);
        mImageMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_imageMarginRight, 0);
        mDescriptionMarginTop = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginTop, 0);
        mDescriptionMarginLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginLeft, 0);
        mDescriptionMarginRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionMarginRight, 0);
        mDescriptionAppearance = typedArray.getResourceId(R.styleable.OptionsItem_descriptionAppearance, 0);
        mDescriptionStyleIndex = typedArray.getInt(R.styleable.OptionsItem_descriptionStyle, Typeface.NORMAL);
        mTextSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_textSize, 0);
        mDescriptionSize = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_descriptionSize, 0);
        mDescriptionColor = typedArray.getColorStateList(R.styleable.OptionsItem_descriptionColor);
        mPaddingLeft = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingLeft, 0);
        mPaddingTop = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingTop, 0);
        mPaddingRight = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingRight, 0);
        mPaddingBottom = typedArray.getDimensionPixelSize(R.styleable.OptionsItem_containerPaddingBottom, 0);
        typedArray.recycle();

        inflate(context, R.layout.widget_contact_info_item, this);
        mImage = (android.widget.ImageView) findViewById(R.id.widget_contact_info_icon);
        mLabel = (android.widget.TextView) findViewById(R.id.widget_contact_info_label);
        mInfo = (android.widget.TextView) findViewById(R.id.widget_contact_info_text);
    }

    public ContactInfoItem(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ContactInfoItem(Context context) {
        this(context, null);
    }

    public void setText(final CharSequence label) {
        mLabel.setText(label);
        mLabel.setVisibility(TextUtils.isEmpty(label) ? View.GONE : View.VISIBLE);
    }

    public void setDescription(final CharSequence description) {
        mInfo.setText(description);
        mInfo.setVisibility(TextUtils.isEmpty(description) ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (mTextAppearance != 0) {
            mLabel.setTextAppearance(getContext(), mTextAppearance);
        }
        mLabel.setTypeface(mLabel.getTypeface(), mTextStyleIndex);
        mLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);

        if (mDescriptionAppearance != 0) {
            mInfo.setTextAppearance(getContext(), mDescriptionAppearance);
        }
        mInfo.setTypeface(mInfo.getTypeface(), mDescriptionStyleIndex);
        mInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDescriptionSize);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mImage.getLayoutParams();
        params.setMargins(mImageMarginLeft, params.topMargin, mImageMarginRight, params.bottomMargin);

        LinearLayout.LayoutParams lparams = (LinearLayout.LayoutParams) mInfo.getLayoutParams();
        lparams.setMargins(mDescriptionMarginLeft, mDescriptionMarginTop, mDescriptionMarginRight,
                lparams.bottomMargin);

        if (mTextColor != null) {
            mLabel.setTextColor(mTextColor);
        }
        if (mDescriptionColor != null) {
            mInfo.setTextColor(mDescriptionColor);
        }

        mImage.setImageDrawable(mImageDrawable);
        setText(mText);
        setDescription(mDescription);

        mInfo.setTextIsSelectable(true);
        mInfo.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ClipboardManager manager =
                        (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText(null, ((android.widget.TextView) v).getText()));
                Toast.makeText(v.getContext(),
                        R.string.toast_copied_to_clipboard,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }
}
