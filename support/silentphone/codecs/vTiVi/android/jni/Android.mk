
LOCAL_PATH:= $(call my-dir)
#LOCAL_PATH := /Users/janisnarbuts/proj/sources/codecs/vTivi


include $(CLEAR_VARS)

LOCAL_MODULE    := libtina

TPM_SRC := ../../

LOCAL_SRC_FILES := \
    $(TPM_SRC)tina_enc_c.cpp  $(TPM_SRC)dec_arm_dsp.cpp  $(TPM_SRC)ep.cpp \
    $(TPM_SRC)mv.cpp $(TPM_SRC)block.cpp $(TPM_SRC)pack_hdr_ed.cpp $(TPM_SRC)huff_msort.cpp \
    $(TPM_SRC)t_filter.cpp $(TPM_SRC)t_dct.cpp $(TPM_SRC)../../utils/utils.cpp
    
LOCAL_EXPORT_C_INCLUDES := tina_exp.h
LOCAL_ARM_MODE := arm
LOCAL_CFLAGS := -DANDROID_NDK=5


#PREBUILT_STATIC_LIBRARY
#include $(PREBUILT_STATIC_LIBRARY)
# bija include $(BUILD_STATIC_LIBRARY)
include $(BUILD_SHARED_LIBRARY)
