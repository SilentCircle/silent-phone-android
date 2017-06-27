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
package com.silentcircle.messaging.model;

import java.util.Arrays;


public class Attachment
        extends Burnable {
    private byte[] key;
    private byte[] locator;
    private byte[] name;
    private byte[] type;
    private long size;

    public void clear() {
        burn(this.key);
        this.key = null;

        burn(this.locator);
        this.locator = null;

        burn(this.name);
        this.name = null;

        burn(this.type);
        this.type = null;

        this.size = 0L;
    }


    public boolean equals(Object object) {
        return ((object instanceof Attachment)) && (hashCode() == object.hashCode());
    }

    public byte[] getKey() {
        return this.key;
    }

    public byte[] getLocator() {
        return this.locator;
    }

    public byte[] getName() {
        return this.name;
    }

    public long getSize() {
        return this.size;
    }

    public byte[] getType() {
        return this.type;
    }

    public int hashCode() {
        return this.locator == null ? 0 : Arrays.hashCode(this.locator);
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public void setLocator(byte[] locator) {
        this.locator = locator;
    }

    public void setName(byte[] name) {
        this.name = name;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setType(byte[] type) {
        this.type = type;
    }
}
