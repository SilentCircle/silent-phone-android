/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.silentcircle.silentphone2.list;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.list.PhoneNumberListAdapter;
import com.silentcircle.contacts.list.PhoneNumberPickerFragment;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.messaging.services.AxoMessaging;
import com.silentcircle.messaging.task.ValidationState;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.views.CallOrConversationDialog;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ContactAdder;
import com.silentcircle.silentphone2.util.Utilities;

public class SearchFragment extends PhoneNumberPickerFragment implements AxoMessaging.AxoMessagingStateCallback {

    @SuppressWarnings("unused")
    private static final String TAG = "SearchFragment";

    private OnListFragmentScrolledListener mActivityScrollListener;

    /*
     * Stores the untouched user-entered string that is used to populate the add to contacts
     * intent.
     */
    private String mAddToContactNumber;
    private int mActionBarHeight;
//    private int mShadowHeight;
    private int mPaddingTop;
    private int mShowDialpadDuration;
    private int mHideDialpadDuration;

    private boolean mStartConversationFlag = false;
    private boolean mAxoRegistered = false;

    private HostInterface mActivity;

    public interface HostInterface {
        boolean isActionBarShowing();
        boolean isDialpadShown();
        @SuppressWarnings("unused")
        int getActionBarHideOffset();
        @SuppressWarnings("unused")
        int getActionBarHeight();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getActivity().getApplicationContext());
        mAxoRegistered = axoMessaging.isRegistered();
        axoMessaging.addStateChangeListener(this);
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

        setQuickContactEnabled(true);
        setAdjustSelectionBoundsEnabled(false);
        setDarkTheme(false);
        setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        setUseCallableUri(true);
        setIncludeProfile(false);

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Don't show the directory header in case of Smartdial search
        if (this instanceof SmartDialSearchFragment && isSearchMode()) {
            getAdapter().configureDefaultPartition(false, false);
        }

        mActivity = (HostInterface) getActivity();

        final Resources res = getResources();
        mActionBarHeight = (int)res.getDimension((R.dimen.dialpad_digits_line_height));
//        mShadowHeight  = res.getDrawable(R.drawable.search_shadow).getIntrinsicHeight();
        mPaddingTop = res.getDimensionPixelSize(R.dimen.search_list_padding_top);
        mShowDialpadDuration = res.getInteger(R.integer.dialpad_slide_in_duration);
        mHideDialpadDuration = res.getInteger(R.integer.dialpad_slide_out_duration);

        final ListView listView = getListView();

//        listView.setBackgroundColor(res.getColor(R.color.background_dialer_results));
        listView.setClipToPadding(false);
        setVisibleScrollbarEnabled(false);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
            }
        });

        updatePosition(false /* animate */);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AxoMessaging axoMessaging = AxoMessaging.getInstance(getActivity().getApplicationContext());
        axoMessaging.removeStateChangeListener(this);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtil.addBottomPaddingToListViewForFab(getListView(), getResources());
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        if (!(this instanceof SmartDialSearchFragment))
            return;
        // This hides the "All contacts with phone numbers" header in the search fragment
        final ScContactEntryListAdapter adapter = getAdapter();
        if (adapter != null) {
            adapter.configureDefaultPartition(false, false);
        }
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    public void setStartConversationFlag(boolean startConversationFlag) {
        mStartConversationFlag = startConversationFlag;
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        setConversationShortcutEnabled();
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;
        ScDirectoryLoader.clearCachedData();

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                startCallOrConversation(position, id, adapter.getPhoneNumber(position));
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(getQueryString());
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                final String number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                final Intent intent = ContactsUtils.getAddNumberToContactIntent(number);  // adds directly to native contacts
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
//            case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
//                listener = getOnPhoneNumberPickerListener();
//                if (listener != null) {
//                    listener.onCallNumberDirectly(getQueryString(), true /* isVideoCall */);
//                }
//                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION:
                validateUser(getQueryString());
                break;
        }
    }

    @Override
    public void axoRegistrationStateChange(boolean registered) {
        mAxoRegistered = registered;
        /* depending on registration state either show or hide conversation button */
        setConversationShortcutEnabled();
    }

    /**
     * Updates the position and padding of the search fragment, depending on whether the dialpad is
     * shown. This can be optionally animated.
     * @param animate
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void updatePosition(boolean animate) {
        // Could happen if fragment is being not visible/being destroyed 
        if (getView() == null || mActivity == null)
            return;

        // Use negative shadow height instead of 0 to account for the 9-patch's shadow.
        int startTranslationValue = 0; // mActivity.isDialpadShown() ? /*mActionBarHeight*/ - mShadowHeight: -mShadowHeight;
        int endTranslationValue = 0;
        // Prevents ListView from being translated down after a rotation when the ActionBar is up.
        if (animate || mActivity.isActionBarShowing()) {
            endTranslationValue = mActivity.isDialpadShown() ? mActionBarHeight : 0;
        }
        if (animate) {
            Interpolator interpolator =
                    mActivity.isDialpadShown() ? AnimUtils.EASE_IN : AnimUtils.EASE_OUT;
            int duration =
                    mActivity.isDialpadShown() ? mShowDialpadDuration : mHideDialpadDuration;
            getView().setTranslationY(startTranslationValue);
            getView().animate()
                    .translationY(endTranslationValue)
                    .setInterpolator(interpolator)
                    .setDuration(duration);
        } else {
            getView().setTranslationY(endTranslationValue);
        }

        // There is padding which should only be applied when the dialpad is not shown.
        int paddingTop = mActivity.isDialpadShown() ? 0 : mPaddingTop;
        final ListView listView = getListView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            listView.setPaddingRelative(
                    listView.getPaddingStart(),
                    paddingTop,
                    listView.getPaddingEnd(),
                    listView.getPaddingBottom());
        }
        else {
            listView.setPadding(
                    listView.getPaddingLeft(),
                    paddingTop,
                    listView.getPaddingRight(),
                    listView.getPaddingBottom());
        }
    }

    protected void startCallOrConversation(final int position, final long id,
                                           final String number) {
        AxoMessaging axoMessaging =
                AxoMessaging.getInstance(getOwnContext().getApplicationContext());
        boolean axoRegistered = axoMessaging.isRegistered();

        /*
         * If AxoMessaging has been registered it is possible to start a conversation
         * with entered number. Check whether fragment has conversation flag set and
         * start conversation if it is otherwise ask user what to do.
         *
         * If AxoMessaging has not been registered, start call.
         */
        if (axoRegistered && Character.isLetter(number.charAt(0))) {
            if (mStartConversationFlag) {
                validateUser(number);
            }
            else {
                buildCallConversationDialog(position, id, number)
                        .show();
            }
        }
        else {
            super.onItemClick(position, id);
        }
    }


    protected void startConversation(final String userName, final String uuid) {
        final Intent conversationIntent = ContactsUtils.getMessagingIntent(uuid, getActivity());
        Extra.ALIAS.to(conversationIntent, userName);
        DialerUtils.startActivityWithErrorToast(getActivity(), conversationIntent,
                R.string.add_contact_not_available);

    }

    protected Dialog buildCallConversationDialog(final int position, final long id, final String number) {

        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();

        final Cursor cursor = (Cursor)adapter.getItem(position);
        final int scIndex = cursor.getColumnIndex(ScDirectoryLoader.SC_UUID_FIELD);
        boolean withContact = false;
        final String scInfo;
        if (scIndex >= 0) {
            scInfo = cursor.getString(scIndex);
            withContact = scInfo != null;
        }
        else {
            scInfo = null;
        }
        final Context context = getActivity();
        final CallOrConversationDialog dialog = new CallOrConversationDialog(context, withContact);
        dialog.setOnCallOrConversationSelectedListener(
                new CallOrConversationDialog.OnCallOrConversationSelectedListener() {

                    @Override
                    public void onCallSelected() {
                        SearchFragment.super.onItemClick(position, id);
                    }

                    @Override
                    public void onConversationSelected() {
                        validateUser(number);
                    }

                    @Override
                    public void onAddContactSelected() {addContactToScAccount(cursor, scInfo);}
                });

        return dialog;
    }

    protected void showValidationError(final String userName, ValidationState state) {
        Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(activity);

            alert.setTitle(R.string.dialog_title_unable_to_send_messages);
            alert.setMessage(getString(R.string.dialog_message_unable_to_send_to, userName)
                    + "\n\n" + getString(ValidationState.getStateDescriptionId(state)));

            alert.setCancelable(false);
            alert.setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            alert.show();
        }
    }

    /**
     * Validate a user name (alias) and get its UUID, display name, and default alias.
     *
     * This function uses the build in name lookup function of the Axolotl library
     * via the UserDataBackgroundTask. It checks if a name exists in SC directory
     * and returns its UUID, default alias and display name.
     *
     * After a first check for an alias the name lookup caches the data and a subsequent
     * verification with the same alias returns the data from the cache.
     *
     * @param userName a user alias name
     */
    public void validateUser(final String userName) {
        if (TextUtils.isEmpty(userName)) {
            showValidationError(userName, ValidationState.USERNAME_EMPTY);
        }

//        Context context = getActivity().getApplicationContext();
//        AxoMessaging axoMessaging = AxoMessaging.getInstance(context);
//        Conversation conversation = axoMessaging.getConversations().findById(userName);
//        // TODO: conversation can have isValidated field which is set to true when conversation
//        // partner's name has first been checked online.
//
//        /* if a conversation already exists, proceed to it, otherwise validate the user name */
//        if (conversation != null) {
//            startConversation(userName);
//        }
//        else {
            // The getUserData _must not_ run on UI thread because it may trigger a network
            // activity to do a lookup on provisioning server. The validation also fill the
            // Name Lookup cache thus follow-up validations are fast.
            AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask() {

                @Override
                protected void onPostExecute(Integer time) {
                    super.onPostExecute(time);
                    if (mData != null && mUserInfo.mUuid != null) {
                        ScDirectoryLoader.clearCachedData();
                        startConversation(userName, mUserInfo.mUuid);
                    }
                    else {
                        showValidationError(userName, ValidationState.VALIDATION_ERROR);

                    }
                }
            };
            AsyncUtils.execute(getUserDataTask, userName);
//        }
    }

    private void addContactToScAccount(Cursor cursor, String scName) {

        final Intent intent = new Intent(getOwnContext(), ContactAdder.class);
        intent.putExtra("AssertedName", "sip:" + scName + getString(R.string.sc_sip_domain_0));
        intent.putExtra("Text", cursor.getString(PhoneNumberListAdapter.PhoneQuery.DISPLAY_NAME));
        getOwnContext().startActivity(intent);
    }

    /**
     * Sets visibility of conversation shortcut.
     *
     * Shortcut is visible if Axolotl is enabled and entered string
     * starts with a letter and is at least two symbols long.
     */
    private void setConversationShortcutEnabled() {
        String queryString = getQueryString();
        final boolean showMessagingShortcut = Utilities.isValidSipUsername(queryString)
                && mAxoRegistered;

        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        if (adapter != null) {
            boolean changed = adapter.setShortcutEnabled(
                    DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION,
                    showMessagingShortcut);
            if (changed) {
                adapter.notifyDataSetChanged();
            }

        }
    }
}
