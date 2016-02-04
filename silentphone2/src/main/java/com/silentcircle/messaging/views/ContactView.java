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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;

import java.util.HashMap;
import java.util.Map;

public class ContactView extends LinearLayout {

    public static final float CROP_BORDER = 0.2f;

    static class Views {

        public TextView displayName;
        public TextView phoneticName;
        public TextView username;
        public ImageView photo;
        public LinearLayout details;

    }

    private Views views;

    private Map<String, ContactInfoSection> mSections = new HashMap<>();

    public ContactView(Context context) {
        this(context, null);
    }

    public ContactView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public ContactView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected Views getViews() {
        if (views == null) {
            views = new Views();
            views.displayName = (TextView) findViewById(R.id.display_name);
            views.phoneticName = (TextView) findViewById(R.id.phonetic_name);
            views.username = (TextView) findViewById(R.id.username);
            views.photo = (ImageView) findViewById(R.id.contact_avatar);
            views.details = (LinearLayout) findViewById(R.id.contact_details_container);
        }
        return views;
    }

    public void setImage(final Bitmap image) {
        // crop image from top and bottom a bit to show only (vertical) center part
        int width = image.getWidth();
        int height = image.getHeight();

        Bitmap newBitmap = Bitmap.createBitmap(image, 0, (int) (height * CROP_BORDER), width,
                height - (int) (height * CROP_BORDER * 2), null, false);
        getViews().photo.setImageBitmap(newBitmap);
        getViews().photo.setVisibility(View.VISIBLE);
    }

    public void setDisplayName(final String displayName) {
        getViews().displayName.setText(displayName);
    }

    public void setPhoneticName(final String phoneticName) {
        getViews().phoneticName.setText(
                TextUtils.isEmpty(phoneticName) ? null : "(" + phoneticName.trim() + ")");
        getViews().phoneticName.setVisibility(
                TextUtils.isEmpty(phoneticName) ? View.GONE : View.VISIBLE);
    }

    public void addDetail(View view) {
        getViews().details.removeView(view);
        getViews().details.addView(view);
        invalidate();
    }

    public void add(final String sectionName, final String label, final String value) {
        ContactInfoSection section = mSections.get(sectionName);
        if (section == null) {
            section = new ContactInfoSection(getContext());
            section.setSectionName(sectionName);
            mSections.put(sectionName, section);
        }

        section.add(label, value);
        addDetail(section);
    }

    public void clear() {
        Views views = getViews();
        views.displayName.setText(null);
        views.phoneticName.setText(null);
        views.username.setText(null);
        views.photo.setImageBitmap(null);
        views.photo.setVisibility(View.GONE);
        views.details.removeAllViews();
        mSections.clear();
        invalidate();
    }

}
