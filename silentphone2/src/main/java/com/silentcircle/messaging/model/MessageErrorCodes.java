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

package com.silentcircle.messaging.model;

import android.support.annotation.IntDef;

import com.silentcircle.silentphone2.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This is a copy of the message error codes defined in the Axolotl native library.
 *
 * Created by werner on 17.07.15.
 */
public class MessageErrorCodes {

    @IntDef({SUCCESS, OK,
            GENERIC_ERROR, VERSION_NO_SUPPORTED, BUFFER_TOO_SMALL, NOT_DECRYPTABLE, NO_OWN_ID,
            JS_FIELD_MISSING, NO_DEVS_FOUND, NO_PRE_KEY_FOUND, NO_SESSION_DATA, SESSION_NOT_INITED, OLD_MESSAGE,
            CORRUPT_DATA, AXO_CONV_EXISTS, MAC_CHECK_FAILED, MSG_PADDING_FAILED, SUP_PADDING_FAILED, NO_STAGED_KEYS,
            RECEIVE_ID_WRONG, SENDER_ID_WRONG, RECV_DATA_LENGTH, WRONG_RECV_DEV_ID, NETWORK_ERROR, DATA_MISSING,
            DATABASE_ERROR, REJECT_DATA_RETENTION, PRE_KEY_HASH_WRONG, ILLEGAL_ARGUMENT, CONTEXT_ID_MISMATCH, NO_SUCH_CURVE,
            KEY_TYPE_MISMATCH, IDENTITY_KEY_TYPE_MISMATCH, WRONG_BLK_SIZE, UNSUPPORTED_KEY_SIZE, MAX_MEMBERS_REACHED,
            GROUP_CMD_MISSING_DATA, GROUP_CMD_DATA_INCONSISTENT, GROUP_MSG_DATA_INCONSISTENT, GROUP_MEMBER_NOT_STORED,
            NO_SUCH_ACTIVE_GROUP, WRONG_UPDATE_TYPE, NO_VECTOR_CLOCK, GROUP_UPDATE_RUNNING, GROUP_UPDATE_INCONSISTENT})

    @Retention(RetentionPolicy.SOURCE)
    public @interface MessageErrorCode {}


    public static final int SUCCESS           = 0;       //!< Success, same as SQLITE SUCCESS
    public static final int OK = 1;                      //!< Is @c true

    // Error codes for message processing, between -10 and -99, code -1 used for other purposes already
    public static final int GENERIC_ERROR     = -10;     //!< Generic error code, unspecified error
    public static final int VERSION_NO_SUPPORTED = -11;  //!< Unsupported protocol version
    public static final int BUFFER_TOO_SMALL  = -12;     //!< Buffer too small to store some data
    public static final int NOT_DECRYPTABLE = -13;       //!< Could not decrypt received message
    public static final int NO_OWN_ID  = -14;            //!< Found no own identity for registration
    public static final int JS_FIELD_MISSING  = -15;     //!< Missing a required JSON field
    public static final int NO_DEVS_FOUND  = -16;        //!< No registered Axolotl devices found for a user
    public static final int NO_PRE_KEY_FOUND  = -17;     //!< Offered Pre-key not found - unknown pre-key id
    public static final int NO_SESSION_DATA  = -18;      //!< No session data for this user - internal problem
    public static final int SESSION_NOT_INITED  = -19;   //!< Session not initialized
    public static final int OLD_MESSAGE  = -20;          //!< Message too old to decrypt
    public static final int CORRUPT_DATA = -21;          //!< Incoming data corrupt
    public static final int AXO_CONV_EXISTS = -22;       //!< Axolotl conversation exists while tyring to setup new one
    public static final int MAC_CHECK_FAILED = -23;      //!< HMAC check of encrypted message failed
    public static final int MSG_PADDING_FAILED = -24;    //!< Incorrect padding of decrypted message
    public static final int SUP_PADDING_FAILED = -25;    //!< Incorrect padding of decrypted supplementary data
    public static final int NO_STAGED_KEYS = -26;        //!< Not a real error, just to report that no staged keys available
    public static final int RECEIVE_ID_WRONG = -27;      //!< Receiver's long term id key hash mismatch
    public static final int SENDER_ID_WRONG = -28;       //!< Sender's long term id key hash mismatch
    public static final int RECV_DATA_LENGTH = -29;      //!< Expected length of data does not match received length
    public static final int WRONG_RECV_DEV_ID = -30;     //!< Expected device id does not match actual device id
    public static final int NETWORK_ERROR = -31;         //!< The HTTP request returned an code >400 or SIP failed
    public static final int DATA_MISSING = -32;          //!< Some data for a function is missing
    public static final int DATABASE_ERROR = -33;        //!< SQLCipher/SQLite returned and error code
    public static final int REJECT_DATA_RETENTION = -34; //!< Reject data retention when sending a message
    public static final int PRE_KEY_HASH_WRONG = -35;    //!< Pre-key check failed during setup of new conversation or re-keying
    public static final int ILLEGAL_ARGUMENT = -36;      //!< Value of an argument is illegal/out of range
    public static final int CONTEXT_ID_MISMATCH = -37;   //!< ZINA ratchet data is probably out of sync


    // ****** Ranges to simplify mapping of error codes, all positive
    private static final int MIN_MSG_ERROR = GENERIC_ERROR * -1;
    private static final int MAX_MSG_ERROR = ILLEGAL_ARGUMENT * -1;

    // Error codes for public key modules, between -100 and -199
    public static final int NO_SUCH_CURVE     = -100;    //!< Curve not supported
    public static final int KEY_TYPE_MISMATCH = -101;    //!< Private and public key use different curves
    private static final int MIN_PUB_ERROR = NO_SUCH_CURVE * -1;
    private static final int MAX_PUB_ERROR = KEY_TYPE_MISMATCH * -1;

    // Error codes for Ratcheting Session
    public static final int IDENTITY_KEY_TYPE_MISMATCH = -200;  //!< Their identity key and out identity key use different curve types
    private static final int MIN_RATCHET_ERROR = IDENTITY_KEY_TYPE_MISMATCH * -1;
    private static final int MAX_RATCHET_ERROR = IDENTITY_KEY_TYPE_MISMATCH * -1;

    // Error codes for encryption/decryption, HMAC
    public static final int WRONG_BLK_SIZE = -300;         //!< The IV or other data length did not match the cipher's block size
    public static final int UNSUPPORTED_KEY_SIZE = -301;   //!< Key size not supported for this cipher
    private static final int MIN_ENC_ERROR = WRONG_BLK_SIZE * -1;
    private static final int MAX_ENC_ERROR = UNSUPPORTED_KEY_SIZE * -1;

    // Error codes group chat
    public static final int GROUP_ERROR_BASE = -400;        //!< Base error code: -400 generic error, -401 thru -449 are SQL errors
    public static final int MAX_MEMBERS_REACHED = -450;     //!< Maximum members for this group reached
    public static final int GROUP_CMD_MISSING_DATA = -451;  //<! Message has type group command but no data
    public static final int GROUP_CMD_DATA_INCONSISTENT = -452; //<! Message is group command but has no command
    public static final int GROUP_MSG_DATA_INCONSISTENT = -453; //<! Message is normal group message command but has no data
    public static final int GROUP_MEMBER_NOT_STORED = -454;     //<! Cannot store a new member
    public static final int NO_SUCH_ACTIVE_GROUP = -455;    //<! Group does not exist or is not active
    public static final int WRONG_UPDATE_TYPE = -456;      //<! Update type not known
    public static final int NO_VECTOR_CLOCK = -457;        //<! No vector clock for group/event pair
    public static final int GROUP_UPDATE_RUNNING = -458;   //<! Currently preparing an sending group updates
    public static final int GROUP_UPDATE_INCONSISTENT = -459; //<! A Prepared change set is not available


    private static final int MIN_GROUP_ERROR = GROUP_ERROR_BASE * -1;
    private static final int MAX_GROUP_SQL_ERROR = MAX_MEMBERS_REACHED * -1;
    private static final int MAX_GROUP_ERROR = GROUP_UPDATE_INCONSISTENT * -1;

    private static final int[] MSG_TO_STRING_IDS = {
            R.string.message_error_msg_generic,             // 10
            R.string.message_error_msg_version,             // 11
            R.string.message_error_msg_buffer,              // 12
            R.string.message_error_msg_not_dec,             // 13
            R.string.message_error_msg_own_id,              // 14
            R.string.message_error_msg_no_js,               // 15
            R.string.message_error_msg_no_dev,              // 16
            R.string.message_error_msg_no_key,              // 17
            R.string.message_error_msg_no_session,          // 18
            R.string.message_error_msg_not_init,            // 19
            R.string.message_error_msg_old,                 // 20
            R.string.message_error_msg_corrupt,             // 21
            R.string.message_error_axo_conv_exists,         // 22
            R.string.message_error_mac_check_failed,        // 23
            R.string.message_error_msg_padding_failed,      // 24
            R.string.message_error_sup_padding_failed,      // 25
            R.string.message_error_no_staged_keys,          // 26
            R.string.message_error_recv_id_mismatch,        // 27
            R.string.message_error_sender_id_mismatch,      // 28
            R.string.message_error_recv_data_length,        // 29
            R.string.message_error_wrong_recv_dev_id,       // 30
            R.string.message_error_network_error,           // 31
            R.string.message_error_data_missing,            // 32
            R.string.message_error_database_error,          // 33
            R.string.message_error_reject_data_retention,   // 34
            R.string.message_error_pre_key_hash_wrong,      // 35
            R.string.message_error_illegal_argument,        // 36
            R.string.message_error_context_id_mismatch
    };

    private static final int[] PUB_TO_STRING_IDS = {
            R.string.message_error_pub_no_curve,            // 100
            R.string.message_error_pub_key_type             // 101
    };

    private static final int[] RATCHET_TO_STRING_IDS = {
            R.string.message_error_rat_key_type             // 200
    };

    private static final int[] ENC_TO_STRING_IDS = {
            R.string.message_error_enc_block,               // 300
            R.string.message_error_enc_key                  // 301
    };

    private static final int[] GROUP_TO_STRING_IDS = {
            R.string.message_error_group_450,
            R.string.message_error_group_451,
            R.string.message_error_group_452,
            R.string.message_error_group_453,
            R.string.message_error_group_454,
            R.string.message_error_group_455,
            R.string.message_error_group_456,
            R.string.message_error_group_457,
            R.string.message_error_group_458,
            R.string.message_error_group_459
    };
    public static int messageErrorToStringId(@MessageErrorCode final int error) {
        if (error >= 0)
            return R.string.message_error_no_error;

        int code = error * -1;                              //make it positive
        if (code >= MIN_MSG_ERROR && code <= MAX_MSG_ERROR) {
            return MSG_TO_STRING_IDS[code - MIN_MSG_ERROR];
        }
        else if (code >= MIN_PUB_ERROR && code <= MAX_PUB_ERROR) {
            return PUB_TO_STRING_IDS[code - MIN_PUB_ERROR];
        }
        else if (code >= MIN_RATCHET_ERROR && code <= MAX_RATCHET_ERROR) {
            return RATCHET_TO_STRING_IDS[code - MIN_RATCHET_ERROR];

        }
        else if (code >= MIN_ENC_ERROR && code <= MAX_ENC_ERROR) {
            return ENC_TO_STRING_IDS[code - MIN_ENC_ERROR];
        }
        else if (code >= MIN_GROUP_ERROR && code <= MAX_GROUP_ERROR) {
            if (code < MAX_GROUP_SQL_ERROR)
                return R.string.message_error_group_400;
            return GROUP_TO_STRING_IDS[code - MAX_GROUP_SQL_ERROR];
        }
        else
            return R.string.message_error_unknown;
    }
}
