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
#ifndef AXORATCHET_H
#define AXORATCHET_H

/**
 * @file ZinaRatchet.h
 * @brief Zina messaging ratchet functions (formerly known as Axolotl ratchet)
 * @ingroup Zina
 * @{
 */

#include <memory>
#include "../crypto/DhKeyPair.h"
#include "../crypto/DhPublicKey.h"
#include "../state/ZinaConversation.h"

namespace zina {
class MessageEnvelope;

class ZinaRatchet
{
public:
    /**
     * @brief Encrypt a message and message supplements, assemble a wire message.
     *
     * @param conv The Axolotl conversation
     * @param message The plaintext message bytes.
     * @param envelope The ProtoBuffer data structure to create the wire data
     * @param supplements Additional data for the message, will be encrypted with the message key
     * @param Context storage
     * @return SUCCESS or a failure code
     */
    static int32_t encrypt(ZinaConversation& conv, const std::string& message, MessageEnvelope& envelope, const std::string &supplements,
                           SQLiteStoreConv &store);

    /**
     * @brief Parse a wire message and decrypt the payload.
     * 
     * @param primaryConv The Axolotl conversation
     * @param wire The wire message.
     * @param supplements Encrypted additional data for the message
     * @param supplementsPlain Additional data for the message if available and decryption was successful.
     * @param idHashes The sender's and receiver's id hashes contained in the message, can be @c NULL if
     *                 not available
     * @param store Context storage
     * @return Plaintext or @c NULL if decryption failed
     *
     * Passing unique_ptr by reference is for in/out unique_ptr parameters.
     * Guideline: Use a non-const unique_ptr& parameter only to modify the unique_ptr. (Herb Sutter)
     */
    static std::shared_ptr<const std::string> decrypt(ZinaConversation* primaryConv, MessageEnvelope& envelope, SQLiteStoreConv &store,
                                                      std::string* supplementsPlain, std::unique_ptr<ZinaConversation>& secondary);

private:
    ZinaRatchet() {};

    ~ZinaRatchet() {};

};
}
/**
 * @}
 */

#endif // AXORATCHET_H
