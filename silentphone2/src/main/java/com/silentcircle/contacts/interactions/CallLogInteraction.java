/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.silentcircle.contacts.interactions;


import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.telephony.PhoneNumberUtils;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.util.Log;

import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.model.account.LabelHelper;
import com.silentcircle.silentcontacts2.ScCallLog.ScCalls;
import com.silentcircle.silentphone2.R;

/**
 * Represents a call log event interaction, wrapping the columns in
 * {@link android.provider.CallLog.Calls}.
 *
 * This class does not return log entries related to voicemail or SIP calls. Additionally,
 * this class ignores number presentation. Number presentation affects how to identify phone
 * numbers. Since, we already know the identity of the phone number owner we can ignore number
 * presentation.
 *
 * As a result of ignoring voicemail and number presentation, we don't need to worry about API
 * version.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CallLogInteraction implements ContactInteraction {

    private static final String URI_TARGET_PREFIX = "tel:";
    private static final int CALL_LOG_ICON_RES = R.drawable.ic_phone_24dp;
    private static final int CALL_LOG_SIP_ICON_RES = R.drawable.ic_dialer_sip_black_24dp;
//    private static final int CALL_ARROW_ICON_RES = R.drawable.ic_call_arrow;
    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    private ContentValues mValues;

    public CallLogInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        String number = getNumber();
        return number == null ? null : ContactsUtils.getCallIntent(number);
    }

    @Override
    public String getViewHeader(Context context) {
        return getNumber();
    }

    @Override
    public long getInteractionDate() {
        Long date = getDate();
        return date == null ? -1 : date;
    }

    @Override
    public String getViewBody(Context context) {
        Integer numberType = getCachedNumberType();
        if (numberType == null) {
            return null;
        }
        return LabelHelper.PhoneLabel.getTypeLabel(context.getResources(), getCachedNumberType(),
                getCachedNumberLabel()).toString();
    }

    @Override
    public String getViewFooter(Context context) {
        Long date = getDate();
        return date == null ? null : ContactInteractionUtil.formatDateStringFromTimestamp(
                date, context);
    }

    @Override
    public Drawable getIcon(Context context) {
        if (PhoneNumberUtils.isGlobalPhoneNumber(getRawNumber()))
            return context.getResources().getDrawable(CALL_LOG_ICON_RES);
        else
            return context.getResources().getDrawable(CALL_LOG_SIP_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        Drawable callArrow = null;
        Resources res = context.getResources();
        Integer type = getType();
        if (type == null) {
            return null;
        }
        switch (type) {
            case ScCalls.INCOMING_TYPE:
                callArrow = res.getDrawable(R.drawable.ic_call_incoming_holo_dark);  //  res.getDrawable(CALL_ARROW_ICON_RES);
//                callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
//                        PorterDuff.Mode.MULTIPLY);
//                break;
            case ScCalls.MISSED_TYPE:
                callArrow = res.getDrawable(R.drawable.ic_call_missed_holo_dark); // res.getDrawable(CALL_ARROW_ICON_RES);
//                callArrow.setColorFilter(res.getColor(R.color.call_arrow_red),
//                        PorterDuff.Mode.MULTIPLY);
                break;
            case ScCalls.OUTGOING_TYPE:
                callArrow = res.getDrawable(R.drawable.ic_call_outgoing_holo_dark); //  BitmapUtil.getRotatedDrawable(res, CALL_ARROW_ICON_RES, 180f);
//                callArrow.setColorFilter(res.getColor(R.color.call_arrow_green),
//                        PorterDuff.Mode.MULTIPLY);
                break;
        }
        return callArrow;
    }

    public String getCachedName() {
        return mValues.getAsString(ScCalls.CACHED_NAME);
    }

    public String getCachedNumberLabel() {
        return mValues.getAsString(ScCalls.CACHED_NUMBER_LABEL);
    }

    public Integer getCachedNumberType() {
        return mValues.getAsInteger(ScCalls.CACHED_NUMBER_TYPE);
    }

    public Long getDate() {
        return mValues.getAsLong(ScCalls.DATE);
    }

    public Long getDuration() {
        return mValues.getAsLong(ScCalls.DURATION);
    }

    public Boolean getIsRead() {
        return mValues.getAsBoolean(ScCalls.IS_READ);
    }

    public Integer getLimitParamKey() {
        return mValues.getAsInteger(ScCalls.LIMIT_PARAM_KEY);
    }

    public Boolean getNew() {
        return mValues.getAsBoolean(ScCalls.NEW);
    }

    private String getRawNumber() {
        return mValues.getAsString(ScCalls.NUMBER);
    }

    public String getNumber() {
        return sBidiFormatter.unicodeWrap(
                mValues.getAsString(ScCalls.NUMBER), TextDirectionHeuristics.LTR);
    }

//    public Integer getNumberPresentation() {
//        return mValues.getAsInteger(ScCalls.NUMBER_PRESENTATION);
//    }
//
//    public Integer getOffsetParamKey() {
//        return mValues.getAsInteger(ScCalls.OFFSET_PARAM_KEY);
//    }

    public Integer getType() {
        return mValues.getAsInteger(ScCalls.TYPE);
    }

    @Override
    public String getContentDescription(Context context) {
        String callDetails = getCallTypeString(context) + ". " + getViewFooter(context) + ". " +
                getViewHeader(context) + ". " + getViewFooter(context);
        return context.getResources().getString(R.string.content_description_recent_call,
                callDetails);
    }

    private String getCallTypeString(Context context) {
        String callType = "";
        Resources res = context.getResources();
        Integer type = getType();
        if (type == null) {
            return callType;
        }
        switch (type) {
            case ScCalls.INCOMING_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_incoming);
                break;
            case ScCalls.MISSED_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_missed);
                break;
            case ScCalls.OUTGOING_TYPE:
                callType = res.getString(R.string.content_description_recent_call_type_outgoing);
                break;
        }
        return callType;
    }

    @Override
    public int getIconResourceId() {
        return CALL_LOG_ICON_RES;
    }
}
