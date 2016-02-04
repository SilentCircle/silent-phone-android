/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.silentcircle.silentphone2.R;

/**
 * Dialog for selection between call or conversation.
 */
public class CallOrConversationDialog extends Dialog {

    private final boolean mWithContact;
    public interface OnCallOrConversationSelectedListener {

        void onCallSelected();

        void onConversationSelected();

        void onAddContactSelected();
    };

    private OnCallOrConversationSelectedListener mListener;

    public CallOrConversationDialog(final Context context, final boolean withContact) {
        super(context, R.style.CallOrConversationDialogTheme);
        mWithContact = withContact;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_choose_call_conversation);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        getWindow().setAttributes(params);

        ImageButton buttonCall = (ImageButton) findViewById(R.id.button_call);
        ImageButton buttonConversation = (ImageButton) findViewById(R.id.button_chat);
        ImageButton buttonAdd = (ImageButton) findViewById(R.id.button_add);

        if (mWithContact) {
            buttonAdd.setVisibility(View.VISIBLE);
            buttonAdd.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    CallOrConversationDialog.this.dismiss();
                    if (mListener != null) {
                        mListener.onAddContactSelected();
                    }
                }
            });
        }

        buttonCall.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                CallOrConversationDialog.this.dismiss();
                if (mListener != null) {
                    mListener.onCallSelected();
                }
            }
        });

        buttonConversation.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                CallOrConversationDialog.this.dismiss();
                if (mListener != null) {
                    mListener.onConversationSelected();
                }
            }
        });
    }

    public void setOnCallOrConversationSelectedListener(
            final OnCallOrConversationSelectedListener listener) {
        mListener = listener;
    }
}
