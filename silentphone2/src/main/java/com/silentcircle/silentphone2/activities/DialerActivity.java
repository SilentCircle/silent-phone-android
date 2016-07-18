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

import android.accounts.Account;
import android.accounts.AccountManager;
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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.InflateException;
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

import com.silentcircle.accounts.AccountConstants;
import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.animation.AnimationListenerAdapter;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DatabaseHelperManager;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.widget.FloatingActionButtonController;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.calllognew.CallLogActivity;
import com.silentcircle.contacts.vcard.MigrateByVCardActivity;
import com.silentcircle.keymanagersupport.KeyManagerSupport;
import com.silentcircle.keystore.KeyStoreActivity;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.messaging.activities.ConversationActivity;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
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
import com.silentcircle.silentphone2.list.OnDragDropListener;
import com.silentcircle.silentphone2.list.OnListFragmentScrolledListener;
import com.silentcircle.silentphone2.list.PhoneFavoriteSquareTileView;
import com.silentcircle.silentphone2.list.RegularSearchFragment;
import com.silentcircle.silentphone2.list.SearchFragment;
import com.silentcircle.silentphone2.list.SmartDialSearchFragment;
import com.silentcircle.silentphone2.receivers.AutoStart;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.CallState;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.Constants;
import com.silentcircle.silentphone2.util.DeviceHandling;
import com.silentcircle.silentphone2.util.DialCodes;
import com.silentcircle.silentphone2.util.PinnedCertificateHandling;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class DialerActivity extends TransactionSafeActivity
        implements DialDrawerFragment.DrawerCallbacks, DialpadFragment.DialpadCallbacks,
        KeyManagerSupport.KeyManagerListener, View.OnClickListener, ListsFragment.HostInterface,
        SearchFragment.HostInterface, OnDragDropListener,
        ViewPager.OnPageChangeListener, DialpadFragment.OnDialpadQueryChangedListener,
        OnListFragmentScrolledListener, OnPhoneNumberPickerActionListener, TiviPhoneService.ServiceStateChangeListener,
        LoadUserInfo.Listener {

    private static final String TAG = DialerActivity.class.getSimpleName();

    public static final String SILENT_CALL_ACTION = "com.silentcircle.silentphone.action.NEW_OUTGOING_CALL";
    public static final String SILENT_EDIT_BEFORE_CALL_ACTION = "com.silentcircle.silentphone.action.EDIT_BEFORE_CALL";
    public static final String ACTION_REMOVE_ACCOUNT = "remove_account";
    public static final String START_FOR_MESSAGING = "start_for_messaging";
    public static final String NO_NUMBER_CHECK = "no_number_check";

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
    private static final String KEY_IS_AUTO_DIAL = "is_auto_dial";
    private static final String KEY_IN_SETTINGS_UI = "in_settings_ui";

    public static final String SHARED_PREFS_NAME = "com.silentcircle.dialer_preferences";

    /*
     * Codes for startActivityWithResult Intents
     */
    public static final int PROVISIONING_RESULT = 1003;
    public static final int KEY_STORE_CHECK = 1004;
    public static final int RINGTONE_SELECTED = 1005;
    public static final int DIAL_HELPER_SELECTED = 1006;
    public static final int KEY_STORE_PASS_ACTION = 1007;
//    public static final int HIDE_WELCOME = 1009;
    public static final int MESSAGING_LOCK_CONFIGURATION_PASS_ACTION = 1010;
    public static final int MESSAGE_RINGTONE_SELECTED = 1011;

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

    private LoadUserInfo mLoadUserInfo;

    // If true then we know this phone is provisioned. During onCreate we may set this to false.
    private boolean isProvisioned = true;
    private boolean provisioningDone;
    private boolean mAutoDialRequested;
    private boolean mCallActionIntent;
    private boolean mStartOnBoot;           // Started right after boot via AutoStart listener
    private boolean mRemoveAccount;         // received a remove account action
    private boolean mStartForMessaging;     // Initialize and start service for messaging

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
    private static boolean mForceUpdates;    // true after we forced a contacts update and refreshed user info

    // some internal features, usually not visible to user
    private boolean mEnableShowZid;
    private boolean mAdvancedSettings;
    private boolean mReProvisioningRequested;

    private boolean mDestroyed;             // Activity being destroyed, don't do delayed actions (service bind)

    // Store this in a static variable to not call PreferenceManager#getDefaultSharedPreferences
    // for every error view
    public static boolean mShowErrors;

    /**
     * Internal handler to receive and process internal messages messages.
     */
//    private final InternalHandler mHandler = new InternalHandler(this);

    private FloatingActionButtonController mFloatingActionButtonController;
    private ImageButton mFloatingActionButton;

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

    private boolean mIsDialpadShown;
    private boolean mShowDialpadOnResume;

    private boolean mInDialpadSearch;
    private boolean mInRegularSearch;
    private boolean mIsInSettingsUi;

    private DialerDatabaseHelperOrig mDialerDatabaseHelper;

    private Toolbar mToolbar;

    private Drawable mDialPadIcon;
    private Drawable mNewChatIcon;

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
            if (!mForceUpdates) {
                if (mLoadUserInfo == null) {
                    mLoadUserInfo = new LoadUserInfo(getApplicationContext(), true);
                    mLoadUserInfo.addUserInfoListener(DialerActivity.this);
                }

                mLoadUserInfo.refreshUserInfo();
            }
            if (mDialDrawerFragment != null) {
                mDialDrawerFragment.setNumberName();
            }

            /* Initialize TiVi library from settings in the preferences */
            activateTraversal();
            activateDropoutTone();
            activateDebugSettings();

            if (!mForceUpdates) {
                FindDialHelper.setDialHelper(DialerActivity.this, false);
                mForceUpdates = true;
                mPhoneService.runContactsUpdater(true);
            }
            if (provisioningDone) {
                provisioningDone = false;
                if (!isDrawerOpen()) {
                    mDialpadFragment.removeDestinationFocus();
                    mDrawerLayout.postDelayed(mOpenDialDrawer, 400);
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
                    enterSearchUi(mIsDialpadShown, mSearchQuery, false /* conversations flag */);
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
                // Currently obsolete - we no longer have directory options, so always exit
                // maybeExitSearchUi();
                exitSearchUi();
                DialerUtils.hideInputMethod(mParentLayout);

                return true;
            }
            return false;
        }
    };

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AutoStart.setDisableAutoStart(true);                            // Don't auto start if we are started once

        super.onCreate(savedInstanceState);

        //register for push notification if registration id has not been saved yet.
        //This may not need to unregister as Ed suggested in the email.
//        String regId = SPAPreferences.getInstance(this).getRegistrationId();
//        if(TextUtils.isEmpty(regId)) {
//            C2DMUtil.registerC2DM(this);
//        }

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

        Utilities.setTheme(this);
        ConfigurationUtilities.initializeDebugSettings(getBaseContext());

        if (Build.VERSION.SDK_INT >= 21 && ConfigurationUtilities.mUseDevelopConfiguration) {
            getWindow().setStatusBarColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (mDomainsToRemove == null)
            mDomainsToRemove = getResources().getStringArray(R.array.domains_to_remove);

        Log.d(TAG, "Debug: " + ConfigurationUtilities.mTrace + ", language: " + Locale.getDefault().getLanguage()
                + ", develop config: " + ConfigurationUtilities.mUseDevelopConfiguration + ", build commit: +-+- " +
                BuildConfig.SPA_BUILD_COMMIT);

        TiviPhoneService.initJNI(getBaseContext());
        isProvisioned = !(TiviPhoneService.phoneService == null && TiviPhoneService.doCmd("isProv") == 0);

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
            mAutoDialRequested = savedInstanceState.getBoolean(KEY_IS_AUTO_DIAL);
            mIsInSettingsUi = savedInstanceState.getBoolean(KEY_IN_SETTINGS_UI);
        }
        // Register even if not key manager may not be active
        KeyManagerSupport.addListener(this);

        Resources.Theme theme = getTheme();
        if (theme != null) {
            TypedArray array = theme.obtainStyledAttributes(new int[]{
                    R.attr.sp_ic_dial_pad,
                    R.attr.sp_ic_new_chat,
            });
            if (array != null) {
                mDialPadIcon = array.getDrawable(0);
                mNewChatIcon = array.getDrawable(1);
                array.recycle();
            }
        }
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mDialPadIcon = getResources().getDrawable(R.drawable.ic_action_dial_pad_light, null);
                mNewChatIcon = getResources().getDrawable(R.drawable.ic_action_new_chat_dark, null);
            }
            else {
                mDialPadIcon = getResources().getDrawable(R.drawable.ic_action_dial_pad_light);
                mNewChatIcon = getResources().getDrawable(R.drawable.ic_action_new_chat_dark);
            }
        }

        // Store this in a static variable to not call PreferenceManager#getDefaultSharedPreferences
        // for every error view
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mShowErrors = prefs.getBoolean(SettingsFragment.SHOW_ERRORS, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(mDialerDatabaseHelper != null) {
            mDialerDatabaseHelper.startSmartDialUpdateThread();
        }

        if (mShowDialpadOnResume) {
            showDialpadFragment(false, mAutoDialRequested /* autoDial */);
            mShowDialpadOnResume = false;
        }

        if (mInRegularSearch) {
            updateSearchFragmentSettings();
        }

        if (isInSettingsUi()) {
            enterSettingsUI();
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
        if (mLoadUserInfo != null) {
            mLoadUserInfo.removeUserInfoListener(this);
        }
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
        outState.putBoolean(KEY_IS_AUTO_DIAL, mAutoDialRequested);
        outState.putBoolean(KEY_IN_SETTINGS_UI, isInSettingsUi());
    }

    @Override
    protected void onNewIntent (Intent intent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "Received new intent: " + intent);
        processIntent(intent);
    }

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

            case DialDrawerFragment.DrawerCallbacks.MESSAGING_LOCK_SCREEN:
                action = (String)params[0];
                intent = new Intent(this, KeyStoreActivity.class);
                intent.setAction(action);
                startActivityForResult(intent, MESSAGING_LOCK_CONFIGURATION_PASS_ACTION);
                break;

            case DialDrawerFragment.DrawerCallbacks.RE_PROVISION:
                mDrawerLayout.closeDrawer(mDrawerView);
                mReProvisioningRequested = true;
                exitSettingsUi();
                findViewById(R.id.floating_action_button_container).setVisibility(View.INVISIBLE);
                FragmentManager fm = getFragmentManager();
                FragmentTransaction ft = fm.beginTransaction();
                ft.hide(mListsFragment).commitAllowingStateLoss();
                checkDeviceStatus();
                break;

            case DialDrawerFragment.DrawerCallbacks.DEVELOPER_SSL_DEBUG:
                TiviPhoneService.doCmd("debug.option=ssl_level:" + (int)params[0]);
                break;

            case DialDrawerFragment.DrawerCallbacks.DEVELOPER_AXO_DEBUG:
                TiviPhoneService.doCmd("debug.option=axo_level:" + (int)params[0]);
                break;

            case DialDrawerFragment.DrawerCallbacks.SETTINGS:
                enterSettingsUI();
                break;
        }
    }

    @Override
    public void onDrawerStateChange(int action) {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.dialpad, menu);
            restoreActionBar();
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
        final MenuItem item = menu.findItem(R.id.dial_menu_search);
        if (item != null) {
            item.setVisible(!(isInSearchUi() || isInSettingsUi()));
        }
        if (isInSettingsUi()) {
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
                            false /* conversations flag */);
                    invalidateOptionsMenu();
                }
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


    public void switchConfiguration() {
        Log.d(TAG, "Switch configuration, currently using development: "
                + ConfigurationUtilities.mUseDevelopConfiguration);
        if (ConfigurationUtilities.mUseDevelopConfiguration) {
            ConfigurationUtilities.switchToProduction(getBaseContext());
            mDrawerLayout.closeDrawer(mDrawerView);
            removeProvisioningAndRestart(false);
        }
        else {
            ConfigurationUtilities.switchToDevelop(getBaseContext(), ConfigurationUtilities.DEVELOPMENT_NETWORK);
            mDrawerLayout.closeDrawer(mDrawerView);
            removeProvisioningAndRestart(false);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.floating_action_button:
                Integer position = (Integer) mFloatingActionButton.getTag();
                if (position == null || position != ListsFragment.TAB_INDEX_CHAT) {
                    showDialpadFragment(true, false /* autoDial */);
                }
                else if (!isInSearchUi()) {
                    startConversation("");
                    invalidateOptionsMenu();
                }
                break;
            default:
                break;
        }
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
            exitSearchUi();
            DialerUtils.hideInputMethod(mParentLayout);
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

    public boolean isDrawerOpen() {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

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

    public void doCall(String callCommand, String destination, boolean isOcaCall) {
        if (mPhoneIsBound) {
            Utilities.audioMode(getBaseContext(), true);        // Switch to IN-COMMUNICATION mode

            AsyncTasks.asyncCommand(callCommand);
            if (mCallActionIntent) {
                setResult(RESULT_OK);
                finish();
            }
            mPhoneService.showCallScreen(TiviPhoneService.CALL_TYPE_OUTGOING, destination, isOcaCall);
        }
    }

    public boolean isAdvancedSettings() {
        return mAdvancedSettings;
    }

    /* finalize application and exit */
    public void exit() {
        mDrawerLayout.closeDrawer(mDrawerView);
        exitApplication();
        finish();
    }

    @SuppressWarnings("deprecation")
    public void wipePhone() {
        // This is only visible for non-production users who are API 19+ (4.4+)
        mDrawerLayout.closeDrawer(mDrawerView);

        // Delete the account from the account manager
        AccountManager am = AccountManager.get(this);
        Account[] accounts = am.getAccountsByType(AccountConstants.ACCOUNT_TYPE);
        // We force only one account to be created of {@link AccountConstants#ACCOUNT_TYPE}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            am.removeAccountExplicitly(accounts[0]);
        } else {
            am.removeAccount(accounts[0], null, null);
        }

        // Clear app data (only for API 19+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (!((ActivityManager) getSystemService(ACTIVITY_SERVICE)).clearApplicationUserData()) {
                Toast.makeText(this, R.string.wipe_data_failed, Toast.LENGTH_LONG).show();
            }
        } else {
            // Sanity toast
            Toast.makeText(this, R.string.wipe_data_failed, Toast.LENGTH_LONG).show();
        }

        finish();
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
        TiviPhoneService.doCmd(".exit");
        doUnbindService();
        stopService(new Intent(DialerActivity.this, TiviPhoneService.class));
        mServiceStarted = false;
        FindDialHelper.resetDialHelper();

        Utilities.Sleep(3000);
        mUuid = null;
        // get the intent that started us and use it to restart
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    // Reset in case Drawer window changed its state
    private void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null || isInSearchUi() || isInSettingsUi())
            return;
        ((TextView)mToolbar.findViewById(R.id.title)).setText(getString(R.string.app_name));
        mToolbar.findViewById(R.id.sub_title).setVisibility(View.VISIBLE);

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
                    (!TextUtils.isEmpty(mDisplayTn) ? "/" + mDisplayTn : "");

            ((TextView) mToolbar.findViewById(R.id.sub_title)).setText(subtitle);
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
        else if (TiviPhoneService.phoneService != null && TiviPhoneService.phoneService.isReady())
            AsyncTasks.asyncCommand(":reg"); // doCmd(":reg"); - do it async to avoid ANR in case network is slow
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

        startPhoneService();             // start phone service
        if (mStartOnBoot || mStartForMessaging) {
            finish();
            return;
        }
        doLayout();
        doBindService();
//        mHandler.sendEmptyMessageDelayed(HIDE_WELCOME, 1500);
        checkMigration();               // **** Migration support contacts data
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
                    mDialDrawerFragment.setNumberName();
                    mDialDrawerFragment.setUserInfo();
                }
            }
        };

        mDrawerToggle.setHomeAsUpIndicator(R.drawable.ic_arrow_back_black_24dp);
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
        actionBar.setDisplayShowTitleEnabled(false);
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

        mParentLayout = (FrameLayout)findViewById(R.id.dialer_main_layout);

        final View floatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        mFloatingActionButton = (ImageButton) findViewById(R.id.floating_action_button);
        mFloatingActionButton.setOnClickListener(this);
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                floatingActionButtonContainer, mFloatingActionButton);
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
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            showBatteryOptInfo();
//        }
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

    private static boolean mMigrationChecked;

    // **** Migration support function ****
    // Check if we need to migrate/save data from old Silent Contacts
    private void checkMigration() {
        if (mMigrationChecked)
            return;
        mMigrationChecked = true;
        if (!MigrateByVCardActivity.doMigration(this)) {
            if (ConfigurationUtilities.mTrace) Log.d(TAG, "Do not start migration.");
            return;
        }
        startActivity(MigrateByVCardActivity.getStartMigrationIntent(this));
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
    /**
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

    private final String[] projection = {ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1
    };

    private static final int MIME_TYPE = 0;
    private static final int NUMBER_OR_NAME = 1;

    private static final String PHONE_MIME = "vnd.android.cursor.item/com.silentcircle.phone";
    private static final String MSG_MIME = "vnd.android.cursor.item/com.silentcircle.message";

    private Intent intentProcessed;
    private static String mBigMContactsHack;
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
                if ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
                    if (ConfigurationUtilities.mTrace) Log.d(TAG, "Ignoring this intent from Contacts, issue in M - flag");
                    mBigMContactsHack = null;
                    finish();
                    return;
                }
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

            final Cursor cursor = getContentResolver().query(numberUri, projection, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                String numberOrName = cursor.getString(NUMBER_OR_NAME);
                String type = cursor.getString(MIME_TYPE);
                cursor.close();

                if (ConfigurationUtilities.mTrace) Log.d(TAG, "Name from DB receiving contacts intent: " + numberOrName + " (" + type + ")");
                if (PHONE_MIME.equals(type)) {
                    action = SILENT_CALL_ACTION;
                    numberUri = Uri.parse(numberOrName);
                }
                else if (MSG_MIME.equals(type)) {
                    startConversationFromContacts(numberOrName);
                    return;
                }
                else {
                    Log.w(TAG, "Received a wrong VIEW intent: " + intent);
                    finish();
                    return;
                }
            }
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }
        else
            mBigMContactsHack = null;

        if (InCallActivity.ADD_CALL_ACTION.equals(action)) {
            mCallActionIntent = true;
            return;
        }
        if(Action.VIEW_CONVERSATIONS.equals(Action.from(intent))) {
            if(mListsFragment != null) {
                mListsFragment.setPagerItem(ListsFragment.TAB_INDEX_CHAT);
            }

            return;
        }

        // If the Intent does not contain a number information and the number of active calls is
        // >= 1 then restart the InCall activity. Activities were destroyed by some user action (recent
        // app list), not by some device action like rotation.
        if (numberUri == null) {
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

        String scheme = numberUri.getScheme();
        String number = numberUri.getSchemeSpecificPart();
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

        boolean needsAssist = TextUtils.isDigitsOnly(number) && (scheme != null && scheme.equalsIgnoreCase("tel"));

        if (SILENT_CALL_ACTION.equals(action) || SILENT_EDIT_BEFORE_CALL_ACTION.equals(action) || fromThirdParty) {

            // This is the case if the user requests a call to a specified device, e.g. from
            // messaging device status display. This number string contains a device-id part.
            if (intent.getBooleanExtra(NO_NUMBER_CHECK, false) && !fromThirdParty) {
                mDialpadFragment.setDestination(rawNumber);     // the "raw" Number string
                if (!mPhoneIsBound)                             // Start dialing if phone service is ready (bound)
                    mAutoDialRequested = true;
                else
                    mDialpadFragment.dialButtonPressed();
                return;
            }
            boolean wasModified = checkCallToNr(number, mDialpadFragment);

            mCallActionIntent = true;
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
                showDialpadFragment(true, !fromThirdParty);
        }
    }

    private void openActionBarQueryField() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return;
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        ((TextView)mToolbar.findViewById(R.id.title)).setText(null);
        ((TextView)mToolbar.findViewById(R.id.sub_title)).setText(null);
        ((SearchEditTextLayout) actionBar.getCustomView()).expand(false /* animate */, true /* requestFocus */);
        ((SearchEditTextLayout) actionBar.getCustomView()).showBackButton(false);
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

        restoreActionBar();
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

            if (msg != TiviPhoneService.CT_cb_msg.eReg)
                return;
            Utilities.setSubtitleColor(getResources(), mToolbar);
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
                    String deviceId = Utilities.hashMd5(TiviPhoneService.getInstanceDeviceId(DialerActivity.this, false));
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
        errMsg.show(fragmentManager, "SilentPhoneDeviceCheck");
    }

    private void showDialog(int titleResId, int msgResId, int positiveBtnLabel, int negativeBtnLabel) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msgResId, positiveBtnLabel, negativeBtnLabel);
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, TAG);

        // Make possible links clickable
        fragmentManager.executePendingTransactions();
        ((TextView) infoMsg.getDialog().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
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

    // Called only for Android M or above
    private void showBatteryOptInfo() {
        IgnoreBatteryOptInfo infoMsg = IgnoreBatteryOptInfo.newInstance(getString(R.string.battery_info));
        FragmentManager fragmentManager = getFragmentManager();
        infoMsg.show(fragmentManager, "IgnoreBatteryOptInfo");

    }

    public static class IgnoreBatteryOptInfo extends DialogFragment {
        private DialerActivity mParent;

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
            mParent = (DialerActivity)activity;
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
        mFloatingActionButtonController.onPageScrolled(1);
        mFloatingActionButton.setImageDrawable(
                (position == ListsFragment.TAB_INDEX_CHAT) ? mNewChatIcon : mDialPadIcon);
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

        if (animate) {
            mFloatingActionButtonController.scaleOut();
        }
        else {
            mFloatingActionButtonController.setVisible(false);
        }
        if (!isInSearchUi()) {
            enterSearchUi(true /* isSmartDial */, mSearchQuery, false /* conversations flag */);
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
        if (!mAutoDialRequested) {
            mAutoDialRequested = false;
            if (mRegularSearchFragment != null) {
                mRegularSearchFragment.setShowScDirectoryOption(false);
            }
        }
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
         * overlap in their hidings, see {@link DialerActivity#exitSearchUi()}
         **/
        if (!mFloatingActionButtonController.isVisible()) {
            mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
        }
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
            else if (mInRegularSearch) {
                mRegularSearchFragment.setShowScDirectoryOption(true);
            }
        }
    }

    /**
     * Updates controller based on currently known information.
     *
     * @param animate Whether or not to animate the transition.
     */
    private void updateFloatingActionButtonControllerAlignment(boolean animate) {
        final int align = FloatingActionButtonController.ALIGN_END;
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
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null)
            return 0;
        return actionBar.getHideOffset();
    }

    @Override
    public boolean isActionBarShowing() {
        return true;
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
        PhoneNumberInteraction.startInteractionForPhoneCall(DialerActivity.this, dataUri, null /*getCallOrigin()*/);
    }

    @Override
    public void onCallNumberDirectly(String phoneNumber) {
        onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
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
    private void enterSearchUi(boolean smartDialSearch, String query, boolean startConversationFlag) {
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
        fragment.setStartConversationFlag(startConversationFlag);
        transaction.commit();

        final View view = mListsFragment.getView();
        if (view == null)
            return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            view.animate().alpha(0).withLayer();
        else
            view.animate().alpha(0);

        mFloatingActionButtonController.scaleOut();
        mDrawerToggle.setDrawerIndicatorEnabled(false);
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
        if (actionBar == null)
            return;
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        SearchEditTextLayout seTextLayout = (SearchEditTextLayout)actionBar.getCustomView();
        if (seTextLayout.isExpanded()) {
            seTextLayout.collapse(true /* animate */);
        }
        if (seTextLayout.isFadedOut()) {
            seTextLayout.fadeIn();
        }
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        invalidateOptionsMenu();
        restoreActionBar();

        /** Could already be visible, due to the dialpad and its internal search UI having
         * overlap in their hidings, see {@link DialerActivity#commitDialpadFragmentHide()}
         **/
        if (!mFloatingActionButtonController.isVisible()) {
            mFloatingActionButtonController.scaleIn(AnimUtils.NO_DELAY);
        }
    }

    private boolean isInSettingsUi() {
        return mIsInSettingsUi;
    }

    private void enterSettingsUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && getFragmentManager().isDestroyed()) {
            // Weird race condition where fragment is doing work after the activity is destroyed
            // due to talkback being on (b/10209937). Just return since we can't do any
            // constructive here.
            return;
        }

        ((TextView)mToolbar.findViewById(R.id.title)).setText(R.string.settings);
        mToolbar.findViewById(R.id.sub_title).setVisibility(View.GONE);

        invalidateOptionsMenu();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        SettingsFragment fragment = (SettingsFragment) getFragmentManager().findFragmentByTag(SettingsFragment.TAG_SETTINGS_FRAGMENT);
        transaction.setCustomAnimations(android.R.animator.fade_in, 0);
        if (fragment == null) {
            fragment = new SettingsFragment();
            transaction.add(R.id.dial_frame, fragment, SettingsFragment.TAG_SETTINGS_FRAGMENT);
        } else {
            transaction.show(fragment);
        }
        transaction.commit();

        mDrawerLayout.post(new Runnable() {
            @Override
            public void run() {
                mDrawerLayout.closeDrawer(mDrawerView);
                mDrawerToggle.setDrawerIndicatorEnabled(false);
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

            }
        });

        setFabButtonVisibility(View.GONE);
        mIsInSettingsUi = true;
    }

    private void exitSettingsUi() {
        mIsInSettingsUi = false;
        setFabButtonVisibility(View.VISIBLE);
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        invalidateOptionsMenu();
        restoreActionBar();

        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        SettingsFragment fragment = (SettingsFragment) getFragmentManager().findFragmentByTag(SettingsFragment.TAG_SETTINGS_FRAGMENT);
        if (fragment != null) {
            transaction.remove(fragment);
        }
        transaction.commit();
    }

    private void setFabButtonVisibility(int visibility) {
        final View floatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        floatingActionButtonContainer.setVisibility(visibility);
    }

    private void callImmediateOrAsk(String number) {
        if (number != null && mDialpadFragment != null) {
            boolean wasModified = checkCallToNr(number, mDialpadFragment);
            if (!wasModified)
                mDialpadFragment.dialButtonPressed();
            else {
                if (isInSearchUi() && !mIsDialpadShown)
                    exitSearchUi();
                showDialpadFragment(true /* animate */, true /* autoDial */);
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

    private void startConversation(final String number) {
        final Intent conversationIntent = ContactsUtils.getMessagingIntent(number, this);
        DialerUtils.startActivityWithErrorToast(this, conversationIntent,
                R.string.add_contact_not_available);

    }

    private void startConversationFromContacts(final String aliasName) {
        AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask(getApplicationContext()) {
            @Override
            protected void onPostExecute(Integer time) {
                super.onPostExecute(time);
                if (mData != null && mUserInfo.mUuid != null) {
                    Intent msgIntent = new Intent(DialerActivity.this, ConversationActivity.class);
                    Extra.PARTNER.to(msgIntent, mUserInfo.mUuid);
                    Extra.ALIAS.to(msgIntent, aliasName);   // Check if we should use the default alias (mAlias)?
                    if (ConfigurationUtilities.mTrace)
                        Log.d(TAG, "Start messaging with: " + mUserInfo.mUuid + ", (" + aliasName + ")");
                    startActivity(msgIntent);
                    finish();
                }
            }
        };
        AsyncUtils.execute(getUserDataTask, Utilities.removeUriPartsSelective(aliasName));
    }

    private void activateTraversal() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
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

    public static String ENABLE_UNDERFLOW_TONE = "enable_underflow_tone";

    public void activateDropoutTone() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean mUseUnderflow = prefs.getBoolean(ENABLE_UNDERFLOW_TONE, true);
        TiviPhoneService.doCmd("set cfg.iAudioUnderflow=" + (mUseUnderflow ? "1" : "0"));
    }

    private void activateDebugSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int sslDebugLevel = prefs.getInt(SettingsFragment.DEVELOPER_SSL_DEBUG, 0);
        int axoDebugLevel = prefs.getInt(SettingsFragment.DEVELOPER_AXO_DEBUG, 2);

        TiviPhoneService.doCmd("debug.option=ssl_level:" + sslDebugLevel);
        TiviPhoneService.doCmd("debug.option=axo_level:" + axoDebugLevel);

    }
}
