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
package com.silentcircle.userinfo;

import android.content.Context;
import com.silentcircle.logs.Log;

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
 *
 * {
 *   "display_alias": "nodid1",
 *   "display_name": "nodid1",
 *   "uuid": "nodid1",
 *   "dr_enabled": false,
 *   "display_plan": "Silent Circle Mobile (Annual)",
 *   "devices": {
 *     "d3c7ce9ef00a86c1184377348e66452c": {"prekeys": 86},
 *     "16716b81f2c49328c6ece9365e755b7f": {"prekeys": 100}
 *   },
 *   "display_tn": null,
 *   "avatar_url": null,
 *   "display_organization": null,
 *   "permissions": {
 *     "maximum_burn_sec": 7776000,
 *     "outbound_calling_pstn": false,
 *     "outbound_calling": true,
 *     "create_conference": true,
 *     "outbound_messaging": true,
 *     "send_attachment": true,
 *     "initiate_video": true
 *   },
 *   "subscription":{
 *     "usage_details": {
 *       "minutes_left": 0,
 *       "base_minutes": 0,
 *       "current_modifier": 0
 *     },
 *     "expires": "2018-07-08T00:00:00Z",
 *     "autorenew": true,
 *     "state": "paying",
 *     "model": "plan",
 *     "balance": {
 *       "amount": "0.00",
 *       "unit": "USD"
 *     }
 *   },
 *   "data_retention": {
 *     "for_org_name": "Subman",
 *     "retained_data": {
 *       "attachment_plaintext": false,
 *       "call_metadata": true,
 *       "call_plaintext": false,
 *       "message_metadata": true,
 *       "message_plaintext": false
 *     }
 *   }
 * }
 */
@SuppressWarnings("unused")
public class UserInfo {
    private String uuid;
    private String display_tn;
    private String display_alias;
    private String display_name;
    private String display_plan;
    private String display_organization;
    private String avatar_url;
    private boolean dr_enabled;

    private Subscription subscription;
    private Permissions permissions;
    private DataRetention data_retention;

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
        private boolean outbound_messaging;
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

        public boolean isOutboundMessaging() {
            return outbound_messaging;
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

    public class DataRetention {
        private String for_org_name;
        private RetentionFlags retained_data;
        private BlockRetentionFlags block_retention_of;

        public String getForOrgName() {
            return for_org_name;
        }

        public RetentionFlags getRetentionFlags() {
            return retained_data;
        }

        public BlockRetentionFlags getBlockRetentionFlags() {
            return block_retention_of;
        }

        public class RetentionFlags {
            private boolean attachment_plaintext;
            private boolean call_plaintext;
            private boolean call_metadata;
            private boolean message_metadata;
            private boolean message_plaintext;

            public boolean isAttachmentPlaintext() {
                return attachment_plaintext;
            }

            public boolean isCallMetadata() {
                return call_metadata;
            }

            public boolean isCallPlaintext() {
                return call_plaintext;
            }

            public boolean isMessageMetadata() {
                return message_metadata;
            }

            public boolean isMessagePlaintext() {
                return message_plaintext;
            }

        }

        public class BlockRetentionFlags {
            private boolean remote_metadata;
            private boolean remote_data;
            private boolean local_metadata;
            private boolean local_data;

            public boolean isRemoteMetadataBlocked() {
                return remote_metadata;
            }

            public boolean isRemoteDataBlocked() {
                return remote_data;
            }

            public boolean isLocalMetadataBlocked() {
                return local_metadata;
            }

            public boolean isLocalDataBlocked() {
                return local_data;
            }
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

    public String getDisplayPlan() {
        return display_plan;
    }

    public String getDisplayOrg() {
        return display_organization;
    }

    public String getAvatarUrl() {
        return avatar_url;
    }

    public boolean getDrEnabled() {
        return dr_enabled;
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

    public DataRetention getDataRetention() { return data_retention; }


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
