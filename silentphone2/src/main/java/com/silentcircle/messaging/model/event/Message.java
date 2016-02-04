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

import com.silentcircle.messaging.model.Location;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.util.IOUtils;

import java.util.ArrayList;
import java.util.List;

public class Message extends Event {

    public static final long DEFAULT_EXPIRATION_TIME = java.lang.Long.MAX_VALUE;
    @MessageStates.MessageState protected int state;
    protected byte[] sender;
    protected byte[] ciphertext;
    protected long burnNotice;
    private long expirationTime;
    private long deliveryTime;
    private List<Long> netMessageIds = new ArrayList<>();
    private Location location;

    private String metaData;

    private boolean mRequestReceipt;

    public Message() {
        this.state = MessageStates.UNKNOWN;
        this.expirationTime = DEFAULT_EXPIRATION_TIME;
    }

    public void clear() {
        super.clear();
        this.removeSender();
        this.removeCiphertext();
        this.removeMessageState();
        this.burnNotice = 0L;
        this.expirationTime = DEFAULT_EXPIRATION_TIME;
        this.deliveryTime = 0L;
        this.netMessageIds = new ArrayList<>();
    }

    public boolean expires() {
        return this.getBurnNotice() > 0L;
    }

    public long getBurnNotice() {
        return this.burnNotice;
    }

    public String getCiphertext() {
        return toString(this.getCiphertextAsByteArray());
    }

    public byte[] getCiphertextAsByteArray() {
        return this.ciphertext;
    }

    public long getDeliveryTime() {
        return this.deliveryTime;
    }

    public long getExpirationTime() {
        return this.expirationTime;
    }

    public String getSender() {
        return toString(this.getSenderAsByteArray());
    }

    public byte[] getSenderAsByteArray() {
        return this.sender;
    }

    public Location getLocation() {
        return location;
    }

    public String getMetaData() { return metaData; }

    @MessageStates.MessageState
    public int getState() {
        return this.state;
    }

    public void setState(@MessageStates.MessageState int state) {
        this.state = state;
    }

    public boolean isDelivered() {
        return this.getDeliveryTime() > 0L;
    }

    public boolean isExpired() {
        return this.expires() && this.getExpirationTime() <= System.currentTimeMillis();
    }

    public boolean isRequestReceipt() {
        return mRequestReceipt;
    }

    public boolean hasAttachment() { return attachment != null; }

    public boolean hasMetaData() {
        return metaData != null;
    }

    public void setRequestReceipt(boolean mRequestReceipt) {
        this.mRequestReceipt = mRequestReceipt;
    }

    public void removeCiphertext() {
        burn(this.ciphertext);
        this.ciphertext = null;
    }

    public void removeMessageState() {
        this.state = MessageStates.UNKNOWN;
    }

    public void removeSender() {
        burn(this.sender);
        this.sender = null;
    }

    public void setBurnNotice(long burnNotice) {
        this.burnNotice = burnNotice;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext = ciphertext;
    }

    public void setCiphertext(CharSequence ciphertext) {
        this.setCiphertext(IOUtils.toByteArray(ciphertext));
    }

    public void setDeliveryTime(long deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public void setSender(byte[] sender) {
        this.sender = sender;
    }

    public void setSender(CharSequence sender) {
        this.setSender(IOUtils.toByteArray(sender));
    }

    public void setLocation(final Location location) {
        this.location = location;
    }

    public void setMetaData(final String metaData) {
        this.metaData = metaData;
    }

    public List<Long> getNetMessageIds() {
        return netMessageIds;
    }

    public void addNetMessageId(long netMessageId) {
        this.netMessageIds.add(netMessageId);
    }
}

