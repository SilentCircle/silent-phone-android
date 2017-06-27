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
package com.silentcircle.messaging.util;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public enum Action {

    UPDATE_CONVERSATION("UPDATE_CONVERSATION"),
    BEGIN_DEACTIVATE("BEGIN_DEACTIVATE"),
    FINISH_DEACTIVATE("FINISH_DEACTIVATE"),
    CONVERSATION_EVENT("CONVERSATION_EVENT"),
    VIEW_CONVERSATIONS("VIEW_CONVERSATIONS"),
    NEW_CONVERSATION("NEW_CONVERSATION"),
    CLOSE_CONVERSATION("CLOSE_CONVERSATION"),
    TRANSITION("TRANSITION"),
    VERIFY("VERIFY"),
    SAVE_STATE("SAVE_STATE"),
    ERROR("ERROR"),
    WARNING("WARNING"),
    SEND_MESSAGE("SEND_MESSAGE"),
    RECEIVE_MESSAGE("RECEIVE_MESSAGE"),
    RECEIVE_ATTACHMENT("RECEIVE_ATTACHMENT"),
    CONNECT("CONNECT"),
    DISCONNECT("DISCONNECT"),
    XMPP_STATE_CHANGED("XMPP_STATE_CHANGED"),
    SYSTEM_NET_CHANGE("SYSTEM_NET_CHANGE"),
    CANCEL("CANCEL"),
    PROGRESS("PROGRESS"),
    UPLOAD("UPLOAD"),
    DOWNLOAD("DOWNLOAD"),
    DOWNLOAD_THUMBNAIL("DOWNLOAD_THUMBNAIL"),
    AUDIO_CAPTURE("AUDIO_CAPTURE"),
    VIDEO_CAPTURE("VIDEO_CAPTURE"),
    ENCRYPT("ENCRYPT"),
    LOCK("LOCK"),
    PURGE_ATTACHMENTS("PURGE_ATTACHMENTS"),
    RUN_ATTACHMENT_HANDLER("RUN_ATTACHMENT_HANDLER"),
    NOTIFY("NOTIFY"),
    REFRESH_SELF("REFRESH_SELF"),
    WIPE("WIPE"),
    DEV("DEV"),
    DATA_RETENTION_EVENT("DATA_RETENTION_EVENT"),
    CREATE_GROUP_CONVERSATION("CREATE_GROUP_CONVERSATION"),
    _INVALID_("_INVALID");

    public static Action from(Intent intent) {
        return from(intent.getAction());
    }

    public static Action from(String name) {
        for (Action action : Action.values()) {
            if (action.getName().equals(name)) {
                return action;
            }
        }
        return _INVALID_;
    }

    @NonNull
    public static IntentFilter filter(@Nullable Action... actions) {
        IntentFilter filter = new IntentFilter();
        if (actions != null) {
            for (Action action : actions) {
                filter.addAction(action.getName());
            }
        }
        return filter;
    }

    private final String name;

    Action(@NonNull String name) {
        this.name = String.format("com.silentcircle.messaging.action.%s", name);
    }

    @NonNull
    public IntentFilter filter() {
        return new IntentFilter(getName());
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public Intent intent() {
        return new Intent(getName());
    }

    public Intent intent(Context context, Class<?> targetClass) {
        return new Intent(context, targetClass).setAction(getName());
    }

}

