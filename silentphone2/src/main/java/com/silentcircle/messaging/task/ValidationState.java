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
package com.silentcircle.messaging.task;

import com.silentcircle.silentphone2.R;

/**
 * States for user name validation.
 */
public enum ValidationState {
    USERNAME_VALID, /* user name is ok */
    VALIDATION_ERROR, /* could not validate user name, generic error */
    USERNAME_EMPTY, /* provided user name is empty, nothing to validate */
    NETWORK_NOT_AVAILABLE; /* cannot validate user name without connection */

    public static int getStateDescriptionId(ValidationState state) {
        int descriptionId = 0;
        switch (state) {
            case VALIDATION_ERROR:
                descriptionId = R.string.user_validation_generic_error;
                break;
            case USERNAME_EMPTY:
                descriptionId = R.string.user_validation_username_empty;
                break;
            case NETWORK_NOT_AVAILABLE:
                descriptionId = R.string.user_validation_no_connection;
                break;
            default:
        }
        return descriptionId;
    }
}
