#ifndef B64HELPER_H
#define B64HELPER_H

/**
 * @file b64helper.h
 * @brief Base64 encoding/decoding helpers
 * @ingroup Axolotl++
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
 * @param b64Data return buffer for the B64 data. The size of this buffer should
 *        be ~1.4 * binLength (rule of thumb).
 * @return number of B64 characters in the @c b64Data buffer.
 */
int b64Encode(const uint8_t *binData, int32_t binLength, char *b64Data, size_t resultSize);

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
int b64Decode(const char *b64Data, int32_t b64length, uint8_t *binData, size_t binLength);

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
int32_t hex2bin(const char* src, uint8_t* target);


/**
 * @brief Convert a binary array to printable hex characters
 */
void bin2hex(const uint8_t* inBuf, size_t inLen, char* outBuf, size_t* outLen);

#endif  /*B64HELPER_H */