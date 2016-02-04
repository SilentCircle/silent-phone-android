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

package com.silentcircle.silentphone2.fragments;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;

public class ProvisioningAutomatic extends Fragment implements View.OnClickListener, ProvisioningActivity.ProvisioningCallback {

    private static final String TAG = "ProvisioningVertuStep1";

    public static final String deviceAuthorization = "authorization";

    private ProvisioningActivity mParent;

    private String mDevAuthorization;

    public static ProvisioningAutomatic newInstance(Bundle args) {
        ProvisioningAutomatic f = new ProvisioningAutomatic();
        f.setArguments(args);
        return f;
    }

    public ProvisioningAutomatic() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }
        mDevAuthorization = args.getString(deviceAuthorization);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (ProvisioningActivity)activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View mVertuStepView = inflater.inflate(R.layout.provisioning_automatic, container, false);
        if (mVertuStepView == null)
            return null;
        provision();
        return mVertuStepView;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    public void provisioningDone(int provisioningResult, String result) {
        if (provisioningResult >= 0) {
            mParent.setResult(Activity.RESULT_OK);
            mParent.finish();
        }
        else {
            mParent.showErrorInfo(result);      // Show Error terminates activity after user confirmed message
        }
    }

    private void provision() {
        mParent.provisionWithAuthorization(mDevAuthorization, false, this);
    }
}
