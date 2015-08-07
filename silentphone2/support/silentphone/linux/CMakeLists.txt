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

pkg_check_modules(SQLITE3 sqlite3>=3.7)
if (SQLITE3_FOUND)
    check_include_files(sqlite3.h HAVE_SQLITE_H)
    set(LIBS ${LIBS} -lsqlite3)
else()
    message(FATAL_ERROR "SQLite3 library not found")
endif()

pkg_check_modules(PULSE libpulse-simple>=4.0)
if (PULSE_FOUND)
    set(LIBS ${LIBS} ${PULSE_LDFLAGS})
else()
    message(FATAL_ERROR "PulseAudio library not found")
endif()

add_definitions(-g -O2 -fno-strict-aliasing)
if(CMAKE_COMPILER_IS_GNUCXX)
    add_definitions(-Wno-long-long -Wno-char-subscripts)
    add_definitions(-fpermissive)  ############### Don't use this for production code !!!!
#   add_definitions(-Wall -ansi -pedantic)
endif()

set(linuxObjs
    ${encryptDir} ${os} ${baseClasses} ${sipParser} ${tiviEngine} ${rtp} ${sdp} ${xml} ${codecsG711}
    ${codecsG722} ${codecsG729float} ${codecsGsm} ${vad} ${codecsJpeg} ${aec}
    ${tiviBase}/utils/utils.cpp
    ${tiviBase}/video/CTRTPVideoPlayer.cpp
    ${tiviBase}/audio/audio_in_out_linux.cpp
    ${tiviBase}/tiviandroid/t_a_main.cpp
    ${tiviBase}/tiviandroid/test_engine.cpp)


add_executable(tivilinux ${linuxObjs})
target_link_libraries(tivilinux ${zrtpDir}/build/clients/tivi/libzrtptivi.so)
target_link_libraries(tivilinux ${polarDir}/library/libpolarssl.a)
target_link_libraries(tivilinux ${LIBS})
# SET_TARGET_PROPERTIES(tivilinux PROPERTIES LINK_FLAGS -m32)

