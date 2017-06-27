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
#ifndef DHKEYPAIR_H
#define DHKEYPAIR_H

/**
 * @file DhKeyPair.h
 * @brief EC key pair
 * @ingroup Zina
 * @{
 */

#include "DhPublicKey.h"
#include "DhPrivateKey.h"

namespace zina {
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
    typedef std::unique_ptr<const DhKeyPair> KeyPairUnique;
}  // namespace
/**
 * @}
 */

#endif // DHKEYPAIR_H
