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

import android.app.Activity;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.silentcircle.messaging.listener.ClickthroughWhenNotInChoiceMode;
import com.silentcircle.messaging.listener.MultipleChoiceSelector;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.views.FailureEventView;
import com.silentcircle.messaging.views.ListView;
import com.silentcircle.messaging.views.MessageEventView;
import com.silentcircle.messaging.views.adapters.ModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ModelViewType;
import com.silentcircle.messaging.views.adapters.MultiSelectModelViewAdapter;
import com.silentcircle.messaging.views.adapters.ViewType;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends BaseFragment implements MultipleChoiceSelector.ActionPerformer {

    public static final String TAG_CONVERSATION_CHAT_FRAGMENT =
            "com.silentcircle.messaging.chat";

    public interface Callback {

        void onActionModeCreated();

        void onActionModeDestroyed();

        void onActionPerformed();

        void performAction(int actionID, List targets);
    }

    private static final ViewType[] VIEW_TYPES = {
        new ModelViewType(IncomingMessage.class, MessageEventView.class, R.layout.messaging_chat_item_incoming_message),
        new ModelViewType(OutgoingMessage.class, MessageEventView.class, R.layout.messaging_chat_item_outgoing_message),
        new ModelViewType(ErrorEvent.class, FailureEventView.class, R.layout.messaging_chat_item_error),
        new ModelViewType(Event.class, TextView.class, R.layout.messaging_chat_item_text)
    };

    private static void attachEventsToAdapter(ModelViewAdapter adapter, List<Event> events) {
        adapter.setModels(events);
        adapter.notifyDataSetChanged();
    }

    private Callback mCallback;
    private ListView mEventsView;

    private void attachEventsToView(ListView view, List<Event> events) {
        MultiSelectModelViewAdapter adapter = new MultiSelectModelViewAdapter(events, VIEW_TYPES);
        view.setAdapter(adapter);

        view.setOnItemClickListener(ClickthroughWhenNotInChoiceMode.getInstance());
        view.setMultiChoiceModeListener(new ChatFragmentMultipleChoiceSelector(this, adapter,
                R.menu.multiselect_event, getString(R.string.n_selected)));
        adapter.notifyDataSetChanged();
    }

    protected Callback getCallback() {
        if (mCallback != null) {
            return mCallback;
        }
        Activity activity = getActivity();
        return activity instanceof Callback ? (Callback) activity : null;
    }

    public Event getEvent(int position) {
        if (mEventsView != null) {
            return (Event) mEventsView.getItemAtPosition(position);
        }
        return null;
    }

    public boolean hasMultipleCheckedItems() {
        return mEventsView != null && mEventsView.hasMultipleCheckedItems();
    }

    public SparseBooleanArray getCheckedItemPositions() {
        return mEventsView != null ? mEventsView.getCheckedItemPositions() : null;
    }

    @Override
    public void onActionPerformed() {
        Callback callback = getCallback();
        if (callback != null) {
            callback.onActionPerformed();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.messaging_chat_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEventsView = (ListView) findViewById(R.id.chat_events);
    }

    @Override
    public void onDestroyView() {
        mEventsView = null;
        super.onDestroyView();
    }

    @Override
    public void performAction(final int menuActionId, final int... positions) {
        final Callback callback = getCallback();
        if (callback != null && positions.length > 0) {
            final List<Event> events = new ArrayList<>(positions.length);
            for (int position : positions) {
                events.add(getEvent(position));
            }

            callback.performAction(menuActionId, events);
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void setEvents(List<Event> events) {
        if (mEventsView != null) {
            ListAdapter adapter = mEventsView.getAdapter();
            if (adapter instanceof ModelViewAdapter) {
                attachEventsToAdapter((ModelViewAdapter) adapter, events);
            } else {
                attachEventsToView(mEventsView, events);
            }
        }
    }

    public void scrollToBottom() {
        if (mEventsView != null) {
            mEventsView.setSelection(mEventsView.getCount() - 1);
        }
    }

}

