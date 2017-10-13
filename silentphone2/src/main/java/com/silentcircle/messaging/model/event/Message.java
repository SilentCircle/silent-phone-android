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

import com.silentcircle.common.StringByteHolder;
import com.silentcircle.messaging.model.Location;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.RetentionInfo;
import com.silentcircle.messaging.util.DateUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Message extends Event {

    public static final long DEFAULT_EXPIRATION_TIME = java.lang.Long.MAX_VALUE;

    // TODO better wording might be pending read/burn notification
    public static final long FAILURE_READ_NOTIFICATION = 1;
    public static final long FAILURE_BURN_NOTIFICATION = 2;
    public static final long FAILURE_NOT_SENT = 3;

    @MessageStates.MessageState protected int state;
    private final StringByteHolder sender = new StringByteHolder();
    private final StringByteHolder ciphertext = new StringByteHolder();
    protected long burnNotice;
    private long expirationTime;
    private long deliveryTime;
    private Location location;
    private Set<Long> failures = new HashSet<>();
    private boolean isRetained;
    private RetentionInfo retentionInfo;

    private final StringByteHolder metaData = new StringByteHolder();

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
        this.failures = new HashSet<>();
        this.retentionInfo = null;
    }

    public boolean expires() {
        return this.getBurnNotice() > 0L;
    }

    public long getBurnNotice() {
        return this.burnNotice;
    }

    public String getCiphertext() {
        return this.ciphertext.getString();
    }

    public byte[] getCiphertextAsByteArray() {
        return this.ciphertext.getByteArray();
    }

    public long getDeliveryTime() {
        return this.deliveryTime;
    }

    public long getExpirationTime() {
        return this.expirationTime;
    }

    public long getReadTime() {
        /* When burn will be optional, a separate field will be necessary */
        return (DEFAULT_EXPIRATION_TIME == getExpirationTime())
                ? 0 : (getExpirationTime() - TimeUnit.SECONDS.toMillis(getBurnNotice()));
    }

    public String getSender() {
        return this.sender.getString();
    }

    public byte[] getSenderAsByteArray() {
        return this.sender.getByteArray();
    }

    public Location getLocation() {
        return location;
    }

    public String getMetaData() {
        return this.metaData.getString();
    }

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

    public boolean hasBurnNotice() {
        return this.getBurnNotice() > 0L;
    }

    public boolean hasLocation() {
        return this.getLocation() != null;
    }

    public boolean hasAttachment() {
        return getAttachment() != null;
    }

    public boolean hasMetaData() {
        return getMetaData() != null;
    }

    public void setRequestReceipt(boolean mRequestReceipt) {
        this.mRequestReceipt = mRequestReceipt;
    }

    public void removeCiphertext() {
        burn(getCiphertextAsByteArray());
        this.ciphertext.set((String) null);
    }

    public void removeMessageState() {
        this.state = MessageStates.UNKNOWN;
    }

    public void removeSender() {
        burn(getSenderAsByteArray());
        this.sender.set((String) null);
    }

    public void setBurnNotice(long burnNotice) {
        this.burnNotice = burnNotice;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext.set(ciphertext);
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext.set(ciphertext);
    }

    public void setDeliveryTime(long deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public void setSender(byte[] sender) {
        this.sender.set(sender);
    }

    public void setSender(String sender) {
        this.sender.set(sender);
    }

    public void setLocation(final Location location) {
        this.location = location;
    }

    public void setMetaData(final String metaData) {
        this.metaData.set(metaData);
    }

    public void setFailureFlag(long failure) {
        failures.add(failure);
    }

    public void clearFailureFlag(long failure) {
        failures.remove(failure);
    }

    public Long[] getFailureFlagsAsArray() {
        return failures.size() > 0 ? failures.toArray(new Long[failures.size()]) : new Long[]{};
    }

    public Collection<Long> getFailureFlags() {
        return failures;
    }

    public boolean hasFailureFlagSet(long flag) {
        return failures.contains(flag);
    }

    public void setFailureFlags(Long[] flags) {
        failures = new HashSet<>();
        Collections.addAll(failures, flags);
    }

    public boolean isRetained() {
        return /**isRetained;**/false;
    }

    public void setRetained(boolean retained) {
        isRetained = retained;
    }

    public RetentionInfo getRetentionInfo() {
        return /**retentionInfo;**/null;
    }

    public void setRetentionInfo(RetentionInfo info) {
        retentionInfo = info;
    }

    public String toFormattedString() {
        long deliveryTime = getDeliveryTime();
        RetentionInfo retentionInfo = getRetentionInfo();
        return super.toFormattedString()
                + "Sender: " + getSender() + "\n"
                + (deliveryTime == 0
                    ? ""
                    : "Delivery time: " + DATE_FORMAT.format(new Date(getDeliveryTime())) + "\n")
                + ((DEFAULT_EXPIRATION_TIME == getExpirationTime())
                    ? ""
                    : "Expiration time: " + android.text.format.DateUtils.getRelativeTimeSpanString(
                        getExpirationTime(),
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS) + "\n"
                    + "Read time: " + DATE_FORMAT.format(new Date(getReadTime())) + "\n")
                + "Burn time: " + DateUtils.getShortTimeString(TimeUnit.SECONDS.toMillis(getBurnNotice())) + "\n"
                /* + "Read receipt requested: " + isRequestReceipt() + "\n" */
                + "Has attachment: " + hasAttachment() + "\n"
                + "Is retained: " + isRetained() + "\n"
                + (retentionInfo != null ? retentionInfo : "");
    }

}
