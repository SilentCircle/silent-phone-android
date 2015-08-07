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
package com.silentcircle.contacts.interactions;

import android.app.Activity;
import android.content.Intent;
import android.app.FragmentManager;
import android.widget.EditText;

import com.silentcircle.silentphone2.R;
import com.silentcircle.contacts.ScContactSaveService;

/**
 * A dialog for creating a new group.
 */
public class GroupCreationDialogFragment extends GroupNameDialogFragment {
    public static final String FRAGMENT_TAG = "createGroupDialog";

    private OnGroupCreatedListener mListener;

    public interface OnGroupCreatedListener {
        public void onGroupCreated();
    }

    public static void show(FragmentManager fragmentManager, OnGroupCreatedListener listener) {

        GroupCreationDialogFragment dialog = new GroupCreationDialogFragment();
        dialog.setOnGroupCreatedListener(listener);
        dialog.show(fragmentManager, FRAGMENT_TAG);
    }

    public GroupCreationDialogFragment() {
        mListener = null;
    }

    public OnGroupCreatedListener getOnGroupCreatedListener() {
        return mListener;
    }

    public void setOnGroupCreatedListener(OnGroupCreatedListener l) {
        mListener = l;
    }

    @Override
    protected void initializeGroupLabelEditText(EditText editText) {
    }

    @Override
    protected int getTitleResourceId() {
        return R.string.create_group_dialog_title;
    }

    @Override
    protected void onCompleted(String groupLabel) {

        // Indicate to the listener that a new group will be created.
        // If the device is rotated, mListener will become null, so that the
        // popup from GroupMembershipView will not be shown.
        if (mListener != null) {
            mListener.onGroupCreated();
        }

        Activity activity = getActivity();
        activity.startService(ScContactSaveService.createNewGroupIntent(activity, groupLabel, null /* no new members to add */,
                activity.getClass(), Intent.ACTION_EDIT));
    }
}
