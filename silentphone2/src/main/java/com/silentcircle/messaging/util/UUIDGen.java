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

package com.silentcircle.messaging.util;

/**
 * Modified to use a random generated node (MAC), see RFC4122, section 4.1.5
 *
 * Created by werner on 23.06.15.
 */
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

import android.util.Log;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * The goods are here: www.ietf.org/rfc/rfc4122.txt.
 */
public class UUIDGen
{
    private static final String TAG = "UUIDGen";

    // A grand day! millis at 00:00:00.000 15 Oct 1582 - see RFC 4122
    private static final long START_EPOCH = -12219292800000L;
    private static final UUIDGen instance = new UUIDGen();

    private long lastNanos;
    private final long mClockSeqAndNode;

    private UUIDGen()
    {
        SecureRandom random = new SecureRandom();
        random.setSeed(System.currentTimeMillis());
        long clockSequence = random.nextLong();
        byte[] randomNode = new byte[6];
        random.nextBytes(randomNode);
        randomNode[0] |= 1;                     // make it multi-cast, see RFC4122, 4.1.5
        long randomNodeLong = makeNode(randomNode);
        mClockSeqAndNode = getClockSeqAndNode(clockSequence, randomNodeLong);
    }

    /**
     * Creates a type 1 UUID (time-based UUID) that substitutes a random
     * number in place of the MAC.
     *
     * @return a UUID instance
     */
    public static UUID makeType1UUID() {
        return new UUID(instance.createTimeSafe(), instance.getSequenceNode());
    }

    /** creates a type 1 uuid from raw bytes. */
    public static UUID getUUID(ByteBuffer raw) {
        return new UUID(raw.getLong(raw.position()), raw.getLong(raw.position() + 8));
    }

    /** reads a uuid from an input stream. */
    public static UUID read(DataInput dis) throws IOException {
        return new UUID(dis.readLong(), dis.readLong());
    }

    /** writes a uuid to an output stream. */
    public static void write(UUID uuid, DataOutput dos) throws IOException {
        dos.writeLong(uuid.getMostSignificantBits());
        dos.writeLong(uuid.getLeastSignificantBits());
    }

    /** decomposes a uuid into raw bytes. */
    public static byte[] decompose(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        byte[] b = new byte[16];
        for (int i = 0; i < 8; i++)
        {
            b[i] = (byte)(most >>> ((7-i) * 8));
            b[8+i] = (byte)(least >>> ((7-i) * 8));
        }
        return b;
    }

    /**
     * Returns a 16 byte representation of a type 1 UUID (a time-based UUID),
     * based on the current system time.
     *
     * @return a type 1 UUID represented as a byte[]
     */
    public static byte[] getTimeUUIDBytes() {
        return createTimeUUIDBytes(instance.createTimeSafe());
    }

    /**
     * Converts a milliseconds-since-epoch timestamp into the 16 byte representation
     * of a type 1 UUID (a time-based UUID).
     *
     * <p><i><b>Warning:</b> This method is not guaranteed to return unique UUIDs; Multiple
     * invocations using identical timestamps will result in identical UUIDs.</i></p>
     *
     * @param timeMillis
     * @return a type 1 UUID represented as a byte[]
     */
    public static byte[] getTimeUUIDBytes(long timeMillis) {
        return createTimeUUIDBytes(instance.createTimeUnsafe(timeMillis));
    }

    private long getSequenceNode() {
        return mClockSeqAndNode;
    }

    private static byte[] createTimeUUIDBytes(long msb) {
        long lsb = instance.getSequenceNode();
        byte[] uuidBytes = new byte[16];

        for (int i = 0; i < 8; i++)
            uuidBytes[i] = (byte) (msb >>> 8 * (7 - i));

        for (int i = 8; i < 16; i++)
            uuidBytes[i] = (byte) (lsb >>> 8 * (7 - i));

        return uuidBytes;
    }

    /**
     * Returns a milliseconds-since-epoch value for a type-1 UUID.
     *
     * @param uuid a type-1 (time-based) UUID
     * @return the number of milliseconds since the unix epoch
     * @throws IllegalArgumentException if the UUID is not version 1
     */
    public static long getAdjustedTimestamp(UUID uuid) {
        if (uuid.version() != 1)
            throw new IllegalArgumentException("incompatible with uuid version: "+uuid.version());

        long timestamp = uuid.timestamp();
        return (timestamp / 10000) + START_EPOCH;
    }

    private long getClockSeqAndNode(long clockSequence, long randomNodeLong) {
        long lsb = 0;
        lsb |= (clockSequence & 0x3f00000000000000L) >>> 56;
        lsb |= 0x0000000000000080;
        lsb |= (clockSequence & 0x00ff000000000000L) >>> 48;
        lsb |= randomNodeLong;
        return lsb;
    }

    // needs to return two different values for the same when.
    // we can generate at most 10k UUIDs per ms.
    private synchronized long createTimeSafe() {
        long nanosSince = (System.currentTimeMillis() - START_EPOCH) * 10000;
        if (nanosSince > lastNanos)
            lastNanos = nanosSince;
        else
            nanosSince = ++lastNanos;
        return createTime(nanosSince);
    }

    private long createTimeUnsafe(long when) {
        long nanosSince = (when - START_EPOCH) * 10000;
        return createTime(nanosSince);
    }

    private long createTime(long time) {
        long msb = 0;
        msb |= (0x00000000ffffffffL & time) << 32;
        msb |= (0x0000ffff00000000L & time) >>> 16;
        msb |= (0xffff000000000000L & time) >>> 48;
        msb |=  0x0000000000001000L; // sets the version to 1.
        return msb;
    }

    // Lazily create node hashes, and cache them for later
    private long makeNode(byte[] randomNode) {

        long node = 0;
        for (int i = 0; i < randomNode.length; i++)
            node |= (0x00000000000000ff & (long)randomNode[i]) << (5-i)*8;
        assert (0xff00000000000000L & node) == 0;

        return node;
    }
}

// for the curious, here is how I generated START_EPOCH
//        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT-0"));
//        c.set(Calendar.YEAR, 1582);
//        c.set(Calendar.MONTH, Calendar.OCTOBER);
//        c.set(Calendar.DAY_OF_MONTH, 15);
//        c.set(Calendar.HOUR_OF_DAY, 0);
//        c.set(Calendar.MINUTE, 0);
//        c.set(Calendar.SECOND, 0);
//        c.set(Calendar.MILLISECOND, 0);
//        long START_EPOCH = c.getTimeInMillis();
