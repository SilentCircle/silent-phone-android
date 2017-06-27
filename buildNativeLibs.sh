#!/bin/bash

# git submodule update --init

set -x

echo "To get the latest version of the modules you may run:"
echo "  git submodule update --recursive"
echo "and check the status with: "
echo "  git submodule status"

echo "\nIf no argument given the scripts builds release version of the libs."
echo "To build develop versions add 'DEVELOP' as argument."

export WORKSPACE=`pwd`          # simulate Jenkins setup
export SC_BUILD_TYPE="$1"
export AUTOMATED_BUILD=1
export JNI_ROOT=$WORKSPACE/silentphone2/support

# build static and shared libs and dependencies, copy resulting libs to
# correct place (silentphone2/jni/armeabi-v7a), then run top level ndk-build
pushd silentphone2/support/axolotl
if ! sh -x axolotl-build.sh; then
    echo "ZINA static library build failed"
    exit 1
fi
popd

pushd silentphone2/support/zrtpcpp
if ! bash -x buildNativeAndroidTivi.sh; then
    echo "ZRTP static library build failed"
    exit 1
fi
popd

pushd silentphone2/support/aec
if ! bash -x aecBuild.sh; then
    echo "WebRTC AEC shared library build failed"
    exit 1
fi
popd

pushd silentphone2/support/silentphone/codecs/vTiVi
if ! bash -x tinaBuild.sh; then
    echo "Tina codec shared library build failed"
    exit 1
fi
popd

# build the native shared lib which includes the static libs.
pushd silentphone2
if ! ndk-build; then
    echo "Build of native silentphone library failed"
    exit 1
fi
popd
exit 0
