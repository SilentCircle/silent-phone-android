/*
Copyright © 2012-2013, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.silentphone.fragments;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.silentcircle.silentphone.R;
import com.silentcircle.silentphone.TiviPhoneService;
import com.silentcircle.silentphone.activities.TMActivity;
import com.silentcircle.silentphone.utils.CTFlags;
import com.silentcircle.silentphone.utils.TBuild;
import com.silentcircle.silentphone.utils.Utilities;

public class InfoFragment extends SherlockFragment {

    /* *********************************************************************
     * Handle information and thanks dialog. Maybe we need to extend this to implement
     * account selection - later. 
     * ******************************************************************* */
    private static String buildInfo = TBuild.T_BUILD_NR;
    private static String deviceInfo = Build.MANUFACTURER + ", " + Build.BRAND + ", " + Build.MODEL + ", " + Build.DEVICE;
    
    private static String[] screenSizes = {"Undefined", "Small", "Normal", "Large", "XLarge"};

    private TextView numberText;
    private TextView infoText;
    private View infoView;

    private boolean showDebug;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        infoView = inflater.inflate(R.layout.dialog_thanks_info, null);

        // catch clicks here. Avoids that they fall through to call screen and trigger unwanted actions.
        infoView.findViewById(R.id.InfoLogoView).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            }
        });

        numberText = (TextView) infoView.findViewById(R.id.ThanksSCnumber);
        numberText.setText(CTFlags.formatNumber(Utilities.getTCValue("edNR")));

        infoText = (TextView) infoView.findViewById(R.id.InfoBuildNumberInfo);
        infoText.setText(buildInfo);
        infoText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showDebug = !showDebug;
                showDebugInfo();
            }
        });
        infoText.setClickable(true);

        infoText = (TextView) infoView.findViewById(R.id.InfoDeviceInfoInfo);
        infoText.setText(deviceInfo);

        Resources res = getResources();
        Configuration config = res.getConfiguration();
        int idx = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        String classification = getString(R.string.dpi_classified_as) + ", " + screenSizes[idx] + ", API: "
                + android.os.Build.VERSION.SDK_INT;
        infoText = (TextView) infoView.findViewById(R.id.InfoDeviceClassInfo);
        infoText.setText(classification);

        if (TMActivity.SP_DEBUG) {
            showDebug = true;
            showDebugInfo();
        }
        return infoView;
    }

    private void showDebugInfo() {
        if (showDebug) {
            infoView.findViewById(R.id.InfoDeviceInfoInfo).setVisibility(View.VISIBLE);
            infoView.findViewById(R.id.InfoDeviceClass).setVisibility(View.VISIBLE);
            infoView.findViewById(R.id.InfoDeviceClassInfo).setVisibility(View.VISIBLE);
        }
        else {
            infoView.findViewById(R.id.InfoDeviceInfoInfo).setVisibility(View.INVISIBLE);
            infoView.findViewById(R.id.InfoDeviceClass).setVisibility(View.INVISIBLE);
            infoView.findViewById(R.id.InfoDeviceClassInfo).setVisibility(View.INVISIBLE);
        }
    }
}
