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

import com.silentcircle.messaging.model.Device;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Adapter for conversation's device list.
 */
public class JSONDeviceAdapter extends JSONAdapter {

    public static final String TAG_DEVICE_NAME = "name";
    public static final String TAG_DEVICE_ID = "device_id";
    public static final String TAG_DEVICE_KEY = "identity_key";
    public static final String TAG_DEVICE_ZRTP_VERIFICATION_STATUS = "zrtp_verification_status";

    public JSONObject adapt(Device device) {
        JSONObject json = new JSONObject();
        try {
            json.put(TAG_DEVICE_NAME, device.getName());
            json.put(TAG_DEVICE_ID, device.getDeviceId());
            json.put(TAG_DEVICE_KEY, device.getIdentityKey());
            json.put(TAG_DEVICE_ZRTP_VERIFICATION_STATUS, device.getZrtpVerificationState());
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return json;
    }

    public JSONArray adapt(List<Device> devices) {
        JSONArray json = new JSONArray();
        if (devices != null) {
            for (Device device : devices) {
                json.put(adapt(device));
            }
        }
        return json;
    }

    public Device adapt(JSONObject json) {
        return new Device(getString(json, TAG_DEVICE_NAME),
                getString(json, TAG_DEVICE_ID),
                getString(json, TAG_DEVICE_KEY),
                getString(json, TAG_DEVICE_ZRTP_VERIFICATION_STATUS));
    }
}
