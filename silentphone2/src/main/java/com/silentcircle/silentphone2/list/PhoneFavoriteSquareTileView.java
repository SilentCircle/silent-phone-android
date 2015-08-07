/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.silentphone2.list;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.silentcontacts2.ScContactsContract.QuickContact;
import com.silentcircle.silentphone2.R;

/**
 * Displays the contact's picture overlaid with their name and number type in a tile.
 */
public class PhoneFavoriteSquareTileView extends PhoneFavoriteTileView {
    @SuppressWarnings("unused")
    private static final String TAG = PhoneFavoriteSquareTileView.class.getSimpleName();

    private final float mHeightToWidthRatio;

    private ImageButton mSecondaryButton;

    private ContactEntry mContactEntry;

    public PhoneFavoriteSquareTileView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHeightToWidthRatio = getResources().getFraction(R.fraction.contact_tile_height_to_width_ratio, 1, 1);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onFinishInflate() {
        super.onFinishInflate();
        final TextView nameView = (TextView) findViewById(R.id.contact_tile_name);
        final TextView phoneTypeView = (TextView) findViewById(R.id.contact_tile_phone_type);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            nameView.setElegantTextHeight(false);
            phoneTypeView.setElegantTextHeight(false);
        }
        mSecondaryButton = (ImageButton) findViewById(R.id.contact_tile_secondary_button);
    }

    @Override
    protected int getApproximateImageSize() {
        // The picture is the full size of the tile (minus some padding, but we can be generous)
        return getWidth();
    }

    private void launchQuickContact() {
        QuickContact.showQuickContact(getContext(), PhoneFavoriteSquareTileView.this,
                getLookupUri(), QuickContact.MODE_LARGE, null);
    }

    @Override
    public void loadFromContact(ContactEntry entry) {
        super.loadFromContact(entry);
        if (entry != null) {
            mSecondaryButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchQuickContact();
                }
            });
        }
        mContactEntry = entry;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = (int) (mHeightToWidthRatio * width);
        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                    );
        }
        setMeasuredDimension(width, height);
    }

    public ContactEntry getContactEntry() {
        return mContactEntry;
    }
}
