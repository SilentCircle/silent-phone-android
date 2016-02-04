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
package com.silentcircle.messaging.model.event;

public enum EventType {
    EVENT(0, Event.class),
    MESSAGE(1, Message.class),
    INCOMING_MESSAGE(17, IncomingMessage.class),
    OUTGOING_MESSAGE(33, OutgoingMessage.class),
    ERROR_EVENT(48, ErrorEvent.class),
    CALL(80, Call.class),
    INCOMING_CALL(81, IncomingCall.class),
    OUTGOING_CALL(82, OutgoingCall.class);

    private final int value;
    private final Class<? extends Event> _class;

    public static EventType forClass(Class<? extends Event> _class) {
        EventType[] var1 = values();
        int var2 = var1.length;

        for (int var3 = 0; var3 < var2; ++var3) {
            EventType type = var1[var3];
            if (type._class.equals(_class)) {
                return type;
            }
        }

        return null;
    }

    public static EventType forValue(int value) {
        EventType[] var1 = values();
        int var2 = var1.length;

        for (int var3 = 0; var3 < var2; ++var3) {
            EventType type = var1[var3];
            if (type.value == value) {
                return type;
            }
        }

        return null;
    }

    private EventType(int value, Class<? extends Event> _class) {
        this.value = value;
        this._class = _class;
    }

    public int value() {
        return this.value;
    }
}
