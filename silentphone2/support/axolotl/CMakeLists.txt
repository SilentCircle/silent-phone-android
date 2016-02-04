#
# Copyright (c) 2015 Slient Circle LLC.  All rights reserved.
#
# @author Werner Dittmann <Werner.Dittmann@t-online.de>
#

cmake_minimum_required(VERSION 3.0)

PROJECT(libaxolotl++)

SET(CPACK_PACKAGE_VERSION_MAJOR 1)
SET(CPACK_PACKAGE_VERSION_MINOR 0)
SET(CPACK_PACKAGE_VERSION_PATCH 0)

set (VERSION 1.0.0)
set (SOVERSION 1)

### Configuration section
# Where to find the modules that we share with ZRTP, i.e. the ZRTP root directory
set (ZRTP_BASE_DIR /home/werner/silentC/zrtpcpp)
# set (ZRTP_BASE_DIR /Users/werner/AndroidStudioProjects/spa2/silentphone2/support/zrtpcpp)

option(STANDALONE "Build standlone shared library, otherwise a static lib without ZRTP crypto modules." OFF)
option(UNITTESTS "Build unit tests, implies STANDALONE true." OFF)

set(LIBRARY_BUILD_TYPE SHARED)
set (CMAKE_POSITION_INDEPENDENT_CODE TRUE)

if (UNITTESTS)
    set(STANDALONE true)
endif()

if (NOT STANDALONE)
    set(LIBRARY_BUILD_TYPE STATIC)
endif()

set(axoLibName axolotl++)
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -g")

### No more configuration below this line (usually ;-) )
if (EXISTS "${ZRTP_BASE_DIR}/zrtp/crypto/hmac256.cpp")
    message(STATUS "Using ${ZRTP_BASE_DIR} for common modules")
else()
    message(FATAL_ERROR "Cannot locate ${ZRTP_BASE_DIR}. Please set correct path")
endif()

# use protobuf_lite.pc
# include most of the fine stuff we need
include(FindPkgConfig)
include(CheckIncludeFiles)

pkg_check_modules(PROTO protobuf-lite>=2.6.1)
if (PROTO_FOUND)
    include_directories(${PROTO_INCLUDE_DIRS})
    set(LIBS ${LIBS} ${PROTO_LDFLAGS})
else()
    message(FATAL_ERROR "ProtocolBuffer library not found")
endif()

pkg_check_modules(SQLCIPHER sqlcipher>=3.7)
if (SQLCIPHER_FOUND)
    set(CMAKE_REQUIRED_INCLUDES ${CMAKE_REQUIRED_INCLUDES} ${SQLCIPHER_INCLUDE_DIRS}) 
    check_include_files(sqlite3.h HAVE_SQLCIPHER_H)
    set(LIBS ${LIBS} -lsqlcipher)
    MESSAGE(STATUS "Using SQlCipher based ZRTP cache")
    add_definitions(-DSQL_CIPHER -DSQLITE_HAS_CODEC)
else()
    message(FATAL_ERROR "SQLCipher library not found")
endif()

# pkg_check_modules(SQLITE3 sqlite3>=3.7)
# if (SQLITE3_FOUND)
# #    check_include_files(sqlite3.h HAVE_SQLITE_H)
#     set(LIBS ${LIBS} -lsqlite3)
#     MESSAGE(STATUS "Using SQLite based ZRTP cache")
# else()
#     message(FATAL_ERROR "SQLite3 library not found")
# endif()

if (UNITTESTS)
    add_definitions(-DUNITTESTS)
else()
    add_definitions(-DEMBEDDED)
endif()

add_subdirectory(axolotl/crypto)

set (protocol_src
    axolotl/AxoZrtpConnector.cpp
    axolotl/AxoPreKeyConnector.cpp
    axolotl/ratchet/AxoRatchet.cpp
    axolotl/state/AxoConversation.cpp
)
set (interface_src 
    interfaceApp/AppInterfaceImpl.cpp
    interfaceApp/MessageEnvelope.pb.cc
    interfaceApp/java/JavaNativeImpl.cpp
    interfaceTransport/sip/SipTransport.cpp
)

set (storage_src
    storage/sqlite/SQLiteStoreConv.cpp
)

set (key_mngmnt_src
    keymanagment/PreKeys.cpp
)

set (provisioning_src
    provisioning/ScProvisioning.cpp
)

set (util_src
    util/cJSON.c
    util/b64helper.cpp
    util/UUID.cpp
)

set (app_repo_src
    appRepository/AppRepository.cpp
)

if (STANDALONE)
    set (external_src
        ${ZRTP_BASE_DIR}/common/osSpecifics.c
    )
endif()

add_subdirectory(attachments)

set (axo_src
    ${protocol_src}
    ${storage_src}
    ${interface_src}
    ${key_mngmnt_src}
    ${provisioning_src}
    ${util_src}
    ${external_src}
    ${app_repo_src}
)

add_library(${axoLibName} ${LIBRARY_BUILD_TYPE} ${axo_src} $<TARGET_OBJECTS:crypto_lib> $<TARGET_OBJECTS:attachment_lib>)
set_target_properties(${axoLibName} PROPERTIES VERSION ${VERSION} SOVERSION ${SOVERSION})
TARGET_INCLUDE_DIRECTORIES(${axoLibName} PUBLIC ${ZRTP_BASE_DIR} ${ZRTP_BASE_DIR}/zrtp)
target_link_libraries(${axoLibName} ${LIBS})

if (UNITTESTS)
    add_subdirectory(gtest-1.7.0)
    add_subdirectory(unittests)
endif()


add_custom_target(protobuf COMMAND ${CMAKE_SOURCE_DIR}/protobuf/android/runndk.sh WORKING_DIRECTORY ${CMAKE_SOURCE_DIR}/protobuf/android)

add_custom_target(android ${CMAKE_SOURCE_DIR}/android/runndk.sh WORKING_DIRECTORY ${CMAKE_BINARY_DIR}/android)
add_dependencies(android ${axoLibName} protobuf)

# add_subdirectory(protobuf/android)
#
# Prepare Android's Android.mk and Application.mk files
string(REPLACE ";" " " crypto_src_spc "${crypto_src_global}")
string(REPLACE ";" " " attachment_src_spc "${attchment_src_global}")
if (STANDALONE)
    set (axo_src "${axo_src}" ${CMAKE_SOURCE_DIR}/android/jni/sqlite3/sqlite3.c)
else()
    set (EMBEDDED -DEMBEDDED)
endif()
if (UNITTESTS)
    set (EMBEDDED -DUNITTESTS)
endif()

string(REPLACE ";" " " axo_src_spc "${axo_src}")
set(local_cpp_features "exceptions")

configure_file(${CMAKE_SOURCE_DIR}/android/jni/Android.mk
               ${CMAKE_BINARY_DIR}/android/jni/Android.mk @ONLY)
configure_file(${CMAKE_SOURCE_DIR}/android/jni/Application.mk
               ${CMAKE_BINARY_DIR}/android/jni/Application.mk @ONLY)

#add_subdirectory(axolotl/android)
