/*
Copyright (C) 2014-2015, Silent Circle, LLC. All rights reserved.

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
package com.silentcircle.silentphone2.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.fragments.DidSelectionFragment;

public class DidSelectionActivity extends ActionBarActivity {

    @SuppressWarnings("unused")
    private static final String TAG = "DidSelectionActivity";

//    public static final boolean testing = false;
//    private static final String TEST_DATA_REGION_1 = "{\n" +
//            "\"regions\": [\"US\"]\n" +
//            "}";
//    private static final String TEST_DATA_AREA_1 = "{\n" +
//            "\"areas\": [\"New York\"]\n" +
//            "}";
//    private static final String TEST_DATA_NUMBERS_1 = "{\n" +
//            "\"numbers\": [\"+13155559123\"]\n" +
//            "}";
//    private static final String TEST_DATA_REGION_2 = "{\n" +
//            "\"regions\": [\"US\", \"UK\"]\n" +
//            "}";
//    private static final String TEST_DATA_AREA_2 = "{\n" +
//            "\"areas\": [\"New York\", \"California\", \"Texas\", \"Florida\"]\n" +
//            "}";
//    private static final String TEST_DATA_NUMBERS_2 = "{\n" +
//            "\"numbers\": [\"+13155559123\", \"+13155550173\", \"+13155559251\", \"+13155556234\"]\n" +
//            "}";
//    private static final String TEST_DATA_NUMBERS_3 = "{\n" +
//            "\"numbers\": []\n" +
//            "}";
//
//    private static String[] TEST_RUN_1 = {TEST_DATA_REGION_1, TEST_DATA_AREA_1, TEST_DATA_NUMBERS_1};
//    private static String[] TEST_RUN_2 = {TEST_DATA_REGION_2, TEST_DATA_AREA_2, TEST_DATA_NUMBERS_2};
//    private static String[] TEST_RUN_3 = {TEST_DATA_REGION_2, TEST_DATA_AREA_2, TEST_DATA_NUMBERS_3};
//    private static String[] TEST_RUN_4 = {TEST_DATA_REGION_1, TEST_DATA_AREA_1, TEST_DATA_NUMBERS_2};
//    private static String[] TEST_RUN_5 = {TEST_DATA_REGION_2, TEST_DATA_AREA_1, TEST_DATA_NUMBERS_2};
//
//    public static String[] TEST_RUN = TEST_RUN_5;


    public static final String REGIONS = "regions";
    public static final String API_KEY = "api_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_did_selection);
        final Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        String regions;
//        if (testing)
//            regions = TEST_RUN[0];
//        else
            regions = intent.getStringExtra(REGIONS);

        String apiKey = intent.getStringExtra(API_KEY);
        if (regions == null || apiKey == null) {
            finish();
            return;
        }
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, DidSelectionFragment.newInstance(regions, apiKey))
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        showInputInfo(getString(R.string.did_ask_to_complete));
    }

    private void showInputInfo(String msg) {
        ProvisioningActivity.InfoMsgDialogFragment infoMsg = ProvisioningActivity.InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "SilentPhoneDidActivityInfo");
    }
}
