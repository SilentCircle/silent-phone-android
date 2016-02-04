/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone2.dialhelpers;

import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.view.KeyEvent;
import android.widget.EditText;

import com.silentcircle.silentphone2.R;

/**
 * A Dial helper that adds a '+' sign during dialing.
 *
 * Created by werner on 22.06.14.
 */
public class SimpleHelper implements DialHelper {

    private static final int MIN_LENGTH = 6;
    private final static int mExplanationId = R.string.sp_dial_helper_simple_explanation;

    private boolean noModifications;

    @Override
    public int getExplanation() {
        return mExplanationId;
    }

    @Override
    public void resetAnalyser() {
        noModifications = false;
    }

    @Override
    public boolean analyseModifyNumberString(String in, StringBuilder out) {
        if (in == null || out == null)
            return false;

        in = PhoneNumberUtils.stripSeparators(in);
        if (in.length() >= MIN_LENGTH && Character.isDigit(in.charAt(0))) {
            out.append('+').append(in);
            return true;
        }
        return false;
    }

    @Override
    public boolean analyseCharModifyEditText(int in, EditText field) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
        field.onKeyDown(in, event);

        if (noModifications)
            return false;

        Editable edit = field.getEditableText();
        if (edit == null)
            return false;
        if (edit.length() >= MIN_LENGTH && Character.isDigit(edit.charAt(0))) {
            edit.insert (0, "+");
            field.setText(edit);
            field.setSelection(edit.length());
            noModifications = true;
            return true;
        }
        return false;
    }
}
