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
#include "ZinaZrtpConnector.h"
#include "crypto/EcCurve.h"
#include "crypto/HKDF.h"
#include "../interfaceApp/AppInterface.h"
#include "../logging/ZinaLogging.h"
#include "../interfaceApp/AppInterfaceImpl.h"

#include <map>

using namespace std;

#ifdef UNITTESTS
// Used in testing and debugging to do in-depth checks
static char hexBuffer[2000] = {0};
static void hexdump(const char* title, const unsigned char *s, size_t l) {
    size_t n = 0;
    if (s == NULL) return;

    memset(hexBuffer, 0, 2000);
    int len = sprintf(hexBuffer, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            len += sprintf(hexBuffer+len, "\n%04x", static_cast<int>(n));
        len += sprintf(hexBuffer+len, " %02x",s[n]);
    }
    sprintf(hexBuffer+len, "\n");
}
static void hexdump(const char* title, const string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}
#endif

static mutex sessionLock;

static map<string, ZinaZrtpConnector*>* stagingList = new map<string, ZinaZrtpConnector*>;

using namespace zina;
void Log(const char* format, ...);

const string getAxoPublicKeyData(const string& localUser, const string& user, const string& deviceId, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");
    unique_lock<mutex> lck(sessionLock);

    auto conv = ZinaConversation::loadConversation(localUser, user, deviceId, store);
    if (conv->isValid()) {              // Already a conversation available, no setup necessary
        LOGGER(ERROR, __func__, " <-- Conversation already exists for user: ", user);
        return emptyString;
    }
    auto localConv = ZinaConversation::loadLocalConversation(localUser, store);
    if (!localConv->isValid()) {
        return emptyString;
    }
    const DhKeyPair& idKey = localConv->getDHIs();

    ZinaZrtpConnector* staging = new ZinaZrtpConnector(move(conv), move(localConv));

    pair<string, ZinaZrtpConnector*> stage(localUser, staging);
    stagingList->insert(stage);

    KeyPairUnique ratchetKey = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);

    std::string combinedKeys;

    // First: length and data of local identity key
    const string key = idKey.getPublicKey().serialize();
    char keyLength = static_cast<char>(key.size() & 0x7f);
    combinedKeys.assign(&keyLength, 1).append(key);

    // second: ratchet key
    const std::string rkey = ratchetKey->getPublicKey().serialize();
    keyLength = static_cast<char>(rkey.size() & 0x7f);
    combinedKeys.append(&keyLength, 1).append(rkey);

    staging->setRatchetKey(ratchetKey.release());
    lck.unlock();

    LOGGER(DEBUGGING, __func__, " <--");
    return combinedKeys;
}

void setAxoPublicKeyData(const string& localUser, const string& user, const string& deviceId, const string& pubKeyData)
{
    LOGGER(DEBUGGING, __func__, " -->");
    unique_lock<mutex> lck(sessionLock);

    std::map<string, ZinaZrtpConnector*>::iterator it;
    it = stagingList->find(localUser);
    ZinaZrtpConnector* staging = it->second;

    if (staging == NULL) {
        LOGGER(ERROR, __func__, " <-- Illegal state, staging not found.");
        return;
    }
    auto localConv = staging->getLocalConversation();
    const DhKeyPair& localIdKey = localConv->getDHIs();
    const char* data = pubKeyData.data();

    // Get remote id key
    size_t keyLength = static_cast<size_t>(*data++);
    string keyData(data, keyLength);

    data += keyLength;
    PublicKeyUnique remoteIdKey = EcCurve::decodePoint((const uint8_t*)keyData.data());

    int32_t cmp = memcmp(localIdKey.getPublicKey().getPublicKeyPointer(), remoteIdKey->getPublicKeyPointer(), localIdKey.getPublicKey().getSize());
    staging->setRole((cmp < 0) ? Alice : Bob);
    staging->setRemoteIdKey(remoteIdKey.release());

    // Now the remote ratchet key
    keyLength = static_cast<size_t>(*data++);
    keyData = string(data, keyLength);
    PublicKeyUnique remoteRatchetKey = EcCurve::decodePoint((const uint8_t*)keyData.data());
    staging->setRemoteRatchetKey(remoteRatchetKey.release());

    lck.unlock();
    LOGGER(DEBUGGING, __func__, " <--");
}

// Also used by AxoPreKeyConnector.
void createDerivedKeys(const string& masterSecret, string* root, string* chain, size_t requested)
{
    LOGGER(DEBUGGING, __func__, " -->");
    uint8_t derivedSecretBytes[256];     // we support up to 128 byte symmetric keys.

    // Use HKDF with 2 input parameters: ikm, info. The salt is SAH256 hash length 0 bytes.
    HKDF::deriveSecrets((uint8_t*)masterSecret.data(), masterSecret.size(), 
                        (uint8_t*)SILENT_MESSAGE.data(), SILENT_MESSAGE.size(), derivedSecretBytes, requested*2);
    root->assign((const char*)derivedSecretBytes, requested);
    chain->assign((const char*)derivedSecretBytes+requested, requested);
    LOGGER(DEBUGGING, __func__, " <--");
}

/*
Alice:
  KDF from master_key: RK, HKs=<none>, HKr, NHKs, NHKr, CKs=<none>, CKr
  DHIs, DHIr = A, B
  DHRs, DHRr = <none>, B1
  Ns, Nr = 0, 0
  PNs = 0
  ratchet_flag = True
Bob:
  KDF from master_key: RK, HKr=<none>, HKs, NHKr, NHKs, CKr=<none>, CKs
  DHIs, DHIr = B, A
  DHRs, DHRr = B1, <none>
  Ns, Nr = 0, 0
  PNs = 0
  ratchet_flag = False

 
 */
void setAxoExportedKey(const string& localUser, const string& user, const string& deviceId, const string& exportedKey, SQLiteStoreConv &store)
{
    LOGGER(DEBUGGING, __func__, " -->");
    unique_lock<mutex> lck(sessionLock);

    std::map<string, ZinaZrtpConnector*>::iterator it;
    it = stagingList->find(localUser);
    ZinaZrtpConnector* staging = it->second;
    if (staging == NULL) {
        LOGGER(ERROR, __func__, " <-- Illegal state, staging not found.");
        return;
    }
    stagingList->erase(it);

    string root;
    string chain;
    createDerivedKeys(exportedKey, &root, &chain, SYMMETRIC_KEY_LENGTH);
    auto conv = staging->getRemoteConversation();

//    hexdump(conv->getPartner().getName().c_str(), staging->getRemoteIdKey()->serialize());

    conv->setDHIr(PublicKeyUnique(staging->getRemoteIdKey()));
    staging->setRemoteIdKey(NULL);

    if (staging->getRole() == Alice) {
//        cerr << "Remote party '" << user << "' takes 'Alice' role" << endl;
        conv->setDHRr(PublicKeyUnique(staging->getRemoteRatchetKey()));     // Bob's B0 public part
        staging->setRemoteRatchetKey(NULL);
        conv->setRK(root);
        conv->setCKr(chain);
        conv->setRatchetFlag(true);
    }
    else {
//        cerr << "Remote party '" << user << "' takes 'Bob' role" << endl;
        conv->setDHRs(KeyPairUnique(staging->getRatchetKey()));           // Bob's B0 key
        staging->setRatchetKey(nullptr);
        conv->setRK(root);
        conv->setCKs(chain);
        conv->setRatchetFlag(false);
    }
    conv->storeConversation(store);

    delete staging;
    lck.unlock();
    LOGGER(DEBUGGING, __func__, " <--");
}

typedef AppInterfaceImpl* (*GET_APP_IF)();

#ifdef ANDROID_NDK
AppInterfaceImpl* j_getAxoAppInterface();
static GET_APP_IF getAppIf = j_getAxoAppInterface;
#elif defined __APPLE__
AppInterfaceImpl* t_getAxoAppInterface();
static GET_APP_IF getAppIf = t_getAxoAppInterface;
#else
#warning Get application interface call not initialized - ZRTP connection may not work
static GET_APP_IF getAppIf = NULL;
#endif

static string extractNameFromUri(const string& sipUri)
{
    size_t colon = sipUri.find_first_of(':');
    if (colon == string::npos)
        colon = 0;
    else
        colon++;
    size_t atSign = sipUri.find_first_of('@', colon);
    if (atSign == string::npos)
        atSign = sipUri.size();
    string name = sipUri.substr(colon, atSign-colon);
    return name;
}

const string getOwnAxoIdKey()
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (getAppIf == NULL)
        return string();
    AppInterfaceImpl* appIf = getAppIf();

    if (appIf == NULL)
        return string();

    const string& localUser = appIf->getOwnUser();

    auto local = ZinaConversation::loadLocalConversation(localUser, *appIf->getStore());
    if (!local->isValid() || !local->hasDHIs()) {
        return string();
    }

    const DhPublicKey& pubKey = local->getDHIs().getPublicKey();

    string key((const char*)pubKey.getPublicKeyPointer(), pubKey.getSize());

//    hexdump("+++ own key", key); Log("%s", hexBuffer);
    LOGGER(DEBUGGING, __func__, " <--");
    return key;
}

// This function queues a command to check and modify the conversation's verify state
// This must run via the command queue because it modifies the conversation (ratchet context)
void checkRemoteAxoIdKey(const string user, const string deviceId, const string pubKey, int32_t verifyState)
{
    LOGGER(DEBUGGING, __func__, " -->");
    if (getAppIf == NULL)
        return;
    AppInterface* appIf = getAppIf();

    if (appIf == NULL)
        return;

    const string& localUser = appIf->getOwnUser();

    string remoteName = extractNameFromUri(user);

    // This happens if user called an own sibling device, somehow P-Asserted-Id of called party
    // is not set for the caller - thus use own name. No risk - because the device id is not
    // available on any other user entry.
    if (remoteName.empty()) {
        remoteName = localUser;
    }

    auto checkRemoteIdKeyCmd = new CmdQueueInfo;

    checkRemoteIdKeyCmd->command = CheckRemoteIdKey;
    checkRemoteIdKeyCmd->stringData1 = remoteName;
    checkRemoteIdKeyCmd->stringData2 = deviceId;
    checkRemoteIdKeyCmd->stringData3 = pubKey;
    checkRemoteIdKeyCmd->int32Data = verifyState;
    appIf->addMsgInfoToRunQueue(unique_ptr<CmdQueueInfo>(checkRemoteIdKeyCmd));

    LOGGER(DEBUGGING, __func__, " <--");
}

