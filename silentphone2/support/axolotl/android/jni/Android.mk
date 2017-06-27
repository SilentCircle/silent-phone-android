# Copyright 2016 Silent Circle, LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# APP_PROJECT_PATH := /path/to/project
#LOCAL_PATH := $(call my-dir)

LOCAL_PATH := @CMAKE_SOURCE_DIR@

# Setup to include the pre-built static protocol buffer library
include $(CLEAR_VARS)
LOCAL_MODULE := protobuf
LOCAL_SRC_FILES := $(LOCAL_PATH)/protobuf/android/obj/local/$(TARGET_ARCH_ABI)/libprotobuf-cpp-lite.a
include $(PREBUILT_STATIC_LIBRARY)

# compile the ZINA and support modules, build the lib
include $(CLEAR_VARS)

LOCAL_CPP_FEATURES := @local_cpp_features@

# include paths for ZINA, protocol buffer and sqlite3 modules
LOCAL_C_INCLUDES := $(LOCAL_PATH) @ZRTP_BASE_DIR@ @ZRTP_BASE_DIR@/zrtp $(LOCAL_PATH)/protobuf/src $(LOCAL_PATH)/android/jni

# includes for ed25516 curve signature/verify math
# LOCAL_C_INCLUDES += $(LOCAL_PATH)/ratchet/crypto/ed25519 $(LOCAL_PATH)/ratchet/crypto/ed25519/additions $(LOCAL_PATH)/ratchet/crypto/ed25519/nacl_includes

# LOCAL_SRC_FILES := clients/tivi/android/jni/sqlite3/sqlite3.c
# LOCAL_SRC_FILES += buildinfo_$(TARGET_ARCH_ABI).c
LOCAL_SRC_FILES := @zina_src_spc@
LOCAL_SRC_FILES += @crypto_src_spc@
LOCAL_SRC_FILES += @attachment_src_spc@
# LOCAL_SRC_FILES += $(LOCAL_PATH)/interfaceUi/java/JavaNativeImpl.cpp

LOCAL_LDLIBS := -llog
LOCAL_MODULE := zina

LOCAL_CPP_EXTENSION := .cpp .cc
LOCAL_STATIC_LIBRARIES := protobuf

# For this Android build we can set the visibility to hidden. Access to functions is only via
# exported JNI functions.
LOCAL_CFLAGS := @EMBEDDED@ @LOG_MAX_LEVEL@ @NO_VISIBILITY@ -DSQLITE_HAS_CODEC -DSQL_CIPHER -DANDROID_NDK
include $(BUILD_@LIBRARY_BUILD_TYPE@_LIBRARY)

