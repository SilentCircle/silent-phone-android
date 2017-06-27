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
package com.silentcircle.messaging.views;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.EditText;

public class ComposeText extends EditText {

    private static final String EXTRA_TEXT = "ComposeText.text";
    private static final String EXTRA_SELECTION_START = "ComposeText.selectionStart";
    private static final String EXTRA_SELECTION_END = "ComposeText.selectionEnd";
    private static final String EXTRA_FOCUSED = "ComposeText.focused";

    public ComposeText(Context context) {
        super(context);
    }

    public ComposeText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ComposeText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void restore(Bundle from) {

        if (from == null) {
            return;
        }

        CharSequence text = from.getCharSequence(EXTRA_TEXT);
        int selectionStart = from.getInt(EXTRA_SELECTION_START);
        int selectionEnd = from.getInt(EXTRA_SELECTION_END);
        boolean focused = from.getBoolean(EXTRA_FOCUSED, false);

        setText(text, BufferType.EDITABLE);

        if (selectionStart >= 0) {
            if (selectionEnd > selectionStart) {
                setSelection(selectionStart, selectionEnd);
            } else {
                setSelection(selectionStart);
            }
        }

        if (focused) {
            requestFocus();
        }

    }

    public void save(Bundle to) {
        CharSequence text = getText();
        to.putCharSequence(EXTRA_TEXT, text);
        to.putInt(EXTRA_SELECTION_START, getSelectionStart());
        to.putInt(EXTRA_SELECTION_END, getSelectionEnd());
        to.putBoolean(EXTRA_FOCUSED, isFocused());
    }
}
