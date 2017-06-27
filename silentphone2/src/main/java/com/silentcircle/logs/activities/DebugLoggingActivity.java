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
package com.silentcircle.logs.activities;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import com.silentcircle.contacts.utils.AsyncTaskExecutor;
import com.silentcircle.contacts.utils.AsyncTaskExecutors;
import com.silentcircle.logs.fragments.DebugLoggingDialogFragment;
import com.silentcircle.logs.fragments.DebugLoggingFragment;
import com.silentcircle.silentphone2.R;

/**
 * Created by rli on 1/12/16.
 */
public class DebugLoggingActivity extends AppCompatActivity {
    public static final String TAG = "DebugLoggingActivity";
    public static final String CURRENT_FRAGMENT_TAG = "current_fragment_tag";

    private AsyncTaskExecutor mAsyncTaskExecutor;
    private DebugLoggingDialogFragment mProgressFragment;

    private FragmentManager mFragmentManager;
    private Fragment mFragment;
    private FragmentTransaction mFragmentTransaction;

    private String mCurFragTag;

    private boolean isSaveState = false;
    private boolean isDestroyed = false;
    private boolean isProgressStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_logging);

        mAsyncTaskExecutor = AsyncTaskExecutors.createThreadPoolExecutor();

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setTitle(getString(R.string.send_logs));

        mFragmentManager = getSupportFragmentManager();
        mFragmentTransaction = mFragmentManager.beginTransaction();

        // if device rotated, savedInstanceState != null
        if( savedInstanceState == null) {
            mCurFragTag = DebugLoggingFragment.TAG;
            mFragmentTransaction.replace(R.id.fragment_logging, new DebugLoggingFragment(), DebugLoggingFragment.TAG).commit();
        }

    }

    public AsyncTaskExecutor getAsyncTaskExecutor(){
        return mAsyncTaskExecutor;
    }

    /**
     *
     * @param resId: the message shown in progress
     */
    public void startProgress(int resId) {
        isProgressStarted = true;
        if(isDestroyed){
            return;
        }
        setRequestedOrientation(this.getResources().getConfiguration().orientation);
        if(mProgressFragment == null) {
            mProgressFragment = DebugLoggingDialogFragment.newInstance(DebugLoggingDialogFragment.PROGRESS_DIALOG, getString(resId));
            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager != null) {
                fragmentManager.beginTransaction()
                        .add(mProgressFragment, DebugLoggingDialogFragment.PROGRESS_DIALOG)
                        .commitAllowingStateLoss();
            }
        }
        mProgressFragment.setProgressMessage(getString(resId));
    }

    public void updateProgressMessage(int resId){
        if(mProgressFragment != null){
            mProgressFragment.setProgressMessage(getString(resId));
        }
    }

    public void stopProgress() {
        isProgressStarted = false;
        if(isSaveState){
            return;
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if(mProgressFragment != null) {
            mProgressFragment.dismiss();
            mProgressFragment = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isSaveState = false;
        if(!isProgressStarted && mProgressFragment != null){
            mProgressFragment.dismiss();
            mProgressFragment = null;
        }
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        mProgressFragment = null;
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        isSaveState = true;
    }
}
