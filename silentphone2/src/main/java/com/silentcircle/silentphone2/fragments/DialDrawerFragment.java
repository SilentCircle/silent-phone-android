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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.messaging.fragments.AlertDialogFragment;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.purchase.activities.PaymentUseStripeActivity;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.ConfigurationUtilities;
import com.silentcircle.userinfo.DownloadImageTask;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.UserInfo;

import java.util.Date;

/**
 * Fragment used for managing interactions for and presentation of a navigation drawer.
 * See the <a href="https://developer.android.com/design/patterns/navigation-drawer.html#Interaction">
 * design guidelines</a> for a complete explanation of the behaviors implemented here.
 */
public class DialDrawerFragment extends Fragment implements View.OnClickListener,
        LoadUserInfo.Listener, AlertDialogFragment.OnAlertDialogConfirmedListener {

    private static final String TAG = DialDrawerFragment.class.getSimpleName();

    private static final String AVATAR_BITMAP =
            "com.silentcircle.silentphone2.fragments.DialDrawerFragment.AVATAR_BITMAP";

    private static final int WIPE_PHONE = 0;

    /**
     * A pointer to the current callbacks instance (the Activity).
     */
    private DrawerCallbacks mCallbacks;

    private ScrollView mDrawerView;
    private AppCompatActivity mParent;

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
    private Bitmap mAvatarBitmap;
    private AsyncTask mDownloadImageTask;
    private float mImageWidth;

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
        if (!isAdded()) {
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

        setNumberName();
        setUserInfo();

        if (silent) {
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
        } else if (LoadUserInfo.checkIfUsesCredit() == LoadUserInfo.VALID) {
            // User is using credit
            showInputInfo(getString(R.string.remaining_credit,
                    userInfo.getSubscription().getBalance().getAmount(),
                    userInfo.getSubscription().getBalance().getUnit()));
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
        int MESSAGING_LOCK_SCREEN = 5;
        int DEVELOPER_SSL_DEBUG = 6;
        int DEVELOPER_AXO_DEBUG = 7;
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

        ((TextView)mDrawerView.findViewById(R.id.sc_privacy)).setMovementMethod(LinkMovementMethod.getInstance());
        ((TextView)mDrawerView.findViewById(R.id.sc_tos)).setMovementMethod(LinkMovementMethod.getInstance());

        mDrawerView.findViewById(R.id.show_oca_minutes).setOnClickListener(this);
        mDrawerView.findViewById(R.id.show_in_app_purchase).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_open_settings).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_exit_application).setOnClickListener(this);
        mDrawerView.findViewById(R.id.dial_drawer_wipe_phone).setOnClickListener(this);
        mDrawerView.findViewById(R.id.self_avatar).setOnClickListener(this);

        mDrawerView.findViewById(R.id.dial_drawer_wipe_phone).setVisibility(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ? View.VISIBLE : View.GONE);

        Resources resources = getResources();
        mImageWidth = resources.getDimension(R.dimen.dial_drawer_avatar_size);

        int colorConnecting, colorOnline, colorOffline;
        colorOnline = ContextCompat.getColor(mParent, R.color.black_green_dark_1);
        colorOffline = ContextCompat.getColor(mParent, R.color.sc_ng_text_red);
        colorConnecting = ContextCompat.getColor(mParent, R.color.sc_ng_background_3);

        mColorFilterOnline = new PorterDuffColorFilter(colorOnline, PorterDuff.Mode.MULTIPLY);
        mColorFilterOffline = new PorterDuffColorFilter(colorOffline, PorterDuff.Mode.MULTIPLY);
        mColorFilterUnknown = new PorterDuffColorFilter(colorConnecting, PorterDuff.Mode.MULTIPLY);

        // retrieve avatar image if set
        if (savedInstanceState != null) {
            mAvatarBitmap = savedInstanceState.getParcelable(AVATAR_BITMAP);
        }

        setBuildInfo();
        setNumberName();
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
        commonOnAttach(activity);
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

        setOnlineStatus();
        setAvatarImage();
    }

    @Override
    public void onResume() {
        super.onResume();

        mSilentLoadUserInfo = new LoadUserInfo(mParent.getApplicationContext(), true);
        mSilentLoadUserInfo.addUserInfoListener(this);
        /* This is unnecessary(?) as DialerActivity initiates refresh itself
        mSilentLoadUserInfo.refreshUserInfo();
        */

        setNumberName();
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

        // cancel running tasks
        if (mDownloadImageTask != null) {
            mDownloadImageTask.cancel(true);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        mDrawerView = null;
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
        outState.putParcelable(AVATAR_BITMAP, mAvatarBitmap);
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
                    mLoadUserInfo = new LoadUserInfo(mParent.getApplicationContext(), false);
                    mLoadUserInfo.addUserInfoListener(this);
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

            case R.id.dial_drawer_exit_application:
                // exit application
                ((DialerActivity) getActivity()).exit();
                break;

            case R.id.dial_drawer_wipe_phone:
                // confirm, log out from service and exit application
                wipePhone();
                break;

            case R.id.self_avatar:
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

    public void setNumberName() {
        if (mDrawerView == null)
            return;

        String phoneNumString = LoadUserInfo.getDisplayTn();

        Date expirationDate = LoadUserInfo.getExpirationDate();
        String expirationDateString = LoadUserInfo.getExpirationDateString();

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
            View numberLayout = mDrawerView.findViewById(R.id.dial_drawer_number);
            numberLayout.setVisibility(View.VISIBLE);

            ((TextView) mDrawerView.findViewById(R.id.dial_drawer_number_data)).setText(phoneNumString);
        }

        // Always show "Account Info" button
        mDrawerView.findViewById(R.id.show_oca_minutes).setVisibility(View.VISIBLE);

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

    /*
     * The following part contains private methods which handle 'settings'
     *
     */
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
        int registerStatus = TiviPhoneService.getPhoneState();
        switch (registerStatus) {
            case 1:             // connecting
                onlineStatus.setColorFilter(mColorFilterUnknown);
                break;
            case 2:             // online
                onlineStatus.setColorFilter(mColorFilterOnline);
                break;
            default:            // offline
                onlineStatus.setColorFilter(mColorFilterOffline);
        }
    }

    private void setAvatarImage() {
        if (mAvatarBitmap != null) {
            ((ImageView) mDrawerView.findViewById(R.id.self_avatar)).setImageBitmap(mAvatarBitmap);
        }
        else {
            if (mDownloadImageTask != null) {
                mDownloadImageTask.cancel(false);
            }
            mDownloadImageTask = AsyncUtils.execute(new DownloadImageTask(mParent) {
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    if (ConfigurationUtilities.mTrace) {
                        Log.d(TAG, "Downloading of image finished, bitmap " + bitmap
                                + ", isAdded: " + isAdded());
                    }
                    if (mDrawerView != null && bitmap != null) {
                        bitmap = ViewUtil.getCircularBitmap(bitmap);
                        int height = (int) (bitmap.getHeight() * (mImageWidth / bitmap.getWidth()));
                        mAvatarBitmap = Bitmap.createScaledBitmap(bitmap, (int) mImageWidth, height, true);
                        ((ImageView) mDrawerView.findViewById(R.id.self_avatar)).setImageBitmap(mAvatarBitmap);
                        if (mDownloadImageTask == this) {
                            mDownloadImageTask = null;
                        }
                    }
                }
            }, LoadUserInfo.getAvatarUrl());
        }
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
        dialogFragment.show(getFragmentManager(), AlertDialogFragment.TAG_ALERT_DIALOG);
    }

}
