/*
 * Copyright (c) 2004 Apple Computer, Inc. All rights reserved.
 *
 * %Begin-Header%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, and the entire permission notice in its entirety,
 *    including the disclaimer of warranties.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote
 *    products derived from this software without specific prior
 *    written permission.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, ALL OF
 * WHICH ARE HEREBY DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF NOT ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 * %End-Header%
 */

#include "UUID.h"
#include <cryptcommon/ZrtpRandom.h>

#include <stdint.h>
#include <string.h>
#include <stdio.h>

#include <sys/time.h>

// RFC4122 defines the time in 100ns steps

UUID_DEFINE(UUID_NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

static uint64_t lastNanos = 0ULL;

static void
read_node(uint8_t *node)
{
    ZrtpRandom::getRandomData(node, 6);
    node[0] |= 0x01;
}

static uint64_t
read_time(void)
{
    struct timeval tv;
    uint64_t clock_reg;

    gettimeofday(&tv, 0);

    clock_reg = tv.tv_usec * 10;
    clock_reg += ((uint64_t) tv.tv_sec)*10000000;
//    clock_reg += (((uint64_t) 0x01B21DD2) << 32) + 0x13814000;
    clock_reg += 0x01B21DD213814000ULL;

    if (clock_reg > lastNanos)
        lastNanos = clock_reg;
    else
        clock_reg = ++lastNanos;

    return clock_reg;
}

void
uuid_clear(uuid_t uu)
{
    memset(uu, 0, sizeof(uuid_t));
}

int
uuid_compare(const uuid_t uu1, const uuid_t uu2)
{
    return memcmp(uu1, uu2, sizeof(uuid_t));
}

void
uuid_copy(uuid_t dst, const uuid_t src)
{
    memcpy(dst, src, sizeof(uuid_t));
}

void
uuid_generate_random(uuid_t out)
{
    ZrtpRandom::getRandomData(out, sizeof(uuid_t));

    out[6] = (out[6] & 0x0F) | 0x40;
    out[8] = (out[8] & 0x3F) | 0x80;
}

void
uuid_generate_time(uuid_t out)
{
    uint64_t time;

    read_node(&out[10]);
    ZrtpRandom::getRandomData(&out[8], 2);

    time = read_time();
    out[0] = (uint8_t)(time >> 24);
    out[1] = (uint8_t)(time >> 16);
    out[2] = (uint8_t)(time >> 8);
    out[3] = (uint8_t)time;
    out[4] = (uint8_t)(time >> 40);
    out[5] = (uint8_t)(time >> 32);
    out[6] = (uint8_t)(time >> 56);
    out[7] = (uint8_t)(time >> 48);
 
    out[6] = (out[6] & 0x0F) | 0x10;
    out[8] = (out[8] & 0x3F) | 0x80;
}

void
uuid_generate(uuid_t out)
{
    uuid_generate_random(out);
}

int
uuid_is_null(const uuid_t uu)
{
    return !memcmp(uu, UUID_NULL, sizeof(uuid_t));
}

int
uuid_parse(const uuid_string_t in, uuid_t uu)
{
    int n = 0;

    sscanf(in,
        "%2hhx%2hhx%2hhx%2hhx-"
        "%2hhx%2hhx-"
        "%2hhx%2hhx-"
        "%2hhx%2hhx-"
        "%2hhx%2hhx%2hhx%2hhx%2hhx%2hhx%n",
        &uu[0], &uu[1], &uu[2], &uu[3],
        &uu[4], &uu[5],
        &uu[6], &uu[7],
        &uu[8], &uu[9],
        &uu[10], &uu[11], &uu[12], &uu[13], &uu[14], &uu[15], &n);

    return (n != 36 || in[n] != '\0' ? -1 : 0);
}

void
uuid_unparse_lower(const uuid_t uu, uuid_string_t out)
{
    snprintf(out,
        sizeof(uuid_string_t),
        "%02x%02x%02x%02x-"
        "%02x%02x-"
        "%02x%02x-"
        "%02x%02x-"
        "%02x%02x%02x%02x%02x%02x",
        uu[0], uu[1], uu[2], uu[3],
        uu[4], uu[5],
        uu[6], uu[7],
        uu[8], uu[9],
        uu[10], uu[11], uu[12], uu[13], uu[14], uu[15]);
}

void
uuid_unparse_upper(const uuid_t uu, uuid_string_t out)
{
    snprintf(out,
        sizeof(uuid_string_t),
        "%02X%02X%02X%02X-"
        "%02X%02X-"
        "%02X%02X-"
        "%02X%02X-"
        "%02X%02X%02X%02X%02X%02X",
        uu[0], uu[1], uu[2], uu[3],
        uu[4], uu[5],
        uu[6], uu[7],
        uu[8], uu[9],
        uu[10], uu[11], uu[12], uu[13], uu[14], uu[15]);
}

void
uuid_unparse(const uuid_t uu, uuid_string_t out)
{
    uuid_unparse_upper(uu, out);
}

time_t uuid_time(const uuid_t uu, struct timeval *ret_tv)
{
    struct timeval  tv;
    uuid            uuid;
    uint32_t        high;
    uint64_t        clock_reg;

    uuid_unpack(uu, &uuid);

    high = uuid.time_mid | ((uuid.time_hi_and_version & 0xFFF) << 16);
    clock_reg = uuid.time_low | ((uint64_t) high << 32);

    clock_reg -= (((uint64_t) 0x01B21DD2) << 32) + 0x13814000;
    tv.tv_sec = clock_reg / 10000000;
    tv.tv_usec = (clock_reg % 10000000) / 10;

    if (ret_tv)
        *ret_tv = tv;

    return tv.tv_sec;
}

int uuid_type(const uuid_t uu)
{
    uuid uuid;

    uuid_unpack(uu, &uuid);
    return ((uuid.time_hi_and_version >> 12) & 0xF);
}

int uuid_variant(const uuid_t uu)
{
    uuid  uuid;
    int   var;

    uuid_unpack(uu, &uuid);
    var = uuid.clock_seq;

    if ((var & 0x8000) == 0)
        return UUID_VARIANT_NCS;
    if ((var & 0x4000) == 0)
        return UUID_VARIANT_DCE;
    if ((var & 0x2000) == 0)
        return UUID_VARIANT_MICROSOFT;
    return UUID_VARIANT_OTHER;
}

void uuid_unpack(const uuid_t in, struct uuid *uu)
{
    const uint8_t   *ptr = in;
    uint32_t        tmp;

    tmp = *ptr++;
    tmp = (tmp << 8) | *ptr++;
    tmp = (tmp << 8) | *ptr++;
    tmp = (tmp << 8) | *ptr++;
    uu->time_low = tmp;

    tmp = *ptr++;
    tmp = (tmp << 8) | *ptr++;
    uu->time_mid = tmp;

    tmp = *ptr++;
    tmp = (tmp << 8) | *ptr++;
    uu->time_hi_and_version = tmp;

    tmp = *ptr++;
    tmp = (tmp << 8) | *ptr++;
    uu->clock_seq = tmp;

    memcpy(uu->node, ptr, 6);
}
