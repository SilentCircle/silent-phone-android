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

package com.silentcircle.contacts.preference;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentphone2.R;

/**
 * Manages user preferences for contacts.
 */
public final class ContactsPreferences extends ContentObserver {

    public static final String PREF_DISPLAY_ONLY_PHONES = "only_phones";
    public static final boolean PREF_DISPLAY_ONLY_PHONES_DEFAULT = false;

    private Context mContext;
    private int mSortOrder = -1;
    private int mDisplayOrder = -1;
    private ChangeListener mListener = null;
    private Handler mHandler;

    public ContactsPreferences(Context context) {
        super(null);
        mContext = context;
        mHandler = new Handler();
    }

    public boolean isSortOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_sort_order_user_changeable);
    }

    public int getDefaultSortOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_sort_order_primary)) {
            return ScContactsContract.Preferences.SORT_ORDER_PRIMARY;
        }
        else {
            return ScContactsContract.Preferences.SORT_ORDER_ALTERNATIVE;
        }
    }

    public int getSortOrder() {
        if (!isSortOrderUserChangeable()) {
            return getDefaultSortOrder();
        }

        if (mSortOrder == -1) {
            try {
                mSortOrder = Settings.System.getInt(mContext.getContentResolver(),
                        ScContactsContract.Preferences.SORT_ORDER);
            } catch (SettingNotFoundException e) {
                mSortOrder = getDefaultSortOrder();
            }
        }
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        Settings.System.putInt(mContext.getContentResolver(), ScContactsContract.Preferences.SORT_ORDER, sortOrder);
    }

    public boolean isDisplayOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_display_order_user_changeable);
    }

    public int getDefaultDisplayOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_display_order_primary)) {
            return ScContactsContract.Preferences.DISPLAY_ORDER_PRIMARY;
        }
        else {
            return ScContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE;
        }
    }

    public int getDisplayOrder() {
        if (!isDisplayOrderUserChangeable()) {
            return getDefaultDisplayOrder();
        }

        if (mDisplayOrder == -1) {
            try {
                mDisplayOrder = Settings.System.getInt(mContext.getContentResolver(),
                        ScContactsContract.Preferences.DISPLAY_ORDER);
            } catch (SettingNotFoundException e) {
                mDisplayOrder = getDefaultDisplayOrder();
            }
        }
        return mDisplayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        Settings.System.putInt(mContext.getContentResolver(),
                ScContactsContract.Preferences.DISPLAY_ORDER, displayOrder);
    }

    public void registerChangeListener(ChangeListener listener) {
        if (mListener != null)
            unregisterChangeListener();

        mListener = listener;

        // Reset preferences to "unknown" because they may have changed while the
        // observer was unregistered.
        mDisplayOrder = -1;
        mSortOrder = -1;

        final ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver
                .registerContentObserver(Settings.System.getUriFor(ScContactsContract.Preferences.SORT_ORDER), false, this);
        contentResolver.registerContentObserver(Settings.System.getUriFor(ScContactsContract.Preferences.DISPLAY_ORDER), false,
                this);
    }

    public void unregisterChangeListener() {
        if (mListener != null) {
            mContext.getContentResolver().unregisterContentObserver(this);
            mListener = null;
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        // This notification is not sent on the Ui thread. Use the previously created Handler
        // to switch to the Ui thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mSortOrder = -1;
                mDisplayOrder = -1;
                if (mListener != null) mListener.onChange();
            }
        });
    }

    public interface ChangeListener {
        void onChange();
    }
}
