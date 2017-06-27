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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

/**
 * Widget to display request to verify call security with other party.
 */
public class VerifySasWidget extends LinearLayout implements View.OnClickListener {

    public interface OnSasVerifyClickListener {

        void onSasVerifyClick(View view);
    }

    private CharSequence mSasPhrase;

    private TextView mVerifyLabel;
    private TextView mSecInfo;
    private TextView mSasText;
    private TextView mUserName;
    private Button mButtonVerifySas;
    private ViewGroup mButtonVerifySasWithDr;

    private OnSasVerifyClickListener mOnSasVerifyClickListener;

    public VerifySasWidget(Context context) {
        this(context, null);
    }

    public VerifySasWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VerifySasWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.widget_incall_verify_sas, this);

        mSasText = (TextView) findViewById(R.id.sas_text);
        mButtonVerifySas = (Button) findViewById(R.id.btn_verify_sas);
        mButtonVerifySasWithDr = (ViewGroup) findViewById(R.id.btn_verify_sas_with_dr);
        mVerifyLabel = (TextView) findViewById(R.id.verify_label);
        mSecInfo = (TextView) findViewById(R.id.sec_info);
        mUserName = (TextView) findViewById(R.id.username);

        mButtonVerifySas.setOnClickListener(this);
        mButtonVerifySasWithDr.setOnClickListener(this);

        // paddings are ignored with this
        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
        int paddingLeft = mButtonVerifySas.getPaddingLeft();
        int paddingTop = mButtonVerifySas.getPaddingTop();
        int paddingRight = mButtonVerifySas.getPaddingRight();
        int paddingBottom = mButtonVerifySas.getPaddingBottom();
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.bg_button);
        background = DrawableCompat.wrap(background);
        DrawableCompat.setTint(background, ContextCompat.getColor(getContext(),
                R.color.sc_ng_background_4));
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);
        mButtonVerifySas.setBackground(background);
        mButtonVerifySas.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    public void setSasPhrase(@Nullable CharSequence sasPhrase) {
        mSasPhrase = sasPhrase;
        mSasText.setText(sasPhrase);
    }

    public CharSequence getSasPhrase() {
        return mSasPhrase;
    }

    public void setUserName(@Nullable CharSequence userName) {
        mUserName.setText(userName);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_verify_sas || v.getId() == R.id.btn_verify_sas_with_dr) {
            if (mOnSasVerifyClickListener != null) {
                mOnSasVerifyClickListener.onSasVerifyClick(mButtonVerifySas);
            }
        }
    }

    public void setOnSasVerifyClickListener(OnSasVerifyClickListener onSasVerifyClickListener) {
        mOnSasVerifyClickListener = onSasVerifyClickListener;
    }

    public void setDataRetentionState(boolean retentionState) {
        mButtonVerifySas.setVisibility(retentionState ? View.GONE : View.VISIBLE);
        mButtonVerifySasWithDr.setVisibility(retentionState ? View.VISIBLE : View.GONE);
    }
}
