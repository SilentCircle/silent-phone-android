# Symbols expected by script
#
export ANDROID_ADT=$HOME/opt/adt-bundle-linux-x86_64-20130729
export ANDROID_ANT=$ANDROID_ADT/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant

export ANDROID_SDK=$ANDROID_ADT/sdk
export ANDROID_NDK=$HOME/opt/android-ndk-r8e

export JAVA_JDK=$HOME/opt/jdk1.6.0_45/bin

export WORKSPACE=`pwd`

export PATH=$ANDROID_NDK:$ANDROID_ADT:$ANDROID_ANT:$ANDROID_SDK/tools:$JAVA_JDK:$PATH

export SRC_ROOT=$WORKSPACE

export AUTOMATED_BUILD=1
export JNI_ROOT=$WORKSPACE/support

export BUILD_NUMBER=1
export BUILD_ID="`date --rfc-3339=seconds`"


rm -f silentphone.apk
bash -x .build-release/android-build.sh

