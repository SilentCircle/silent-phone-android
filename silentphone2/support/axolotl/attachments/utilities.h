/*
Copyright 2016-2017 Silent Circle, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

#ifndef Included_SCutilities_h  /* [ */
#define Included_SCutilities_h

#include "scloudPubTypes.h"


/* Functions to load and store in network (big) endian format */


SCLError sLoadArray( void *val, size_t len,  uint8_t **ptr, uint8_t* limit );

uint64_t sLoad64( uint8_t **ptr );

uint32_t sLoad32( uint8_t **ptr );

uint16_t sLoad16( uint8_t **ptr );

uint8_t sLoad8( uint8_t **ptr );

void sStorePad( uint8_t pad, size_t len,  uint8_t **ptr );

void sStoreArray( void *val, size_t len,  uint8_t **ptr );

void sStore64( uint64_t val, uint8_t **ptr );

void sStore32( uint32_t val, uint8_t **ptr );

void sStore16( uint16_t val, uint8_t **ptr );

void sStore8( uint8_t val, uint8_t **ptr );

SCLError URL64_encode(uint8_t *in, size_t inlen,  uint8_t *out, size_t * outLen);

SCLError URL64_decode(const uint8_t *in,  size_t inlen, uint8_t *out, size_t *outlen);

size_t URL64_encodeLength(  size_t  inlen);

size_t URL64_decodeLength(  size_t  inlen);


#endif /* Included_scPubTypes_h */ /* ] */

