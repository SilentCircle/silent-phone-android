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

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;

import com.silentcircle.contacts.ScContactSaveService;
import com.silentcircle.silentphone2.R;

/**
 * A dialog for deleting a group.
 */
public class GroupDeletionDialogFragment extends DialogFragment {

    private static final String ARG_GROUP_ID = "groupId";
    private static final String ARG_LABEL = "label";
    private static final String ARG_SHOULD_END_ACTIVITY = "endActivity";

    public static void show(FragmentManager fragmentManager, long groupId, String label, boolean endActivity) {
        GroupDeletionDialogFragment dialog = new GroupDeletionDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_LABEL, label);
        args.putBoolean(ARG_SHOULD_END_ACTIVITY, endActivity);
        dialog.setArguments(args);
        dialog.show(fragmentManager, "deleteGroup");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String label = getArguments().getString(ARG_LABEL);
        String message = getActivity().getString(R.string.delete_group_dialog_message, label);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);

        return builder.setMessage(message)
                .setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            deleteGroup();
                        }
                    }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    protected void deleteGroup() {
        Bundle arguments = getArguments();
        long groupId = arguments.getLong(ARG_GROUP_ID);

        getActivity().startService(ScContactSaveService.createGroupDeletionIntent(getActivity(), groupId));
        if (shouldEndActivity()) {
            getActivity().finish();
        }
    }

    private boolean shouldEndActivity() {
        return getArguments().getBoolean(ARG_SHOULD_END_ACTIVITY);
    }
}
