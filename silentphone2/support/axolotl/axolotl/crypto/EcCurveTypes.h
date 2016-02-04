#ifndef ECCURVETYPES_H
#define ECCURVETYPES_H

/**
 * @file EcCurveTypes.h
 * @brief Global definitions for supported EC curves
 * @ingroup Axolotl++
 * @{
 * 
 */

#include <stdint.h>

namespace axolotl {
class EcCurveTypes {
public:
    static const int32_t Curve25519 = 1;
    static const int32_t Curve25519Basepoint = 9;
    static const int32_t Curve25519KeyLength = 32;
};
} // namespace

/**
 * @}
 */

#endif // ECPUBLICKEY_H
