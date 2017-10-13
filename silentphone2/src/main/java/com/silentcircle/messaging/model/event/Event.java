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
package com.silentcircle.messaging.model.event;


import android.text.TextUtils;

import com.silentcircle.common.StringByteHolder;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.Burnable;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.UUIDGen;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class Event extends Burnable implements Comparable<Event> {

    protected static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yy hh:mm:ss.SSS", Locale.getDefault());

    public static final Event NONE = new Event();
    private final StringByteHolder conversationID = new StringByteHolder();
    private final StringByteHolder id = new StringByteHolder();
    private long time = System.currentTimeMillis();
    private final StringByteHolder text = new StringByteHolder();

    private final StringByteHolder attributes = new StringByteHolder();
    private final StringByteHolder attachment = new StringByteHolder();

    protected EventDeviceInfo[] eventDeviceInfo;

    protected long composeTime;

    /**
     * Compares two events by timestamps obtained from their UUID ids.
     *
     * Sorts younger items after older items as this is used in conversation view.
     */
    public static class EventIdComparator implements Comparator<Event> {

        @Override
        public int compare(Event lhs, Event rhs) {
            if (lhs == null && rhs == null) {
                return 0;
            }
            if (lhs == null || TextUtils.isEmpty(lhs.getId())) {
                return -1;
            }
            if (rhs == null || TextUtils.isEmpty(rhs.getId())) {
                return 1;
            }
            long timestamp1 = lhs.getComposeTime();
            long timestamp2 = rhs.getComposeTime();
            return timestamp1 < timestamp2 ? -1 : (timestamp1 == timestamp2 ? 0 : 1);
        }
    }

    public static final EventIdComparator EVENT_ID_COMPARATOR = new EventIdComparator();

    public Event() {
    }

    public Event(String text) {
        setText(text);
    }

    protected static String toString(byte[] value) {
        return value == null ? null : IOUtils.toString(value);
    }

    public void clear() {
        this.removeID();
        this.removeConversationID();
        this.removeText();
        this.removeAttachment();
        this.removeAttribute();
        this.time = 0L;
        composeTime = 0L;
    }

    public int compareTo(Event another) {
        if (another == null) {
            return -1;
        } else {
            long dt = this.getTime() - another.getTime();
            return dt < 0L ? -1 : (dt > 0L ? 1 : this.getId().compareTo(another.getId()));
        }
    }

    public boolean equals(Object o) {
        return o != null && this.hashCode() == o.hashCode();
    }

    public String getConversationID() {
        return this.conversationID.getString();
    }

    public byte[] getConversationIDAsByteArray() {
        return this.conversationID.getByteArray();
    }

    public String getId() {
        return this.id.getString();
    }

    public byte[] getIDAsByteArray() {
        return this.id.getByteArray();
    }

    public String getText() {
        return this.text.getString();
    }

    public byte[] getTextAsByteArray() {
        return this.text.getByteArray();
    }

    public long getTime() {
        return this.time;
    }

    public long getComposeTime() {
        if (composeTime == 0) {
            try {
                composeTime = UUIDGen.getAdjustedTimestamp(UUID.fromString(getId()));
            } catch (Exception e) {
                // failed to determine compose time, return 0 for unknown
                composeTime = 0;
            }
        }
        return composeTime;
    }

    public int hashCode() {
        return this.id.getString() == null ? (this.time == 0L ? 0 : (int) this.time)
                : this.id.getString().hashCode();
    }

    public boolean hasText() {
        return this.getText() != null;
    }

    public void removeConversationID() {
        burn(getConversationIDAsByteArray());
        this.conversationID.set((String) null);
    }

    public void removeID() {
        burn(getIDAsByteArray());
        this.id.set((String) null);
    }

    public void removeText() {
        burn(getTextAsByteArray());
        this.text.set((String) null);
    }

    public void setConversationID(byte[] conversationID) {
        this.conversationID.set(conversationID);
    }

    public void setConversationID(String conversationID) {
        this.conversationID.set(conversationID);
    }

    public void setId(byte[] id) {
        this.id.set(id);
    }

    public void setId(String id) {
        this.id.set(id);
    }

    public void setText(byte[] text) {
        this.text.set(text);
    }

    public void setText(String text) {
        this.text.set(text);
    }

    public void setAttributes(byte[] attributes) {
        this.attributes.set(attributes);
    }

    public void setAttributes(String attributes) {
        this.attributes.set(attributes);
    }

    public String getAttributes() {
        String attributes = this.attributes.getString();
        return (attributes == null) ? "" : attributes;
    }

    public byte[] getAttributesAsByteArray() {
        return this.attributes.getByteArray();
    }

    public void removeAttribute() {
        burn(getAttributesAsByteArray());
        this.attributes.set((String) null);
    }

    public void setAttachment(byte[] attachment) {
        this.attachment.set(attachment);
    }

    public void setAttachment(String attachment) {
        this.attachment.set(attachment);
    }

    public String getAttachment() {
        return this.attachment.getString();
    }

    public byte[] getAttachmentAsByteArray() {
        return this.attachment.getByteArray();
    }

    public void removeAttachment() {
        burn(getAttachmentAsByteArray());
        this.attachment.set((String) null);
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String toFormattedString() {
        long composeTime = getComposeTime();
        return "Id: " + getId() + "\n"
                + "Time: " + DATE_FORMAT.format(new Date(getTime())) + "\n"
                + "Text: " + getText() + "\n"
                + "Compose time: " + (composeTime  == 0 ? "Unknown" : DATE_FORMAT.format(new Date(composeTime))) + "\n";
    }

    public EventDeviceInfo[] getEventDeviceInfo() {
        return eventDeviceInfo;
    }

    public void setEventDeviceInfo(EventDeviceInfo[] info) {
        eventDeviceInfo = info;
    }
}
