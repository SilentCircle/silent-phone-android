#ifndef EC255PUBLICKEY_H
#define EC255PUBLICKEY_H

/**
 * @file Ec255PublicKey.h
 * @brief EC public key for curve 25519
 * @ingroup Axolotl++
 * @{
 * 
 * Storage and contants of Curve25519 public key data. Each public key object
 * has a byte array to store the data, thus now new/delete operation necessary
 * when copying or assigning a key object. Thus the object can use compiler
 * generated code for copy/assign (memberwise copy).
 * 
 * The Constructor just copies the data into the internal array, the Destructor
 * clears the memory.
 */

#include <stdint.h>
#include <string.h>

#include "DhPublicKey.h"
#include "EcCurveTypes.h"

namespace axolotl {
class Ec255PublicKey: public DhPublicKey
{
public:
    const static size_t KEY_LENGTH = 32;
    /**
     * @brief Construct and initialize with public key data
     * 
     * The buffer must contain at least @c getKeysize - 1 bytes.
     */
    Ec255PublicKey(const uint8_t* data);

    /**
     * @brief Destructor clears internal data (set to 0)
     */
    ~Ec255PublicKey();

    Ec255PublicKey(const Ec255PublicKey& other);

    DhPublicKey& operator=( const axolotl::DhPublicKey& other );

    /**
     * @brief Comprare with another generic public key, could be of different type.
     */
    bool operator== (const DhPublicKey& other) const;

    void serialize(uint8_t* outBuffer) const;
    const std::string serialize() const;

    int32_t getType() const {return EcCurveTypes::Curve25519;}
    int32_t getEncodedSize() const {return KEY_LENGTH + 1;}
    int32_t getSize() const {return KEY_LENGTH;}

    void getPublicKey(uint8_t* outBuffer) const {memcpy(outBuffer, keyData_, KEY_LENGTH);}
    const std::string  getPublicKey() const { return std::string((const char*)keyData_, KEY_LENGTH); }

    const uint8_t* getPublicKeyPointer() const {return keyData_;}

private:
    uint8_t keyData_[KEY_LENGTH];
};
}

/**
 * @}
 */

#endif // EC255PUBLICKEY_H
