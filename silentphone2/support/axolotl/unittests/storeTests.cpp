#include <limits.h>

#include "../storage/sqlite/SQLiteStoreConv.h"

#include "../axolotl/crypto/DhKeyPair.h"
#include "../axolotl/crypto/Ec255PrivateKey.h"
#include "../axolotl/crypto/Ec255PublicKey.h"
#include "../util/cJSON.h"
#include "../util/b64helper.h"


#include "gtest/gtest.h"
#include <iostream>
#include <string>

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
static const uint8_t keyInData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32};
static const uint8_t keyInData_2[] = {"ZZZZZzzzzzYYYYYyyyyyXXXXXxxxxxW"};  // 32 bytes
static     std::string empty;

using namespace axolotl;

static string* preKeyJson(int32_t keyId, const DhKeyPair& preKeyPair)
{
    cJSON *root;
    char b64Buffer[280];   // Twice the max. size on binary data - b64 is times 1.5

    root = cJSON_CreateObject();

    int32_t b64Len = b64Encode((const uint8_t*)preKeyPair.getPrivateKey().privateData(), preKeyPair.getPrivateKey().getEncodedSize(), b64Buffer, 270);
    b64Buffer[b64Len] = 0;
    cJSON_AddStringToObject(root, "private", b64Buffer);

    b64Len = b64Encode((const uint8_t*)preKeyPair.getPublicKey().serialize().data(), preKeyPair.getPublicKey().getEncodedSize(), b64Buffer, 270);
    b64Buffer[b64Len] = 0;
    cJSON_AddStringToObject(root, "public", b64Buffer);

    char *out = cJSON_Print(root);
    std::string* data = new std::string(out);
    cerr << "PreKey data to store: " << *data << endl;
    cJSON_Delete(root); free(out);

    return data;
}

TEST(PreKeyStore, Basic)
{
    // Need a key pair here
    const Ec255PublicKey baseKey_1(keyInData_1);
    const Ec255PrivateKey basePriv_1(keyInData_2);
    const DhKeyPair basePair(baseKey_1, basePriv_1);

    string* pk = preKeyJson(3, basePair);

    SQLiteStoreConv* pks = SQLiteStoreConv::getStoreForTesting();
    pks->setKey(std::string((const char*)keyInData, 32));
    pks->openStore(std::string());

    string* pk_1 = pks->loadPreKey(3);
    ASSERT_EQ(NULL, pk_1) <<  "Some data in an empty store?";

    pks->storePreKey(3, *pk);
    ASSERT_TRUE(pks->containsPreKey(3));

    pks->storePreKey(3, *pk);
    ASSERT_TRUE(pks->getSqlCode() == SQLITE_CONSTRAINT) << pks->getLastError();

    pk_1 = pks->loadPreKey(3);
    ASSERT_EQ(*pk, *pk_1);
    delete pk_1;

    pks->removePreKey(3);
    ASSERT_FALSE(pks->containsPreKey(3));

    SQLiteStoreConv::closeStoreForTesting(pks);
}

