package com.silentcircle.silentphone2.util;
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
/**
 * Modified by werner on 08.02.14.
 */

import android.os.AsyncTask;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.widget.TextView;


public final class PhoneNumberFormatter {
    private PhoneNumberFormatter() {}

    /**
     * Load {@link TextWatcherLoadAsyncTask} in a worker thread and set it to a {@link TextView}.
     */
    private static class TextWatcherLoadAsyncTask extends
            AsyncTask<Void, Void, PhoneNumberFormattingTextWatcher> {
        private final TextView mTextView;

        public TextWatcherLoadAsyncTask(TextView textView) {
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
        }
    }

    /**
     * Delay-set {@link PhoneNumberFormattingTextWatcher} to a {@link TextView}.
     */
    public static void setPhoneNumberFormattingTextWatcher(TextView textView) {
        new TextWatcherLoadAsyncTask(textView)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }
}
