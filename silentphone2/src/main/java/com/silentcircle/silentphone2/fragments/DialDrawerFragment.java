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

import android.app.Activity;
import android.support.v7.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.keystore.KeyStoreActivity;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.activities.ProvisioningActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.LoadUserInfo;
import com.silentcircle.silentphone2.util.Utilities;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class DialDrawerFragment extends Fragment implements View.OnClickListener, LoadUserInfo.Listener {

    private static final String TAG = DialDrawerFragment.class.getSimpleName();

    public static String RINGTONE_KEY = "sp_ringtone";
    public static String START_ON_BOOT = "start_on_boot";
    public static String NATIVE_CALL_CHECK = "native_call_check";
    public static String ENABLE_FW_TRAVERSAL = "enable_fw_traversal";
    public static String FORCE_FW_TRAVERSAL = "force_fw_traversal";

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private DrawerCallbacks mCallbacks;

    private DrawerLayout mDrawerLayout;
    private ScrollView mDrawerView;
    private View mFragmentContainerView;
    private ActionBarDrawerToggle mDrawerToggle;
//    private String mNumber;
//    private String mName;
    private String mExpirationDateString;

    private ActionBarActivity mParent;
    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public static interface DrawerCallbacks {
        public static int OPENED = 1;
        public static int CLOSED = 2;
        public static int MOVING = 3;

        public static int RINGTONE = 1;
        public static int DIAL_HELPER = 2;
        public static int KEY_STORE = 3;
        public static int RE_PROVISION = 4;

        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onDrawerItemSelected(int type, Object... params);
        /**
         * Called when an item in the navigation drawer is selected.
         */
        void onDrawerStateChange(int action);
    }

    public DialDrawerFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Indicate that this fragment would like to influence the set of actions in the action/tool bar
        // necessary for ActionBarDrawerToggle
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerView = (ScrollView)inflater.inflate(R.layout.dialer_drawer, container, false);
        setBuildInfo();
        setNumberName();
        prepareKeyStoreOptions();
        prepareRingtone();
        prepareOnBoot();
        prepareReProvision();
        prepareUiTheme();
        prepareAdvancedSettings();

        ((TextView)mDrawerView.findViewById(R.id.sc_privacy)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView)mDrawerView.findViewById(R.id.sc_tos)).setMovementMethod(LinkMovementMethod.getInstance());

        mDrawerView.findViewById(R.id.show_oca_minutes).setOnClickListener(this);
        return mDrawerView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParent = (ActionBarActivity)activity;
        try {
            mCallbacks = (DrawerCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement DrawerCallbacks.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu (Menu menu) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.dial_menu_keystore: {
                selectKeyStorePassword();
                return true;
            }
            case R.id.dial_menu_re_provision:
                mCallbacks.onDrawerItemSelected(DrawerCallbacks.RE_PROVISION);
                return true;

            default:
                break;
        }
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.nist_check:
            case R.id.nist_check_title:
                toggleNistCheckbox();
                break;

            case R.id.sas_type_title:
            case R.id.sas_type_text:
                selectSasType();
                break;

            case R.id.ringtone_title:
            case R.id.ringtone_name:
                selectRingtone();
                break;

            case R.id.boot_check:
            case R.id.boot_check_title:
                toggleBootCheckbox();
                break;

            case R.id.native_call_checkbox:
            case R.id.native_call_check_title:
                toggleNativeCallCheck();
                break;

            case R.id.re_provision_title:
            case R.id.re_provision_text:
                doReProvision();
                break;

            case R.id.theme_title:
            case R.id.theme_text:
                selectUiTheme();
                break;

            case R.id.dial_helper:
            case R.id.dial_helper_name:
            case R.id.dial_helper_title:
                selectDialHelper();
                break;

            case R.id.media_relay_check:
            case R.id.media_relay_title:
                toggleMediaRelayCheckbox();
                break;

            case R.id.underflow_check:
            case R.id.underflow_title:
                toggleUnderflowCheckbox();
                break;

            case R.id.keystore_title:
            case R.id.keystore_type:
                selectKeyStorePassword();
                break;

            case R.id.traversal_check:
            case R.id.traversal_title:
                toggleEnableTraversalBox();
                break;

            case R.id.force_traversal_check:
            case R.id.force_traversal_title:
                toggleForceTraversalBox();
                break;

            case R.id.show_oca_minutes:
                LoadUserInfo loadUserInfo = new LoadUserInfo(mParent, this);
                loadUserInfo.loadOcaMinutesInfo();
                break;

            case R.id.extended_menu:
                selectExtendedMenu();
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
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
            private boolean openState;

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }

                mParent.invalidateOptionsMenu(); // calls onPrepareOptionsMenu()
                openState = false;
                if (mCallbacks != null)
                    mCallbacks.onDrawerStateChange(DrawerCallbacks.CLOSED);

            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
                if (!openState &&
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
                openState = true;
                setBuildInfo();
                prepareSasOptions();
                prepareNistCheckbox();
                showAdvancedSettings();
                prepareDialHelperOption();
                prepareExtendedMenu();
                prepareNativeCallCheck();
                setNumberName();
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

    /**
     * Listener that receives the data if OCA minutes are available from LoadUserInfo.
     *
     * @param minutesLeft Remaining minutes
     * @param baseMinutes Minutes available per month
     * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
     *                    this shows the error reason
     */
    public void ocaMinutes(final int minutesLeft, final int baseMinutes, final String errorInfo) {
        if (errorInfo != null) {
            showInputInfo(errorInfo);
            return;
        }
        final int minutesUsed = baseMinutes - minutesLeft;
        final String msg = (minutesUsed <= 0)? getString(R.string.remaining_oca_minutes_zero, baseMinutes) :
                getResources().getQuantityString(R.plurals.remaining_oca_minutes_info, minutesUsed, baseMinutes, minutesLeft);
        showInputInfo(msg);
    }

    public void setNumberName() {
        if (mDrawerView == null)
            return;

        mExpirationDateString = LoadUserInfo.getExpirationDateString();
        if (mExpirationDateString != null) {
            View line = mDrawerView.findViewById(R.id.dial_drawer_valid);  // layout holds valid until line
            line.setVisibility(View.VISIBLE);
            ((TextView) mDrawerView.findViewById(R.id.dial_drawer_valid_data)).setText(mExpirationDateString);
        }
        if (!TextUtils.isEmpty(DialerActivity.mNumber))
            mDrawerView.findViewById(R.id.show_oca_minutes).setVisibility(View.VISIBLE);
    }

    public void updateRingtone(Uri ringtone) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        String toneString;

        if (ringtone != null) {
            toneString = ringtone.toString();
            SharedPreferences.Editor e = prefs.edit();
            e.putString(RINGTONE_KEY, toneString).apply();
        }
        else
            toneString = prefs.getString(RINGTONE_KEY, null);

        Uri tone = Settings.System.DEFAULT_RINGTONE_URI;
        if (!TextUtils.isEmpty(toneString))
            tone = Uri.parse(toneString);

        Ringtone ring = RingtoneManager.getRingtone(mParent, tone);
        if (ring != null)
            mRingtoneName.setText(ring.getTitle(mParent));
        else
            mRingtoneName.setText(getString(R.string.no_ringtone));
    }

    public void updateDialHelper() {
        if (FindDialHelper.showDialHelperOption())
            ((TextView)mDrawerView.findViewById(R.id.dial_helper_name)).setText(FindDialHelper.getCountryName(mParent));
    }

    private String createDetailInfo() {
        Display display = mParent.getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        Configuration config = getResources().getConfiguration();
        final int size = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        final String swSetting = getString(R.string.sw_setting);
//        return "Active configuration: " +
//                (ConfigurationUtilities.mUseDevelopConfiguration ? " Develop" : "Production") +
//                "\nDevice information:\n" +
//                Build.MANUFACTURER + ", " + Build.BRAND + ", " + Build.MODEL + ", " + Build.DEVICE +
//                "\nScreen density: " + metrics.densityDpi + " (" + size + ", " + swSetting + ")" +
//                ((DialerActivity.mAutoAnswerForTesting) ? "\nAuto answered: " + DialerActivity.mAutoAnsweredTesting : "");
        return getResources().getString(R.string.active_configuration)+": " +
                (ConfigurationUtilities.mUseDevelopConfiguration ? getResources().getString(R.string.develop) : getResources().getString(R.string.production) )+
                "\n"+getResources().getString(R.string.device_information)+"\n" +
                Build.MANUFACTURER + ", " + Build.BRAND + ", " + Build.MODEL + ", " + Build.DEVICE +
                "\n"+getResources().getString(R.string.screen_density)+": " + metrics.densityDpi + " (" + size + ", " + swSetting + ")" +
                ((DialerActivity.mAutoAnswerForTesting) ? "\n"+getResources().getString(R.string.auto_answered) + DialerActivity.mAutoAnsweredTesting : "");
    }

    private void setBuildInfo() {
        final String buildString = BuildConfig.VERSION_NAME + " (" + BuildConfig.SPA_BUILD_NUMBER + ", " + BuildConfig.SPA_BUILD_COMMIT + ")";
        ((TextView)mDrawerView.findViewById(R.id.dial_drawer_build_number)).setText(buildString);

        TextView flavor = (TextView)mDrawerView.findViewById(R.id.dial_drawer_build_flavor);
        flavor.setText(getString(R.string.dial_drawer_build_flavor, BuildConfig.FLAVOR));
        flavor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TextView detail = (TextView)mDrawerView.findViewById(R.id.dial_drawer_build_detail);
                if (detail.getVisibility() != View.VISIBLE) {
                    detail.setText(createDetailInfo());
                    detail.setVisibility(View.VISIBLE);
                    mDrawerView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mDrawerView.fullScroll(View.FOCUS_DOWN);
                        }
                    }, 100);
                }
                else
                    detail.setVisibility(View.GONE);
            }
        });
        flavor.setClickable(true);

        if ("debug".equals(BuildConfig.BUILD_TYPE) || "develop".equals(BuildConfig.BUILD_TYPE)) {
            TextView detail = (TextView)mDrawerView.findViewById(R.id.dial_drawer_build_detail);
            detail.setText(createDetailInfo());
            detail.setVisibility(View.VISIBLE);
        }
    }

    public void prepareKeyStoreOptions() {
        TextView typeView = (TextView)mDrawerView.findViewById(R.id.keystore_type);
        typeView.setOnClickListener(this);
        mDrawerView.findViewById(R.id.keystore_title).setOnClickListener(this);

        int type = KeyStoreHelper.getUserPasswordType(mParent);
        String text = getString(R.string.key_store_type_default);
        switch (type) {
            case KeyStoreHelper.USER_PW_TYPE_PW:
                text = getString(R.string.key_store_type_pw);
                break;
            case KeyStoreHelper.USER_PW_TYPE_PIN:
                text = getString(R.string.key_store_type_pin);
                break;
        }
        typeView.setText(text);
    }

    /*
     * The following part contains private methods which handle 'settings'
     *
     * Depending on the required visibility I define some private variables near the
     * methods, not on top of the class
     */

    /*
     *** Key store password handling
     */
    private void selectKeyStorePassword() {
        final int type = KeyStoreHelper.getUserPasswordType(mParent);
        PopupMenu popupMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.dial_drawer_name));
        switch (type) {
            case KeyStoreHelper.USER_PW_TYPE_NONE:
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_set_pw, Menu.NONE, R.string.key_store_set_pw);
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_set_pin, Menu.NONE, R.string.key_store_set_pin);
                break;
            case KeyStoreHelper.USER_PW_TYPE_PW:
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_change_pw, Menu.NONE, R.string.key_store_change_pw);
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_reset_default, Menu.NONE, R.string.key_store_reset_default);
                break;
            case KeyStoreHelper.USER_PW_TYPE_PIN:
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_change_pin, Menu.NONE, R.string.key_store_change_pin);
                popupMenu.getMenu().add(Menu.NONE, R.string.key_store_reset_default, Menu.NONE, R.string.key_store_reset_default);
                break;
        }
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String action;
                switch (item.getItemId()) {
                    case R.string.key_store_set_pw:
                        action = KeyStoreActivity.KEY_STORE_SET_PASSWORD;
                        break;
                    case R.string.key_store_set_pin:
                        action = KeyStoreActivity.KEY_STORE_SET_PIN;
                        break;
                    case R.string.key_store_change_pw:
                        action = KeyStoreActivity.KEY_STORE_CHANGE_PASSWORD;
                        break;
                    case R.string.key_store_change_pin:
                        action = KeyStoreActivity.KEY_STORE_CHANGE_PIN;
                        break;
                    case R.string.key_store_reset_default:
                        action = (type == KeyStoreHelper.USER_PW_TYPE_PW) ?
                                KeyStoreActivity.KEY_STORE_RESET_PASSWORD : KeyStoreActivity.KEY_STORE_RESET_PIN;
                        break;
                    default:
                        return false;
                }
                mCallbacks.onDrawerItemSelected(DrawerCallbacks.KEY_STORE, action);
                return true;
            }
        });
        popupMenu.show();
    }

    /*
     *** SAS type selection
     */
    private String sasTypeTexts[];
    private TextView mSasType;
    private boolean mSas256Enabled;
    private static int CHAR_MODE = 0;
    private static int WORD_MODE = 1;

    private void prepareSasOptions() {
        mSasType = (TextView)mDrawerView.findViewById(R.id.sas_type_text);
        mSasType.setOnClickListener(this);
        mDrawerView.findViewById(R.id.sas_type_title).setOnClickListener(this);
        sasTypeTexts = new String[] {
                getString(R.string.sas_char_mode),
                getString(R.string.sas_word_mode),
        };
        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iDisable256SAS");

        mSas256Enabled = "0".equals(result);     // negative logic: if 0 then B256 is not disabled -> enabled :-)
        String sasTypeText = (mSas256Enabled) ? sasTypeTexts[WORD_MODE] : sasTypeTexts[CHAR_MODE];
        mSasType.setText(sasTypeText);
    }

    private void selectSasType() {
        PopupMenu popupMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.sas_type_text));
        if (mSas256Enabled)
            popupMenu.getMenu().add(Menu.NONE, R.string.sas_char_mode, Menu.NONE, sasTypeTexts[CHAR_MODE]);
        else
            popupMenu.getMenu().add(Menu.NONE, R.string.sas_word_mode, Menu.NONE, sasTypeTexts[WORD_MODE]);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.string.sas_char_mode:
                        mSasType.setText(sasTypeTexts[CHAR_MODE]);
                        TiviPhoneService.doCmd("set cfg.iDisable256SAS=1");
                        mSas256Enabled = false;
                        return true;
                    case R.string.sas_word_mode:
                        mSasType.setText(sasTypeTexts[WORD_MODE]);
                        TiviPhoneService.doCmd("set cfg.iDisable256SAS=0");
                        mSas256Enabled = true;
                        return true;
                    default:
                        return false;
                }
            }
        });
        popupMenu.show();
    }

    /*
     *** NIST setting
     */
    private boolean mNistPreferred;
    private CheckBox mNistBox;

    private void prepareNistCheckbox() {
        mNistBox = (CheckBox)mDrawerView.findViewById(R.id.nist_check);
        mNistBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.nist_check_title).setOnClickListener(this);

        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iPreferNIST");
        mNistPreferred = "1".equals(result);             // 1 -> true, do prefer NIST
        mNistBox.setChecked(!mNistPreferred);            // The checkbox title asks for the inverted intention
    }

    private void toggleNistCheckbox() {
        mNistPreferred = !mNistPreferred;
        if (mNistPreferred)
            TiviPhoneService.doCmd("set cfg.iPreferNIST=1");
        else
            TiviPhoneService.doCmd("set cfg.iPreferNIST=0");
        mNistBox.setChecked(!mNistPreferred);            // The checkbox title asks for the inverted intention
    }

    /*
     *** Start On-boot checkbox handling
     */
    private boolean mStartOnBoot;
    private CheckBox mBootBox;

    private void prepareOnBoot() {
        mBootBox = (CheckBox)mDrawerView.findViewById(R.id.boot_check);
        mBootBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.boot_check_title).setOnClickListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mStartOnBoot = prefs.getBoolean(START_ON_BOOT, true);
        mBootBox.setChecked(mStartOnBoot);
    }

    private void toggleBootCheckbox() {
        mStartOnBoot = !mStartOnBoot;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(START_ON_BOOT, mStartOnBoot).apply();
        mBootBox.setChecked(mStartOnBoot);
    }

    /*
     *** Option to monitor call from native contact application
     */
    private boolean mNativeCallCheck;
    private CheckBox mNativeCallBox;

    private void prepareNativeCallCheck() {
        mNativeCallBox = (CheckBox)mDrawerView.findViewById(R.id.native_call_checkbox);
        mNativeCallBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.native_call_check_title).setOnClickListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mNativeCallCheck = prefs.getBoolean(NATIVE_CALL_CHECK, false);
        mNativeCallBox.setChecked(mNativeCallCheck);
    }

    private void toggleNativeCallCheck() {
        mNativeCallCheck = !mNativeCallCheck;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(NATIVE_CALL_CHECK, mNativeCallCheck).apply();
        mNativeCallBox.setChecked(mNativeCallCheck);
    }

    /*
     *** re-provisioning
     */
    private void prepareReProvision() {
        mDrawerView.findViewById(R.id.re_provision_title).setOnClickListener(this);
    }

    private void doReProvision() {
        mCallbacks.onDrawerItemSelected(DrawerCallbacks.RE_PROVISION);
    }

    /*
     *** Ringtone selection
     */
    private TextView mRingtoneName;

    private void prepareRingtone() {
        mRingtoneName = (TextView)mDrawerView.findViewById(R.id.ringtone_name);
        mRingtoneName.setOnClickListener(this);
        mDrawerView.findViewById(R.id.ringtone_title).setOnClickListener(this);
        updateRingtone(null);                           // shows current selection
    }

    private void selectRingtone() {
        if (mCallbacks == null)
            return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);

        String toneString = prefs.getString(RINGTONE_KEY, null);

        Uri selected = Settings.System.DEFAULT_RINGTONE_URI;
        if (!TextUtils.isEmpty(toneString))
            selected = Uri.parse(toneString);

        mCallbacks.onDrawerItemSelected(DrawerCallbacks.RINGTONE, selected);
    }

    /*
     *** UI Theme selection
     */
    private void prepareUiTheme() {
        TextView themeText = (TextView)mDrawerView.findViewById(R.id.theme_text);
        themeText.setOnClickListener(this);
        mDrawerView.findViewById(R.id.theme_title).setOnClickListener(this);

        // Change here if standard (startup) theme changes
        String theme = getString(R.string.current_theme, Utilities.getSelectedTheme(mParent));
        themeText.setText(theme);
    }

    private void selectUiTheme() {
        if (mCallbacks == null)
            return;

        PopupMenu popupMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.theme_title));
        popupMenu.getMenu().add(Menu.NONE, R.string.theme_orange, Menu.NONE, getString(R.string.theme_orange));
        popupMenu.getMenu().add(Menu.NONE, R.string.theme_white, Menu.NONE, getString(R.string.theme_white));
        popupMenu.getMenu().add(Menu.NONE, R.string.theme_dusk, Menu.NONE, getString(R.string.theme_dusk));
        popupMenu.getMenu().add(Menu.NONE, R.string.theme_black, Menu.NONE, getString(R.string.theme_black));
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.string.theme_orange:
                        Utilities.storeThemeSelection(mParent, getString(item.getItemId()), R.style.SilentPhoneThemeOrange);
                        return true;
                    case R.string.theme_white:
                        Utilities.storeThemeSelection(mParent, getString(item.getItemId()), R.style.SilentPhoneThemeWhite);
                        return true;
                    case R.string.theme_dusk:
                        Utilities.storeThemeSelection(mParent, getString(item.getItemId()), R.style.SilentPhoneThemeDusk);
                        return true;
                    case R.string.theme_black:
                        Utilities.storeThemeSelection(mParent, getString(item.getItemId()), R.style.SilentPhoneThemeBlack);
                        return true;
                    default:
                        return false;
                }
            }
        });
        popupMenu.show();
    }

    /*
     *** Dial assist handling
     */
    private void prepareDialHelperOption() {
        if (FindDialHelper.showDialHelperOption() && (ConfigurationUtilities.mTrace || !TextUtils.isEmpty(DialerActivity.mNumber))) {
            mDrawerView.findViewById(R.id.dial_helper).setVisibility(View.VISIBLE);
            ((TextView)mDrawerView.findViewById(R.id.dial_helper_name)).setText(FindDialHelper.getCountryName(mParent));
            mDrawerView.findViewById(R.id.dial_helper).setOnClickListener(this);
            mDrawerView.findViewById(R.id.dial_helper_name).setOnClickListener(this);
        }
    }

    private void selectDialHelper() {
        mCallbacks.onDrawerItemSelected(DrawerCallbacks.DIAL_HELPER);
    }

    private void showInputInfo(String msg) {
        ProvisioningActivity.InfoMsgDialogFragment infoMsg = ProvisioningActivity.InfoMsgDialogFragment.newInstance(msg);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager to show error info: " + msg);
            Toast.makeText(mParent, msg, Toast.LENGTH_LONG).show();
            return;
        }
        infoMsg.show(fragmentManager, "SilentPhoneOcaInfo");
    }

    private PopupMenu mExtendedMenu;
    private void prepareExtendedMenu() {
        if (mExtendedMenu == null) {
            mExtendedMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.extended_menu));
            mDrawerView.findViewById(R.id.extended_menu).setOnClickListener(this);
            mExtendedMenu.inflate(R.menu.dial_drawer);
        }
        if (ConfigurationUtilities.mEnableDevDebOptions) {
            Menu menu = mExtendedMenu.getMenu();
            menu.setGroupVisible(R.id.dial_group_develop, true);
            MenuItem menuItem = menu.findItem(R.id.dial_menu_production);
            menuItem.setVisible(ConfigurationUtilities.mUseDevelopConfiguration);

            menuItem = menu.findItem(R.id.dial_menu_develop);
            menuItem.setVisible(!ConfigurationUtilities.mUseDevelopConfiguration);

            menuItem = menu.findItem(R.id.dial_menu_answer_on);
            menuItem.setVisible(false /* !DialerActivity.mAutoAnswerForTesting */);

            menuItem = menu.findItem(R.id.dial_menu_answer_off);
            menuItem.setVisible(false /*DialerActivity.mAutoAnswerForTesting*/);
        }
        mExtendedMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onOptionsItemSelected(item) || mParent.onOptionsItemSelected(item);
            }
        });
    }

    private void selectExtendedMenu() {
        if (mExtendedMenu == null)
            return;
        mExtendedMenu.show();
    }
    /*
     *** Advanced setting, available after *##*123*
     */
    private boolean mUseMediaRelay;
    private CheckBox mMediaRelayBox;

    private boolean mUseUnderflow;
    private CheckBox mUnderflowBox;

    private boolean mEnableTraversal;
    private CheckBox mEnableTraversalBox;

    private boolean mForceTraversal;
    private CheckBox mForceTraversalBox;

    private void prepareAdvancedSettings() {
        // Setup media relay check box
        mMediaRelayBox = (CheckBox)mDrawerView.findViewById(R.id.media_relay_check);
        mMediaRelayBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.media_relay_title).setOnClickListener(this);

        // Setup underflow check box
        mUnderflowBox = (CheckBox)mDrawerView.findViewById(R.id.underflow_check);
        mUnderflowBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.underflow_title).setOnClickListener(this);

        // Enable FW traversal
        mEnableTraversalBox = (CheckBox)mDrawerView.findViewById(R.id.traversal_check);
        mEnableTraversalBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.traversal_title).setOnClickListener(this);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mEnableTraversal = prefs.getBoolean(ENABLE_FW_TRAVERSAL, true);
        mEnableTraversalBox.setChecked(mEnableTraversal);

        // Prepare force FW traversal
        mForceTraversalBox = (CheckBox)mDrawerView.findViewById(R.id.force_traversal_check);
        mForceTraversalBox.setOnClickListener(this);
        mDrawerView.findViewById(R.id.force_traversal_title).setOnClickListener(this);
        mForceTraversal = prefs.getBoolean(FORCE_FW_TRAVERSAL, false);
        if (!mEnableTraversal)
            mForceTraversal = false;
        mForceTraversalBox.setChecked(mForceTraversal);
    }

    public void activateTraversal() {
        if (mEnableTraversal)
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=1");
        else {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=0");
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
        }
        if (mForceTraversal)
            TiviPhoneService.doCmd("set cfg.ForceFWTraversal=1");
        else {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
        }
    }

    private void toggleEnableTraversalBox() {
        mEnableTraversal = !mEnableTraversal;
        boolean show = ((DialerActivity)mParent).isAdvancedSettings();
        mEnableTraversalBox.setChecked(mEnableTraversal);
        if (mEnableTraversal) {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=1");
            if (show)
                mDrawerView.findViewById(R.id.force_traversal).setVisibility(View.VISIBLE);
        }
        else {
            TiviPhoneService.doCmd("set cfg.iEnableFWTraversal=0");
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
            mForceTraversal = false;
            if (show)
                mDrawerView.findViewById(R.id.force_traversal).setVisibility(View.GONE);
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(ENABLE_FW_TRAVERSAL, mEnableTraversal)
                .putBoolean(FORCE_FW_TRAVERSAL, mForceTraversal).apply();
    }

    private void toggleForceTraversalBox() {
        mForceTraversal = !mForceTraversal;
        mForceTraversalBox.setChecked(mForceTraversal);
        if (mForceTraversal) {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=1");
        }
        else {
            TiviPhoneService.doCmd("set cfg.iForceFWTraversal=0");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(FORCE_FW_TRAVERSAL, mForceTraversal).apply();
    }

    private void toggleUnderflowCheckbox() {
        mUseUnderflow = !mUseUnderflow;
        if (mUseUnderflow)
            TiviPhoneService.doCmd("set cfg.iAudioUnderflow=1");
        else
            TiviPhoneService.doCmd("set cfg.iAudioUnderflow=0");
        TiviPhoneService.doCmd(":s");
        mUnderflowBox.setChecked(mUseUnderflow);
    }


    private void toggleMediaRelayCheckbox() {
        mUseMediaRelay = !mUseMediaRelay;
        if (mUseMediaRelay)
            TiviPhoneService.doCmd("set cfg.iCanUseP2Pmedia=0");
        else
            TiviPhoneService.doCmd("set cfg.iCanUseP2Pmedia=1");
        TiviPhoneService.doCmd(":s");
        mMediaRelayBox.setChecked(mUseMediaRelay);
    }

    private void showAdvancedSettings() {
        // Show dropout tone option (underflow) always
        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iAudioUnderflow");
        mUseUnderflow = "1".equals(result);             // 1 -> true,
        mUnderflowBox.setChecked(mUseUnderflow);

        boolean show = ((DialerActivity)mParent).isAdvancedSettings();
        View v = mDrawerView.findViewById(R.id.media_relay);
        if (show) {
            result = TiviPhoneService.getInfo(0, -1, "cfg.iCanUseP2Pmedia");
            mUseMediaRelay = "0".equals(result);             // 1 -> true, client can use p2p
            mMediaRelayBox.setChecked(mUseMediaRelay);      // The checkbox title asks for the inverted intention
            v.setVisibility(View.VISIBLE);
        }
        else
            v.setVisibility(View.GONE);

        // Show force FW enable only if FW enabled is set
        v = mDrawerView.findViewById(R.id.force_traversal);
        if (show && mEnableTraversal) {
            mForceTraversalBox.setChecked(mForceTraversal);
            v.setVisibility(View.VISIBLE);
        }
        else
            v.setVisibility(View.GONE);
    }
}
