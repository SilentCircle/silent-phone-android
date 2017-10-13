/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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

import android.app.Activity;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.format.DateUtils;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.thread.Updater;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Updatable;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment;

import java.util.concurrent.TimeUnit;

/**
 * Base fragment for group and contact details fragments.
 */
public abstract class BaseConversationDetailsFragment extends BaseFragment implements Updatable {

    public static final int CONVERSATION_MUTE_SELECTION_DIALOG = 1020;
    public static final int CONVERSATION_MUTE_UNSELECTED = -1;
    public static final int CONVERSATION_MUTE_5M_INDEX = 0;
    public static final int CONVERSATION_MUTE_1H_INDEX = 1;
    public static final int CONVERSATION_MUTE_8H_INDEX = 2;
    public static final int CONVERSATION_MUTE_24H_INDEX = 3;
    public static final int CONVERSATION_MUTE_1W_INDEX = 4;
    public static final long[] CONVERSATION_MUTE_DURATIONS = new long[] {
            TimeUnit.MINUTES.toMillis(5),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.HOURS.toMillis(8),
            TimeUnit.HOURS.toMillis(24),
            TimeUnit.DAYS.toMillis(7)
    };

    protected int mConversationMuteIndex = CONVERSATION_MUTE_UNSELECTED;

    private final Updater mUpdater;
    private final Handler mHandler;

    private String mMuteDescriptionUnmuted;
    private String mMuteDescriptionMuted;

    public BaseConversationDetailsFragment() {
        mHandler = new Handler();
        mUpdater = new Updater(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMuteDescriptionUnmuted = getString(R.string.contact_info_description_mute_conversation);
        mMuteDescriptionMuted = getString(R.string.contact_info_description_conversation_muted_till);
    }

    @Override
    public void onResume() {
        super.onResume();
        scheduleNextUpdate();
    }

    @Override
    public void onPause() {
        mHandler.removeCallbacks(mUpdater);
        super.onPause();
    }

    protected void showSelectionDialog(int titleId, int itemArrayId, int selectedItemIndex, int requestCode) {
        if (!isAdded()) {
            return;
        }

        SingleChoiceDialogFragment fragment = SingleChoiceDialogFragment.getInstance(
                titleId,
                getResources().getTextArray(itemArrayId),
                selectedItemIndex);
        fragment.setTargetFragment(this, requestCode);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(fragment, SingleChoiceDialogFragment.TAG_CHOICE_DIALOG)
                    .commitAllowingStateLoss();
        }
    }

    protected void setMuteDuration(final @NonNull String conversationId, long duration) {
        ConversationRepository repository = ConversationUtils.getConversations();
        Conversation conversation = ConversationUtils.getConversation(conversationId);
        if (repository != null && conversation != null) {
            conversation.setMuteDuration(System.currentTimeMillis() + duration);
            repository.save(conversation);
        }
    }

    protected long getMuteDuration(final @NonNull String conversationId) {
        long duration = 0;
        Conversation conversation = ConversationUtils.getConversation(conversationId);
        if (conversation != null) {
            duration = conversation.getMuteDuration();
        }
        return duration;
    }

    protected void scheduleNextUpdate() {
        mHandler.removeCallbacks(mUpdater);
        final long now = System.currentTimeMillis();
        final long duration = getMuteDuration(getConversationId());
        if (duration - now > 0) {
            mHandler.postDelayed(mUpdater, duration - now);
        }
    }

    protected CharSequence getMuteStatusDescription(final @Nullable Conversation conversation) {
        Activity activity = getActivity();
        CharSequence muteDescription = mMuteDescriptionUnmuted;
        if (activity != null && conversation != null && conversation.isMuted()) {
            muteDescription =
                    String.format(mMuteDescriptionMuted,
                            DateUtils.formatDateTime(activity, conversation.getMuteDuration(),
                                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE));
        }
        return muteDescription;
    }

    protected abstract String getConversationId();

}
