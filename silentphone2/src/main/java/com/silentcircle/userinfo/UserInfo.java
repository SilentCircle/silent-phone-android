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

package com.silentcircle.userinfo;

import android.content.Context;
import android.util.Log;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Utilities;

import java.io.IOException;

/**
 * This is a class that has a 1:1 representation of the "v1/me" API as classes,
 * meant to be used in conjunction with the {@link com.google.gson.Gson} library
 * for cleanliness and excellent maintainability
 * {"display_alias": "nodid2", "display_name": "nodid2", "uuid": "nodid2",
 * "devices": {"ffb980158c0d45b7098fb3c85637ccf7": {"prekeys": 1364}, "44935f4beeeac03dae8e8afb8d6567ec": {"prekeys": 99}, "3754f9ad5fad2f5658105a5aa48a7616": {"prekeys": 91}, "b6af036da117da378bb5c54d3cc3997b": {"prekeys": 48}},
 * "display_tn": null, "avatar_url": null,
 * "permissions": {"maximum_burn_sec": 7776000, "outbound_calling_pstn": false, "outbound_calling": true, "create_conference": true, "initiate_video": true, "send_attachment": true},
 * "subscription": {"usage_details": {"minutes_left": 0, "base_minutes": 0, "current_modifier": 0}, "expires": "2016-07-08T00:00:00Z", "autorenew": true, "state": "paying", "model": "plan", "balance": {"amount": "0.00", "unit": "USD"}}}

 */
@SuppressWarnings("unused")
public class UserInfo {
    private String uuid;
    private String display_tn;
    private String display_alias;
    private String display_name;
    private String avatar_url;

    private Subscription subscription;
    private Permissions permissions;

    @SerializedName("devices")
    private DeviceInfoMe devices;

    public class Subscription {
        private String state;
        private String model;
        private String expires;
        private boolean autorenew;

        private UsageDetails usage_details;
        private Balance balance;

        public class UsageDetails {
            private int minutes_left;
            private int base_minutes;
            private int current_modifier;

            public int getMinutesLeft() {
                return minutes_left;
            }

            public int getBaseMinutes() {
                return base_minutes;
            }

            public int getCurrentModifier() {
                return current_modifier;
            }
        }

        public class Balance {
            private double amount;
            private String unit;

            public double getAmount() {
                return amount;
            }

            public String getUnit() {
                return unit;
            }
        }

        public String getState() {
            return state;
        }

        public String getModel() {
            return model;
        }

        public String getExpires() {
            return expires;
        }

        public boolean isAutorenew() {
            return autorenew;
        }

        public UsageDetails getUsageDetails() {
            return usage_details;
        }

        public Balance getBalance() {
            return balance;
        }
    }

    public class Permissions {
        private boolean outbound_calling_pstn;
        private boolean outbound_calling;
        private boolean initiate_video;
        private boolean create_conference;
        private boolean send_attachment;
        private int maximum_burn_sec;

        public boolean isOutboundCallingPstn() {
            return outbound_calling_pstn;
        }

        public boolean isOutboundCalling() {
            return outbound_calling;
        }

        public boolean isInitiateVideo() {
            return initiate_video;
        }

        public boolean isCreateConference() {
            return create_conference;
        }

        public boolean isSendAttachment() {
            return send_attachment;
        }

        public int getMaximumBurnSec() {
            return maximum_burn_sec;
        }
    }

    public String getUuid() {
        return uuid;
    }

    public String getDisplayTn() {
        return display_tn;
    }

    public String getDisplayAlias() {
        return display_alias;
    }

    public String getDisplayName() {
        return display_name;
    }

    public String getAvatarUrl() {
        return avatar_url;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public Permissions getPermissions() {
        return permissions;
    }

    public DeviceInfoMe getDevices() {
        return devices;
    }


    /*
     * The DevicesDeserializer gets the following JSON object (example) :
     * {"0a0a0a058c0d45b7098fb3c8561c1c1c": {"prekeys": 1364},
     *  "0a0a0a0beeeac03dae8e8afb8d1c1c1c": {"prekeys": 99},
     *  "0a0a0a0d5fad2f5658105a5aa41c1c1c": {"prekeys": 91},
     *  "0a0a0a0da117da378bb5c54d3c1c1c1c": {"prekeys": 48}
     *  }
     *
     *  The first hex-string is a device id that defines to which device the next
     *  object belongs. The deserializer filters the data to get the info for this
     *  device. It gets the devices' id and  uses it as filter argument and skips
     *  all non-matching items.
     */
    public static class DevicesDeserializer extends TypeAdapter<DeviceInfoMe> {
        private final Context mContext;

        public DevicesDeserializer(Context ctx) {
            mContext = ctx;
        }

        public DeviceInfoMe read(JsonReader reader) throws IOException {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                return null;
            }

            String devId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(mContext, false));
            if (devId == null)
                devId = "";

            DeviceInfoMe device = null;
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (devId.equals(name)) {
                    device = fillDeviceInfo(reader);
                    if (ConfigurationUtilities.mTrace)
                        Log.d("UserInfo", String.format("Available pre-keys for device %s (%s): %d",
                                name, devId, device != null ? device.getPreKeys() : -1));
                }
                else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return device;
        }

        private DeviceInfoMe fillDeviceInfo(JsonReader reader) throws IOException {
            DeviceInfoMe device = new DeviceInfoMe();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("prekeys".equals(name)) {
                    device.setPreKeys(reader.nextInt());
                }
            }
            reader.endObject();
            return device;
        }

        public void write(JsonWriter writer, DeviceInfoMe value) throws IOException {
            throw new IOException("Serializing not supported for DeviceInfo");
        }
    }
}
