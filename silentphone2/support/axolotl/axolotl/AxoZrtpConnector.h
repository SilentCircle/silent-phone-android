#ifndef ZRTPCONNECTOR_H
#define ZRTPCONNECTOR_H
/**
 * @file AxoZrtpConnector.h
 * @brief Functions to link the Axolotl protocol with a ZRTP client
 * @ingroup Axolotl++
 * @{
 */

#include <string>
#include <stdint.h>

#include "state/AxoConversation.h"
#include "crypto/DhKeyPair.h"

/**
 * @brief Get the public keys to use for the remote user.
 * 
 * If a public key pair already exists then this functions returns its public
 * part. If no key pair exist yet the function calls createAxoKeyPair to
 * create a new key pair and to store it.
 * 
 * The functions also returns the public part of the own identity key.
 * 
 * The functions stores the remote conversation in a staging area.
 * 
 * @param localUser name of local user/account
 * @param user Name of the remote user
 * @param deviceId The remote user's device id if it is available
 * @return the serialized data of the public keys.
 */
const std::string getAxoPublicKeyData( const std::string& localUser, const std::string& user, const std::string& deviceId );

/**
 * @brief Set public keys of a remote user.
 * 
 * The ZRTP client calls this function after it got the public key data of the
 * remote user during the ZRTP negotiation. 
 * 
 * The function gets the remote conversation from the staging area, determines
 * the Alice and Bob roles, stores the public key data according the the Axolotl
 * specification and stores the prepared conversation in the staging area.
 * 
 * @param localUser name of local user/account
 * @param pubKeyData The serialized data of the public keys
 * @param user the remote user's name
 * @param deviceId The remote user's device id if it is available
 */
void setAxoPublicKeyData(const std::string& localUser, const std::string& user, const std::string& deviceId, const std::string& pubKeyData);

/**
 * @brief Receive the exported key data.
 * 
 * The ZRTP client call this functions to hand over the created exported key
 * data. The ZRTP based Axolotl protocol uses this data as it's master secret
 * and dervies other keys from this master secret, refer to the Axolotl specification.
 * 
 * The function gets the remote conversation from the staging area, derives the 
 * various keys and finishes the setup of the remote conversation state.
 * In the last setp the function commits the new remote conversation to persitent store.
 * 
 * @param localUser name of local user/account
 * @param user Name of the remote user
 * @param deviceId The remote user's device id if it is available
 * @param exportedKey The raw data of the exported key from ZRTP
 * @param role the current client ZRTP role, Initiator or Responder
 */
void setAxoExportedKey( const std::string& localUser, const std::string& user, const std::string& deviceId, const std::string& exportedKey );


const string getOwnIdKey();

void checkRemoteIdKey( const string user, const string deviceId, const string pubKey, int32_t verifyState );

/*
 * To get some information from the SIP engine we need to something like this:

void *pEng = getAccountByID(0);

if (pEng) {
    user = (char*)sendEngMsg(pEng, "cfg.un");
    devId = (char*)sendEngMsg(pEng, "device_id");
}
*/
static const int32_t Alice = 1;
static const int32_t Bob   = 2;

using namespace axolotl;
class AxoZrtpConnector
{
public:
    /**
     * @brief Constructor
     * 
     * @param conv Pointer to a remote AxoConversation.
     * @param localConv Pointer to local Axolotl state
     */
    AxoZrtpConnector(AxoConversation* conv, AxoConversation* localConv): conv_(conv), localConv_(localConv), ratchetKey_(NULL), 
                     remoteRatchetKey_(NULL), remoteIdKey_(NULL) {}
    ~AxoZrtpConnector() { delete ratchetKey_; ratchetKey_ = NULL; delete remoteRatchetKey_; remoteRatchetKey_ = NULL; }


    /**
     * @brief Set pointer of staged Ratchet key pair.
     */
    void setRatchetKey(const DhKeyPair* ratchetKey) { ratchetKey_ = ratchetKey; }

    /**
     * @brief Get pointer of staged Ratchet key pair.
     */
    const DhKeyPair* getRatchetKey() const          { return ratchetKey_; }

    /**
     * @brief Set pointer of staged remote Ratchet key.
     */
    void setRemoteRatchetKey(const DhPublicKey* ratchetKey) { remoteRatchetKey_ = ratchetKey; }

    void setRemoteIdKey(const DhPublicKey* idKey)           { remoteIdKey_ = idKey; }

    /**
     * @brief Get pointer of staged remote Ratchet key.
     */
    const DhPublicKey* getRemoteRatchetKey() const  { return remoteRatchetKey_; }

    const DhPublicKey* getRemoteIdKey() const       { return remoteIdKey_; }

    void setRole(int32_t role)                { role_ = role; }
    int32_t getRole()                         { return role_; }

    AxoConversation* getRemoteConversation()  { return conv_; }
    AxoConversation* getLocalConversation()   { return localConv_; }

private:
    AxoZrtpConnector (const AxoZrtpConnector& other) {}
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wreturn-type"
    AxoZrtpConnector& operator= (const AxoZrtpConnector& other) {}
    bool operator== (const AxoZrtpConnector& other) const {}
#pragma clang diagnostic pop

    AxoConversation* conv_;
    AxoConversation* localConv_;
    const DhKeyPair* ratchetKey_;
    const DhPublicKey* remoteRatchetKey_;
    const DhPublicKey* remoteIdKey_;
    int32_t role_;
};
/**
 * @}
 */


#endif // ZRTPCONNECTOR_H
