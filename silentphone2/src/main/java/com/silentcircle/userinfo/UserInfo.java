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

/**
 * This is a class that has a 1:1 representation of the "v1/me" API as classes,
 * meant to be used in conjunction with the {@link com.google.gson.Gson} library
 * for cleanliness and excellent maintainability
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
}
