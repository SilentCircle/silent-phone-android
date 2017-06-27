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

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Point;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;

import org.json.JSONObject;

/**
 * View to show debug details
 */
public class DebugInformationView extends LinearLayout {

    private TextView mTextView;
    private Button mButtonToggleJSON;
    private ScrollView mContainer;

    private String mLabelledString;
    private String mJsonString;

    public DebugInformationView(Context context) {
        this(context, null);
    }

    public DebugInformationView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DebugInformationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inflate(context, R.layout.debug_information, this);
        initializeViews(null, null);
    }

    public DebugInformationView(Context context, String labeledString, JSONObject json) {
        super(context, null, 0);
        inflate(context, R.layout.debug_information, this);
        initializeViews(labeledString, json);
    }

    protected void initializeViews(final String labeledString, final JSONObject json) {
        mTextView = (TextView) findViewById(R.id.debug_information);
        mButtonToggleJSON = (Button) findViewById(R.id.debug_information_toggle);
        mContainer = (ScrollView) findViewById(R.id.debug_information_container);

        mLabelledString = labeledString;
        try {
            mJsonString = json.toString(4);
        }
        catch (Exception e) {
            mJsonString = json.toString();
        }

        Point point = new Point();
        ViewUtil.getScreenDimensions(getContext(), point);

        ViewGroup.LayoutParams params = mContainer.getLayoutParams();
        params.height = (int) (point.y * 0.6);

        mTextView.setText(labeledString);
        mTextView.setLineSpacing(0.0f, 1.2f);
        mTextView.setPadding(30, 10, 10, 30);

        mTextView.setTextIsSelectable(true);
        mTextView.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                ClipboardManager manager =
                        (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText(null, ((TextView) v).getText()));
                Toast.makeText(v.getContext(),
                        R.string.toast_copied_to_clipboard,
                        Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        mButtonToggleJSON.setOnClickListener(new View.OnClickListener() {

            boolean mToggle = false;

            @Override
            public void onClick(View v) {
                // toggle between field view and JSON
                mTextView.setText(mToggle ? mLabelledString : mJsonString);
                mToggle = !mToggle;
            }
        });

    }
}
