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

package com.silentcircle.silentphone2.activities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.CryptoInfoDialog;
import com.silentcircle.silentphone2.dialogs.VerifyDialog;
import com.silentcircle.silentphone2.dialogs.ZrtpMessageDialog;
import com.silentcircle.silentphone2.fragments.CallManagerFragment;
import com.silentcircle.silentphone2.fragments.DtmfDialerFragment;
import com.silentcircle.silentphone2.fragments.InCallDrawerFragment;
import com.silentcircle.silentphone2.fragments.InCallMainFragment;
import com.silentcircle.silentphone2.fragments.InCallVideoFragmentHw;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.ManageCallStates;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;

/**
 * This activity handles all in-call events.
 *
 * Created by werner on 13.02.14.
 */
public class InCallActivity extends AppCompatActivity
        implements InCallDrawerFragment.DrawerCallbacks, TiviPhoneService.ServiceStateChangeListener,
        TiviPhoneService.DeviceStateChangeListener, InCallCallback,
        SensorEventListener {

    private static final String TAG = InCallActivity.class.getSimpleName();

    // Which states we need to save in case of rotation
    private static final String IS_MUTED = "mIsMuted";
    private static final String HIDE_AFTER = "mHideAfter";

    private static final String DTMF = "dtmf";
    private static final String CALL_MANAGER = "call_manager";
    private static final String MAIN = "main";
    private static final String VIDEO_ACTIVE = "video_active";
    private static final String PROXIMITY_WAKEUP = "proximity_wakeup";

    public static final String ADD_CALL_ACTION = "add_call";

    public static final String NOTIFICATION_LAUNCH_ACTION = "notification_launch";
    public static final String NOTIFICATION_END_CALL_ACTION = "notification_end_call";

    public static final int ACTIVE_CALL_NOTIFICATION_ID = 47110815;
    private static final int ADD_CALL_ACTIVITY = 1113;

    private NotificationManager mNotificationManager;
    /**
     * Fragment managing the some content related to the in-call activity.
     */
    private InCallDrawerFragment mInCallDrawerFragment;

    private Thread callMonitoringThread;

    private InCallMainFragment mInCallMain;
    private InCallVideoFragmentHw mVideoFragment;
    private CallManagerFragment mCallManagerFragment;
    private DtmfDialerFragment mDtmfDialer;

    private boolean mIsMuted;

    /** True if we set to mute before we start DTMF dialer */
    private boolean mDtmfSetToMute;

    /**
     * Variable controls the delay when to close the call window after a call ended.
     * If set to -1 then call is still active and the monitoring thread ignores the delay.
     * The endCall function sets this variable to some delay value greater zero to control
     * the delay. The call monitoring thread performs then delay handling.
     */
    private int mHideAfter = -1;

    /** If activity is in foreground, controls timer in monitoring thread */
    private boolean mIsForeground = true;

    /** True if a VideoFragment is active */
    private boolean mVideoActive;

    private final Collection<TiviPhoneService.ServiceStateChangeListener> callStateListeners = new LinkedList<>();
    private final Collection<TiviPhoneService.DeviceStateChangeListener> deviceStateListeners = new LinkedList<>();

    /** If true leave hint detected, controls proximity wake-lock handling */
    private boolean mLeaveHint;

    // Some hardware stuff
    private SensorManager mSensorManager;

    private static PowerManager.WakeLock mProximityWakeLock;
    private final Object mProximitySync = new Object();

    /** {@code true} if we already display an ZRTP error/warning dialog */
    private boolean mZrtpErrorShowing;

    /** Only used in error condition. If {@code true} onDestroy does not cleanup */
    private boolean mForceDestroy;

    // Set to true in case the user terminates the call very early and we have no call state yet.
    // In this case terminate the call as soon as we have a valid call state.
    private boolean mTerminateEarly;

    private static Bundle mSaveForRestart;

    private boolean mDestroyed;

    private Toolbar mToolbar;

    /* *********************************************************************
     * Functions and variables to bind this activity to the TiviPhoneService
     * This uses standard Android mechanism.
     * ******************************************************************* */
    private boolean mPhoneIsBound;
    private TiviPhoneService mPhoneService;

    private ServiceConnection phoneConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mPhoneService = ((TiviPhoneService.LocalBinder)service).getService();
            mPhoneIsBound = true;
            if (mDestroyed) {
                doUnbindService();
                return;
            }
            mPhoneService.addStateChangeListener(InCallActivity.this);
            mPhoneService.addDeviceChangeListener(InCallActivity.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mPhoneService = null;
            mPhoneIsBound = false;
        }
    };

    private void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(InCallActivity.this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService() {
        if (mPhoneIsBound) {
            // Detach our existing connection.
            unbindService(phoneConnection);
            mPhoneIsBound = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == ADD_CALL_ACTIVITY) {
            boolean startCallManager = TiviPhoneService.calls.getCallCount() > 1;
            if (resultCode != RESULT_OK) {
                CallState call = TiviPhoneService.calls.selectedCall;
                mInCallMain.showCall(call);
                mInCallMain.showSecurityFields(call);
                if (call != null && call.iIsOnHold && !startCallManager) {
                    call.iIsOnHold = false;
                    TiviPhoneService.doCmd("*u" + call.iCallId);
                }
                if (startCallManager)
                    startCallManager();
            }
        }
    }

    /*
     * OnCreate initializes only some required resources and then starts/binds the
     * phone service. The method 'serviceBound' then sets up the UI relevant stuff,
     * initializes the drawer, in-call fragments, etc.
     */
    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstanceState) {
        Utilities.setTheme(this);
        super.onCreate(savedInstanceState);

        ViewUtil.setBlockScreenshots(this);
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Intent onCreate: " + getIntent());

        Bundle bundle = getIntent().getExtras();
        if (bundle == null && getIntent().getAction() == null) {
            mForceDestroy = true;
            finish();
            return;
        }

        onNewIntent(getIntent());

        // If this is not an onCreate during a rotation then fix orientation to
        // portrait for small devices. The video fragment enables rotation and thus
        // we can have an onCreate here, however in this case to force back to portrait.
        if (!Utilities.isTablet(this) && savedInstanceState == null)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // This is a phone call screen, thus perform some specific handling
        int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        windowFlags |= WindowManager.LayoutParams.FLAG_IGNORE_CHEEK_PRESSES;
        windowFlags |= WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
        windowFlags |= WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

        getWindow().addFlags(windowFlags);

        boolean startVideo = false;
        if (savedInstanceState == null && mSaveForRestart != null) {
            savedInstanceState = mSaveForRestart;
            startVideo = savedInstanceState.getBoolean(VIDEO_ACTIVE, false);
        }
        mSaveForRestart = null;

        PowerManager powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
        int lockValue = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? 
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK : 0x00000020;

        if (mProximityWakeLock == null) {
            mProximityWakeLock =
                    powerManager.newWakeLock(lockValue, "silentphone_proximity_off_wake");
        }
        // Preliminary set the mPhoneService
        if (mPhoneService == null)
            mPhoneService = TiviPhoneService.phoneService;
        doBindService();        // Bind the TiviPhoneService, only locally, this will set mPhoneService correctly

        if (savedInstanceState != null) {
            mIsMuted = savedInstanceState.getBoolean(IS_MUTED);
            mHideAfter = savedInstanceState.getInt(HIDE_AFTER);
            updateProximitySensorMode(savedInstanceState.getBoolean(PROXIMITY_WAKEUP));
        }

        // Proximity sensor to switch off video
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor proximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        // Register here, not on onStop or onPause, de-register on onDestroy. Otherwise Video control
        // does not work correctly.
        mSensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL);

        /*
         * This is a setting to enable this for earlier Android versions, this includes
         * the 3 commented code lines. We may enhance this after Gingerbread support is gone.
         */
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        setupMonitoringThread();

        setContentView(R.layout.activity_incall);

        // Set up the drawer.
        mInCallDrawerFragment = (InCallDrawerFragment)getFragmentManager().findFragmentById(R.id.incall_content_drawer);
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        View drawerView = findViewById(R.id.incall_content_drawer);
        mInCallDrawerFragment.setUp(drawerView, drawerLayout);

        mToolbar = (Toolbar)findViewById(R.id.in_call_toolbar);
        setSupportActionBar(mToolbar);

        // If framework restarted activity, for example after a rotation, than the fragment manager
        // keeps track of existing and known fragments. Get them here. FragmentManager also restarts
        // the fragments with their saved parameters, new layouts etc.
        // We only create the InCallMain fragment here if it is not available. The activity creates
        // other fragments on demand according to user actions.
        FragmentManager fm = getFragmentManager();

        mVideoFragment = (InCallVideoFragmentHw)fm.findFragmentByTag("com.silentcircle.silentphone.video");
        if (mVideoFragment != null) {
            getSupportActionBar().hide();
            mVideoActive = true;
        }

        mCallManagerFragment = (CallManagerFragment)fm.findFragmentByTag("com.silentcircle.silentphone.callManager");
        mDtmfDialer = (DtmfDialerFragment)fm.findFragmentByTag("com.silentcircle.silentphone.dtmfDialer");

        boolean dtmf = false;
        boolean callManager = false;
        boolean main = false;
        if (savedInstanceState != null) {
            dtmf = savedInstanceState.getBoolean(DTMF);
            callManager = savedInstanceState.getBoolean(CALL_MANAGER);
            main = savedInstanceState.getBoolean(MAIN);
        }
        FragmentTransaction  ft = fm.beginTransaction();
        mInCallMain = (InCallMainFragment)fm.findFragmentByTag("com.silentcircle.silentphone.inCall");
        if (mInCallMain == null ) {
            mInCallMain = new InCallMainFragment();
            mInCallMain.setArguments(bundle);           // Bundle contains call type, name/number
            ft.replace(R.id.main_call, mInCallMain, "com.silentcircle.silentphone.inCall");
        }
        else {
            // Remove the mDialerFragment if it's not required to avoid display of soft keyboard
            // Never remove call manager fragment - it stores some own states which would be lost
            if (mVideoActive) {
                ft.hide(mInCallMain);
            }
            if (dtmf) {
                ft.hide(mInCallMain);
                hideFragment(ft, mCallManagerFragment);
            }
            if (callManager) {
                ft.hide(mInCallMain);
                hideFragment(ft, mDtmfDialer);
            }
            if (main || mVideoActive) {
                hideFragment(ft, mCallManagerFragment);
                hideFragment(ft, mDtmfDialer);
            }
        }
        if (!ft.isEmpty()) {
            ft.commitAllowingStateLoss();
        }
        if (isVolumeToLow()) {
            findViewById(R.id.low_volume).setVisibility(View.VISIBLE);
        }
        callMonitoringThread.start();
        if (startVideo && TiviPhoneService.calls.getCallCount() >= 1)
            activateVideo(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsForeground = true;
        if (TiviPhoneService.calls.getCallCount() >= 1 && !TiviPhoneService.calls.selectedCall.mustShowAnswerBT()) {
            mNotificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID);
        }
        mLeaveHint = false;
        updateProximitySensorMode(true);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        mLeaveHint = true;
        updateProximitySensorMode(false);
        if (TiviPhoneService.calls.getCallCount() >= 1)
            setCallActiveNotification(TiviPhoneService.calls.selectedCall);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsForeground = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // onDestroy: usually only the monitoring thread finishes this activity, thus it's
    // not active anymore and also the call count is zero. In case the system terminates
    // this activity make sure to end all active calls to avoid open communication.
    @Override
    protected void onDestroy() {
        
        mDestroyed = true;
        super.onDestroy();
        if (TiviPhoneService.calls.getCallCount() > 0 && callMonitoringThread != null && callMonitoringThread.isAlive()) {
            mMonitorStopLatch = new CountDownLatch(1);
        }
        else if(mNotificationManager != null) {
            mNotificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID);
        }

        if(mPhoneService != null) {
            mPhoneService.removeStateChangeListener(this);
            mPhoneService.removeDeviceChangeListener(this);
            mPhoneService.stopRinger();
            mPhoneService.setNotificationToDialer();
        }

        waitForLatch(mMonitorStopLatch);
        mMonitorStopLatch = null;
        if (mForceDestroy)
            return;
        mSensorManager.unregisterListener(this);

        new Thread() {
            @Override
            public void run() {
                Utilities.restoreSpeakerMode(getApplicationContext());
                Utilities.audioMode(getApplicationContext(), false);
            }
        }.start();

        updateProximitySensorMode(false);
        doUnbindService();
    }

    @Override
    public void onSaveInstanceState (@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(IS_MUTED, mIsMuted);
        outState.putInt(HIDE_AFTER, mHideAfter);
        outState.putBoolean(DTMF, isDtmfDialerShown());
        outState.putBoolean(CALL_MANAGER, isCallManagerShown());
        outState.putBoolean(MAIN, (mInCallMain != null && !mInCallMain.isHidden()));
        outState.putBoolean(PROXIMITY_WAKEUP, mProximityWakeLock.isHeld());

        Bundle save = new Bundle();
        save.putBoolean(IS_MUTED, mIsMuted);
        save.putInt(HIDE_AFTER, mHideAfter);
        save.putBoolean(DTMF, isDtmfDialerShown());
        save.putBoolean(CALL_MANAGER, isCallManagerShown());
        save.putBoolean(MAIN, (mInCallMain != null && !mInCallMain.isHidden()));
        save.putBoolean(VIDEO_ACTIVE, mVideoActive);
        save.putBoolean(PROXIMITY_WAKEUP, mProximityWakeLock.isHeld());
        mSaveForRestart = save;

    }

    // We handle the back button here and do not use the fragment manager's back stack.
    // It's simpler to handle the in-call main fragment and its buttons and states. Otherwise
    // we would need to simulate the call events (to a certain degree) to update the
    // in-call fragment.
    // The function ignores back pressed if video is active - it does not switch
    // fragments in this case.
    @Override
    public void onBackPressed() {
    // Do not terminate this activity ? -> or perform endCall?
    // super.onBackPressed();

        boolean startCallManager = false;
        FragmentTransaction ft = null;
        FragmentManager fm = getFragmentManager();
        // User pressed back button while in call manager fragment. Show main in-call
        // fragment. No need to update fields in this case, call manager does not change
        // the in-call fragment state.
        if (isCallManagerShown()) {
            ft = fm.beginTransaction();
            ft.hide(mCallManagerFragment);

            mInCallMain.showCall(TiviPhoneService.calls.selectedCall);
            mInCallMain.refreshSecurityFields(TiviPhoneService.calls.selectedCall);
        }

        // User pressed back while in DTMF dialer
        if (isDtmfDialerShown()) {
            if (ft == null)
                ft = fm.beginTransaction();
            ft.hide(mDtmfDialer);
            mDtmfDialer.clearDialedDigits();
            mInCallMain.refreshScreen();
            if (mDtmfSetToMute) {
                mDtmfSetToMute = false;
                TiviPhoneService.doCmd(":mute 0");
            }
            startCallManager = TiviPhoneService.calls.getCallCount() > 1;
        }
        if (ft != null && !isFinishing()) {
            ft.show(mInCallMain).commitAllowingStateLoss();
            if (startCallManager)
                startCallManager();
            else {
                fm.executePendingTransactions();    // execute now to have correct state when re-creating menu
                invalidateOptionsMenu();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if(intent != null && intent.getAction() != null) {
            switch(intent.getAction()) {
                case NOTIFICATION_LAUNCH_ACTION:
                    break;

                case NOTIFICATION_END_CALL_ACTION:
                    endCall(true, TiviPhoneService.calls.selectedCall);
                    break;
            }
        }

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received onNewIntent: " + intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            // The drawer does not modify action bar settings, thus no need to
            // restore them too.
            getMenuInflater().inflate(R.menu.incall, menu);
            ViewUtil.tintMenuIcons(this, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        CallState call = TiviPhoneService.calls.selectedCall;

        // Some other action screen active?
        boolean otherScreenShown = isCallManagerShown() || isDtmfDialerShown();

        // We display CM button if we have more than one call, and no other screens on display (only main screen)
        boolean showCM = TiviPhoneService.calls.getCallCount() > 1 && !otherScreenShown;
        MenuItem item = menu.findItem(R.id.call_manager);
        if (item != null)
            item.setVisible(showCM);

        // Show DTMF dialer button if:
        // call is active (answered), video not active, and no other screen on display
        boolean showDtmf = call != null && !call.mustShowAnswerBT() && !mVideoActive && !otherScreenShown;
        item = menu.findItem(R.id.dtmf_dialer);
        if (item != null)
            item.setVisible(showDtmf);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /*
         * OpenDialDrawer is a bit tricky: To avoid flickering first prepare the dialpad
         * layout. Then delay the call to open the drawer which gives some time to remove
         * the keyboard (slide in), perform necessary layout changes, before the drawer
         * opens.
         *
         * No such issues during drawer close. Just close it, after it closed we use
         * the state change to slide in the soft keyboard {@see onDrawerStateChange}
         */
        switch (item.getItemId()) {
            case R.id.call_manager:
                startCallManager();
                item.setVisible(false);
                return true;

            case R.id.dtmf_dialer:
                startDtmfDialer();
                item.setVisible(false);
                return true;

            // The DTMF dialer adds this to the menu
//            case R.id.dtmf_menu_contacts:
//                if (mPhoneIsBound)
//                    Utilities.doLaunchContactPickerIgnoreResult(this);
//                return true;

            // Call Manager adds the following menu item
            case R.id.call_manager_add_call:
                if (mPhoneIsBound) {
                    onBackPressed();
                    mInCallMain.doAddCall(TiviPhoneService.calls.selectedCall);
                }
                return true;

            case R.id.call_manager_mic:
                mIsMuted = !mIsMuted;
                TiviPhoneService.doCmd(mIsMuted ? ":mute 1" : ":mute 0");
                invalidateOptionsMenu();
                break;

            case R.id.call_manager_speaker:
                if (getPhoneService().btHeadsetScoActive()) {
                    return true;
                }
                final boolean speakerOnly = getResources().getBoolean(R.bool.has_speaker_only);
                final boolean isSpeakerOn = speakerOnly || !Utilities.isSpeakerOn(getBaseContext());
                Utilities.turnOnSpeaker(getBaseContext(), isSpeakerOn, true);
                invalidateOptionsMenu();
                break;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean dispatchKeyEvent (@NonNull KeyEvent event) {
        CallState call = TiviPhoneService.calls.selectedCall;

        if (call == null)
            return super.dispatchKeyEvent(event);

        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN && !call.iActive && call.iIsIncoming) {
                    mPhoneService.stopRinger();
                    return true;
                }
                if (action == KeyEvent.ACTION_UP) {
                    if (isVolumeToLow()) {
                        findViewById(R.id.low_volume).setVisibility(View.VISIBLE);
                    }
                }
                break;

            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_UP) {
                    if (!isVolumeToLow()) {
                        findViewById(R.id.low_volume).setVisibility(View.GONE);
                    }
                }
                break;

            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (action == KeyEvent.ACTION_DOWN) {
                    if (call.mustShowAnswerBT())
                        answerCall(true);
                    else
                        endCall(true, call);
                }
                return true;

            default:
                break;
        }
        return super.dispatchKeyEvent(event);
    }

    /*
     * InCall drawer callbacks
     */

    @Override
    public void onDrawerItemSelected(int type, Object... params) {
        if (type == InCallDrawerFragment.SHOW_SECURITY_DETAIL) {
            FragmentManager fragmentManager = getFragmentManager();
            CryptoInfoDialog cryptoScreen = CryptoInfoDialog.newInstance();
            cryptoScreen.show(fragmentManager, "crypto_dialog");
        }
    }

    @Override
    public void onDrawerStateChange(int action) {}

    /* *******************************************************************
     * Call and device state change listeners
     ******************************************************************* */

    /**
     * The ZRTP state change listener.
     *
     * This listener runs in the context of the TiviPhoneService thread. To perform some
     * activities on the UI you must use <code>runOnUiThread(new Runnable() { ... }</code>
     *
     * @param call the call that changed its ZRTP state.
     * @param msg  the message id of the ZRTP status change.
     */
    public void zrtpStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        runOnUiThread(new zrtpChangeWrapper(call, msg));
    }


    /**
     * The Service state change listener.
     *
     * This listener runs in the context of the TiviPhoneService thread. To perform some
     * activities on the UI you must use <code>runOnUiThread(new Runnable() { ... }</code>
     *
     * @param call the call that changed its state.
     * @param msg  the message id of the status change.
     */
    public void callStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) {
        runOnUiThread(new callChangeWrapper(call, msg));
    }

    /**
     * Device state change listener.
     *
     * Currently handles wired head set and bluetooth handling, later we add docking.
     */
    public void deviceStateChange(final int state) {
        runOnUiThread(new Runnable() {
            public void run() {
                deviceStateChanged(state);
            }
        });
    }

    /* *********************************************************************************
     * Implementation of InCallCallback interface methods
     ********************************************************************************* */
    public void addStateChangeListener(TiviPhoneService.ServiceStateChangeListener l) {
        synchronized (callStateListeners) {
            if (callStateListeners.contains(l))     // don't add twice
                return;
            callStateListeners.add(l);
        }
    }

    public void removeStateChangeListener(TiviPhoneService.ServiceStateChangeListener l) {
        synchronized (callStateListeners) {
            callStateListeners.remove(l);
        }
    }

    public void addDeviceChangeListener(TiviPhoneService.DeviceStateChangeListener l) {
        synchronized (deviceStateListeners) {
            if (deviceStateListeners.contains(l))   // don't add twice
                return;
            deviceStateListeners.add(l);
        }
    }

    public void removeDeviceChangeListener(TiviPhoneService.DeviceStateChangeListener l) {
        synchronized (deviceStateListeners) {
            deviceStateListeners.remove(l);
        }
    }

    public void answerCallCb() {
        answerCall(true);
    }

    public void addCallCb(CallState call) {
        if (mCallManagerFragment != null)            {
            mCallManagerFragment.setLastCallUnHold(call);// remember this call as last call not on hold
            mCallManagerFragment.setConferenceOnHold();  // and hold conference if necessary
        }
        if (!call.iIsOnHold) {
            call.iIsOnHold = true;                           // hold current call
            TiviPhoneService.doCmd("*h" + call.iCallId);
        }
        Intent intent = new Intent(this, DialerActivity.class);
        intent.setAction(ADD_CALL_ACTION);
        startActivityForResult(intent, ADD_CALL_ACTIVITY);
        invalidateOptionsMenu();
    }

    public void endCallCb(CallState call) {
        endCall(true, call);
    }

    public void verifySasCb(String Sas, int callId) {
        FragmentManager fragmentManager = getFragmentManager();
        VerifyDialog verify = VerifyDialog.newInstance(Sas, callId);
        verify.show(fragmentManager, "com.silentcircle.silentphone2.verify_dialog");
    }

    public void activateVideoCb() {
        activateVideo(false);
    }

    // Hide and remove the active video fragment. The fragment itself already
    // stopped the preview camera and released other resources.
    public void removeVideoCb() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (mVideoFragment != null) {
            mVideoActive = false;
            ft.hide(mVideoFragment).remove(mVideoFragment);
            if (!Utilities.isTablet(this))
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        getSupportActionBar().show();
        invalidateOptionsMenu();
        mInCallMain.refreshScreen();     // Global states may have changed, trigger InCallMain to refresh buttons
        ft.show(mInCallMain).commitAllowingStateLoss();
        if (TiviPhoneService.calls.getCallCount() > 1)    // user terminated video call, check if we need CM
            startCallManager();
    }

    public void updateProximityCb(boolean onOrOff) {
        updateProximitySensorMode(onOrOff);
    }

    public void hideCallManagerCb() {
        if (isCallManagerShown()) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.hide(mCallManagerFragment).show(mInCallMain).commitAllowingStateLoss();
            // execute immediately otherwise status during onPrepareOptionsMenu is not correct.
            getFragmentManager().executePendingTransactions();
        }
        invalidateOptionsMenu();
    }

    public void activeCallChangedCb(CallState oldCall, CallState newCall, boolean endOldCall) {
        if (newCall != null) {
            TiviPhoneService.calls.setCurCall(newCall);
        }
        if (endOldCall) {
            endCall(true, oldCall);
        }
        if (mInCallMain != null && newCall != null) {
            mInCallMain.showCall(newCall);
            mInCallMain.refreshSecurityFields(newCall);
        }
    }

    public void setMuteStatusCb(boolean onOrOff) {
        mIsMuted = onOrOff;
    }

    public boolean getMuteStateCb() {
        return mIsMuted;
    }

    public TiviPhoneService getPhoneService() {
        return mPhoneService;
    }

    public void setActiveCallNotificationCb(CallState call) {
        setCallActiveNotification(call);
    }

    /* ********************************************************************************
     * Callback functions for the DialpadFragment
     ******************************************************************************** */

    public boolean isDrawerOpen() {
        return mInCallDrawerFragment != null && mInCallDrawerFragment.isDrawerOpen();
    }

    /* ***********************************************************************
     * Sensors et.al.
     *********************************************************************** */

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_PROXIMITY)
            return;

        /* Will become true if proximity detector reports that phone is "near" */
        boolean isNear = false;

        switch (event.accuracy) {
            case SensorManager.SENSOR_STATUS_UNRELIABLE:
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                if (event.values[0] < event.sensor.getMaximumRange()) {
                    isNear = true;
                }
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                if (event.values[0] < 5.0f) {
                    isNear = true;
                }
                break;
        }
        if (mVideoActive) {
            mVideoFragment.proximityHandler(isNear);
        }
    }

    /* *****************************************************************************
     * Our Dialogs call these functions to perform action or simply close.
     ***************************************************************************** */

    /**
     * The VerifyDialog calls this method to store the peer name.
     *
     * @param peerName The peer name that the user typed.
     */
    public void storePeerAndVerify(String peerName, CallState call) {
        if (call == null)
            return;

        String cmd = "*z" + call.iCallId + " " + peerName;
        TiviPhoneService.doCmd(cmd);
        call.zrtpPEER.setText(peerName);
        call.iShowVerifySas = false;
        mInCallMain.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
        if(mVideoFragment != null)
            mVideoFragment.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
        mInCallMain.refreshScreen();
        if (mCallManagerFragment != null) {
            mCallManagerFragment.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
        }
        if (isDrawerOpen())
            mInCallDrawerFragment.setupCallSecurityInfo();
    }


    /**
     * The CryptoInfoDialog calls this method to reset the verify flag.
     *
     * @param peerName The peer name that the user typed.
     */
    public void resetVerify(ImageView view) {
        CallState call = TiviPhoneService.calls.selectedCall;
        if (call == null)
            return;
        if (!call.iShowVerifySas) {
            view.setImageResource(R.drawable.main_lock_locked);
            TiviPhoneService.doCmd("*v" + call.iCallId);
            call.zrtpPEER.setText("");
            call.iShowVerifySas = true;
            mInCallMain.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
            if(mVideoFragment != null)
                mVideoFragment.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
            mInCallMain.refreshScreen();
            if (mCallManagerFragment != null) {
                mCallManagerFragment.zrtpStateChange(call, TiviPhoneService.CT_cb_msg.eZRTP_peer);
            }
            if (isDrawerOpen())
                mInCallDrawerFragment.setupCallSecurityInfo();
        }
    }
    /**
     * After a Dialog closed restore button states/views.
     */
    public void dialogClosed() {
        mInCallMain.refreshScreen();
    }

    /**
     * Method to handle end of the ZRTP Message dialog
     */
    public void zrtpMessageDialogClose() {
        mZrtpErrorShowing = false;
    }

    /**
     * Method to close the ZRTP Warning/Error and end the call overlay
     */
    public void zrtpMessageDialogCloseEndCall(int callId) {
        CallState call = TiviPhoneService.calls.findCallById(callId);
        endCall(true, call);
        mZrtpErrorShowing = false;
    }

    /* ****************************************************************************
     * The following section contains private functions
     **************************************************************************** */

    // Reset in case Drawer window changed its state
    private void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;

        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        ((TextView)mToolbar.findViewById(R.id.title)).setText(getString(R.string.app_name));

        if (!TextUtils.isEmpty(DialerActivity.mDisplayName)) {
            ((TextView)mToolbar.findViewById(R.id.sub_title)).setText(DialerActivity.mDisplayName);
            Utilities.setSubtitleColor(this, mToolbar);
        }
    }

    private void answerCall(boolean userPressed) {

        runOnUiThread(new Runnable() {
            public void run() {
                mPhoneService.stopRinger();  // This must be on UI thread
                setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            }
        });
        if (userPressed) {
            // this should be called only before starting audio engine(recording, playback)
            // or it can brake audio playback (Samsung S3)
            Utilities.audioMode(getBaseContext(), true);
        }

        CallState call = TiviPhoneService.calls.selectedCall;
        if (userPressed) {
            String command = (call != null)? "*a" + call.iCallId : ":a";
            AsyncTasks.asyncCommand(command);
            mNotificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID);
        }

        if (!userPressed) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // this must be called after audio (playback,recording) starts
                    // if not then mic is not working on some devices.
                    Utilities.restoreSpeakerMode(getBaseContext());
                }
            });
        }
    }

    private boolean mPreviousUserPressed;
    synchronized private void endCall(boolean userPressed, final CallState call) {

        // No active call - this should not happen. Anyway, as a precaution: Stop ringer (just in case),
        // switch to normal audio mode, done
        // Usually this could not happen because we always set an active call state during call dialing
        // (see DialpadFragment#makeCall() )
        if (call == null) {
            Log.w(TAG, "Null call during end call processing: " + userPressed);
            mPreviousUserPressed = userPressed;
            mTerminateEarly = true;
            AsyncTasks.asyncCommand("*e0");      // cancel the dialing and call setup, the monitoring thread terminates
            if(mPhoneService != null) {
                mPhoneService.onStopCall();
            }
            mHideAfter = mPreviousUserPressed ? 1 : 3;
            runOnUiThread(new Runnable() {
                public void run() {
                    if(mPhoneService != null) {
                        mPhoneService.stopRinger();
                    }
                    if (TiviPhoneService.calls.getCallCount() < 1) {
                        Utilities.audioMode(getBaseContext(), false);
                        Utilities.turnOnSpeaker(getBaseContext(), false, true);
                    }
                }
            });
            mHideAfter = mPreviousUserPressed ? 1 : 3;
            return;
        }

        // Our user pressed the end-call button, send SIP BYE. After SIP processing was
        // done the phone service sets call.callEnded to true. I case of a call that was not yet
        // set-up completely the call id is zero. In this case we have an early call termination
        // that needs a specific handling.
        if (userPressed) {
            mPreviousUserPressed = true;
            AsyncTasks.asyncCommand("*e" + call.iCallId);
            if (call.iCallId == 0) {                         // mark as ended and let monitor thread handle it
                call.callEnded = true;
                if(mPhoneService != null) {
                    mPhoneService.onStopCall();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if(mPhoneService != null) {
                            mPhoneService.stopRinger();
                        }
                        if (TiviPhoneService.calls.getCallCount() < 1) {
                            Utilities.audioMode(getBaseContext(), false);
                            Utilities.turnOnSpeaker(getBaseContext(), false, true);
                        }
                    }
                });
                mHideAfter = 1;
            }
            return;
        }
        mHideAfter = mPreviousUserPressed ? 1 : 3;           // hide screen after x seconds (approx.)
        mPreviousUserPressed = false;

        call.secExceptionMsg = null;
        runOnUiThread(new Runnable() {
            public void run() {
                if(mPhoneService != null) {
                    mPhoneService.stopRinger();                   // This and audioMode call must be on UI thread
                }

                if(mNotificationManager != null) {
                    mNotificationManager.cancel(ACTIVE_CALL_NOTIFICATION_ID);
                }
                Utilities.audioMode(getBaseContext(), false);
                Utilities.turnOnSpeaker(getBaseContext(), false, true); // Set speaker to off at end of call and remember this
            }
        });
    }

    /**
     * Comment and code taken from Phone app - will use some of the commented
     * code/features sometime later when looking BT, speaker, etc
     *
     * Updates the wake lock used to control proximity sensor behavior,
     * based on the current state of the phone.  This method is called
     * from the CallNotifier on any phone state change.
     *
     * On devices that have a proximity sensor, to avoid false touches
     * during a call, we hold a PROXIMITY_SCREEN_OFF_WAKE_LOCK wake lock
     * whenever the phone is off hook.  (When held, that wake lock causes
     * the screen to turn off automatically when the sensor detects an
     * object close to the screen.)
     *
     * This method is a no-op for devices that don't have a proximity
     * sensor.
     *
     * Note this method doesn't care if the InCallScreen is the foreground
     * activity or not.  That's because we want the proximity sensor to be
     * enabled any time the phone is in use, to avoid false cheek events
     * for whatever app you happen to be running.
     *
     * Proximity wake lock will *not* be held if any one of the
     * conditions is true while on a call:
     * 1) If the audio is routed via Bluetooth
     * 2) If a wired headset is connected
     * 3) if the speaker is ON
     * 4) If the slider is open(i.e. the HW keyboard is *not* hidden)
     *
     */
    @SuppressLint("WakeLock")
    private void updateProximitySensorMode(boolean enable) {

        if (proximitySensorModeEnabled()) {
            synchronized (mProximitySync) {
                // turn proximity sensor off and turn screen on immediately if
                // we are using a headset, the keyboard is open, we saw a onUserLeaveHint,
                // or the device is being held in a horizontal position.
//                boolean screenOnImmediately = (isHeadsetPlugged()
//                            || PhoneUtils.isSpeakerOn(this)
//                            || ((mBtHandsFree != null) && mBtHandsFree.isAudioOn())
//                            || mIsHardKeyboardOpen);
                // We do not keep the screen off when we are horizontal, but we do not force it
                // on when we become horizontal until the proximity sensor goes negative.
//                boolean horizontal = (mOrientation == AccelerometerListener.ORIENTATION_HORIZONTAL);

                boolean usingHeadset = false;

                if(mPhoneService != null) {
                    usingHeadset = mPhoneService.isHeadsetPlugged() || mPhoneService.btHeadsetScoActive();
                }

                boolean keepScreenOn = Utilities.isSpeakerOn(getBaseContext()) || usingHeadset;

                if (enable && !keepScreenOn && !mLeaveHint) {
                    // Phone is in use!  Arrange for the screen to turn off
                    // automatically when the sensor detects a close object.
                    if (!mProximityWakeLock.isHeld()) {
                        mProximityWakeLock.acquire();
                    }
                } else {
                    if (mProximityWakeLock.isHeld()) {
                        // Wait until user has moved the phone away from his head if we are
                        // releasing due to the phone call ending.
                        // Otherwise, turn screen on immediately - not available in API 10
//                        int flags =
//                            (screenOnImmediately ? 0 : PowerManager.WAIT_FOR_PROXIMITY_NEGATIVE);
//                        mProximityWakeLock.release(flags);
                        mProximityWakeLock.release();
                    }
                }
            }
        }
    }

    /**
     * Notifies all registered <tt>CallStateChangeListener</tt>s that ZRTP state changed.
     *
     * @param call
     *            the <tt>the CTCall information</tt>
     */
    private void zrtpStateChanged(CallState call, TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (TiviPhoneService.ServiceStateChangeListener l : callStateListeners)
                l.zrtpStateChange(call, msg);
        }
    }

    /**
     * Notifies all registered <tt>CallStateChangeListener</tt>s that Call state changed.
     *
     * @param call
     *            the <tt>the CTCall information</tt>
     */
    private void callStateChanged(CallState call, TiviPhoneService.CT_cb_msg msg) {
        synchronized (callStateListeners) {
            for (TiviPhoneService.ServiceStateChangeListener l : callStateListeners)
                l.callStateChange(call, msg);
        }
    }

    /**
     * Notifies all registered <tt>DeviceStateChangeListener</tt>s that a device state changed.
     */
    private void deviceStateChanged(int stateChanged) {
        synchronized (deviceStateListeners) {
            for (TiviPhoneService.DeviceStateChangeListener l : deviceStateListeners)
                l.deviceStateChange(stateChanged);
        }
    }

    /**
     * Check if this device uses the ProximitySensorWakeLock.
     *
     * @return true if this device supports the "proximity sensor auto-lock" feature while
     *              in-call (see updateProximitySensorMode()).
     */
    private boolean proximitySensorModeEnabled() {
        return (mProximityWakeLock != null && DeviceHandling.useProximityWakeup());
    }

    private boolean isCallManagerShown() {
        return mCallManagerFragment != null && !mCallManagerFragment.isHidden();
    }

    private boolean isDtmfDialerShown() {
        return mDtmfDialer != null && !mDtmfDialer.isHidden();
    }

    private CountDownLatch mMonitorStopLatch;

    private void waitForLatch(CountDownLatch latch) {
        if (latch == null) {
            return;
        }
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void setupMonitoringThread() {
        /* *********************************************************************
         * Monitoring thread: this monitoring look at the active (selected) call
         * _only_  and checks for some periodic activities to happen, mainly if
         * video data is available. It also controls the delay when to close the
         * call window after the last call ended.
         *
         * Note: the call manager uses callback methods to the InCallActivity
         * to change which call is the active call and terminate calls on it own.
         * ******************************************************************* */
        callMonitoringThread = new Thread(new Runnable() {
            public void run() {

                boolean sleepModeIsFast;
                boolean callActivePreviousState = false;

                while (true) {

                    sleepModeIsFast = callActivePreviousState && mIsForeground && mVideoActive;
                    Utilities.Sleep(sleepModeIsFast ? 12 : 1000);

                    // selectedCall is the "active" call from, InCallMainFragment shows state of this call
                    CallState call = TiviPhoneService.calls.selectedCall;
                    if (mMonitorStopLatch != null) {
                        mMonitorStopLatch.countDown();
                        return;
                    }
                    if (call != null && call.iUpdated != 0) {
                        call.iUpdated = 0;
                    }
                    // isCallType returns true only if call is still active and is the correct type
                    // Active means: !callEnded && iActive . iActive becomes true after a eStartCall
                    // event (see phone service). Thus a not answered call (both incoming and outgoing)
                    // is not active.
                    boolean callActive = call != null &&
                            (ManageCallStates.isCallType(call, ManageCallStates.ePrivateCall) ||
                            ManageCallStates.isCallType(call, ManageCallStates.eConfCall));
                    // if mHideAfter is -1 we check if the call is still active
                    if (call != null && mHideAfter == -1) {
                        if (!callActive && call.callEnded) {
                            // endCall sets mHideAfter to some positive value
                            endCall(false, TiviPhoneService.calls.selectedCall);
                        }
                        else if (callActive && mVideoActive) {
                            mVideoFragment.checkVideoData(true);
                        }
                    }
                    callActivePreviousState = callActive;

                    // Check if the selected call is the only active call and is still in conference.
                    // May happen if user ends some other conf calls and only the selected call remains.
                    // in this case remove it from conference.
                    if (TiviPhoneService.calls.getCallCountWithClear() == 1 && callActive && call.isInConference) {
                        call.isInConference = false;
                        TiviPhoneService.doCmd("*-" + call.iCallId);
                    }

                    // mHideAfter goes to zero some time after a call ends. endCall() sets mHideAfter
                    // to some positive value and this monitoring loop counts down the value to delay
                    // the removal of the current call screen. If the current call was the
                    // last call - terminate InCallActivity, otherwise take another active call and
                    // update its call screen data in main call fragment.
                    // If only one call remains hide call manager and show call screen, otherwise
                    // re-trigger call manager.
                    if (mHideAfter > 0)
                        mHideAfter--;
                    if (mHideAfter == 0) {
                        /*
                         *  If no more calls to handle - break loop and finish InCallActivity. Otherwise get
                         *  an active call, set it as selectedCall, set un-hold mode if necessary and let
                         *  call manager handle it if necessary.
                         */
                        int numCalls = TiviPhoneService.calls.getCallCountWithClear();
                        if (numCalls < 1) {
                            break;
                        }
                        final CallState nextCall = TiviPhoneService.calls.getLastCall();
                        if (nextCall == null) {
                            break;
                        }

                        TiviPhoneService.calls.selectedCall = nextCall;
                        if (nextCall.iIsOnHold) {
                            TiviPhoneService.doCmd("*u" + nextCall.iCallId);
                            nextCall.iIsOnHold = false;
                        }
                        mHideAfter = -1;        // enable normal end-call monitoring

                        // Just one call left, handle it here, terminate call manager if running
                        // Set Audio to IN_CALL because we just took over another call,
                        // endCall processing sets audio to NORMAL
                        if (numCalls == 1) {
                            if (nextCall.isInConference) {   // no need to have the last call in a conference
                                nextCall.isInConference = false;
                                TiviPhoneService.doCmd("*-" + nextCall.iCallId);
                            }
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    mInCallMain.showCall(nextCall);
                                    mInCallMain.refreshSecurityFields(nextCall);
                                    Utilities.audioMode(getBaseContext(), true);
                                    hideCallManagerCb();
                                }
                            });
                        }
                        // More than one call left - start call manager
                        else {
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    mInCallMain.showCall(nextCall);
                                    mInCallMain.refreshSecurityFields(nextCall);
                                    Utilities.audioMode(getBaseContext(), true);
                                    startCallManager();
                                }
                            });
                        }
                    }
                }
                mPhoneService.bluetoothHeadset(false);
                InCallActivity.this.finish();
                if (mMonitorStopLatch != null) {
                    mMonitorStopLatch.countDown();
                }
            }
        });
    }

    // For a new video session we always create a new Fragment to have a clean state with
    // regard to camera, video display, etc. Restarting after screen rotation is handled by
    // normal means.
    private void activateVideo(boolean incoming) {
        FragmentManager fm = getFragmentManager();
        mVideoFragment = InCallVideoFragmentHw.newInstance(incoming);
        // Video handling is a bit critical: the system may destroy the activity because of rotation
        // and we thus may come into a race condition here because we may call activateVideo via
        // a runOnUiThread handler. Thus check is the activity was destroyed.
        if (!mDestroyed) {
            getSupportActionBar().hide();
            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(R.id.in_call_video, mVideoFragment, "com.silentcircle.silentphone.video")
                    .hide(mInCallMain).commitAllowingStateLoss();
        }
        invalidateOptionsMenu();
        mVideoActive = true;
    }

    private void checkMedia(CallState call) {
        // Handle media stream changes for selected call only.
        // We currently handle video stream changes only.
        if (call != TiviPhoneService.calls.selectedCall)
            return;
        // If video is currently active check if we need to switch off video, i.e. other
        // party removed video stream.
        if (mVideoActive) {
            if (!call.videoMediaActive) {
                mVideoFragment.stopVideo();
            }
            return;
        }

        // If video is currently not active and we got a new video media then
        // activate the video fragment only if this is a ZRTP call.
        if (call.videoMediaActive) {
            if (TextUtils.isEmpty(call.bufSAS.toString())) {
                return;
            }
            activateVideo(true);
        }
    }

    private void startCallManager() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (mCallManagerFragment == null) {
            mCallManagerFragment = new CallManagerFragment();
            ft.replace(R.id.in_call_manager, mCallManagerFragment, "com.silentcircle.silentphone.callManager");
        }
        ft.hide(mInCallMain).show(mCallManagerFragment).commitAllowingStateLoss();
        fm.executePendingTransactions();
        invalidateOptionsMenu();
    }

    private void startDtmfDialer() {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        if (mDtmfDialer == null) {
            mDtmfDialer = new DtmfDialerFragment();
            ft.replace(R.id.dtmf_dialer, mDtmfDialer, "com.silentcircle.silentphone.dtmfDialer");
        }
        ft.hide(mInCallMain).show(mDtmfDialer).commitAllowingStateLoss();
        mDtmfDialer.startTimer();
        if (!mIsMuted) {
            mDtmfSetToMute = true;
            TiviPhoneService.doCmd(":mute 1");
        }
    }

    private void showZrtpErrorWarning(String[] message, int type, CallState call) {
        if (message == null)
            return;

        // If error dialog already on display don't show a new one to avoid stacking
        if (!mZrtpErrorShowing) {
            ZrtpMessageDialog infoMsg =
                    ZrtpMessageDialog.newInstance(message, type, call.iCallId);
            FragmentManager fragmentManager = getFragmentManager();
            infoMsg.show(fragmentManager, "SilentPhoneZrtpMessage");
        }
        mZrtpErrorShowing = true;
    }

    /**
     * Private Helper class that implements the actions for ZRTP state changes.
     *
     * This runs on the UI thread.
     *
     * @author werner
     *
     */
    private class zrtpChangeWrapper implements Runnable {

        private final CallState call;
        private final TiviPhoneService.CT_cb_msg msg;

        zrtpChangeWrapper(CallState _call, TiviPhoneService.CT_cb_msg _msg) {
            call = _call;
            msg = _msg;
        }

        public void run() {
            switch (msg) {
                case eZRTPErrV:
                case eZRTPErrA:
                    if (call.zrtpWarning.getLen() > 0) {
                        showZrtpErrorWarning(ZrtpMessageDialog.getTranslatedMessage(getBaseContext(),
                                        call.zrtpWarning.toString(), call),
                                TiviPhoneService.CT_cb_msg.eZRTPErrA.ordinal(), call);
                    }
                    break;

                case eZRTPWarn:
                    if (call.zrtpWarning.getLen() > 0) {
                        showZrtpErrorWarning(ZrtpMessageDialog.getTranslatedMessage(getBaseContext(),
                                        call.zrtpWarning.toString(), call),
                                TiviPhoneService.CT_cb_msg.eZRTPWarn.ordinal(), call);
                    }
                    return;
            }
            zrtpStateChanged(call, msg);
        }
    }

    /**
     * Private Helper class that implements the actions for call state changes.
     *
     * This runs on the UI thread.
     *
     * @author werner
     *
     */
    private class callChangeWrapper implements Runnable {

        private final CallState call;
        private final TiviPhoneService.CT_cb_msg msg;

        /*
         * we get the ZRTP state changes here as well, but ignore them. Processed in separate
         * state change callback.
         */
        callChangeWrapper(CallState _call, TiviPhoneService.CT_cb_msg _msg) {
            call = _call;
            msg = _msg;
        }

        public void run() {

            if (call == null && msg == TiviPhoneService.CT_cb_msg.eReg) {
                Utilities.setSubtitleColor(InCallActivity.this, mToolbar);
                return;
            }
            if (call == null)       // This may happen in case of eError, PhoneService handles this
                return;
            String sipMessage = call.bufMsg.toString();
            if (!DialerActivity.mAutoAnswerForTesting &&
                    !TextUtils.isEmpty(sipMessage) && sipMessage.startsWith(TiviPhoneService.ERROR_MSG_PREFIX)) {
                sipMessage = sipMessage.substring(TiviPhoneService.ERROR_MSG_PREFIX.length(), sipMessage.length());
                call.bufMsg.reset();
                showCallInformation(sipMessage);
            }
            switch (msg) {
                case eStartCall:
                    updateProximitySensorMode(true);
                    answerCall(false);
                    invalidateOptionsMenu();
                    callStateChanged(call, msg);
                    break;

                case eIncomingCall:
                    // Check if this is another incoming call. If yes, start the call manager to handle this.
                    if (call != TiviPhoneService.calls.selectedCall && TiviPhoneService.calls.getCallCount() > 1) {
                        TiviPhoneService.doCmd("*r" + call.iCallId);  // play secondary ringtone
                        if (!mVideoActive && !isDtmfDialerShown())
                            startCallManager();
                    }
                    callStateChanged(call, msg);
                    break;

                case eRinging:
                    setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
                    callStateChanged(call, msg);
                    break;

                case eEndCall:
                    if (mVideoActive && call.videoMediaActive)
                        mVideoFragment.stopVideo();
                    callStateChanged(call, msg);
                    // The monitoring thread handles the actual endCall, delay by a few seconds.
                    break;

                case eNewMedia:
                    checkMedia(call);
                    break;

                case eCalling:
                    if (mTerminateEarly) {
                        mTerminateEarly = false;
                        endCall(mPreviousUserPressed, call);
                    }
                    updateProximitySensorMode(true);
                    callStateChanged(call, msg);
                    break;

                default:
                    break;
            }
        }
    }

    private static Bitmap mNotifyLargeIcon;
    public synchronized void setCallActiveNotification(final CallState call) {
        final int ico = R.drawable.stat_sys_phone_call;

        // The PendingIntent to launch our activity if the user selects this notification
        Intent launchIntent = new Intent(this, InCallActivity.class);
        launchIntent.setAction(NOTIFICATION_LAUNCH_ACTION);
        launchIntent.putExtra(TiviPhoneService.CALL_TYPE, TiviPhoneService.CALL_TYPE_RESTART);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent launchPendingIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to end the current call from the notification
        Intent endCallIntent = new Intent(this, InCallActivity.class);
        endCallIntent.setAction(NOTIFICATION_END_CALL_ACTION);
        endCallIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent endCallPendingIntent = PendingIntent.getActivity(this, 1, endCallIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (mNotifyLargeIcon == null)
            mNotifyLargeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_sp);

        String nameNum = call.getNameFromAB();
        nameNum = TextUtils.isEmpty(nameNum) ? call.bufPeer.toString() : nameNum;
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(nameNum)
                .setContentText(call.uiStartTime == 0 ?
                        getString(call.iIsIncoming ? R.string.type_incoming : R.string.type_outgoing) : getString(R.string.call_in_progress))
                .setSmallIcon(ico)
                .setLargeIcon(mNotifyLargeIcon)
                .setWhen(call.uiStartTime == 0 ? System.currentTimeMillis() : call.uiStartTime)
                .setContentIntent(launchPendingIntent)
                .setDeleteIntent(endCallPendingIntent)
                // Setting PRIORITY_MAX, otherwise other ongoing notifications (such as a dismissible alarm)
                // will cause action buttons (end call, etc.) to be hidden
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .addAction(R.drawable.ic_end_call_dark, getString(R.string.end_call_button), endCallPendingIntent)
                .build();
        mNotificationManager.notify(ACTIVE_CALL_NOTIFICATION_ID, notification);
    }

    private void showCallInformation(String message) {
        Intent intent = new Intent(this, CallInfoActivity.class);
        intent.putExtra("message", message);
        startActivity(intent);
    }

    private void hideFragment(FragmentTransaction ft, Fragment f) {
        if (f != null) {
            ft.hide(f);
        }
    }

    private boolean isVolumeToLow() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        final int maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        final int currentVolume = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        final float relativeVolume = (float)currentVolume / (float)maxVolume;
        return (relativeVolume <= 0.15);
    }
}
