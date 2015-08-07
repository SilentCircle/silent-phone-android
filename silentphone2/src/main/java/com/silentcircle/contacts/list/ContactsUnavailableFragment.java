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
package com.silentcircle.contacts.list;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentcontacts2.ScContactsContract.ProviderStatus;

/**
 * Fragment shown when contacts are unavailable. It contains provider status
 * messaging as well as instructions for the user.
 */
public class ContactsUnavailableFragment extends Fragment implements OnClickListener {

    /**
     * Action callbacks that can be sent by the "contacts unavailable" fragment.
     */
    public interface OnContactsUnavailableActionListener  {

        /**
         * Creates a new contact.
         */
        void onCreateNewContactAction();

        /**
         * Creates a new own profile.
         */
        public void onCreateNewProfileAction();

        /**
         * Initiates contact import from a file.
         */
        void onImportContactsFromFileAction();

        /**
         * Initiates an interaction that frees up some internal storage for the purposes
         * of a database upgrade.
         */
        void onFreeInternalStorageAction();
    }

    private View mView;
    private TextView mMessageView;
    private TextView mSecondaryMessageView;
    private Button mCreateContactButton;
    private Button mAddProfileButton;
    private Button mImportContactsButton;
    private Button mUninstallAppsButton;
    private Button mRetryUpgradeButton;
    private ProgressBar mProgress;
    private int mNoContactsMsgResId = -1;
    private int mNSecNoContactsMsgResId = -1;

    private OnContactsUnavailableActionListener mListener;

    private ProviderStatusWatcher.Status mProviderStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.contacts_unavailable_fragment, null);
        mMessageView = (TextView) mView.findViewById(R.id.message);
        mSecondaryMessageView = (TextView) mView.findViewById(R.id.secondary_message);
        mCreateContactButton = (Button) mView.findViewById(R.id.create_contact_button);
        mCreateContactButton.setOnClickListener(this);
        mAddProfileButton = (Button) mView.findViewById(R.id.add_profile_button);
        mAddProfileButton.setOnClickListener(this);
        mImportContactsButton = (Button) mView.findViewById(R.id.import_contacts_button);
        mImportContactsButton.setOnClickListener(this);
        mUninstallAppsButton = (Button) mView.findViewById(R.id.import_failure_uninstall_button);
        mUninstallAppsButton.setOnClickListener(this);
        mRetryUpgradeButton = (Button) mView.findViewById(R.id.import_failure_retry_button);
        mRetryUpgradeButton.setOnClickListener(this);
        mProgress = (ProgressBar) mView.findViewById(R.id.progress);

        if (mProviderStatus != null) {
            updateStatus(mProviderStatus);
        }
        return mView;
    }

    public void setOnContactsUnavailableActionListener(OnContactsUnavailableActionListener listener) {
        mListener = listener;
    }

    public void updateStatus(ProviderStatusWatcher.Status providerStatus) {
        mProviderStatus = providerStatus;
        if (mView == null) {
            // The view hasn't been inflated yet.
            return;
        }
        switch (providerStatus.status) {
            case ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS:
                setMessageText(mNoContactsMsgResId, mNSecNoContactsMsgResId);
                mCreateContactButton.setVisibility(View.VISIBLE);
                mAddProfileButton.setVisibility(View.VISIBLE);
                mImportContactsButton.setVisibility(View.VISIBLE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.GONE);
                break;

            case ProviderStatus.STATUS_CHANGING_LOCALE:
                mMessageView.setText(R.string.locale_change_in_progress);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddProfileButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.VISIBLE);
                break;

            case ProviderStatus.STATUS_UPGRADING:
                mMessageView.setText(R.string.upgrade_in_progress);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddProfileButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.GONE);
                mRetryUpgradeButton.setVisibility(View.GONE);
                mProgress.setVisibility(View.VISIBLE);
                break;

            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                String message = getResources().getString(R.string.upgrade_out_of_memory, new Object[] { providerStatus.data});
                mMessageView.setText(message);
                mMessageView.setGravity(Gravity.START);
                mMessageView.setVisibility(View.VISIBLE);
                mCreateContactButton.setVisibility(View.GONE);
                mAddProfileButton.setVisibility(View.GONE);
                mImportContactsButton.setVisibility(View.GONE);
                mUninstallAppsButton.setVisibility(View.VISIBLE);
                mRetryUpgradeButton.setVisibility(View.VISIBLE);
                mProgress.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onClick(View v) {
        if (mListener == null) {
            return;
        }
        switch (v.getId()) {
            case R.id.create_contact_button:
                mListener.onCreateNewContactAction();
                break;
            case R.id.add_profile_button:
                mListener.onCreateNewProfileAction();
                break;
            case R.id.import_contacts_button:
                mListener.onImportContactsFromFileAction();
                break;
            case R.id.import_failure_uninstall_button:
                mListener.onFreeInternalStorageAction();
                break;
            case R.id.import_failure_retry_button:
                final Context context = getActivity();
                if (context != null) { // Just in case.
                    ProviderStatusWatcher.retryUpgrade(context);
                }
                break;
        }
    }
    /**
     * Set the message to be shown if no data is available for the selected tab
     *
     * @param resId - String resource ID of the message , -1 means view will not be visible
     */
    public void setMessageText(int resId, int secResId) {
        mNoContactsMsgResId = resId;
        mNSecNoContactsMsgResId = secResId;
        if ((mMessageView != null) && (mProviderStatus != null) &&
                (mProviderStatus.status == ProviderStatus.STATUS_NO_ACCOUNTS_NO_CONTACTS)) {
            if (resId != -1) {
                mMessageView.setText(mNoContactsMsgResId);
                mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                mMessageView.setVisibility(View.VISIBLE);
                if (secResId != -1) {
                    mSecondaryMessageView.setText(mNSecNoContactsMsgResId);
                    mSecondaryMessageView.setGravity(Gravity.CENTER_HORIZONTAL);
                    mSecondaryMessageView.setVisibility(View.VISIBLE);
                } else {
                    mSecondaryMessageView.setVisibility(View.INVISIBLE);
                }
            } else {
                mSecondaryMessageView.setVisibility(View.GONE);
                mMessageView.setVisibility(View.GONE);
            }
        }
    }
}
