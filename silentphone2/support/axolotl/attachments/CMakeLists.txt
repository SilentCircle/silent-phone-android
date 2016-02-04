#
# Copyright (c) 2015 Slient Circle LLC.  All rights reserved.
#
# @author Werner Dittmann <Werner.Dittmann@t-online.de>
#

cmake_minimum_required(VERSION 3.0)

set (external_crypto_src
    ${ZRTP_BASE_DIR}/zrtp/crypto/skein256.cpp
    ${ZRTP_BASE_DIR}/cryptcommon/skeinApi.c
    ${ZRTP_BASE_DIR}/cryptcommon/skein.c
    ${ZRTP_BASE_DIR}/cryptcommon/skein_block.c
)

set(common_src
    ${CMAKE_SOURCE_DIR}/attachments/utilities.cpp
)
set(fileHandler_src
    ${CMAKE_SOURCE_DIR}/attachments/fileHandler/scloud.cpp
    ${CMAKE_SOURCE_DIR}/attachments/fileHandler/scloudJson.cpp
)

set(attchment_src
    ${common_src}
    ${fileHandler_src}
)

if (STANDALONE)
    set(attchment_src ${attchment_src} ${external_crypto_src})
endif()


set(attchment_src_global ${attchment_src} PARENT_SCOPE)  # make it visible in parent script

add_library(attachment_lib OBJECT ${attchment_src})
set_property(TARGET attachment_lib PROPERTY POSITION_INDEPENDENT_CODE TRUE)
TARGET_INCLUDE_DIRECTORIES(attachment_lib PUBLIC ${ZRTP_BASE_DIR})
