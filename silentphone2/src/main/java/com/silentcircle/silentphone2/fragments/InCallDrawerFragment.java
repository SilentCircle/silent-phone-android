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

package com.silentcircle.silentphone2.fragments;

import com.silentcircle.silentphone2.activities.InCallActivity;

import android.app.Activity;
import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class InCallDrawerFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = InCallDrawerFragment.class.getSimpleName();

    public static final int SHOW_SECURITY_DETAIL = 1;
    public static String ECHO_CANCEL_SWITCHING = "echo_cancel_switching";

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private DrawerCallbacks mCallbacks;
    private InCallActivity mParent;

    private DrawerLayout mDrawerLayout;
    private ScrollView mDrawerView;
    private ActionBarDrawerToggle mDrawerToggle;

    private View mFragmentContainerView;

//    private TextView mDuration;
    private TextView mQuality;
    private TextView mQualityDetails;

    private boolean mShowQualityDetails;

    /** If drawer is open we do some regular updates, for example display of call duration time every second */
    private boolean mOpenState;

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public static interface DrawerCallbacks {
        public static int OPENED = 1;
        public static int CLOSED = 2;
        public static int MOVING = 3;

        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onDrawerItemSelected(int type, Object... params);
        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onDrawerStateChange(int action);
    }

    public InCallDrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerView = (ScrollView)inflater.inflate(R.layout.incall_drawer, container, false);

        return mDrawerView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (InCallActivity)activity;
        try {
            mCallbacks = (DrawerCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement DrawerCallbacks.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
        mCallbacks = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.echo_checkbox:
            case R.id.echo_title:
                toggleEchoOptionCheck();
                break;
            default:
                break;
        }
    }

    /**
     * Users of this fragment must call this method to set up the drawer interactions.
     *
     * Currently no specific Action bar settings
     *
     * @param drawerView   The view of this fragment in its activity's layout.
     * @param drawerLayout The DrawerLayout containing this fragment's UI.
     */
    public void setUp(View drawerView, DrawerLayout drawerLayout) {
        mFragmentContainerView = drawerView;
        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(Utilities.mDrawerShadowId, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        ActionBar actionBar = mParent.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        mDrawerToggle = new ActionBarDrawerToggle(
                mParent,                    /* host Activity */
                mDrawerLayout,              /* DrawerLayout object */
                R.string.navigation_drawer_open,   /* "open drawer" description for accessibility */
                R.string.navigation_drawer_close   /* "close drawer" description for accessibility */
        ) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }

                mParent.invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                mOpenState = false;
                if (mCallbacks != null)
                    mCallbacks.onDrawerStateChange(DrawerCallbacks.CLOSED);

            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
                if (!mOpenState &&
                        (newState == DrawerLayout.STATE_SETTLING || newState == DrawerLayout.STATE_DRAGGING) && mCallbacks != null)
                    mCallbacks.onDrawerStateChange(DrawerCallbacks.MOVING);

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) {
                    return;
                }
                mParent.invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                mOpenState = true;
                setupCallInfo();
                setupCallSecurityInfo();
                prepareEchoOptionCheck();
                if (mCallbacks != null)
                    mCallbacks.onDrawerStateChange(DrawerCallbacks.OPENED);
            }
        };

        // Defer code dependent on restoration of previous instance state.
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });
        mDrawerLayout.setDrawerListener(mDrawerToggle);
    }

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }

    public void setupCallSecurityInfo() {
        final CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;

        View sdes = mDrawerView.findViewById(R.id.sdes_info);
        if (!call.sdesActive)
            sdes.setVisibility(View.GONE);
        else {
            sdes.setVisibility(View.VISIBLE);
        }

        View zrtp = mDrawerView.findViewById(R.id.zrtp_info);
        View zrtpName = mDrawerView.findViewById(R.id.zrtp_peer_name);

        // If SAS is not empty then we have a ZRTP connection
        if (!TextUtils.isEmpty(call.bufSAS.toString())) {
            TextView txtView = (TextView)mDrawerView.findViewById(R.id.zrtp_info_data);
            zrtp.setVisibility(View.VISIBLE);
            if (call.iShowVerifySas)                // need a verification
                txtView.setText(getString(R.string.in_call_drawer_zrtp_not_confirmed));
            else
                txtView.setText(getString(R.string.in_call_drawer_zrtp_confirmed));

            // Check if we should display peer name and make it editable
            if (!TextUtils.isEmpty(call.zrtpPEER.toString()) && !call.iShowVerifySas) {
                zrtpName.setVisibility(View.VISIBLE);
                txtView = (TextView)mDrawerView.findViewById(R.id.zrtp_peer_name_data);
                txtView.setText(call.zrtpPEER.toString());
                txtView.setSelected(true);

                Button editPeer = (Button)mDrawerView.findViewById(R.id.zrtp_peer_name_btn);
                editPeer.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mParent.verifySasCb(call.bufSAS.toString());
                    }
                });
            }
            else {
                zrtpName.setVisibility(View.GONE);
            }
        }
        else {
            zrtp.setVisibility(View.GONE);
            zrtpName.setVisibility(View.GONE);
        }
        TextView txtTls = (TextView)mDrawerView.findViewById(R.id.tls_info_data);
        String tlsInfo = TiviPhoneService.getInfo(call.iEngID, -1, ".sock");
        txtTls.setText(tlsInfo);   //socket info, tls ciphers or udp, tcp;
        txtTls.setSelected(true);

        mDrawerView.findViewById(R.id.security_detail).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallbacks.onDrawerItemSelected(SHOW_SECURITY_DETAIL);
            }
        });
        String buildInfo = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.zrtp.buildInfo");
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "++++ ZRTP build information: " + buildInfo);
    }

    private void setupCallInfo() {
        mQuality = (TextView)mDrawerView.findViewById(R.id.quality_info_data);
        mQualityDetails = (TextView)mDrawerView.findViewById(R.id.quality_info_detail_data);
        if (mOpenState)
            mQuality.postDelayed(mUpdate, 1000);
        Button qualityDetail = (Button)mDrawerView.findViewById(R.id.quality_info_detail);
        qualityDetail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowQualityDetails = !mShowQualityDetails;
                if (mShowQualityDetails)
                    mQualityDetails.setVisibility(View.VISIBLE);
                else
                    mQualityDetails.setVisibility(View.GONE);
            }
        });
    }

    private Runnable mUpdate = new Runnable() {
        @Override
        public void run() {
            if (mOpenState) {
                setAntennaInfo();
                mDrawerView.postDelayed(mUpdate, 1000);
            }
        }
    };

    private void setAntennaInfo() {
        CallState call = TiviPhoneService.calls.selectedCall;

        if (call == null || call.uiStartTime == 0) {
            return;
        }
        String info = TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.bars");
        if (TextUtils.isEmpty(info))
            return;
        char antenna = info.charAt(0);
        switch (antenna) {
            case '0':
                mQuality.setText(R.string.call_quality_bad);
                break;
            case '1':
                mQuality.setText(R.string.call_quality_weak);
                break;
            case '2':
                mQuality.setText(R.string.call_quality_acceptable);
                break;
            case '3':
                mQuality.setText(R.string.call_quality_good);
                break;
            case '4':
                mQuality.setText(R.string.call_quality_very_good);
                break;
            default:
                break;
        }
        if (mShowQualityDetails)
            mQualityDetails.setText(TiviPhoneService.getInfo(call.iEngID, call.iCallId, "media.codecs"));
    }

    /*
 *** Option to monitor call from native contact application
 */
    private boolean mEchoSwitchCheck;
    private CheckBox mEchoSwitchBox;

    private void prepareEchoOptionCheck() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mEchoSwitchCheck = prefs.getBoolean(ECHO_CANCEL_SWITCHING, true);

        mEchoSwitchBox = (CheckBox)mDrawerView.findViewById(R.id.echo_checkbox);
        mEchoSwitchBox.setOnClickListener(this);
        mEchoSwitchBox.setChecked(mEchoSwitchCheck);

        TextView tv = (TextView)mDrawerView.findViewById(R.id.echo_title);
        tv.setOnClickListener(this);
        String longText = getString(R.string.setting_echo_option_summary) + 
                (DeviceHandling.mEchoCancelerActive ? " (AEC)." : ".");
        tv.setText(longText);
    }

    private void toggleEchoOptionCheck() {
        mEchoSwitchCheck = !mEchoSwitchCheck;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(ECHO_CANCEL_SWITCHING, mEchoSwitchCheck).apply();
        mEchoSwitchBox.setChecked(mEchoSwitchCheck);
        DeviceHandling.setAecMode(mParent, Utilities.isSpeakerOn(mParent));

        TextView tv = (TextView)mDrawerView.findViewById(R.id.echo_title);
        String longText = getString(R.string.setting_echo_option_summary) +
                (DeviceHandling.mEchoCancelerActive ? " (AEC)." : ".");
        tv.setText(longText);
    }
}
