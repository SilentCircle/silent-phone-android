/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.silentcircle.common.util;

import android.app.DialogFragment;
import android.content.Context;

import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.contacts.calllognew.ContactInfoHelper;
import com.silentcircle.contacts.calllognew.CallLogAdapter;
import static com.silentcircle.contacts.calllognew.CallLogAdapter.CallFetcher;
import com.silentcircle.contacts.calllognew.CallLogAdapter.CallItemExpandedListener;
import com.silentcircle.contacts.calllognew.CallLogAdapter.OnReportButtonClickListener;

/**
 * Default static binding for various objects.
 */
public class ObjectFactory {

    public static CachedNumberLookupService newCachedNumberLookupService() {
        // no-op
        return null;
    }

    /**
     * Create a new instance of the call log adapter.
     * @param context The context to use.
     * @param callFetcher Instance of call fetcher to use.
     * @param contactInfoHelper Instance of contact info helper class to use.
     * @param isCallLog Is this call log adapter being used on the call log?
     * @return Instance of CallLogAdapter.
     */
    public static CallLogAdapter newCallLogAdapter(Context context,
            CallFetcher callFetcher, ContactInfoHelper contactInfoHelper,
            CallItemExpandedListener callItemExpandedListener,
            OnReportButtonClickListener onReportButtonClickListener, 
            OnPhoneNumberPickerActionListener actionListener, boolean isCallLog) {
        return new CallLogAdapter(context, callFetcher, contactInfoHelper,
                callItemExpandedListener, onReportButtonClickListener, actionListener, isCallLog);
    }

    public static DialogFragment getReportDialogFragment(String number) {
        return null;
    }
}
