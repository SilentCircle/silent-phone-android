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
#ifndef B64HELPER_H
#define B64HELPER_H

/**
 * @file b64helper.h
 * @brief Base64 encoding/decoding helpers
 * @ingroup Zina
 * @{
 */

/**
 * @brief Encode binary data to Base64 string.
 * 
 * These function encodes without line break but adds the appropriate number of "="
 * characters to the b64 result string.
 * 
 * @param binData Pointer to binary data byte array
 * @param binlength length of binary data array
 * @param b64Data output buffer for the B64 data. The size of this buffer should
 *        be ~1.4 * binLength (rule of thumb).
 * @param resultSize length of output buffer
 * @return number of B64 characters in the @c b64Data buffer.
 */
size_t b64Encode(const uint8_t *binData, size_t binLength, char *b64Data, size_t resultSize);

/**
 * @brief Decode a Base64 string to binary data
 * 
 * These function decodes a Base64 string into binary data. The function
 * accepts all valid B64 formatted strings.
 * 
 * @param b64Data B64 data
 * @param b64length length of b64 string
 * @param binData Pointer to binary data byte array
 * @return number of binary bytes in the @c binData buffer
 */
size_t b64Decode(const char *b64Data, size_t b64length, uint8_t *binData, size_t binLength);

/**
 * @brief Convert a hex char string to binary array.
 * 
 * This function assumes src to be a zero terminated sanitized string with
 * an even number of [0-9a-f] characters, and target to be sufficiently large
 * 
 * @param src input string
 * @param target output array
 * @return -1 if an illegals character detected in the string, @c 0 otherwise
 */
size_t hex2bin(const char* src, uint8_t* target);


/**
 * @brief Convert a binary array to printable hex characters
 */
void bin2hex(const uint8_t* inBuf, size_t inLen, char* outBuf, size_t* outLen);

#endif  /*B64HELPER_H */
