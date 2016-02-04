#ifndef EC255PRIVATEKEY_H
#define EC255PRIVATEKEY_H

/**
 * @file Ec255PrivateKey.h
 * @brief EC private key for curve 25519
 * @ingroup Axolotl++
 * @{
 * 
 * Storage and contants of Curve25519 private key data. Each private key object
 * has a byte array to store the data, thus now new/delete operation necessary
 * when copying or assigning a key object. Thus the object can use compiler
 * generated code for copy/assign (memberwise copy).
 * 
 * The Constructor just copies the data into the internal array, the Destructor
 * clears the memory.
 */

#include <stdint.h>
#include <string.h>

#include "DhPrivateKey.h"
#include "EcCurveTypes.h"

namespace axolotl {
class Ec255PrivateKey : public DhPrivateKey
{
public:
    const static size_t KEY_LENGTH  = 32;   //!< Defined by Curve25519, also length of agreement
    const static size_t SIGN_LENGTH = 64;   //!< Defined by ed25519 signature

    /**
     * @brief Construct and initialize with private key data
     * 
     * The buffer must contain at least @c getKeysize - 1 bytes.
     */
    Ec255PrivateKey(const uint8_t* data);

    /**
     * @brief Destructor clears internal data (set to 0)
     */
    ~Ec255PrivateKey();

    Ec255PrivateKey(const Ec255PrivateKey& other);

    DhPrivateKey& operator=(const DhPrivateKey& other);

    /**
     * @brief Comprare with another generic private key, could be of different type.
     */
    bool operator== (const Ec255PrivateKey& other) const;

    void serialize(uint8_t* outBuffer) const;

    const std::string serialize() const { return std::string((const char*)keyData_, KEY_LENGTH); }

    int32_t getType() const {return EcCurveTypes::Curve25519;}
    int32_t getEncodedSize() const {return KEY_LENGTH;}

    void getPrivateKey(uint8_t* outBuffer) const {memcpy(outBuffer, keyData_, KEY_LENGTH);}

    const uint8_t* privateData() const {return keyData_;}


private:
    uint8_t keyData_[KEY_LENGTH];
};
}

/**
 * @}
 */
#endif // EC255PRIVATEKEY_H
