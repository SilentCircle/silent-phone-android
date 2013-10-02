#
# Copyright (c) 2013 Slient Circle LLC.  All rights reserved.
#
# @author Werner Dittmann <Werner.Dittmann@t-online.de>
#

LOCAL_PATH := @CMAKE_SOURCE_DIR@
ROOT_SRC_PATH := $(LOCAL_PATH)

#
# Define and build the zrtpcpp static lib
#
include $(CLEAR_VARS)
LOCAL_MODULE := zrtpcpp
LOCAL_CPP_FEATURES := @local_cpp_features@

#
# set to false if testing/compiling new modules to catch undefined symbols (if build shared lib without TIVI_ENV)
# LOCAL_ALLOW_UNDEFINED_SYMBOLS := true

# include paths for zrtpcpp modules
LOCAL_C_INCLUDES += $(ROOT_SRC_PATH) $(ROOT_SRC_PATH)/srtp $(ROOT_SRC_PATH)/zrtp $(ROOT_SRC_PATH)/bnlib \
                    $(ROOT_SRC_PATH)/clients/tivi $(ROOT_SRC_PATH)/clients/tivi/android/jni/sqlite3

# LOCAL_CFLAGS := @TIVI_ENV@
LOCAL_SRC_FILES  := clients/tivi/android/jni/sqlite3/sqlite3.c
LOCAL_SRC_FILES += @zrtpcpp_src_spc@

include $(BUILD_STATIC_LIBRARY)
