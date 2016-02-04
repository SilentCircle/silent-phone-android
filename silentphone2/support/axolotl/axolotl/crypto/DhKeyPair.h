#ifndef DHKEYPAIR_H
#define DHKEYPAIR_H

/**
 * @file DhKeyPair.h
 * @brief EC key pair
 * @ingroup Axolotl++
 * @{
 */

#include "DhPublicKey.h"
#include "DhPrivateKey.h"

namespace axolotl {
class DhKeyPair
{
public:
    DhKeyPair(const DhPublicKey& publicKey, const DhPrivateKey& privateKey);

    DhKeyPair(const DhKeyPair& otherPair);

    ~DhKeyPair();

    DhKeyPair& operator=(const DhKeyPair& otherPair);

    /**
     * @brief Get the public key - use with care.
     * 
     * @c DhPublicKey is an abstract base class and the caller should determine the actual
     * key class and cast and copy it as necessary.
     * 
     * @return Reference to the internal public key - will become invalid if this DhKeyPair is destroyed.
     */
    const DhPublicKey& getPublicKey() const { return *publicKey_; }

    /**
     * @brief Get private key - use with care.
     * 
     * @c DhPrivateKey is an abstract base class and the caller should determine the actual
     * key class and cast and copy it as necessary.
     * 
     * @return Reference to the internal private key - will become invalid if this DhKeyPair is destroyed.
     */
    const DhPrivateKey& getPrivateKey() const { return *privateKey_; }

private:
    const DhPrivateKey* privateKey_;
    const DhPublicKey*  publicKey_;
};
}  // namespace
/**
 * @}
 */

#endif // DHKEYPAIR_H
