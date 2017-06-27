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
package com.silentcircle.common.util;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.silentcircle.contacts.utils.PhoneNumberHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

public class StringUtils {
    private final static Pattern LTRIM = Pattern.compile("^\\s+");
    private final static Pattern RTRIM = Pattern.compile("\\s+$");

    private static final int MAX_DISPLAY_NAME_LENGTH = 25;

    private static final int MAX_SHORTENED_NAME_LENGTH = 4;

    private static final Collator STRING_COLLATOR = Collator.getInstance(Locale.getDefault());

    private static class NameComparator implements Comparator<CharSequence> {

        @Override
        public int compare(@NonNull CharSequence lhs, @NonNull CharSequence rhs) {
            return STRING_COLLATOR.compare(lhs, rhs);
        }
    }

    public static final Comparator<CharSequence> NAME_COMPARATOR = new NameComparator();

    public static String ltrim(String s) {
        return LTRIM.matcher(s).replaceAll("");
    }

    public static CharSequence ltrim(CharSequence s) {
        return LTRIM.matcher(s).replaceAll("");
    }

    public static String ltrim(String s, Character c) {
        return s.replaceAll("^" + c + "+", "");
    }

    public static String ltrim(String s, String trim) {
        return s.replaceAll("^" + trim + "+", "");
    }

    public static String rtrim(String s) {
        return RTRIM.matcher(s).replaceAll("");
    }

    public static CharSequence rtrim(CharSequence s) {
        return RTRIM.matcher(s).replaceAll("");
    }

    public static String rtrim(String s, Character c) {
        return s.replaceAll(c + "+$", "");
    }

    public static String rtrim(String s, String trim) {
        return s.replaceAll(trim + "+$", "");
    }

    public static String trim(String s, Character c) {
        return s.replaceAll(c + "+$", "").replaceAll("^" + c + "+", "");
    }

    @NonNull
    public static CharSequence trim(@Nullable CharSequence s) {
        if (s == null) {
            return "";
        }
        return rtrim(ltrim(s));
    }

    @NonNull
    public static String trim(@Nullable String s) {
        if (s == null) {
            return "";
        }
        return rtrim(ltrim(s));
    }

    @Nullable
    public static CharSequence formatDisplayName(@Nullable final CharSequence displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return displayName;
        }

        CharSequence result = trim(displayName);
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            try {
                String[] parts = TextUtils.split(displayName.toString(), " ");
                if (parts != null && parts.length > 1) {
                    result = trim(parts[0]) + " " + Character.toUpperCase(trim(parts[1]).charAt(0)) + ".";
                }
            } catch (IndexOutOfBoundsException e) {
                result = displayName.subSequence(0, MAX_DISPLAY_NAME_LENGTH) + "...";
            }
        }
        return result;
    }

    @NonNull
    public static String formatShortName(@Nullable final String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return "";
        }

        String result = trim(displayName);
        int firstAtSignPosition = result.indexOf('@');
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(result).matches()) {
            String name = firstAtSignPosition > 0 ? result.substring(0, firstAtSignPosition) : "";
            if (name.length() > MAX_SHORTENED_NAME_LENGTH) {
                name = name.substring(0, MAX_SHORTENED_NAME_LENGTH) + "...";
            }
            String domain = firstAtSignPosition + 1 < result.length()
                    ? result.substring(firstAtSignPosition + 1) : "";
            if (domain.length() > MAX_SHORTENED_NAME_LENGTH) {
                domain = domain.substring(0, MAX_SHORTENED_NAME_LENGTH) + "...";
            }
            result = name + "@" + domain;
        }
        else if (PhoneNumberUtils.isGlobalPhoneNumber(result.replaceAll("\\s", ""))) {
            result = PhoneNumberHelper.normalizeNumber(result);
        }
        else {
            int firstSpacePosition = result.indexOf(' ');
            if (firstSpacePosition > 0) {
                result = result.substring(0, firstSpacePosition);
            }
        }
        return result;
    }

    /**
     * Create a Json string from passed list of tag/value pairs. Use this to have special
     * characters in value properly escaped.
     *
     * @param pairs Pairs of tag {@link String} and value {@link Object) to use as content for
     *              created Json.
     * @return {@code null} if no parameters were passed or there was an error creating Json object.
     *              Otherwise return string representation of Json object consisting of passed
     *              tag/value pairs.
     */
    @Nullable
    @SafeVarargs
    public static String jsonFromPairs(Pair<String, Object>... pairs) {
        if (pairs == null || pairs.length == 0) {
            return null;
        }

        String result = null;
        try {
            JSONObject json = new JSONObject();
            for (Pair<String, Object> entry : pairs) {
                json.put(entry.first, entry.second);
            }
            result = json.toString();
        } catch (JSONException exception) {
            // leave result as null
        }
        return result;
    }

    public static Uri removeQueryParameter(@NonNull Uri uri, @NonNull String paramToRemove) {
        final Collection<String> params = uri.getQueryParameterNames();
        final Uri.Builder builder = uri.buildUpon().clearQuery();
        for (String param : params) {
            String value;
            if (param.equals(paramToRemove)) {
                continue;
            } else {
                value = uri.getQueryParameter(param);
            }
            builder.appendQueryParameter(param, value);
        }
        return builder.build();
    }
}