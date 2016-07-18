#!/bin/bash

# symbols set by build host:
# 
# export JAVA_JDK=/opt/jdk1.7.0_45/bin
#
# export ANDROID_ADT=/opt/adt-bundle-linux-x86_64
# export ANDROID_ANT=$ANDROID_ADT/eclipse/plugins/org.apache.ant_1.8.3.v20120321-1730/bin/ant
# export ANDROID_SDK=$ANDROID_ADT/sdk
# export ANDROID_NDK=/opt/android-ndk-r9c
#
# export PATH=$JAVA_JDK:$ANDROID_NDK:$ANDROID_ADT:$ANDROID_ANT:$ANDROID_SDK/tools:$PATH
# 
# export GRADLE_USER_HOME="$WORKSPACE"
# export SRC_ROOT=$WORKSPACE
# export AUTOMATED_BUILD=1
# export JNI_ROOT=$WORKSPACE/support
#
# export BUILD_NUMBER_PREFIX="alpha-"
# which varies depending on build dev-, alpha-, 17-Apr-2014: <- last is release 

set -e

SC_BUILD_TYPE="$1"

BUILT_APK_ROOT="silentphone2/build/outputs/apk/"
ARTIFACT_APK="silentphone.apk"

if [[ "$SC_BUILD_TYPE" = "DEVELOP" ]];
then
   BUILD_GRADLE_TASK="assembleDevelop"
   BUILD_APK_NAME="silentphone2-normal-develop.apk"
   echo "*** building develop configuration"
else
   SC_BUILD_TYPE="RELEASE"
   BUILD_GRADLE_TASK="assembleRelease"
   BUILD_APK_NAME="silentphone2-normal-release.apk"
   echo "*** building release configuration"
fi


git submodule init
git submodule update --recursive
git submodule status

echo "sdk.dir=$ANDROID_SDK" > local.properties
echo "ndk.dir=$ANDROID_NDK" >> local.properties

# build static Axolotl lib and dependencies, copy resulting libs to
# correct place (silentphone2/jni/armeabi-v7a), then run ndk-build
pushd silentphone2/support/axolotl
    sh -x axolotl-build.sh
popd

pushd silentphone2

if [ ! -h local.properties ]; then
  ln -s ../local.properties
fi

# ndk jni builds
# ndk-build -d -B V=1 NDK_LOG=1
ndk-build

popd


# network proxy so gradle can update gradle toolset
echo "systemProp.http.proxyHost=hlp01-fsyyz.hlp.silentcircle.net"   > gradle.properties
echo "systemProp.http.proxyPort=3128"                              >> gradle.properties
echo "systemProp.https.proxyHost=hlp01-fsyyz.hlp.silentcircle.net" >> gradle.properties
echo "systemProp.https.proxyPort=3128"                             >> gradle.properties

echo "build_environment=silentcircle.com"                 >> gradle.properties
echo "build_version=$BUILD_NUMBER_PREFIX$BUILD_NUMBER"    >> gradle.properties
echo "build_version_numeric=$BUILD_NUMBER"                >> gradle.properties
echo "build_commit=$(git log -n 1 --pretty=format:'%h')"  >> gradle.properties
echo "build_date=$BUILD_ID"                               >> gradle.properties
echo "build_debug=true"                                   >> gradle.properties
echo "build_partners=Vertu"                               >> gradle.properties

## Next is required for gradle plugin 1.3.0 to support the old way to
## handle NDK builds. Maybe we can update/remove it once gradle fulls supports NDK
echo "android.useDeprecatedNdk=true"                      >> gradle.properties

# for more information on what gradlew is doing consider using --info
# ./gradlew --info clean $BUILD_GRADLE_TASK
./gradlew clean $BUILD_GRADLE_TASK

# build-header.txt is sent to the distribution webserver with the apk
# WEB_REPO is a URL prepended to the commit id resulting in a web link to the commit
#
git log -n 1 --pretty=tformat:$"%cD :: <a href=\"$WEB_REPO/%H\">%h</a>%+cn<br />build #$BUILD_NUMBER on $BUILD_ID%+s" > build-header.txt


SymlinkAPK() {
    local RELATIVE_DIR=$1
    local RELATIVE_APK_PATH=$1"/"$ARTIFACT_APK
    local BUILT_APK_NAME=$2

    mkdir -p "$RELATIVE_DIR"
    if [ -L "$RELATIVE_APK_PATH" ]
    then
      rm -f "$RELATIVE_APK_PATH"
    fi
    ln -s "../$BUILT_APK_ROOT$BUILT_APK_NAME" "$RELATIVE_APK_PATH"

    echo "linked $BUILT_APK_NAME to $RELATIVE_APK_PATH"
    openssl sha1 "$RELATIVE_APK_PATH"
}


SymlinkAPK "bin"   $BUILD_APK_NAME

# Jenkins build variable - has format of timestamp
echo $BUILD_ID > ../build-id.txt
