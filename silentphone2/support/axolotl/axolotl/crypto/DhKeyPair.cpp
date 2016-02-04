#include "DhKeyPair.h"
#include "EcCurveTypes.h"
#include "Ec255PrivateKey.h"
#include "Ec255PublicKey.h"

using namespace axolotl;

DhKeyPair::DhKeyPair(const DhPublicKey& publicKey, const DhPrivateKey& privateKey)
{
    if (publicKey.getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(publicKey.getPublicKeyPointer());
    else
        publicKey_ = NULL;

    if (privateKey.getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(privateKey.privateData());
    else
        privateKey_ = NULL;
}

DhKeyPair::DhKeyPair (const DhKeyPair& otherPair)
{
    if (otherPair.publicKey_->getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(otherPair.publicKey_->getPublicKeyPointer());
    else
        publicKey_ = NULL;

    if (otherPair.privateKey_->getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(otherPair.privateKey_->privateData());
    else
        privateKey_ = NULL;
}

DhKeyPair::~DhKeyPair()
{
    delete publicKey_;
    delete privateKey_;
}

DhKeyPair& DhKeyPair::operator=(const DhKeyPair& otherPair)
{
    if (this == &otherPair)
        return *this;

    delete privateKey_;
    delete publicKey_;
    if (otherPair.publicKey_->getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(otherPair.publicKey_->getPublicKeyPointer());
    else
        publicKey_ = NULL;

    if (otherPair.privateKey_->getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(otherPair.privateKey_->privateData());
    else
        privateKey_ = NULL;
    return *this;
}
