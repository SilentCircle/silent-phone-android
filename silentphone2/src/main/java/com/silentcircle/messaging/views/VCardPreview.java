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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

/**
 *
 */
public class VCardPreview extends LinearLayout {

    private ImageView mImage;
    private TextView mText;

    public VCardPreview(Context context) {
        this(context, null);
    }

    public VCardPreview(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VCardPreview(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.messaging_generic_vcard_preview, this);
        initializeViews();
    }

    public VCardPreview(Context context, Drawable contactPhoto, String displayName) {
        super(context, null, 0);
        if (contactPhoto != null && !TextUtils.isEmpty(displayName)) {
            inflate(context, R.layout.messaging_vcard_preview, this);
            initializeViews();
            setImage(contactPhoto);
            setText(displayName);
        }
        else {
            inflate(context, R.layout.messaging_generic_vcard_preview, this);
            initializeViews();
        }
    }

    protected void initializeViews() {
        mImage = (ImageView) findViewById(R.id.vcard_preview_avatar);
        mText = (TextView) findViewById(R.id.vcard_preview_name);
    }

    public void setText(final CharSequence text) {
        mText.setText(text);
    }

    public void setImage(final Drawable image) {
        CircleClipDrawable clipDrawable = new CircleClipDrawable(image);
        mImage.setImageDrawable(clipDrawable);
    }
}
