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

package com.silentcircle.contacts.utils;

import android.content.Context;
import android.os.AsyncTask;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.widget.TextView;

import com.silentcircle.contacts.ContactsUtils;

public final class PhoneNumberFormatter {
    private PhoneNumberFormatter() {}

    /**
     * Load {@link TextWatcherLoadAsyncTask} in a worker thread and set it to a {@link android.widget.TextView}.
     */
    private static class TextWatcherLoadAsyncTask extends AsyncTask<Void, Void, PhoneNumberFormattingTextWatcher> {
        private final String mCountryCode;
        private final TextView mTextView;

        public TextWatcherLoadAsyncTask(String countryCode, TextView textView) {
            mCountryCode = countryCode;
            mTextView = textView;
        }

        @Override
        protected PhoneNumberFormattingTextWatcher doInBackground(Void... params) {
            return new PhoneNumberFormattingTextWatcher();
        }

        @Override
        protected void onPostExecute(PhoneNumberFormattingTextWatcher watcher) {
            if (watcher == null || isCancelled()) {
                return; // May happen if we cancel the task.
            }
            // Setting a text changed listener is safe even after the view is detached.
            mTextView.addTextChangedListener(watcher);

            // Note changes the user made before onPostExecute() will not be formatted, but
            // once they type the next letter we format the entire text, so it's not a big deal.
            // (And loading PhoneNumberFormattingTextWatcher is usually fast enough.)
            // We could use watcher.afterTextChanged(mTextView.getEditableText()) to force format
            // the existing content here, but that could cause unwanted results.
            // (e.g. the contact editor thinks the user changed the content, and would save
            // when closed even when the user didn't make other changes.)
        }
    }

    /**
     * Delay-set {@link android.telephony.PhoneNumberFormattingTextWatcher} to a {@link android.widget.TextView}.
     */
    public static final void setPhoneNumberFormattingTextWatcher(Context context, TextView textView) {
        new TextWatcherLoadAsyncTask(ContactsUtils.getCurrentCountryIso(context), textView)
                .execute((Void[]) null);
    }
}
