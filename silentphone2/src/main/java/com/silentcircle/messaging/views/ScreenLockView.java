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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.Arrays;

/**
 * Widget to show when messaging screens are locked.
 */
public class ScreenLockView extends LinearLayout {

    public interface OnUnlockListener {

        void onUnlock();
    }

    private OnUnlockListener mOnUnlockListener;
    private OnClickListener mOnSubmit = new OnClickListener() {

        @Override
        public void onClick(View v) {
            checkAndSubmitPassword();
        }
    };

    private Button mButton;
    private EditText mEditTextPassword;

    public ScreenLockView(Context context) {
        this(context, null);
    }

    public ScreenLockView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScreenLockView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        inflate(context, R.layout.widget_screen_lock, this);

        if (!isInEditMode()) {
            mButton = (Button) findViewById(R.id.button_submit);
            mButton.setOnClickListener(mOnSubmit);
            mEditTextPassword = (EditText) findViewById(R.id.edittext_password);
            mEditTextPassword.setOnEditorActionListener(new TextView.OnEditorActionListener() {

                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_GO) {
                        checkAndSubmitPassword();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    public void setOnUnlockListener(OnUnlockListener listener) {
        mOnUnlockListener = listener;
    }

    public void clear() {
        if (mEditTextPassword != null) {
            mEditTextPassword.setText(null);
            DialerUtils.hideInputMethod(mEditTextPassword);
        }
    }

    private void checkAndSubmitPassword() {
        CharSequence password = mEditTextPassword.getText();
        mEditTextPassword.setText(null);

        if (!TextUtils.isEmpty(password)) {
            String hashString = Utilities.hashSha256(password.toString());
            byte[] chatKeyData =
                    KeyManagerSupport.getPrivateKeyData(getContext().getContentResolver(),
                            ConfigurationUtilities.getChatProtectionKey());

            if (chatKeyData != null && hashString != null && Arrays.equals(chatKeyData, hashString.getBytes())) {
                MessagingPreferences.getInstance(getContext().getApplicationContext())
                        .setLastMessagingUnlockTime(System.currentTimeMillis());
                clear();
                if (mOnUnlockListener != null) {
                    mOnUnlockListener.onUnlock();
                }
            } else {
                Toast.makeText(getContext(), R.string.messaging_screen_lock_password_not_correct,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void focus() {
        if (mEditTextPassword != null) {
            mEditTextPassword.requestFocus();
            DialerUtils.showInputMethod(mEditTextPassword);
        }
    }

}
