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

package com.silentcircle.silentphone2.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.RingtoneUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.keystore.KeyStoreActivity;
import com.silentcircle.keystore.KeyStoreHelper;
import com.silentcircle.messaging.activities.AxoRegisterActivity;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.util.MessagingPreferences;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialhelpers.FindDialHelper;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.fragments.DialDrawerFragment.DrawerCallbacks;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.views.SettingsItem;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

/**
 *
 */
public class SettingsFragment extends Fragment implements View.OnClickListener,
        LoadUserInfo.Listener, SettingsItem.OnCheckedChangeListener,
        RadioGroup.OnCheckedChangeListener,
        SingleChoiceDialogFragment.OnSingleChoiceDialogItemSelectedListener {

    public static final String TAG_SETTINGS_FRAGMENT = "com.silentcircle.silentphone2.fragments.Settings";

    // @TODO: do not define them in dial drawer fragment and here, create a separate settings class
    public static String RINGTONE_KEY = "sp_ringtone";
    public static String RINGTONE_EMERGENCY_KEY = "sp_ringtone_emergency";
    public static String START_ON_BOOT = "start_on_boot";
    public static String SHOW_ERRORS = "show_errors";
    public static String NATIVE_CALL_CHECK = "native_call_check";
    public static String ENABLE_FW_TRAVERSAL = "enable_fw_traversal";
    public static String FORCE_FW_TRAVERSAL = "force_fw_traversal";
    public static String ENABLE_UNDERFLOW_TONE = "enable_underflow_tone";
    public static String DEVELOPER = "developer";
    public static String DEVELOPER_SSL_DEBUG = "developer_ssl_debug";
    public static String DEVELOPER_AXO_DEBUG = "developer_axo_debug";
    public static String BLOCK_SCREENSHOTS = "block_screenshots";

    public static final int MESSAGE_LIGHTS_SELECTION_DIALOG = 1000;
    public static final int MESSAGE_VIBRATE_SELECTION_DIALOG = 1010;
    public static final int MESSAGE_THEME_SELECTION_DIALOG = 1020;

    @SuppressWarnings("unused")
    private static final String TAG = SettingsFragment.class.getSimpleName();

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private DrawerCallbacks mCallbacks;

    private ScrollView mDrawerView;

    private AppCompatActivity mParent;

    private TextView mDeveloperHeader;

    private LinearLayout mDeveloperContent;

    public SettingsFragment() {
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerView = (ScrollView)inflater.inflate(R.layout.settings_fragment, container, false);

        mDeveloperHeader = (TextView)mDrawerView.findViewById(R.id.developer_header);

        mDeveloperContent = (LinearLayout)mDrawerView.findViewById(R.id.developer_content);

        return mDrawerView;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        commonOnAttach(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();

        setBuildInfo();
        prepareSasOptions();
        prepareNistCheckbox();
        prepareKeyStoreOptions();
        prepareRingtone();
        prepareDialHelperOption();
        prepareOnBoot();
        prepareShowErrors();
        // prepareReProvision();
        // prepareUiTheme();
        prepareAdvancedSettings();
        prepareMessagingSettings();
        prepareExtendedMenu();
        prepareBlockScreenshots();
        if (ConfigurationUtilities.mEnableDevDebOptions) {
            prepareDeveloper();
            prepareDeveloperMenu();
        }
        showAdvancedSettings();
    }

    /*
     * Deprecated on API 23
     * Use onAttachToContext instead
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        commonOnAttach(activity);
    }

    private void commonOnAttach(Activity activity) {
        try {
            mParent = (AppCompatActivity) activity;
            mCallbacks = (DrawerCallbacks) activity;

            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            }

            activity.invalidateOptionsMenu();
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
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                return false;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.nist_check:
                toggleNistCheckbox();
                break;

            case R.id.sas_type:
                selectSasType();
                break;

            case R.id.ringtone:
                selectRingtone();
                break;

            case R.id.boot_check:
                toggleBootCheckbox();
                break;

            case R.id.errors_option_check:
                toggleErrorsCheckbox();
                break;

            case R.id.re_provision:
                doReProvision();
                break;

            case R.id.dial_helper:
                selectDialHelper();
                break;

            case R.id.underflow_check:
                toggleUnderflowCheckbox();
                break;

            case R.id.traversal_check:
                toggleEnableTraversalBox();
                break;

            case R.id.force_traversal:
                toggleForceTraversalBox();
                break;

            case R.id.media_relay_check:
                toggleMediaRelayCheckbox();
                break;

            case R.id.attachment_decrypt_warn_check:
                toggleMessageAttachmentDecryptWarnCheckbox();
                break;

            case R.id.messaging_sounds_check:
                toggleMessageSoundsCheckbox();
                break;

            case R.id.messaging_ringtone:
                selectMessageRingtone();
                break;

            case R.id.messaging_light:
                showSelectionDialog(R.string.dialog_title_select_notification_light,
                        R.array.lights_array, mMessageLightIndex, MESSAGE_LIGHTS_SELECTION_DIALOG);
                break;

            case R.id.messaging_vibration:
                showSelectionDialog(R.string.dialog_title_select_notification_vibrate,
                        R.array.vibrate_array, mMessageVibrateIndex, MESSAGE_VIBRATE_SELECTION_DIALOG);
                break;

            case R.id.messaging_theme:
                showSelectionDialog(R.string.dialog_title_select_messaging_theme,
                        R.array.message_theme_array, mMessageThemeIndex, MESSAGE_THEME_SELECTION_DIALOG);
                break;

            case R.id.extended_menu:
                selectExtendedMenu();
                break;

            case R.id.messaging_lock_configuration:
                mCallbacks.onDrawerItemSelected(DrawerCallbacks.MESSAGING_LOCK_SCREEN,
                        KeyStoreActivity.KEY_STORE_SET_CHAT_PASSWORD_CHANGE);
                break;

            case R.id.settings_chat_device_management:
                {
                    Intent intent = new Intent(mParent, AxoRegisterActivity.class);
                    intent.setAction(AxoRegisterActivity.ACTION_MANAGE);
                    startActivity(intent);
                }
                break;

            case R.id.settings_set_passphrase:
                selectKeyStorePassword();
                break;

            case R.id.reload_account_data:
                doReProvision();
                break;

            case R.id.settings_switch_configuration:
                ((DialerActivity ) mParent).switchConfiguration();
                break;

            case R.id.settings_force_axo_reregister:
                {
                    AxoMessaging axoMessaging = AxoMessaging.getInstance(mParent);
                    axoMessaging.registerDeviceMessaging(true);
                }
                break;

            case R.id.settings_axo_register:
                {
                    Intent intent = new Intent(mParent, AxoRegisterActivity.class);
                    intent.setAction(AxoRegisterActivity.ACTION_REGISTER);
                    startActivity(intent);
                }
                break;

            case R.id.developer_option:
                toggleDeveloperCheckbox();
                break;

            case R.id.settings_block_screenshots:
                toggleBlockScreenshots();
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    @Override
    public void onCheckedChanged(SettingsItem view, boolean isChecked) {
        switch (view.getId()) {
            case R.id.attachment_decrypt_warn_check:
                MessagingPreferences.getInstance(getActivity())
                        .setWarnWhenDecryptAttachment(mMessageAttachmentDecryptWarnBox.isChecked());
                break;

            case R.id.messaging_sounds_check:
                MessagingPreferences.getInstance(getActivity())
                        .setMessageSoundsEnabled(mMessageSoundsBox.isChecked());
                break;
            default:
                Log.wtf(TAG, "Unexpected onCheckChanged() event from: " + view);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        switch (group.getId()) {
            case R.id.developer_ssl_debug_radio_group:
                checkDeveloperSslDebugLevelRadioGroup();
                break;

            case R.id.developer_axo_debug_radio_group:
                checkDeveloperAxoDebugLevelRadioGroup();
                break;

            default:
                Log.wtf(TAG, "Unexpected onCheckChanged() event from: " + group);
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
    public void setUp(View drawerView, DrawerLayout drawerLayout, android.support.v7.widget.Toolbar toolbar) {
    }

    /**
     *
     * @param userInfo A {@link UserInfo} object
     * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
     *                    this shows the error reason
     * @param silent      Should the user see anything?
     */
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        /**
         * Has to still be attached to be able to call {@link Fragment#getResources()}
         */
        if(!isAdded()) {
            return;
        }

        if (errorInfo != null) {
            showInputInfo(errorInfo);

            return;
        }

        if (LoadUserInfo.checkIfExpired() == LoadUserInfo.VALID) {
            showInputInfo(R.string.expired_account_info);

            return;
        }

        if (LoadUserInfo.checkIfUsesMinutes() == LoadUserInfo.VALID) {
            // User is using minutes
            final int baseMinutes = userInfo.getSubscription().getUsageDetails().getBaseMinutes()
                    + userInfo.getSubscription().getUsageDetails().getCurrentModifier();
            final int minutesLeft = userInfo.getSubscription().getUsageDetails().getMinutesLeft();
            final int minutesUsed = baseMinutes - minutesLeft;

            final String msg = (minutesUsed <= 0 && minutesLeft != 0)
                    ? getString(R.string.remaining_oca_minutes_zero, baseMinutes) :
                    getResources().getQuantityString(R.plurals.remaining_oca_minutes_info,
                            minutesUsed, baseMinutes, minutesLeft);
            showInputInfo(msg);
        } else if(LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID) {
            // User is using credit
            showInputInfo(getString(R.string.remaining_credit,
                    userInfo.getSubscription().getBalance().getAmount(),
                    userInfo.getSubscription().getBalance().getUnit()));
        }
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

        Uri tone = RingtoneUtils.getDefaultRingtoneUri(mParent);
        if (!TextUtils.isEmpty(toneString))
            tone = Uri.parse(toneString);

        Ringtone ring = RingtoneManager.getRingtone(mParent, tone);
        if (ring != null)
            mRingtoneName.setDescription(ring.getTitle(mParent));
        else
            mRingtoneName.setDescription(getString(R.string.no_ringtone));
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
                ((/*DialerActivity.mAutoAnswerForTesting*/ false) ? "\n"+getResources().getString(R.string.auto_answered) + /*DialerActivity.mAutoAnsweredTesting*/ false : "");
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
        SettingsItem typeView = (SettingsItem) mDrawerView.findViewById(R.id.settings_set_passphrase);
        typeView.setOnClickListener(this);

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
        typeView.setDescription(text);
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
        PopupMenu popupMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.settings_set_passphrase));
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
    private SettingsItem mSasType;
    private boolean mSas256Enabled;
    private static int CHAR_MODE = 0;
    private static int WORD_MODE = 1;
    private static int EMOJI_MODE = 2;

    private void prepareSasOptions() {
        mSasType = (SettingsItem) mDrawerView.findViewById(R.id.sas_type);
        mSasType.setOnClickListener(this);
        sasTypeTexts = new String[] {
                getString(R.string.sas_char_mode),
                getString(R.string.sas_word_mode),
                getString(R.string.sas_emoji_mode)
        };
        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iDisable256SAS");

        mSas256Enabled = "0".equals(result);     // negative logic: if 0 then B256 is not disabled -> enabled :-)
        String sasTypeText = "0".equals(result)
                ? sasTypeTexts[WORD_MODE]
                : "2".equals(result) ? sasTypeTexts[EMOJI_MODE] : sasTypeTexts[CHAR_MODE];
        mSasType.setDescription(sasTypeText);
    }

    private void selectSasType() {
        PopupMenu popupMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.sas_type));

        // Enable this for debug/development builds only. After some testing/discussing we either add it
        // permanently or remove the Emoji SAS
        if (ConfigurationUtilities.mTrace) {
            popupMenu.getMenu().add(Menu.NONE, R.string.sas_char_mode, Menu.NONE, sasTypeTexts[CHAR_MODE]);
            popupMenu.getMenu().add(Menu.NONE, R.string.sas_emoji_mode, Menu.NONE, sasTypeTexts[EMOJI_MODE]);
            popupMenu.getMenu().add(Menu.NONE, R.string.sas_word_mode, Menu.NONE, sasTypeTexts[WORD_MODE]);
        }
        else {
            if (mSas256Enabled) {
                popupMenu.getMenu().add(Menu.NONE, R.string.sas_char_mode, Menu.NONE, sasTypeTexts[CHAR_MODE]);
            }
            else {
                popupMenu.getMenu().add(Menu.NONE, R.string.sas_word_mode, Menu.NONE, sasTypeTexts[WORD_MODE]);
            }
        }
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.string.sas_char_mode:
                        mSasType.setDescription(sasTypeTexts[CHAR_MODE]);
                        TiviPhoneService.doCmd("set cfg.iDisable256SAS=1");
                        mSas256Enabled = false;
                        return true;
                    case R.string.sas_word_mode:
                        mSasType.setDescription(sasTypeTexts[WORD_MODE]);
                        TiviPhoneService.doCmd("set cfg.iDisable256SAS=0");
                        mSas256Enabled = true;
                        return true;
                    case R.string.sas_emoji_mode:
                        mSasType.setDescription(sasTypeTexts[EMOJI_MODE]);
                        TiviPhoneService.doCmd("set cfg.iDisable256SAS=2");
                        mSas256Enabled = false;
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
    private SettingsItem mNistBox;

    private void prepareNistCheckbox() {
        mNistBox = (SettingsItem) mDrawerView.findViewById(R.id.nist_check);
        mNistBox.setOnClickListener(this);

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
    private SettingsItem mBootBox;

    private void prepareOnBoot() {
        mBootBox = (SettingsItem) mDrawerView.findViewById(R.id.boot_check);
        mBootBox.setOnClickListener(this);

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
     *** Start errors checkbox handling
     */
    private boolean mShowErrors;
    private SettingsItem mErrorsOptionBox;

    private void prepareShowErrors() {
        mErrorsOptionBox = (SettingsItem) mDrawerView.findViewById(R.id.errors_option_check);
        mErrorsOptionBox.setOnClickListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mShowErrors = prefs.getBoolean(SHOW_ERRORS, false);
        mErrorsOptionBox.setChecked(mShowErrors);
    }

    private void toggleErrorsCheckbox() {
        mShowErrors = !mShowErrors;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(SHOW_ERRORS, mShowErrors).apply();
        DialerActivity.mShowErrors = mShowErrors;
        mErrorsOptionBox.setChecked(mShowErrors);
    }

    /**
     * Start developer checkbox handling
     */
    private boolean mDeveloper;
    private SettingsItem mDeveloperOptionBox;

    private void prepareDeveloper() {
        if (!ConfigurationUtilities.mEnableDevDebOptions) {
            return;
        }

        mDeveloperOptionBox = (SettingsItem) mDrawerView.findViewById(R.id.developer_option);
        mDeveloperOptionBox.setVisibility(View.VISIBLE);
        mDeveloperOptionBox.setOnClickListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mDeveloper = prefs.getBoolean(DEVELOPER, false);
        mDeveloperOptionBox.setChecked(mDeveloper);
        mDeveloperHeader.setVisibility(mDeveloper ? View.VISIBLE : View.GONE);
        mDeveloperContent.setVisibility(mDeveloper ? View.VISIBLE : View.GONE);
    }

    private void toggleDeveloperCheckbox() {
        mDeveloper = !mDeveloper;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(DEVELOPER, mDeveloper).apply();
        mDeveloperOptionBox.setChecked(mDeveloper);
        mDeveloperHeader.setVisibility(mDeveloper ? View.VISIBLE : View.GONE);
        mDeveloperContent.setVisibility(mDeveloper ? View.VISIBLE : View.GONE);
    }

    /**
     * Block screenshots handling
     */
    private boolean mBlockScreenshots;
    private SettingsItem mBlockScreenshotsBox;

    private void prepareBlockScreenshots() {
        mBlockScreenshotsBox = (SettingsItem) mDrawerView.findViewById(R.id.settings_block_screenshots);
        mBlockScreenshotsBox.setOnClickListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mBlockScreenshots = prefs.getBoolean(BLOCK_SCREENSHOTS, false);
        mBlockScreenshotsBox.setChecked(mBlockScreenshots);
    }

    private void toggleBlockScreenshots() {
        mBlockScreenshots = !mBlockScreenshots;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(BLOCK_SCREENSHOTS, mBlockScreenshots).apply();
        mBlockScreenshotsBox.setChecked(mBlockScreenshots);
        ViewUtil.setBlockScreenshots(getActivity());
    }

    /*
     *** Start developer menu handling
     */
    private int mDeveloperSslRadioButtonId;
    private RadioGroup mDeveloperSslDebugRadioGroup;

    private int mDeveloperAxoRadioButtonId;
    private RadioGroup mDeveloperAxoDebugRadioGroup;

    private void prepareDeveloperMenu() {
        mDeveloperSslDebugRadioGroup = (RadioGroup) mDrawerView.findViewById(R.id.developer_ssl_debug_radio_group);
        mDeveloperSslDebugRadioGroup.setOnCheckedChangeListener(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        mDeveloperSslRadioButtonId = getRadioButtonId(prefs.getInt(DEVELOPER_SSL_DEBUG, 0));
        mDeveloperSslDebugRadioGroup.check(mDeveloperSslRadioButtonId);

        // Axolotl radio button group setup
        mDeveloperAxoDebugRadioGroup = (RadioGroup) mDrawerView.findViewById(R.id.developer_axo_debug_radio_group);
        mDeveloperAxoDebugRadioGroup.setOnCheckedChangeListener(this);

        mDeveloperAxoRadioButtonId = getRadioButtonIdAxo(prefs.getInt(DEVELOPER_AXO_DEBUG, 2));
        mDeveloperAxoDebugRadioGroup.check(mDeveloperAxoRadioButtonId);
    }

    private void checkDeveloperSslDebugLevelRadioGroup() {
        mDeveloperSslRadioButtonId = mDeveloperSslDebugRadioGroup.getCheckedRadioButtonId();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putInt(DEVELOPER_SSL_DEBUG, getRadioButtonIndex(mDeveloperSslRadioButtonId)).apply();

        RadioButton checkedRadioButton = (RadioButton) mDrawerView.findViewById(mDeveloperSslRadioButtonId);
        int level = Integer.valueOf(checkedRadioButton.getText().toString());

        mCallbacks.onDrawerItemSelected(DrawerCallbacks.DEVELOPER_SSL_DEBUG, level);
    }

    private int getRadioButtonId(int index) {
        int result = R.id.developer_ssl_debug_radio_button_0;
        switch (index) {
            case 0:
                result = R.id.developer_ssl_debug_radio_button_0;
                break;
            case 1:
                result = R.id.developer_ssl_debug_radio_button_1;
                break;
            case 2:
                result = R.id.developer_ssl_debug_radio_button_2;
                break;
            case 3:
                result = R.id.developer_ssl_debug_radio_button_3;
                break;
            case 4:
                result = R.id.developer_ssl_debug_radio_button_4;
                break;
        }
        return result;
    }

    private int getRadioButtonIndex(int index) {
        int result = 0;
        switch (index) {
            case R.id.developer_ssl_debug_radio_button_0:
                result = 0;
                break;
            case R.id.developer_ssl_debug_radio_button_1:
                result = 1;
                break;
            case R.id.developer_ssl_debug_radio_button_2:
                result = 2;
                break;
            case R.id.developer_ssl_debug_radio_button_3:
                result = 3;
                break;
            case R.id.developer_ssl_debug_radio_button_4:
                result = 4;
                break;
        }
        return result;
    }

    private void checkDeveloperAxoDebugLevelRadioGroup() {
        mDeveloperAxoRadioButtonId = mDeveloperAxoDebugRadioGroup.getCheckedRadioButtonId();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putInt(DEVELOPER_AXO_DEBUG, getRadioButtonIndexAxo(mDeveloperAxoRadioButtonId)).apply();

        RadioButton checkedRadioButton = (RadioButton) mDrawerView.findViewById(mDeveloperAxoRadioButtonId);
        int level = Integer.valueOf(checkedRadioButton.getText().toString());

        mCallbacks.onDrawerItemSelected(DrawerCallbacks.DEVELOPER_AXO_DEBUG, level);
    }

    private int getRadioButtonIdAxo(int index) {
        int result = R.id.developer_axo_debug_radio_button_0;
        switch (index) {
            case 0:
                result = R.id.developer_axo_debug_radio_button_0;
                break;
            case 1:
                result = R.id.developer_axo_debug_radio_button_1;
                break;
            case 2:
                result = R.id.developer_axo_debug_radio_button_2;
                break;
            case 3:
                result = R.id.developer_axo_debug_radio_button_3;
                break;
            case 4:
                result = R.id.developer_axo_debug_radio_button_4;
                break;
            case 5:
                result = R.id.developer_axo_debug_radio_button_5;
                break;
            case 6:
                result = R.id.developer_axo_debug_radio_button_6;
                break;
        }
        return result;
    }

    private int getRadioButtonIndexAxo(int index) {
        int result = 0;
        switch (index) {
            case R.id.developer_axo_debug_radio_button_0:
                result = 0;
                break;
            case R.id.developer_axo_debug_radio_button_1:
                result = 1;
                break;
            case R.id.developer_axo_debug_radio_button_2:
                result = 2;
                break;
            case R.id.developer_axo_debug_radio_button_3:
                result = 3;
                break;
            case R.id.developer_axo_debug_radio_button_4:
                result = 4;
                break;
            case R.id.developer_axo_debug_radio_button_5:
                result = 5;
                break;
            case R.id.developer_axo_debug_radio_button_6:
                result = 6;
                break;
        }
        return result;
    }

    /*
     *** re-provisioning
     */

    private void doReProvision() {
        mCallbacks.onDrawerItemSelected(DrawerCallbacks.RE_PROVISION);
    }

    /**
     * Ringtone selection
     */
    private SettingsItem mRingtoneName;

    private void prepareRingtone() {
        mRingtoneName = (SettingsItem) mDrawerView.findViewById(R.id.ringtone);
        mRingtoneName.setOnClickListener(this);
        updateRingtone(null);                           // shows current selection
    }

    private void selectRingtone() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        String toneString = prefs.getString(RINGTONE_KEY, null);

        Uri selected = Settings.System.DEFAULT_RINGTONE_URI;
        if (!TextUtils.isEmpty(toneString)) {
            selected = Uri.parse(toneString);
        }

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selected);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_RINGTONE_URI);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        startActivityForResult(intent, DialerActivity.RINGTONE_SELECTED);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DialerActivity.RINGTONE_SELECTED && data != null) {
            Uri tone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateRingtone(tone);
        }
        else if (requestCode == DialerActivity.MESSAGE_RINGTONE_SELECTED && data != null) {
            Uri tone = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            updateMessageRingtone(tone);
        }
    }

    /*
     *** Dial assist handling
     */

    private SettingsItem mDialHelper;

    private void prepareDialHelperOption() {
        if (FindDialHelper.showDialHelperOption()) {
            mDialHelper = (SettingsItem) mDrawerView.findViewById(R.id.dial_helper);
            mDialHelper.setVisibility(View.VISIBLE);
            mDialHelper.setDescription(FindDialHelper.getCountryName(mParent));
            mDialHelper.setOnClickListener(this);
        }
    }

    private void selectDialHelper() {
        mCallbacks.onDrawerItemSelected(DrawerCallbacks.DIAL_HELPER);
    }

    private void showInputInfo(String msg) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(R.string.remaining_oca_minutes_dialog, msg, R.string.confirm_dialog, -1);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager: " + msg);
            Toast.makeText(mParent, msg, Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(infoMsg, "SilentPhoneOcaInfo");
        ft.commitAllowingStateLoss();
    }

    // This is a distinct function because of an issue with AlertDialog.Builder#set*(String) stripping links
    private void showInputInfo(int msgResId) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(R.string.remaining_oca_minutes_dialog, msgResId, R.string.confirm_dialog, -1);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager: " + getString(msgResId));
            Toast.makeText(mParent, getString(msgResId), Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(infoMsg, "SilentPhoneOcaInfo");
        ft.commitAllowingStateLoss();

        // Make possible links clickable
        fragmentManager.executePendingTransactions();
        ((TextView)infoMsg.getDialog().findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    private PopupMenu mExtendedMenu;
    private void prepareExtendedMenu() {
        // currently there are no items in extended menu so hide it
        mDrawerView.findViewById(R.id.extended_menu).setVisibility(View.GONE);
        if (mExtendedMenu == null) {
            mExtendedMenu = new PopupMenu(mParent, mDrawerView.findViewById(R.id.extended_menu));
            mDrawerView.findViewById(R.id.extended_menu).setOnClickListener(this);
            mExtendedMenu.inflate(R.menu.dial_drawer);
        }
        Menu menu = mExtendedMenu.getMenu();
        MenuItem menuItem;
        if (ConfigurationUtilities.mEnableDevDebOptions) {
            menu.setGroupVisible(R.id.dial_group_develop, true);

            menuItem = menu.findItem(R.id.dial_menu_answer_on);
            menuItem.setVisible(false /* !DialerActivity.mAutoAnswerForTesting */);

            menuItem = menu.findItem(R.id.dial_menu_answer_off);
            menuItem.setVisible(false /* DialerActivity.mAutoAnswerForTesting */);
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
    private SettingsItem mMediaRelayBox;

    private boolean mUseUnderflow;
    private SettingsItem mUnderflowBox;

    private boolean mEnableTraversal;
    private SettingsItem mEnableTraversalBox;

    private boolean mForceTraversal;
    private SettingsItem mForceTraversalBox;

    private void prepareAdvancedSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);

        // Setup media relay check box
        mMediaRelayBox = (SettingsItem) mDrawerView.findViewById(R.id.media_relay_check);
        mMediaRelayBox.setOnClickListener(this);

        // Setup underflow check box
        mUnderflowBox = (SettingsItem) mDrawerView.findViewById(R.id.underflow_check);
        mUnderflowBox.setOnClickListener(this);
        mUseUnderflow = prefs.getBoolean(ENABLE_UNDERFLOW_TONE, true);
        mUnderflowBox.setChecked(mUseUnderflow);

        // Enable FW traversal
        mEnableTraversalBox = (SettingsItem) mDrawerView.findViewById(R.id.traversal_check);
        mEnableTraversalBox.setOnClickListener(this);
        mEnableTraversal = prefs.getBoolean(ENABLE_FW_TRAVERSAL, true);
        mEnableTraversalBox.setChecked(mEnableTraversal);

        // Prepare force FW traversal
        mForceTraversalBox = (SettingsItem) mDrawerView.findViewById(R.id.force_traversal);
        mForceTraversalBox.setOnClickListener(this);
        mForceTraversal = prefs.getBoolean(FORCE_FW_TRAVERSAL, false);
        if (!mEnableTraversal)
            mForceTraversal = false;
        mForceTraversalBox.setChecked(mForceTraversal);

        mDrawerView.findViewById(R.id.settings_set_passphrase).setOnClickListener(this);
        mDrawerView.findViewById(R.id.reload_account_data).setOnClickListener(this);
        mDrawerView.findViewById(R.id.settings_switch_configuration).setOnClickListener(this);
        mDrawerView.findViewById(R.id.settings_force_axo_reregister).setOnClickListener(this);
        mDrawerView.findViewById(R.id.settings_axo_register).setOnClickListener(this);

        AxoMessaging axoMessaging = AxoMessaging.getInstance(mParent.getApplicationContext());
        boolean isRegisterButtonVisible = axoMessaging.isRegistered();
        mDrawerView.findViewById(R.id.reload_account_data)
                .setVisibility(ConfigurationUtilities.mEnableDevDebOptions
                               ? View.VISIBLE : View.GONE);
        mDrawerView.findViewById(R.id.settings_axo_register)
                .setVisibility(isRegisterButtonVisible ? View.GONE : View.VISIBLE);

        /* some entries are intended for debugging only, show them only in certain conditions */
        if (ConfigurationUtilities.mEnableDevDebOptions) {

            mDrawerView.findViewById(R.id.settings_force_axo_reregister)
                    .setVisibility(isRegisterButtonVisible ? View.VISIBLE : View.GONE);

            ((Button) mDrawerView.findViewById(R.id.settings_switch_configuration)).setText(
                    ConfigurationUtilities.mUseDevelopConfiguration
                            ? R.string.switch_to_production : R.string.switch_to_develop);
            mDrawerView.findViewById(R.id.settings_switch_configuration)
                    .setVisibility(View.GONE); // This is horribly unreliable - wipe the phone
                                                // and instead switch before even logging in
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        prefs.edit().putBoolean(ENABLE_UNDERFLOW_TONE, mUseUnderflow).apply();
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

    /* messaging related settings */
    private SettingsItem mMessageSoundsBox;
    private SettingsItem mMessageAttachmentDecryptWarnBox;
    private SettingsItem mMessageRingtone;
    private SettingsItem mMessageLight;
    private SettingsItem mMessageVibration;
    private SettingsItem mMessageTheme;
    private int mMessageLightIndex = -1;
    private int mMessageVibrateIndex = -1;
    private int mMessageThemeIndex = -1;

    private void prepareMessagingSettings() {
        // this section works only if chat option is enabled
        // set up options
        mMessageSoundsBox = (SettingsItem) mDrawerView.findViewById(R.id.messaging_sounds_check);
        mMessageSoundsBox.setChecked(MessagingPreferences.getInstance(getActivity()).getMessageSoundsEnabled());
        mMessageSoundsBox.setOnCheckedChangeListener(this);

        mMessageAttachmentDecryptWarnBox = (SettingsItem) mDrawerView.findViewById(R.id.attachment_decrypt_warn_check);
        mMessageAttachmentDecryptWarnBox.setChecked(MessagingPreferences.getInstance(getActivity()).getWarnWhenDecryptAttachment());
        mMessageAttachmentDecryptWarnBox.setOnCheckedChangeListener(this);

        /* message ring tone */
        mMessageRingtone = (SettingsItem) mDrawerView.findViewById(R.id.messaging_ringtone);
        mMessageRingtone.setOnClickListener(this);
        mMessageRingtone.setVisibility(View.VISIBLE);
        updateMessageRingtone(null);

        /* message light */
        mMessageLight = (SettingsItem) mDrawerView.findViewById(R.id.messaging_light);
        mMessageLight.setOnClickListener(this);
        updateMessageLight();

        /* message vibration pattern */
        mMessageVibration = (SettingsItem) mDrawerView.findViewById(R.id.messaging_vibration);
        mMessageVibration.setOnClickListener(this);
        updateMessageVibratePattern();

        mMessageTheme = (SettingsItem) mDrawerView.findViewById(R.id.messaging_theme);
        mMessageTheme.setOnClickListener(this);
        updateMessageTheme();

        if (ConfigurationUtilities.mEnableDevDebOptions) {
            mDrawerView.findViewById(R.id.messaging_lock_configuration).setVisibility(View.VISIBLE);
            mDrawerView.findViewById(R.id.messaging_lock_configuration).setOnClickListener(this);
        }

        if (AxoMessaging.getInstance(mParent.getApplicationContext()).isReady()) {
            mDrawerView.findViewById(R.id.settings_chat_device_management).setVisibility(View.VISIBLE);
            mDrawerView.findViewById(R.id.settings_chat_device_management).setOnClickListener(this);
        }
        else {
            mDrawerView.findViewById(R.id.settings_chat_device_management).setVisibility(View.GONE);
        }
    }

    private void toggleMessageSoundsCheckbox() {
        mMessageSoundsBox.toggle();
    }

    private void toggleMessageAttachmentDecryptWarnCheckbox() {
        mMessageAttachmentDecryptWarnBox.toggle();
    }

    public void updateMessageRingtone(Uri ringtone) {
        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        Uri currentRingTone = preferences.getMessageRingtone();

        String toneString = null;
        if (currentRingTone != null) {
            toneString = currentRingTone.toString();
        }

        if (ringtone != null) {
            toneString = ringtone.toString();
            preferences.setMessageRingtone(ringtone);
        }

        Uri tone = RingtoneUtils.getDefaultRingtoneUri(mParent, RingtoneManager.TYPE_NOTIFICATION);
        if (!TextUtils.isEmpty(toneString)) {
            tone = Uri.parse(toneString);
        }

        Ringtone ring = RingtoneManager.getRingtone(mParent, tone);
        if (ring != null) {
            mMessageRingtone.setDescription(ring.getTitle(mParent));
        }
        else {
            mMessageRingtone.setDescription(getString(R.string.no_ringtone));
        }
    }

    public void updateMessageLight() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        int index = preferences.getMessageLight();
        CharSequence[] lights = getResources().getTextArray(R.array.lights_array);
        mMessageLightIndex = index < lights.length && index >= 0
                ? index : MessagingPreferences.INDEX_LIGHT_DEFAULT;
        mMessageLight.setDescription(lights[mMessageLightIndex]);
    }

    public void updateMessageVibratePattern() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        int index = preferences.getMessageVibrate();
        CharSequence[] vibrates = getResources().getTextArray(R.array.vibrate_array);
        mMessageVibrateIndex = index < vibrates.length && index >= 0
                ? index : MessagingPreferences.INDEX_VIBRATE_DEFAULT;
        mMessageVibration.setDescription(vibrates[mMessageVibrateIndex]);
    }

    public void updateMessageTheme() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        int index = preferences.getMessageTheme();
        CharSequence[] themes = getResources().getTextArray(R.array.message_theme_array);
        mMessageThemeIndex = index < themes.length && index >= 0
                ? index : MessagingPreferences.INDEX_THEME_DARK;
        mMessageTheme.setDescription(themes[mMessageThemeIndex]);
    }

    private void selectMessageRingtone() {
        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        Uri selected = preferences.getMessageRingtone();
        if (selected == null) {
            selected = RingtoneUtils.getDefaultRingtoneUri(mParent, RingtoneManager.TYPE_NOTIFICATION);
        }

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selected);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        startActivityForResult(intent, DialerActivity.MESSAGE_RINGTONE_SELECTED);
    }

    private void showSelectionDialog(int titleId, int itemArrayId, int selectedItemIndex, int requestCode) {
        SingleChoiceDialogFragment fragment = SingleChoiceDialogFragment.getInstance(
                titleId,
                getResources().getTextArray(itemArrayId),
                selectedItemIndex);
        fragment.setTargetFragment(this, requestCode);
        fragment.show(getFragmentManager(), SingleChoiceDialogFragment.TAG_CHOICE_DIALOG);
    }

    private void showAdvancedSettings() {
        // Show dropout tone option (underflow) always
        String result = TiviPhoneService.getInfo(-1, -1, "cfg.iAudioUnderflow");
        mUseUnderflow = "1".equals(result);             // 1 -> true,
        mUnderflowBox.setChecked(mUseUnderflow);

        boolean show = ((DialerActivity)mParent).isAdvancedSettings();
        View v = mDrawerView.findViewById(R.id.media_relay_check);
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

    @Override
    public void onSingleChoiceDialogItemSelected(DialogInterface dialog, int requestCode, int index) {
        if (mParent == null) {
            return;
        }

        MessagingPreferences preferences = MessagingPreferences.getInstance(mParent);
        switch (requestCode) {
            case MESSAGE_LIGHTS_SELECTION_DIALOG:
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Setting notification light to index " + index);
                }

                preferences.setMessageLight(index);
                updateMessageLight();
                break;
            case MESSAGE_VIBRATE_SELECTION_DIALOG:
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Setting notification vibrate pattern to index " + index);
                }

                preferences.setMessageVibrate(index);
                updateMessageVibratePattern();
                break;
            case MESSAGE_THEME_SELECTION_DIALOG:
                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Setting theme to index " + index);
                }

                preferences.setMessageTheme(index);
                updateMessageTheme();
                break;
        }
    }
}
