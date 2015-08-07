/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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


package com.silentcircle.contacts.model.account;

import android.content.res.Resources;
import android.text.TextUtils;

import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Im;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Event;
import com.silentcircle.silentphone2.R;

public class LabelHelper {
    private LabelHelper() {}
    
    public static final class PhoneLabel {

        private PhoneLabel() {}
        /**
         * Return the string resource that best describes the given
         * { @link #TYPE}. Will always return a valid resource.
         */
        public static final int getTypeLabelResource(int type) {
            switch (type) {
                case Phone.TYPE_HOME: return R.string.phoneTypeHome;
                case Phone.TYPE_MOBILE: return R.string.phoneTypeMobile;
                case Phone.TYPE_WORK: return R.string.phoneTypeWork;
                case Phone.TYPE_SILENT: return R.string.phoneTypeSilent;
//                case TYPE_FAX_WORK: return com.android.internal.R.string.phoneTypeFaxWork;
//                case TYPE_FAX_HOME: return com.android.internal.R.string.phoneTypeFaxHome;
//                case TYPE_PAGER: return com.android.internal.R.string.phoneTypePager;
//                case TYPE_OTHER: return com.android.internal.R.string.phoneTypeOther;
//                case TYPE_CALLBACK: return com.android.internal.R.string.phoneTypeCallback;
//                case TYPE_CAR: return com.android.internal.R.string.phoneTypeCar;
//                case TYPE_COMPANY_MAIN: return com.android.internal.R.string.phoneTypeCompanyMain;
//                case TYPE_ISDN: return com.android.internal.R.string.phoneTypeIsdn;
//                case TYPE_MAIN: return com.android.internal.R.string.phoneTypeMain;
//                case TYPE_OTHER_FAX: return com.android.internal.R.string.phoneTypeOtherFax;
//                case TYPE_RADIO: return com.android.internal.R.string.phoneTypeRadio;
//                case TYPE_TELEX: return com.android.internal.R.string.phoneTypeTelex;
//                case TYPE_TTY_TDD: return com.android.internal.R.string.phoneTypeTtyTdd;
//                case TYPE_WORK_MOBILE: return com.android.internal.R.string.phoneTypeWorkMobile;
//                case TYPE_WORK_PAGER: return com.android.internal.R.string.phoneTypeWorkPager;
//                case TYPE_ASSISTANT: return com.android.internal.R.string.phoneTypeAssistant;
//                case TYPE_MMS: return com.android.internal.R.string.phoneTypeMms;
                default: return R.string.phoneTypeCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given type,
         * possibly substituting the given { @link #LABEL} value
         * for { @link #TYPE_CUSTOM}.
         */
        public static final CharSequence getTypeLabel(Resources res, int type, CharSequence label) {
            if (type == Phone.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            } else {
                final int labelRes = getTypeLabelResource(type);
                return res.getText(labelRes);
            }
        }
    }

    public static final class EmailLabel {

        private EmailLabel() {}

        /**
         * Return the string resource that best describes the given { @link #TYPE}. Will always return a valid resource.
         */
        public static final int getTypeLabelResource(int type) {
            switch (type) {
             case Email.TYPE_HOME: return R.string.emailTypeHome;
             case Email.TYPE_WORK: return R.string.emailTypeWork;
             case Email.TYPE_OTHER: return R.string.emailTypeOther;
             case Email.TYPE_MOBILE: return R.string.emailTypeMobile;
            default:
                return R.string.emailTypeCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given type, possibly substituting the given { @link #LABEL} value
         * for { @link #TYPE_CUSTOM}.
         */
        public static final CharSequence getTypeLabel(Resources res, int type, CharSequence label) {
            if (type == Email.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            }
            else {
                final int labelRes = getTypeLabelResource(type);
                return res.getText(labelRes);
            }
        }
    }
    
    public static final class StructuredPostalLabel {

        private StructuredPostalLabel() {}

        /**
         * Return the string resource that best describes the given
         * { @link #TYPE}. Will always return a valid resource.
         */
        public static final int getTypeLabelResource(int type) {
            switch (type) {
                case StructuredPostal.TYPE_HOME: return R.string.postalTypeHome;
                case StructuredPostal.TYPE_WORK: return R.string.postalTypeWork;
                case StructuredPostal.TYPE_OTHER: return R.string.postalTypeOther;
                default: return R.string.postalTypeCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given type,
         * possibly substituting the given { @link #LABEL} value
         * for { @link #TYPE_CUSTOM}.
         */
        public static final CharSequence getTypeLabel(Resources res, int type,
                CharSequence label) {
            if (type == StructuredPostal.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            } else {
                final int labelRes = getTypeLabelResource(type);
                return res.getText(labelRes);
            }
        }
    }
    
    public static final class ImLabel {

        private ImLabel() {}
    
        /**
         * Return the string resource that best describes the given
         * { @link #TYPE}. Will always return a valid resource.
         */
        public static final int getTypeLabelResource(int type) {
            switch (type) {
                case Im.TYPE_HOME: return R.string.imTypeHome;
                case Im.TYPE_WORK: return R.string.imTypeWork;
                case Im.TYPE_OTHER: return R.string.imTypeOther;
                case Im.TYPE_SILENT: return R.string.imTypeSilent;
                default: return R.string.imTypeCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given type,
         * possibly substituting the given { @link #LABEL} value
         * for { @link #TYPE_CUSTOM}.
         */
        public static final CharSequence getTypeLabel(Resources res, int type,
                CharSequence label) {
            if (type == Im.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            } else {
                final int labelRes = getTypeLabelResource(type);
                return res.getText(labelRes);
            }
        }

        /**
         * Return the string resource that best describes the given
         * { @link #PROTOCOL}. Will always return a valid resource.
         */
        public static final int getProtocolLabelResource(int type) {
            switch (type) {
                case Im.PROTOCOL_AIM: return R.string.imProtocolAim;
                case Im.PROTOCOL_MSN: return R.string.imProtocolMsn;
                case Im.PROTOCOL_YAHOO: return R.string.imProtocolYahoo;
                case Im.PROTOCOL_SKYPE: return R.string.imProtocolSkype;
                case Im.PROTOCOL_QQ: return R.string.imProtocolQq;
                case Im.PROTOCOL_GOOGLE_TALK: return R.string.imProtocolGoogleTalk;
                case Im.PROTOCOL_ICQ: return R.string.imProtocolIcq;
                case Im.PROTOCOL_JABBER: return R.string.imProtocolJabber;
                case Im.PROTOCOL_NETMEETING: return R.string.imProtocolNetMeeting;
                case Im.PROTOCOL_SILENT: return R.string.imProtocolSilent;
                default: return R.string.imProtocolCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given
         * protocol, possibly substituting the given
         * { @link #CUSTOM_PROTOCOL} value for { @link #PROTOCOL_CUSTOM}.
         */
        public static final CharSequence getProtocolLabel(Resources res, int type,
                CharSequence label) {
            if (type == Im.PROTOCOL_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            } else {
                final int labelRes = getProtocolLabelResource(type);
                return res.getText(labelRes);
            }
        }
    }
    
    public static class EventLabel {
        /**
         * Return the string resource that best describes the given
         * {@link #TYPE}. Will always return a valid resource.
         */
        public static int getTypeResource(Integer type) {
            if (type == null) {
                return R.string.eventTypeOther;
            }
            switch (type) {
                case Event.TYPE_ANNIVERSARY: return R.string.eventTypeAnniversary;
                case Event.TYPE_BIRTHDAY: return R.string.eventTypeBirthday;
                case Event.TYPE_OTHER: return R.string.eventTypeOther;
                default: return R.string.eventTypeCustom;
            }
        }

        /**
         * Return a {@link CharSequence} that best describes the given type,
         * possibly substituting the given {@link #LABEL} value
         * for {@link #TYPE_CUSTOM}.
         */
        public static final CharSequence getTypeLabel(Resources res, int type,
                                                      CharSequence label) {
            if (type == Event.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
                return label;
            } else {
                final int labelRes = getTypeResource(type);
                return res.getText(labelRes);
            }
        }


    }
}
