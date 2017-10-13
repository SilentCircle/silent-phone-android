/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package zina;

/**
 * This class defines the JSON strings, it's a partial copy of the same class in the native library.
 *
 * Created by werner on 16.06.16.
 */

public class JsonStrings {
    private JsonStrings() {}        // No one should instantiate the class

    public static final String GROUP_ID = "grpId";
    public static final String GROUP_NAME = "name";
    public static final String GROUP_OWNER = "ownerId";
    public static final String GROUP_DESC = "desc";
    public static final String GROUP_MAX_MEMBERS = "maxMbr";
    public static final String GROUP_ATTRIBUTE = "grpA";
    public static final String GROUP_MEMBER_COUNT = "mbrCnt";
    public static final String GROUP_MOD_TIME = "grpMT";
    public static final String GROUP_BURN_SEC = "BSec";
    public static final String GROUP_BURN_MODE = "BMode";
    public static final String GROUP_AVATAR = "Ava";

    public static final String MEMBER_ID = "mbrId";
    public static final String MEMBER_ATTRIBUTE = "mbrA";
    public static final String MEMBER_MOD_TIME = "mbrMT";
    public static final String MEMBER_DISPLAY_NAME = "mbr_display_name";

    public static final String GROUP_COMMAND = "grp";

    // The following strings follow the GROUP_COMMAND and identify the command
    public static final String ADD_MEMBERS = "addm";    //!< Command to add a list of new group members
    public static final String RM_MEMBERS = "rmm";      //!< Command to remove a list of group members
    public static final String HELLO = "hel";           //!< Introduce myself as a new member of the list
    public static final String LEAVE = "lve";           //!< A member leaves a group
    public static final String REMOVE_MSG = "rmsg";     //!< remove a message from a group conversation
    public static final String NEW_GROUP = "ngrp";      //!< New group created
    public static final String NEW_NAME = "nnm";        //!< New group name
    public static final String NEW_AVATAR = "navtr";    //!< New group avatar information
    public static final String NEW_BURN = "nbrn";       //!< New group burn information
    public static final String MEMBERS = "mbrs";


    public static final String MSG_RECIPIENT = "recipient";
    public static final String MSG_ID = "msgId";
    public static final String MSG_MESSAGE = "message";
    public static final String MSG_SENDER = "sender";
    public static final String MSG_DISPLAY_NAME = "display_name";
    public static final String MSG_DEVICE_ID = "scClientDevId";
    public static final String MSG_VERSION = "version";
    public static final String MSG_COMMAND = "cmd";
    public static final String MSG_SYNC_COMMAND = "syc";
    public static final String MSG_ID_KEY_CHANGED = "idkc";
    public static final String MSG_IDS = "msgIds";

    public static final String MSG_DEVICE_NAME = "scClientDevName";
    public static final String MSG_USER_ID = "userId";

    // The following strings follow the MSG_COMMAND and identify the command
    public static final String READ_RECEIPT = "rr";
    public static final String DELIVERY_RECEIPT = "dr";
    public static final String BURN = "bn";

    // Parameters inside commands other than generic Group or Member tags
    public static final String READ_TIME = "rr_time";
    public static final String DELIVERY_TIME = "dr_time";
    public static final String ORIGINAL_RECEIVER = "or";
    public static final String ORIGINAL_MESSAGE = "om";
    public static final String COMMAND_TIME = "cmd_time";
    public static final String COMMAND_TIME_U = "cmd_time_u"; //!< Time at client (ZULU) when it created the command, micro-sec

    public static final String ATTRIBUTE_SHRED_AFTER = "s";
    public static final String ATTRIBUTE_READ_RECEIPT = "r";
    public static final String ATTRIBUTE_CALL_TYPE = "ct";
    public static final String ATTRIBUTE_CALL_DURATION = "cd";
    public static final String ATTRIBUTE_CALL_ERROR = "errorMessage";
    public static final String DR_STATUS_BITS = "dr_status"; //!< Status bits for the set R* flags

    public static final String EXTRA_GROUP_CMD_MESSAGE = "cmdMessage";

    // Message rejected due to DR policies
    public static final String DR_DATA_REQUIRED = "errdrq";     //!< Local client requires to retain plaintext data, remote party does not accept this policy
    public static final String DR_META_REQUIRED = "errmrq";     //!< Local client requires to retain meta data, remote party does not accept this policy
    public static final String DR_DATA_REJECTED = "errdrj";     //!< Remote party retained plaintext data, local client blocks this policy
    public static final String DR_META_REJECTED = "errmrj";     //!< Remote party retained meta data, local client blocks this policy
    public static final String DECRYPTION_FAILED = "errdecf";   //!< Not Delivered due to decryption failure
    public static final String COMM_BLOCKED     = "errblk";     //!< Not Delivered Due to DR policy and user blocked DR

    // JSON keys for local messaging retention flags, used to set flags in ZINA
    public static final String LRMM = "lrmm";       // local user retains message meta data
    public static final String LRMP = "lrmp";       // local user retains message plaintext data
    public static final String LRAP = "lrap";       // local user retains attachment plaintext data
    public static final String BLDR = "bldr";       // block local data retention, set by UI
    public static final String BLMR = "blmr";       // block local meta data retention, set by UI
    public static final String BRDR = "brdr";       // block remote data retention, set by UI
    public static final String BRMR = "brmr";       // block remote meta data retention, set by UI

    // JSON keys for remote user retention flags
    public static final String RETENTION_ORG = "ret_org";
    public static final String RRMM = "rrmm";       // remote user retains message meta data
    public static final String RRMP = "rrmp";       // remote user retains message plaintext data
    public static final String RRCM = "rrcm";       // remote user retains call meta data
    public static final String RRCP = "rrcp";       // remote user retains call plaintext data
    public static final String RRAP = "rrap";       // remote user retains attachment plaintext data

}
