

LOCAL_PATH:= $(call my-dir)

# The following section installs SQLCipher (native library only)
include $(CLEAR_VARS)
LOCAL_MODULE := libsqlcipher
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libsqlcipher.so
include $(PREBUILT_SHARED_LIBRARY)

# CHECK THIS
# When we start an automated build set some environment variable to 'yes'
# The path setup follows the proposed dir structure depicted in the e-mail

ifeq ($(AUTOMATED_BUILD),1)

### setup for automated build
ROOT_SRC_PATH := $(JNI_ROOT)
TIVI_SRC_PATH := $(ROOT_SRC_PATH)/silentphone

else ifeq ($(USER),werner)

### setup for Werner
ROOT_SRC_PATH := $(HOME)/silentC
TIVI_SRC_PATH := $(ROOT_SRC_PATH)/tivi/sources
else ifeq ($(USER),janisn)

### setup for Janis
ROOT_SRC_PATH := $(HOME)/proj
TIVI_SRC_PATH := $(ROOT_SRC_PATH)/sources
else

### setup for documented build
ROOT_SRC_PATH := $(LOCAL_PATH)/silentphone2/support
TIVI_SRC_PATH := $(ROOT_SRC_PATH)/silentphone
endif

#
# Where to find the ZRTP files, the top level directory of ZRTP sources
ZRTP_SRC_PATH := $(ROOT_SRC_PATH)/zrtpcpp
AXO_SRC_PATH := $(ROOT_SRC_PATH)/axolotl

include $(CLEAR_VARS)
LOCAL_MODULE := zrtpcpp
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libzrtpcpp.a

# Where to find CtZrtpSession.h
LOCAL_EXPORT_C_INCLUDES := $(ZRTP_SRC_PATH)/clients/tivi

# Where to find commoncrypto/ZrtpRandom
LOCAL_EXPORT_C_INCLUDES += $(ZRTP_SRC_PATH)
include $(PREBUILT_STATIC_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := tina
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libtina.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := aec
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libaec.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := protobuf
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libprotobuf-cpp-lite.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := zina
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libzina.a
LOCAL_EXPORT_C_INCLUDES := $(AXO_SRC_PATH)

include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)


# Base directory of the Tivi sources. All other path are relative to
# this path because it's used to set LOCAL_PATH
LOCAL_PATH := $(TIVI_SRC_PATH)


LOCAL_MODULE    := tivi
LOCAL_SRC_FILES := tiviandroid/jni_glue2.cpp tiviandroid/t_a_main.cpp tiviandroid/sc_logs.cpp
LOCAL_ARM_MODE := arm
LOCAL_LDLIBS := -llog -lz

LOCAL_SHARED_LIBRARIES := tina
LOCAL_SHARED_LIBRARIES += aec
LOCAL_SHARED_LIBRARIES += libsqlcipher
LOCAL_STATIC_LIBRARIES := zrtpcpp
LOCAL_STATIC_LIBRARIES += zina
LOCAL_STATIC_LIBRARIES += protobuf


ifeq ($(AUTOMATED_BUILD),1)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../polarssl/include
TLS_SRC := $(LOCAL_PATH)/../polarssl
TLS_SOURCES := $(shell cd support/silentphone; ls ../polarssl/library/*.c) 

else ifeq ($(USER),werner)

# Werner: use this
LOCAL_C_INCLUDES := ../libs/polarssl/include
TLS_SRC := ../libs/polarssl
TLS_SOURCES := $(wildcard $(TLS_SRC)/library/*.c)
else ifeq ($(USER),janis)

# Janis: use this
LOCAL_C_INCLUDES := ../libs/zrtp/clients/tivi
LOCAL_C_INCLUDES += ../libs/zrtp
LOCAL_C_INCLUDES += ../libs/polarssl/include
TLS_SRC := ../libs/polarssl
TLS_SOURCES := $(wildcard $(TLS_SRC)/library/*.c)
else

# documented build for everyone else
LOCAL_C_INCLUDES := $(ROOT_SRC_PATH)/polarssl/include
TLS_SRC := $(ROOT_SRC_PATH)/polarssl
TLS_SOURCES := $(wildcard $(TLS_SRC)/library/*.c)
endif

LOCAL_SRC_FILES += $(TLS_SOURCES)


TPM_SRC := tiviengine/

MY_SOURCES := $(TPM_SRC)password.cpp $(TPM_SRC)media.cpp $(TPM_SRC)threads.cpp \
	$(TPM_SRC)CMakeSipSdp.cpp $(TPM_SRC)CPhone.cpp $(TPM_SRC)CSessions.cpp\
	$(TPM_SRC)g_cfg.cpp $(TPM_SRC)app_license.cpp $(TPM_SRC)prov.cpp\
	$(TPM_SRC)lic_keys.cpp $(TPM_SRC)CTSipSock.cpp $(TPM_SRC)sip_reason_translator.cpp\
	$(TPM_SRC)digestmd5.cpp $(TPM_SRC)userCfg.cpp $(TPM_SRC)build_nr.cpp\
	$(TPM_SRC)release.cpp $(TPM_SRC)CTLangStrings.cpp $(TPM_SRC)tivi_log.cpp\
	$(TPM_SRC)axolotl_glue.cpp

T_R_SRC := ./

MY_SOURCES += $(T_R_SRC)utils/utils.cpp $(T_R_SRC)encrypt/md5/md5.cpp \
	$(T_R_SRC)xml/parse_xml.cpp $(T_R_SRC)sipparser/client/CSipParse.cpp \
	$(T_R_SRC)rtp/parseRTP.cpp $(T_R_SRC)video/CTRTPVideoPlayer.cpp\
	$(T_R_SRC)sdp/parseSDP.cpp $(T_R_SRC)audio/android_audio.cpp \
	$(T_R_SRC)baseclasses/CTEditBase.cpp $(T_R_SRC)codecs/g711/g711.cpp \
	$(T_R_SRC)os/sys_utils.cpp $(T_R_SRC)encrypt/tls/CTTLS.cpp \
	$(T_R_SRC)utils/CTCoutryCode.cpp $(T_R_SRC)utils/utils_video.cpp

VAD_PATH := audio/vad/
MY_SOURCES += $(VAD_PATH)vad1.c $(VAD_PATH)vad2.c $(VAD_PATH)r_fft.c

G722_PATH := codecs/g722/
MY_SOURCES += $(G722_PATH)g722.c $(G722_PATH)t_g722.cpp $(G722_PATH)vector_int.c

GSM_PATH := codecs/gsm/
MY_SOURCES += $(GSM_PATH)add.cpp $(GSM_PATH)code.cpp $(GSM_PATH)decode.cpp $(GSM_PATH)gsm_crea.cpp $(GSM_PATH)gsm_deco.cpp \
    $(GSM_PATH)gsm_dest.cpp $(GSM_PATH)gsm_enco.cpp $(GSM_PATH)long_ter.cpp \
	$(GSM_PATH)gsm_lpc.cpp $(GSM_PATH)preproce.cpp $(GSM_PATH)rpe.cpp $(GSM_PATH)short_te.cpp

#G729_SRC :=home/ezis/sources/codecs/g729int/
#MY_SOURCES += $(G729_SRC)CTG729ED_int.cpp $(G729_SRC)g729_ACELP_CA.Cpp\
 $(G729_SRC)g729_BASIC_OP.Cpp $(G729_SRC)g729_BITS.Cpp\
 $(G729_SRC)g729_COD_LD8A.Cpp $(G729_SRC)g729_COR_FUNC.Cpp\
 $(G729_SRC)g729_DEC_GAIN.Cpp $(G729_SRC)g729_DEC_LAG3.Cpp\
 $(G729_SRC)g729_DEC_LD8A.Cpp $(G729_SRC)g729_DE_ACELP.Cpp\
 $(G729_SRC)g729_DSPFUNC.Cpp $(G729_SRC)g729_FILTER.Cpp\
 $(G729_SRC)g729_GAINPRED.Cpp $(G729_SRC)g729_LPCFUNC.Cpp\
 $(G729_SRC)g729_LPC_ren.Cpp $(G729_SRC)g729_LSPDEC.Cpp $(G729_SRC)g729_LSPGETQ.Cpp\
 $(G729_SRC)g729_OPER_32B.Cpp $(G729_SRC)g729_PITCH_A.Cpp\
 $(G729_SRC)g729_POSTFILT.Cpp $(G729_SRC)g729_POST_PRO.Cpp\
 $(G729_SRC)g729_PRED_LT3.Cpp $(G729_SRC)g729_PRE_PROC.Cpp\
 $(G729_SRC)g729_P_PARITY.Cpp $(G729_SRC)g729_QUA_GAIN.Cpp\
 $(G729_SRC)g729_QUA_LSP.Cpp $(G729_SRC)g729_TAB_LD8A.Cpp\
 $(G729_SRC)g729_TAMING.Cpp $(G729_SRC)g729_UTIL.Cpp

JPG_SRC := codecs/jpg/lib/

MY_SOURCES += $(JPG_SRC)jcapimin.c $(JPG_SRC)jcapistd.c $(JPG_SRC)jccoefct.c $(JPG_SRC)jccolor.c\
  $(JPG_SRC)jcdctmgr.c $(JPG_SRC)jchuff.c $(JPG_SRC)jcinit.c $(JPG_SRC)jcmainct.c $(JPG_SRC)jcmarker.c\
  $(JPG_SRC)jcmaster.c $(JPG_SRC)jcomapi.c $(JPG_SRC)jcparam.c\
  $(JPG_SRC)jcphuff.c $(JPG_SRC)jcprepct.c $(JPG_SRC)jcsample.c $(JPG_SRC)jctrans.c $(JPG_SRC)jdapimin.c\
  $(JPG_SRC)jdapistd.c $(JPG_SRC)jdatadst.c $(JPG_SRC)jdatasrc.c $(JPG_SRC)jdcoefct.c $(JPG_SRC)jdcolor.c\
  $(JPG_SRC)jddctmgr.c $(JPG_SRC)jdhuff.c $(JPG_SRC)jdinput.c $(JPG_SRC)jdmainct.c $(JPG_SRC)jdmarker.c\
  $(JPG_SRC)jdmaster.c $(JPG_SRC)jdmerge.c $(JPG_SRC)jdphuff.c $(JPG_SRC)jdpostct.c $(JPG_SRC)jdsample.c\
  $(JPG_SRC)jdtrans.c $(JPG_SRC)jerror.c $(JPG_SRC)jfdctflt.c $(JPG_SRC)jfdctfst.c $(JPG_SRC)jfdctint.c\
  $(JPG_SRC)jidctflt.c $(JPG_SRC)jidctfst.c $(JPG_SRC)jidctint.c $(JPG_SRC)jidctred.c $(JPG_SRC)jmemmgr.c \
  $(JPG_SRC)jmemname.c $(JPG_SRC)jquant1.c $(JPG_SRC)jquant2.c $(JPG_SRC)jutils.c $(JPG_SRC)rdbmp.c \
  $(JPG_SRC)rdcolmap.c $(JPG_SRC)rdswitch.c $(JPG_SRC)transupp.c $(JPG_SRC)wrbmp.c

MY_SOURCES +=  $(T_R_SRC)encrypt/zrtp/CTZrtp.cpp

TMR_SRC := tmr/
MY_SOURCES += $(TMR_SRC)/CTMRTunnel.cpp $(TMR_SRC)/tmr_parse.cpp

LOCAL_SRC_FILES += $(MY_SOURCES)

# $(warning Local_src $(LOCAL_SRC_FILES))
LOCAL_CPP_FEATURES := exceptions

# For this Android build we can set the visibility to hidden. Access to functions is only via
# exported JNI functions.
LOCAL_CFLAGS := -DANDROID_NDK $(SPA_NDK_OPTIONS) -DWITH_AXOLOTL -fvisibility=hidden -fvisibility-inlines-hidden
$(info Local flags:  $(LOCAL_CFLAGS))

include $(BUILD_SHARED_LIBRARY)
