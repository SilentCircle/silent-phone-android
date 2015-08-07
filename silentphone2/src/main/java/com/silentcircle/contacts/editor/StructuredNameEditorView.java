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
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;

import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.contacts.model.dataitem.StructuredNameDataItem;
import com.silentcircle.contacts.utils.NameConverter;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredName;

import java.util.HashMap;
import java.util.Map;

/**
 * A dedicated editor for structured name.  When the user collapses/expands
 * the structured name, it will reparse or recompose the name, but only
 * if the user has made changes.  This distinction will be particularly
 * obvious if the name has a non-standard structure. Consider this structure:
 * first name="John Doe", family name="".  As long as the user does not change
 * the full name, expand and collapse will preserve this.  However, if the user
 * changes "John Doe" to "Jane Doe" and then expands the view, we will reparse
 * and show first name="Jane", family name="Doe".
 */
public class StructuredNameEditorView extends TextFieldsEditorView {

    private StructuredNameDataItem mSnapshot;
    private boolean mChanged;

    public StructuredNameEditorView(Context context) {
        super(context);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void setValues(DataKind kind, RawContactDelta.ValuesDelta entry, RawContactDelta state, boolean readOnly, ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        if (mSnapshot == null) {
            mSnapshot = (StructuredNameDataItem) DataItem.createFrom(null, new ContentValues(getValues().getCompleteValues()));
            mChanged = entry.isInsert();
        } else {
            mChanged = false;
        }
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        // First save the new value for the column.
        saveValue(column, value);
        mChanged = true;

        // Next make sure the display name and the structured name are synced
        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                rebuildFullName(getValues());
            } else {
                rebuildStructuredName(getValues());
            }
        }

        // Then notify the listener, which will rely on the display and structured names to be
        // synced (in order to provide aggregate suggestions).
        notifyEditorListener();
    }

    @Override
    protected void onOptionalFieldVisibilityChange() {
        if (hasShortAndLongForms()) {
            if (areOptionalFieldsVisible()) {
                switchFromFullNameToStructuredName();
            } else {
                switchFromStructuredNameToFullName();
            }
        }

        super.onOptionalFieldVisibilityChange();
    }

    private void switchFromFullNameToStructuredName() {
        RawContactDelta.ValuesDelta values = getValues();

        if (!mChanged) {
            for (String field : NameConverter.STRUCTURED_NAME_FIELDS) {
                values.put(field, mSnapshot.getContentValues().getAsString(field));
            }
            return;
        }

        String displayName = values.getDisplayName();
        Map<String, String> structuredNameMap = NameConverter.displayNameToStructuredName(
                getContext(), displayName);
        if (!structuredNameMap.isEmpty()) {
            eraseFullName(values);
            for (String field : structuredNameMap.keySet()) {
                values.put(field, structuredNameMap.get(field));
            }
        }

        mSnapshot.getContentValues().clear();
        mSnapshot.getContentValues().putAll(values.getCompleteValues());
        mSnapshot.setDisplayName(displayName);
    }

    private void switchFromStructuredNameToFullName() {
        RawContactDelta.ValuesDelta values = getValues();

        if (!mChanged) {
            values.setDisplayName(mSnapshot.getDisplayName());
            return;
        }

        Map<String, String> structuredNameMap = valuesToStructuredNameMap(values);
        String displayName = NameConverter.structuredNameToDisplayName(getContext(),
                structuredNameMap);
        if (!TextUtils.isEmpty(displayName)) {
            eraseStructuredName(values);
            values.put(StructuredName.DISPLAY_NAME, displayName);
        }

        mSnapshot.getContentValues().clear();
        mSnapshot.setDisplayName(values.getDisplayName());
        for (String field : structuredNameMap.keySet()) {
            mSnapshot.getContentValues().put(field, structuredNameMap.get(field));
        }
    }

    private Map<String, String> valuesToStructuredNameMap(RawContactDelta.ValuesDelta values) {
        Map<String, String> structuredNameMap = new HashMap<String, String>();
        for (String key : NameConverter.STRUCTURED_NAME_FIELDS) {
            structuredNameMap.put(key, values.getAsString(key));
        }
        return structuredNameMap;
    }

    private void eraseFullName(RawContactDelta.ValuesDelta values) {
        values.setDisplayName(null);
    }

    private void rebuildFullName(RawContactDelta.ValuesDelta values) {
        Map<String, String> structuredNameMap = valuesToStructuredNameMap(values);
        String displayName = NameConverter.structuredNameToDisplayName(getContext(),
                structuredNameMap);
        values.setDisplayName(displayName);
    }

    private void eraseStructuredName(RawContactDelta.ValuesDelta values) {
        for (String field : NameConverter.STRUCTURED_NAME_FIELDS) {
            values.putNull(field);
        }
    }

    private void rebuildStructuredName(RawContactDelta.ValuesDelta values) {
        String displayName = values.getDisplayName();
        Map<String, String> structuredNameMap = NameConverter.displayNameToStructuredName(
                getContext(), displayName);
        for (String field : structuredNameMap.keySet()) {
            values.put(field, structuredNameMap.get(field));
        }
    }

    private static void appendQueryParameter(Uri.Builder builder, String field, String value) {
        if (!TextUtils.isEmpty(value)) {
            builder.appendQueryParameter(field, value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.mChanged = mChanged;
        state.mSnapshot = mSnapshot.getContentValues();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.mSuperState);

        DataItem di = DataItem.createFrom(null, ss.mSnapshot);
        if (di instanceof StructuredNameDataItem) {
            mChanged = ss.mChanged;
            mSnapshot = (StructuredNameDataItem)di;
        }
    }

    private static class SavedState implements Parcelable {
        public boolean mChanged;
        public ContentValues mSnapshot;
        public Parcelable mSuperState;

        SavedState(Parcelable superState) {
            mSuperState = superState;
        }

        private SavedState(Parcel in) {
            ClassLoader loader = ((Object)this).getClass().getClassLoader();
            mSuperState = in.readParcelable(loader);

            mChanged = in.readInt() != 0;
            mSnapshot = in.readParcelable(loader);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeParcelable(mSuperState, 0);

            out.writeInt(mChanged ? 1 : 0);
            out.writeParcelable(mSnapshot, 0);
        }

        @SuppressWarnings({"unused"})
        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
