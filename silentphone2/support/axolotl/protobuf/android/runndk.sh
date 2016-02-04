#! /bin/sh
if ndk-build; then
    cp obj/local/armeabi-v7a/libprotobuf-cpp-lite.a ../../android/jni/armeabi-v7a
else
    exit 1
fi
