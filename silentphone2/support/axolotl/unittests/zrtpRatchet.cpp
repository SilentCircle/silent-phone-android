#include <limits.h>
#include "gtest/gtest.h"

#include "../axolotl/state/AxoConversation.h"
#include "../axolotl/AxoZrtpConnector.h"
#include "../storage/sqlite/SQLiteStoreConv.h"
#include "../axolotl/crypto/EcCurve.h"
#include "../axolotl/ratchet/AxoRatchet.h"

#include <iostream>
using namespace axolotl;
using namespace std;

static AxoConversation* p1Conv;
static AxoConversation* p2Conv;

static const string p1Name("party1");
static const string p2Name("party2");

static const string p1dev("party1_dev");
static const string p2dev("party2_dev");

const string getAxoPublicKeyData(const string& localUser, const string& user, const string& deviceId);
void setAxoPublicKeyData(const string& localUser, const string& user, const string& deviceId, const string& pubKeyData);
void setAxoExportedKey( const string& localUser, const string& user, const string& deviceId, const string& exportedKey );

static const uint8_t keyInData[] = {0,1,2,3,4,5,6,7,8,9,19,18,17,16,15,14,13,12,11,10,20,21,22,23,24,25,26,27,28,20,31,30};

// Used in testing and debugging to do in-depth checks
static void hexdump(const char* title, const unsigned char *s, int l) {
    int n=0;

    if (s == NULL) return;

    fprintf(stderr, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            fprintf(stderr, "\n%04x",n);
        fprintf(stderr, " %02x",s[n]);
    }
    fprintf(stderr, "\n");
}
static void hexdump(const char* title, const std::string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}

void prepareStore()
{
    SQLiteStoreConv* store = SQLiteStoreConv::getStore();
    if (store->isReady())
        return;
    store->setKey(std::string((const char*)keyInData, 32));
    store->openStore(std::string());
    if (SQL_FAIL(store->getSqlCode())) {
        cerr << store->getLastError() << endl;
        exit(1);
    }
    p1Conv = new AxoConversation(p1Name, p1Name, emptyString);   // Create P1's own (local) conversation
    p1Conv->setDHIs(EcCurve::generateKeyPair(EcCurveTypes::Curve25519));
    p1Conv->storeConversation();

    p2Conv = new AxoConversation(p2Name, p2Name, emptyString);   // Create P2's own (local) conversation
    p2Conv->setDHIs(EcCurve::generateKeyPair(EcCurveTypes::Curve25519));
    p2Conv->storeConversation();
}


TEST(ZrtpRatchet, Setup) 
{
    prepareStore();

    // Simulate the ZRTP data exchange via the confirm packets
    string p1_0_p2 = getAxoPublicKeyData(p1Name, p2Name, p2dev);
    string p2_0_p1 = getAxoPublicKeyData(p2Name, p1Name, p1dev);

    setAxoPublicKeyData(p1Name, p2Name, p2dev, p2_0_p1);
    setAxoPublicKeyData(p2Name, p1Name, p1dev, p1_0_p2);

    string exportedKey((const char*)keyInData, 32);
    setAxoExportedKey(p1Name, p2Name, p2dev, exportedKey);
    setAxoExportedKey(p2Name, p1Name, p1dev, exportedKey);

    // Load P2's conversation
    AxoConversation* p1p2Conv = AxoConversation::loadConversation(p1Name, p2Name, p2dev);
    ASSERT_TRUE(p1p2Conv != NULL);
    ASSERT_TRUE(p2Conv->getDHIs()->getPublicKey() == *p1p2Conv->getDHIr());

    AxoConversation* p2p1Conv = AxoConversation::loadConversation(p2Name, p1Name, p1dev);
    ASSERT_TRUE(p1p2Conv != NULL);
    ASSERT_TRUE(p1Conv->getDHIs()->getPublicKey() == *p2p1Conv->getDHIr());

    if (p1p2Conv->getDHRs() == NULL) {    // This conversation is Alice

    }
    else {
    }
    string p1toP2("11__22------");
    string p2toP1("22__11------");

    const string* p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, p1toP2, string(), NULL);
    ASSERT_TRUE(p1p2Wire != NULL);
//    hexdump("p1p2Wire", *p1p2Wire);

    string* p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *p1p2Wire, string(), NULL);
    ASSERT_TRUE(p1p2Plain != NULL);
//    hexdump("p1p2Plain", *p1p2Plain);
//    cerr << *p1p2Plain << endl;
    ASSERT_EQ(p1toP2, *p1p2Plain);

    const string* p2p1Wire = AxoRatchet::encrypt(*p2p1Conv, p2toP1, string(), NULL);
    ASSERT_TRUE(p2p1Wire != NULL);
//    hexdump("p2p1Wire", *p2p1Wire);

    string* p2p1Plain =  AxoRatchet::decrypt(p1p2Conv, *p2p1Wire, string(), NULL);
    ASSERT_TRUE(p2p1Plain != NULL);
//    hexdump("p2p1Plain", *p2p1Plain);
//    cerr << *p2p1Plain << endl;
    ASSERT_EQ(p2toP1, *p2p1Plain);

    const std::string baseMsg("This is round: ");
    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P1");
        p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, loop, string(), NULL);

        p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *p1p2Wire, string(), NULL);
//        std::cerr << *p1p2Plain << '\n';
        ASSERT_EQ(loop, *p1p2Plain);
        delete p1p2Wire;
        delete p1p2Plain;
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P2");
        p2p1Wire = AxoRatchet::encrypt(*p2p1Conv, loop, string(), NULL);

        p2p1Plain = AxoRatchet::decrypt(p1p2Conv, *p2p1Wire, string(), NULL);
//        std::cerr << *p2p1Plain << '\n';
        ASSERT_EQ(loop, *p2p1Plain);
        delete p2p1Wire;
        delete p2p1Plain;
    }

    std::list<std::pair<std::string, const string*>*> outOfOrder;

    for (int32_t i = 0; i < 10; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P1 - unordered");
        p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, loop, string(), NULL);
        std::pair<std::string, const string*>* pair = new std::pair<std::string, const string*>(loop, p1p2Wire);
        if (i&1)
            outOfOrder.push_back(pair);
        else
            outOfOrder.push_front(pair);
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P1 - second time");
        p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, loop, string(), NULL);

        p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *p1p2Wire, string(), NULL);
//        std::cerr << *p1p2Plain << '\n';
        ASSERT_EQ(loop, *p1p2Plain);
        delete p1p2Wire;
        delete p1p2Plain;
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P2 - second time");
        p2p1Wire = AxoRatchet::encrypt(*p2p1Conv, loop, string(), NULL);

        p2p1Plain = AxoRatchet::decrypt(p1p2Conv, *p2p1Wire, string(), NULL);
//        std::cerr << *p2p1Plain << '\n';
        ASSERT_EQ(loop, *p2p1Plain);
        delete p2p1Wire;
        delete p2p1Plain;
    }

        std::list<std::pair<std::string, const string*>*>::iterator it;

    for(it = outOfOrder.begin(); it != outOfOrder.end();) {
        std::pair<std::string, const string*>* pair = *it;
        p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *(pair->second), string(), NULL);
//        std::cerr << *p1p2Plain << '\n';
        ASSERT_EQ(pair->first, *p1p2Plain);
        delete pair->second;
        delete pair;
        delete p1p2Plain;
        outOfOrder.erase(it++);
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P1 - third time");
        p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, loop, string(), NULL);

        p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *p1p2Wire, string(), NULL);
//        std::cerr << *p1p2Plain << '\n';
        ASSERT_EQ(loop, *p1p2Plain);
        delete p1p2Wire;
        delete p1p2Plain;
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P2 - third time");
        p2p1Wire = AxoRatchet::encrypt(*p2p1Conv, loop, string(), NULL);

        p2p1Plain = AxoRatchet::decrypt(p1p2Conv, *p2p1Wire, string(), NULL);
//        std::cerr << *p2p1Plain << '\n';
        ASSERT_EQ(loop, *p2p1Plain);
        delete p2p1Wire;
        delete p2p1Plain;
    }

    for (int32_t i = 0; i < 4; i++) {
        std::string loop = baseMsg;
        const char c = i+0x30;
        loop.append(1, c).append(" from P1 - forth time");
        p1p2Wire = AxoRatchet::encrypt(*p1p2Conv, loop, string(), NULL);

        p1p2Plain = AxoRatchet::decrypt(p2p1Conv, *p1p2Wire, string(), NULL);
//        std::cerr << *p1p2Plain << '\n';
        ASSERT_EQ(loop, *p1p2Plain);
        delete p1p2Wire;
        delete p1p2Plain;
    }

}






























