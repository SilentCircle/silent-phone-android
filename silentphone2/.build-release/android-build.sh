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

die() {
    echo $* >&2
    exit 1
}

assert_nonempty_var() {
    local var_name="$1"; shift
    [[ -n "$(eval echo \$$var_name)" ]] || die "$var_name must not be empty"
}

iso8601_timestamp() {
    date -u +'%FT%TZ'
}

set_gradle_properties() {
    local build_number="$1"; shift
    local build_id="$1"; shift
    local build_number_prefix="$1"; shift

    cat <<end_set_gradle_properties > gradle.properties
org.gradle.jvmargs=-Xmx3584M
build_environment=silentcircle.com
build_version=$build_number_prefix$build_number
build_version_numeric=$build_number
build_commit=$(git log -n 1 --pretty=format:'%h')
build_date=$build_id
build_debug=true
build_partners=Vertu

## Next is required for gradle plugin 1.3.0 to support the old way to handle
## NDK builds. Maybe we can update/remove it once gradle fully supports NDK
android.useDeprecatedNdk=true
end_set_gradle_properties
}

export SC_BUILD_TYPE="$1"

BUILT_APK_ROOT="silentphone2/build/outputs/apk/"
ARTIFACT_APK="silentphone.apk"

if [[ "$SC_BUILD_TYPE" = "DEVELOP" ]];
then
   BUILD_GRADLE_TASK="assembleNormalDevelop"
   BUILD_APK_NAME="silentphone2-normal-develop.apk"
   echo "*** building develop configuration"
else
   export SC_BUILD_TYPE="RELEASE"
   BUILD_GRADLE_TASK="assembleRelease"
   BUILD_APK_NAME="silentphone2-normal-release.apk"
   echo "*** building release configuration"
fi

# Show environment for debug purposes
env

git submodule init
git submodule update --recursive
git submodule status

cat <<END_LOCAL_PROPERTIES > local.properties
sdk.dir=$ANDROID_SDK
ndk.dir=$ANDROID_NDK
END_LOCAL_PROPERTIES

echo "local.properties:"
cat local.properties

# build static and shared libs and dependencies, copy resulting libs to
# correct place (silentphone2/jni/armeabi-v7a), then run top level ndk-build
pushd silentphone2/support/axolotl
chmod +x ./axolotl-build.sh
if ! ./axolotl-build.sh; then
    echo "ZINA static library build failed"
    exit 1
fi
popd

pushd silentphone2/support/zrtpcpp
chmod +x ./buildNativeAndroidTivi.sh
if ! ./buildNativeAndroidTivi.sh; then
    echo "ZRTP static library build failed"
    exit 1
fi
popd

pushd silentphone2/support/aec
chmod +x ./aecBuild.sh
if ! ./aecBuild.sh; then
    echo "WebRTC AEC shared library build failed"
    exit 1
fi
popd

pushd silentphone2/support/silentphone/codecs/vTiVi
chmod +x ./tinaBuild.sh
if ! ./tinaBuild.sh; then
    echo "Tina codec shared library build failed"
    exit 1
fi
popd

pushd silentphone2

if [ ! -h local.properties ]; then
  ln -s ../local.properties
fi

# try to make a clean build for silentphone submodule
ndk-build clean

# ndk jni builds
# ndk-build -d -B V=1 NDK_LOG=1
if ! ndk-build NDK_DEBUG=0; then
    echo "Build of native silentphone library failed"
    exit 1
fi

popd

## See which build system we are using
## and set env vars accordingly
if [ -n "$AUTOMATED_BUILD" ]; then
    if [ -n "$GITLAB_CI" ]; then
        # BUILD_NUMBER_PREFIX is (should be) set by gitlab-ci.yml
        # It is allowed to be empty.
        BUILD_NUMBER="$CI_JOB_ID"
        BUILD_ID=$(iso8601_timestamp)
    elif [ -n "$JENKINS_HOME" ]; then
        # The required build variables should already be set
        assert_nonempty_var BUILD_NUMBER
        assert_nonempty_var BUILD_ID
    else
        echo "AUTOMATED_BUILD set but I don't know which CI system this is."
        exit 1
    fi
else # Manual build
    BUILD_NUMBER_PREFIX='manual-'
    BUILD_NUMBER=$$ # This should be monotonically increasing, but not worth it
    BUILD_ID=$(iso8601_timestamp)
fi

set_gradle_properties "$BUILD_NUMBER" "$BUILD_ID" "$BUILD_NUMBER_PREFIX"
echo "gradle.properties:"
cat gradle.properties

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

# CI build variable - has format of timestamp
echo $BUILD_ID > ${WORKSPACE}/build-id.txt
