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
#ifndef DHPUBLICKEY_H
#define DHPUBLICKEY_H

/**
 * @file DhPublicKey.h
 * @brief Interface for EC private key implementations
 * @ingroup Zina
 * @{
 * 
 * Each implementation must have a type and a serialize function. Public keys 
 * also return their encoded length, usually on byte longer then the actual
 * key data.
 */

#include <stdint.h>
#include <string>
#include <memory>

namespace zina {
class DhPublicKey
{
public:
    virtual ~DhPublicKey() {}

    virtual bool operator== (const DhPublicKey& other) const = 0;

    virtual DhPublicKey& operator=(const DhPublicKey& other) = 0;

    /**
     * @brief Get serialized (encoded) public key data
     * 
     * @param outBuffer Buffer with a minimum length of @c getKeySize length.
     */
    virtual void serialize(uint8_t* outBuffer) const = 0;

    /**
     * @brief Get serialized (encoded) public key data in a std::string container.
     * 
     * @return a std::string that contains the serialized key data
     */
    virtual const std::string serialize() const = 0;

    virtual int32_t getType() const = 0;

    /**
     * @brief Get encoded key size in bytes.
     */
    virtual size_t getEncodedSize() const = 0;

    /**
     * @brief Get key size in bytes.
     */
    virtual size_t getSize() const = 0;

    /**
     * @brief get raw public key data.
     */
    virtual void getPublicKey(uint8_t* outBuffer) const = 0;

    /**
     * @brief get raw public key data in a std:string container.
     */
    virtual const std::string  getPublicKey() const = 0;

    /**
     * @brief Get a pointer to public key data, use with care.
     * 
     * The returned pointer is only valid while the key object is in scope and alive.
     * Otherwise use the other @c getPublicKey functions that copies data into a supplied buffer.
     * 
     * @return pointer to internal key data.
     */
    virtual const uint8_t* getPublicKeyPointer() const = 0;

};
typedef std::unique_ptr<const DhPublicKey> PublicKeyUnique;
} // namespace

/**
 * @}
 */

#endif // DHPUBLICKEY_H
