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
package com.silentcircle.messaging.fragments;

import android.app.ListFragment;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ListView;

import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.views.ComposeText;
import com.silentcircle.silentphone2.R;

/**
 * Fragment for contact selection when forwarding a message.
 *
 */
public class SearchFragment extends ListFragment {

    @SuppressWarnings("unused")
    private static final String TAG = SearchFragment.class.getSimpleName();

    public static final String TAG_CONVERSATION_CONTACT_SEARCH_FRAGMENT =
            "com.silentcircle.messaging.contactsearch";
    public static final int MINIMUM_USERNAME_LENGTH = 2;

    protected ImageButton mButtonStartConversation;
    protected ComposeText mEditboxUsername;

    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(Editable s) {
            mButtonStartConversation.setEnabled(s.length() >= MINIMUM_USERNAME_LENGTH);
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Ignore.
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Ignore.
        }
    };

    public SearchFragment() {
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstance) {
        return inflateView(inflater, container);
    }

    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.messaging_search_contact_content, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mButtonStartConversation = (ImageButton) view.findViewById(R.id.button_start_conversation);
        mButtonStartConversation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                DialerUtils.hideInputMethod(mEditboxUsername);
                startConversation(mEditboxUsername.getText().toString());
            }
        });
        mButtonStartConversation.setEnabled(false);

        mEditboxUsername = (ComposeText) view.findViewById(R.id.editbox_username);
        mEditboxUsername.setFilters(new InputFilter[]{SearchUtil.USERNAME_INPUT_FILTER,
                SearchUtil.LOWER_CASE_INPUT_FILTER});
        mEditboxUsername.addTextChangedListener(mTextWatcher);
        mEditboxUsername.requestFocus();
        DialerUtils.showInputMethod(mEditboxUsername);
    }

    public void startConversation(final String number) {
        Intent messagingIntent = ContactsUtils.getMessagingIntent(number, getActivity());
        getActivity().startActivity(messagingIntent);
    }

    @Override
    public void onListItemClick(ListView list, View v, int position, long id) {
    }
}
