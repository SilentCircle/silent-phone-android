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
#ifndef AXOCONVERSATION_H
#define AXOCONVERSATION_H

/**
 * @file ZinaConversation.h
 * @brief The ZINA state of a conversation with a partner
 * 
 * @ingroup Zina
 * @{
 */
#include <string>
#include <stdint.h>
#include <string.h>    // for memset
#include <list>
#include <utility>
#include <memory>
#include <vector>

#include "../crypto/DhPublicKey.h"
#include "../crypto/DhKeyPair.h"
#include "ZinaContact.h"
#include "../crypto/Ec255PublicKey.h"
#include "../../util/cJSON.h"
#include "../../Constants.h"
#include "../../storage/sqlite/SQLiteStoreConv.h"

static const std::string emptyString;

namespace zina {

/**
 * This class manages the state of a ZINA conversation with a partner. It manages,
 * stores and retrieves keys and other state information. Refer to
 * https://github.com/trevp/double_ratchet/wiki to get a full description of all the state
 * information.
 *
 * In this implementation we don't manage the header keys because we don't use header
 * encryption. This is a variant of the ratchet that the above document describes.
 *
 * The variable names map to the short names that the document uses.
 *
 */
class ZinaConversation
{
public:
    ZinaConversation(const std::string& localUser, const std::string& user, const std::string& deviceId) :
            partner_(user, emptyString),
            deviceId_(deviceId), localUser_(localUser), DHRs(nullptr), DHRr(nullptr), DHIs(nullptr), DHIr(nullptr), A0(nullptr), Ns(0),
            Nr(0), PNs(0), preKeyId(0), ratchetFlag(false), zrtpVerifyState(0), contextId(0),
            versionNumber(0), identityKeyChanged(false), errorCode_(SUCCESS), sqlErrorCode_(SUCCESS), valid_(false)
    { }


   ~ZinaConversation() { reset(); }

    /**
     * @brief Load local conversation from database.
     * 
     * @param localUser name of local user/account
     * @return the loaded AxoConversation or NULL if none was stored.
     */
    static std::unique_ptr<ZinaConversation> loadLocalConversation(const std::string& localUser, SQLiteStoreConv &store) {
        return loadConversation(localUser, localUser, std::string(), store);
    }

    /**
     * @brief Load a conversation from database.
     * 
     * @param localUser name of own user/account
     * @param user Name of the remote user
     * @param deviceId The remote user's device id if it is available
     * @return the loaded AxoConversation or NULL if none was stored.
     */
    static std::unique_ptr<ZinaConversation> loadConversation(const std::string& localUser, const std::string& user,
                                                              const std::string& deviceId, SQLiteStoreConv &store);

    // Currently not used, maybe we need to re-enable it, depending on new user UID (canonical name) design
#if 0
    /**
     * @brief Rename a conversation in the database.
     * 
     * @param localUserOld existing name of own user/account
     * @param localUserNew new name of own user/account
     * @param userOld existing name of the remote user
     * @param userNew new name of the remote user
     * @param deviceId The remote user's device id if it is available
     * @return @c SQLITE_OK if renaming of conversation was OK, an SQLite error code on failure.
     */
    static int32_t renameConversation(const string& localUserOld, const string& localUserNew, 
                                      const string& userOld, const string& userNew, const string& deviceId);
#endif
    /**
     * @brief Store this conversation in persistent store.
     *
     * @param store In which store to persist the data
     * @return A SQLite code
     */
    int32_t storeConversation(SQLiteStoreConv &store);

    int32_t storeStagedMks(SQLiteStoreConv &store);

    static void clearStagedMks(std::list<std::string> &keys, SQLiteStoreConv &store);

    int32_t loadStagedMks(std::list<std::string> &keys, SQLiteStoreConv &store);

    void deleteStagedMk(std::string& mkiv, SQLiteStoreConv &store);

    std::list<std::string>& getEmptyStagedMks() { return stagedMk; }

    const ZinaContact& getPartner() const   { return partner_; }

    const std::string& getLocalUser() const      { return localUser_; }

    const std::string& getDeviceId() const       { return deviceId_; }

    void setDeviceName(const std::string& name)  { deviceName_ = name; }
    const std::string& getDeviceName()           { return deviceName_; }

    void setErrorCode(int32_t code)         { errorCode_ = code; } 
    int32_t getErrorCode()                  { return errorCode_; }

    void setSqlErrorCode(int32_t code)      { sqlErrorCode_ = code; }
    int32_t getSqlErrorCode()               { return sqlErrorCode_; }

    void setRK(const std::string& key)      { RK = key; }
    const std::string& getRK() const        { return RK; }

    bool hasDHRr() const                    { return (bool)DHRr; }
    void setDHRr(PublicKeyUnique key)       { DHRr = move(key); }
    const DhPublicKey& getDHRr() const      { return *DHRr; }

    bool hasDHRs() const                    { return (bool)DHRs; }
    void setDHRs(KeyPairUnique keyPair)     { DHRs = move(keyPair); }
    const DhKeyPair& getDHRs() const        { return *DHRs; }

    bool hasDHIr() const                    { return (bool)DHIr; }
    void setDHIr(PublicKeyUnique key)       { DHIr = move(key); }
    const DhPublicKey& getDHIr() const      { return *DHIr; }

    bool hasDHIs() const                    { return (bool)DHIs; }
    void setDHIs(KeyPairUnique keyPair)     { DHIs = move(keyPair); }
    const DhKeyPair& getDHIs() const        { return *DHIs; }

    bool hasA0() const                      { return (bool)A0; }
    void setA0(KeyPairUnique keyPair)       { A0 = move(keyPair); }
    const DhKeyPair& getA0() const          { return *A0; }

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

    void setPreKeyId(int32_t id)            { preKeyId = id; }
    int32_t getPreKeyId() const             { return preKeyId; }

    void setRatchetFlag(bool flag)          { ratchetFlag = flag; }
    bool getRatchetFlag() const             { return ratchetFlag; }

    void setZrtpVerifyState(int32_t state)  { zrtpVerifyState = state; }
    int32_t getZrtpVerifyState() const      { return zrtpVerifyState; }

    void setIdentityKeyChanged(bool flag)   { identityKeyChanged = flag; }
    bool isIdentityKeyChanged() const       { return identityKeyChanged; }

    bool isValid() const                    { return valid_; }

    uint32_t getContextId() const { return contextId; }

    void setContextId(uint32_t ctxId) { contextId = ctxId; }

    int32_t getVersionNumber() const { return versionNumber; }

    void setVersionNumber(int32_t version) { versionNumber = version; }

    /**
     * @brief Check if a secondary ratchet exists for the pre-key id.
     *
     * @param prekeyId the pre-key id to check
     * @return the secondary device id or an empty string if no ratchet exists.
     */
    std::string lookupSecondaryDevId(int32_t prekeyId);

    void saveSecondaryAddress(const std::string& secondaryDevId, int32_t localPreKeyId);

    /**
     * @brief Get a secondary ratchet from vector.
     *
     * @param index The index of the secondary ratchet
     * @param store database
     * @return the secondary ratchet or an empty unique pointer if no ratchet/wrong index
     */
    std::unique_ptr<ZinaConversation> getSecondaryRatchet(int32_t index, SQLiteStoreConv &store);

    /**
     * @brief Delete all existing secondary ratchet data from database.
     *
     * @param store database
     */
    void deleteSecondaryRatchets(SQLiteStoreConv &store);

    void reset();

    /**
     * @brief Prepare data for capture.
     *
     * The functions prepares the ratchet status data to capture it. The function does not
     * copy security relevant information.
     *
     * @param existingRoot If this is @c NULL then create a new cJSON data structure,
     *                     otherwise append to the existing structure
     * @param beforeAction If @c true then this capture is before an encrypt or decrypt
     *                     action with the current ratchet.
     * @return a pointer to the created/used cJSON structure. The caller is responsible to
     *         free the pointer.
     */
    cJSON* prepareForCapture(cJSON* existingRoot, bool beforeAction);

#ifdef UNITTESTS
    const std::string* dump() const         { return serialize(); }
#endif

private:

    struct SecondaryInfo {
        int32_t     preKeyId;
        std::string deviceId;
        time_t      creationTime;

    };
    void deserialize(const std::string& data);
    const std::string* serialize() const;

    std::list<std::string> stagedMk;


    // The following data goes to persistent store
    ZinaContact partner_;
    std::string  deviceId_;
    std::string  deviceName_;
    std::string  localUser_;

    // The std::string variables below are not strings, used as data containers. May be helpful if
    // some data/key lengths change
    std::string  RK;            //!< the current root key

    KeyPairUnique   DHRs;       //!< Diffie-Helman Ratchet key pair sender
    PublicKeyUnique DHRr;       //!< Diffie-Helman Ratchet key receiver

    KeyPairUnique   DHIs;       //!< DH Identity key pair sender
    PublicKeyUnique DHIr;       //!< DH Identity key receiver

    KeyPairUnique   A0;         //!< used when using pre-ky bundles, if not null send this info (pre-key message type)

    std::string       CKs;           //!< 32-byte chain key sender (used for forward-secrecy updating)
    std::string       CKr;           //!< 32-byte chain key receiver (used for forward-secrecy updating)

    int32_t      Ns;            //!< Message number sender (reset to 0 with each new ratchet)
    int32_t      Nr;            //!< Message number receiver (reset to 0 with each new ratchet)

    int32_t      PNs;           //!< Previous message numbers (# of msgs sent under prev ratchet)

    int32_t      preKeyId;      //!< Remote party's pre-key id
    bool      ratchetFlag;      //!< True if the party will send a new ratchet key in next message
    int32_t   zrtpVerifyState;
    uint32_t  contextId;        //!< unique ID of context, changes with every re-keying
    int32_t   versionNumber;    //!< This is the version number the partner supports
    bool      identityKeyChanged;
    // ***** end of persistent data

    /*
    skipped_HK_MK : A list of stored message keys and associated header keys
                for "skipped" messages, i.e. messages that have not been
                received despite the reception of more recent messages.
                Entries may be stored with a timestamp, and deleted after
                a certain age.
    Implemented via database and temporary list, see stagedMk above.
    */
    std::vector<std::unique_ptr<SecondaryInfo> > secondaryRatchets;
    int32_t errorCode_;
    int32_t sqlErrorCode_;      //!< Valid if errorCode_ is DATABASE_ERROR
    bool valid_;
};
}
/**
 * @}
 */

#endif // AXOCONVERSATION_H
