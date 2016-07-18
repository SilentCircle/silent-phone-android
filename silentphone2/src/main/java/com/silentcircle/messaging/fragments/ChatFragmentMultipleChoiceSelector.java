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

import android.support.v7.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;

import com.silentcircle.messaging.listener.MultipleChoiceSelector;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.views.adapters.HasChoiceMode;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;

public class ChatFragmentMultipleChoiceSelector extends MultipleChoiceSelector<Event> {

    private final ChatFragment mFragment;

    public ChatFragmentMultipleChoiceSelector(ChatFragment fragment, HasChoiceMode choices,
            int menuResourceID, String titleFormat) {
        super(choices, menuResourceID, fragment, titleFormat);
        mFragment = fragment;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        boolean result = super.onCreateActionMode(mode, menu);
        ChatFragment.Callback callback = mFragment.getCallback();
        if (callback != null) {
            callback.onActionModeCreated(mode);
        }
        return result;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        super.onDestroyActionMode(mode);
        ChatFragment.Callback callback = mFragment.getCallback();
        if (callback != null) {
            callback.onActionModeDestroyed();
        }
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long itemId,
            boolean checked) {
        super.onItemCheckedStateChanged(mode, position, itemId, checked);
        mode.invalidate();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        MenuItem itemCopy = menu.findItem(R.id.copy);
        MenuItem itemForward = menu.findItem(R.id.forward);
        MenuItem itemInfo = menu.findItem(R.id.info);
        MenuItem itemResend = menu.findItem(R.id.action_resend);

        int before = getMenuItemVisibilityMask(menu);

        boolean multipleChecked = mFragment.hasMultipleCheckedItems();
        boolean hasMessageStateOtherThanFailed = false;
        boolean hasErrorMessage = false;
        boolean hasAttachmentMessage = false;

        itemCopy.setVisible(!multipleChecked);

        SparseBooleanArray checkedItems = mFragment.getCheckedItemPositions();
        if (checkedItems != null) {
            for (int i = 0; i < checkedItems.size(); i++) {
                int key = checkedItems.keyAt(i);

                if (checkedItems.get(key, false)) {
                    Event event = mFragment.getEvent(key);

                    if (event != null && event instanceof Message) {
                        if (((Message) event).hasAttachment()) {
                            hasAttachmentMessage = true;
                        }
                        hasMessageStateOtherThanFailed |=
                                (((Message) event).getState() != MessageStates.FAILED);
                    }
                    if (event != null && event instanceof ErrorEvent) {
                        hasErrorMessage = true;
                    }
                }
            }
        }

        itemCopy.setVisible(!multipleChecked && !hasAttachmentMessage && !hasErrorMessage);
        itemForward.setVisible(!multipleChecked && !hasErrorMessage);
        itemInfo.setVisible(!multipleChecked && BuildConfig.DEBUG);
        itemResend.setVisible(!hasMessageStateOtherThanFailed && !hasErrorMessage);

        int after = getMenuItemVisibilityMask(menu);

        return super.onPrepareActionMode(mode, menu) || before != after;
    }

    private int getMenuItemVisibilityMask(Menu menu) {
        /* probably unnecessary */
        MenuItem itemCopy = menu.findItem(R.id.copy);
        MenuItem itemForward = menu.findItem(R.id.forward);
        MenuItem itemInfo = menu.findItem(R.id.info);
        MenuItem itemResend = menu.findItem(R.id.action_resend);
        MenuItem itemBurn = menu.findItem(R.id.burn);
        return (itemCopy.isVisible() ? 1 : 0)
                | (itemForward.isVisible() ? 2 : 0)
                | (itemInfo.isVisible() ? 4 : 0)
                | (itemResend.isVisible() ? 8 : 0)
                | (itemBurn.isVisible() ? 16 : 0);
    }

}
