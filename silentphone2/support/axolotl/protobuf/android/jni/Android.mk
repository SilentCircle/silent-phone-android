# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# reference is: 
#   https://android.googlesource.com/platform/external/protobuf/
#

LOCAL_PATH := $(call my-dir)
PROTO_SRC_PATH := ../../src/google/protobuf
TEST_SRC_PATH  := ../testing

IGNORED_WARNINGS := -Wno-sign-compare -Wno-unused-parameter -Wno-sign-promo

CC_LITE_SRC_FILES := \
    $(PROTO_SRC_PATH)/stubs/common.cc                              \
    $(PROTO_SRC_PATH)/stubs/once.cc                                \
    $(PROTO_SRC_PATH)/extension_set.cc                             \
    $(PROTO_SRC_PATH)/generated_message_util.cc                    \
    $(PROTO_SRC_PATH)/message_lite.cc                              \
    $(PROTO_SRC_PATH)/repeated_field.cc                            \
    $(PROTO_SRC_PATH)/wire_format_lite.cc                          \
    $(PROTO_SRC_PATH)/io/coded_stream.cc                           \
    $(PROTO_SRC_PATH)/io/zero_copy_stream.cc                       \
    $(PROTO_SRC_PATH)/io/zero_copy_stream_impl_lite.cc

#     $(PROTO_SRC_PATH)/stubs/hash.cc  is an empty source file in reference

TEST_SRC_FILES := \
    $(TEST_SRC_PATH)/basicTest.cpp \
    $(TEST_SRC_PATH)/testmessage.pb.cc

# C++ lite library
# =======================================================
include $(CLEAR_VARS)

LOCAL_MODULE := protobuf-cpp-lite

LOCAL_CPP_EXTENSION := .cc

LOCAL_SRC_FILES := $(CC_LITE_SRC_FILES)

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../../android \
    $(LOCAL_PATH)/../../src

LOCAL_CFLAGS := -DGOOGLE_PROTOBUF_NO_RTTI $(IGNORED_WARNINGS)

include $(BUILD_STATIC_LIBRARY)


# axo++ library
# ========================================================
include $(CLEAR_VARS)

LOCAL_MODULE := axolotl

LOCAL_CPP_EXTENSION := .cc .cpp
LOCAL_STATIC_LIBRARIES := protobuf-cpp-lite
LOCAL_SRC_FILES := $(TEST_SRC_FILES)

# points to the local protobuffer source directory
LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/../../src
LOCAL_LDLIBS := -llog

LOCAL_CFLAGS := -DANDROID_NDK

include $(BUILD_SHARED_LIBRARY)
