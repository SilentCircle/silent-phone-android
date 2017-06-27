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
package com.silentcircle.messaging.model;


import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

/**
 * Structure to hold retention data for a message.
 */
public class RetentionInfo {

    @SerializedName("local_retention_data")
    public DataRetention localRetentionData;

    @SerializedName("remote_retention_data")
    public DataRetention[] remoteRetentionData;

    public static class DataRetention {

        @SerializedName("for_org_name")
        private String organization;

        @SerializedName("retained_data")
        private RetentionFlags retaindData;

        public String getForOrgName() {
            return organization;
        }

        public void setForOrgName(String organization) {
            this.organization = organization;
        }

        public void setRetentionFlags(RetentionFlags flags) {
            retaindData = flags;
        }

        public void setRetentionFlags(boolean isMm, boolean isMp, boolean isCm, boolean isCp, boolean isAp) {
            retaindData = new RetentionFlags();
            retaindData.setAttachmentPlaintext(isAp);
            retaindData.setCallPlaintext(isCp);
            retaindData.setCallMetadata(isCm);
            retaindData.setMessageMetadata(isMm);
            retaindData.setMessagePlaintext(isMp);
        }

        public RetentionFlags getRetentionFlags() {
            return retaindData;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            String organization = getForOrgName();
            if (TextUtils.isEmpty(organization)) {
                organization = "N/A";
            }
            sb.append("Retaining organization: ").append(organization).append("\n");
            sb.append("Retained data:");
            RetentionFlags flags = getRetentionFlags();
            if (flags != null) {
                if (!(flags.isMessagePlaintext() | flags.isMessageMetadata() | flags.isCallMetadata()
                        | flags.isCallPlaintext() | flags.isAttachmentPlaintext())) {
                    sb.append("N/A");
                } else {
                    sb.append("\n");
                    if (flags.isMessagePlaintext()) {
                        sb.append(" - ").append("message plaintext").append("\n");
                    }
                    if (flags.isMessageMetadata()) {
                        sb.append(" - ").append("message metadata").append("\n");
                    }
                    if (flags.isCallPlaintext()) {
                        sb.append(" - ").append("call audio").append("\n");
                    }
                    if (flags.isCallMetadata()) {
                        sb.append(" - ").append("call metadata").append("\n");
                    }
                    if (flags.isAttachmentPlaintext()) {
                        sb.append(" - ").append("message attachments").append("\n");
                    }
                }
            }
            return sb.toString();
        }


        public static class RetentionFlags {
            @SerializedName("attachment_plaintext")
            private boolean attachments;

            @SerializedName("call_plaintext")
            private boolean callPlaintext;

            @SerializedName("call_metadata")
            private boolean callMetadata;

            @SerializedName("message_metadata")
            private boolean messageMetadata;

            @SerializedName("message_plaintext")
            private boolean messagePlaintext;

            public boolean isAttachmentPlaintext() {
                return attachments;
            }

            public boolean isCallMetadata() {
                return callMetadata;
            }

            public boolean isCallPlaintext() {
                return callPlaintext;
            }

            public boolean isMessageMetadata() {
                return messageMetadata;
            }

            public boolean isMessagePlaintext() {
                return messagePlaintext;
            }

            public void setAttachmentPlaintext(boolean value) {
                this.attachments = value;
            }

            public void setCallPlaintext(boolean value) {
                this.callPlaintext = value;
            }

            public void setCallMetadata(boolean value) {
                this.callMetadata = value;
            }

            public void setMessageMetadata(boolean value) {
                this.messageMetadata = value;
            }

            public void setMessagePlaintext(boolean value) {
                this.messagePlaintext = value;
            }
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (localRetentionData != null) {
            sb.append("Local data retention:").append("\n").append(localRetentionData).append("\n\n");
        }
        if (remoteRetentionData != null) {
            sb.append("Remote data retention:").append("\n");
            for (DataRetention dataRetention : remoteRetentionData) {
                if (dataRetention != null) {
                    sb.append(dataRetention).append("\n");
                }
            }
        }
        return sb.toString();
    }
}
