#!/bin/bash

# git submodule update --init

export WORKSPACE=`pwd`          # simulate Jenkins setup
export SC_BUILD_TYPE="$1"
export AUTOMATED_BUILD=1
export JNI_ROOT=$WORKSPACE/silentphone2/support

# build static Axolotl lib and dependencies, copy resulting libs to
# correct place (silentphone2/jni/armeabi-v7a), then run ndk-build
pushd silentphone2/support/axolotl
    sh -x axolotl-build.sh
popd

# build the native shared lib which include tha static libs.
pushd silentphone2
ndk-build
popd

