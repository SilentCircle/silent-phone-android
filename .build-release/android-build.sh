#!/bin/bash

# This script expects the following environment variables (values can differ):
#
# ANDROID_ADT=/opt/adt-bundle-linux-x86_64-20130522
# ANDROID_ANT=/opt/adt-bundle-linux-x86_64-20130522/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant
# ANDROID_NDK=/opt/android-ndk-r8e
# ANDROID_SDK=/opt/android-sdk-linux
#
# SRC_ROOT=$HOME
# AUTOMATED_BUILD=1

# derived locations
#
SCANDROID_ROOT=$SRC_ROOT

# cleanup 
#
rm -rf $SCANDROID_ROOT/obj
rm -rf $SCANDROID_ROOT/libs/armeabi

# ndk jni builds
#
pushd $SCANDROID_ROOT
ndk-build
# ndk-build -d -B V=1 NDK_LOG=1 
popd

# android build 
# 
pushd $SCANDROID_ROOT

echo "sdk.dir=$ANDROID_SDK" > local.properties
echo "ndk.dir=$ANDROID_NDK" >> local.properties

android update project --target android-17 --name ActionBarSherlock --path support/ActionBarSherlock/library

$ANDROID_ANT \
-Dkey.store=$SCANDROID_ROOT/.build-release/test-debug.keystore \
-Dkey.store.password=android \
-Dkey.alias=androiddebugkey  \
-Dkey.alias.password=android \
clean release

popd

