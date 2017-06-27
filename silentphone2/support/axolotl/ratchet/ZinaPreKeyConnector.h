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
#ifndef AXOPREKEYCONNECTOR_H
#define AXOPREKEYCONNECTOR_H

/**
 * @file ZinaPreKeyConnector.h
 * @brief Functions to initialize the Axolotl protocol from pre-keys
 * @ingroup Zina
 * @{
 */


#include <string>
#include <stdint.h>

#include "state/ZinaConversation.h"
#include "crypto/DhKeyPair.h"

namespace zina {

/**
 * The static functions in this class set up the ZinaConversation from loaded pre-keys. The
 * functions assume the optimized Pre-key mechanism (see xxxx paper)
 */
class ZinaPreKeyConnector
{
public:
    /**
     * @brief Setup Axolotl conversion for Alice role.
     * 
     * According to the optimized pre-key mechanism the party who requested the pre-key bundle
     * takes the Alice Axolotl role.
     * 
     * Because of requested pre-key bundle Alice knows Bob's identity key and has one of his
     * pre-keys including the id of that pre-key. Alice uses this to perform the following steps:
     * 
     * - generate an own pre-key (A0) and uses this together with Bob's identity and pre-key (B, B0)
     *   to compute the master secret.
     * - Alice also sends the first message, thus she creates the first ratchet keys. Because of
     *   the optimization she can use Bob's B0 to create the first ratchet.
     * - encrypts the message
     * - Alice creates a message to Bob which includes her identity key, her generated pre-key,
     *   the new ratchet key and the message data itself.
     * 
     * This function performs the master secret computation and generation of Alice's pre-key (A0).
     *
     * @param localUser Own name
     * @param user Name of the receiver of the message
     * @param deviceId Device id of the receiver's device
     * @param bobPreKeyId Identifier of Bob's pre-key
     * @param bobKeys A pair that contains Bob's keys, the first entry Bob's identity key, the second
     *                Bob's pre-key.
     * @return @c OK or an error code
     */
    static int32_t setupConversationAlice(const std::string& localUser, const std::string& user, const std::string& deviceId,
                                          int32_t bobPreKeyId, std::pair<PublicKeyUnique, PublicKeyUnique>& bobKeys,
                                          SQLiteStoreConv &store);

    /**
     * @brief Setup Axolotl conversation for Bob role.
     * 
     * This party received a first message from a yet unknown party. This message contains that
     * party's identity key, its pre-key and the identifier of my pre-pey that was used by that
     * other party. Because Alice sends this message it also has a new ratechet key. Thus the 
     * initialization takes the Axolotl 'Bob' role. Bob uses the information in that message to
     * perform the following steps:
     * 
     * - compute the master shared secret using Alice's identity key, Alices's pre-key, Bob's
     *   pre-key and Bob's identity key.
     * - sets the new ratchet key.
     * - decrypts the message.
     * 
     * This function performs the master secret computation.
     */
    static int32_t setupConversationBob(ZinaConversation* conv, int32_t bobPreKeyId, PublicKeyUnique aliceId,
                                        PublicKeyUnique alicePreKey, SQLiteStoreConv &store);

private:
    ZinaPreKeyConnector() {};
    ~ZinaPreKeyConnector() {};
};
} // namespace

/**
 * @}
 */

#endif // AXOPREKEYCONNECTOR_H
