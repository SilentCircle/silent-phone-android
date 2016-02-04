#include <limits.h>
#include "../axolotl/crypto/Ec255PrivateKey.h"
#include "../axolotl/crypto/Ec255PublicKey.h"
#include "../axolotl/crypto/EcCurveTypes.h"
#include "../axolotl/crypto/EcCurve.h"
#include "../axolotl/crypto/AesCbc.h"
#include "gtest/gtest.h"
#include <iostream>

using namespace axolotl;
static int32_t type255 = EcCurveTypes::Curve25519;

TEST(Ec255PublicKey, Serialize) {
    // 32 bytes
    uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
    uint8_t encodedData[33] = {0};   // encodedData size is 32 + 1

    Ec255PublicKey key(keyInData);

    EXPECT_EQ(type255, key.getType());
    EXPECT_EQ(33, key.getEncodedSize());

    key.serialize(encodedData);
    ASSERT_EQ(type255, *encodedData & 0xff);  // public key has a type byte (encoded key buffer)
    for (int i = 0; i < 32; ++i) {
        ASSERT_EQ(keyInData[i], encodedData[i+1]) << "key material and output differ at index " << i;
    }
    memset(encodedData, 0, 33);
    key.getPublicKey(encodedData);
    for (int i = 0; i < 32; ++i) {
        ASSERT_EQ(keyInData[i], encodedData[i]) << "key material and output differ at index (getPublicKeyKey) " << i;
    }
}

TEST(Ec255PrivateKey, Serialize) {
    // 32 bytes
    uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
    uint8_t encodedData[32] = {0};   // encodedData for private key is 32

    Ec255PrivateKey key(keyInData);

    EXPECT_EQ(type255, key.getType());
    EXPECT_EQ(32, key.getEncodedSize());  // private key contains no type byte in the buffer

    key.serialize(encodedData);
    for (int i = 0; i < 32; ++i) {
        ASSERT_EQ(keyInData[i], encodedData[i]) << "key material and output differ at index " << i;
    }
    memset(encodedData, 0, 32);
    key.getPrivateKey(encodedData);
    for (int i = 0; i < 32; ++i) {
        ASSERT_EQ(keyInData[i], encodedData[i]) << "key material and output differ at index (getPrivateKey) " << i;
    }
}

TEST(Ec255PublicKey, CopyCompare) {
    // 32 bytes
    uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
    uint8_t keyInData_1[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,32};
 
    Ec255PublicKey key(keyInData);

    ASSERT_TRUE(key == key) << "Compare the same key";

    Ec255PublicKey key1 = key;
    ASSERT_TRUE(key == key1) << "Compare assigned key";

    Ec255PublicKey key2(key);
    ASSERT_TRUE(key == key2) << "Compare copy initializer key";

    Ec255PublicKey key3(keyInData_1);
    ASSERT_FALSE(key == key3) << "Compare with different key data";
}


uint8_t alicePublic[] = { 
    0x05,  0x1b,  0xb7,  0x59,  0x66, 0xf2,  0xe9,  0x3a,  0x36,  0x91,
    0xdf,  0xff,  0x94,  0x2b,  0xb2, 0xa4,  0x66,  0xa1,  0xc0,  0x8b,
    0x8d,  0x78,  0xca,  0x3f,  0x4d, 0x6d,  0xf8,  0xb8,  0xbf,  0xa2,
    0xe4,  0xee,  0x28};

uint8_t alicePrivate[] = {
    0xc8,  0x06,  0x43,  0x9d,  0xc9, 0xd2,  0xc4,  0x76,  0xff,  0xed,
    0x8f,  0x25,  0x80,  0xc0,  0x88, 0x8d,  0x58,  0xab,  0x40,  0x6b,
    0xf7,  0xae,  0x36,  0x98,  0x87, 0x90,  0x21,  0xb9,  0x6b,  0xb4,
    0xbf,  0x59};

uint8_t bobPublic[] = {
    0x05,  0x65,  0x36,  0x14,  0x99, 0x3d,  0x2b,  0x15,  0xee,  0x9e,
    0x5f,  0xd3,  0xd8,  0x6c,  0xe7, 0x19,  0xef,  0x4e,  0xc1,  0xda,
    0xae,  0x18,  0x86,  0xa8,  0x7b, 0x3f,  0x5f,  0xa9,  0x56,  0x5a,
    0x27,  0xa2,  0x2f};

uint8_t bobPrivate[] = {
    0xb0,  0x3b,  0x34,  0xc3,  0x3a, 0x1c,  0x44,  0xf2,  0x25,  0xb6,
    0x62,  0xd2,  0xbf,  0x48,  0x59, 0xb8,  0x13,  0x54,  0x11,  0xfa,
    0x7b,  0x03,  0x86,  0xd4,  0x5f, 0xb7,  0x5d,  0xc5,  0xb9,  0x1b,
    0x44,  0x66};

uint8_t shared[] = {
    0x32,  0x5f,  0x23,  0x93,  0x28, 0x94,  0x1c,  0xed,  0x6e,  0x67,
    0x3b,  0x86,  0xba,  0x41,  0x01, 0x74,  0x48,  0xe9,  0x9b,  0x64,
    0x9a,  0x9c,  0x38,  0x06,  0xc1, 0xdd,  0x7c,  0xa4,  0xc4,  0x77,
    0xe6,  0x29};


TEST(Curve25519, Agreement)
{
    const EcPublicKey* alicePublicKey = EcCurve::decodePoint(alicePublic);
    const EcPrivateKey* alicePrivateKey = EcCurve::decodePrivatePoint(alicePrivate, sizeof(alicePrivate));

    const EcPublicKey* bobPublicKey = EcCurve::decodePoint(bobPublic);
    const EcPrivateKey* bobPrivateKey = EcCurve::decodePrivatePoint(bobPrivate, sizeof(bobPrivate));

    uint8_t sharedOne[Ec255PrivateKey::KEY_LENGTH] = {0};
    uint8_t sharedTwo[Ec255PrivateKey::KEY_LENGTH] = {0};

    int32_t result = EcCurve::calculateAgreement(*alicePublicKey, *bobPrivateKey, sharedOne, Ec255PrivateKey::KEY_LENGTH);
    ASSERT_EQ(result, 32) << "Calculate shared agreement one failed.";

    result = EcCurve::calculateAgreement(*bobPublicKey, *alicePrivateKey, sharedTwo, Ec255PrivateKey::KEY_LENGTH);
    ASSERT_EQ(result, 32) << "Calculate shared agreement two failed.";

    for (int i = 0; i < Ec255PrivateKey::KEY_LENGTH; ++i) {
        ASSERT_EQ(shared[i], sharedOne[i]) << "Agreement one differs at index " << i;
    }

    for (int i = 0; i < Ec255PrivateKey::KEY_LENGTH; ++i) {
        ASSERT_EQ(shared[i], sharedTwo[i]) << "Agreement two differs at index " << i;
    }
    delete alicePublicKey;
    delete alicePrivateKey;
    delete bobPublicKey;
    delete bobPrivateKey;
}

uint8_t aliceIdentityPrivate[] = {
    0xc0, 0x97, 0x24, 0x84, 0x12, 0xe5, 0x8b, 0xf0, 0x5d, 0xf4,
    0x87, 0x96, 0x82, 0x05, 0x13, 0x27, 0x94, 0x17, 0x8e, 0x36,
    0x76, 0x37, 0xf5, 0x81, 0x8f, 0x81, 0xe0, 0xe6, 0xce, 0x73,
    0xe8, 0x65};

uint8_t aliceIdentityPublic[] = {
    0x05, 0xab, 0x7e, 0x71, 0x7d, 0x4a, 0x16, 0x3b, 0x7d, 0x9a,
    0x1d, 0x80, 0x71, 0xdf, 0xe9, 0xdc, 0xf8, 0xcd, 0xcd, 0x1c,
    0xea, 0x33, 0x39, 0xb6, 0x35, 0x6b, 0xe8, 0x4d, 0x88, 0x7e,
    0x32, 0x2c, 0x64};

uint8_t aliceEphemeralPublic[] = {
    0x05, 0xed, 0xce, 0x9d, 0x9c, 0x41, 0x5c, 0xa7, 0x8c, 0xb7,
    0x25, 0x2e, 0x72, 0xc2, 0xc4, 0xa5, 0x54, 0xd3, 0xeb, 0x29,
    0x48, 0x5a, 0x0e, 0x1d, 0x50, 0x31, 0x18, 0xd1, 0xa8, 0x2d,
    0x99, 0xfb, 0x4a};

uint8_t aliceSignature[] = {
    0x5d, 0xe8, 0x8c, 0xa9, 0xa8, 0x9b, 0x4a, 0x11, 0x5d, 0xa7,
    0x91, 0x09, 0xc6, 0x7c, 0x9c, 0x74, 0x64, 0xa3, 0xe4, 0x18,
    0x02, 0x74, 0xf1, 0xcb, 0x8c, 0x63, 0xc2, 0x98, 0x4e, 0x28,
    0x6d, 0xfb, 0xed, 0xe8, 0x2d, 0xeb, 0x9d, 0xcd, 0x9f, 0xae,
    0x0b, 0xfb, 0xb8, 0x21, 0x56, 0x9b, 0x3d, 0x90, 0x01, 0xbd,
    0x81, 0x30, 0xcd, 0x11, 0xd4, 0x86, 0xce, 0xf0, 0x47, 0xbd,
    0x60, 0xb8, 0x6e, 0x88};


TEST(Curve25519, GenerateKeys)
{
    const EcKeyPair* alice = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);
    const EcKeyPair* bob = EcCurve::generateKeyPair(EcCurveTypes::Curve25519);

    uint8_t sharedOne[Ec255PrivateKey::KEY_LENGTH] = {0};
    uint8_t sharedTwo[Ec255PrivateKey::KEY_LENGTH] = {0};

    int32_t result = EcCurve::calculateAgreement(alice->getPublicKey(), bob->getPrivateKey(), sharedOne, Ec255PrivateKey::KEY_LENGTH);
    ASSERT_EQ(result, 32) << "Calculate shared agreement one failed.";

    result = EcCurve::calculateAgreement(bob->getPublicKey(), alice->getPrivateKey(), sharedTwo, Ec255PrivateKey::KEY_LENGTH);
    ASSERT_EQ(result, 32) << "Calculate shared agreement two failed.";

    for (int i = 0; i < Ec255PrivateKey::KEY_LENGTH; ++i) {
        ASSERT_EQ(sharedOne[i], sharedTwo[i]) << "Agreements differ at index " << i;
    }
}


TEST(Aes, Basic)
{
    // 32 bytes
    uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
    // 16 bytes
    uint8_t ivData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14};

    std::string key((const char*)keyInData, sizeof(keyInData));
    std::string iv((const char*)ivData, sizeof(ivData));

    std::string plainText("0123456789");   // 10 characters, expect 6 bytes padding
    std::string *cryptText = new std::string();

    aesCbcEncrypt(key, iv, plainText, cryptText);
    ASSERT_EQ(cryptText->size(), 16) << "Wrong cryptText size";
    ASSERT_NE(plainText, *cryptText);

    std::string *newPlainText = new std::string();

    aesCbcDecrypt(key, iv, *cryptText, newPlainText);
    ASSERT_EQ(newPlainText->size(), 16) << "Wrong newPlainText size";
    ASSERT_EQ((*newPlainText)[15], '\6') << "Wrong padding byte";
    
    ASSERT_TRUE(checkAndRemovePadding(*newPlainText));
    ASSERT_EQ(plainText, *newPlainText);
}

TEST(Aes, ZeroLen)
{
    // 32 bytes
    uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};
    // 16 bytes
    uint8_t ivData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14};

    std::string key((const char*)keyInData, sizeof(keyInData));
    std::string iv((const char*)ivData, sizeof(ivData));

    std::string plainText;   // 0 characters, expect 16 bytes padding
    std::string *cryptText = new std::string();

    aesCbcEncrypt(key, iv, plainText, cryptText);
    ASSERT_EQ(cryptText->size(), 16) << "Wrong cryptText size";
    ASSERT_NE(plainText, *cryptText);

    std::string *newPlainText = new std::string();

    aesCbcDecrypt(key, iv, *cryptText, newPlainText);
    ASSERT_EQ(newPlainText->size(), 16) << "Wrong newPlainText size";
    ASSERT_EQ(16, (*newPlainText)[15]) << "Wrong padding byte";

    ASSERT_TRUE(checkAndRemovePadding(*newPlainText));
    ASSERT_EQ(plainText, *newPlainText);
}





























