/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts.calllognew;

import android.content.Context;
import android.view.View;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import com.silentcircle.common.testing.NeededForTesting;
import com.silentcircle.silentphone2.R;

/**
 * Simple value object containing the various views within a call log entry.
 */
public final class CallLogListItemViews {
    /** The quick contact badge for the contact. */
    public final QuickContactBadge quickContactView;
    /** The primary action view of the entry. */
    public final View primaryActionView;
    /** The details of the phone call. */
    public final PhoneCallDetailsViews phoneCallDetailsViews;
    /** The text of the header for a day grouping. */
    public final TextView dayGroupHeader;
    /** The view containing the details for the call log row, including the action buttons. */
    public final View callLogEntryView;
    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actionsView;
    /** The view containing call log item actions.  Null until the ViewStub is inflated. */
    public View actions2View;
    /** The "call back" action button - assigned only when the action section is expanded. */
    public TextView callBackButtonView;
    /** The "video call" action button - assigned only when the action section is expanded. */
    public TextView videoCallButtonView;
    /** The "voicemail" action button - assigned only when the action section is expanded. */
    public TextView voicemailButtonView;
    /** The "details" action button - assigned only when the action section is expanded. */
    public TextView detailsButtonView;
    /** The "write back" action button. */
    public TextView writeBackButtonView;
    /** The "invite" action button */
    public TextView inviteButtonView;

    /**
     * The row Id for the first call associated with the call log entry.  Used as a key for the
     * map used to track which call log entries have the action button section expanded.
     */
    public long rowId;

    /**
     * The call Ids for the calls represented by the current call log entry.  Used when the user
     * deletes a call log entry.
     */
    public long[] callIds;

    /**
     * The callable phone number for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String number;

    /**
     * The SIP address for the current call log entry, may be null.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public String assertedId;

    /**
     * The phone number presentation for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int numberPresentation;

    /**
     * The type of call for the current call log entry.  Cached here as the call back
     * intent is set only when the actions ViewStub is inflated.
     */
    public int callType;

    /**
     * If the call has an associated voicemail message, the URI of the voicemail message for
     * playback.  Cached here as the voicemail intent is only set when the actions ViewStub is
     * inflated.
     */
    public String voicemailUri;

    /**
     * The name or number associated with the call.  Cached here for use when setting content
     * descriptions on buttons in the actions ViewStub when it is inflated.
     */
    public CharSequence nameOrNumber;

    private CallLogListItemViews(QuickContactBadge quickContactView, View primaryActionView,
            PhoneCallDetailsViews phoneCallDetailsViews, View callLogEntryView,
            TextView dayGroupHeader) {
        this.quickContactView = quickContactView;
        this.primaryActionView = primaryActionView;
        this.phoneCallDetailsViews = phoneCallDetailsViews;
        this.callLogEntryView = callLogEntryView;
        this.dayGroupHeader = dayGroupHeader;
    }

    public static CallLogListItemViews fromView(View view) {
        return new CallLogListItemViews(
                (QuickContactBadge) view.findViewById(R.id.quick_contact_photo),
                view.findViewById(R.id.primary_action_view),
                PhoneCallDetailsViews.fromView(view),
                view.findViewById(R.id.call_log_row),
                (TextView) view.findViewById(R.id.call_log_day_group_label));
    }

    @NeededForTesting
    public static CallLogListItemViews createForTest(Context context) {
        CallLogListItemViews views = new CallLogListItemViews(
                new QuickContactBadge(context),
                new View(context),
                PhoneCallDetailsViews.createForTest(context),
                new View(context),
                new TextView(context));
        views.callBackButtonView = new TextView(context);
        views.voicemailButtonView = new TextView(context);
        views.detailsButtonView = new TextView(context);
        views.actionsView = new View(context);
        return views;
    }
}
