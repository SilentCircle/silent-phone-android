# Copyright (C) 2012 Werner Dittman
#
# Linux specific setup for Tivi.
#
# include the fine stuff we need
#
include(FindPkgConfig)
include(CheckLibraryExists)
include(CheckIncludeFiles)
include(CheckFunctionExists)

check_include_files(stdlib.h HAVE_STDLIB_H)
check_include_files(string.h HAVE_STRING_H)

# setup the Thread include and lib
find_package(Threads)
if(CMAKE_HAVE_PTHREAD_H)
    set(HAVE_PTHREAD_H TRUE)
endif()
set(LIBS ${LIBS} ${CMAKE_THREAD_LIBS_INIT})

pkg_check_modules(SQLITE3 sqlcipher>=3.7)
if (SQLITE3_FOUND)
    check_include_files(sqlite3.h HAVE_SQLITE_H)
    set(LIBS ${LIBS} -lsqlcipher)
else()
    message(FATAL_ERROR "SQLite3 library not found")
endif()

pkg_check_modules(PULSE libpulse-simple>=4.0)
if (PULSE_FOUND)
    set(LIBS ${LIBS} ${PULSE_LDFLAGS})
else()
    message(FATAL_ERROR "PulseAudio library not found")
endif()

pkg_check_modules(PROTO protobuf-lite>=2.6.1)
if (PROTO_FOUND)
    include_directories(${PROTO_INCLUDE_DIRS})
    set(LIBS ${LIBS} ${PROTO_LDFLAGS})
else()
    message(FATAL_ERROR "ProtocolBuffer library not found")
endif()

add_definitions(-g -O2 -fno-strict-aliasing)
add_definitions("-I${polarDir}/include -I${polarDir}/configs -DMBEDTLS_CONFIG_FILE='<config-sc.h>'")
MESSAGE(STATUS "-I${polarDir}/configs -DMBEDTLS_CONFIG_FILE='<config-sc.h>'")

#set(CMAKE_C_FLAGS ${CMAKE_C_FLAGS} -I${polarDir}/configs "-DMBEDTLS_CONFIG_FILE=<config_sc.h>")
#set(CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS} -I${polarDir}/configs "-DMBEDTLS_CONFIG_FILE='<config_sc.h>'")
if(CMAKE_COMPILER_IS_GNUCXX)
    add_definitions(-Wno-long-long -Wno-char-subscripts -std=c++11)
    add_definitions(-fpermissive)  ############### Don't use this for production code !!!!
#   add_definitions(-Wall -ansi -pedantic)
endif()

set(linuxObjs
    ${encryptDir} ${os} ${baseClasses} ${sipParser} ${tiviEngine} ${rtp} ${sdp} ${xml} ${codecsG711}
    ${codecsG722} ${codecsG729float} ${codecsGsm} ${vad} ${codecsJpeg} ${aec} ${tmr}
    ${tiviBase}/utils/utils.cpp
    ${tiviBase}/utils/CTCoutryCode.cpp
    ${tiviBase}/video/CTRTPVideoPlayer.cpp
    ${tiviBase}/audio/audio_in_out_linux.cpp
    ${tiviBase}/tiviandroid/t_a_main.cpp
    ${tiviBase}/tiviandroid/test_engine.cpp)


add_executable(tivilinux ${linuxObjs})
target_link_libraries(tivilinux ${zrtpDir}/build/clients/tivi/libzrtptivi.so)
target_link_libraries(tivilinux ${polarDir}/build/library/libmbedtls.a)
target_link_libraries(tivilinux ${polarDir}/build/library/libmbedx509.a)
target_link_libraries(tivilinux ${polarDir}/build/library/libmbedcrypto.a)
target_link_libraries(tivilinux ${axoDir}/buildStatic/libaxolotl++.a)
target_link_libraries(tivilinux ${LIBS})
# SET_TARGET_PROPERTIES(tivilinux PROPERTIES LINK_FLAGS -m32)

