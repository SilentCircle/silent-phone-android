#include "Ec255PrivateKey.h"

#include <string.h>

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

using namespace axolotl;
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
