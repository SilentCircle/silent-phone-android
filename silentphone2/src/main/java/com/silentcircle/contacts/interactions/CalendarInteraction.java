/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.contacts.interactions;


import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;

import com.silentcircle.silentphone2.R;

/**
 * Represents a calendar event interaction, wrapping the columns in
 * {@link android.provider.CalendarContract.Attendees}.
 */
public class CalendarInteraction implements ContactInteraction {
    private static final String TAG = CalendarInteraction.class.getSimpleName();

    private static final int CALENDAR_ICON_RES = R.drawable.ic_event_24dp;

    private ContentValues mValues;

    public CalendarInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        return new Intent(Intent.ACTION_VIEW).setData(
                ContentUris.withAppendedId(Events.CONTENT_URI, getEventId()));
    }

    @Override
    public long getInteractionDate() {
        return getDtstart();
    }

    @Override
    public String getViewHeader(Context context) {
        String title = getTitle();
        if (TextUtils.isEmpty(title)) {
            return context.getResources().getString(R.string.untitled_event);
        }
        return title;
    }

    @Override
    public String getViewBody(Context context) {
        return null;
    }

    @Override
    public String getViewFooter(Context context) {
        // Pulled from com.android.calendar.EventInfoFragment.updateEvent(View view)
        // TODO: build callback to update time zone if different than preferences
        String localTimezone = Time.getCurrentTimezone();

        Long dateEnd = getDtend();
        Long dateStart = getDtstart();
        if (dateStart == null && dateEnd == null) {
            return null;
        } else if (dateEnd == null) {
            dateEnd = dateStart;
        } else if (dateStart == null) {
            dateStart = dateEnd;
        }

        String displayedDatetime = CalendarInteractionUtils.getDisplayedDatetime(
                dateStart, dateEnd, System.currentTimeMillis(), localTimezone,
                getAllDay(), context);

        return displayedDatetime;
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(CALENDAR_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        return null;
    }

    public String getAttendeeEmail() {
        return mValues.getAsString(Attendees.ATTENDEE_EMAIL);
    }

    public String getAttendeeIdentity() {
        return mValues.getAsString(Attendees.ATTENDEE_IDENTITY);
    }

    public String getAttendeeIdNamespace() {
        return mValues.getAsString(Attendees.ATTENDEE_ID_NAMESPACE);
    }

    public String getAttendeeName() {
        return mValues.getAsString(Attendees.ATTENDEE_NAME);
    }

    public Integer getAttendeeRelationship() {
        return mValues.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP);
    }

    public Integer getAttendeeStatus() {
        return mValues.getAsInteger(Attendees.ATTENDEE_STATUS);
    }

    public Integer getAttendeeType() {
        return mValues.getAsInteger(Attendees.ATTENDEE_TYPE);
    }

    public Integer getEventId() {
        return mValues.getAsInteger(Attendees.EVENT_ID);
    }

    public Integer getAccessLevel() {
        return mValues.getAsInteger(Attendees.ACCESS_LEVEL);
    }

    public Boolean getAllDay() {
        return mValues.getAsInteger(Attendees.ALL_DAY) == 1 ? true : false;
    }

    public Integer getAvailability() {
        return mValues.getAsInteger(Attendees.AVAILABILITY);
    }

    public Integer getCalendarId() {
        return mValues.getAsInteger(Attendees.CALENDAR_ID);
    }

    public Boolean getCanInviteOthers() {
        return mValues.getAsBoolean(Attendees.CAN_INVITE_OTHERS);
    }

    public String getCustomAppPackage() {
        return mValues.getAsString(Attendees.CUSTOM_APP_PACKAGE);
    }

    public String getCustomAppUri() {
        return mValues.getAsString(Attendees.CUSTOM_APP_URI);
    }

    public String getDescription() {
        return mValues.getAsString(Attendees.DESCRIPTION);
    }

    public Integer getDisplayColor() {
        return mValues.getAsInteger(Attendees.DISPLAY_COLOR);
    }

    public Long getDtend() {
        return mValues.getAsLong(Attendees.DTEND);
    }

    public Long getDtstart() {
        return mValues.getAsLong(Attendees.DTSTART);
    }

    public String getDuration() {
        return mValues.getAsString(Attendees.DURATION);
    }

    public Integer getEventColor() {
        return mValues.getAsInteger(Attendees.EVENT_COLOR);
    }

    public String getEventColorKey() {
        return mValues.getAsString(Attendees.EVENT_COLOR_KEY);
    }

    public String getEventEndTimezone() {
        return mValues.getAsString(Attendees.EVENT_END_TIMEZONE);
    }

    public String getEventLocation() {
        return mValues.getAsString(Attendees.EVENT_LOCATION);
    }

    public String getExdate() {
        return mValues.getAsString(Attendees.EXDATE);
    }

    public String getExrule() {
        return mValues.getAsString(Attendees.EXRULE);
    }

    public Boolean getGuestsCanInviteOthers() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_INVITE_OTHERS);
    }

    public Boolean getGuestsCanModify() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_MODIFY);
    }

    public Boolean getGuestsCanSeeGuests() {
        return mValues.getAsBoolean(Attendees.GUESTS_CAN_SEE_GUESTS);
    }

    public Boolean getHasAlarm() {
        return mValues.getAsBoolean(Attendees.HAS_ALARM);
    }

    public Boolean getHasAttendeeData() {
        return mValues.getAsBoolean(Attendees.HAS_ATTENDEE_DATA);
    }

    public Boolean getHasExtendedProperties() {
        return mValues.getAsBoolean(Attendees.HAS_EXTENDED_PROPERTIES);
    }

    public String getIsOrganizer() {
        return mValues.getAsString(Attendees.IS_ORGANIZER);
    }

    public Long getLastDate() {
        return mValues.getAsLong(Attendees.LAST_DATE);
    }

    public Boolean getLastSynced() {
        return mValues.getAsBoolean(Attendees.LAST_SYNCED);
    }

    public String getOrganizer() {
        return mValues.getAsString(Attendees.ORGANIZER);
    }

    public Boolean getOriginalAllDay() {
        return mValues.getAsBoolean(Attendees.ORIGINAL_ALL_DAY);
    }

    public String getOriginalId() {
        return mValues.getAsString(Attendees.ORIGINAL_ID);
    }

    public Long getOriginalInstanceTime() {
        return mValues.getAsLong(Attendees.ORIGINAL_INSTANCE_TIME);
    }

    public String getOriginalSyncId() {
        return mValues.getAsString(Attendees.ORIGINAL_SYNC_ID);
    }

    public String getRdate() {
        return mValues.getAsString(Attendees.RDATE);
    }

    public String getRrule() {
        return mValues.getAsString(Attendees.RRULE);
    }

    public Integer getSelfAttendeeStatus() {
        return mValues.getAsInteger(Attendees.SELF_ATTENDEE_STATUS);
    }

    public Integer getStatus() {
        return mValues.getAsInteger(Attendees.STATUS);
    }

    public String getTitle() {
        return mValues.getAsString(Attendees.TITLE);
    }

    public String getUid2445() {
        return mValues.getAsString(Attendees.UID_2445);
    }

    @Override
    public String getContentDescription(Context context) {
        // The default TalkBack is good
        return null;
    }

    @Override
    public int getIconResourceId() {
        return CALENDAR_ICON_RES;
    }
}
