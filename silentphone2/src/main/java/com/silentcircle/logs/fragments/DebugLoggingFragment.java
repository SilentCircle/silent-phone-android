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
package com.silentcircle.logs.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.silentcircle.logs.HandleDebugLoggingTask;
import com.silentcircle.logs.activities.DebugLoggingActivity;
import com.silentcircle.silentphone2.R;

/**
 * Created by rli on 1/12/16.
 */
public class DebugLoggingFragment extends Fragment implements View.OnClickListener {

    public static final String TAG = "DebugLoggingFragment";
    public static final String INPUT_DESCRIPTION = "input_description";
    public static final String LOGGING_TASK_DONE_RECEIVER = "Logging_Task_Done_Receiver";

    private EditText mInput;
    private Button mSendBtn;
    public static boolean enableSendBtn = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState ) {
        View v = inflater.inflate(R.layout.debug_logging_fragment, container, false);

        mSendBtn = (Button)v.findViewById( R.id.send_logs_btn_id );
        mSendBtn.setOnClickListener( this );
        if(enableSendBtn){
            mSendBtn.setEnabled(true);
            mSendBtn.setTextColor(Color.WHITE);
        }else {
            mSendBtn.setEnabled(false);
            mSendBtn.setTextColor(Color.DKGRAY);
        }

        mInput = (EditText) v.findViewById( R.id.description_of_problem_et_id );
        Bundle args =  getArguments();
        if(args != null){
            mInput.setText(args.getString(DebugLoggingFragment.INPUT_DESCRIPTION));
        }

        return v;
    }

    @Override
    public void onClick( View v ) {
        enableSendBtn = false;
        mSendBtn.setEnabled(false);
        mSendBtn.setTextColor(Color.DKGRAY);
        ((DebugLoggingActivity)getActivity()).startProgress(R.string.decrypting);
        ((DebugLoggingActivity)getActivity()).getAsyncTaskExecutor().submit(HandleDebugLoggingTask.DEBUG_LOGS_TASK, new HandleDebugLoggingTask((AppCompatActivity) getActivity()), HandleDebugLoggingTask.DEBUG_LOGS_TASK, mInput.getText().toString());
    }

    public void enabelSendButton() {
        enableSendBtn = true;
        mSendBtn.setEnabled(true);
        mSendBtn.setTextColor(Color.WHITE);
    }
}
