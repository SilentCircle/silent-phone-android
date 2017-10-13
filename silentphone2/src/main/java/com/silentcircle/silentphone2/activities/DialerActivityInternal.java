/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.InflateException;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.getkeepsafe.taptargetview.TapTarget;
import com.getkeepsafe.taptargetview.TapTargetSequence;
import com.google.android.gms.iid.InstanceID;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.animation.AnimationListenerAdapter;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.API;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DatabaseHelperManager;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ExplainPermissionDialog;
import com.silentcircle.common.util.HttpUtil;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.calllognew.CallLogActivity;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreActivity;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.fragments.SearchAgainFragment;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.database.DialerDatabaseHelperOrig;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.dialpad.SmartDialPrefix;
import com.silentcircle.silentphone2.fragments.DialDrawerFragment;
import com.silentcircle.silentphone2.fragments.DialpadFragment;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.interactions.PhoneNumberInteraction;
import com.silentcircle.silentphone2.list.ListsFragment;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.RegularSearchFragment;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.list.SmartDialSearchFragment;
import com.silentcircle.silentphone2.passcode.PasscodeController;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.DialCodes;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.SPAPreferences;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class DialerActivityInternal extends TransactionSafeActivity
        implements DialDrawerFragment.DrawerCallbacks, DialpadFragment.DialpadCallbacks,
        KeyManagerSupport.KeyManagerListener, View.OnClickListener, ListsFragment.HostInterface,
        SearchFragment.HostInterface, ViewPager.OnPageChangeListener, DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener, OnPhoneNumberPickerActionListener, SearchFragment.OnSearchActionListener,
        TiviPhoneService.ServiceStateChangeListener, LoadUserInfo.Listener, ExplainPermissionDialog.AfterReading {

    private static final String TAG = DialerActivityInternal.class.getSimpleName();

    public static final String SILENT_CALL_ACTION = "com.silentcircle.silentphone.action.NEW_OUTGOING_CALL";
    public static final String SILENT_EDIT_BEFORE_CALL_ACTION = "com.silentcircle.silentphone.action.EDIT_BEFORE_CALL";
    public static final String ACTION_REMOVE_ACCOUNT = "remove_account";
    public static final String START_FOR_MESSAGING = "start_for_messaging";
    public static final String NO_NUMBER_CHECK = "no_number_check";
    public static final String EXTRA_FROM_CALL = "from_call";

    /** Store configuration setting */
    public static final String SILENT_PHONE_KEY = "silent_phone_sip_key";

    private static final String DIAL_PAD_FRAGMENT = "com.silentcircle.silentphone.dialpad";
    private static final String FAVORITES_FRAGMENT = "com.silentcircle.silentphone.favorites";
    private static final String REGULAR_SEARCH_FRAGMENT = "com.silentcircle.silentphone.search";
    private static final String SMARTDIAL_SEARCH_FRAGMENT = "com.silentcircle.silentphone.smartdial";

    private static final String KEY_KEY_MANAGER = "KEY_MANAGER";
    private static final String KEY_IN_REGULAR_SEARCH_UI = "in_regular_search_ui";
    private static final String KEY_IN_DIALPAD_SEARCH_UI = "in_dialpad_search_ui";
    private static final String KEY_SEARCH_QUERY = "search_query";
    private static final String KEY_SEARCH_PHONE_INPUT = "keyboard_input";
    private static final String KEY_IS_DIALPAD_SHOWN = "is_dialpad_shown";
    private static final String KEY_IS_AUTO_DIAL = "is_auto_dial";
    private static final String KEY_IN_SETTINGS_UI = "in_settings_ui";
    private static final String KEY_HAS_CHECKED_KEY_MANAGER = "has_checked_key_manager";
    private static final String KEY_MANAGER_CHECK_ACTIVE = "key_manager_check_active";

    private static final String KEY_HAD_TRANSITIONED = "had_transitioned";
    private static final String KEY_OPTIONAL_PERMISSIONS_ASKED = "optional_permissions_asked";

    public static final String SHARED_PREFS_NAME = "com.silentcircle.dialer_preferences";

    // Identifiers for permission handling
    public static final int PERMISSIONS_REQUIRED_REQUEST_SP = 1;
    public static final int PERMISSIONS_OPTIONAL_REQUEST_SP = 2;
    public static final int PERMISSIONS_REQUEST_EXIT = 3;
    public static final int PERMISSIONS_REQUEST_READ_PHONE_STATE = 4;
    public static final String KEY_PERMISSIONS_EXPLAINED = "is_permissions_explained";

    /*
     * Codes for startActivityWithResult Intents
     */
    public static final int PROVISIONING_RESULT = 1003;
    public static final int KEY_STORE_CHECK = 1004;
    public static final int RINGTONE_SELECTED = 1005;
    public static final int DIAL_HELPER_SELECTED = 1006;
    public static final int KEY_STORE_PASS_ACTION = 1007;
//    public static final int HIDE_WELCOME = 1009;
    public static final int MESSAGE_RINGTONE_SELECTED = 1011;

    private static final String KEY_DIALER_ACTIVITY_FINISH = "dialer_activity_finish";

    public static final String KEY_FEATURE_DISCOVERY_ONBOARDING = "feature_discovery_onboarding";
    public static final String KEY_FEATURE_DISCOVERY_NEW_CONVERSATION = "feature_discovery_new_conversation";

    // Transition time when transitioning to a settings item
    private static final int SETTINGS_ITEM_TRANSITION_MS = 325;

    public static boolean mAutoAnswerForTesting;
    public static int mAutoAnsweredTesting;

    public static String mUuid;
    public static String mDisplayTn;
    public static String mDisplayAlias;
    public static String mDisplayName;

    public static String[] mDomainsToRemove;

    /**
     * Fragment managing some content related to the dialer.
     */
    private DialDrawerFragment mDialDrawerFragment;

    private DialpadFragment mDialpadFragment;
    private DrawerLayout mDrawerLayout;
    private View mDrawerView;
    private ListsFragment mListsFragment;

    /**
     * Runnable classes executed by mDrawerView
     */
    private Runnable mEnteringSettingsUiRunner;
    private Runnable mClosingDrawerRunner;

    private LoadUserInfo mLoadUserInfo;

    // If true then we know this phone is provisioned. During onCreate we may set this to false.
    private boolean isProvisioned = true;
    private boolean provisioningDone;
    private boolean mAutoDialRequested;
    private boolean mCallActionIntent;
    private boolean mStartOnBoot;           // Started right after boot via AutoStart listener
    private boolean mRemoveAccount;         // received a remove account action
    private boolean mStartForMessaging;     // Initialize and start service for messaging
    private boolean mShouldProcessNewIntent;

    // If true, we already explained the main permissions to the user
    private boolean mPermissionsExplained;

    /*
     * Manage and monitor the KeyManager
     */
    private boolean hasKeyManager;
    private boolean hasCheckedKeyManager;
    private boolean mKeyManagerCheckActive;
    private boolean mShouldContinueCreationFlow;

    /**
     * Monitor the service status
     */
    private boolean mPhoneIsBound;
    private TiviPhoneService mPhoneService;
    private static boolean mServiceStarted;  // set to true after call to startService(..., TiviPhoneService)
    private static boolean mForceUpdates;    // true after we forced a contacts update and refreshed user info

    // some internal features, usually not visible to user
    private boolean mEnableShowZid;

    private boolean mDestroyed;             // Activity being destroyed, don't do delayed actions (service bind)

    // Store this in a static variable to not call PreferenceManager#getDefaultSharedPreferences
    // for every error view
    public static boolean mShowErrors;

    public static boolean mShowCreditDialog;

    /**
     * Internal handler to receive and process internal messages messages.
     */
//    private final InternalHandler mHandler = new InternalHandler(this);

    private android.support.design.widget.FloatingActionButton mFloatingActionButton;

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
    private boolean mSearchPhoneInput;

    /**
     * For slow networks, this is shown when initiating a phone call
     */
    private Handler mPhoneCallProgressHandler;
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

    private boolean mIsDialpadShown;
    private boolean mShowDialpadOnResume;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mIsInSettingsUi;

    private DialerDatabaseHelperOrig mDialerDatabaseHelper;

    private Toolbar mToolbar;

    private Drawable mDialPadIcon;
    private Drawable mNewChatIcon;

    private Bundle mSavedInstanceState;

    /**
     * Store this not to call FragmentTransaction.commit(), commitAllowingStateLoss() and etc.
     * after calling isSaveInstanceState. It is used on 'showDialog(int, int, int, int)'. Although
     * 'commitAllowingStateLoss()' allows the commit to be executed after an activity's state is saved,
     * 'IllegalStateException: Activity has been destroyed' may occur if the activity has been destroyed already.
     * 'removeCallbacks()' at onStop() may not work if the Runner has already been dequeued.
     * Also, 'isFinishing()' and 'isDestroyed()' may not return a correct result or may not be called
     * depending on the situation.
     */
    private boolean mIsInstanceStateSaved;

    public static boolean mOnboardingFeatureDiscoveryRan;
    public static boolean mNewConversationFeatureDiscoveryRan;

    private boolean mShowingFeatureDiscovery = false;

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
            mPhoneService.addStateChangeListener(DialerActivityInternal.this);
            if (!mForceUpdates) {
                if (mLoadUserInfo == null) {
                    mLoadUserInfo = new LoadUserInfo(true);
                    mLoadUserInfo.addUserInfoListener(DialerActivityInternal.this);
                }

                mLoadUserInfo.refreshUserInfo();
            }
            if (mDialDrawerFragment != null) {
                mDialDrawerFragment.setNumberNameEtc();
            }

            if (!mForceUpdates) {
                FindDialHelper.setDialHelper(DialerActivityInternal.this, false);
                mForceUpdates = true;
                mPhoneService.runContactsUpdater(true);
            }
            if (provisioningDone) {
                provisioningDone = false;
                if (!isDrawerOpen()) {
                    mDialpadFragment.removeDestinationFocus();
                }
                return;
            }
            if (mAutoDialRequested) {
                mDialpadFragment.dialButtonPressed();
                mAutoDialRequested = false;
            }
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
        mPhoneIsBound = bindService(new Intent(this, TiviPhoneService.class), phoneConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
    }

    private void doUnbindService() {
        if (mPhoneIsBound) {
            if (mPhoneService != null) {
                mPhoneService.removeStateChangeListener(this);
            }
            // Detach our existing connection.
            try {
                unbindService(phoneConnection);
            } catch (IllegalArgumentException ignore) {}
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
                    enterSearchUi(mIsDialpadShown, mSearchQuery, false /* conversations flag */, true /* animate */);
                }
            }

            if (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible()) {
                mSmartDialSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            } else if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                mRegularSearchFragment.setQueryString(mSearchQuery, false /* delaySelection */);
            }
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };

    /**
     * If the search term is empty and the user closes the soft keyboard, close the search UI.
     */
    private final View.OnKeyListener mSearchEditTextLayoutListener = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN &&
                    TextUtils.isEmpty(mSearchView.getText().toString())) {
                // Currently obsolete - we no longer have directory options, so always exit
                // maybeExitSearchUi();
                if (!mShowingFeatureDiscovery) {
                    exitSearchUi();
                }
                DialerUtils.hideInputMethod(mParentLayout);

                return true;
            }
            return false;
        }
    };

    /**
     * Callback from ExplainPermissionDialog after user read the explanation.
     *
     * After we explained the facts and the user read the explanation ask for permission again.
     *
     * @param token The token from ExplainPermissionDialog#showExplanation() call
     * @param callerBundle the optional bundle from ExplainPermissionDialog#showExplanation() call
     */
    @Override
    public void explanationRead(final int token, final Bundle callerBundle) {
        switch (token) {
            case PERMISSIONS_REQUEST_READ_PHONE_STATE:
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, token);
                break;
            case PERMISSIONS_REQUIRED_REQUEST_SP:
                ActivityCompat.requestPermissions(this, SP_PERMISSIONS_REQUIRED, token);
                break;
            case PERMISSIONS_REQUEST_EXIT:
                exitAndDelay();
                break;
            default:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // Occurs on rotation of permission dialog
        if (permissions.length == 0) {
            return;
        }

        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_PHONE_STATE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    onCreateAfterPermission();
                }
                else {
                    ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_EXIT,
                            getString(R.string.permission_main_not_granted_title), getString(R.string.permission_main_not_granted_explanation), null);
                }
                break;
            }
            case PERMISSIONS_REQUIRED_REQUEST_SP: {
                if (grantResults.length == SP_PERMISSIONS_REQUIRED.length) {
                    boolean fail = false;
                    for (int result : grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            fail = true;
                            ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_EXIT,
                                    getString(R.string.permission_main_not_granted_title), getString(R.string.permission_main_not_granted_explanation), null);
                            break;
                        }
                    }
                    if (!fail)
                        requiredPermissionGranted();
                }
                else {
                    ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_EXIT,
                            getString(R.string.permission_main_not_granted_title), getString(R.string.permission_main_not_granted_explanation), null);
                }
                break;
            }
            case PERMISSIONS_OPTIONAL_REQUEST_SP: {
                SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_OPTIONAL_PERMISSIONS_ASKED, true).apply();

                optionalPermissionsCompleted();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //This block of code to ensure SPA services (including closing communication sockets) have shutdown
        //before re-launch the app to avoid crash (NGA-666)
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        long timeMillisec =  pref.getLong(KEY_DIALER_ACTIVITY_FINISH, 0);
        if(timeMillisec != 0){
            pref.edit().putLong(KEY_DIALER_ACTIVITY_FINISH, 0).apply();
            long timeDiff = 4000 - (System.currentTimeMillis() - timeMillisec);
            if(timeDiff > 0){
                Utilities.Sleep(timeDiff);
            }
        }

        AutoStart.setDisableAutoStart(true);            // Don't auto start if we are started once
        Utilities.setTheme(this);

        super.onCreate(savedInstanceState);
        mSavedInstanceState = savedInstanceState;       // Copy it because we use it later after permission checks

        mPhoneCallProgressHandler = new Handler();

        if (mSavedInstanceState != null) {
            hasCheckedKeyManager = mSavedInstanceState.getBoolean(KEY_HAS_CHECKED_KEY_MANAGER);
            mKeyManagerCheckActive = mSavedInstanceState.getBoolean(KEY_MANAGER_CHECK_ACTIVE);
            hasKeyManager = mSavedInstanceState.getBoolean("KEY_MANAGER");
            mPermissionsExplained = mSavedInstanceState.getBoolean(KEY_PERMISSIONS_EXPLAINED);
            mSearchQuery = mSavedInstanceState.getString(KEY_SEARCH_QUERY);
            mSearchPhoneInput = mSavedInstanceState.getBoolean(KEY_SEARCH_PHONE_INPUT);
            mInRegularSearch = mSavedInstanceState.getBoolean(KEY_IN_REGULAR_SEARCH_UI);
            mInDialpadSearch = mSavedInstanceState.getBoolean(KEY_IN_DIALPAD_SEARCH_UI);
            mShowDialpadOnResume = mSavedInstanceState.getBoolean(KEY_IS_DIALPAD_SHOWN);
            mAutoDialRequested = mSavedInstanceState.getBoolean(KEY_IS_AUTO_DIAL);
            mIsInSettingsUi = mSavedInstanceState.getBoolean(KEY_IN_SETTINGS_UI);
        }

        mIsInstanceStateSaved = false;

        // Executed by mDrawerLayout.postDelayed() to enter the settings
        // This is removed from the message queue if it exists when the activity becomes hidden
        // - onStop().
        mEnteringSettingsUiRunner = new Runnable() {
            @Override
            public void run() {
                enterSettingsUI();
            }
        };

        mClosingDrawerRunner = new Runnable() {
            @Override
            public void run() {
                mDrawerLayout.closeDrawer(mDrawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(false);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        };

        // We need to phone state permission early because we register phone state monitoring
        // and use the IMEI (plus some other data) and hash them to construct a device id. Thus
        // the phone state permission is required before starting/initializing the phone service.
        checkPhoneStatePermissions();
    }

    private void checkPhoneStatePermissions() {
        // Pause creation flow if Passcode does not allow us. There is only one exception though.
        // If the activity is recreated and keyManager was checked last time. This will allow
        // DoLayout() to be called in time and have the layout restored correctly.
        if (hasCheckedKeyManager || !PasscodeController.getSharedController().shouldWaitForPasscode(this)) {
            mShouldContinueCreationFlow = false;
            doCheckPhoneStatePermissions();
        }
        else {
            mShouldContinueCreationFlow = true;
        }
    }

    private void doCheckPhoneStatePermissions() {
        TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        PackageManager pm = getPackageManager();

        boolean needPhoneStatePermission = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        if (needPhoneStatePermission &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_PHONE_STATE)) {
                ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUEST_READ_PHONE_STATE,
                        getString(R.string.permission_phone_title), getString(R.string.permission_phone_explanation), null);
            }
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE},
                        PERMISSIONS_REQUEST_READ_PHONE_STATE);
            }
        }
        else {
            onCreateAfterPermission();
        }
    }

    @SuppressWarnings("ResourceType")
    private void onCreateAfterPermission() {
        ViewUtil.setBlockScreenshots(this);

        String action = getIntent().getAction();

        mRemoveAccount = ACTION_REMOVE_ACCOUNT.equals(action);

        mStartOnBoot = AutoStart.ON_BOOT.equals(action);
        mStartForMessaging = START_FOR_MESSAGING.equals(action);

        if ((InCallActivity.ADD_CALL_ACTION.equals(action)
                || Action.VIEW_CONVERSATIONS.equals(Action.from(getIntent())))
                && TiviPhoneService.calls.getCallCount() > 0) {
            // This is a phone call screen to add a call, thus perform some specific handling
            int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
            getWindow().addFlags(windowFlags);
        }

        ConfigurationUtilities.initializeDebugSettings(getBaseContext());

        if (Build.VERSION.SDK_INT >= 21 && ConfigurationUtilities.mUseDevelopConfiguration) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }

        if (mDomainsToRemove == null)
            mDomainsToRemove = getResources().getStringArray(R.array.domains_to_remove);

        Log.d(TAG, "Debug: " + ConfigurationUtilities.mTrace + ", language: " + Locale.getDefault().getLanguage()
                + ", develop config: " + ConfigurationUtilities.mUseDevelopConfiguration + ", build commit: +-+- " +
                BuildConfig.SPA_BUILD_COMMIT);

        TiviPhoneService.initJNI(getBaseContext());
        isProvisioned = !(TiviPhoneService.phoneService == null && TiviPhoneService.doCmd("isProv") == 0);

        /* possibly previously provisioned application, allow it to start on boot */
        if (isProvisioned) {
            setStartOnBoot();
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received intent: " + getIntent());

        final boolean isLayoutRtl = Utilities.isRtl();
        if (isLandscape) {
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
        // restart, usually because of config changes (screen rotation) or memory issues. In this
        // case we _must_ call the keyManagerCheck (which calls doLayout) function before we
        // leave onCreate. Otherwise the system does not restore the fragments correctly.
        // On a restart we don't need to check and set the key manager, we take the saved
        // state.
        //
        // checkAndSetKeyManager leaves onCreate without doing the layout because it starts
        // an Activity for result. The result action may call the layout function. On a new
        // start this is not a problem.
        setResult(RESULT_CANCELED);
        if (mSavedInstanceState == null) {
            checkAndSetKeyManager();
        }
        else {
            // When the activity is recreated and SPa has already been initialized, `hasCheckedKeyManager`
            // is true and `mKeyManagerCheckActive` is false. `hasCheckedKeyManager` can be false if
            // passcode was shown before SPa initialized and the activity was destroyed to release
            // memory. Both of them can be true if the activity was destroyed while presenting the
            // KeyStoreActivity.
            if (!hasCheckedKeyManager) {
                checkAndSetKeyManager();
            }
            else if (!mKeyManagerCheckActive){
                keyManagerChecked();
            }
        }
        mSavedInstanceState = null;             // Not needed anymore
        // Register even if not key manager may not be active
        KeyManagerSupport.addListener(this);

        final Resources.Theme theme = getTheme();
        final TypedArray array = theme != null ? theme.obtainStyledAttributes(new int[]{
                R.attr.sp_ic_dial_pad,
                R.attr.sp_ic_new_chat,
        }) : null;

        if (array != null) {
            mDialPadIcon = array.getDrawable(0);
            mNewChatIcon = array.getDrawable(1);
            array.recycle();
        }
        else {
            mDialPadIcon = ContextCompat.getDrawable(this, R.drawable.ic_action_dial_pad_light);
            mNewChatIcon = ContextCompat.getDrawable(this, R.drawable.ic_add_white);
        }

        // Store this in a static variable to not call PreferenceManager#getDefaultSharedPreferences
        // for every error view
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mShowErrors = prefs.getBoolean(SettingsFragment.SHOW_ERRORS, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Resume the creation flow if it was stopped due to passcode. ProccessIntent will be called
        // later on, so we won't do it here.
        if (mShouldContinueCreationFlow) {
            checkPhoneStatePermissions();
        }
        // The new intent can be processed now.
        else if (mShouldProcessNewIntent) {
            processIntent(getIntent());
        }
    }
    @Override
    public void onResume() {
        super.onResume();

        if (mIsInstanceStateSaved) {
            mIsInstanceStateSaved = false;
        }

        if(mDialerDatabaseHelper != null
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            mDialerDatabaseHelper.startSmartDialUpdateThread();
        }

        if (isDialpadShown()) {
            hideDialpadFragment(false, true);
        }

        // Exit the search UI if the user had previously transitioned from it
        Intent intent = getIntent();
        boolean fromCall = intent != null && intent.hasExtra(EXTRA_FROM_CALL);
        boolean toCall = intent != null && SILENT_CALL_ACTION.equals(intent.getAction());
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        boolean transitioned = prefs.getBoolean(KEY_HAD_TRANSITIONED, false);
        if (transitioned) {
            if (!fromCall && !toCall) {
                prefs.edit().putBoolean(KEY_HAD_TRANSITIONED, false).apply();
            }

            if (isInSearchUi()) {
                exitSearchUi();
            }
        }

//        if (mShowDialpadOnResume) {
//            showDialpadFragment(false, mAutoDialRequested /* autoDial */);
//            mShowDialpadOnResume = false;
//        }

        if (mInRegularSearch) {
            updateSearchFragmentSettings();
        }

        if (isInSettingsUi()) {
            enterSettingsUI();
        }

        if (mShowCreditDialog) {
            mShowCreditDialog = false;

            if (LoadUserInfo.checkIfUsesMinutes() == LoadUserInfo.VALID &&
                    LoadUserInfo.checkIfLowMinutes(LoadUserInfo.DEFAULT_LOW_MINUTES_THRESHHOLD) == LoadUserInfo.VALID) {
                showDialog(R.string.information_dialog, R.string.minutes_low_info,
                        android.R.string.ok, -1);
            } else if (LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID &&
                    LoadUserInfo.checkIfLowCredit(LoadUserInfo.DEFAULT_LOW_CREDIT_THRESHHOLD) == LoadUserInfo.VALID) {
                showDialog(R.string.information_dialog, R.string.credit_low_info,
                        android.R.string.ok, -1);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mDrawerLayout != null) {
            // Prevent a FragmentTransaction from committing after the activity is destroyed
            // Committing after onSaveInstanceState() (or onStop())
            // causes 'IllegalStateException: Can not perform this action after onSaveInstanceState'
            mDrawerLayout.removeCallbacks(mEnteringSettingsUiRunner);

            // Restore the action bar when 'mClosingDrawerRunner' has already executed and
            // 'enterSettingsUI()' in the Runnable class above is canceled
            // See'closeDrawerAndShowSettingsView()'
            if (mDrawerToggle != null && !mDrawerToggle.isDrawerIndicatorEnabled()) {
                exitSubView();
            }
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDestroyed = true;
        if (mLoadUserInfo != null) {
            mLoadUserInfo.removeUserInfoListener(this);
        }
        KeyManagerSupport.removeListener(this);
        doUnbindService();

        //mBigMContactsHack is particularly used for Hacking Build.VERSION_CODES.M
        //In processIntent() method, mBigMContactsHack = numberUri.toString() if Build.VERSION_CODES.M
        //After call ended, value of mBigMContactsHack needs to be cleared to avoid fail to
        //make outgoing call by click on sp icon from native contacts
        mBigMContactsHack = null;
    }

    @Override
    public void onSaveInstanceState (@NonNull Bundle outState) {
        outState.putBoolean(KEY_HAS_CHECKED_KEY_MANAGER, hasCheckedKeyManager);
        outState.putBoolean(KEY_MANAGER_CHECK_ACTIVE, mKeyManagerCheckActive);
        outState.putBoolean(KEY_PERMISSIONS_EXPLAINED, mPermissionsExplained);
        outState.putBoolean(KEY_KEY_MANAGER, hasKeyManager);
        outState.putString(KEY_SEARCH_QUERY, mSearchQuery);
        outState.putBoolean(KEY_SEARCH_PHONE_INPUT, mSearchPhoneInput);
        outState.putBoolean(KEY_IN_REGULAR_SEARCH_UI, mInRegularSearch);
        outState.putBoolean(KEY_IN_DIALPAD_SEARCH_UI, mInDialpadSearch);
        outState.putBoolean(KEY_IS_DIALPAD_SHOWN, mIsDialpadShown);
        outState.putBoolean(KEY_IS_AUTO_DIAL, mAutoDialRequested);
        outState.putBoolean(KEY_IN_SETTINGS_UI, isInSettingsUi());
        super.onSaveInstanceState(outState);

        mIsInstanceStateSaved = true;
    }

    @Override
    protected void onNewIntent (Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received new intent: " + intent);
        processIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode: " + requestCode + " resultCode: " + resultCode);

        if (requestCode == PROVISIONING_RESULT) {
            if (resultCode != RESULT_OK) {
                exitAndDelay();
            }
            else {
                provisioningDone = true;
                isProvisioned = true;
                setStartOnBoot();
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
                exitAndDelay();
            }
            else {
                keyManagerChecked();
            }
        }
    }

    /*
     * Dial drawer callbacks
     */
    @Override
    public void onDrawerItemSelected(int type, Object... params) {
        Intent intent;
        switch (type) {
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
                exitSettingsUi();
                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.hide(mListsFragment).commitAllowingStateLoss();
                checkDeviceStatus();
                break;

            case DialDrawerFragment.DrawerCallbacks.DEVELOPER_SSL_DEBUG:
                TiviPhoneService.doCmd("debug.option=ssl_level:" + (int)params[0]);
                break;

            case DialDrawerFragment.DrawerCallbacks.DEVELOPER_ZINA_DEBUG:
                TiviPhoneService.doCmd("debug.option=zina_level:" + (int)params[0]);
                break;

            case DialDrawerFragment.DrawerCallbacks.SETTINGS:
                closeDrawerAndShowSettingsView();
                break;
        }
    }

    @Override
    public void onDrawerStateChange(int action) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isDrawerOpen()) {
//            getMenuInflater().inflate(R.menu.group_chat, menu);
            getMenuInflater().inflate(R.menu.dialpad, menu);
            ViewUtil.tintMenuIcons(this, menu);
            // hide search entry, currently not used
            MenuItem menuSearch = menu.findItem(R.id.dial_menu_search);
            menuSearch.setVisible(false);

            restoreActionBar();

            if (!isDrawerOpen() && !isInSettingsUi()) {
                new Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isInSearchUi()) {
                            featureDiscoveryOnboarding();
                        } else {
                            boolean showConversationFeatureDiscovery = shouldShowConversationFeatureDiscovery();
                            View listView = mRegularSearchFragment != null ? mRegularSearchFragment.getListView() : null;

                            if (showConversationFeatureDiscovery) {
                                mNewConversationFeatureDiscoveryRan = true;

                                featureDiscoveryNewConversation();
                            } else {
                                if (listView != null) listView.setEnabled(true);
                                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!Utilities.isNetworkConnected(DialerActivityInternal.this)) {
                                            showDialog(R.string.network_not_available_title,
                                                    R.string.network_not_available_description, android.R.string.ok, -1);
                                        }
                                    }
                                }, 100);
                            }
                        }
                    }
                });
            } else {
                View listView = mRegularSearchFragment != null ? mRegularSearchFragment.getListView() : null;
                if (listView != null) listView.setEnabled(true);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }

            return true;
        }
        if (isDrawerOpen() && mDialDrawerFragment.isVisible()) {
            return false;
        }
        if (mPendingSearchViewQuery != null) {
            mSearchView.setText(mPendingSearchViewQuery);
            mPendingSearchViewQuery = null;
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isInSubView = (isInSearchUi() || isInSettingsUi());
        if (isInSubView) {
            mDrawerToggle.setDrawerIndicatorEnabled(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.dial_menu_search:
                if (!isInSearchUi()) {
                    openActionBarQueryField();
                    enterSearchUi(false /* smartDialSearch */, mSearchView.getText().toString(),
                            false /* conversations flag */,  true /* animate */);
                    invalidateOptionsMenu();
                }
                return true;

            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void featureDiscoveryOnboarding() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        boolean startDiscovery = !prefs.getBoolean(KEY_FEATURE_DISCOVERY_ONBOARDING, BuildConfig.DEBUG);

        SharedPreferences defPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean forceStartDiscovery = defPrefs.getBoolean(SettingsFragment.FORCE_FEATURE_DISCOVERY, false);

        if (!mOnboardingFeatureDiscoveryRan && (startDiscovery || forceStartDiscovery)) {
            mOnboardingFeatureDiscoveryRan = true;
        }
        else {
            // feature discovery already shown, return
            return;
        }

        List<TapTarget> targets = new ArrayList<>();
        targets.add(ViewUtil.applyDefTapTargetParams(
                TapTarget.forToolbarNavigationIcon(mToolbar,
                        getString(R.string.discovery_onboarding_1_title),
                        getString(R.string.discovery_onboarding_1_desc)))
                .id(2));
        targets.add(ViewUtil.applyDefTapTargetParams(
                TapTarget.forView(mFloatingActionButton,
                        getString(R.string.discovery_onboarding_2_title),
                        getString(R.string.discovery_onboarding_2_desc)))
                .targetCircleColor(R.color.feature_discovery_target_circle)
                .transparentTarget(true)
                .id(1));

        final TapTargetSequence sequence = new TapTargetSequence(this)
                .targets(targets)
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putBoolean(KEY_FEATURE_DISCOVERY_ONBOARDING, true).apply();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                        // nothing to do here
                    }
                });

        sequence.continueOnCancel(true);
        sequence.considerOuterCircleCanceled(true);
        sequence.start();
    }

    private void featureDiscoveryNewConversation() {
        mShowingFeatureDiscovery = true;
        List<TapTarget> targets = new ArrayList<>();

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        final int width = metrics.widthPixels;
        final int height = metrics.heightPixels;

        String displayOrg = LoadUserInfo.getDisplayOrg();
        if (TextUtils.isEmpty(displayOrg)) {
            displayOrg = "Silent Circle";
        }

        targets.add(ViewUtil.applyDefTapTargetParams(
                TapTarget.forBounds(new Rect((int) (0.75 * width), height, width, height),
                        getString(R.string.discovery_search_1_title),
                        getString(R.string.discovery_search_1_desc)))
                .targetRadius(0)
                .id(1));

        if (LoadUserInfo.canCallOutboundOca(this)) {
            targets.add(ViewUtil.applyDefTapTargetParams(
                    TapTarget.forBounds(new Rect(width, height, 0, height),
                            getString(R.string.discovery_search_2_title),
                            getString(R.string.discovery_search_2_desc)))
                    .targetRadius(0)
                    .id(2));
        }

        targets.add(ViewUtil.applyDefTapTargetParams(
                TapTarget.forBounds(new Rect(width, (int) Math.floor(-0.75 * height), 0, height),
                        getString(R.string.discovery_search_3_title),
                        String.format(getString(R.string.discovery_search_3_desc), displayOrg)))
                .targetRadius(0)
                .id(3));

        View keypadButton = getSupportActionBar().getCustomView().findViewById(R.id.keypad_toggle_button);
        if (keypadButton != null) {
            targets.add(ViewUtil.applyDefTapTargetParams(
                    TapTarget.forView(keypadButton,
                            getString(R.string.discovery_search_4_title),
                            getString(R.string.discovery_search_4_desc)))
                    .id(4));
        }

        final TapTargetSequence sequence = new TapTargetSequence(this)
                .targets(targets)
                .listener(new TapTargetSequence.Listener() {
                    @Override
                    public void onSequenceFinish() {
                        mShowingFeatureDiscovery = false;
                        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
                        prefs.edit().putBoolean(KEY_FEATURE_DISCOVERY_NEW_CONVERSATION, true).apply();
                    }

                    @Override
                    public void onSequenceStep(TapTarget lastTarget, boolean targetClicked) {
                    }

                    @Override
                    public void onSequenceCanceled(TapTarget lastTarget) {
                    }
                });

        sequence.continueOnCancel(true);
        sequence.considerOuterCircleCanceled(true);

        sequence.start();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                if (!isInSearchUi()) {
                    openActionBarQueryField();
                    enterSearchUi(false /* smartDialSearch */, mSearchView.getText().toString(),
                            false /* conversations flag */,  true /* animate */);
                    invalidateOptionsMenu();
                }
                break;
            default:
                break;
        }
        ActivityManager.isUserAMonkey();
    }

    @Override
    public void onBackPressed() {
        if (mDialpadFragment != null && !mDialpadFragment.isHidden()) {
            if (TextUtils.isEmpty(mSearchQuery) ||
                    (mSmartDialSearchFragment != null && mSmartDialSearchFragment.isVisible())) {
                if (mDialpadFragment.isDialpadVisible() &&
                        mSmartDialSearchFragment.getAdapter().getCount() > 0) {
                    mDialpadFragment.setDialpadVisible(false);

                    return;
                } else {
                    exitSearchUi();
                }
            }

            hideDialpadFragment(true, false);
        }
        else if (isInSearchUi()) {
            DialerUtils.hideInputMethod(mParentLayout);
            exitSearchUi();
        }
        else if (isInSettingsUi()) {
            exitSettingsUi();
        }
        else {
            boolean handled = false;
            if (mDrawerLayout != null) {
                if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                    handled = true;
                }
            }

            if (!handled) {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    // If Dialer detects special prefix it calls internal handling
    @Override
    public void internalCall(String internalCommand) {
        if (internalCommand.indexOf("*##*") == 0) {
            if ("*##*943*".compareTo(internalCommand) == 0) {
                mEnableShowZid = !mEnableShowZid;
                return;
            }
            if (!DialCodes.internalCallStatic(this, mPhoneService, internalCommand)) {
                TiviPhoneService.doCmd(internalCommand);  // do some internal voodoo (for example delete config files ;-) )
                if("*##*3357768*".compareTo(internalCommand) == 0) {
                    exitAndDelay();
                }
            }
        }
    }

    @Override
    public void doCall(String callCommand, String destination, boolean isOcaCall) {
        mPhoneCallProgressHandler.removeCallbacksAndMessages(null);
        if (mRegularSearchFragment != null) {
            mRegularSearchFragment.setProgressEnabled(false);
        }

        if (mPhoneIsBound) {
            Utilities.audioMode(getBaseContext(), true);        // Switch to IN-COMMUNICATION mode

            AsyncTasks.asyncCommand(callCommand);
            if (mCallActionIntent) {
                setResult(RESULT_OK);
                finish();
                // If a task didn't exist and a new one was created with root activity this one.
                // When we try to enter it from recent tasks, a call will always be made because
                // the current intent will be used. The following, allows us to remove the task from
                // recents.
                Intent intent = new Intent(this, HideTaskActivity.class);
                startActivity(intent);
            }
            mPhoneService.showCallScreen(TiviPhoneService.CALL_TYPE_OUTGOING, destination, isOcaCall);
        }
    }

    @Override
    public void onInitCallCancelled(String destination) {
        mPhoneCallProgressHandler.removeCallbacksAndMessages(null);
        if (mRegularSearchFragment != null) {
            mRegularSearchFragment.setProgressEnabled(false);
        }
    }

    @Override
    public void checkDoFinish() {
        if (mCallActionIntent) {
            setResult(RESULT_OK);
            finish();
        }
    }

    /* finalize application and exit */
    public void exitAndDelay() {
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mDrawerView);
        }
        Log.d(TAG, "exitApplication");

        //save app exit time in milliseconds,  it will be used when relaunch the app to determine
        //if it needs delay to start dialer activity and services, because the app (including all services and communication sockets)
        //needs to be shutdown completely before relaunch it to avoid crash.
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong(KEY_DIALER_ACTIVITY_FINISH, System.currentTimeMillis()).apply();

        stopService(new Intent(this, SCloudService.class));
        new Thread(new Runnable() {
            public void run() {
                TiviPhoneService.doCmd(".exit");
                stopServiceAndExit(400);
            }
        }).start();

        finish();
    }

    @SuppressWarnings("deprecation")
    public void wipePhone() {
        // This is only visible for non-production users who are API 19+ (4.4+)
        if (mDrawerLayout != null) {
            mDrawerLayout.closeDrawer(mDrawerView);
        }

        // Unregister from GCM
        try {
            InstanceID.getInstance(this).deleteInstanceID();
        } catch (Exception ignore) {}

        final String deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(this, false));
        Log.w(TAG, "Wiping device " + deviceId);
        API.V1.Me.Device.delete(this, deviceId, new API.Callback() {
            @Override
            public void onComplete(HttpUtil.HttpResponse httpResponse, Exception exception) {
                if (exception != null) {
                    Log.w(TAG, "Error on device delete: " + exception.getMessage());
                }
                if (httpResponse != null) {
                    Log.d(TAG, "Delete device, response: " + httpResponse.responseCode);
                }
                if (httpResponse == null || httpResponse.responseCode != 200) {
                    Log.w(TAG, "Could not delete device " + deviceId + " on wipe.");
                }

                if (!Utilities.wipePhone(DialerActivityInternal.this)) {
                    // Sanity toast
                    Toast.makeText(DialerActivityInternal.this, R.string.wipe_data_failed, Toast.LENGTH_LONG).show();
                }

                DialerActivityInternal.this.finish();
            }
        });
    }

    public void restartClient() {
        TiviPhoneService.doCmd(".exit");
        doUnbindService();
        stopService(new Intent(DialerActivityInternal.this, TiviPhoneService.class));
        mServiceStarted = false;
        FindDialHelper.resetDialHelper();

        Utilities.Sleep(3000);
        mUuid = null;
        // get the intent that started us and use it to restart
        Intent intent = getIntent();
        finish();
        startActivity(intent);
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

    @SuppressLint("CommitPrefEdits")
    private void removeProvisioningAndRestart(final boolean force) {
        Log.d(TAG, "removeProvisioningAndRestart force: " + force);

        if (force) {
//             TODO: These are the beginning steps of a non-clear-data solution of logging out
//             FIXME: more will have to be decided
//            // Clear call log
//            getContentResolver().delete(ScCallLog.ScCalls.CONTENT_URI, null, null);
//
//            // Clear conversation repository
//            AxoMessaging axoMessaging = AxoMessaging.getInstance(this);
//
//            if (axoMessaging != null) {
//                ConversationRepository conversations = axoMessaging.getConversations();
//
//                if (conversations != null) {
//                    conversations.clear();
//                }
//            }
//
//            // Flag AxoMessaging instance to no longer be ready
//            axoMessaging.setReady(false);
//
//            // Flag AxoMessaging instance to no longer be registered
//            axoMessaging.setRegistered(false);
//
//            // Clear ZRTP cache
//            TiviPhoneService.doCmd("*##*9787257*");

            // Delete shared key data
            KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        }
        else {
            final SharedPreferences prefs =  getSharedPreferences(ProvisioningActivity.PREF_KM_API_KEY, Context.MODE_PRIVATE);
            prefs.edit().putString(ConfigurationUtilities.getReprovisioningNameKey(), mUuid).commit();
        }
        TiviPhoneService.doCmd("*##*3357768*");         // remove existing provisioning files

        restartClient();
    }

    // Reset in case Drawer window changed its state
    private void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null || isInSearchUi() || isInSettingsUi())
            return;
        actionBar.setTitle(getString(R.string.app_name));
        actionBar.setSubtitle(null);

        // Get the user's UUID/name, store it in mUuid, UUID is not very readable at all.
        if (TextUtils.isEmpty(mUuid)) {
            mUuid = LoadUserInfo.getUuid();
        }

        if (TextUtils.isEmpty(mDisplayTn)) {
            mDisplayTn = LoadUserInfo.getDisplayTn();
        }

        if (TextUtils.isEmpty(mDisplayAlias)) {
            mDisplayAlias = LoadUserInfo.getDisplayAlias();
        }

        if (TextUtils.isEmpty(mDisplayName)) {
            mDisplayName = LoadUserInfo.getDisplayName();
        }

        if (!TextUtils.isEmpty(mDisplayAlias)) {
            final String subtitle = mDisplayAlias +
                    (!TextUtils.isEmpty(mDisplayTn) ? "/" + Utilities.formatNumber(mDisplayTn) : "");

            actionBar.setSubtitle(subtitle);
            Utilities.setSubtitleColor(this, mToolbar);
        }
    }

    private void startPhoneService() {
        // Check and set the secret key
        if (hasKeyManager && TiviPhoneService.getSipPasswordStatus() == TiviPhoneService.SIP_PASSWORD_NOT_AVAILABLE) {
            setSipAuthentication();
       }

        /* Initialize TiVi library from settings in the preferences. This must be done _after_ starting the
         * TiviEngine via initJNI() and _after_ setting the key data.
         */
        SettingsFragment.applyDefaultDeprecatedSettings(this);
        activateTraversal(getApplicationContext());
        activateDropoutTone(getApplicationContext());
        activateDebugSettings(getApplicationContext());
        indicateDataRetention();

        if (!mServiceStarted) {
            startService(new Intent(this, TiviPhoneService.class));
            mServiceStarted = true;            // service sends ":reg"
        }
        else if (TiviPhoneService.phoneService != null && TiviPhoneService.phoneService.isReady())
            AsyncTasks.asyncCommand(":reg"); // doCmd(":reg"); - do it async to avoid ANR in case network is slow
    }

    // We may need to enhance the "checkKeyManager" process in case we use keys to encrypt
    // the config files. Then we may need to check more often and at different places, needs to
    // be investigated.
    private void checkAndSetKeyManager() {
        hasCheckedKeyManager = true;
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
            final int pwType = KeyStoreHelper.getUserPasswordType(this);
            if (mStartOnBoot && !(pwType == KeyStoreHelper.USER_PW_TYPE_NONE || pwType == KeyStoreHelper.USER_PW_SECURE)) {
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

        if (!isProvisioned) {
            if (mStartOnBoot || mStartForMessaging) {  // no automatic start if client not yet provisioned
                finish();
                return;
            }
            Intent provisioningIntent = new Intent(this, ProvisioningActivity.class);
            provisioningIntent.putExtra("KeyManager", hasKeyManager);
            startActivityForResult(provisioningIntent, PROVISIONING_RESULT);
            return;
        }

        checkRequiredPermissions();
    }

    private String[] SP_PERMISSIONS_REQUIRED = {
            Manifest.permission.RECORD_AUDIO
    };

    private String[] SP_PERMISSIONS_OPTIONAL = {
            Manifest.permission.WRITE_CONTACTS
    };

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void checkRequiredPermissions() {
        if (!hasPermissionsGranted(SP_PERMISSIONS_REQUIRED)) {
            if (shouldShowRequestPermissionRationale(SP_PERMISSIONS_REQUIRED)) {
                if (mPermissionsExplained) {
                    return;
                }

                String text = getString(R.string.permission_sp_main_explanation);
                CharSequence styledText = Html.fromHtml(text);

                ExplainPermissionDialog.showExplanation(this, PERMISSIONS_REQUIRED_REQUEST_SP,
                        getString(R.string.permission_sp_main_title), styledText, null);

                mPermissionsExplained = true;
            }
            else {
                ActivityCompat.requestPermissions(this, SP_PERMISSIONS_REQUIRED, PERMISSIONS_REQUIRED_REQUEST_SP);
            }
        }
        else {
            requiredPermissionGranted();
        }
    }

    private void checkOptionalPermissions() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        boolean optionalPermissionsAsked = prefs.getBoolean(KEY_OPTIONAL_PERMISSIONS_ASKED, false);

        if (!optionalPermissionsAsked && !hasPermissionsGranted(SP_PERMISSIONS_OPTIONAL)) {
            ActivityCompat.requestPermissions(this, SP_PERMISSIONS_OPTIONAL, PERMISSIONS_OPTIONAL_REQUEST_SP);
        } else {
            optionalPermissionsCompleted();
        }
    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
    private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }

    private void requiredPermissionGranted() {
        checkOptionalPermissions();
    }

    private void optionalPermissionsCompleted() {
        // Try to register the contact observer - the user may have now accepted contact permissions
        ContactsCache.registerContactObserver();

        startPhoneService();             // start phone service
        if (mStartOnBoot || mStartForMessaging) {
            finish();
            return;
        }
        doLayout();
        doBindService();
//        mHandler.sendEmptyMessageDelayed(HIDE_WELCOME, 1500);
    }

    ActionBarDrawerToggle mDrawerToggle;

    // Called after service was connected, actually the third part of onCreate
    @SuppressWarnings("deprecation")
    private void doLayout() {
        try {
            setContentView(R.layout.activity_dialer_new);
        } catch (InflateException exception) {
            Log.e(TAG, "Error inflating the activity", exception);

            finish();
            return;
        }

        // Set up the drawer.
        mDialDrawerFragment = (DialDrawerFragment)getFragmentManager().findFragmentById(R.id.dial_content_drawer);
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);

        if (getIntent() != null && SILENT_CALL_ACTION.equals(getIntent().getAction())) {
            getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(this, R.color.black_background));
            findViewById(R.id.dialer_main_layout).setVisibility(View.GONE);
            // Show a progress bar after 1.25 seconds (for slow networks)
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(DialerActivityInternal.this, R.color.sc_ng_background));
                    findViewById(R.id.dialer_progress_bar).setVisibility(View.VISIBLE);
                }
            }, 1250);
        }

        mDrawerView = findViewById(R.id.dial_content_drawer);
        mToolbar = (Toolbar)findViewById(R.id.dialer_toolbar);
        setSupportActionBar(mToolbar);

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                mToolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()


            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                if (mDialDrawerFragment != null) {
                    mDialDrawerFragment.setNumberNameEtc();
                    mDialDrawerFragment.setUserInfo();

                    final boolean developer =
                            SPAPreferences.getInstance(SilentPhoneApplication.getAppContext()).isDeveloper();
                    mDialDrawerFragment.setDeveloperMode(developer);
                }
            }
        };

        Drawable homeAsUp = ContextCompat.getDrawable(this, R.drawable.ic_arrow_back_black_24dp);
        homeAsUp = DrawableCompat.wrap(homeAsUp);
        DrawableCompat.setTint(homeAsUp,
                getResources().getColor(
                        ViewUtil.getColorIdFromAttributeId(this,
                                R.attr.sp_actionbar_title_text_color)));
        mDrawerToggle.setHomeAsUpIndicator(homeAsUp);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerToggle.setToolbarNavigationClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mDrawerLayout.setDrawerListener(mDrawerToggle);

        // Defer code dependent on restoration of previous instance state.
        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerToggle.syncState();
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            throw new AssertionError("Fatal: Action bar is null.");

        //noinspection ConstantConditions
        actionBar.setCustomView(R.layout.search_edittext);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);

        SearchEditTextLayout searchEditTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
        searchEditTextLayout.setPreImeKeyListener(mSearchEditTextLayoutListener);

        mSearchView = (EditText)searchEditTextLayout.findViewById(R.id.search_view);
        mSearchView.addTextChangedListener(mPhoneSearchQueryTextListener);

        searchEditTextLayout.setOnBackButtonClickedListener(new SearchEditTextLayout.OnBackButtonClickedListener() {
            @Override
            public void onBackButtonClicked() {
                onBackPressed();
            }
        });

        searchEditTextLayout.setOnInputSwitchedListener(new SearchEditTextLayout.OnInputSwitchedListener() {
            @Override
            public void onInputSwitched(int inputType) {
                if (!isInSearchUi()) {
                    return;
                }

                if (mRegularSearchFragment == null) {
                    return;
                }

                mSearchPhoneInput = inputType == InputType.TYPE_CLASS_PHONE;
                mRegularSearchFragment.setPhoneInput(inputType == InputType.TYPE_CLASS_PHONE);
            }
        });

        mParentLayout = (FrameLayout)findViewById(R.id.dialer_main_layout);

        mFloatingActionButton = (android.support.design.widget.FloatingActionButton) findViewById(R.id.floating_action_button);
        mFloatingActionButton.setOnClickListener(this);

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
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            showBatteryOptInfo();
//        }
    }

    /**
     * Read API-key from key manager, compute device id and set both as SIP authentication data.
     *
     * If no API-key is available or device id computation fails the application exits.
     */
    private void setSipAuthentication() {
        byte[] sipPwd = KeyManagerSupport.getSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        if (sipPwd == null) {
            showToast("No data to authenticate SIP register.");
            Log.e(TAG, "No data to authenticate SIP register.");
            exitAndDelay();
        }
        else {
            TiviPhoneService.setSipPassword(sipPwd);
        }
        String deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(this, false));
        if (deviceId == null) {
            showToast("No device id to authenticate SIP register.");
            Log.e(TAG, "No device id to authenticate SIP register.");
            exitAndDelay();
        }
        else {
            TiviPhoneService.setSIPAuthName(deviceId);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    /*
     * TODO: Extract a util out of this to be used in other places
     */
    public static boolean checkCallToNr(String number, DialpadFragment pad) {
        if (TextUtils.isEmpty(number))
            return false;

        number = number.trim();
        if (Utilities.isUriNumber(number)) {
            number = Utilities.removeUriPartsSelective(number);
        }
        boolean wasModified = false;
        char firstChar = number.charAt(0);
        String formatted = null;
        if (firstChar == '+' || Character.isDigit(firstChar)) {
            StringBuilder modified = new StringBuilder(20);
            wasModified = FindDialHelper.getDialHelper().analyseModifyNumberString(number, modified);
            if (wasModified) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    formatted = PhoneNumberUtils.formatNumber(modified.toString(), Locale.getDefault().getCountry());
                else
                    formatted = PhoneNumberUtils.formatNumber(modified.toString());
            }
        }
        pad.setDestination(formatted != null ? formatted : number);
        return wasModified;
    }

    public String formatSearchInput(String input) {
        if (TextUtils.isEmpty(input))
            return "";

        input = input.trim();
        if (Utilities.isUriNumber(input)) {
            input = Utilities.removeUriPartsSelective(input);
        }
        boolean wasModified = false;
        char firstChar = input.charAt(0);
        String formatted = null;
        if (firstChar == '+' || Character.isDigit(firstChar)) {
            StringBuilder modified = new StringBuilder(20);
            wasModified = FindDialHelper.getDialHelper().analyseModifyNumberString(input, modified);
            if (wasModified) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                    formatted = PhoneNumberUtils.formatNumber(modified.toString(), Locale.getDefault().getCountry());
                else
                    formatted = PhoneNumberUtils.formatNumber(modified.toString());
            }
        }

        return formatted != null ? formatted : input;
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Perform an orderly application shutdown and exit.
     */
    private void stopServiceAndExit(int delay) {
        Log.d(TAG, "stopServiceAndExit");

        if (hasKeyManager)
            KeyManagerSupport.unregisterFromKeyManager(getContentResolver());
        KeyStoreHelper.closeStore();
        stopService(new Intent(DialerActivityInternal.this, TiviPhoneService.class));
        doUnbindService();
        Utilities.Sleep(delay);
        System.exit(0);
    }

    private void removeAccount() {
        KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardAuthTag());
        KeyManagerSupport.deleteSharedKeyData(getContentResolver(), ConfigurationUtilities.getShardDevIdTag());

        TiviPhoneService.doCmd("*##*3357768*");  // delete config files ;-)
        exitAndDelay();
    }

    private final String[] projection = {ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1
    };

    private static final int MIME_TYPE = 0;
    private static final int NUMBER_OR_NAME = 1;

    public static final String PHONE_MIME = "vnd.android.cursor.item/com.silentcircle.phone";
    public static final String MSG_MIME = "vnd.android.cursor.item/com.silentcircle.message";

    private Intent intentProcessed;
    private static String mBigMContactsHack;

    /**
     * Calls {@link #doProcessIntent(Intent)} if PasscodeController allows it. Otherwise, we store
     * the intent and process it later when ({@link #onStart()} is called.
     */
    private void processIntent(Intent intent) {
        setIntent(intent);
        if (!mShouldContinueCreationFlow && !PasscodeController.getSharedController().shouldWaitForPasscode(this)) {
            mShouldProcessNewIntent = false;
            doProcessIntent(intent);
        }
        else {
            mShouldProcessNewIntent = true;
        }
    }

    private void doProcessIntent(Intent intent) {
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

        Uri numberUri = intent.getData();

        // act=android.intent.action.VIEW dat=content://com.android.contacts/data/22 typ=vnd.android.cursor.item/com.silentcircle.phone
        // It's a simple query with a full qualified DATA URI, IMHO we don't need to
        // put that one query in an async task ;-) .
        if (Intent.ACTION_VIEW.equals(action) &&
                (MSG_MIME.equals(intent.getType()) || PHONE_MIME.equals(intent.getType()))) {
            if (numberUri == null) {
                finish();
                return;
            }

            // ********** HACK ahead HACK ahead *********
            // Android M Contacts seems to send the view intent twice and this would cause
            // double calling or incorrect behaviour of back buttons. Try to ignore
            // the second Intent. Unfortunately Android M sends the second Intent
            // with different flags, depending of the state of this Activity. Thus
            // use two different methods to check if this is a duplicate Intent.
            // Need some more investigation on M for this.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
//                if ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
//                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Ignoring this intent from Contacts, issue in M - flag");
//                    mBigMContactsHack = null;
//                    finish();
//                    return;
//                }
                //Received the same Intent twice - Thus is a HACK. Need to check why this happens on M
                if (numberUri.toString().equals(mBigMContactsHack)) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Ignoring this intent from Contacts, issue in M - dup");
                    mBigMContactsHack = null;
                    finish();
                    return;
                }
                mBigMContactsHack = numberUri.toString();
            }
            // ********** HACK above HACK above *********
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                final Cursor cursor = getContentResolver().query(numberUri, projection, null, null, null);
                try {
                    if (cursor != null && cursor.getCount() > 0) {
                        cursor.moveToFirst();
                        String numberOrName = cursor.getString(NUMBER_OR_NAME);
                        String type = cursor.getString(MIME_TYPE);
                        cursor.close();

                        if (ConfigurationUtilities.mTrace)
                            Log.d(TAG, "Name from DB receiving contacts intent: " + numberOrName + " (" + type + ")");
                        if (PHONE_MIME.equals(type)) {
                            action = SILENT_CALL_ACTION;
                            numberUri = Uri.parse(numberOrName);
                        } else if (MSG_MIME.equals(type)) {
                            startConversationFromContacts(numberOrName);
                            return;
                        } else {
                            Log.w(TAG, "Received a wrong VIEW intent: " + intent);
                            finish();
                            return;
                        }
                    }
                } finally {
                    if (cursor != null && !cursor.isClosed())
                        cursor.close();
                }
            }
        }
        else
            mBigMContactsHack = null;

        if (InCallActivity.ADD_CALL_ACTION.equals(action)) {
            mCallActionIntent = true;
            return;
        }
        if (Action.VIEW_CONVERSATIONS.equals(Action.from(intent))) {
            if(mListsFragment != null) {
                mListsFragment.setPagerItem(ListsFragment.TAB_INDEX_CHAT);
            }

            return;
        }

        // If the Intent does not contain a number information and the number of active calls is
        // >= 1 then restart the InCall activity. Activities were destroyed by some user action (recent
        // app list), not by some device action like rotation.
        if (numberUri == null) {
            // Not needed anymore since InCall runs in a separate task. If we let this work here,
            // then a device rotation in this activity would launch the InCall activity again!
//            if (TiviPhoneService.calls.getCallCount() >= 1) {
//                Intent forward = new Intent(intent);
//                Bundle bundle = new Bundle();
//                bundle.putInt(TiviPhoneService.CALL_TYPE, TiviPhoneService.CALL_TYPE_RESTART);
//                forward.putExtras(bundle);
//                forward.setClass(this, InCallActivity.class);
//                forward.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                if (mPhoneIsBound && mPhoneService != null)
//                    mPhoneService.setNotificationToIncall();
//                startActivity(forward);
//            }
            return;
        }

        String host = numberUri.getHost();
        String scheme = numberUri.getScheme();
        String number = numberUri.getSchemeSpecificPart();
        number = number != null ? number.trim() : null;
        String rawNumber = number;

        String[] numberFields = Utilities.splitFields(number, ";");

        if (numberFields == null || mDialpadFragment == null)
            return;
        number = numberFields[0];

        Set<String> categories = intent.getCategories();
        boolean fromThirdParty = Intent.ACTION_DIAL.equals(action)
                || Intent.ACTION_CALL.equals(action)
                || (Intent.ACTION_VIEW.equals(action)
                        && (categories != null && categories.contains(Intent.CATEGORY_BROWSABLE)));

        // Handle a website entry from contacts - "http://silentphone/<alias>"
        if ("http".equals(scheme) && "silentphone".equals(host)) {
            fromThirdParty = true;
            number = numberUri.getLastPathSegment();
        }

        if (fromThirdParty) {
            if (!isInSearchUi()) {
                openActionBarQueryField();
                enterSearchUi(false /* smartDialSearch */, number,
                        false /* conversations flag */,  true /* animate */);
                invalidateOptionsMenu();
            }

            mSearchView.setText(number);

            return;
        }

        boolean needsAssist = TextUtils.isDigitsOnly(number) && (scheme != null && scheme.equalsIgnoreCase("tel"));

        if (SILENT_CALL_ACTION.equals(action) || SILENT_EDIT_BEFORE_CALL_ACTION.equals(action)) {

            mCallActionIntent = true;

            // This is the case if the user requests a call to a specified device, e.g. from
            // messaging device status display. This number string contains a device-id part.
            if (intent.getBooleanExtra(NO_NUMBER_CHECK, false)) {
                mDialpadFragment.setDestination(rawNumber);     // the "raw" Number string
                if (!mPhoneIsBound)                             // Start dialing if phone service is ready (bound)
                    mAutoDialRequested = true;
                else
                    mDialpadFragment.dialButtonPressed();
                return;
            }
            boolean wasModified = checkCallToNr(number, mDialpadFragment);

            if (SILENT_CALL_ACTION.equals(action)) {
                if (!wasModified) {
                    if (!mPhoneIsBound)                         // Start dialing if phone service is ready (bound)
                        mAutoDialRequested = true;
                    else
                        mDialpadFragment.dialButtonPressed();
                }
                else {
                    showDialpadFragment(true /* animate */, !needsAssist /* autoDial */);
                }
            }
            else
                showDialpadFragment(true, true);
        }
    }

    public void openActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setTitle(null);
        actionBar.setSubtitle(null);

        boolean willShowDiscovery = shouldShowConversationFeatureDiscovery();
        ((SearchEditTextLayout) actionBar.getCustomView()).expand(false /* animate */,
                !willShowDiscovery /* requestFocus */);
        ((SearchEditTextLayout) actionBar.getCustomView()).showBackButton(false);
        ((SearchEditTextLayout) actionBar.getCustomView()).keyboardLayout(false);
    }

    public void closeActionBarQueryField() {
//        mActionBarController.onSearchUiExited();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        SearchEditTextLayout seTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
        if (seTextLayout.isExpanded()) {
            seTextLayout.collapse(false /* animate */);
        }
        if (seTextLayout.isFadedOut()) {
            seTextLayout.fadeIn();
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
    public void zrtpStateChange(@NonNull CallState call, TiviPhoneService.CT_cb_msg msg) { }

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
     * Listener that receives the data if OCA minutes are available from LoadUserInfo.
     *
     * @param userInfo A {@link UserInfo} object
     * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
     *                    this shows the error reason
     * @param silent      Should the user see anything?
     */
    @Override
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onUserInfo, errorInfo " + errorInfo);

        if (errorInfo != null) {
            return;
        }

        mUuid = userInfo.getUuid();
        mDisplayTn = userInfo.getDisplayTn();
        mDisplayAlias = userInfo.getDisplayAlias();
        mDisplayName = userInfo.getDisplayName();

        SilentPhoneApplication.sUuid = mUuid;
        SilentPhoneApplication.sDisplayName = mDisplayName;
        SilentPhoneApplication.sDisplayAlias = mDisplayAlias;

        restoreActionBar();
    }

    private CharSequence getWipeDialogMessage(int seconds) {
        Resources resources = getResources();
        seconds = Math.max(seconds, 0);
        String duration = resources.getQuantityString(R.plurals.duration_seconds, seconds, seconds);
        return getString(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
                    ? R.string.dialog_message_wipe_after_timer
                    : R.string.dialog_message_close_after_timer,
                duration);
    }

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
            if (TiviPhoneService.calls.getCallCount() == 0) {
                int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
                getWindow().clearFlags(windowFlags);
            }
            if (mListsFragment != null) {
                mListsFragment.onCallStateChanged(call, msg);
            }

            if (msg != TiviPhoneService.CT_cb_msg.eReg)
                return;
            Utilities.setSubtitleColor(DialerActivityInternal.this, mToolbar);
            if (mDialDrawerFragment != null) {
                mDialDrawerFragment.setOnlineStatus();
            }
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
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Authentication data (API key) : " + devAuthorization);

                    // Compute device id from existing data, i.e. don't create a new instance dev id
                    // because this is a check for an existing device
                    String deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(DialerActivityInternal.this, false));
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
                if(!Utilities.isNetworkConnected(DialerActivityInternal.this)){
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
                showDialog(R.string.information_dialog, R.string.connected_to_network, android.R.string.ok, -1);
            }
            else if (result == HttpsURLConnection.HTTP_FORBIDDEN || result == HttpsURLConnection.HTTP_NOT_FOUND) {
                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Device status check - not found on provisioning server");
                showDeviceCheckInfo();
            }
            else {
                showDialog(R.string.information_dialog, R.string.connected_to_network,
                        android.R.string.ok, -1);
            }
        }
    }

    private void showDeviceCheckInfo() {
        DeviceStatusInfo errMsg = DeviceStatusInfo.newInstance(getString(R.string.re_provisioning_device_not_found));
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                .add(errMsg, "SilentPhoneDeviceCheck")
                .commitAllowingStateLoss();
        }
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction fragmentTransaction;
        if (fragmentManager != null) {
            fragmentTransaction = fragmentManager.beginTransaction().add(infoMsg, TAG);
            if (mIsInstanceStateSaved) {
                Log.d(TAG, "FragmentTransaction commit for showing a dialog is canceled. Instance state has already been saved");
                return;
            }
            fragmentTransaction.commitAllowingStateLoss();
        }
    }

    public static class DeviceStatusInfo extends DialogFragment {
        private DialerActivityInternal mParent;

        public static DeviceStatusInfo newInstance(String msg) {
            DeviceStatusInfo f = new DeviceStatusInfo();

            Bundle args = new Bundle();
            args.putString("MESSAGE", msg);
            f.setArguments(args);

            return f;
        }

        public DeviceStatusInfo() {
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            commonOnAttach(getActivity());
        }

        /*
         * Deprecated on API 23
         * Use onAttachToContext instead
         */
        @SuppressWarnings("deprecation")
        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                commonOnAttach(activity);
            }
        }

        private void commonOnAttach(Activity activity) {
            mParent = (DialerActivityInternal)activity;
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

    // Called only for Android M or above
    @SuppressWarnings("unused")
    private void showBatteryOptInfo() {
        IgnoreBatteryOptInfo infoMsg = IgnoreBatteryOptInfo.newInstance(getString(R.string.battery_info));
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                .add(infoMsg, "IgnoreBatteryOptInfo")
                .commitAllowingStateLoss();
        }
    }

    public static class IgnoreBatteryOptInfo extends DialogFragment {
        private DialerActivityInternal mParent;

        public static IgnoreBatteryOptInfo newInstance(String msg) {
            IgnoreBatteryOptInfo f = new IgnoreBatteryOptInfo();

            Bundle args = new Bundle();
            args.putString("MESSAGE", msg);
            f.setArguments(args);

            return f;
        }

        public IgnoreBatteryOptInfo() {
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            mParent = (DialerActivityInternal)activity;
        }

        @TargetApi(Build.VERSION_CODES.M)
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
                            String packageName = mParent.getPackageName();
                            PowerManager pm = (PowerManager) mParent.getSystemService(Context.POWER_SERVICE);
                            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                                startActivity(intent);
                            }
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
        } else if (fragment instanceof SearchAgainFragment) {
            //
        } else if (fragment instanceof SearchFragment) {
            mRegularSearchFragment = (RegularSearchFragment) fragment;
            mRegularSearchFragment.setOnPhoneNumberPickerActionListener(this);
            mRegularSearchFragment.setOnSearchActionListener(this);
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
        mFloatingActionButton.setTag(position);
    }

    @Override
    public void onPageSelected(int position) {
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    /**
     * Initiates a fragment transaction to show the dialpad fragment. Animations and other visual
     * updates are handled by a callback which is invoked after the dialpad fragment is shown.
     * @see #onDialpadShown
     */
    private void showDialpadFragment(boolean animate, boolean autoDial) {
        if (mIsDialpadShown) {
            return;
        }
        mIsDialpadShown = true;
        mAutoDialRequested = autoDial;
//        mDialpadFragment.setAnimate(animate);
//        mDialpadFragment.sendScreenView();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.hide();
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.show(mDialpadFragment);
        ft.commit();

        if (!isInSearchUi()) {
            enterSearchUi(true /* isSmartDial */, mSearchQuery, false /* conversations flag */,  true /* animate */);
        }
        mDialpadFragment.showKeyboard();
    }

    /**
     * Callback from child DialpadFragment when the dialpad is shown.
     */
    @Override
    public void onDialpadShown() {
//        if (mDialpadFragment.getAnimate()) {
//        final View view = mDialpadFragment.getView();
//        if (view != null)
//            view.startAnimation(mSlideIn);
////        } else {
////            mDialpadFragment.setYFraction(0);
////        }
//        if (!mAutoDialRequested) {
//            mAutoDialRequested = false;
//            if (mRegularSearchFragment != null) {
//                mRegularSearchFragment.setShowScDirectoryOption(false);
//            }
//        }
//        updateSearchFragmentSettings();
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
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                if (mSearchPhoneInput) {
                    ((SearchEditTextLayout) actionBar.getCustomView()).dialpadLayout(true);
                } else {
                    ((SearchEditTextLayout) actionBar.getCustomView()).keyboardLayout(true);
                }
            }

            fragment.updatePosition(true /* animate */);
            fragment.setShowEmptyListForNullQuery(false);
            if (mSearchView != null && mSearchQuery != null)
                mSearchView.setText(mSearchQuery);
            if (fragment == mRegularSearchFragment) {
                if (actionBar == null)
                    return;
                actionBar.setDisplayShowCustomEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(false);
                SearchEditTextLayout seTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
                seTextLayout.expand(false /*animate */, true /*focus */);
                seTextLayout.showBackButton(false);
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

        /** Could already be visible, due to the dialpad and its internal search UI having
         * overlap in their hidings, see {@link DialerActivityInternal#exitSearchUi()}
         **/
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.show();
//        mDialpadFragment.setAnimate(animate);
//
        updateSearchFragmentSettings();

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
            else if (mInRegularSearch) {
                mRegularSearchFragment.setShowScDirectoryOption(true);
            }
        }
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return 0;
        return actionBar.getHideOffset();
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
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
    }

    @Override
    public void onPickPhoneNumberAction(Uri dataUri) {
        // Specify call-origin so that users will see the previous tab instead of
        // CallLog screen (search UI will be automatically exited).
        PhoneNumberInteraction.startInteractionForPhoneCall(DialerActivityInternal.this, dataUri, null /*getCallOrigin()*/);
    }

    @Override
    public void onStartConversation(String userName, String uuid) {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_HAD_TRANSITIONED, true).apply();
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber) {
        onCallNumberDirectly(phoneNumber, false /* isVideoCall */);

        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_HAD_TRANSITIONED, true).apply();
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
        String[] numberFields = Utilities.splitFields(phoneNumber, ";");
        if (numberFields != null)
            callImmediateOrAsk(numberFields[0]);
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
    private void enterSearchUi(boolean smartDialSearch, String query, boolean startConversationFlag, boolean animate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        if (mDrawerLayout == null) {
            // Layout not truly inflated, probably permission handling race condition
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
        if (TextUtils.isEmpty(query) && animate) {
            transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        }
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
        // DialerActivityInternal will provide the options menu
        fragment.setHasOptionsMenu(false);
        fragment.setShowEmptyListForNullQuery(false);
        fragment.setQueryString(query, false /* delaySelection */);
        fragment.setStartConversationFlag(startConversationFlag);
        transaction.commitAllowingStateLoss();

        final View view = mListsFragment.getView();
        if (view == null)
            return;

        if (TextUtils.isEmpty(query) && animate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                view.animate().alpha(0).withLayer();
            else
                view.animate().alpha(0);
        }
        mDrawerToggle.setDrawerIndicatorEnabled(false);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        setFabButtonVisibility(View.GONE);
    }

    /**
     * Hides the search fragment
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void exitSearchUi() {
        // See related bug in enterSearchUI();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            return;
        }

        if (mDrawerLayout == null) {
            // Layout not truly inflated, probably permission handling race condition
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

        closeActionBarQueryField();
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        invalidateOptionsMenu();
        restoreActionBar();
        setFabButtonVisibility(View.VISIBLE);
    }

    private void closeDrawerAndShowSettingsView() {

        // Start the drawer closing process now
        mDrawerLayout.post(mClosingDrawerRunner);

        mDrawerLayout.postDelayed(mEnteringSettingsUiRunner, SETTINGS_ITEM_TRANSITION_MS);
    }

    private boolean isInSettingsUi() {
        return mIsInSettingsUi;
    }

    private void enterSettingsUI() {
        final FragmentManager fragmentManager = getFragmentManager();
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && fragmentManager.isDestroyed())
                || mParentLayout == null) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        SettingsFragment fragment =
                (SettingsFragment) fragmentManager.findFragmentByTag(
                        SettingsFragment.TAG_SETTINGS_FRAGMENT);
        transaction.setCustomAnimations(R.animator.fade_in_fast, 0);
        if (fragment == null) {
            fragment = new SettingsFragment();
            transaction.add(R.id.dial_frame, fragment, SettingsFragment.TAG_SETTINGS_FRAGMENT);
        } else {
            transaction.show(fragment);
        }
        transaction.commit();

        enterSubView();
        mIsInSettingsUi = true;
    }

    private void exitSettingsUi() {
        mIsInSettingsUi = false;
        exitSubView();

        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        SettingsFragment fragment =
                (SettingsFragment) fragmentManager.findFragmentByTag(
                        SettingsFragment.TAG_SETTINGS_FRAGMENT);
        if (fragment != null) {
            transaction.remove(fragment);
        }
        transaction.commit();
    }

    private void enterSubView() {
        invalidateOptionsMenu();

        mDrawerLayout.post(mClosingDrawerRunner);

        setFabButtonVisibility(View.GONE);
    }

    private void exitSubView() {
        setFabButtonVisibility(View.VISIBLE);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        invalidateOptionsMenu();
        restoreActionBar();
    }

    // ---------------------------------------------------------------------------------------------

    private void setFabButtonVisibility(int visibility) {
        mFloatingActionButton.setVisibility(visibility);
    }

    private void callImmediateOrAsk(String number) {
        if (number != null && mDialpadFragment != null) {
            boolean wasModified = checkCallToNr(number, mDialpadFragment);
//            if (!wasModified)
            // Show a search progress bar after 1.25 seconds (for slow networks)
            mPhoneCallProgressHandler.removeCallbacksAndMessages(null);
            mPhoneCallProgressHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mRegularSearchFragment != null && mRegularSearchFragment.isVisible()) {
                        mRegularSearchFragment.setProgressEnabled(true);
                    }
                }
            }, 1250);

            mDialpadFragment.dialButtonPressed();
//            else {
//                if (isInSearchUi() && !mIsDialpadShown)
//                    exitSearchUi();
//                showDialpadFragment(true /* animate */, true /* autoDial */);
//            }
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

        if (!TextUtils.equals(mSearchView != null ? mSearchView.getText() : null, query)) {
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
            if (mSearchView != null)
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

    private void startConversationFromContacts(final String aliasName) {
        AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask() {
            @Override
            protected void onPostExecute(Integer time) {
                super.onPostExecute(time);
                if (mData != null && mUserInfo.mUuid != null) {
                    Intent msgIntent = new Intent(DialerActivityInternal.this, ConversationActivity.class);
                    Extra.PARTNER.to(msgIntent, mUserInfo.mUuid);
                    Extra.ALIAS.to(msgIntent, aliasName);   // Check if we should use the default alias (mAlias)?
                    Extra.VALID.to(msgIntent, true);
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Start messaging with: " + mUserInfo.mUuid + ", (" + aliasName + ")");
                    startActivity(msgIntent);
                    finish();
                }
            }
        };
        AsyncUtils.execute(getUserDataTask, Utilities.removeUriPartsSelective(aliasName));
    }

    public static void activateTraversal(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean enableTraversal = prefs.getBoolean(SettingsFragment.ENABLE_FW_TRAVERSAL, true);
        boolean forceTraversal = prefs.getBoolean(SettingsFragment.FORCE_FW_TRAVERSAL, false);

        if (enableTraversal)
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=1");
        else {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=0");
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
        }
        if (forceTraversal)
            TiviPhoneService.doCmd("set cfg.ForceFWTraversal=1");
        else {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
        }
    }

    private static String ENABLE_UNDERFLOW_TONE = "enable_underflow_tone";

    public static void activateDropoutTone(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean mUseUnderflow = prefs.getBoolean(ENABLE_UNDERFLOW_TONE, false);
        TiviPhoneService.doCmd("set cfg.iAudioUnderflow=" + (mUseUnderflow ? "1" : "0"));
    }

    public static void activateDebugSettings(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        int sslDebugLevel = prefs.getInt(SettingsFragment.DEVELOPER_SSL_DEBUG, 0);
        int zinaDebugLevel = prefs.getInt(SettingsFragment.DEVELOPER_ZINA_DEBUG, 2);

        TiviPhoneService.doCmd("debug.option=ssl_level:" + sslDebugLevel);
        TiviPhoneService.doCmd("debug.option=zina_level:" + zinaDebugLevel);
    }

    public static void indicateDataRetention() {
        TiviPhoneService.setDataRetention(LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp(),
                LoadUserInfo.isBldr() | LoadUserInfo.isBlmr() | LoadUserInfo.isBrdr() | LoadUserInfo.isBrmr());
    }

    private void setStartOnBoot() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.contains(SettingsFragment.START_ON_BOOT)) {
            Log.d(TAG, "Enabling start-on-boot after provisioning");
            prefs.edit().putBoolean(SettingsFragment.START_ON_BOOT, true).apply();
        }
    }

    private boolean shouldShowConversationFeatureDiscovery() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        boolean startDiscovery = !prefs.getBoolean(KEY_FEATURE_DISCOVERY_NEW_CONVERSATION, BuildConfig.DEBUG);
        SharedPreferences defPrefs = PreferenceManager.getDefaultSharedPreferences(DialerActivityInternal.this);
        boolean forceStartDiscovery = defPrefs.getBoolean(SettingsFragment.FORCE_FEATURE_DISCOVERY, false);
        return (!mNewConversationFeatureDiscoveryRan && (startDiscovery || forceStartDiscovery));
    }

}
