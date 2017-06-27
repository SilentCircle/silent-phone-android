/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.userinfo.LoadUserInfo;

import static com.silentcircle.messaging.model.event.ErrorEvent.DECRYPTION_ERROR_MESSAGE_UNDECRYPTABLE;
import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_MESSAGE_BLOCKED;
import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_MESSAGE_REJECTED;
import static com.silentcircle.messaging.model.event.ErrorEvent.POLICY_ERROR_RETENTION_REQUIRED;

public class FailureEventView extends CheckableRelativeLayout implements View.OnClickListener, HasChoiceMode {

    private TextView mText;
    private ErrorEvent mErrorEvent;
    private boolean mInChoiceMode;

    public FailureEventView(Context context) {
        this(context, null);
    }

    public FailureEventView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FailureEventView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mText = (TextView) findViewById(R.id.error_message);
    }

    @Override
    public void onClick(View view) {
        /* show description of policy error */
        if (mErrorEvent != null && (POLICY_ERROR_RETENTION_REQUIRED.equals(mErrorEvent.getText()))
                || POLICY_ERROR_MESSAGE_REJECTED.equals(mErrorEvent.getText())
                || POLICY_ERROR_MESSAGE_BLOCKED.equals(mErrorEvent.getText())) {
            showInfoDialog();
        /* show error event info on click */
        } else if (mErrorEvent != null && !TextUtils.isEmpty(mErrorEvent.getMessageText())) {
            Context context = getContext();
            Intent intent = ContactsUtils.getMessagingIntent(mErrorEvent.getConversationID(), context);
            Extra.TASK.to(intent, ConversationActivity.TASK_SHOW_EVENT_INFO);
            Extra.ID.to(intent, mErrorEvent.getId());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        }
    }

    @Override
    public boolean isInChoiceMode() {
        return mInChoiceMode;
    }

    @Override
    public void setInChoiceMode(boolean inChoiceMode) {
        if (inChoiceMode != mInChoiceMode) {
            mInChoiceMode = inChoiceMode;
        }
    }

    @Override
    public void setChecked(boolean checked) {
        // allow to change checked state only in action mode
        if (checked && !isInChoiceMode()) {
            return;
        }
        super.setChecked(checked);
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        if (tag instanceof ErrorEvent) {
            setFailure((ErrorEvent) tag);
        }
    }

    public void setFailure(ErrorEvent errorEvent) {
        mErrorEvent = errorEvent;

        int failureTextId = MessageErrorCodes.messageErrorToStringId(errorEvent.getError());

        if (errorEvent.hasText()) {
            if (POLICY_ERROR_RETENTION_REQUIRED.equals(errorEvent.getText())) {
                setText(getContext().getString(R.string.data_retention_policy_error));
            } else if (POLICY_ERROR_MESSAGE_REJECTED.equals(errorEvent.getText())) {
                setText(getContext().getString(R.string.data_retention_policy_error));
            } else if (POLICY_ERROR_MESSAGE_BLOCKED.equals(errorEvent.getText())) {
                setText(getContext().getString(R.string.data_retention_policy_error));
            } else if (DECRYPTION_ERROR_MESSAGE_UNDECRYPTABLE.equals(errorEvent.getText())) {
                setText(getContext().getString(R.string.message_error_remote_decrypt_failed));
            } else {
                setText(errorEvent.getText());
            }
        } else {
            setText(getResources().getString(failureTextId) + (errorEvent.isDuplicate() ? " (>1)" : ""));
        }
    }

    public void setText(String text) {
        mText.setText(text);
    }

    private void showInfoDialog() {
        String organization = LoadUserInfo.getRetentionOrganization();
        if (!TextUtils.isEmpty(organization)) {
            organization = "(" + organization + ")";
        }
        String message = "";
        if (POLICY_ERROR_RETENTION_REQUIRED.equals(mErrorEvent.getText()))
            message = getContext().getString(R.string.dialog_message_communication_dr_required);
        else if (POLICY_ERROR_MESSAGE_REJECTED.equals(mErrorEvent.getText()))
            message = getContext().getString(R.string.dialog_message_communication_dr_blocked, organization);
        else if (POLICY_ERROR_MESSAGE_BLOCKED.equals(mErrorEvent.getText()))
            message = getContext().getString(R.string.dialog_message_communication_blocked_remote);

        InfoMsgDialogFragment.showDialog((Activity) getContext(), R.string.information_dialog,
                message, R.string.dialog_button_ok, -1);
    }

}
