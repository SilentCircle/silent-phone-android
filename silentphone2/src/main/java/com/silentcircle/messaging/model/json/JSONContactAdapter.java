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

import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Device;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class JSONContactAdapter extends JSONAdapter {

    private final JSONDeviceAdapter deviceAdapter = new JSONDeviceAdapter();

    public static final String TAG_CONTACT_ALIAS = "alias";
    public static final String TAG_CONTACT_DEVICE = "device";
    public static final String TAG_CONTACT_USER_NAME = "username";
    public static final String TAG_CONTACT_DISPLAY_NAME = "display_name";
    public static final String TAG_CONTACT_IS_VALIDATED = "is_validated";
    public static final String TAG_CONTACT_IS_GROUP = "is_group";
    public static final String TAG_CONTACT_DEVICES = "devices";

    public JSONObject adapt(Contact contact) {
        JSONObject json = new JSONObject();
        try {
            json.put(TAG_CONTACT_ALIAS, contact.getAlias());
            json.put(TAG_CONTACT_DEVICE, contact.getDevice());
            json.put(TAG_CONTACT_USER_NAME, contact.getUserId());
            json.put(TAG_CONTACT_DISPLAY_NAME, contact.getDisplayName());
            json.put(TAG_CONTACT_IS_VALIDATED, contact.isValidated());
            json.put(TAG_CONTACT_IS_GROUP, contact.isGroup());
            json.put(TAG_CONTACT_DEVICES, deviceAdapter.adapt(contact.getDeviceInfo()));
        } catch (JSONException exception) {
            // This should never happen because we control all of the keys.
        }
        return json;
    }

    public Contact adapt(JSONObject json) {

        Contact contact = new Contact(getString(json, TAG_CONTACT_USER_NAME));
        contact.setAlias(getString(json, TAG_CONTACT_ALIAS));
        contact.setDevice(getString(json, TAG_CONTACT_DEVICE));
        contact.setDisplayName(getString(json, TAG_CONTACT_DISPLAY_NAME));
        contact.setValidated(getBoolean(json, TAG_CONTACT_IS_VALIDATED));
        contact.setGroup(getBoolean(json, TAG_CONTACT_IS_GROUP, false));
        JSONArray devicesJson = getJSONArray(json, TAG_CONTACT_DEVICES);
        if (devicesJson != null && devicesJson.length() > 0) {
            for (int i = 0; i < devicesJson.length(); i++) {
                try {
                    JSONObject deviceJson = devicesJson.getJSONObject(i);
                    Device device = deviceAdapter.adapt(deviceJson);
                    contact.addDeviceInfo(device);
                } catch (JSONException exception) {
                }
            }
        }
        return contact;
    }
}
