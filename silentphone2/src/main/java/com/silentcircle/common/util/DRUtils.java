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

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.userinfo.LoadUserInfo;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import zina.ZinaNative;

/**
 * Utilities to use for DR.
 */
public class DRUtils {

    private DRUtils() {
    }

    /**
     * Determine whether calling is blocked by local or remote DR.
     */
    public static boolean isCallingDrBlocked(@NonNull Activity activity, String partner) {
//        // call cannot be placed as it is retained locally which is prohibited by user
//        if ((LoadUserInfo.isLrcm() && LoadUserInfo.isBlmr())
//                || (LoadUserInfo.isLrcp() && LoadUserInfo.isBldr())) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_dr_enabled, android.R.string.ok, -1);
//            return true;
//        }
//        // call cannot be placed as it is retained by remote party which is prohibited by user
//        if (remoteDRBlocksCall(partner)) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_remote_dr_enabled, android.R.string.ok, -1);
//            return true;
//        }
        return false;
    }

    /**
     * Determine whether calling is blocked by local DR.
     */
    public static boolean isCallingDrBlocked(@NonNull Activity activity) {
//        boolean result = false;
//        // call cannot be placed as it is retained locally which is prohibited by user
//        if ((LoadUserInfo.isLrcm() && LoadUserInfo.isBlmr())
//                || (LoadUserInfo.isLrcp() && LoadUserInfo.isBldr())) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_dr_enabled, android.R.string.ok, -1);
//            result = true;
//        }
//        return result;
        return false;
    }

    /**
     * Determine whether calling is blocked by remote DR.
     */
    public static boolean isCallingRemoteDrBlocked(@NonNull Activity activity, String partner) {
//        // call cannot be placed as it is retained by remote party which is prohibited by user
//        if (remoteDRBlocksCall(partner)) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_remote_dr_enabled, android.R.string.ok, -1);
//            return true;
//        }
        return false;
    }

    private static boolean remoteDRBlocksCall(String partner) {
//        AsyncTasks.UserInfo userInfo = getCachedUserInfo(partner);
//        return userInfo != null
//                && ((userInfo.rrcm && LoadUserInfo.isBrmr())
//                || (userInfo.rrcp && LoadUserInfo.isBrdr()));
        return false;
    }

    /**
     * Determine whether calling is blocked by local or remote DR.
     */
    public static boolean isMessagingDrBlocked(@NonNull Activity activity, String partner) {
//        // message cannot be sent as it is retained locally which is prohibited by user
//        if ((LoadUserInfo.isLrmm() && LoadUserInfo.isBlmr())
//                || ((LoadUserInfo.isLrmp() | LoadUserInfo.isLrap()) && LoadUserInfo.isBldr())) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_dr_enabled, android.R.string.ok, -1);
//            return true;
//        }
//        // message cannot be sent as it is retained by remote party which is prohibited by user
//        if (remoteDRBlocksMessaging(partner)) {
//            InfoMsgDialogFragment.showDialog(activity, R.string.information_dialog,
//                    R.string.dialog_message_remote_dr_enabled, android.R.string.ok, -1);
//            return true;
//        }
        return false;
    }

    public static boolean isLocalDrEnabled() {
//        return (LoadUserInfo.isLrap() | LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp()
//                | LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp());
        return false;
    }

    public static boolean isLocalCallDrEnabled() {
//        return (LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp());
        return false;
    }

    public static boolean isLocalMessagingDrEnabled() {
//        return (LoadUserInfo.isLrap() | LoadUserInfo.isLrmm() | LoadUserInfo.isLrmp());
        return false;
    }

    /**
     * Check whether communication is blocked for this device.
     *
     * Communication is blocked if local data retention is enabled and user/server has blocked
     * local data retention in settings.
     */
    public static boolean isDrBlockedAndEnabled() {
//        return isLocalCallDrBlockedAndEnabled() && isLocalMessagingDrBlockedAndEnabled();
        return false;
    }

    public static boolean isLocalCallDrBlockedAndEnabled() {
//        return ((LoadUserInfo.isBldr() | LoadUserInfo.isBlmr())
//                && (isLocalCallDrEnabled()));
        return false;
    }

    public static boolean isLocalMessagingDrBlockedAndEnabled() {
//        return ((LoadUserInfo.isBldr() | LoadUserInfo.isBlmr())
//                && (isLocalMessagingDrEnabled()));
        return false;
    }

    private static boolean remoteDRBlocksMessaging(String partner) {
//        AsyncTasks.UserInfo userInfo = getCachedUserInfo(partner);
//        return userInfo != null
//                && ((userInfo.rrmm && LoadUserInfo.isBrmr())
//                || ((userInfo.rrmp | userInfo.rrmp) && LoadUserInfo.isBrdr()));
        return false;
    }

    @Nullable
    public static AsyncTasks.UserInfo getCachedUserInfo(String partner) {
        AsyncTasks.UserInfo userInfo = null;
        byte[] partnerUserInfo = ZinaNative.getUserInfoFromCache(partner);
        if (partnerUserInfo != null) {
            userInfo = AsyncTasks.parseUserInfo(partnerUserInfo);
        }
        return userInfo;
    }

    public static boolean isAnyDataRetainedForUser(@Nullable AsyncTasks.UserInfo userInfo) {
//        return userInfo != null
//                && (userInfo.rrcm | userInfo.rrcp
//                // TODO show banner during call when messaging DR is active as well?
//                | userInfo.rrmm | userInfo.rrmp | userInfo.rrap);
        return false;
    }

    public static class DRMessageHelper {

        private static final String DELIMITER_DASH = " - ";
        private static final String DELIMITER_SPACE = " ";
        private static final String DELIMITER_COMMA = ", ";
        private static final String DELIMITER_PERIOD = ".";
        private static final String DELIMITER_COLON = ": ";

        private static final String NOT_AVAILABLE = "N/A";

        private final Context mContext;

        private String mYourOrganization;
        private String mRemoteOrganization;
        private String mMessagePlaintext;
        private String mMessageMetadata;
        private String mCallRecording;
        private String mCallMetadata;
        private String mMessageAttachments;
        private String mFormatImposesDataRetentionPolicy;
        private String mRetains;
        private String mForUser;

        public DRMessageHelper(@NonNull Context context) {
            mContext = context;

            mYourOrganization = context.getString(R.string.data_retention_your_organization);
            mRemoteOrganization = context.getString(R.string.data_retention_remote_organization);
            mMessagePlaintext = context.getString(R.string.data_retention_message_plaintext);
            mMessageMetadata = context.getString(R.string.data_retention_message_metadata);
            mCallRecording = context.getString(R.string.data_retention_calls);
            mCallMetadata = context.getString(R.string.data_retention_call_metadata);
            mMessageAttachments = context.getString(R.string.data_retention_message_attachments);
            mFormatImposesDataRetentionPolicy = context.getString(R.string.data_retention_data_retained_by);
            mRetains = context.getString(R.string.data_retention_data_retains);
            mForUser = context.getString(R.string.data_retention_for_user);

        }

        public String getLocalRetentionInformation() {
            String result = null;
            boolean isDataRetained =
                    (LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp() | LoadUserInfo.isLrmp()
                            | LoadUserInfo.isLrmm() | LoadUserInfo.isLrap());

            if (isDataRetained) {
                String organization = LoadUserInfo.getRetentionOrganization();
                if (TextUtils.isEmpty(organization)) {
                    organization = mYourOrganization;
                }
                StringBuilder sb = new StringBuilder();
                getRetentionDescription(sb, organization, LoadUserInfo.isLrmp(),
                        LoadUserInfo.isLrmm(), LoadUserInfo.isLrcp(), LoadUserInfo.isLrcm(),
                        LoadUserInfo.isLrap());
                sb.append(DELIMITER_PERIOD);

/*
            // Retaining organization: [N/A | <organization name>]
            sb.append(getContext().getString(R.string.data_retention_retaining_organization))
                    .append(DELIMITER_SPACE)
                    .append(TextUtils.isEmpty(LoadUserInfo.getRetentionOrganization()) ? "N/A" : LoadUserInfo.getRetentionOrganization())
                    .append("\n\n");

            getRetentionDescriptionAsList(sb,
                    LoadUserInfo.isLrmp(), LoadUserInfo.isLrmm(), LoadUserInfo.isLrcp(),
                    LoadUserInfo.isLrcm(), LoadUserInfo.isLrap());
*/

                result = sb.toString();
            }
            return result;
        }

        public String getRemoteRetentionInformation(String partner) {
            String result = null;
            AsyncTasks.UserInfo userInfo = DRUtils.getCachedUserInfo(partner);
            if (userInfo != null
                    && (userInfo.rrmp | userInfo.rrmm | userInfo.rrcp | userInfo.rrcm | userInfo.rrap)) {
                StringBuilder sb = new StringBuilder();
                String organization = userInfo.retentionOrganization;
                if (TextUtils.isEmpty(organization)) {
                    organization = mRemoteOrganization;
                }
                sb.append(mForUser).append(DELIMITER_SPACE).append(userInfo.mDisplayName).append(":\n");
                getRetentionDescription(sb, organization, userInfo.rrmp, userInfo.rrmm,
                        userInfo.rrcp, userInfo.rrcm, userInfo.rrap);
                sb.append(DELIMITER_PERIOD);

/*
            // Retaining organization: [N/A | <organization name>]
            sb.append(getContext().getString(R.string.data_retention_retaining_organization))
                    .append(DELIMITER_SPACE)
                    .append(TextUtils.isEmpty(userInfo.retentionOrganization) ? "N/A" : userInfo.retentionOrganization)
                    .append("\n\n");

            getRetentionDescriptionAsList(sb, userInfo.rrmp, userInfo.rrmm,
                    userInfo.rrcp, userInfo.rrcm, userInfo.rrap);
*/

                result = sb.toString();
            }
            return result;
        }

        private StringBuilder getRetentionDescription(StringBuilder sb, String organization,
                boolean mp, boolean mm, boolean cp, boolean cm, boolean ap) {
            sb.append(String.format(Locale.getDefault(), mFormatImposesDataRetentionPolicy, organization));
            sb.append(mRetains);
            CharSequence delimiter = DELIMITER_SPACE;
            if (mp) {
                sb.append(delimiter).append(mMessagePlaintext);
                delimiter = DELIMITER_COMMA;
            }
            if (mm) {
                sb.append(delimiter).append(mMessageMetadata);
                delimiter = DELIMITER_COMMA;
            }
            if (cp) {
                sb.append(delimiter).append(mCallRecording);
                delimiter = DELIMITER_COMMA;
            }
            if (cm) {
                sb.append(delimiter).append(mCallMetadata);
                delimiter = DELIMITER_COMMA;
            }
            if (ap) {
                sb.append(delimiter).append(mMessageAttachments);
            }
            return sb;
        }

        public StringBuilder getRetainingOrganizationHeader(StringBuilder sb, String... partners) {
            boolean isLocal = false;
            boolean isRemote = false;

            if (!TextUtils.isEmpty(LoadUserInfo.getRetentionOrganization())
                    && isLocalDrEnabled()) {
                isLocal = true;
            }

            if (partners != null) {
                for (String partner : partners) {
                    AsyncTasks.UserInfo userInfo = DRUtils.getCachedUserInfo(partner);
                    if (userInfo != null && !TextUtils.isEmpty(userInfo.retentionOrganization)) {
                        isRemote = true;
                    }
                }
            }

            String organizationsHeader =
                    mContext.getString((isLocal & isRemote)
                            ? R.string .data_retention_data_retention_description_both
                            : (isRemote
                                    ? R.string.data_retention_data_retention_description_remote
                                    : R.string.data_retention_data_retention_description_local));
            sb.append(organizationsHeader).append("\n\n");

            return sb;
        }

        public StringBuilder getRetainingOrganizationDescription(StringBuilder sb, String... partners) {
            StringBuilder sbOrganizations = new StringBuilder();
            String localOrganization = LoadUserInfo.getRetentionOrganization();
            Set<String> organizationsSet = new HashSet<>();

            if (!TextUtils.isEmpty(localOrganization)) {
                organizationsSet.add(localOrganization);
            }

            if (partners != null) {
                for (String partner : partners) {
                    AsyncTasks.UserInfo userInfo = DRUtils.getCachedUserInfo(partner);
                    if (userInfo != null && !TextUtils.isEmpty(userInfo.retentionOrganization)) {
                        organizationsSet.add(userInfo.retentionOrganization);
                    }
                }
            }

            String separator = "";
            for (String organization : organizationsSet) {
                sbOrganizations.append(separator).append(organization);
                separator = DELIMITER_COMMA;
            }

            String organizationsLabel = mContext.getString(organizationsSet.size() > 1
                    ? R.string.data_retention_retaining_organizations
                    : R.string.data_retention_retaining_organization);
            String organizations = sbOrganizations.toString();
            sb.append(organizationsLabel)
                    .append(DELIMITER_COLON)
                    .append(TextUtils.isEmpty(organizations) ? NOT_AVAILABLE : organizations)
                    .append("\n\n");

            return sb;
        }

        public StringBuilder getRetainingOrganizationDescription(StringBuilder sb,
            String organization) {
            sb.append(mContext.getString(R.string.message_info_retaining_organization))
                    .append(DELIMITER_COLON)
                    .append(TextUtils.isEmpty(organization) ? NOT_AVAILABLE : organization).append("\n");
            return sb;
        }

        public StringBuilder getRetentionDescriptionAsList(StringBuilder sb, String... partners) {
            boolean mp = LoadUserInfo.isLrmp();
            boolean mm = LoadUserInfo.isLrmm();
            boolean cp = LoadUserInfo.isLrcp();
            boolean cm = LoadUserInfo.isLrcm();
            boolean ap = LoadUserInfo.isLrap();

            if (partners != null) {
                for (String partner : partners) {
                    AsyncTasks.UserInfo userInfo = DRUtils.getCachedUserInfo(partner);
                    if (userInfo != null) {
                        mp |= userInfo.rrmp;
                        mm |= userInfo.rrmm;
                        cp |= userInfo.rrcp;
                        cm |= userInfo.rrcm;
                        ap |= userInfo.rrap;
                    }
                }
            }
            return getRetentionDescriptionAsList(sb, mp, mm, cp, cm, ap);
        }

        public StringBuilder getRetentionDescriptionAsList(StringBuilder sb,
                boolean mp, boolean mm, boolean cp, boolean cm, boolean ap) {
            sb.append(mContext.getString(R.string.data_retention_data_retained)).append("\n");
            if (!(mp | mm | cp | cm | ap)) {
                sb.append(NOT_AVAILABLE);
            } else {
                if (mp) {
                    sb.append(DELIMITER_DASH).append(mMessagePlaintext).append("\n");
                }
                if (mm) {
                    sb.append(DELIMITER_DASH).append(mMessageMetadata).append("\n");
                }
                if (cp) {
                    sb.append(DELIMITER_DASH).append(mCallRecording).append("\n");
                }
                if (cm) {
                    sb.append(DELIMITER_DASH).append(mCallMetadata).append("\n");
                }
                if (ap) {
                    sb.append(DELIMITER_DASH).append(mMessageAttachments).append("\n");
                }
            }
            return sb;
        }

    }
}
