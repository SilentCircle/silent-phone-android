/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.EditText;

import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Dial helper for users who select a numbering plan that has national and international trunk prefixes.
 *
 * This implementations assumes that the national trunk prefix is shorter than the
 * international trunk prefix and that the national and international prefixes start with
 * the different digits (hence the _n).
 *
 * Created by werner on 22.06.14.
 */
public class Dial_00_nHelper implements DialHelper {

    @SuppressWarnings("unused")
    private static final String TAG = Dial_00_nHelper.class.getSimpleName();

    private final static int mExplanationId = R.string.sp_dial_helper_00_0_explanation;
    private boolean mNoModifications;
    private boolean firstCharSeen;
    private boolean mIddStartSeen;

    @Override
    public int getExplanation() {
        return mExplanationId;
    }

    @Override
    public void resetAnalyser() {
        firstCharSeen = false;
        mNoModifications = false;
        mIddStartSeen = false;
    }

    @Override
    public boolean analyseModifyNumberString(String in, StringBuilder out) {
        if (in == null || out == null)
            return false;

        in = PhoneNumberUtils.stripSeparators(in);
        char firstChar = in.charAt(0);

        String national = FindDialHelper.getActiveCountry().national;
        String idd = FindDialHelper.getActiveCountry().idd;

        if (firstChar == '+')
            return false;

        if (in.startsWith(idd)) {
            out.append('+').append(in.substring(idd.length()));
            return true;
        }
        if (in.startsWith(national)) {
            out.append(FindDialHelper.getActiveCountry().countryCode).append(in.substring(national.length()));
            return true;
        }
        return false;
    }

    @Override
    public boolean analyseCharModifyEditText(int in, EditText field) {
        KeyEvent event;

        if (mNoModifications) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
            field.onKeyDown(in, event);
            return false;
        }
        String national = FindDialHelper.getActiveCountry().national;
        String idd = FindDialHelper.getActiveCountry().idd;

        if (!firstCharSeen) {
            firstCharSeen = true;
            if (in == KeyEvent.KEYCODE_PLUS) {
                event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
                field.onKeyDown(in, event);
                mNoModifications = true;
                return true;
            }
            event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
            char first = event.getDisplayLabel();

            // Refer to class documentation above regarding the assumption about prefixes
            if (national.charAt(0) == first || idd.charAt(0) == first)
                mIddStartSeen = true;
            else {
                field.onKeyDown(in, event);
                mNoModifications = true;
                return false;
            }
        }
        if (mIddStartSeen) {
            event = new KeyEvent(KeyEvent.ACTION_DOWN, in);
            field.onKeyDown(in, event);

            Editable edit = field.getEditableText();
            if (edit == null)
                return false;

            String editNormalized = PhoneNumberHelper.normalizeNumber(edit.toString());
            if (TextUtils.isEmpty(editNormalized))
                return false;
            if (editNormalized.length() >= idd.length()) {
                SpannableStringBuilder replaceEdit = new SpannableStringBuilder(editNormalized);
                if (editNormalized.startsWith(idd)) {
                    replaceStart(idd, "+", replaceEdit, field);
                    return true;
                }
                else if (editNormalized.startsWith(national)) {
                    edit.clear();
                    edit.append(editNormalized);
                    replaceStart(national, FindDialHelper.getActiveCountry().countryCode, replaceEdit, field);
                    return true;
                }
            }
        }
        return false;
    }

    // Replace start of number with some other code.
    // Use sendString which sends key code to the EditText field, this triggers all
    // necessary EditText listeners and the on-the-fly formatter
    private void replaceStart(String replace, String with, Editable edit, EditText field) {
        edit.delete(0, replace.length());
        field.setText(edit);
        field.setSelection(0);
        Utilities.sendString(with, field);
        field.setSelection(field.length());
        mIddStartSeen = false;
        mNoModifications = true;
    }
}
