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

package com.silentcircle.silentphone2.fragments;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.AlertDialogFragment;
import com.silentcircle.messaging.providers.PictureProvider;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.views.AvatarActionsDialog;
import com.silentcircle.purchase.activities.PaymentUseStripeActivity;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifier;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.silentphone2.util.SPAPreferences;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.ErrorInfo;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UploadImageTask;
import com.silentcircle.userinfo.UserInfo;
import com.silentcircle.userinfo.activities.AvatarCropActivity;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import static android.widget.Toast.makeText;
import static com.silentcircle.silentphone2.R.id.dial_drawer_help_and_support;
import static com.silentcircle.silentphone2.R.id.dial_drawer_terms_of_service;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class DialDrawerFragment extends Fragment implements View.OnClickListener,
        LoadUserInfo.Listener, AlertDialogFragment.OnAlertDialogConfirmedListener,
        ZinaMessaging.AxoMessagingStateCallback {

    private static final String TAG = DialDrawerFragment.class.getSimpleName();

    private static final String AVATAR_BITMAP =
            "com.silentcircle.silentphone2.fragments.DialDrawerFragment.AVATAR_BITMAP";
    private static final String AVATAR_IMAGE_CAPTURE_URI =
            "com.silentcircle.silentphone2.fragments.DialDrawerFragment.AVATAR_IMAGE_CAPTURE_URI";

    private static final int WIPE_PHONE = 0;

    private static final int GALLERY_IMAGE_FOR_AVATAR = 10098;
    private static final int CAPTURED_IMAGE_FOR_AVATAR = 10099;
    private static final int CROPPED_IMAGE_FOR_AVATAR = 10199;

    public static final int PERMISSION_CAMERA = 1;

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private DrawerCallbacks mCallbacks;

    private ScrollView mDrawerView;
    private AppCompatActivity mParent;
    private View mUserDetailsView;

    /* Used to show remaining OCA minutes */
    private LoadUserInfo mLoadUserInfo;
    /*
     * Used for general information refresh in fragment.
     * As DialerActivity is not calling onUserInfo, but fragment has to show the user name
     * information, it has to listen to user info availability itself.
     * But this listener is only needed when there is a slow network and drawer has been opened
     * before user information has become available).
     */
    private LoadUserInfo mSilentLoadUserInfo;

    /*
     * Colour filters to colour status indication
     */
    private ColorFilter mColorFilterOnline;
    private ColorFilter mColorFilterOffline;
    private ColorFilter mColorFilterUnknown;

    /* Self avatar */
    private float mImageWidth;
    private Uri mPendingImageCaptureUri;
    private ContactPhotoManagerNew mContactPhotoManager;
    private String mManageAccounts;

    /* Progress dialog when retrieving account info */
    private ProgressDialog mAccountProgressDialog;

    /* Progress dialog when uploading avatar */
    private ProgressDialog mAvatarUploadProgressDialog;

    /**
     * Listener that receives the data if OCA minutes are available from LoadUserInfo.
     *
     * @param userInfo A {@link UserInfo} object
     * @param errorInfo   If not {@code null} then an error occurred, minutes data invalid,
     *                    this shows the error reason
     * @param silent      Should the user see anything?
     */
    public void onUserInfo(UserInfo userInfo, String errorInfo, boolean silent) {
        if (ConfigurationUtilities.mTrace) Log.d(TAG, "onUserInfo");
        // side drawer could open before LoadUserInfo task completed.
        // onUserInfo callback will provide correct organization info
        /*
         * Temporarily disable in-app purchase UI.
         *
         *
        if(LoadUserInfo.checkIfHasOrganization() == LoadUserInfo.INVALID){
            mDrawerView.findViewById(R.id.show_in_app_purchase).setVisibility(View.VISIBLE);
        }
        else{
            mDrawerView.findViewById(R.id.show_in_app_purchase).setVisibility(View.GONE);
        }
         */
        /**
         * Has to still be attached to be able to call {@link Fragment#getResources()}
         */
        if (mAccountProgressDialog != null) {
            mAccountProgressDialog.dismiss();
            mAccountProgressDialog = null;
        }

        if (!isAdded()) {
            return;
        }

        if (ErrorInfo.API_KEY_INVALID.equals(errorInfo)) {
            return;
        }

        if (errorInfo != null) {
            if(!silent) {
                showInputInfo(errorInfo);
            }
            return;
        }

        if (userInfo == null) {
            return;
        }

        setNumberNameEtc();
        setUserInfo();

        if (silent) {
            return;
        }

        if (LoadUserInfo.checkIfExpired() == LoadUserInfo.VALID) {
            showInputInfo(new SpannableString(getString(R.string.expired_account_info) + mManageAccounts),
                    R.string.remaining_oca_minutes_dialog);
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
            showInputInfo(new SpannableString(msg + mManageAccounts),
                    R.string.remaining_oca_minutes_dialog);
        } else if (LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID) {
            // User is using credit
            showInputInfo(new SpannableString(getString(R.string.remaining_credit,
                    userInfo.getSubscription().getBalance().getAmount(),
                    userInfo.getSubscription().getBalance().getUnit())
                    + mManageAccounts),
                    R.string.remaining_oca_minutes_dialog);
        }
    }

    /**
     * Callbacks interface that all activities using this fragment must implement.
     */
    public interface DrawerCallbacks {
        int OPENED = 1;
        int CLOSED = 2;
        int MOVING = 3;

        int RINGTONE = 1;
        int DIAL_HELPER = 2;
        int KEY_STORE = 3;
        int RE_PROVISION = 4;
        int DEVELOPER_SSL_DEBUG = 6;
        int DEVELOPER_ZINA_DEBUG = 7;
        int SETTINGS = 8;

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
        mContactPhotoManager =
                ContactPhotoManagerNew.getInstance(SilentPhoneApplication.getAppContext());
        ZinaMessaging.getInstance().addStateChangeListener(this);
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
        mDrawerView = (ScrollView)inflater.inflate(R.layout.dialer_drawer, container, false);

/*
        ((TextView)mDrawerView.findViewById(R.id.dial_drawer_privacy_statement)).setMovementMethod(
                new ViewUtil.MovementCheck(mParent, mDrawerView, R.string.toast_no_browser_found));
        ((TextView)mDrawerView.findViewById(R.id.dial_drawer_terms_of_service)).setMovementMethod(
                new ViewUtil.MovementCheck(mParent, mDrawerView, R.string.toast_no_browser_found));
*/

        boolean developer = false;
        if(mParent != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
            developer = prefs.getBoolean(SettingsFragment.DEVELOPER, false);
        }
        setDeveloperMode(developer);
        mDrawerView.findViewById(R.id.show_oca_minutes).setOnClickListener(this);
        mDrawerView.findViewById(R.id.show_in_app_purchase).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_open_settings).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_exit_application).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_wipe_phone).setOnClickListener(this);
        mDrawerView.findViewById(R.id.self_avatar).setOnClickListener(this);
        mDrawerView.findViewById(R.id.self_avatar_expand_details).setOnClickListener(this);
        mDrawerView.findViewById(dial_drawer_help_and_support).setOnClickListener(this);
        mDrawerView.findViewById(dial_drawer_terms_of_service).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_privacy_statement).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_manage_account).setOnClickListener(this);
        mUserDetailsView = mDrawerView.findViewById(R.id.dialer_drawer_user_info);

        mDrawerView.findViewById(R.id.dial_drawer_wipe_phone).setVisibility(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? View.VISIBLE : View.GONE);

        Resources resources = getResources();
        mImageWidth = resources.getDimension(R.dimen.dial_drawer_avatar_size);

        mManageAccounts = getString(ConfigurationUtilities.mUseDevelopConfiguration
                ? R.string.manage_account_dev
                : R.string.manage_account);

        int colorConnecting, colorOnline, colorOffline;
        colorOnline = ContextCompat.getColor(mParent, R.color.black_green_dark_1);
        colorOffline = ContextCompat.getColor(mParent, R.color.sc_ng_text_red);
        colorConnecting = ContextCompat.getColor(mParent, R.color.sc_ng_background_3);

        mColorFilterOnline = new PorterDuffColorFilter(colorOnline, PorterDuff.Mode.MULTIPLY);
        mColorFilterOffline = new PorterDuffColorFilter(colorOffline, PorterDuff.Mode.MULTIPLY);
        mColorFilterUnknown = new PorterDuffColorFilter(colorConnecting, PorterDuff.Mode.MULTIPLY);

        // retrieve avatar image if set
        if (savedInstanceState != null) {
            mPendingImageCaptureUri = savedInstanceState.getParcelable(AVATAR_IMAGE_CAPTURE_URI);
        }

        setBuildInfo();
        setNumberNameEtc();
        setUserInfo();

        return mDrawerView;
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

    public void setDeveloperMode(boolean developer) {
        mDrawerView
          .findViewById(R.id.dial_drawer_exit_application)
          .setVisibility(developer ? View.VISIBLE : View.GONE);
        mDrawerView
          .findViewById(R.id.dial_drawer_action_rule)
          .setVisibility(developer ? View.VISIBLE : View.GONE);
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        if (registered) {
            setAvatarImage();
        }
    }


    private void commonOnAttach(Activity activity) {
        try {
            mParent = (AppCompatActivity) activity;
            mCallbacks = (DrawerCallbacks) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must implement DrawerCallbacks.");
        }
    }

    public void setUserInfo() {
        if (mDrawerView == null) {
            return;
        }

        String displayName = LoadUserInfo.getDisplayName();
        String alias = LoadUserInfo.getDisplayAlias();

        TextView textDisplayName = (TextView) mDrawerView.findViewById(R.id.self_avatar_display_name);
        TextView textAliasName = (TextView) mDrawerView.findViewById(R.id.self_avatar_alias);
        textAliasName.setVisibility(View.GONE);

        textDisplayName.setText(TextUtils.isEmpty(displayName) ? alias : displayName);

        if (!TextUtils.isEmpty(alias) && !TextUtils.isEmpty(displayName) && !alias.equals(displayName)) {
            textAliasName.setVisibility(View.VISIBLE);
            textAliasName.setText(alias);
        }

        setDrStatus();
        setOnlineStatus();
        setAvatarImage();
    }

    private void setDrStatus() {
        if (mDrawerView == null || getActivity() == null) {
            return;
        }

        boolean isDataRetained =
                        (LoadUserInfo.isLrcm() | LoadUserInfo.isLrcp() | LoadUserInfo.isLrmp()
                        | LoadUserInfo.isLrmm() | LoadUserInfo.isLrap());

        Log.d(TAG, "setDrStatus: isDataRetained " + isDataRetained);
        int visibility = isDataRetained ? View.VISIBLE : View.GONE;
        View dataRetentionStatus = mDrawerView.findViewById(R.id.data_retention_status);
        View dataRetentionInfoLayout = mDrawerView.findViewById(R.id.dial_drawer_data_retention);
        dataRetentionStatus.setVisibility(visibility);

        if (isDataRetained) {
            DRUtils.DRMessageHelper messageHelper = new DRUtils.DRMessageHelper(getActivity());
            final String message = messageHelper.getLocalRetentionInformation();

            dataRetentionInfoLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showInputInfo(message, R.string.dialog_title_data_retention);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mSilentLoadUserInfo = new LoadUserInfo(true);
        mSilentLoadUserInfo.addUserInfoListener(this);
        /* This is unnecessary(?) as DialerActivity initiates refresh itself
        mSilentLoadUserInfo.refreshUserInfo();
        */

        setNumberNameEtc();
        setUserInfo();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            setUserInfo();
        }
    }

    @Override
    public void onPause() {
        // remove fragment from user info listeners
        mSilentLoadUserInfo.removeUserInfoListener(this);
        mSilentLoadUserInfo = null;
        // fragment already removed as listener, just nullify
        mLoadUserInfo = null;

        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mDrawerView = null;
        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        axoMessaging.removeStateChangeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(AVATAR_IMAGE_CAPTURE_URI, mPendingImageCaptureUri);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.show_oca_minutes:
                if (mLoadUserInfo == null) {
                    mLoadUserInfo = new LoadUserInfo(false);
                    mLoadUserInfo.addUserInfoListener(this);
                }
                if (mAccountProgressDialog == null) {
                    mAccountProgressDialog = new ProgressDialog(mParent);
                    mAccountProgressDialog.setMessage(getText(R.string.remaining_oca_minutes_loading_dialog));
                    mAccountProgressDialog.show();
                }
                mLoadUserInfo.loadOcaMinutesInfo();
                break;

            case R.id.show_in_app_purchase:
                showInAppPurchase();
                break;

            case R.id.dial_drawer_open_settings:
                // open settings view
                mCallbacks.onDrawerItemSelected(DrawerCallbacks.SETTINGS);
                break;

            case R.id.dial_drawer_help_and_support:
                showUrlInBrowser("https://www.silentcircle.com/support/");
                break;

            case R.id.dial_drawer_terms_of_service:
                showUrlInBrowser("https://accounts.silentcircle.com/terms/");
                break;

            case R.id.dial_drawer_privacy_statement:
                showUrlInBrowser("https://accounts.silentcircle.com/privacy-policy/");
                break;

            case R.id.dial_drawer_manage_account:
                showUrlInBrowser(ConfigurationUtilities.mUseDevelopConfiguration
                        ? "https://accounts-dev.silentcircle.com/"
                        : "https://accounts.silentcircle.com/");
                break;

            case R.id.dial_drawer_exit_application:
                // exit application
                ((DialerActivity) getActivity()).exitAndDelay();
                break;

            case R.id.dial_drawer_wipe_phone:
                // confirm, log out from service and exit application
                wipePhone();
                break;

            case R.id.self_avatar:
                // allow to choose image action
                if (!Utilities.isNetworkConnected(getActivity())) {
                    showInputInfo(getString(R.string.connected_to_network), R.string.no_internet);
                    break;
                }
                showAvatarActionDialog(mParent);
                break;

            case R.id.self_avatar_expand_details:
                toggleAccountDetailsView();
                break;

            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                break;
            }
        }
    }

    @Override
    public void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle, boolean saveChoice) {
        if (requestCode == WIPE_PHONE) {
            ((DialerActivity) getActivity()).wipePhone();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == GALLERY_IMAGE_FOR_AVATAR
                    || requestCode == CAPTURED_IMAGE_FOR_AVATAR) {
                Uri selectedImageUri = mPendingImageCaptureUri;
                if (requestCode == GALLERY_IMAGE_FOR_AVATAR) {
                    selectedImageUri = data.getData();
                }

                try {
                    File imagePath = new File(mParent.getFilesDir(), "captured/image");
                    if (!imagePath.exists()) imagePath.mkdirs();
                    File imageFile = new File(imagePath, "avatar-" + PictureProvider.JPG_FILE_NAME);
                    imageFile.createNewFile();
                    Uri uri = FileProvider.getUriForFile(mParent, BuildConfig.AUTHORITY_BASE + ".files",
                            imageFile);

                    Intent cropImageIntent = new Intent(mParent, AvatarCropActivity.class);
                    cropImageIntent.setData(selectedImageUri);
                    cropImageIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                    startActivityForResult(cropImageIntent, CROPPED_IMAGE_FOR_AVATAR);
                } catch (IOException e) {
                    // Failed to set avatar
                }
            } else if (requestCode == CROPPED_IMAGE_FOR_AVATAR) {
                Uri selectedImageUri = data.getData();
                handleAvatarImageSelection(selectedImageUri);
            }
        }
    }

    private void handleAvatarCapturePermission() {
        if (ContextCompat.checkSelfPermission(mParent,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            FragmentCompat.requestPermissions(this, new String[]{ Manifest.permission.CAMERA }, PERMISSION_CAMERA);
        } else {
            AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
            Intent intent = createCaptureImageIntent();
            startActivityForResult(intent, CAPTURED_IMAGE_FOR_AVATAR);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Occurs on rotation of permission dialog
        if (permissions.length == 0) {
            return;
        }

        switch (requestCode) {
            case PERMISSION_CAMERA: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
                    Intent intent = createCaptureImageIntent();
                    startActivityForResult(intent, CAPTURED_IMAGE_FOR_AVATAR);
                }
                break;
            }
            default:
                break;
        }
    }

    public void setNumberNameEtc() {
        if (mDrawerView == null)
            return;

        String phoneNumString = LoadUserInfo.getDisplayTn();

        Date expirationDate = LoadUserInfo.getExpirationDate();
        String expirationDateString = LoadUserInfo.getExpirationDateString();
        String displayOrg = LoadUserInfo.getDisplayOrg();
        String displayPlan = LoadUserInfo.getDisplayPlan();
        String baseMinutes = LoadUserInfo.getBaseMinutes();
        String remainingMinutes = LoadUserInfo.getRemainingMinutes();
        String remainingCredit = LoadUserInfo.getRemainingCredit();
        String creditUnit = !TextUtils.isEmpty(LoadUserInfo.getCreditUnit()) ? LoadUserInfo.getCreditUnit() : "USD";

        if (ConfigurationUtilities.mTrace) {
            Log.d(TAG, "setNumberName expirationDate: " + expirationDate + " (" + expirationDateString + ")");
        }

        if (LoadUserInfo.checkExpirationDateValid(expirationDateString)) {
            if (expirationDate != null && expirationDate.before(LoadUserInfo.VALID_FOR_LIFETIME)) {
                View expirationLayout = mDrawerView.findViewById(R.id.dial_drawer_valid);
                expirationLayout.setVisibility(View.VISIBLE);
                ((TextView) mDrawerView.findViewById(R.id.dial_drawer_valid_data)).setText(expirationDateString);
            }
        }

        if (!TextUtils.isEmpty(phoneNumString)) {
            phoneNumString = Utilities.formatNumber(phoneNumString);

            View numberLayout = mDrawerView.findViewById(R.id.dial_drawer_number);
            numberLayout.setVisibility(View.VISIBLE);

            ((TextView) mDrawerView.findViewById(R.id.dial_drawer_number_data)).setText(phoneNumString);
        }

/*
        // Always show "Account Info" button
        mDrawerView.findViewById(R.id.show_oca_minutes).setVisibility(View.VISIBLE);
*/

        if (!TextUtils.isEmpty(displayOrg)) {
            View orgLayout = mDrawerView.findViewById(R.id.dial_drawer_org);
            orgLayout.setVisibility(View.VISIBLE);

            ((TextView) mDrawerView.findViewById(R.id.dial_drawer_org_data)).setText(displayOrg);
        }

        if (!TextUtils.isEmpty(displayPlan)) {
            View planLayout = mDrawerView.findViewById(R.id.dial_drawer_plan);
            planLayout.setVisibility(View.VISIBLE);

            ((TextView) mDrawerView.findViewById(R.id.dial_drawer_plan_data)).setText(displayPlan);
        }

        if (LoadUserInfo.checkIfUsesMinutes() == LoadUserInfo.VALID) {
            if (!TextUtils.isEmpty(baseMinutes) || !TextUtils.isEmpty(remainingMinutes)) {
                View minutesLayout = mDrawerView.findViewById(R.id.dial_drawer_minutes);
                minutesLayout.setVisibility(View.VISIBLE);

                ((TextView) mDrawerView.findViewById(R.id.dial_drawer_minutes_data)).setText(remainingMinutes + "/" + baseMinutes);
            }
        } else if (LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID) {
            if (!TextUtils.isEmpty(remainingCredit)) {
                View creditLayout = mDrawerView.findViewById(R.id.dial_drawer_credit);
                creditLayout.setVisibility(View.VISIBLE);

                NumberFormat format = NumberFormat.getInstance();
                format.setMinimumFractionDigits(2);
                format.setMaximumFractionDigits(2);
                Currency currency = Currency.getInstance(creditUnit); // Default is a USD format (see above)
                String symbol = currency.getSymbol();
                format.setCurrency(currency);

                // Either show symbol (if we have it) + credit or credit + unit ($5.00 or 5.00 USD)
                String creditString = (!TextUtils.isEmpty(symbol) ? symbol : "") +
                        format.format(Double.valueOf(remainingCredit)) +
                        (TextUtils.isEmpty(symbol) ? " " + creditUnit : "");

                ((TextView) mDrawerView.findViewById(R.id.dial_drawer_credit_data)).setText(creditString);
            }
        }

        setAccountDetailsViewVisibility(SPAPreferences.getInstance(mParent).getAccountDetailsExpanded()
                != SPAPreferences.INDEX_ACCOUNT_DETAILS_HIDDEN, /* animate */ false);

        // Enterprise users who have org name cannot have in-app purchase.
        /*
         * Temporarily disable in-app purchase UI.
         *
         *
        if(LoadUserInfo.checkIfHasOrganization() == LoadUserInfo.INVALID){

            mDrawerView.findViewById(R.id.show_in_app_purchase).setVisibility(View.VISIBLE);
        }
        else{
            mDrawerView.findViewById(R.id.show_in_app_purchase).setVisibility(View.GONE);
        }
         */
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

        StringBuilder detailInfo = new StringBuilder();
        detailInfo.append(getString(R.string.active_configuration,
                (ConfigurationUtilities.mUseDevelopConfiguration
                        ? getString(R.string.develop) : getString(R.string.production))));
        detailInfo.append("\n").append(getString(R.string.device_information)).append("\n")
                .append(Build.MANUFACTURER).append(", ")
                .append(Build.BRAND).append(", ")
                .append(Build.MODEL).append(", ")
                .append(Build.DEVICE);
        detailInfo.append("\n").append(getString(R.string.screen_density, metrics.densityDpi + " (" + size + ", " + swSetting + ")"));
        if (DialerActivity.mAutoAnswerForTesting) {
            detailInfo.append("\n").append(getString(R.string.auto_answered, DialerActivity.mAutoAnsweredTesting));
        }
        if (BuildConfig.DEBUG) {
            detailInfo.append("\n").append(getString(R.string.build_time, BuildConfig.SPA_BUILD_DATE));
        }
        return detailInfo.toString();
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
                            if (mDrawerView != null) {
                                mDrawerView.fullScroll(View.FOCUS_DOWN);
                            }
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

    /*
     * The following part contains private methods which handle 'settings'
     *
     */
    private void showInputInfo(String msg) {
        showInputInfo(msg, R.string.remaining_oca_minutes_dialog);
    }

    private void showInputInfo(Spanned msg, int titleResId) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msg, R.string.dialog_button_ok, -1);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager: " + msg);
            makeText(mParent, msg.toString(), Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(infoMsg, "SilentPhoneOcaInfo");
        ft.commitAllowingStateLoss();
    }

    private void showInputInfo(String msg, int titleResId) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(titleResId, msg, R.string.dialog_button_ok, -1);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager: " + msg);
            makeText(mParent, msg, Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(infoMsg, "SilentPhoneOcaInfo");
        ft.commitAllowingStateLoss();
    }

    // This is a distinct function because of an issue with AlertDialog.Builder#set*(String) stripping links
    private void showInputInfo(int msgResId) {
        InfoMsgDialogFragment infoMsg = InfoMsgDialogFragment.newInstance(R.string.remaining_oca_minutes_dialog, msgResId, R.string.dialog_button_ok, -1);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager == null) {
            Log.e(TAG, "Could not get Fragment manager: " + getString(msgResId));
            makeText(mParent, getString(msgResId), Toast.LENGTH_LONG).show();
            return;
        }

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.add(infoMsg, "SilentPhoneOcaInfo");
        ft.commitAllowingStateLoss();
    }

    // In-app purchase
    private void showInAppPurchase(){
        Intent intent = new Intent(getActivity(), PaymentUseStripeActivity.class);
        startActivity(intent);
    }

    @SuppressWarnings("deprecation")
    public void setOnlineStatus() {
        if (mDrawerView == null) {
            return;
        }

        ImageView onlineStatus = (ImageView) mDrawerView.findViewById(R.id.self_avatar_online_status);
        TextView onlineStatusText = (TextView) mDrawerView.findViewById(R.id.self_avatar_online_status_text);
        int registerStatus = TiviPhoneService.getPhoneState();
        ColorFilter filter = mColorFilterUnknown;
        int text = R.string.connecting;
        switch (registerStatus) {
            case 1:             // connecting
                filter = mColorFilterUnknown;
                text = R.string.connecting;
                break;
            case 2:             // online
                filter = mColorFilterOnline;
                text = R.string.online;
                break;
            default:            // offline
                filter = mColorFilterOffline;
                text = R.string.offline;
        }
        onlineStatusText.setText(text);
        onlineStatus.setContentDescription(getString(text));
        onlineStatus.setColorFilter(filter);
    }

    private void setAvatarImage() {
        if (mDrawerView == null) {
            return;
        }

        if (!ZinaMessaging.getInstance().isRegistered()) {
            return;
        }
        String avatarUrl = LoadUserInfo.getAvatarUrl();
        AvatarUtils.setPhoto(mContactPhotoManager,
                ((ImageView) mDrawerView.findViewById(R.id.self_avatar)),
                AvatarUtils.getAvatarProviderUri(avatarUrl, LoadUserInfo.getUuid(), (int) mImageWidth,
                        R.drawable.ic_avatar_placeholder_circular),
                ContactPhotoManagerNew.TYPE_DEFAULT);
    }

    private void wipePhone() {
        AlertDialogFragment dialogFragment = AlertDialogFragment.getInstance(
                R.string.are_you_sure,
                R.string.dialog_message_wipe_phone,
                R.string.dialog_button_cancel,
                R.string.wipe,
                null,
                false);
        dialogFragment.setTargetFragment(this, WIPE_PHONE);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                .add(dialogFragment, AlertDialogFragment.TAG_ALERT_DIALOG)
                .commitAllowingStateLoss();
        }
    }

    private void showAvatarActionDialog(final Context context) {
        final AvatarActionsDialog dialog = new AvatarActionsDialog(context);
        dialog.setOnCallOrConversationSelectedListener(
                new AvatarActionsDialog.OnAvatarActionSelectedListener() {

                    @Override
                    public void onGallerySelected() {
                        AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
                        Intent intent = new Intent(Intent.ACTION_PICK,
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(intent, GALLERY_IMAGE_FOR_AVATAR);
                    }

                    @Override
                    public void onCaptureImageSelected() {
                        handleAvatarCapturePermission();
                    }

                    @Override
                    public void onDeleteAvatarSelected() {
                        doAvatarAction("", "Failed to delete image on server");
                        // clear avatar immediately
                        if (mDrawerView != null) {
                            ((ImageView) mDrawerView.findViewById(R.id.self_avatar))
                                    .setImageResource(R.drawable.ic_avatar_placeholder_circular);
                        }
                    }

                });
        dialog.setDeleteButtonEnabled(!TextUtils.isEmpty(LoadUserInfo.getAvatarUrl()));
        dialog.setCameraButtonEnabled(Utilities.hasCamera());
        dialog.show();
    }

    private void toggleAccountDetailsView() {
        if (mDrawerView == null || getActivity() == null) {
            return;
        }

        if (mUserDetailsView != null) {
            boolean expanded = mUserDetailsView.getVisibility() != View.VISIBLE;
            setAccountDetailsViewVisibility(expanded, /* animate */ true);
            SPAPreferences.getInstance(mParent).setAccountDetailsExpanded(expanded
                    ? SPAPreferences.INDEX_ACCOUNT_DETAILS_VISIBLE
                    : SPAPreferences.INDEX_ACCOUNT_DETAILS_HIDDEN);
        }
    }

    private void setAccountDetailsViewVisibility(boolean visible, boolean animate) {
        if (mDrawerView == null || getActivity() == null) {
            return;
        }

        if (mUserDetailsView != null) {
            int visibility = visible ? View.VISIBLE : View.GONE;
            mUserDetailsView.setVisibility(visibility);
            int contentDescriptionId = visible
                    ? R.string.navigation_drawer_collapse_details_voiceover
                    : R.string.navigation_drawer_expand_details_voiceover;
            ImageView buttonToggleAccountDetails =
                    (ImageView) mDrawerView.findViewById(R.id.self_avatar_expand_details);
            AnimUtils.setViewRotation(buttonToggleAccountDetails, animate ? 200 : 0, visible ? -180 : 0);
            buttonToggleAccountDetails.setContentDescription(getString(contentDescriptionId));
        }
    }

    private void handleAvatarImageSelection(@Nullable Uri selectedImageUri) {
        String base64 = null;
        if (selectedImageUri != null) {
            base64 = AttachmentUtils.getFileAsBase64String(mParent, selectedImageUri);
        }
        if (!TextUtils.isEmpty(base64)) {
            doAvatarAction(base64, "Failed to upload image to server");
            ConversationUtils.setConversationAvatar(mParent, LoadUserInfo.getUuid(),
                    LoadUserInfo.getAvatarUrl(), base64);
            mContactPhotoManager.refreshCache();
            setAvatarImage();
        }
    }

    private void doAvatarAction(final String base64Image, final String failureMessage) {
        AsyncTask task = new UploadImageTask(mParent) {
            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                if (mAvatarUploadProgressDialog != null) {
                    mAvatarUploadProgressDialog.dismiss();
                    mAvatarUploadProgressDialog = null;
                }

                if (ConfigurationUtilities.mTrace) {
                    Log.d(TAG, "Avatar image action result " + result);
                }
                if (result != HttpsURLConnection.HTTP_OK) {
                    makeText(mParent, failureMessage, Toast.LENGTH_LONG).show();
                }
            }
        };
        AsyncUtils.execute(task, base64Image);

        if (mAvatarUploadProgressDialog == null) {
            mAvatarUploadProgressDialog = new ProgressDialog(mParent);
            mAvatarUploadProgressDialog.setMessage(getText(!TextUtils.isEmpty(base64Image)
                    ? R.string.avatar_upload_loading_dialog
                    : R.string.avatar_delete_loading_dialog));
            mAvatarUploadProgressDialog.show();
        }
    }

    private Intent createCaptureImageIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //FIXME: Hotfix
        File imagePath = new File(mParent.getFilesDir(), "captured/image");
        if (!imagePath.exists()) imagePath.mkdirs();
        Uri uri = FileProvider.getUriForFile(mParent, BuildConfig.AUTHORITY_BASE + ".files",
                new File(imagePath, PictureProvider.JPG_FILE_NAME));
        mPendingImageCaptureUri = uri;

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(null, uri));
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 10 * 1024 * 1024L);
        intent.putExtra("return-data", true);

        return intent;
    }

    private void showUrlInBrowser(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(mDrawerView, R.string.toast_no_browser_found, Snackbar.LENGTH_LONG).show();
        }
    }
}
