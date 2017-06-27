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
package com.silentcircle.messaging.model.json;

import android.support.annotation.Nullable;

import com.silentcircle.messaging.model.RetentionInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Adapter for event's retention info.
 */
public class JSONRetentionInfoAdapter extends JSONAdapter {

    public static final String TAG_LOCAL_RETENTION_DATA = "local_retention_data";
    public static final String TAG_REMOTE_RETENTION_DATA = "remote_retention_data";
    public static final String TAG_ORGANIZATION_NAME = "for_org_name";
    public static final String TAG_RETAINED_DATA= "retained_data";
    public static final String TAG_MESSAGE_METADATA = "message_metadata";
    public static final String TAG_MESSAGE_PLAINTEXT = "message_plaintext";
    public static final String TAG_CALL_METADATA = "call_metadata";
    public static final String TAG_CALL_PLAINTEXT = "call_plaintext";
    public static final String TAG_ATTACHMENTS = "attachment_plaintext";

    public JSONObject adapt(RetentionInfo retentionInfo) {
        JSONObject json = new JSONObject();
        try {
            if (retentionInfo.localRetentionData != null) {
                json.put(TAG_LOCAL_RETENTION_DATA, adapt(retentionInfo.localRetentionData));
            }
            if (retentionInfo.remoteRetentionData != null) {
                json.put(TAG_REMOTE_RETENTION_DATA, adapt(retentionInfo.remoteRetentionData));
            }
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return json;
    }

    @Nullable
    public RetentionInfo adapt(@Nullable JSONObject json) {
        if (json == null) {
            return null;
        }

        RetentionInfo retentionInfo = new RetentionInfo();
        JSONObject retentionData = json.optJSONObject(TAG_LOCAL_RETENTION_DATA);
        if (retentionData != null) {
            retentionInfo.localRetentionData = adapt(retentionData, new RetentionInfo.DataRetention());
        }
        ArrayList<RetentionInfo.DataRetention> remoteRetentionDataList = new ArrayList<>();
        JSONArray remoteRetentionData = json.optJSONArray(TAG_REMOTE_RETENTION_DATA);
        if (remoteRetentionData != null) {
            for (int i = 0; i < remoteRetentionData.length(); i++) {
                try {
                    retentionData = remoteRetentionData.getJSONObject(i);
                    if (retentionData == null) {
                        continue;
                    }
                    RetentionInfo.DataRetention item = adapt(retentionData,
                            new RetentionInfo.DataRetention());
                    remoteRetentionDataList.add(item);
                } catch (JSONException e) {
                    // Ignore failure to parse an element
                }
            }
        }
        if (remoteRetentionDataList.size() > 0) {
            retentionInfo.remoteRetentionData =
                    remoteRetentionDataList.toArray(
                            new RetentionInfo.DataRetention[remoteRetentionDataList.size()]);
        }
        return retentionInfo.localRetentionData != null
                || retentionInfo.remoteRetentionData != null ? retentionInfo : null;
    }

    public RetentionInfo.DataRetention adapt(JSONObject json, RetentionInfo.DataRetention to) {
        to.setForOrgName(json.optString(TAG_ORGANIZATION_NAME));
        to.setRetentionFlags(adapt(json.optJSONObject(TAG_RETAINED_DATA),
                new RetentionInfo.DataRetention.RetentionFlags()));
        return to;
    }

    public RetentionInfo.DataRetention.RetentionFlags adapt(@Nullable JSONObject json,
            RetentionInfo.DataRetention.RetentionFlags to) {
        if (json == null) {
            return to;
        }
        try {
            to.setMessageMetadata(json.getBoolean(TAG_MESSAGE_METADATA));
            to.setMessagePlaintext(json.getBoolean(TAG_MESSAGE_PLAINTEXT));
            to.setCallMetadata(json.getBoolean(TAG_CALL_METADATA));
            to.setCallPlaintext(json.getBoolean(TAG_CALL_PLAINTEXT));
            to.setAttachmentPlaintext(json.getBoolean(TAG_ATTACHMENTS));
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return to;
    }

    @Nullable
    private JSONArray adapt(@Nullable RetentionInfo.DataRetention[] dataRetention) {
        if (dataRetention == null) {
            return null;
        }

        JSONArray json = new JSONArray();
        for (RetentionInfo.DataRetention data : dataRetention) {
            if (data != null) {
                json.put(adapt(data));
            }
        }
        return json;
    }

    @Nullable
    private JSONObject adapt(@Nullable RetentionInfo.DataRetention dataRetention) {
        if (dataRetention == null) {
            return null;
        }

        JSONObject json = new JSONObject();
        try {
            json.put(TAG_ORGANIZATION_NAME, dataRetention.getForOrgName());
            json.put(TAG_RETAINED_DATA, adapt(dataRetention.getRetentionFlags()));
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return json;
    }

    @Nullable
    private JSONObject adapt(@Nullable RetentionInfo.DataRetention.RetentionFlags flags) {
        if (flags == null) {
            return null;
        }
        JSONObject json = new JSONObject();
        try {
            json.put(TAG_MESSAGE_METADATA, flags.isMessageMetadata());
            json.put(TAG_MESSAGE_PLAINTEXT, flags.isMessagePlaintext());
            json.put(TAG_CALL_METADATA, flags.isCallMetadata());
            json.put(TAG_CALL_PLAINTEXT, flags.isCallPlaintext());
            json.put(TAG_ATTACHMENTS, flags.isAttachmentPlaintext());
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return json;
    }
}
