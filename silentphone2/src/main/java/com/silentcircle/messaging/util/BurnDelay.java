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
import android.text.format.DateUtils;
import android.util.SparseArray;

import com.silentcircle.silentphone2.R;

public class BurnDelay {

    private static class DefaultBurnDelay extends BurnDelay {

        private static final long MINUTE = 60;
        private static final long HOUR = 60 * MINUTE;
        private static final long DAY = 24 * HOUR;
        private static final long WEEK = 7 * DAY;
        private static final long MONTH = 30 * DAY;
        private static final long YEAR = 365 * DAY;

        private static final long DEFAULT_MAX_DELAY = 3 * MONTH;

        public DefaultBurnDelay() {
            int level = 0;
            /*
             * For now do not allow messages without burn notice.
             * put(level++, 0);
             */
            put(level++, MINUTE);
            put(level++, 5 * MINUTE);
            put(level++, 15 * MINUTE);
            put(level++, 30 * MINUTE);
            put(level++, HOUR);
            put(level++, 3 * HOUR);
            put(level++, 6 * HOUR);
            put(level++, 12 * HOUR);
            put(level++, DAY);
            put(level++, 2 * DAY);
            put(level++, 3 * DAY);
            put(level++, 4 * DAY);
            put(level++, WEEK);
            put(level++, 2 * WEEK);
            put(level++, MONTH);
            put(level++, DEFAULT_MAX_DELAY);
            /*
             * For now leave maximum to 3 months
             * put(level++, 6 * MONTH);
             * put(level++, YEAR);
             */
        }
    }

    public static final BurnDelay Defaults = new DefaultBurnDelay();

    private static final int DEFAULT_LEVEL = 10; // 3 day default

    private static CharSequence _pluralize(long value, CharSequence word) {
        return value == 1 ? word : word + "s"; // localize??
    }

    public static long getDefaultDelay() {
        return Defaults.getDelay(DEFAULT_LEVEL);
    }

    private static CharSequence getTimeSpanString(long time, long now) {
        // to whom it may concern:
        // DateUtils getRelativeTimeSpanString() doesn't correctly format relative dates
        // above a week
        // this is implemented here (in English) for time spans not supported
        // also the case of 1 day (which would DateUtils would produce "tomorrow"
        // TODO: this needs to be localized. See DateUtils.getRelativeDayString() for example.
        long timeSpanSecs = (time - now) / 1000;
        if (timeSpanSecs == DefaultBurnDelay.DAY) {
            return "in 1 day"; // override to not display "tomorrow", localize this?
        } else if (timeSpanSecs >= DefaultBurnDelay.YEAR) {
            long numYears = timeSpanSecs / DefaultBurnDelay.YEAR;
            return "in " + numYears + _pluralize(numYears, " year"); // localize
        } else if (timeSpanSecs >= DefaultBurnDelay.MONTH) {
            long numMonths = timeSpanSecs / DefaultBurnDelay.MONTH;
            return "in " + numMonths + _pluralize(numMonths, " month"); // localize
        } else if (timeSpanSecs >= DefaultBurnDelay.WEEK) {
            long numWeeks = timeSpanSecs / DefaultBurnDelay.WEEK;
            return "in " + numWeeks + _pluralize(numWeeks, " week"); // localize
        } else {
            return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.SECOND_IN_MILLIS);
        }
    }

    private final SparseArray<Long> levels = new SparseArray<Long>();

    public long getDelay(int level) {
        return levels.get(level).longValue();
    }

    public String getLabel(Context context, int level) {
        long delay = getDelay(level);
        if (delay <= 0) {
            return context.getResources().getString(R.string.no_expiration);
        }
        long now = System.currentTimeMillis();
        long time = now + delay * 1000;
        return context.getResources().getString(R.string.messages_expire, getTimeSpanString(time, now));
    }

    public String getAlternateLabel(Context context, int level) {
        long delay = getDelay(level);
        if (delay <= 0) {
            return context.getResources().getString(R.string.no_expiration);
        }
        long time = delay * 1000;
        return com.silentcircle.messaging.util.DateUtils.formatDuration(context, time).toString();
    }

    public int getLevel(long delay) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.valueAt(i).longValue() == delay) {
                return i;
            }
        }
        return DEFAULT_LEVEL;
    }

    public int numLevels() {
        return levels.size();
    }

    public void put(int level, long delay) {
        levels.put(level, Long.valueOf(delay));
    }

    public void setMaxDelay(long delay) {
        put(numLevels() - 1, delay);
    }
}
