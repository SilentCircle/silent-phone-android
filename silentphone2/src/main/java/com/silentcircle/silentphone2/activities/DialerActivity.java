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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.animation.AnimationListenerAdapter;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.DatabaseHelperManager;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.widget.FloatingActionButtonController;
import com.silentcircle.contacts.calllognew.CallLogActivity;
import com.silentcircle.contacts.utils.PhoneNumberHelper;
import com.silentcircle.contacts.vcard.MigrateByVCardActivity;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreActivity;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.silentcontacts2.ScContactsContract;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.database.DialerDatabaseHelper;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.dialpad.SmartDialPrefix;
import com.silentcircle.silentphone2.fragments.DialDrawerFragment;
import com.silentcircle.silentphone2.fragments.DialpadFragment;
import com.silentcircle.silentphone2.interactions.PhoneNumberInteraction;
import com.silentcircle.silentphone2.list.DragDropController;
import com.silentcircle.silentphone2.list.ListsFragment;
import com.silentcircle.silentphone2.list.OnDragDropListener;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.PhoneFavoriteSquareTileView;
import com.silentcircle.silentphone2.list.RegularSearchFragment;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.list.SmartDialSearchFragment;
import com.silentcircle.silentphone2.list.SpeedDialFragment;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.receivers.OutgoingCallReceiver;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.DialCodes;
import com.silentcircle.silentphone2.util.LoadUserInfo;
import com.silentcircle.silentphone2.util.MigrationHelperOld;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class DialerActivity extends TransactionSafeActivity
        implements DialDrawerFragment.DrawerCallbacks, DialpadFragment.DialpadCallbacks,
        KeyManagerSupport.KeyManagerListener, View.OnClickListener, ListsFragment.HostInterface,
        SpeedDialFragment.HostInterface, SearchFragment.HostInterface, OnDragDropListener, 
        ViewPager.OnPageChangeListener, DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener, OnPhoneNumberPickerActionListener, TiviPhoneService.ServiceStateChangeListener {

    private static final String TAG = DialerActivity.class.getSimpleName();

    private static final String SILENT_CALL_ACTION = "com.silentcircle.silentphone.action.NEW_OUTGOING_CALL";
    private static final String SILENT_EDIT_BEFORE_CALL_ACTION = "com.silentcircle.silentphone.action.EDIT_BEFORE_CALL";
    private static final String SILENT_PHONE_CHECK_ACCOUNT = "com.silentcircle.silentphone.action.CHECK_ACCOUNT";
    public static final String ACTION_REMOVE_ACCOUNT = "remove_account";

    /** Store configuration setting */
    public static final String SILENT_PHONE_KEY = "silent_phone_sip_key";

    private static final String DIAL_PAD_FRAGMENT = "com.silentcircle.silentphone.dialpad";
    private static final String FAVORITES_FRAGMENT = "com.silentcircle.silentphone.favorites";
    private static final String REGULAR_SEARCH_FRAGMENT = "com.silentcircle.silentphone.search";
    private static final String SMARTDIAL_SEARCH_FRAGMENT = "com.silentcircle.silentphone.smartdial";

    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";

    public static final String SHARED_PREFS_NAME = "com.silentcircle.dialer_preferences";

    /*
     * Codes for startActivityWithResult Intents
     */
    private static final int PROVISIONING_RESULT = 1003;
    private static final int KEY_STORE_CHECK = 1004;
    private static final int RINGTONE_SELECTED = 1005;
    private static final int DIAL_HELPER_SELECTED = 1006;
    private static final int KEY_STORE_PASS_ACTION = 1007;
    private static final int KEY_STORE_CHECK_OLD = 1008;        // **** Migration support

    public static boolean mAutoAnswerForTesting;
    public static int mAutoAnsweredTesting;

    public static String mNumber;
    public static String mName;

    public static float mRefreshRate;
    /**
     * Fragment managing some content related to the dialer.
     */
    private DialDrawerFragment mDialDrawerFragment;

    private DialpadFragment mDialpadFragment;
    private DrawerLayout mDrawerLayout;
    private View mDrawerView;
    private ListsFragment mListsFragment;

    // If true then we know this phone is provisioned. During onCreate we may set this to false.
    private boolean isProvisioned = true;
    private boolean isActionCheckAccount;
    private boolean provisioningDone;
    private boolean mAutoDialRequested;
    private boolean mCallActionIntent;
    private boolean mStartOnBoot;           // Started right after boot via AutoStart listener
    private boolean mRemoveAccount;         // received a remove account action

    /*
     * Manage and monitor the KeyManager
     */
    private boolean hasKeyManager;
    private boolean mKeyManagerCheckActive;

    /**
     * Monitor the service status
     */
    private boolean mPhoneIsBound;
    private TiviPhoneService mPhoneService;
    private static boolean mServiceStarted;  // set to true after call to startService(..., TiviPhoneService)
    private static boolean mUseBpForwarder;

    // some internal features, usually not visible to user
    private boolean mEnableShowZid;
    private boolean mAdvancedSettings;
    private boolean mReProvisioningRequested;

    private boolean mDestroyed;             // Activity being destroyed, don't do delayed actions (service bind) 

    private FloatingActionButtonController mFloatingActionButtonController;
    /**
     * Fragment for searching phone numbers using the alphanumeric keyboard.
     */
    private RegularSearchFragment mRegularSearchFragment;

    /**
     * Fragment for searching phone numbers using the dialpad.
     */
    private SmartDialSearchFragment mSmartDialSearchFragment;

    private EditText mSearchView;
    private String mSearchQuery;
    /**
     * Search query to be applied to the SearchView in the ActionBar once
     * onCreateOptionsMenu has been called.
     */
    private String mPendingSearchViewQuery;

    private FrameLayout mParentLayout;
    /**
     * Animation that slides in.
     */
    private Animation mSlideIn;

    /**
     * Animation that slides out.
     */
    private Animation mSlideOut;

    private int mActionBarHeight;
    
    private boolean mIsLandscape;
    private int mCurrentTabPosition;

    private boolean mIsDialpadShown;
    private boolean mShowDialpadOnResume;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private DialerDatabaseHelper mDialerDatabaseHelper;

    private Toolbar mToolbar;

    /**
     * Listener for after slide out animation completes on dialer fragment.
     */
    private final AnimationListenerAdapter mSlideOutListener = new AnimationListenerAdapter() {
        @Override
        public void onAnimationEnd(Animation animation) {
            commitDialpadFragmentHide();
        }
    };

    /**
     *
     * Setup the function to connect to the phone service and disconnect if required.
     *
     * The Activity calls bindService after it created the fragments, thus the mDialPad
     * and mDrawerLayout, etc are not null.
     */
    private final ServiceConnection phoneConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            //
            // After service is bound and ready we can access phone service data
            // and initialize the dial drawer and the user info.
            mPhoneService = ((TiviPhoneService.LocalBinder)service).getService();
            mPhoneIsBound = true;
            if (mDestroyed) {
                doUnbindService();
                return;
            }
            mPhoneService.addStateChangeListener(DialerActivity.this);
            LoadUserInfo mLoadUserInfo = new LoadUserInfo(getBaseContext(), null);
            mLoadUserInfo.loadExpirationInfo();
            mDialDrawerFragment.setNumberName();
            mDialDrawerFragment.activateTraversal();

            if (provisioningDone) {
                provisioningDone = false;
                if (!isDrawerOpen()) {
                    mDialpadFragment.removeDestinationFocus();
                    mDrawerLayout.postDelayed(mOpenDialDrawer, 400);
                }
                // Another application (SilentText mainly) triggered the provisioning. Display info
                // for a few seconds, then return result to Intent sender.
                if (isActionCheckAccount) {
                    Utilities.Sleep(5000);
                    setResult(Activity.RESULT_OK);
                    finish();
                }
                return;
            }
            checkCallToNr(OutgoingCallReceiver.getCallToNumber(getBaseContext()), true, mDialpadFragment);
            if (mAutoDialRequested) {
                mDialpadFragment.dialButtonPressed();
                mAutoDialRequested = false;
            }
            FindDialHelper.setDialHelper(DialerActivity.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mPhoneIsBound = false;
            mPhoneService = null;
        }
    };

    private void doBindService() {
        
        // Establish a connection with the service. We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE);
    }

    private void doUnbindService() {
        if (mPhoneIsBound) {
            mPhoneService.removeStateChangeListener(this);
            // Detach our existing connection.
            unbindService(phoneConnection);
            mPhoneIsBound = false;
        }
    }


    /**
     * Listener used to send search queries to the phone search fragment.
     */
    private final TextWatcher mPhoneSearchQueryTextListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            final String newText = s.toString();
            if (newText.equals(mSearchQuery)) {
                // If the query hasn't changed (perhaps due to activity being destroyed
                // and restored, or user launching the same DIAL intent twice), then there is
                // no need to do anything here.
                return;
            }
//            Log.d(TAG, "++++ onTextChange for mSearchView called with new query: " + newText);
//            Log.d(TAG, "++++ Previous Query: " + mSearchQuery);

            mSearchQuery = newText;

            // Show search fragment only when the query string is changed to non-empty text.
            if (!TextUtils.isEmpty(newText)) {
                // Call enterSearchUi only if we are switching search modes, or showing a search
                // fragment for the first time.
                final boolean sameSearchMode = (mIsDialpadShown && mInDialpadSearch) ||
                        (!mIsDialpadShown && mInRegularSearch);
                if (!sameSearchMode) {
                    enterSearchUi(mIsDialpadShown, mSearchQuery);
                }
            }

            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    /**
     * If the search term is empty and the user closes the soft keyboard, close the search UI.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN &&
                    TextUtils.isEmpty(mSearchView.getText().toString())) {
                maybeExitSearchUi();
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AutoStart.setDisableAutoStart(true);                            // Don't auto start if we are started once

        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();

        mRemoveAccount = ACTION_REMOVE_ACCOUNT.equals(action);

        mStartOnBoot = AutoStart.ON_BOOT.equals(action);

        if (InCallActivity.ADD_CALL_ACTION.equals(action)) {
            // This is a phone call screen to add a call, thus perform some specific handling
            int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            getWindow().addFlags(windowFlags);
        }

        mRefreshRate = getWindowManager().getDefaultDisplay().getRefreshRate();
        
        Utilities.setTheme(this);
        ConfigurationUtilities.initializeDebugSettings(getBaseContext());

        Log.d(TAG, "Debug: " + ConfigurationUtilities.mTrace + ", language: " + Locale.getDefault().getLanguage()
                + ", develop config: " + ConfigurationUtilities.mUseDevelopConfiguration + ", build commit: +-+- " +
                BuildConfig.SPA_BUILD_COMMIT);

        TiviPhoneService.initJNI(getBaseContext());
        isProvisioned = !(TiviPhoneService.mc == null && TiviPhoneService.doCmd("isProv") == 0);

        mIsLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received intent: " + getIntent());

        final boolean isLayoutRtl = Utilities.isRtl();
        if (mIsLandscape) {
            mSlideIn = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_in_left : R.anim.dialpad_slide_in_right);
            mSlideOut = AnimationUtils.loadAnimation(this,
                    isLayoutRtl ? R.anim.dialpad_slide_out_left : R.anim.dialpad_slide_out_right);
        }
        else {
            mSlideIn = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_in_bottom);
            mSlideOut = AnimationUtils.loadAnimation(this, R.anim.dialpad_slide_out_bottom);
        }

        mSlideIn.setInterpolator(AnimUtils.EASE_IN);
        mSlideOut.setInterpolator(AnimUtils.EASE_OUT);

        mSlideOut.setAnimationListener(mSlideOutListener);

        mDialerDatabaseHelper = DatabaseHelperManager.getDatabaseHelper(this);
        SmartDialPrefix.initializeNanpSettings(this);

        mActionBarHeight = getResources().getDimensionPixelSize(R.dimen.action_bar_height_large);

        // If savedInstance is null then we have a fresh start. Otherwise we have have
        // restart, usually because of config changes (screen rotation). In this case
        // we _must_ call the keyManagerCheck (which calls doLayout) function before we
        // leave onCreate. Otherwise the system does not restore the fragments correctly.
        // On a restart we don't need to check and set the key manager, we take the saved
        // state.
        //
        // checkAndSetKeyManager leaves onCreate without doing the layout because it starts
        // an Activity for result. The result action may call the layout function. On a new
        // start this is not a problem.
        setResult(RESULT_CANCELED);
        if (savedInstanceState == null) {
            checkAndSetKeyManager();
        }
        else {
            hasKeyManager = savedInstanceState.getBoolean("KEY_MANAGER");
            keyManagerChecked();
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY);
            mInRegularSearch = savedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = savedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mShowDialpadOnResume = savedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
        }
        // Register even if not key manager may not be active
        KeyManagerSupport.addListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDialerDatabaseHelper.startSmartDialUpdateThread();

        if (mDialpadFragment != null)
            checkCallToNr(OutgoingCallReceiver.getCallToNumber(getBaseContext()), true, mDialpadFragment);

        if (mShowDialpadOnResume) {
            showDialpadFragment(false);
            mShowDialpadOnResume = false;
        }
        if (mInRegularSearch) {
            updateSearchFragmentSettings();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDestroyed = true;
        KeyManagerSupport.removeListener(this);
        doUnbindService();
    }

    @Override
    public void onSaveInstanceState (@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("KEY_MANAGER", hasKeyManager);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
    }

    @Override
    protected void onNewIntent (Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received new intent: " + intent);
        processIntent(intent);
    }

    private boolean mOldKeyManagerInactive;
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == PROVISIONING_RESULT) {
            if (resultCode != RESULT_OK) {
                exitApplication();
            }
            else {
                provisioningDone = true;
                isProvisioned = true;
                keyManagerChecked();                // this also starts the service, layout, initializes fragments, etc
            }
            return;
        }
        if (requestCode == KEY_STORE_CHECK) {
            if (!mKeyManagerCheckActive)
                return;
            mKeyManagerCheckActive = false;
            if (resultCode != RESULT_OK) {
                Log.w(TAG, "KeyManager READY request failed - exit.");
                exitApplication();
            }
            else {
                keyManagerChecked();
            }
            return;
        }
        // **** Migration support start ****
        if (requestCode == KEY_STORE_CHECK_OLD) {

            if (resultCode == RESULT_CANCELED) {
                Log.w(TAG, "Request for old KeyManager failed.");
            }
            else {
                if (resultCode == RESULT_FIRST_USER)
                    mOldKeyManagerInactive = true;
                // If we cannot copy existing key data correctly then force a provisioning
                if (resultCode == RESULT_OK && !MigrationHelperOld.copyOldKeyData(this)) {
                    Log.w(TAG, "Missing provisioning data during migration. Force provisioning");
                    removeConfigFiles();
                    isProvisioned = false;
                }
            }
            keyManagerChecked();
            return;
        }
        // **** Migration support end ****

        if (requestCode == RINGTONE_SELECTED) {
            if (data == null)
                return;
            Uri tone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            mDialDrawerFragment.updateRingtone(tone);
        }
        if (requestCode == DIAL_HELPER_SELECTED) {
            mDialDrawerFragment.updateDialHelper();
        }
        if (requestCode == KEY_STORE_PASS_ACTION) {
            mDialDrawerFragment.prepareKeyStoreOptions();
        }
    }

    /*
     * Dial drawer callbacks
     */
    @Override
    public void onDrawerItemSelected(int type, Object... params) {
        Intent intent;
        switch (type) {
            case DialDrawerFragment.DrawerCallbacks.RINGTONE:
                Uri selected = (Uri)params[0];
                intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selected);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

                startActivityForResult(intent, RINGTONE_SELECTED);
                break;

            case DialDrawerFragment.DrawerCallbacks.DIAL_HELPER:
                intent = new Intent(this, DialHelperSelectorActivity.class);
                startActivityForResult(intent, DIAL_HELPER_SELECTED);
                break;

            case DialDrawerFragment.DrawerCallbacks.KEY_STORE:
                String action = (String)params[0];
                intent = new Intent(this, KeyStoreActivity.class);
                intent.setAction(action);
                startActivityForResult(intent, KEY_STORE_PASS_ACTION);
                break;

            case DialDrawerFragment.DrawerCallbacks.RE_PROVISION:
                mDrawerLayout.closeDrawer(mDrawerView);
                mReProvisioningRequested = true;
                break;
        }
    }

    @Override
    public void onDrawerStateChange(int action) {
        if (action == DialDrawerFragment.DrawerCallbacks.CLOSED) {
            if (mReProvisioningRequested) {
                mReProvisioningRequested = false;
                findViewById(R.id.floating_action_button_container).setVisibility(View.INVISIBLE);
                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.hide(mListsFragment).commitAllowingStateLoss();
                checkDeviceStatus();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mDialDrawerFragment != null && !mDialDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.dialpad, menu);
            restoreActionBar();
            return true;
        }
        if (mPendingSearchViewQuery != null) {
            mSearchView.setText(mPendingSearchViewQuery);
            mPendingSearchViewQuery = null;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final MenuItem item = menu.findItem(R.id.dial_menu_search);
        if (item != null) {
            if (isInSearchUi()) {
                item.setVisible(false);
            }
            else {
                item.setVisible(true);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        ActionBar actionBar = getSupportActionBar();
        /*
         * OpenDialDrawer is a bit tricky: To avoid flickering first prepare the dialpad
         * layout. Then delay the call to open the drawer which gives some time to remove
         * the keyboard (slide out) and perform necessary layout changes, before the drawer
         * really opens.
         *
         * No such issues during drawer close. Just close it, after it closed we use
         * the state change to slide in the soft keyboard {@see onDrawerStateChange}
         */
        switch (item.getItemId()) {

            case R.id.dial_menu_search:
                if (!isInSearchUi()) {
                    actionBar.setDisplayShowCustomEnabled(true);
                    ((TextView)mToolbar.findViewById(R.id.title)).setText(null);
                    ((TextView)mToolbar.findViewById(R.id.sub_title)).setText(null);
                    ((SearchEditTextLayout) actionBar.getCustomView()).expand(true /* animate */, true /* requestFocus */);
                    enterSearchUi(false /* smartDialSearch */, mSearchView.getText().toString());
                    invalidateOptionsMenu();
                }
                return true;

            case R.id.dial_menu_exit:
                exitApplication();
                return true;

            case R.id.dial_menu_production:
                ConfigurationUtilities.switchToProduction(getBaseContext());
                mDrawerLayout.closeDrawer(mDrawerView);
                mReProvisioningRequested = true;
                return true;

            case R.id.dial_menu_develop:
                ConfigurationUtilities.switchToDevelop(getBaseContext(), ConfigurationUtilities.DEVELOPMENT_NETWORK);
                mDrawerLayout.closeDrawer(mDrawerView);
                mReProvisioningRequested = true;
                return true;

            case R.id.dial_menu_answer_on:
                mAutoAnswerForTesting = true;
                mAutoAnsweredTesting = 0;
                return true;

            case R.id.dial_menu_answer_off:
                mAutoAnswerForTesting = false;
                mAutoAnsweredTesting = 0;
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                showDialpadFragment(true);
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && !mDialpadFragment.isHidden()) {
            if (TextUtils.isEmpty(mSearchQuery) ||
                    (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()
                            && mSmartDialSearchFragment.getAdapter().getCount() == 0)) {
                exitSearchUi();
            }
            hideDialpadFragment(true, false);
        }
        else if (isInSearchUi()) {
            exitSearchUi();
            DialerUtils.hideInputMethod(mParentLayout);
        }
        else {
            super.onBackPressed();
        }
    }

    public boolean isDrawerOpen() { return mDialDrawerFragment != null && mDialDrawerFragment.isDrawerOpen(); }

    // If Dialer detects special prefix it calls internal handling
    public void internalCall(String internalCommand) {
        if (internalCommand.indexOf("*##*") == 0) {
            if ("*##*943*".compareTo(internalCommand) == 0) {
                mEnableShowZid = !mEnableShowZid;
                return;
            }
            if ("*##*123*".compareTo(internalCommand) == 0) {
                mAdvancedSettings = !mAdvancedSettings;
                return;
            }
            if (!DialCodes.internalCallStatic(this, mPhoneService, internalCommand)) {
                TiviPhoneService.doCmd(internalCommand);  // do some internal voodoo (for example delete config files ;-) )
                if("*##*3357768*".compareTo(internalCommand) == 0) {
                    exitApplication();
                }
            }
        }
    }

    public void doCall(String callCommand, String destination) {
        if (mPhoneIsBound) {
            Utilities.audioMode(getBaseContext(), true);        // Switch to IN-COMMUNICATION mode
            Utilities.asyncCommand(callCommand);
            if (mCallActionIntent) {
                setResult(RESULT_OK);
                finish();
            }
            mPhoneService.showCallScreen(TiviPhoneService.CALL_TYPE_OUTGOING, destination);
        }
    }

    public boolean isAdvancedSettings() {
        return mAdvancedSettings;
    }

    /* ****************************************************************************
     * The following section contains private functions
     ***************************************************************************  */

    private final Runnable mOpenDialDrawer = new Runnable() {
        @Override
        public void run() {
            mDrawerLayout.openDrawer(mDrawerView);
        }
    };

    private void checkDeviceStatus() {
        CheckDeviceStatus statusTask = new CheckDeviceStatus();
        statusTask.execute();
    }

    private void removeProvisioningAndRestart(final boolean force) {
        if (force) {
            KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        }
        else {
            final SharedPreferences prefs =  getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
            prefs.edit().putString(ConfigurationUtilities.getReprovisioningNameKey(), mName).commit();
        }
        TiviPhoneService.doCmd("*##*3357768*");         // remove existing provisioning files
        TiviPhoneService.doCmd(".exit");
        doUnbindService();
        stopService(new Intent(DialerActivity.this, TiviPhoneService.class));
        mServiceStarted = false;
        FindDialHelper.resetDialHelper();

        // get the intent that started us and use it to restart
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    // Reset in case Drawer window changed its state
    private void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null || isInSearchUi())
            return;
        ((TextView)mToolbar.findViewById(R.id.title)).setText(getString(R.string.app_name));

        if (TextUtils.isEmpty(mNumber))
            mNumber = TiviPhoneService.getInfo(0, -1, "cfg.nr");
        if (!TextUtils.isEmpty(mNumber)) {
            String number = PhoneNumberHelper.formatNumber(mNumber, Locale.getDefault().getCountry());
            mNumber = TextUtils.isEmpty(number) ? mNumber : number;  // if format number eats all data show raw number
        }
        if (TextUtils.isEmpty(mName))
            mName  = TiviPhoneService.getInfo(0, -1, "cfg.un");
        if (!TextUtils.isEmpty(mName)) {
            ((TextView)mToolbar.findViewById(R.id.sub_title)).setText(mName + (!TextUtils.isEmpty(mNumber) ? "/" + mNumber : ""));
            Utilities.setSubtitleColor(getResources(), mToolbar);
        }
    }

    private void startPhoneService() {
        // Check and set the secret key
        if (hasKeyManager && TiviPhoneService.getSecretKeyStatus() == TiviPhoneService.SECURE_KEY_NOT_AVAILABLE)
            initializeKeyData();

        if (!mServiceStarted) {
            startService(new Intent(this, TiviPhoneService.class));
            mServiceStarted = true;            // service sends ":reg"
        }
        else if (TiviPhoneService.mc != null && TiviPhoneService.mc.isReady())
            Utilities.asyncCommand(":reg"); // doCmd(":reg"); - do it async to avoid ANR in case network is slow
    }

    private static boolean mKeyDataMigrationDone;           // **** Migration support ****
    // This is the second step of the of onCreate flow.
    //
    // Either called during onCreate or after we got the KeyManager result.
    //
    // At this point the phone service is not yet active, thus start the service
    // if phone is provisioned. Otherwise it starts provisioning and if provisioning
    // was OK then this function is called again (isProvisioning is true).
    private void keyManagerChecked() {

        if (mRemoveAccount) {
            removeAccount();
            finish();
            return;
        }

        // **** Migration support start - key data *****
        if (hasKeyManager && !mKeyDataMigrationDone) {
            mKeyDataMigrationDone = true;
            byte[] keyData = KeyManagerSupport.getPrivateKeyData(getContentResolver(), SILENT_PHONE_KEY);

            // Migrate data only if not started during boot
            // Migration may ask for unlock of old key manager and this may disturb boot sequence
            if (keyData == null && MigrateByVCardActivity.doMigration(this)) {
                if (mStartOnBoot) {
                    mKeyDataMigrationDone = false;
                    finish();
                }
                else {
                    MigrationHelperOld.checkOldKeyManager(this, KEY_STORE_CHECK_OLD);
                }
                return;             // continues from onActivityResult
            }
        }
        // **** Migration support end - key data ****

        if (!isProvisioned) {
            if (mStartOnBoot) {          // no automatic start if client not yet provisioned
                finish();
                return;
            }
            Intent provisioningIntent = new Intent(this, ProvisioningActivity.class);
            provisioningIntent.putExtra("KeyManager", hasKeyManager);
            provisioningIntent.putExtra("DeviceId", TiviPhoneService.getDeviceId(this));
            startActivityForResult(provisioningIntent, PROVISIONING_RESULT);
            return;
        }
        startPhoneService();             // start phone service
        if (mStartOnBoot) {
            finish();
            return;
        }
        doLayout();
        doBindService();
        checkMigration();               // **** Migration support contacts data
    }

    // Called after service was connected, actually the third part of onCreate
    @SuppressWarnings("deprecation")
    private void doLayout() {
        setContentView(R.layout.activity_dialer_new);
        // Set up the drawer.
        mDialDrawerFragment = (DialDrawerFragment)getFragmentManager().findFragmentById(R.id.dial_content_drawer);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        mDrawerView = findViewById(R.id.dial_content_drawer);
        mDialDrawerFragment.setUp(mDrawerView, mDrawerLayout);
        mToolbar = (Toolbar)findViewById(R.id.dialer_toolbar);
        setSupportActionBar(mToolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);

        SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
        searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);

        mSearchView = (EditText)searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);
//        searchEditTextLayout.findViewById(R.id.search_magnifying_glass).setOnClickListener(mSearchViewOnClickListener);
//        searchEditTextLayout.findViewById(R.id.search_box_start_search).setOnClickListener(mSearchViewOnClickListener);
        searchEditTextLayout.setOnBackButtonClickedListener(new SearchEditTextLayout.OnBackButtonClickedListener() {
            @Override
            public void onBackButtonClicked() {
                onBackPressed();
            }
        });

        mParentLayout = (FrameLayout)findViewById(R.id.dialer_main_layout);

        final View floatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        ImageButton floatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                floatingActionButtonContainer, floatingActionButton);
        floatingActionButtonContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        final ViewTreeObserver observer = floatingActionButtonContainer.getViewTreeObserver();
                        if (!observer.isAlive()) {
                            return;
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
                            observer.removeGlobalOnLayoutListener(this);
                        else
                            observer.removeOnGlobalLayoutListener(this);
                        int screenWidth = mDrawerLayout.getWidth();
                        mFloatingActionButtonController.setScreenWidth(screenWidth);
                        updateFloatingActionButtonControllerAlignment(false /* animate */);
                    }
                });

        setupActivityOverlay();

        final FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();
        mDialpadFragment = (DialpadFragment)fragmentManager.findFragmentByTag(DIAL_PAD_FRAGMENT);
        if (mDialpadFragment == null) {
            mDialpadFragment = new DialpadFragment();
            ft.add(R.id.dial_container, mDialpadFragment, DIAL_PAD_FRAGMENT)
                    .add(R.id.dial_frame, new ListsFragment(), FAVORITES_FRAGMENT)
                    .hide(mDialpadFragment)
                    .commitAllowingStateLoss();
        }
        else {
            ft.hide(mDialpadFragment).commitAllowingStateLoss();
        }
        fragmentManager.executePendingTransactions();
        DeviceHandling.checkAndSetAec();
        processIntent(getIntent());
    }

    // We may need to enhance the "checkKeyManager" process in case we use keys to encrypt
    // the config files. Then we may need to check more often and at different places, needs to
    // be investigated.
    private void checkAndSetKeyManager() {
        hasKeyManager = true;
        final long token = KeyManagerSupport.registerWithKeyManager(getContentResolver(), getPackageName(), getString(R.string.app_name));
        if (token == 0) {
            Log.w(TAG, "Cannot register with KeyManager.");
            hasKeyManager = false;
        }
        // the onActivityResult calls keyManagerChecked if SKA is ready to use. Otherwise we proceed
        // without key manager
        if (hasKeyManager) {  // add check if !ready ??
            // Don't auto start if the user protected the key store with own password or PIN
            if (mStartOnBoot && KeyStoreHelper.getUserPasswordType(this) != KeyStoreHelper.USER_PW_TYPE_NONE) {
                finish();
                return;
            }
            mKeyManagerCheckActive = true;
            startActivityForResult(KeyManagerSupport.getKeyManagerReadyIntent(), KEY_STORE_CHECK);
        }
        else {
            keyManagerChecked();
        }
    }

    public static boolean useBpForwarder() {
        return mUseBpForwarder;
    }

    private static boolean mMigrationChecked;

    // **** Migration support function ****
    // Check if we need to migrate data from old Silent Contacts to embedded SCA
    // this function also checks if we need the special BP migration settings.
    private void checkMigration() {
        if (mMigrationChecked)
            return;
        mMigrationChecked = true;

        // First check if the old SCA is the special BP variant that uses a launch intent forwarder
        // If yes then enable out FORWARDER alias which points to the SCA activity and disable the
        // real SCA activity. This avoids display of two SCA icons while at the same time
        // maintaining the BP desktop.
        PackageManager pm = getPackageManager();
        Intent checkActivity = new Intent("com.silentcircle.blackphone.contact.FORWARDER");
        ResolveInfo ri = pm.resolveActivity(checkActivity, PackageManager.MATCH_DEFAULT_ONLY);

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "BP forwarder found: " + ri + ", match: " + (ri == null ? "0" : ri.match));

        mUseBpForwarder = !(ri == null || ri.match == 0);
        if (!mUseBpForwarder) {
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactDetailActivityForwarder"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactDetailActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        }
        else {
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivityForwarder"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactsMainActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactDetailActivityForwarder"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(new ComponentName(this, "com.silentcircle.contacts.activities.ScContactDetailActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
        if (!mOldKeyManagerInactive) {
            MigrateByVCardActivity.setBpForwarder(mUseBpForwarder);

            if (!MigrateByVCardActivity.doMigration(this)) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Do not start migration.");
                return;
            }
            startActivity(MigrateByVCardActivity.getStartMigrationIntent(this));
        }
        else if (mUseBpForwarder) {
            ComponentName cn = new ComponentName("com.silentcircle.contacts",
                    "com.silentcircle.contacts.activities.ScContactsMainActivity");
            Intent intent = new Intent("com.silentcircle.blackphone.contact.FORWARDER");
            intent.setComponent(cn);
            startActivity(intent);
        }
    }

    /**
     * Read encryption key from key manager to encrypt/decrypt the SIP password.
     *
     * If no encryption key available yet the functions uses a key manager functions to
     * create and store a random key.
     */
    private void initializeKeyData() {
        byte[] keyData = KeyManagerSupport.getPrivateKeyData(getContentResolver(), SILENT_PHONE_KEY);
        if (keyData == null) {
            keyData = KeyManagerSupport.randomPrivateKeyData(getContentResolver(), SILENT_PHONE_KEY, 32);
        }
        if (keyData == null) {
            showToast("Cannot get key to decrypt password");
            exitApplication();
        }
        else
            TiviPhoneService.setSecretKey(keyData);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    private static boolean checkCallToNr(String number, boolean pstnViaOca, DialpadFragment pad) {
        if (TextUtils.isEmpty(number))
            return false;

        number = number.trim();
        if (Utilities.isUriNumber(number)) {
            number = Utilities.removeSipParts(number); // number.replace(getString(R.string.sc_sip_domain_0), "");
        }
        if (TextUtils.isEmpty(number))
            return false;
        boolean wasModified = false;
        char firstChar = number.charAt(0);
        if (firstChar == '+' || Character.isDigit(firstChar)) {
            StringBuilder modified = new StringBuilder(20);
            wasModified = FindDialHelper.getDialHelper().analyseModifyNumberString(number, modified);
            if (wasModified) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    number = PhoneNumberUtils.formatNumber(modified.toString(), Locale.getDefault().getCountry());
                else
                    number = PhoneNumberUtils.formatNumber(modified.toString());
            }
        }
        pad.setDestination(number, pstnViaOca);
        return wasModified;
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Perform an orderly application shutdown and exit.
     */
    private void exitApplication() {
        new Thread(new Runnable() {
            public void run() {
                TiviPhoneService.doCmd(".exit");
                stopServiceAndExit(400);
            }
        }).start();

    }

    private void stopServiceAndExit(int delay) {
        if (hasKeyManager)
            KeyManagerSupport.unregisterFromKeyManager(getContentResolver());
        KeyStoreHelper.closeStore();
        stopService(new Intent(DialerActivity.this, TiviPhoneService.class));
        doUnbindService();
        Utilities.Sleep(delay);
        System.exit(0);
    }

    private void removeAccount() {
        KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardDevIdTag());

        TiviPhoneService.doCmd("*##*3357768*");  // delete config files ;-)
        exitApplication();
    }

    private Intent intentProcessed;
    private void processIntent(Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.v(TAG, "Process received intent: " + intent);

        if (intentProcessed == intent) {
            if (ConfigurationUtilities.mTrace) Log.v(TAG, "Same intent received: " + intent);
            return;
        }
        intentProcessed = intent;
        if (intent == null)
            return;

        String action = intent.getAction();
        if (action == null)
            return;

        if (InCallActivity.ADD_CALL_ACTION.equals(action)) {
            mCallActionIntent = true;
            return;
        }
        if (SILENT_PHONE_CHECK_ACCOUNT.equals(action)) {
            if (isProvisioned) {
                setResult(Activity.RESULT_OK);
                finish();
                return;
            }
            else
                isActionCheckAccount = true;
        }

        Uri numberUri = intent.getData();

        // Check if this is a call from native contacts and user decided to have a secure call
        boolean pstnViaOca = false;
        String pstnNumber = null;
        if (OutgoingCallReceiver.OCA_CALL.equals(action)) {
            pstnNumber = intent.getStringExtra(OutgoingCallReceiver.PSTN_NUMBER);
            pstnViaOca = true;
        }
        // If the Intent does not contain a number information and the number of active calls is
        // >= 1 then restart the InCall activity. Activities were destroyed by some user action (recent
        // app list), not by some device action like rotation.
        if (numberUri == null && !pstnViaOca) {
            if (TiviPhoneService.calls.getCallCount() >= 1) {
                Intent forward = new Intent(intent);
                Bundle bundle = new Bundle();
                bundle.putInt(TiviPhoneService.CALL_TYPE, TiviPhoneService.CALL_TYPE_RESTART);
                forward.putExtras(bundle);
                forward.setClass(this, InCallActivity.class);
                forward.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                if (mPhoneIsBound)
                    mPhoneService.setNotificationToIncall();
                startActivity(forward);
            }
            return;
        }
        String number = (!pstnViaOca) ? numberUri.getSchemeSpecificPart() : pstnNumber;

        if (number == null || mDialpadFragment == null)
            return;

        if (pstnViaOca || SILENT_CALL_ACTION.equals(action) || SILENT_EDIT_BEFORE_CALL_ACTION.equals(action)) {
            boolean wasModified = checkCallToNr(number, pstnViaOca, mDialpadFragment);

            mCallActionIntent = true;
            if (pstnViaOca || SILENT_CALL_ACTION.equals(action)) {
                if (!wasModified) {
                    if (!mPhoneIsBound)                      // Start dialing if phone service is ready (bound)
                        mAutoDialRequested = true;
                    else
                        mDialpadFragment.dialButtonPressed();
                }
                else
                    showDialpadFragment(true);
            }
            else
                showDialpadFragment(true);
        }
    }

    // We should move this to C++ code but not with a special dial command. We need it as
    // externally callable function that can work before any other initialization. At least
    // we may need a functions to get the file names and remove the constants here.
    private void removeConfigFiles() {
        final String names[] = {"settings.txt", "tivi_cfg10555.xml", "tivi_cfg.xml", "tivi_cfg1.xml"};
        File directory = getFilesDir();

        for (String name : names) {
            File file = new File(directory, name);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (ConfigurationUtilities.mTrace)
                    Log.d(TAG, "Remove config file: " + file.getAbsolutePath() + "(" + deleted + ")");
            }
        }
    }

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
    public void zrtpStateChange(CallState call, TiviPhoneService.CT_cb_msg msg) { }

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
        runOnUiThread(new ServiceChangeWrapper(call, msg));
    }

    /**
     * The key manager read data during a {@code store*} or {@code update*} call.
     */
    public void onKeyDataRead() {}

    /**
     * The support provider received a lock request from the key manager.
     * <p/>
     * The user may <em>lock</em> the key manager. In this case the key manager closes the
     * key store and sends a lock notification to all registered applications. If the key store
     * is locked the key manager cannot process key entry requests. Registration and de-registration
     * is still possible.
     * <p/>
     * An applications may take more actions, such as deleting sensitive data or closing connections.
     */
    public void onKeyManagerLockRequest() {}

    /**
     * The support provider received an unlock request from the key manager.
     * <p/>
     * The user has <em>unlocked</em> the key manager. The key store is open, key manager is ready and
     * sends an unlock notification to all registered applications.
     * <p/>
     * An applications may take more actions, such as reading their key data, shared data etc.
     */
    public void onKeyManagerUnlockRequest() {}

    /**
     * Private Helper class that implements the actions for service state changes.
     *
     * This runs on the UI thread.
     *
     * @author werner
     *
     */
    private class ServiceChangeWrapper implements Runnable {

        @SuppressWarnings("unused")
        private final CallState call;
        private final TiviPhoneService.CT_cb_msg msg;

        /*
         * we get the ZRTP state changes here as well, but ignore them. Processed in separate
         * state change callback.
         */
        ServiceChangeWrapper(CallState _call, TiviPhoneService.CT_cb_msg _msg) {
            call = _call;
            msg = _msg;
        }

        public void run() {
            if (msg != TiviPhoneService.CT_cb_msg.eReg)
                return;
            Utilities.setSubtitleColor(getResources(), mToolbar);
        }
    }

    /**
     * Async task to check device availability on the provisioning server.
     *
     * If the device is still known on the provisioning server then continue with the re-provisioning,
     * otherwise ask user how to proceed.
     */
    private class CheckDeviceStatus extends AsyncTask<Void, Integer, Integer> {

        private URL requestUrl;

        CheckDeviceStatus() {
            String resourceSec = ConfigurationUtilities.getProvisioningBaseUrl(getBaseContext()) +
                    ConfigurationUtilities.getDeviceManagementBase(getBaseContext());  // /v1/me/device/

            byte[] data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
            if (data != null) {
                try {
                    String devAuthorization = new String(data, "UTF-8").trim();
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Authentication data (API key) : " + devAuthorization);
                    data = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardDevIdTag());
                    if (data == null) {
                        showDialog(getString(R.string.information_dialog), getString(R.string.provisioning_no_data),
                                android.R.string.ok, -1);
                        return;
                    }
                    String deviceId = new String(data, "UTF-8").trim();
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Shared deviceId : " + deviceId);
                    requestUrl = new URL(resourceSec + Uri.encode(deviceId) + "/?api_key=" + Uri.encode(devAuthorization));
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Device check URL: " + requestUrl);
                } catch (UnsupportedEncodingException ignored) {
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                    finish();
                }
            }
        }

        // /v1/me/device/{device_id}/?api_key={api_key}
        @Override
        protected Integer doInBackground(Void... params) {
            HttpsURLConnection urlConnection = null;
            try {
                urlConnection = (HttpsURLConnection)requestUrl.openConnection();
                SSLContext context = PinnedCertificateHandling.getPinnedSslContext(ConfigurationUtilities.mNetworkConfiguration);
                if (context != null) {
                    urlConnection.setSSLSocketFactory(context.getSocketFactory());
                }
                else {
                    Log.e(TAG, "Cannot get a trusted/pinned SSL context; failing");
                    throw new AssertionError("Failed to get pinned SSL context");
                }
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept-Language", Locale.getDefault().getLanguage());

                int ret = urlConnection.getResponseCode();
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "HTTP code device status: " + ret);
                return ret;

            } catch (IOException e) {
                if(!Utilities.isNetworkConnected(DialerActivity.this)){
                    return Constants.NO_NETWORK_CONNECTION;
                }
                Log.e(TAG, "Network not available: " + e.getMessage());
                return -1;
            } catch (Exception e) {
                Log.e(TAG, "Network connection problem: " + e.getMessage());
                return -1;
            } finally {
                if (urlConnection != null)
                    urlConnection.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == HttpsURLConnection.HTTP_OK) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Device status is OK");
                removeProvisioningAndRestart(false);
            }
            else if(result == Constants.NO_NETWORK_CONNECTION) {
                showDialog(getString(R.string.information_dialog), getString(R.string.connected_to_network), android.R.string.ok, -1);
            }
            else if (result == HttpsURLConnection.HTTP_FORBIDDEN || result == HttpsURLConnection.HTTP_NOT_FOUND) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Device status check - not found on provisioning server");
                showDeviceCheckInfo();
            }
            else {
                showDialog(getString(R.string.information_dialog), getString(R.string.connected_to_network),
                        android.R.string.ok, -1);
            }
        }
    }

    private void showDeviceCheckInfo() {
        DeviceStatusInfo errMsg = DeviceStatusInfo.newInstance(getString(R.string.re_provisioning_device_not_found));
        FragmentManager fragmentManager = getFragmentManager();
        errMsg.show(fragmentManager, "SilentPhoneDeviceCheck");
    }

    private void showDialog(String title, String msg, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(title, msg, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, TAG);
    }

    public static class DeviceStatusInfo extends DialogFragment {
        private DialerActivity mParent;

        public static DeviceStatusInfo newInstance(String msg) {
            DeviceStatusInfo f = new DeviceStatusInfo();

            Bundle args = new Bundle();
            args.putString("MESSAGE", msg);
            f.setArguments(args);

            return f;
        }

        public DeviceStatusInfo() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mParent = (DialerActivity)activity;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the Builder class for convenient dialog construction
            AlertDialog.Builder builder = new AlertDialog.Builder(mParent);
            Bundle args = getArguments();
            if (args == null)
                return null;
            builder.setTitle(getString(R.string.provisioning_info))
                    .setMessage(args.getString("MESSAGE"))
                    .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mParent.removeProvisioningAndRestart(true);
                        }
                    })
                    .setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mParent.finish();
                        }
                    });
            // Create the AlertDialog object and return it
            return builder.create();
        }
    }
    
    // ************** New code for Lollipop and new layout ***********************
    // ***************************************************************************

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof DialpadFragment) {
            mDialpadFragment = (DialpadFragment) fragment;
//            if (!mShowDialpadOnResume) {
//                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
//                transaction.hide(mDialpadFragment);
//                transaction.commit();
//            }
        } else if (fragment instanceof SmartDialSearchFragment) {
            mSmartDialSearchFragment = (SmartDialSearchFragment) fragment;
            mSmartDialSearchFragment.setOnPhoneNumberPickerActionListener(this);
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
        } else if (fragment instanceof ListsFragment) {
            mListsFragment = (ListsFragment) fragment;
            mListsFragment.addOnPageChangeListener(this);
        }
    }

    /*
     * Monitor the tab movements and slide the button
     */
    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        position = mListsFragment.getRtlPosition(position);
        // Only scroll the button when the first tab is selected. The button should scroll from
        // the middle to right position only on the transition from the first tab to the second
        // tab.
        // If the app is in RTL mode, we need to check against the second tab, rather than the
        // first. This is because if we are scrolling between the first and second tabs, the
        // viewpager will report that the starting tab position is 1 rather than 0, due to the
        // reversal of the order of the tabs.
        final boolean isLayoutRtl = Utilities.isRtl();
        final boolean shouldScrollButton = position == (isLayoutRtl
                ? ListsFragment.TAB_INDEX_RECENTS : ListsFragment.TAB_INDEX_SPEED_DIAL);
        if (shouldScrollButton && !mIsLandscape) {
            mFloatingActionButtonController.onPageScrolled(
                    isLayoutRtl ? 1 - positionOffset : positionOffset);
        } 
        else if (position != ListsFragment.TAB_INDEX_SPEED_DIAL) {
            mFloatingActionButtonController.onPageScrolled(1);
        }
    }

    @Override
    public void onPageSelected(int position) {
        position = mListsFragment.getRtlPosition(position);
        mCurrentTabPosition = position;
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    /**
     * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
     * updates are handled by a callback which is invoked after the dialpad fragment is shown.
     * @see #onDialpadShown
     */
    private void showDialpadFragment(boolean animate) {
        if (mIsDialpadShown) {
            return;
        }
        mIsDialpadShown = true;
//        mDialpadFragment.setAnimate(animate);
//        mDialpadFragment.sendScreenView();

        getSupportActionBar().hide();
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.show(mDialpadFragment);
        ft.commit();

        if (animate) {
            mFloatingActionButtonController.scaleOut();
        } else {
            mFloatingActionButtonController.setVisible(false);
        }
        if (!isInSearchUi()) {
            enterSearchUi(true /* isSmartDial */, mSearchQuery);
        }
        else {                                  // inSearchUi: Dialpad overlays the regular search
            mDialpadFragment.clearDialpad();
            if (mRegularSearchFragment != null)
                mRegularSearchFragment.setShowScDirectoryOption(false);
        }
        mDialpadFragment.showKeyboard();
    }

    /**
     * Callback from child DialpadFragment when the dialpad is shown.
     */
    public void onDialpadShown() {
//        if (mDialpadFragment.getAnimate()) {
        final View view = mDialpadFragment.getView();
        if (view != null)
            view.startAnimation(mSlideIn);
//        } else {
//            mDialpadFragment.setYFraction(0);
//        }
        updateSearchFragmentSettings();
    }

    // Update the search position in any case, if the fragment is visible or not.
    // When called via onDialpadShow(...) the search fragments my not be visible yet.
    private void updateSearchFragmentSettings() {
        SearchFragment fragment = null;
        if (mSmartDialSearchFragment != null) {
            fragment = mSmartDialSearchFragment;
        } else if (mRegularSearchFragment != null) {
            fragment = mRegularSearchFragment;
        }
        if (fragment != null) {
            fragment.updatePosition(true /* animate */);
            fragment.setShowEmptyListForNullQuery(true);
            if (mSearchQuery != null)
                mSearchView.setText(mSearchQuery);
            if (fragment == mRegularSearchFragment) {
                ActionBar actionBar = getSupportActionBar();
                actionBar.setDisplayShowCustomEnabled(true);
                SearchEditTextLayout seTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
                seTextLayout.expand(false /*animate */, true /*focus */);
            }
        }
    }

    /**
     * Finishes hiding the dialpad fragment after any animations are completed.
     */
    private void commitDialpadFragmentHide() {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.hide(mDialpadFragment);
        ft.commit();

        mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
    }

    /**
     * Initiates animations and other visual updates to hide the dialpad. The fragment is hidden in
     * a callback after the hide animation ends.
     * @see #commitDialpadFragmentHide
     */
    public void hideDialpadFragment(boolean animate, boolean clearDialpad) {
        if (mDialpadFragment == null) {
            return;
        }
        if (clearDialpad) {
            mDialpadFragment.clearDialpad();
        }
        if (!mIsDialpadShown) {
            return;
        }
        mIsDialpadShown = false;
        getSupportActionBar().show();
//        mDialpadFragment.setAnimate(animate);
//
        updateSearchFragmentSettings();

        updateFloatingActionButtonControllerAlignment(animate);
        if (animate) {
            final View view = mDialpadFragment.getView();
            if (view != null)
                view.startAnimation(mSlideOut);
        }
        else {
            commitDialpadFragmentHide();
        }

        if (isInSearchUi()) {
            if (TextUtils.isEmpty(mSearchQuery)) {
                exitSearchUi();
            }
            else if (mInRegularSearch)
                mRegularSearchFragment.setShowScDirectoryOption(true);
        }
    }

    /**
     * Updates controller based on currently known information.
     *
     * @param animate Whether or not to animate the transition.
     */
    private void updateFloatingActionButtonControllerAlignment(boolean animate) {
        final int align = (!mIsLandscape && mCurrentTabPosition == ListsFragment.TAB_INDEX_SPEED_DIAL) ?
                FloatingActionButtonController.ALIGN_MIDDLE :
                FloatingActionButtonController.ALIGN_END;
        mFloatingActionButtonController.align(align, 0 /* offsetX */, 0 /* offsetY */, animate);
    }

    @Override
    public void showCallHistory() {
        // Use explicit CallLogActivity intent instead of ACTION_VIEW +
        // CONTENT_TYPE, so that we always open our call log from our dialer
        final Intent intent = new Intent(this, CallLogActivity.class);
        startActivity(intent);
    }

    @Override
    public int getActionBarHeight() {
        return mActionBarHeight;
    }

    @Override
    public boolean isDialpadShown() {
        return mIsDialpadShown;
    }

    @Override
    public void setActionBarHideOffset(int hideOffset) {
//        if (hideOffset > 0 && getSupportActionBar().isShowing())
//            getSupportActionBar().hide();
//        getSupportActionBar().setHideOffset(hideOffset);
    }

    @Override
    public int getActionBarHideOffset() {
        return getSupportActionBar().getHideOffset();
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
    }

    public static Intent getAddNumberToContactIntent(CharSequence text) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        if (Character.isLetter(text.charAt(0)))
            intent.putExtra(ScContactsContract.Intents.Insert.SIP_ADDRESS, text);
        else
            intent.putExtra(ScContactsContract.Intents.Insert.PHONE, text);
        intent.setType(ScContactsContract.RawContacts.CONTENT_ITEM_TYPE);
        return intent;
    }

    /**
     * Called when the user has long-pressed a contact tile to start a drag operation.
     */
    @Override
    public void onDragStarted(int x, int y, PhoneFavoriteSquareTileView view) {
//        if (mListsFragment.isPaneOpen()) {
//            mActionBarController.setAlpha(ListsFragment.REMOVE_VIEW_SHOWN_ALPHA);
//        }
        mListsFragment.showRemoveView(true);
    }

    @Override
    public void onDragHovered(int x, int y, PhoneFavoriteSquareTileView view) {
    }

    /**
     * Called when the user has released a contact tile after long-pressing it.
     */
    @Override
    public void onDragFinished(int x, int y) {
//        if (mListsFragment.isPaneOpen()) {
//            mActionBarController.setAlpha(ListsFragment.REMOVE_VIEW_HIDDEN_ALPHA);
//        }
        mListsFragment.showRemoveView(false);
    }

    @Override
    public void onDroppedOnRemove() {}

    /**
     * Allows the SpeedDialFragment to attach the drag controller to mRemoveViewContainer
     * once it has been attached to the activity.
     */
    @Override
    public void setDragDropController(DragDropController dragController) {
        mListsFragment.getRemoveView().setDragDropController(dragController);
    }

    @Override
    public void onListFragmentScrollStateChange(int scrollState) {
        if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            hideDialpadFragment(true, false);
            DialerUtils.hideInputMethod(mParentLayout);
        }
    }

    @Override
    public void onListFragmentScroll(int firstVisibleItem, int visibleItemCount,
                                     int totalItemCount) {
        // TODO: No-op for now. This should eventually show/hide the actionBar based on
        // interactions with the ListsFragments.
    }

    @Override
    public void onPickPhoneNumberAction(Uri dataUri) {
        // Specify call-origin so that users will see the previous tab instead of
        // CallLog screen (search UI will be automatically exited).
        PhoneNumberInteraction.startInteractionForPhoneCall(DialerActivity.this, dataUri, null /*getCallOrigin()*/);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber) {
        onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
        callImmediateOrAsk(phoneNumber);
//        Intent intent = isVideoCall ?
//                CallUtil.getVideoCallIntent(phoneNumber, getCallOrigin()) :
//                CallUtil.getCallIntent(phoneNumber, getCallOrigin());
    }

    @Override
    public void onShortcutIntentCreated(Intent intent) {
        Log.w(TAG, "Unsupported intent has come (" + intent + "). Ignoring.");
    }

    @Override
    public void onHomeInActionBarSelected() {
        exitSearchUi();
    }

    /**
     * Shows the search fragment
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void enterSearchUi(boolean smartDialSearch, String query) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mInDialpadSearch && mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        } else if (mInRegularSearch && mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }

        final String tag;
        if (smartDialSearch) {
            tag = SMARTDIAL_SEARCH_FRAGMENT;
        } else {
            tag = REGULAR_SEARCH_FRAGMENT;
        }
        mInDialpadSearch = smartDialSearch;
        mInRegularSearch = !smartDialSearch;

        SearchFragment fragment = (SearchFragment) getFragmentManager().findFragmentByTag(tag);
        transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        if (fragment == null) {
            if (smartDialSearch) {
                fragment = new SmartDialSearchFragment();
            } else {
                fragment = new RegularSearchFragment();
                fragment.setShowScDirectoryOption(true);
            }
            transaction.add(R.id.dial_frame, fragment, tag);
        } else {
            transaction.show(fragment);
        }
        // DialerActivity will provide the options menu
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(true);
        fragment.setQueryString(query, false /* delaySelection */);
        transaction.commit();

        final View view = mListsFragment.getView();
        if (view == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            view.animate().alpha(0).withLayer();
        else
            view.animate().alpha(0);
    }

    /**
     * Hides the search fragment
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void exitSearchUi() {
        // See related bug in enterSearchUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            return;
        }

        mSearchView.setText(null);
//        mDialpadFragment.clearDialpad();
        setNotInSearchUi();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        if (mSmartDialSearchFragment != null) {
            transaction.remove(mSmartDialSearchFragment);
        }
        if (mRegularSearchFragment != null) {
            transaction.remove(mRegularSearchFragment);
        }
        transaction.commit();

        final View view = mListsFragment.getView();
        if (view == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            view.animate().alpha(1).withLayer();
        else
            view.animate().alpha(1);

//        mActionBarController.onSearchUiExited();
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(false);
        SearchEditTextLayout seTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
        if (seTextLayout.isExpanded()) {
            seTextLayout.collapse(true /* animate */);
        }
        if (seTextLayout.isFadedOut()) {
            seTextLayout.fadeIn();
        }
        invalidateOptionsMenu();
        restoreActionBar();
    }

    private void callImmediateOrAsk(String number) {
        if (number != null && mDialpadFragment != null) {
            boolean wasModified = checkCallToNr(number, false, mDialpadFragment);
            if (!wasModified)
                mDialpadFragment.dialButtonPressed();
            else {
                if (isInSearchUi() && !mIsDialpadShown)
                    exitSearchUi();
                showDialpadFragment(true);
            }
        }
    }

    private void setNotInSearchUi() {
        mInDialpadSearch = false;
        mInRegularSearch = false;
    }

    boolean isInSearchUi() {
        return mInDialpadSearch || mInRegularSearch;
    }

    private void maybeExitSearchUi() {
        if (isInSearchUi() && TextUtils.isEmpty(mSearchQuery) && !mInRegularSearch) { // to allow clicking on SC options in regular search
            exitSearchUi();
            DialerUtils.hideInputMethod(mParentLayout);
        }
    }
    @Override
    public void onDialpadQueryChanged(String query) {
        if (mSmartDialSearchFragment != null) {
            mSmartDialSearchFragment.setAddToContactNumber(query);
        }
        // final String normalizedQuery = query; // SmartDialNameMatcher.normalizeNumber(query, SmartDialNameMatcher.LATIN_SMART_DIAL_MAP);

        if (!TextUtils.equals(mSearchView.getText(), query)) {
            if (mDialpadFragment == null || !mDialpadFragment.isVisible()) {
                // This callback can happen if the dialpad fragment is recreated because of
                // activity destruction. In that case, don't update the search view because
                // that would bring the user back to the search fragment regardless of the
                // previous state of the application. Instead, just return here and let the
                // fragment manager correctly figure out whatever fragment was last displayed.
                if (!TextUtils.isEmpty(query)) {
                    mPendingSearchViewQuery = query;
                }
                return;
            }
            mSearchView.setText(query);
        }
    }

    private void setupActivityOverlay() {
        final View activityOverlay = findViewById(R.id.activity_overlay);
        activityOverlay.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!mIsDialpadShown) {
                    maybeExitSearchUi();
                }
                return false;
            }
        });
    }
}
