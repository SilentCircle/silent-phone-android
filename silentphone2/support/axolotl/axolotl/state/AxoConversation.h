#ifndef AXOCONVERSATION_H
#define AXOCONVERSATION_H

/**
 * @file AxoConversation.h
 * @brief The Axolotl state of a conversation with a partner
 * 
 * This class manages the state of a Axolotl conversation with a partner. It manages,
 * stores and retrieves keys and other state information. Refer to 
 * https://github.com/trevp/axolotl/wiki to get a full description of all the state
 * information.
 * 
 * In this implementation we don't manage the header keys because we don't use header
 * encryption. This is a variant of the Axolotl that the above document describes.
 * 
 * The variable names map to the short names that the document uses.
 * 
 * @ingroup Axolotl++
 * @{
 */
#include <string>
#include <stdint.h>
#include <string.h>    // for memset
#include <list>
#include <utility>

#include "../crypto/DhPublicKey.h"
#include "../crypto/DhKeyPair.h"
#include "../state/AxoContact.h"
#include "../crypto/Ec255PublicKey.h"

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

static const std::string emptyString;

using namespace std;

namespace axolotl {
class AxoConversation
{
public:
    AxoConversation(const string& localUser, const string& user, const string& deviceId) : partner_(user, emptyString), 
                    deviceId_(deviceId), localUser_(localUser), DHRs(NULL), DHRr(NULL), DHIs(NULL), DHIr(NULL), A0(NULL), Ns(0), 
                    Nr(0), PNs(0), preKeyId(0), ratchetFlag(false), zrtpVerifyState(0), availablePreKeys(0)
                    { }


   ~AxoConversation() { reset(); }

    /**
     * @brief Load local conversation from database.
     * 
     * @param localUser name of local user/account
     * @return the loaded AxoConversation or NULL if none was stored.
     */
    static AxoConversation* loadLocalConversation(const string& localUser) { return loadConversation(localUser, localUser, string());}

    /**
     * @brief Load a conversation from database.
     * 
     * @param localUser name of own user/account
     * @param user Name of the remote user
     * @param deviceId The remote user's device id if it is available
     * @return the loaded AxoConversation or NULL if none was stored.
     */
    static AxoConversation* loadConversation(const string& localUser, const string& user, const string& deviceId);

    /**
     * @brief Store this conversation in persitent store
     */
    void storeConversation();

    void storeStagedMks();

    list<string>* loadStagedMks();

    void deleteStagedMk(string& mkiv);

    const AxoContact& getPartner()  { return partner_; }

    const string& getLocalUser()    { return localUser_; }

    const string& getDeviceId()     { return deviceId_; }

    void setDeviceName(const string& name)  { deviceName_ = name; }
    const string& getDeviceName()           { return deviceName_; }

    void setErrorCode(int32_t code)         { errorCode_ = code; } 
    int32_t getErrorCode()                  { return errorCode_; }

    void setRK(const std::string& key)      { RK = key; }
    const std::string& getRK() const        { return RK; }

    void setDHRr(const DhPublicKey* key)    { DHRr = key; }
    const DhPublicKey* getDHRr() const      { return DHRr; }

    void setDHRs(const DhKeyPair* keyPair)  { DHRs = keyPair; }
    const DhKeyPair* getDHRs()              { return DHRs; }

    void setDHIr(const DhPublicKey* key)    { DHIr = key; }
    const DhPublicKey* getDHIr() const      { return DHIr; }

    void setDHIs(const DhKeyPair* keyPair)  { DHIs = keyPair; }
    const DhKeyPair* getDHIs()              { return DHIs; }

    void setA0(const DhKeyPair* keyPair)    { A0 = keyPair; }
    const DhKeyPair* getA0()                { return A0; }

    void setCKs(const std::string& key)     { CKs = key; }
    const std::string& getCKs() const       { return CKs; }

    void setCKr(const std::string& key)     { CKr = key; }
    const std::string& getCKr() const       { return CKr; }

    void setNs(int32_t number)              { Ns = number; }
    int32_t getNs() const                   { return Ns; }

    void setNr(int32_t number)              { Nr = number; }
    int32_t getNr() const                   { return Nr; }

    void setPNs(int32_t number)             { PNs = number; }
    int32_t getPNs() const                  { return PNs; }

    void setPreKeyId(uint32_t id)           { preKeyId = id; }
    uint32_t getPreKeyId() const            { return preKeyId; }

    void setRatchetFlag(bool flag)          { ratchetFlag = flag; }
    bool getRatchetFlag() const             { return ratchetFlag; }

    void setZrtpVerifyState(int32_t state)  { zrtpVerifyState = state; }
    int32_t getZrtpVerifyState() const      { return zrtpVerifyState; }

    void setPreKeysAvail(int32_t num)       { availablePreKeys = num; }
    int32_t getPreKeysAvail() const         { return availablePreKeys; }

    list<string>* stagedMk;

    void reset();

#ifdef UNITTESTS
    const std::string* dump() const         { return serialize(); }
#endif

private:
    void deserialize(const std::string& data);
    const std::string* serialize() const;

    // The following data goes to persistant store
    AxoContact partner_;
    string  deviceId_;
    string  deviceName_;
    string  localUser_;

    // The std::string variables below are not strings, used as data containers. May be helpful if
    // some data/key lengths change
    std::string  RK;            //!< the current root key

    const DhKeyPair*   DHRs;    //!< Diffie-Helman Ratchet key pair sender
    const DhPublicKey* DHRr;    //!< Diffie-Helman Ratchet key receiver

    const DhKeyPair*   DHIs;    //!< DH Identity key pair sender
    const DhPublicKey* DHIr;    //!< DH Identity key receiver

    const DhKeyPair*   A0;      //!< used when using pre-ky bundles, if not null send this info (pre-key message type)

    string       CKs;           //!< 32-byte chain key sender (used for forward-secrecy updating)
    string       CKr;           //!< 32-byte chain key receiver (used for forward-secrecy updating)

    int32_t      Ns;            //!< Message number sender (reset to 0 with each new ratchet)
    int32_t      Nr;            //!< Message number receiver (reset to 0 with each new ratchet)

    int32_t      PNs;           //!< Previous message numbers (# of msgs sent under prev ratchet)

    int32_t      preKeyId;      //!< Remote party's pre-key id
    bool      ratchetFlag;      //!< True if the party will send a new ratchet key in next message
    int32_t   zrtpVerifyState;
    int32_t   availablePreKeys; //!< Only used in local conversation to track number of available pre-keys
    // ***** end of persitent data

    /*
    skipped_HK_MK : A list of stored message keys and associated header keys
                for "skipped" messages, i.e. messages that have not been
                received despite the reception of more recent messages.
                Entries may be stored with a timestamp, and deleted after
                a certain age.
    Impemented via database and temporary list, see stagedMk above.
    */ 
    int32_t errorCode_;
};
}
/**
 * @}
 */

#endif // AXOCONVERSATION_H
