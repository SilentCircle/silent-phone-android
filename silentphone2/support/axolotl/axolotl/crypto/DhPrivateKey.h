#ifndef DHPRIVATEKEY_H
#define DHPRIVATEKEY_H

/**
 * @file DhPrivateKey.h
 * @brief Interface for DH private key implementations
 * @ingroup Axolotl++
 * @{
 * Each implementation must have a type and a serialize function.
 */

#include <stdint.h>
#include <string>

namespace axolotl {
class DhPrivateKey
{
public:
    virtual ~DhPrivateKey() {}
    /**
     * @brief Get private key data
     * 
     * @param outBuffer Buffer with a minimum length of @c getKeySize length.
     */
    virtual void serialize(uint8_t* outBuffer) const = 0;
    /**
     * @brief Get serialized (encoded) private key data in a std::string container.
     * 
     * @return a std::string that contains the serialized key data
     */
    virtual const std::string serialize() const = 0;

    virtual int32_t getType() const = 0;

    virtual DhPrivateKey& operator=(const DhPrivateKey& other) = 0;

    /**
     * @brief Get key size, private key data is not encoded.
     */
    virtual int32_t getEncodedSize() const = 0;

    /**
     * @brief get raw private key data.
     */
    virtual void getPrivateKey(uint8_t* outBuffer) const = 0;

    /**
     * @brief Get a pointer to private key data, use with care.
     * 
     * The returned pointer is only valid while the key object is in scope and alive.
     * Otherwise use the other @c getPrivateKey functions that copies data into a supplied buffer.
     * 
     * @return pointer to internal key data.
     */
    virtual const uint8_t* privateData() const = 0;

};
} // namespace

/**
 * @}
 */

#endif // DHPRIVATEKEY_H
