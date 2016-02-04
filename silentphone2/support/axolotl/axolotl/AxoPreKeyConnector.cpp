#include "AxoPreKeyConnector.h"

#include "../axolotl/Constants.h"
#include "../axolotl/crypto/EcCurve.h"

#include "../util/cJSON.h"
#include "../util/b64helper.h"
#include "../keymanagment/PreKeys.h"

#include "../storage/sqlite/SQLiteStoreConv.h"
#include <iostream>
#include <stdio.h>

// Generic function, located in AxoZrtpConnector.
void createDerivedKeys(const std::string& masterSecret, std::string* root, std::string* chain, int32_t requested);

using namespace axolotl;

void Log(const char* format, ...);

#ifdef UNITTESTS
static char hexBuffer[2000] = {0};
static void hexdump(const char* title, const unsigned char *s, int l) {
    int n=0;
    if (s == NULL) return;

    memset(hexBuffer, 0, 2000);
    int len = sprintf(hexBuffer, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            len += sprintf(hexBuffer+len, "\n%04x",n);
        len += sprintf(hexBuffer+len, " %02x",s[n]);
    }
    sprintf(hexBuffer+len, "\n");
}
static void hexdump(const char* title, const std::string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}
#endif

/*
 * We are P1 at this point:
 * 
    A  = P1_I   (private data)
    B  = P2_I   (public data)
    A0 = P1_PK1 (private data)
    B0 = P2_PK1 (public data)

    masterSecret = HASH(DH(A, B0) || DH(A0, B) || DH(A0, B0))
*/
int32_t AxoPreKeyConnector::setupConversationAlice(const string& localUser, const string& user, const string& deviceId, 
                                                   int32_t bobPreKeyId, pair<const DhPublicKey*, const DhPublicKey*> bobKeys)
{
    AxoConversation* conv = AxoConversation::loadConversation(localUser, user, deviceId);
    if (conv != NULL) {              // Already a conversation available, no setup necessary
        return AXO_CONV_EXISTS;
    }
    AxoConversation* localConv = AxoConversation::loadLocalConversation(localUser);
    if (localConv == NULL)
        return NO_OWN_ID;

    const DhKeyPair* A = new DhKeyPair(*(localConv->getDHIs()));
    const DhKeyPair* A0 = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
    delete localConv;

    const DhPublicKey* B = bobKeys.first;
    const DhPublicKey* B0 = bobKeys.second;

    uint8_t masterSecret[EcCurveTypes::Curve25519KeyLength*3];

    EcCurve::calculateAgreement(*B0, A->getPrivateKey(), masterSecret, EcCurveTypes::Curve25519KeyLength);
    EcCurve::calculateAgreement(*B, A0->getPrivateKey(), masterSecret+EcCurveTypes::Curve25519KeyLength, EcCurveTypes::Curve25519KeyLength);
    EcCurve::calculateAgreement(*B0, A0->getPrivateKey(), masterSecret+EcCurveTypes::Curve25519KeyLength*2, EcCurveTypes::Curve25519KeyLength);
    string master((const char*)masterSecret, EcCurveTypes::Curve25519KeyLength*3);

//    hexdump("master Alice", master); Log("%s", hexBuffer);

    // derive root and chain key
    std::string root;
    std::string chain;
    createDerivedKeys(master, &root, &chain, SYMMETRIC_KEY_LENGTH);
    memset_volatile(masterSecret, 0, EcCurveTypes::Curve25519KeyLength*3);
    memset_volatile((void*)master.data(), 0, master.size());

    conv = new AxoConversation(localUser, user, deviceId);

    // Conversation takes over the ownership of the keys.
    conv->setDHIr(B);
    conv->setDHIs(A);
//    cerr << "Remote party '" << user << "' takes 'Alice' role" << endl;
    conv->setDHRr(B0);              // Bob's B0 public part
    conv->setA0(A0);                // Alice's generated pre-key.
    conv->setRK(root);
    conv->setCKr(chain);
    conv->setPreKeyId(bobPreKeyId);
    conv->setRatchetFlag(true);
    conv->storeConversation();
    delete conv;

    return OK;
}

/*
 * We are P1 at this point:
 *  The conversation msut be a new conversation state, otherwise clear old state
    A  = P2_I   (private data)
    B  = P1_I   (public data)
    A0 = P2_PK1 (private data)
    B0 = P1_PK1 (public data)

*/
int32_t AxoPreKeyConnector::setupConversationBob(AxoConversation* conv, int32_t bobPreKeyId, const DhPublicKey* aliceId, const DhPublicKey* alicePreKey)
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
//    store->dumpPreKeys();
    string* preKeyData = store->loadPreKey(bobPreKeyId);

    // If no such prekey then check if the converstaion is already set-up (RK available)
    // if yes -> OK, Alice sent the key more then one time because Bob didn't answer her
    // yet. Otherwise Bob got an illegal pre-key.
    if (preKeyData == NULL) {
        if (conv->getRK().empty()) {
            conv->setErrorCode(NO_PRE_KEY_FOUND);
            return -1;
        }
        else
            return OK;
    }
    store->removePreKey(bobPreKeyId);
    conv->reset();

//     cerr << "Load local of: " << conv.getLocalUser() << ", sender: " << conv.getPartner().getName() << endl;
//     cerr << "PreKey id: " << bobPreKeyId << ", data: " << preKeyData << endl;

    DhKeyPair* A0 = PreKeys::parsePreKeyData(*preKeyData);
    delete preKeyData;

    AxoConversation* localConv = AxoConversation::loadLocalConversation(conv->getLocalUser());
    const DhKeyPair* A = new DhKeyPair(*(localConv->getDHIs()));
    delete localConv;

    const DhPublicKey* B = aliceId;
    const DhPublicKey* B0 = alicePreKey;

    uint8_t masterSecret[EcCurveTypes::Curve25519KeyLength*3];

    EcCurve::calculateAgreement(*B, A0->getPrivateKey(), masterSecret, EcCurveTypes::Curve25519KeyLength);
    EcCurve::calculateAgreement(*B0, A->getPrivateKey(), masterSecret+EcCurveTypes::Curve25519KeyLength, EcCurveTypes::Curve25519KeyLength);
    EcCurve::calculateAgreement(*B0, A0->getPrivateKey(), masterSecret+EcCurveTypes::Curve25519KeyLength*2, EcCurveTypes::Curve25519KeyLength);
    string master((const char*)masterSecret, EcCurveTypes::Curve25519KeyLength*3);
    delete B0;
//    hexdump("master Bob", master);  Log("%s", hexBuffer);

    // derive root and chain key
    std::string root;
    std::string chain;
    createDerivedKeys(master, &root, &chain, SYMMETRIC_KEY_LENGTH);
    memset_volatile(masterSecret, 0, EcCurveTypes::Curve25519KeyLength*3);
    memset_volatile((void*)master.data(), 0, master.size());

//    cerr << "Remote party '" << conv.getPartner().getName() << "' takes 'Bob' role" << endl;
    conv->setDHRs(A0);              // Actually Bob's pre-key - because of the optimized pre-key handling
    conv->setDHIs(A);               // Bob's (own) identity keys
    conv->setDHIr(B);               // Alice's (remote) identity key
    conv->setRK(root);
    conv->setCKs(chain);
    conv->setRatchetFlag(false);

    return OK;
}