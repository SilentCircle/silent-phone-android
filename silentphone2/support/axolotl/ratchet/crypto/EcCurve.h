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
#ifndef ECCURVE_H
#define ECCURVE_H

/**
 * @file EcCurve.h
 * @brief Functions to perform key agreemtn, sign, verify and encode/decode
 * @ingroup Zina
 * @{
 * 
 */

#include "DhKeyPair.h"
#include "DhPrivateKey.h"
#include "DhPublicKey.h"
#include "Ec255PrivateKey.h"

#ifdef __cplusplus
extern "C"
{
int curve25519_donna(uint8_t *, const uint8_t *, const uint8_t *);

/* returns 0 on success */
int curve25519_sign(unsigned char* signature_out, /* 64 bytes */
                     const unsigned char* curve25519_privkey, /* 32 bytes */
                     const unsigned char* msg, const unsigned long msg_len,
                     const unsigned char* random); /* 64 bytes */

/* returns 0 on success */
int curve25519_verify(const unsigned char* signature, /* 64 bytes */
                      const unsigned char* curve25519_pubkey, /* 32 bytes */
                      const unsigned char* msg, const unsigned long msg_len);

}
#endif

namespace zina {
class EcCurve
{
public:
    static KeyPairUnique generateKeyPair(int32_t curveType);

    /**
     * @brief Computes the key agreement value.
     *
     * Takes the public EC point of the other party and applies the EC DH algorithm
     * to compute the agreed value. The keys must use the same curve type. The length
     * of the output buffer must be >= the curve's requirements. See @c EcCurveTypes.h
     * and the specific key classes.
     * 
     * The output buffer contains the agreement starting at index 0, up to length-1 defined
     * be the curve, for example a curve25519 agreement: 0-31
     *
     * @param publicKey is the other party's public point.
     * @param privateKey is the own secret random number.
     * @param agreement the functions writes the computed agreed value in this parameter.
     * @param length Length of agreement buffer, must be >= the agreement of the curve.
     * @return length of the computed agreement, < 0 on error, no agreement computed
     *
     */
    static int32_t calculateAgreement(const DhPublicKey& publicKey, const DhPrivateKey& privateKey, uint8_t* agreement, size_t length);

//     /**
//      * @brief Verifies a message signature
//      * 
//      * @param signingKey The public part of the EC signing key
//      * @param message The message to verfiy
//      * @param msgLength Length of the message in bytes
//      * @param signature the received signature
//      * @param signLengt length of the signature buffer
//      * @return @c true if signature is OK, @c false otherwise
//      */
//     static bool verifySignature(const DhPublicKey& signingKey, const uint8_t* message, size_t msgLength, const uint8_t* signature, size_t signLength);
// 
//     /**
//      * @brief Computes a message a signature
//      * 
//      * @param signingKey The private part of the EC signing key
//      * @param message The message to sign, must no exceed the maximum size defined for the signature algorithm
//      * @param msgLength Length of the message in bytes
//      * @param signature the computed signature
//      * @param signLengt length of the signature buffer
//      * @return @c SUCCESS or an error code
//      */
//     static int32_t calculateSignature(const DhPrivateKey& signingKey, const uint8_t* message, size_t msgLength, uint8_t* signature, size_t signLength);

    /**
     * @brief Takes a serialized presentation of a public key and returns a public key object.
     * 
     * The functions dervies the actual type of the public key from the serialized data. The first
     * byte contains a type specifier.
     * 
     * @param bytes the serialized bytes of the public key
     * @return The public key object or @c NULL if serialized data is wrong.
     */
    static PublicKeyUnique decodePoint(const uint8_t* bytes);

    static PrivateKeyUnique decodePrivatePoint(const uint8_t* bytes, size_t length,
                                                         int32_t type = EcCurveTypes::Curve25519) {
        return PrivateKeyUnique(new Ec255PrivateKey(bytes));
    }

    static PrivateKeyUnique decodePrivatePoint(const std::string& data, int32_t type = EcCurveTypes::Curve25519) {
        return decodePrivatePoint((const uint8_t*)data.data(), data.size(), type);
    }

};
}  // namespace
/**
 * @}
 */

#endif // ECCURVE_H
