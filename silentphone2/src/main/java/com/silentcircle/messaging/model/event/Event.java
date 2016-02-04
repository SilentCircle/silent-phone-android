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


import android.text.TextUtils;

import com.silentcircle.messaging.model.Burnable;
import com.silentcircle.messaging.util.IOUtils;

import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

public class Event extends Burnable implements Comparable<Event> {
    public static final Event NONE = new Event();
    private byte[] conversationID;
    protected byte[] id;
    protected long time = System.currentTimeMillis();
    protected byte[] text;

    protected byte[] attributes;
    protected byte[] attachment;

    /**
     * Compares two events by timestamps obtained from their UUID ids.
     *
     * Sorts younger items below older items as this is used in conversation view.
     */
    public static class EventIdComparator implements Comparator<Event> {

        @Override
        public int compare(Event lhs, Event rhs) {
            if (lhs == null || TextUtils.isEmpty(lhs.getId())) {
                return -1;
            }
            if (rhs == null || TextUtils.isEmpty(rhs.getId())) {
                return 1;
            }
            UUID uuid1 = UUID.fromString(lhs.getId());
            UUID uuid2 = UUID.fromString(rhs.getId());
            long timestamp1 = uuid1.timestamp();
            long timestamp2 = uuid2.timestamp();
            return timestamp1 < timestamp2 ? 1 : (timestamp1 == timestamp2 ? 0 : -1);
        }
    }

    public static final EventIdComparator EVENT_ID_COMPARATOR = new EventIdComparator();

    public Event() {
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
        return toString(this.getConversationIDAsByteArray());
    }

    public byte[] getConversationIDAsByteArray() {
        return this.conversationID;
    }

    public String getId() {
        return toString(this.getIDAsByteArray());
    }

    public byte[] getIDAsByteArray() {
        return this.id;
    }

    public String getText() {
        return toString(this.getTextAsByteArray());
    }

    public byte[] getTextAsByteArray() {
        return this.text;
    }

    public long getTime() {
        return this.time;
    }

    public int hashCode() {
        return this.id == null ? (this.time == 0L ? 0 : (int) this.time) : Arrays.hashCode(this.id);
    }

    public boolean hasText() {
        return this.getText() != null;
    }

    public void removeConversationID() {
        burn(this.conversationID);
        this.conversationID = null;
    }

    public void removeID() {
        burn(this.id);
        this.id = null;
    }

    public void removeText() {
        burn(this.text);
        this.text = null;
    }

    public void setConversationID(byte[] conversationID) {
        this.conversationID = conversationID;
    }

    public void setConversationID(CharSequence conversationID) {
        this.setConversationID(IOUtils.toByteArray(conversationID));
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public void setId(CharSequence id) {
        this.setId(IOUtils.toByteArray(id));
    }

    public void setText(byte[] text) {
        this.text = text;
    }

    public void setText(CharSequence text) {
        this.setText(IOUtils.toByteArray(text));
    }

    public void setAttributes(byte[] text) {
        this.attributes = text;
    }

    public void setAttributes(String text) {
        this.setAttributes(IOUtils.encode(text));
    }

    public String getAttributes() {
        byte[] attributes = this.getAttributesAsByteArray();
        if (attributes == null) {
            attributes = new byte[0];
        }
        return new String(attributes);
    }

    public byte[] getAttributesAsByteArray() {
        return this.attributes;
    }

    public void removeAttribute() {
        burn(this.attributes);
        this.attributes = null;
    }

    public void setAttachment(byte[] text) {
        this.attachment = text;
    }

    public void setAttachment(String text) {
        this.setAttachment(IOUtils.encode(text));
    }

    public String getAttachment() {
        byte[] attachment = getAttachmentAsByteArray();
        return attachment != null ? new String(attachment) : null;
    }

    public byte[] getAttachmentAsByteArray() {
        return this.attachment;
    }

    public void removeAttachment() {
        burn(this.attachment);
        this.attachment = null;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
