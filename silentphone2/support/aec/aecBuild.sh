#!/usr/bin/env bash

set -x

if [ ! -d "${WORKSPACE}/silentphone2" ]; then
    echo '***** Variable WORKSPACE does not point to correct directory *****'
    exit 1
fi

if [ "x$ANDROID_NDK" = "x" ]; then
    echo '***** Variable ANDROID_NDK not set *****'
    exit 1
fi

pushd android

if ndk-build; then
    echo "WebRTC AEC build OK"
else
    exit 1
fi

# now copy the created AEC shared lib to silentphone2 JNI directory
cp libs/armeabi-v7a/libaec.so ${WORKSPACE}/silentphone2/jni/armeabi-v7a/

popd
exit 0
