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
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Dial helper for users who selected North American Number Plan (NANP).
 *
 * Created by werner on 22.06.14.
 */
public class NanpHelper implements DialHelper {

    @SuppressWarnings("unused")
    private static final String TAG = NanpHelper.class.getSimpleName();

    private final static String NANP_IDD = "011";
    private final static int mExplanationId = R.string.sp_dial_helper_nanp_explanation;
    private boolean noModifications;
    private boolean firstCharSeen;
    private boolean mIddStartSeen;

    @Override
    public int getExplanation() {
        return mExplanationId;
    }

    @Override
    public void resetAnalyser() {
        firstCharSeen = false;
        noModifications = false;
        mIddStartSeen = false;
    }

    @Override
    public boolean analyseModifyNumberString(String in, StringBuilder out) {
        if (in == null || out == null)
            return false;

        in = PhoneNumberUtils.stripSeparators(in);
        char firstChar = in.charAt(0);

        switch (firstChar) {
            case '+':
                return false;

            case '1':
                if (in.length() == 11) {
                    out.append('+').append(in);
                    return true;
                }
                return false;

            case '0':
                if (in.startsWith(NANP_IDD)) {
                    out.append('+').append(in.substring(NANP_IDD.length()));
                    return true;
                }
                return false;

            default:
                if (in.length() == 10) {
                    out.append('+').append('1').append(in);
                    return true;
                }
                return false;
        }
    }

    @Override
    public boolean analyseCharModifyEditText(int in, EditText field) {
        KeyEvent event;

        if (noModifications) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
            field.onKeyDown(in, event);
            return false;
        }
        if (!firstCharSeen) {
            firstCharSeen = true;
            switch (in) {
                case KeyEvent.KEYCODE_PLUS:
                    event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
                    field.onKeyDown(in, event);
                    noModifications = true;
                    return true;

                case KeyEvent.KEYCODE_1:
                    event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PLUS);
                    field.onKeyDown(KeyEvent.KEYCODE_PLUS, event);
                    event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
                    field.onKeyDown(in, event);
                    noModifications = true;
                    return true;

                case KeyEvent.KEYCODE_0:
                    mIddStartSeen = true;
                    break;

                default:
                    event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
                    char first = event.getDisplayLabel();
                    if (Character.isDigit(first))
                        Utilities.sendString(FindDialHelper.getActiveCountry().countryCode, field);

                    field.onKeyDown(in, event);

                    noModifications = true;
                    return true;
            }
        }
        if (mIddStartSeen) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
            field.onKeyDown(in, event);

            Editable edit = field.getEditableText();
            if (edit == null)
                return false;
            if (edit.length() >= NANP_IDD.length()) {
                if (edit.toString().startsWith(NANP_IDD)) {
                    edit.delete(0, NANP_IDD.length());
                    field.setText(edit.toString().trim());
                    field.setSelection(0);
                    Utilities.sendString("+", field);
                    field.setSelection(field.length());
                    mIddStartSeen = false;
                    noModifications = true;
                    return true;
                }
            }
        }
        return false;
    }
}
