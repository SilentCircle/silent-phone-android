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

public class Credential extends Burnable {
    protected byte[] domain;
    protected byte[] resource;
    protected byte[] username;
    protected byte[] password;

    public void clear() {
        removeDomain();
        removeUsername();
        removePassword();
        removeResource();
    }

    public boolean equals(Object o) {
        return (o != null) && (hashCode() == o.hashCode());
    }

    public String getDomain() {
        return IOUtils.toString(getDomainAsByteArray());
    }

    public byte[] getDomainAsByteArray() {
        if (this.domain == null) {
            if (this.username == null) {
                return null;
            }
            int index = IOUtils.indexOf(this.username, (byte) 64);
            if (index < 0) {
                return null;
            }
            this.domain = new byte[this.username.length - index - 1];
            System.arraycopy(this.username, index + 1, this.domain, 0, this.domain.length);
        }
        return this.domain;
    }

    public String getPassword() {
        return IOUtils.toString(getPasswordAsByteArray());
    }

    public byte[] getPasswordAsByteArray() {
        return this.password;
    }

    public String getResource() {
        return IOUtils.toString(getResourceAsByteArray());
    }

    public byte[] getResourceAsByteArray() {
        return this.resource;
    }

    @Deprecated
    public String getShortUsername() {
        return IOUtils.toString(getShortUsernameAsByteArray());
    }


    @Deprecated
    public byte[] getShortUsernameAsByteArray() {
        if (this.username == null) {
            return null;
        }
        int index = IOUtils.indexOf(this.username, (byte) 64);
        if (index < 0) {
            return this.username;
        }
        byte[] shortUsername = new byte[index];
        System.arraycopy(this.username, 0, shortUsername, 0, index);
        return shortUsername;
    }

    public String getUsername() {
        return IOUtils.toString(getUsernameAsByteArray());
    }

    public byte[] getUsernameAsByteArray() {
        return this.username;
    }

    public int hashCode() {
        byte[] id = getUsernameAsByteArray();
        return id == null ? 0 : Arrays.hashCode(id);
    }

    public void removeDomain() {
        burn(this.domain);
        this.domain = null;
    }

    public void removePassword() {
        burn(this.password);
        this.password = null;
    }

    public void removeResource() {
        burn(this.resource);
        this.resource = null;
    }

    public void removeUsername() {
        burn(this.username);
        this.username = null;
    }

    public void setDomain(byte[] domain) {
        this.domain = domain;
    }

    public void setDomain(CharSequence domain) {
        setDomain(IOUtils.toByteArray(domain));
    }

    public void setPassword(byte[] password) {
        this.password = password;
    }

    public void setPassword(CharSequence password) {
        setPassword(IOUtils.toByteArray(password));
    }

    public void setResource(byte[] resource) {
        this.resource = resource;
    }

    public void setResource(CharSequence resource) {
        setResource(IOUtils.toByteArray(resource));
    }

    public void setUsername(byte[] username) {
        this.username = username;
    }

    public void setUsername(CharSequence username) {
        setUsername(IOUtils.toByteArray(username));
    }
}
