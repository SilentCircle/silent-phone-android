export JAVA_JDK=$HOME/opt/jdk1.7.0_51/bin
export ANDROID_NDK=$HOME/opt/android-ndk-r10e
export ANDROID_SDK=$HOME/opt/android-sdk-linux

export WORKSPACE=$HOME/SPa2

export PATH=$JAVA_JDK:$ANDROID_NDK:$PATH

export GRADLE_USER_HOME="$WORKSPACE"
export SRC_ROOT=$WORKSPACE
export AUTOMATED_BUILD=1
export JNI_ROOT=$WORKSPACE/silentphone2/support

export BUILD_NUMBER=1
export BUILD_NUMBER_PREFIX="open:"

# BUILD_ID The current build id, such as "2005-08-22_23-59-59" (YYYY-MM-DD_hh-mm-ss) 
export BUILD_ID=$(date +%Y-%m-%d_%H-%M-%S)

bash -x ./silentphone2/.build-release/android-build.sh RELEASE


cp silentphone2/build/outputs/apk/silentphone2-normal-release.apk silentphone.apk
