#include "DhKeyPair.h"
#include "EcCurveTypes.h"
#include "Ec255PrivateKey.h"
#include "Ec255PublicKey.h"
#include "../../logging/AxoLogging.h"

using namespace axolotl;

DhKeyPair::DhKeyPair(const DhPublicKey& publicKey, const DhPrivateKey& privateKey)
{
    LOGGER(INFO, __func__, " -->");
    if (publicKey.getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(publicKey.getPublicKeyPointer());
    else {
        publicKey_ = NULL;
        LOGGER(ERROR, "Unsuported public key type.")
    }

    if (privateKey.getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(privateKey.privateData());
    else {
        privateKey_ = NULL;
        LOGGER(ERROR, "Unsuported private key type.")
    }

    LOGGER(INFO, __func__, " <--");
}

DhKeyPair::DhKeyPair(const DhKeyPair& otherPair)
{
    LOGGER(INFO, __func__, " -->");
    if (otherPair.publicKey_->getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(otherPair.publicKey_->getPublicKeyPointer());
    else {
        publicKey_ = NULL;
        LOGGER(ERROR, "Unsuported public key type.")
    }

    if (otherPair.privateKey_->getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(otherPair.privateKey_->privateData());
    else {
        privateKey_ = NULL;
        LOGGER(ERROR, "Unsuported private key type.")
    }

    LOGGER(INFO, __func__, " <--");
}

DhKeyPair::~DhKeyPair()
{
    delete publicKey_;
    delete privateKey_;
}

DhKeyPair& DhKeyPair::operator=(const DhKeyPair& otherPair) {
    if (this == &otherPair) {
        LOGGER(INFO, __func__, " <--");
        return *this;
    }

    delete privateKey_;
    delete publicKey_;
    if (otherPair.publicKey_->getType() == EcCurveTypes::Curve25519)
        publicKey_  = new Ec255PublicKey(otherPair.publicKey_->getPublicKeyPointer());
    else {
        publicKey_ = NULL;
        LOGGER(ERROR, "Unsuported public key type.")
    }

    if (otherPair.privateKey_->getType() == EcCurveTypes::Curve25519)
        privateKey_  = new Ec255PrivateKey(otherPair.privateKey_->privateData());
    else {
        privateKey_ = NULL;
        LOGGER(ERROR, "Unsuported private key type.")
    }
    LOGGER(INFO, __func__, " <--");
    return *this;
}
