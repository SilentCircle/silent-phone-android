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

import com.silentcircle.messaging.util.IOUtils;

import java.util.Arrays;

public class Server
        extends Burnable {
    protected byte[] id;
    protected byte[] url;
    protected Credential credential;

    public Server() {
    }

    public Server(byte[] id) {
        this.id = id;
    }

    public Server(CharSequence id) {
        this(IOUtils.toByteArray(id));
    }

    public void clear() {
        removeId();
        removeUrl();
        removeCredential();
    }

    public boolean equals(Object o) {
        return (o != null) && (hashCode() == o.hashCode());
    }

    public Credential getCredential() {
        return this.credential;
    }

    public String getDomain() {
        return IOUtils.toString(getDomainAsByteArray());
    }

    public byte[] getDomainAsByteArray() {
        return this.credential == null ? null : this.credential.getDomainAsByteArray();
    }

    public String getId() {
        return IOUtils.toString(getIDAsByteArray());
    }

    public byte[] getIDAsByteArray() {
        return this.id;
    }

    public String getServiceName() {
        return IOUtils.toString(getServiceNameAsByteArray());
    }

    public byte[] getServiceNameAsByteArray() {
        return getDomainAsByteArray();
    }

    public String getURL() {
        return IOUtils.toString(getURLAsByteArray());
    }

    public byte[] getURLAsByteArray() {
        return this.url;
    }

    public int hashCode() {
        return this.id == null ? 0 : Arrays.hashCode(this.id);
    }

    public void removeCredential() {
        if (this.credential != null) {
            this.credential.clear();
            this.credential = null;
        }
    }

    public void removeId() {
        burn(this.id);
        this.id = null;
    }

    public void removeUrl() {
        burn(this.url);
        this.url = null;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public void setId(CharSequence id) {
        setId(IOUtils.toByteArray(id));
    }

    public void setURL(byte[] url) {
        this.url = url;
    }

    public void setURL(CharSequence url) {
        setURL(IOUtils.toByteArray(url));
    }
}
