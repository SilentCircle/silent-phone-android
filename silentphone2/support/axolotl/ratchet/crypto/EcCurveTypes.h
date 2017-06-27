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
#ifndef ECCURVETYPES_H
#define ECCURVETYPES_H

/**
 * @file EcCurveTypes.h
 * @brief Global definitions for supported EC curves
 * @ingroup Zina
 * @{
 * 
 */

#include <stdint.h>

namespace zina {
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
