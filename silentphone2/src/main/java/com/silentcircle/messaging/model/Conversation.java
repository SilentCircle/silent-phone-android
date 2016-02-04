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
package com.silentcircle.messaging.model;

import android.text.TextUtils;
import android.util.Base64;

import java.util.Arrays;

import com.silentcircle.messaging.util.IOUtils;

public class Conversation extends Burnable implements Comparable<Conversation> {

    private static String toString(byte[] value) {
        return value == null ? null : new String(value);
    }

    protected byte[] id;
    protected Contact partner;
    protected boolean locationEnabled;
    protected boolean burnNotice;
    protected boolean sendReadReceipts;
    protected long burnDelay;
    private int unreadMessageCount;
    private byte[] previewEventID;
    private long lastModified;
    private int failures;
    private String mUnsentText;

    @Override
    public void clear() {
        removeID();
        removePartner();
        locationEnabled = false;
        burnNotice = false;
        sendReadReceipts = false;
        burnDelay = 0;
        unreadMessageCount = 0;
        removePreviewEventID();
        lastModified = 0;
        failures = 0;
    }

    @Override
    public int compareTo(Conversation other) {
        if (other == null) {
            return -1;
        }
        return lastModified == 0 ? -1 : lastModified < other.lastModified ? 1 : lastModified == other.lastModified ? 0 : -1;
    }

    public boolean containsUnreadMessages() {
        return unreadMessageCount > 0;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && hashCode() == o.hashCode();
    }

    public long getBurnDelay() {
        return burnDelay;
    }

    public int getFailures() {
        return failures;
    }

    public String getId() {
        return toString(getIDAsByteArray());
    }

    public byte[] getIDAsByteArray() {
        return id;
    }

    public long getLastModified() {
        return lastModified;
    }

    public Contact getPartner() {
        return partner;
    }

    public String getPreviewEventID() {
        return toString(getPreviewEventIDAsByteArray());
    }

    public byte[] getPreviewEventIDAsByteArray() {
        return previewEventID;
    }

    public int getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public boolean hasBurnNotice() {
        return burnNotice;
    }

    @Override
    public int hashCode() {
        if (id == null) {
            if (partner == null) {
                return 0;
            }
            return partner.hashCode();
        }
        return Arrays.hashCode(id);
    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public void offsetFailures(int offset) {
        setFailures(getFailures() + offset);
    }

    public void offsetUnreadMessageCount(int offset) {
        unreadMessageCount = Math.max(0, unreadMessageCount + offset);
        setLastModified(System.currentTimeMillis());
    }

    public void removeID() {
        burn(id);
        id = null;
    }

    public void removePartner() {
        if (partner != null) {
            partner.clear();
            partner = null;
        }
    }

    public void removePreviewEventID() {
        burn(previewEventID);
        previewEventID = null;
    }

    public void setBurnDelay(long burnDelay) {
        this.burnDelay = burnDelay;
    }

    public void setBurnNotice(boolean burnNotice) {
        this.burnNotice = burnNotice;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public void setId(CharSequence id) {
        setId(IOUtils.toByteArray(id));
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public void setLocationEnabled(boolean locationEnabled) {
        this.locationEnabled = locationEnabled;
    }

    public void setPartner(Contact partner) {
        this.partner = partner;
    }

    public void setPreviewEventID(byte[] previewEventID) {
        this.previewEventID = previewEventID;
    }

    public void setPreviewEventID(CharSequence previewEventID) {
        setPreviewEventID(IOUtils.toByteArray(previewEventID));
    }

    public void setSendReadReceipts(boolean sendReadReceipts) {
        this.sendReadReceipts = sendReadReceipts;
    }

    public void setUnreadMessageCount(int unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    }

    public boolean shouldSendReadReceipts() {
        return sendReadReceipts;
    }

    public String getUnsentText() {
        return mUnsentText;
    }

    public void setUnsentText(String unsentText) {
        mUnsentText = unsentText;
        if (TextUtils.isEmpty(mUnsentText)) {
            mUnsentText = null;
        }
    }
}
