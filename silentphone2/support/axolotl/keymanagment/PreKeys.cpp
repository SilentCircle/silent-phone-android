#include "PreKeys.h"

#include "../axolotl/crypto/EcCurve.h"
#include "../util/cJSON.h"
#include "../util/b64helper.h"
#include "../axolotl/Constants.h"


#include <cryptcommon/ZrtpRandom.h>
#include <stdlib.h>
#include <iostream>
#include <time.h>
#include <utility>

using namespace axolotl;
static string* preKeyJson(int32_t keyId, const DhKeyPair& preKeyPair)
{
    cJSON *root;
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5

    root = cJSON_CreateObject();

    int32_t b64Len = b64Encode((const uint8_t*)preKeyPair.getPrivateKey().privateData(), preKeyPair.getPrivateKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "private", b64Buffer);

    b64Len = b64Encode((const uint8_t*)preKeyPair.getPublicKey().serialize().data(), preKeyPair.getPublicKey().getEncodedSize(), b64Buffer, MAX_KEY_BYTES_ENCODED*2);
    cJSON_AddStringToObject(root, "public", b64Buffer);

    char *out = cJSON_Print(root);
    std::string* data = new std::string(out);
    cJSON_Delete(root); free(out);

    return data;
}

pair<int32_t, const DhKeyPair*> PreKeys::generatePreKey(SQLiteStoreConv* store)
{
    int32_t keyId;
    for (bool ok = false; !ok; ) {
        ZrtpRandom::getRandomData((uint8_t*)&keyId, sizeof(int32_t));
        keyId &= 0x7fffffff;      // always a positive value
        ok = !store->containsPreKey(keyId);
    }
    const DhKeyPair* preKeyPair = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);

    // Create storage format (JSON) of pre-key and store it. Storage encrypts the JSON data
    const string pk = *preKeyJson(keyId, *preKeyPair);
    store->storePreKey(keyId, pk);

    pair <int32_t, const DhKeyPair*> prePair(keyId, preKeyPair);
    return prePair;
}

list<pair<int32_t, const DhKeyPair*> >* PreKeys::generatePreKeys(SQLiteStoreConv* store, int32_t num)
{
    std::list< pair<int32_t, const DhKeyPair*> >* pkrList = new std::list< pair<int32_t, const DhKeyPair*> >;

    for (int32_t i = 0; i < num; i++) {
        pair<int32_t, const DhKeyPair*> pkPair = generatePreKey(store);
        pkrList->push_back(pkPair);
    }
    return pkrList;
}

DhKeyPair* PreKeys::parsePreKeyData(const string& data)
{
    char b64Buffer[MAX_KEY_BYTES_ENCODED*2];   // Twice the max. size on binary data - b64 is times 1.5
    uint8_t binBuffer[MAX_KEY_BYTES_ENCODED];

    cJSON* root = cJSON_Parse(data.c_str());
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "public")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    int32_t b64Length = strlen(b64Buffer);
    int32_t binLength = b64Decode(b64Buffer, b64Length, binBuffer, MAX_KEY_BYTES_ENCODED);
    const DhPublicKey* pubKey = EcCurve::decodePoint(binBuffer);

    // Here we may check the public curve type and do some code to support different curves and
    // create to correct private key. The serilaized public key data contain a curve type id. For
    // the time being use Ec255 (DJB's curve 25519).
    strncpy(b64Buffer, cJSON_GetObjectItem(root, "private")->valuestring, MAX_KEY_BYTES_ENCODED*2-1);
    binLength = b64Decode(b64Buffer, strlen(b64Buffer), binBuffer, MAX_KEY_BYTES_ENCODED);
    const DhPrivateKey* privKey = EcCurve::decodePrivatePoint(binBuffer, binLength);

    cJSON_Delete(root);

    DhKeyPair* keyPair = new DhKeyPair(*pubKey, *privKey);
    delete pubKey;
    delete privKey;

    return keyPair;
}
