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
#include "DhKeyPair.h"
#include "EcCurveTypes.h"
#include "Ec255PrivateKey.h"
#include "Ec255PublicKey.h"
#include "../../logging/ZinaLogging.h"

using namespace zina;

DhKeyPair::DhKeyPair(const DhPublicKey& publicKey, const DhPrivateKey& privateKey)
{
    LOGGER(DEBUGGING, __func__, " -->");
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

    LOGGER(DEBUGGING, __func__, " <--");
}

DhKeyPair::DhKeyPair(const DhKeyPair& otherPair)
{
    LOGGER(DEBUGGING, __func__, " -->");
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

    LOGGER(DEBUGGING, __func__, " <--");
}

DhKeyPair::~DhKeyPair()
{
    delete publicKey_;
    delete privateKey_;
}

DhKeyPair& DhKeyPair::operator=(const DhKeyPair& otherPair) {
    if (this == &otherPair) {
        LOGGER(DEBUGGING, __func__, " <--");
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
    LOGGER(DEBUGGING, __func__, " <--");
    return *this;
}
