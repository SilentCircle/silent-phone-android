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
#include "Ec255PrivateKey.h"

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

using namespace zina;
Ec255PrivateKey::Ec255PrivateKey(const uint8_t* data)
{
    memcpy(keyData_, data, KEY_LENGTH);
}

Ec255PrivateKey::~Ec255PrivateKey()
{
    memset_volatile(keyData_, 0, KEY_LENGTH);
}

Ec255PrivateKey::Ec255PrivateKey(const Ec255PrivateKey& other)
{
    memcpy(keyData_, other.keyData_, KEY_LENGTH);
}


DhPrivateKey& Ec255PrivateKey::operator=( const DhPrivateKey& other )
{
    if (this != &other) {
        if (getType() == other.getType())
            memcpy(keyData_, other.privateData(), KEY_LENGTH);
        else
            memset_volatile(keyData_, 0, KEY_LENGTH);
    }
    return *this;
}

void Ec255PrivateKey::serialize(uint8_t* outBuffer) const {
    memcpy(outBuffer, keyData_, KEY_LENGTH);
}

bool Ec255PrivateKey::operator== (const Ec255PrivateKey& other) const
{
    if (this == &other)
        return true;
    if (getType() != other.getType())
        return false;
    int result = memcmp(keyData_, other.privateData(), KEY_LENGTH);
    return result == 0;
}
