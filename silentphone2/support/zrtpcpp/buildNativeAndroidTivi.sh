#!/usr/bin/env bash

# This script builds the specific variant of zrtpcpp as used by Silent Phone
# on Android. The script always runs on a clean build directory and copies
# the resulting static library to the SPA jni directory.
# The script requires some environment variables which are either set by the
# build system (Jenkins for example) or by a shell script that call this script.

if [ ! -d "${WORKSPACE}/silentphone2" ]; then
    echo '***** Variable WORKSPACE does not point to correct directory *****'
    exit 1
fi

if [ "x$ANDROID_NDK" = "x" ]; then
    echo '***** Variable ANDROID_NDK not set *****'
    exit 1
fi

#if [ "x$SC_BUILD_TYPE" = "xDEVELOP" ]; then
#    BUILD_TYPE=Debug
#    echo "*** building develop configuration"
#else
#   BUILD_TYPE="Release"
#   echo "*** building release configuration"
#fi

# remove old build dir and files that may hang around after an unsuccessful build
rm -rf buildTiviAndroid
rm -f buildinfo_*.c

mkdir buildTiviAndroid
pushd buildTiviAndroid

cmake -DTIVI=true -DBUILD_STATIC=true -DAXO=true -DANDROID=true ..  # -DCMAKE_BUILD_TYPE=$BUILD_TYPE ..

pushd clients/tivi/android

if ndk-build; then
    echo "ZRTPCPP Android build OK."
else
    echo "ZRTPCPP Android build failed!"
    exit 1
fi

cp obj/local/armeabi-v7a/libzrtpcpp.a ${WORKSPACE}/silentphone2/jni/armeabi-v7a/

popd
popd

# remove build dir and generated temporary files
rm -rf buildTiviAndroid
rm buildinfo_*.c

exit 0
