/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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
 * This  implementation is edited version of original Android sources.
 */

/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.silentcircle.contacts.editor;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.silentcircle.silentphone2.R;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.account.AccountType;

/**
 * Base view that provides common code for the editor interaction for a specific
 * RawContact represented through an {@link com.silentcircle.contacts.model.RawContactDelta}.
 * <p>
 * Internal updates are performed against {@link com.silentcircle.contacts.model.RawContactDelta.ValuesDelta} so that the
 * source { @link RawContact} can be swapped out. Any state-based changes, such as
 * adding { @link Data} rows or changing {@link com.silentcircle.contacts.model.account.AccountType.EditType}, are performed through
 * {@link com.silentcircle.contacts.model.RawContactModifier} to ensure that {@link com.silentcircle.contacts.model.account.AccountType} are enforced.
 */
public abstract class BaseRawContactEditorView extends LinearLayout {

    private PhotoEditorView mPhoto;
    private boolean mHasPhotoEditor = false;

    private View mBody;
    private View mDivider;
    
    protected Context mContext;

    private boolean mExpanded = true;

    public BaseRawContactEditorView(Context context) {
        super(context);
        mContext = context;
    }

    public BaseRawContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBody = findViewById(R.id.body);
        mDivider = findViewById(R.id.divider);

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhoto.setEnabled(isEnabled());
    }

    public void setGroupMetaData(Cursor groupMetaData) {
    }

    /**
     * Assign the given {@link android.graphics.Bitmap} to the internal {@link PhotoEditorView}
     * for the {@link com.silentcircle.contacts.model.RawContactDelta} currently being edited.
     */
    public void setPhotoBitmap(Bitmap bitmap) {
        mPhoto.setPhotoBitmap(bitmap);
    }

    protected void setHasPhotoEditor(boolean hasPhotoEditor) {
        mHasPhotoEditor = hasPhotoEditor;
        mPhoto.setVisibility(hasPhotoEditor ? View.VISIBLE : View.GONE);
    }

    /**
     * Return true if the current { @link RawContacts} supports { @link Photo},
     * which means that { @link PhotoEditorView} is enabled.
     */
    public boolean hasPhotoEditor() {
        return mHasPhotoEditor;
    }

    /**
     * Return true if internal {@link PhotoEditorView} has a { @link Photo} set.
     */
    public boolean hasSetPhoto() {
        return mPhoto.hasSetPhoto();
    }

    public PhotoEditorView getPhotoEditor() {
        return mPhoto;
    }

    /**
     * @return the RawContact ID that this editor is editing.
     */
    public abstract long getRawContactId();

    /**
     * Set the internal state for this view, given a current
     * {@link com.silentcircle.contacts.model.RawContactDelta} state and the {@link com.silentcircle.contacts.model.account.AccountType} that
     * apply to that state.
     */
    public abstract void setState(RawContactDelta state, AccountType source, ViewIdGenerator vig, boolean isProfile);

    /* package */ void setExpanded(boolean value) {
        // only allow collapsing if we are one of several children
        final boolean newValue;
        if (getParent() instanceof ViewGroup && ((ViewGroup) getParent()).getChildCount() == 1) {
            newValue = true;
        } else {
            newValue = value;
        }

        if (newValue == mExpanded) return;
        mExpanded = newValue;
        mBody.setVisibility(newValue ? View.VISIBLE : View.GONE);
        mDivider.setVisibility(newValue ? View.GONE : View.VISIBLE);
    }
}
