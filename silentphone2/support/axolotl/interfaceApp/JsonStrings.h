/*
Copyright 2016-2017 Silent Circle, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

// The JSON tags for group commands, data etc.
//
// Created by werner on 22.05.16.
//

#ifndef LIBZINA_JSONSTRINGS_H
#define LIBZINA_JSONSTRINGS_H

namespace zina {
    static const char* GROUP_ID = "grpId";
    static const char* GROUP_NAME = "name";
    static const char* GROUP_OWNER = "ownerId";
    static const char* GROUP_DESC = "desc";
    static const char* GROUP_MAX_MEMBERS = "maxMbr";
    static const char* GROUP_MEMBER_COUNT = "mbrCnt";
    static const char* GROUP_ATTRIBUTE = "grpA";
    static const char* GROUP_MOD_TIME = "grpMT";
    static const char* GROUP_BURN_SEC = "BSec";
    static const char* GROUP_BURN_MODE = "BMode";
    static const char* GROUP_AVATAR = "Ava";

    static const char* GROUP_CHANGE_SET = "grpChg";


    static const char* MEMBER_ID = "mbrId";
    static const char* MEMBER_ATTRIBUTE = "mbrA";
    static const char* MEMBER_MOD_TIME = "mbrMT";

    static const char* MSG_VERSION = "version";
    static const char* MSG_RECIPIENT = "recipient";
    static const char* MSG_ID = "msgId";
    static const char* MSG_MESSAGE = "message";
    static const char* MSG_SENDER = "sender";
    static const char* MSG_DEVICE_ID = "scClientDevId";
    static const char* MSG_DISPLAY_NAME = "display_name";
    static const char* MSG_COMMAND = "cmd";
    static const char* MSG_SYNC_COMMAND = "syc";
    static const char* MSG_TYPE = "type";
    static const char* MSG_ID_KEY_CHANGED = "idkc";
    static const char* MSG_IDS = "msgIds";

    // The following strings follow the MSG_COMMAND and identify the command
    static const char* DELIVERY_RECEIPT = "dr";

    // Error commands, sent by message receiver to the sender
    static const char* DR_DATA_REQUIRED = "errdrq";     //!< Not Delivered Due to Policy: DR required [ERRDRQ]
    static const char* DR_META_REQUIRED = "errmrq";     //!< Not Delivered Due to Policy: MR required [ERRMRQ]
    static const char* DR_DATA_REJECTED = "errdrj";     //!< Not Delivered Due to Policy: DR rejected [ERRDRJ]
    static const char* DR_META_REJECTED = "errmrj";     //!< Not Delivered Due to Policy: MR rejected [ERRMRJ]
    static const char* DECRYPTION_FAILED = "errdecf";   //!< Not Delivered Due to decryption failure [ERRDECF]
    static const char* COMM_BLOCKED     = "errblk";     //!< Not Delivered Due to DR policy and user blocked DR

    static const char* GROUP_COMMAND = "grp";

    // The following strings follow the GROUP_COMMAND and identify the command
    static const char* ADD_MEMBERS = "addm";    //!< Command to add a list of new group members
    static const char* RM_MEMBERS = "rmm";      //!< Command to remove a list of group members
    static const char* HELLO = "hel";           //!< Introduce myself as a new member of the list
    static const char* LEAVE = "lve";           //!< A member leaves a group
    static const char* REMOVE_MSG = "rmsg";     //!< remove a message from a group conversation
    static const char* NEW_GROUP = "ngrp";      //!< New group created
    static const char* NEW_NAME = "nnm";        //!< New group name
    static const char* NEW_AVATAR = "navtr";    //!< New group avatar information
    static const char* NEW_BURN = "nbrn";       //!< New group burn information

    static const char* INVITE_SYNC = "s_inv";   //!< Sync accepted Group Invitation

    // Parameters inside commands other than generic Group or Member tags
    static const char* MEMBERS = "mbrs";
    static const char* INITIAL_LIST = "ini";
    static const char* DELIVERY_TIME = "dr_time";
    static const char* COMMAND_TIME = "cmd_time"; //!< Time at client (ZULU) when it created the command, seconds
    static const char* COMMAND_TIME_U = "cmd_time_u"; //!< Time at client (ZULU) when it created the command, micro-sec

    // JSON keys for local messaging retention flags
    static const char* LRMM = "lrmm";           //!< local client retains message metadata
    static const char* LRMP = "lrmp";           //!< local client retains message plaintext
    static const char* LRAP = "lrap";           //!< local client retains attachment plaintext
    static const char* BLDR = "bldr";           //!< Block local data retention
    static const char* BLMR = "blmr";           //!< Block local metadata retention
    static const char* BRDR = "brdr";           //!< Block remote data retention
    static const char* BRMR = "brmr";           //!< Block remote metadata retention

    // JSON keys for remote user messaging retention flags
    static const char* RETENTION_ORG = "ret_org";
    static const char* RRMM = "rrmm";
    static const char* RRMP = "rrmp";
    static const char* RRCM = "rrcm";
    static const char* RRCP = "rrcp";
    static const char* RRAP = "rrap";

    // JSON keys in message attributes to show DR states
    static const char* RAP = "RAP";             //!< set by sender: "retention accepted plaintext"
    static const char* RAM = "RAM";             //!< set by sender: "retention accepted metadata"
    static const char* ROP = "ROP";             //!< set by sender: "retention occurred plaintext"
    static const char* ROM = "ROM";             //!< set by sender: "retention occurred metadata"

    static const char* DR_STATUS_BITS = "dr_status"; //!< Status bits for the set R* flags

}
#endif //LIBZINA_JSONSTRINGS_H
