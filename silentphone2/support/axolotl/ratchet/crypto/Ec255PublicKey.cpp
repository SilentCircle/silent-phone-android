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
#include <string.h>
#include "Ec255PublicKey.h"
#include <iostream>

using namespace std;

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

using namespace zina;

Ec255PublicKey::Ec255PublicKey(const uint8_t* data)
{
    memcpy(keyData_, data, KEY_LENGTH);
}

Ec255PublicKey::~Ec255PublicKey()
{
    memset_volatile(keyData_, 0, KEY_LENGTH);
}

Ec255PublicKey::Ec255PublicKey(const Ec255PublicKey& other)
{
    memcpy(keyData_, other.keyData_, KEY_LENGTH);
}


DhPublicKey&  Ec255PublicKey::operator=(const DhPublicKey& other)
{
    if (this != &other) {
        if (getType() == other.getType())
            memcpy(keyData_, other.getPublicKeyPointer(), KEY_LENGTH);
        else
            memset_volatile(keyData_, 0, KEY_LENGTH);
    }
    return *this;
}

void Ec255PublicKey::serialize(uint8_t* outBuffer) const
{
    *outBuffer = EcCurveTypes::Curve25519;
    memcpy(outBuffer+1, keyData_, KEY_LENGTH);
}

const std::string Ec255PublicKey::serialize() const
{
    uint8_t type = EcCurveTypes::Curve25519;
    return std::string().assign((const char*)&type, 1).append((const char*)keyData_, KEY_LENGTH);
}

bool Ec255PublicKey::operator== (const DhPublicKey& other) const
{
    if (this == &other)
        return true;
    if (getType() != other.getType())
        return false;
    int result = memcmp(keyData_, other.getPublicKeyPointer(), KEY_LENGTH);
    return result == 0;
}
