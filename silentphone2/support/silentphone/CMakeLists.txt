# Copyright (C) 2012 Werner Dittman
#
# This file contains the setup of some variables that describe the Tivi files
# common for all builds/systems. For each system we add a subdirectory which
# contains another cmake script file that covers the specific settings.
#
# Here we also set some global path variables and includes to find the ZRTP
# and PolarSSL subsystems.
#
cmake_minimum_required(VERSION 2.6)

PROJECT(Tivi)

SET(CPACK_PACKAGE_VERSION_MAJOR 1)
SET(CPACK_PACKAGE_VERSION_MINOR 0)
SET(CPACK_PACKAGE_VERSION_PATCH 0)

set (VERSION 3.0.0)
set (SOVERSION 3)

# ***** SETUP section *****
# see README
#
# Setup of variable that point to additional directories, libraries, etc.
# Set the 'otherlibs' variable accouring to you environment
#
set(otherLibs ${CMAKE_SOURCE_DIR}/../libs) ## adjust this setting if necessary

set(zrtpDir ${otherLibs}/zrtpcpp)          ## adjust this setting if necessary
set(polarDir ${otherLibs}/polarssl)        ## adjust this setting if necessary

# set base directory of tivi sources - usually not necessary to change this
set(tiviBase ${CMAKE_SOURCE_DIR})

# **** End of SETUP section ****

option(LINUX "Build library and tivi slient for LINUX." OFF)
option(ANDROID "Prepare Android makefiles - don run them." OFF)
option(IOS "Create Xcode project files. Run on Mac only and use cmake -G Xcode." OFF)

if (LINUX)
    set(tiviSystem "Linux")
elseif(ANDROID)
    set(tiviSystem "Android")
elseif(IOS)
    set(tiviSystem "iOS")
else()
    MESSAGE(FATAL_ERROR "No build system defined.")
endif()


MESSAGE(STATUS "Configuring Tivi client for ${tiviSystem}. Base settings: zrtp: ${zrtpDir}, polar: ${polarDir}")

include_directories (${zrtpDir} ${zrtpDir}/zrtp ${zrtpDir}/clients/tivi ${polarDir}/include)

set(encryptDir 
    ${tiviBase}/encrypt/md5/md5.cpp 
    ${tiviBase}/encrypt/tls/CTTLS.cpp 
    ${tiviBase}/encrypt/zrtp/CTZrtp.cpp)

set(baseClasses 
    ${tiviBase}/baseclasses/CTEditBase.cpp)

set(codecsGsm 
    ${tiviBase}/codecs/gsm/add.cpp 
    ${tiviBase}/codecs/gsm/code.cpp 
    ${tiviBase}/codecs/gsm/decode.cpp
    ${tiviBase}/codecs/gsm/gsm_crea.cpp
    ${tiviBase}/codecs/gsm/gsm_debug.cpp
    ${tiviBase}/codecs/gsm/gsm_deco.cpp
    ${tiviBase}/codecs/gsm/gsm_dest.cpp
    ${tiviBase}/codecs/gsm/gsm_enco.cpp
    ${tiviBase}/codecs/gsm/gsm_lpc.cpp
    ${tiviBase}/codecs/gsm/long_ter.cpp
    ${tiviBase}/codecs/gsm/preproce.cpp
    ${tiviBase}/codecs/gsm/rpe.cpp
    ${tiviBase}/codecs/gsm/short_te.cpp)

set(codecsG729float
    ${tiviBase}/codecs/g729float/acelp_ca.cpp  
    ${tiviBase}/codecs/g729float/cor_func.cpp  
    ${tiviBase}/codecs/g729float/dec_lag3.cpp
    ${tiviBase}/codecs/g729float/gainpred.cpp
    ${tiviBase}/codecs/g729float/lspdec.cpp
    ${tiviBase}/codecs/g729float/postfila.cpp
    ${tiviBase}/codecs/g729float/pred_lt3.cpp
    ${tiviBase}/codecs/g729float/qua_gain.cpp
    ${tiviBase}/codecs/g729float/taming.cpp
    ${tiviBase}/codecs/g729float/bits.cpp
    ${tiviBase}/codecs/g729float/de_acelp.cpp
    ${tiviBase}/codecs/g729float/dec_ld8a.cpp
    ${tiviBase}/codecs/g729float/lpca.cpp
    ${tiviBase}/codecs/g729float/lspgetq.cpp
    ${tiviBase}/codecs/g729float/post_pro.cpp
    ${tiviBase}/codecs/g729float/pre_proc.cpp
    ${tiviBase}/codecs/g729float/qua_lsp.cpp
    ${tiviBase}/codecs/g729float/util.cpp
    ${tiviBase}/codecs/g729float/cod_ld8a.cpp
    ${tiviBase}/codecs/g729float/dec_gain.cpp
    ${tiviBase}/codecs/g729float/filter.cpp
    ${tiviBase}/codecs/g729float/lpcfunca.cpp
    ${tiviBase}/codecs/g729float/pitch_a.cpp
    ${tiviBase}/codecs/g729float/p_parity.cpp
    ${tiviBase}/codecs/g729float/pwf.cpp
    ${tiviBase}/codecs/g729float/tab_ld8a.cpp)

set(codecsG711
    ${tiviBase}/codecs/g711/g711.cpp)

set(codecsG722
    ${tiviBase}/codecs/g722/g722.c
    ${tiviBase}/codecs/g722/t_g722.cpp  
    ${tiviBase}/codecs/g722/vector_int.c)

set(codecsJpeg
    ${tiviBase}/codecs/jpg/lib/jcapimin.c  
    ${tiviBase}/codecs/jpg/lib/jchuff.c
    ${tiviBase}/codecs/jpg/lib/jcomapi.c
    ${tiviBase}/codecs/jpg/lib/jctrans.c
    ${tiviBase}/codecs/jpg/lib/jdcoefct.c
    ${tiviBase}/codecs/jpg/lib/jdmainct.c
    ${tiviBase}/codecs/jpg/lib/jdpostct.c
    ${tiviBase}/codecs/jpg/lib/jfdctfst.c
    ${tiviBase}/codecs/jpg/lib/jidctred.c
    ${tiviBase}/codecs/jpg/lib/jutils.c
    ${tiviBase}/codecs/jpg/lib/transupp.c
    ${tiviBase}/codecs/jpg/lib/jcapistd.c
    ${tiviBase}/codecs/jpg/lib/jcinit.c
    ${tiviBase}/codecs/jpg/lib/jcparam.c
    ${tiviBase}/codecs/jpg/lib/jdapimin.c
    ${tiviBase}/codecs/jpg/lib/jdcolor.c
    ${tiviBase}/codecs/jpg/lib/jdmarker.c
    ${tiviBase}/codecs/jpg/lib/jdsample.c
    ${tiviBase}/codecs/jpg/lib/jfdctint.c
    ${tiviBase}/codecs/jpg/lib/jmemmgr.c
    ${tiviBase}/codecs/jpg/lib/rdbmp.c
    ${tiviBase}/codecs/jpg/lib/wrbmp.c
    ${tiviBase}/codecs/jpg/lib/jccoefct.c
    ${tiviBase}/codecs/jpg/lib/jcmainct.c
    ${tiviBase}/codecs/jpg/lib/jcphuff.c
    ${tiviBase}/codecs/jpg/lib/jdapistd.c
    ${tiviBase}/codecs/jpg/lib/jddctmgr.c
    ${tiviBase}/codecs/jpg/lib/jdmaster.c
    ${tiviBase}/codecs/jpg/lib/jdtrans.c
    ${tiviBase}/codecs/jpg/lib/jidctflt.c
    ${tiviBase}/codecs/jpg/lib/jmemname.c
    ${tiviBase}/codecs/jpg/lib/rdcolmap.c
    ${tiviBase}/codecs/jpg/lib/wrjpgcom.c
    ${tiviBase}/codecs/jpg/lib/jccolor.c
    ${tiviBase}/codecs/jpg/lib/jcmarker.c
    ${tiviBase}/codecs/jpg/lib/jcprepct.c
    ${tiviBase}/codecs/jpg/lib/jdatadst.c
    ${tiviBase}/codecs/jpg/lib/jdhuff.c
    ${tiviBase}/codecs/jpg/lib/jdmerge.c
    ${tiviBase}/codecs/jpg/lib/jerror.c
    ${tiviBase}/codecs/jpg/lib/jidctfst.c
    ${tiviBase}/codecs/jpg/lib/jquant1.c
    ${tiviBase}/codecs/jpg/lib/rdjpgcom.c
    ${tiviBase}/codecs/jpg/lib/jcdctmgr.c
    ${tiviBase}/codecs/jpg/lib/jcmaster.c
    ${tiviBase}/codecs/jpg/lib/jcsample.c
    ${tiviBase}/codecs/jpg/lib/jdatasrc.c
    ${tiviBase}/codecs/jpg/lib/jdinput.c
    ${tiviBase}/codecs/jpg/lib/jdphuff.c
    ${tiviBase}/codecs/jpg/lib/jfdctflt.c
    ${tiviBase}/codecs/jpg/lib/jidctint.c
    ${tiviBase}/codecs/jpg/lib/jquant2.c
    ${tiviBase}/codecs/jpg/lib/rdswitch.c)

set(sipParser 
    ${tiviBase}/sipparser/client/CSipParse.cpp)

set(tiviEngine
    ${tiviBase}/tiviengine/app_license.cpp
    ${tiviBase}/tiviengine/build_nr.cpp
    ${tiviBase}/tiviengine/CPhone.cpp
    ${tiviBase}/tiviengine/CTLangStrings.cpp
    ${tiviBase}/tiviengine/digestmd5.cpp
    ${tiviBase}/tiviengine/lic_keys.cpp
    ${tiviBase}/tiviengine/recents_save_load.cpp
    ${tiviBase}/tiviengine/userCfg.cpp
    ${tiviBase}/tiviengine/media.cpp
    ${tiviBase}/tiviengine/CMakeSipSdp.cpp
    ${tiviBase}/tiviengine/CSessions.cpp
    ${tiviBase}/tiviengine/CTSipSock.cpp
    ${tiviBase}/tiviengine/g_cfg.cpp
    ${tiviBase}/tiviengine/prov.cpp
    ${tiviBase}/tiviengine/password.cpp
    ${tiviBase}/tiviengine/threads.cpp
    ${tiviBase}/tiviengine/tivi_log.cpp
    ${tiviBase}/tiviengine/release.cpp
    ${tiviBase}/tiviengine/sip_reason_translator.cpp
)

set(rtp 
    ${tiviBase}/rtp/parseRTP.cpp)

set(sdp 
    ${tiviBase}/sdp/parseSDP.cpp)

set(utils
    ${tiviBase}/utils/arg_parser.cpp
    ${tiviBase}/utils/gr_utils.cpp
    ${tiviBase}/utils/utf8_ushort.cpp
    ${tiviBase}/utils/utils_ms_audio.cpp
    ${tiviBase}/utils/utils_ms_net.cpp
    ${tiviBase}/utils/utils_symb_etel.cpp
    ${tiviBase}/utils/CTCoutryCode.cpp
    ${tiviBase}/utils/tpwd.cpp
    ${tiviBase}/utils/utils.cpp
    ${tiviBase}/utils/utils_ms.cpp
    ${tiviBase}/utils/utils_os.cpp
    ${tiviBase}/utils/utils_tcp.cpp)

set(os 
    ${tiviBase}/os/sys_utils.cpp)

set(vad
    ${tiviBase}/audio/vad/r_fft.c
    ${tiviBase}/audio/vad/vad1.c
    ${tiviBase}/audio/vad/vad2.c)

set(xml
    ${tiviBase}/xml/parse_xml.cpp)

set(vTivi ${tiviBase}/codecs/vTiVi/
    ${tiviBase}/codecs/vTiVi/blockARM.cpp
    ${tiviBase}/codecs/vTiVi/dbg.cpp
    ${tiviBase}/codecs/vTiVi/ep.cpp
    ${tiviBase}/codecs/vTiVi/graf_utils.cpp
    ${tiviBase}/codecs/vTiVi/mv.cpp
    ${tiviBase}/codecs/vTiVi/pack_hdr_ed.cpp
    ${tiviBase}/codecs/vTiVi/predl.cpp
    ${tiviBase}/codecs/vTiVi/tab_c_l_t.cpp
    ${tiviBase}/codecs/vTiVi/t_dct.cpp
    ${tiviBase}/codecs/vTiVi/tina_enc_c.cpp
    ${tiviBase}/codecs/vTiVi/block.cpp
    ${tiviBase}/codecs/vTiVi/dec_arm_dsp.cpp
    ${tiviBase}/codecs/vTiVi/gr2_utils.cpp
    ${tiviBase}/codecs/vTiVi/huff_msort.cpp
    ${tiviBase}/codecs/vTiVi/mvtest.cpp
    ${tiviBase}/codecs/vTiVi/pred4.cpp
    ${tiviBase}/codecs/vTiVi/tab_c_l.cpp
    ${tiviBase}/codecs/vTiVi/tabs.cpp
    ${tiviBase}/codecs/vTiVi/t_filter.cpp
    ${tiviBase}/codecs/vTiVi/t_ssim.c)

#set(aec
#    ${tiviBase}/audio/ec/speex_aec/speex_aec.cpp)

if (LINUX)
    add_subdirectory(linux)
elseif(ANDROID)
    add_subdirectory(android)
elseif(IOS)
    add_subdirectory(ios)
else()
    MESSAGE(FATAL_ERROR "No build system defined.")
endif()
