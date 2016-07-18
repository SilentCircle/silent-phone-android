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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;

import com.silentcircle.silentphone2.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateUtils {

    private static final SimpleDateFormat ISO8601 = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss'Z'" );

    public static final long MILLI = 1;
    public static final long SECOND = 1000 * MILLI;
    public static final long MINUTE = 60 * SECOND;
    public static final long HOUR = 60 * MINUTE;
    public static final long DAY = 24 * HOUR;

    public static final String HEADER_DATE_FORMAT = "d MMMM yyyy";

    public static final String MESSAGE_TIME_FORMAT = "HH:mm";
    public static final String MESSAGE_TIME_FORMAT_JELLYBEAN = "kk:mm";

    static {
        ISO8601.setTimeZone( TimeZone.getTimeZone("UTC") );
    }

    public static CharSequence getRelativeTimeSpanString(Context context, long time) {
        return android.text.format.DateUtils.getRelativeTimeSpanString(context, time);
    }

    public static CharSequence getShortTimeString(Context context, long interval) {
        return getShortTimeString(context.getResources(), interval);
    }

    public static CharSequence getShortTimeString(Resources resources, long interval) {
        if (interval >= DAY) {
            final int d = (int) (interval / DAY);
            final int h = (int)((interval % DAY) / HOUR);
            String dh = resources.getQuantityString(R.plurals.short_time_days, d, d);
            if (h > 0)
                dh += " " + resources.getQuantityString(R.plurals.short_time_hours, h, h);
            return dh;
        }
        if (interval >= HOUR) {
            final int h = (int) (interval / HOUR);
            final int m = (int)((interval % HOUR) / MINUTE);
            String hm = resources.getQuantityString(R.plurals.short_time_hours, h, h);
            if (m > 0)
                hm += " " + resources.getQuantityString(R.plurals.short_time_minutes, m, m);
            return hm;
        }
        if (interval >= MINUTE) {
            final int m = (int) (interval / MINUTE);
            final int s = (int)((interval % MINUTE) / SECOND);
            String ms = resources.getQuantityString(R.plurals.short_time_minutes, m, m);
            if (s > 0)
                ms += " " + resources.getQuantityString(R.plurals.short_time_seconds, s, s);
            return ms;
        }

        int s = interval >= SECOND ? (int) (interval / SECOND) : 0;
        return resources.getQuantityString(R.plurals.short_time_seconds, s, s);
    }

    public static CharSequence getShortTimeString(long interval) {
        if (interval >= DAY) {
            int d = (int) (interval / DAY);
            return Integer.valueOf(d) + "d";
        }
        if (interval >= HOUR) {
            int h = (int) (interval / HOUR);
            return Integer.valueOf(h) + "h";
        }
        if (interval >= MINUTE) {
            int m = (int) (interval / MINUTE);
            return Integer.valueOf(m) + "m";
        }

        int s = interval >= SECOND ? (int) (interval / SECOND) : 0;
        return Integer.valueOf(s) + "s";
    }

    public static String getTimeString(Context context, long raw) {
        java.text.DateFormat dateFormat = DateFormat.getLongDateFormat(context);
        java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
        String now = dateFormat.format(new Date());
        String date = dateFormat.format(new Date(raw));
        String time = timeFormat.format(new Date(raw));
        return now.equals(date) ? time : String.format("%s, %s", date, time);
    }

    public static String getISO8601Date( long value ) {
        Date date = new Date( value );
        return ISO8601.format(date);
    }

    public static long getISO8601Date( String value ) {
        if( value == null ) {
            return 0;
        }
        try {
            Date date = ISO8601.parse( value );
            return date == null ? 0 : date.getTime();
        } catch( ParseException exception ) {
            return 0;
        }
    }

    /**
     * Returns string representation of date as defined by {@link #HEADER_DATE_FORMAT}. For today's
     * date and yesterday's date returns strings "Today", "Yesterday" respectively.
     */
    public static CharSequence getMessageGroupDate(@NonNull Context context, long time) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(time);
        Calendar now = Calendar.getInstance();

        Resources resources = context.getResources();

        CharSequence result;
        if (now.get(Calendar.DATE) == calendar.get(Calendar.DATE)
                && now.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
            result = resources.getString(R.string.call_log_header_today);
        } else if (now.get(Calendar.DATE) - calendar.get(Calendar.DATE) == 1
                && now.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
                && now.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) {
            result = resources.getString(R.string.call_log_header_yesterday);
        } else {
            result = DateFormat.format(HEADER_DATE_FORMAT, calendar);
        }

        return result;
    }

    /**
     * Returns string representation of time as defined by {@link #MESSAGE_TIME_FORMAT}.
     */
    public static CharSequence getMessageTimeFormat(long time) {
        String format = Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR1
                ? MESSAGE_TIME_FORMAT : MESSAGE_TIME_FORMAT_JELLYBEAN;
        return DateFormat.format(format, time);
    }
}
