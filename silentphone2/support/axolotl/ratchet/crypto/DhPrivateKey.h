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
#ifndef DHPRIVATEKEY_H
#define DHPRIVATEKEY_H

/**
 * @file DhPrivateKey.h
 * @brief Interface for DH private key implementations
 * @ingroup Zina
 * @{
 * Each implementation must have a type and a serialize function.
 */

#include <stdint.h>
#include <string>
#include <memory>

namespace zina {
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
    virtual size_t getEncodedSize() const = 0;

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
typedef std::unique_ptr<const DhPrivateKey> PrivateKeyUnique;

} // namespace

/**
 * @}
 */

#endif // DHPRIVATEKEY_H
