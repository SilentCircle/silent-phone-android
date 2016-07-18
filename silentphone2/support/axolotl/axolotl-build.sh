#!/bin/sh

if [ ! -d "${WORKSPACE}/silentphone2" ]; then
    echo '***** Variable WORKSPACE does not point to correct directory *****'
    exit 1
fi

if [ "x$ANDROID_NDK" = "x" ]; then
    echo '***** Variable ANDROID_NDK not set *****'
    exit 1
fi

if [ "x$SC_BUILD_TYPE" = "xDEVELOP" ]; then
    BUILD_TYPE=Debug
    echo "*** building develop configuration"
else
   BUILD_TYPE="Release"
   echo "*** building release configuration"
fi

rm -rf buildAxoAndroid
mkdir buildAxoAndroid
cd buildAxoAndroid

echo "###### If AUTOMATED_BUILD is set then ignore 2 cmake errors regarding OBJECT library #####"
cmake -DANDROID=ON -DCMAKE_BUILD_TYPE=$BUILD_TYPE ..

if make android; then
    echo "Android build OK"
else
    exit 1
fi

# now copy the created static libs to silentphone2 JNI directory
cp android/obj/local/armeabi-v7a/libaxolotl++.a ${WORKSPACE}/silentphone2/jni/armeabi-v7a/

# Back to main Axolotl directory
cd ..
cp protobuf/android/obj/local/armeabi-v7a/libprotobuf-cpp-lite.a ${WORKSPACE}/silentphone2/jni/armeabi-v7a/

# cleanup build directory
rm -rf buildAxoAndroid
exit 0
