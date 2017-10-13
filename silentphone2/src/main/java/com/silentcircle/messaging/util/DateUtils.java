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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.format.DateFormat;


import com.silentcircle.silentphone2.R;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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

    private static DateUtils mSharedInstance;

    public static DateUtils getSharedInstance(Context context) {
        if (mSharedInstance == null) {
            mSharedInstance = new DateUtils(context);
        }
        return mSharedInstance;
    }

    private final String mStringHeaderToday;
    private final String mStringHeaderYesterday;

    public DateUtils(final @NonNull Context context) {
        Resources resources = context.getResources();
        mStringHeaderToday = resources.getString(R.string.call_log_header_today);
        mStringHeaderYesterday = resources.getString(R.string.call_log_header_yesterday);
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

    /*
    public static String getTimeString(Context context, long raw) {
        java.text.DateFormat dateFormat = DateFormat.getLongDateFormat(context);
        java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
        String now = dateFormat.format(new Date());
        String date = dateFormat.format(new Date(raw));
        String time = timeFormat.format(new Date(raw));
        return now.equals(date) ? time : String.format("%s, %s", date, time);
    }
     */

    public static String getTimeString(@NonNull Context context, long interval) {
        Resources resources = context.getResources();
        if (interval >= DAY) {
            final int d = (int) (interval / DAY);
            final int h = (int)((interval % DAY) / HOUR);
            String dh = resources.getQuantityString(R.plurals.time_days, d, d);
            if (h > 0)
                dh += " " + resources.getQuantityString(R.plurals.time_hours, h, h);
            return dh;
        }
        if (interval >= HOUR) {
            final int h = (int) (interval / HOUR);
            final int m = (int)((interval % HOUR) / MINUTE);
            String hm = resources.getQuantityString(R.plurals.time_hours, h, h);
            if (m > 0)
                hm += " " + resources.getQuantityString(R.plurals.time_minutes, m, m);
            return hm;
        }
        if (interval >= MINUTE) {
            final int m = (int) (interval / MINUTE);
            final int s = (int)((interval % MINUTE) / SECOND);
            String ms = resources.getQuantityString(R.plurals.time_minutes, m, m);
            if (s > 0)
                ms += " " + resources.getQuantityString(R.plurals.time_seconds, s, s);
            return ms;
        }

        int s = interval >= SECOND ? (int) (interval / SECOND) : 0;
        return resources.getQuantityString(R.plurals.short_time_seconds, s, s);
    }

    public static String getTimeString(@NonNull Context context, long interval, boolean withFraction) {
        Resources resources = context.getResources();
        if (interval >= DAY) {
            final int d = (int) (interval / DAY);
            final int h = (int)((interval % DAY) / HOUR);
            String dh = resources.getQuantityString(R.plurals.time_days, d, d);
            if (withFraction && h > 0)
                dh += " " + resources.getQuantityString(R.plurals.time_hours, h, h);
            return dh;
        }
        if (interval >= HOUR) {
            final int h = (int) (interval / HOUR);
            final int m = (int)((interval % HOUR) / MINUTE);
            String hm = resources.getQuantityString(R.plurals.time_hours, h, h);
            if (withFraction && m > 0)
                hm += " " + resources.getQuantityString(R.plurals.time_minutes, m, m);
            return hm;
        }
        if (interval >= MINUTE) {
            final int m = (int) (interval / MINUTE);
            final int s = (int)((interval % MINUTE) / SECOND);
            String ms = resources.getQuantityString(R.plurals.time_minutes, m, m);
            if (withFraction && s > 0)
                ms += " " + resources.getQuantityString(R.plurals.time_seconds, s, s);
            return ms;
        }

        int s = interval >= SECOND ? (int) (interval / SECOND) : 0;
        return resources.getQuantityString(R.plurals.short_time_seconds, s, s);
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
    public CharSequence getMessageGroupDate(long time) {
        LocalDate calendar = new LocalDate(time);
        LocalDate now = LocalDate.now();

        CharSequence result;
        if (calendar.equals(now)) {
            result = mStringHeaderToday;
        } else if (now.minusDays(1).equals(calendar)) {
            result = mStringHeaderYesterday;
        } else {
            result = DateTimeFormat.forPattern(HEADER_DATE_FORMAT).print(new DateTime(time));
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

    public static CharSequence formatDuration(Context context, long millis) {
        final Resources res = context.getResources();
        if (millis >= DAY) {
            final int days = (int) ((millis + (DAY / 2)) / DAY);
            return res.getQuantityString(R.plurals.duration_days, days, days);
        } else if (millis >= HOUR) {
            final int hours = (int) ((millis + (HOUR / 2)) / HOUR);
            return res.getQuantityString(R.plurals.duration_hours, hours, hours);
        } else if (millis >= MINUTE) {
            final int minutes = (int) ((millis + (MINUTE / 2)) / MINUTE);
            return res.getQuantityString(R.plurals.duration_minutes, minutes, minutes);
        } else {
            final int seconds = (int) ((millis + (SECOND / 2)) / SECOND);
            return res.getQuantityString(R.plurals.duration_seconds, seconds, seconds);
        }
    }
}
