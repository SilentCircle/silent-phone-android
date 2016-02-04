#ifndef AXORATCHET_H
#define AXORATCHET_H

/**
 * @file AxoRatchet.h
 * @brief Axolotl ratchet functions
 * @ingroup Axolotl++
 * @{
 */

#include "../crypto/DhKeyPair.h"
#include "../crypto/DhPublicKey.h"
#include "../state/AxoConversation.h"

using namespace std;

namespace axolotl {
class AxoRatchet
{
public:
    AxoRatchet();
    ~AxoRatchet();

    /**
     * @brief Encrypt a message and message supplements, assemble a wire message.
     *
     * @param conv The Axolotl conversation
     * @param message The plaintext message bytes.
     * @param supplements Additional data for the message, will be encrypted with the message key
     * @param idHashes The sender's and receiver's id hashes to send with the message, can be @c NULL if
     *                 not required
     * @return An encrypted wire message, ready to send to the recipient+device tuple.
     */
    static const string* encrypt(AxoConversation& conv, const string& message, const string& supplements, 
                                 string* supplementsEncrypted, pair<string, string>* idHashes = NULL);

    /**
     * @brief Parse a wire message and decrypt the payload.
     * 
     * @param conv The Axolotl conversation
     * @param wire The wire message.
     * @param supplements Encrypted additional data for the message
     * @param supplementsPlain Additional data for the message if available and decryption was successful.
     * @param idHashes The sender's and receiver's id hashes contained in the message, can be @c NULL if
     *                 not available
     * @return Plaintext or @c NULL if decryption failed
     */
    static string* decrypt( axolotl::AxoConversation* conv, const string& wire, const string& supplements, 
                            string* supplementsPlain, pair<string, string>* idHashes = NULL);
};
}
/**
 * @}
 */

#endif // AXORATCHET_H
