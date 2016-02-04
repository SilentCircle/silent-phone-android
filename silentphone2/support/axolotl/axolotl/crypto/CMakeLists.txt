#
# Copyright (c) 2015 Slient Circle LLC.  All rights reserved.
#
# @author Werner Dittmann <Werner.Dittmann@t-online.de>
#

cmake_minimum_required(VERSION 3.0)

set (external_crypto_src
    ${ZRTP_BASE_DIR}/bnlib/ec/curve25519-donna.c
    ${ZRTP_BASE_DIR}/zrtp/crypto/sha2.c
    ${ZRTP_BASE_DIR}/zrtp/crypto/hmac256.cpp
    ${ZRTP_BASE_DIR}/zrtp/crypto/sha256.cpp
    ${ZRTP_BASE_DIR}/cryptcommon/aes_modes.c
    ${ZRTP_BASE_DIR}/cryptcommon/aescrypt.c
    ${ZRTP_BASE_DIR}/cryptcommon/aestab.c
    ${ZRTP_BASE_DIR}/cryptcommon/aeskey.c
    ${ZRTP_BASE_DIR}/cryptcommon/ZrtpRandom.cpp
    ${ZRTP_BASE_DIR}/common/MutexClass.cpp
)

set (hkdf_src
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/HKDF.cpp
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/Ec255PublicKey.cpp
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/Ec255PrivateKey.cpp
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/DhKeyPair.cpp
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/EcCurve.cpp
    ${CMAKE_SOURCE_DIR}/axolotl/crypto/AesCbc.cpp
)

set(crypto_src
    ${hkdf_src}
)
if (STANDALONE)
    set(crypto_src ${crypto_src} ${external_crypto_src})
endif()

set(crypto_src_global ${crypto_src} PARENT_SCOPE)

# crypto_src may be empty (no set) in case STANDALONE is false

add_library(crypto_lib OBJECT ${crypto_src})
set_property(TARGET crypto_lib PROPERTY POSITION_INDEPENDENT_CODE TRUE)
TARGET_INCLUDE_DIRECTORIES(crypto_lib PUBLIC ${ZRTP_BASE_DIR})
