#ifndef AXOPREKEYCONNECTOR_H
#define AXOPREKEYCONNECTOR_H

/**
 * @file AxoPreKeyConnector.h
 * @brief Functions to initialize the Axolotl protocol from pre-keys
 * @ingroup Axolotl++
 * @{
 * 
 * These functions set up the AxoConversation  from loaded pre-keys. The
 * functions assume the optimized Pre-key mechanism (see xxxx paper)
 */

#include <string>
#include <stdint.h>

#include "state/AxoConversation.h"
#include "crypto/DhKeyPair.h"

using namespace std;

namespace axolotl {

class AxoPreKeyConnector
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
    static int32_t setupConversationAlice(const string& localUser, const string& user, const string& deviceId,
                                          int32_t bobPreKeyId, pair<const DhPublicKey*, const DhPublicKey*> bobKeys);

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
    static int32_t setupConversationBob( axolotl::AxoConversation* conv, int32_t bobPreKeyId, const axolotl::DhPublicKey* aliceId, const axolotl::DhPublicKey* alicePreKey );

private:
    AxoPreKeyConnector() {};
    ~AxoPreKeyConnector() {};
};
} // namespace

/**
 * @}
 */

#endif // AXOPREKEYCONNECTOR_H
