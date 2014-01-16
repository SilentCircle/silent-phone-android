#!/bin/bash

# Symbols expected by script
#
# export ANDROID_ADT=/opt/adt-bundle-linux-x86_64
# export ANDROID_ANT=$ANDROID_ADT/eclipse/plugins/org.apache.ant_1.8.3.v20120321-1730/bin/ant
# export ANDROID_SDK=$ANDROID_ADT/sdk
# export ANDROID_NDK=/opt/android-ndk-r8e
# export JAVA_JDK=/opt/jdk1.6.0_45/bin
#
# export PATH=$JAVA_JDK:$ANDROID_NDK:$ANDROID_ADT:$ANDROID_ANT:$ANDROID_SDK/tools:$PATH
# export SRC_ROOT=$WORKSPACE
# export AUTOMATED_BUILD=1
# export JNI_ROOT=$WORKSPACE/scandroid/support
#
# export GRADLE_USER_HOME="$WORKSPACE/scandroid"
#

echo "sdk.dir=$ANDROID_SDK" > local.properties
echo "ndk.dir=$ANDROID_NDK" >> local.properties

# ndk jni builds
# ndk-build -d -B V=1 NDK_LOG=1
ndk-build

echo "build_environment=silentcircle.com"                 >> gradle.properties
echo "build_version=$BUILD_NUMBER"                        >> gradle.properties
echo "build_commit=$(git log -n 1 --pretty=format:'%h')"  >> gradle.properties
echo "build_date=$BUILD_ID"                               >> gradle.properties
echo "build_debug=true"                                   >> gradle.properties
echo "build_partners="                                    >> gradle.properties

#./gradlew tasks
#./gradlew assembleDebug
./gradlew clean assembleRelease

