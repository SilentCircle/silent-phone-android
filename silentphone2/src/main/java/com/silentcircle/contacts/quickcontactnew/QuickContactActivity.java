/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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

package com.silentcircle.contacts.quickcontactnew;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.silentcircle.contacts.quickcontact.QuickContactActivityV19;

/**
 * Simple Intent forwarder to support different versions/variants of the real QuickContactActivity.
 *
 * Created by werner on 01.04.15.
 */
public class QuickContactActivity extends Activity {

    @SuppressWarnings("unused")
    private static final String TAG = "QuickContactForwarder";

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        processIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        processIntent();
    }

    private void processIntent() {
        ComponentName cn;
        Intent newIntent = getIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            newIntent.setClass(this, QuickContactActivityV21.class);
//            cn = new ComponentName("com.silentcircle.silentphone",
//                    "com.silentcircle.contacts.quickcontactnew.QuickContactActivityV21");
        else
            newIntent.setClass(this, QuickContactActivityV19.class);
//            cn = new ComponentName("com.silentcircle.silentphone",
//                    "com.silentcircle.contacts.quickcontact.QuickContactActivityV19");

//        newIntent.setComponent(cn);
        try {
            startActivity(newIntent);
        } catch (Exception ignore) { }

        finish();
    }
}

