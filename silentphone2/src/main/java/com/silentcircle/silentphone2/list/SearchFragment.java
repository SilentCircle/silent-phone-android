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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DRUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.contacts.list.ContactListFilter;
import com.silentcircle.contacts.list.DirectoryPartition;
import com.silentcircle.contacts.list.PhoneNumberListAdapter;
import com.silentcircle.contacts.list.PhoneNumberPickerFragment;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.contacts.list.ScDirectoryLoader;
import com.silentcircle.contacts.list.ScV1UserLoader;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.fragments.CreateGroupFragment;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.task.ScConversationLoader;
import com.silentcircle.messaging.task.ValidationState;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.CallOrConversationDialog;
import com.silentcircle.messaging.views.ContactSelection;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.ContactAdder;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SearchEditTextLayout;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.Format;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import zina.ZinaNative;

public class SearchFragment extends PhoneNumberPickerFragment implements
        ZinaMessaging.AxoMessagingStateCallback, ContactSelection.OnSelectionChangedListener {

    @SuppressWarnings("unused")
    private static final String TAG = "SearchFragment";

    public static final String SELECTION_MAX_SIZE = "com.silentcircle.messaging.extra.SELECTION_SIZE";
    public static final String DISPLAY_NAMES = "com.silentcircle.messaging.extra.DISPLAY_NAMES";

    public static final int SELECTION_SIZE_LIMIT = CreateGroupFragment.MAX_GROUP_MEMBERS - 1;

    private static final Format DATE_FORMAT =
            android.text.format.DateFormat.getDateFormat(SilentPhoneApplication.getAppContext());

    private ContactSelection mContactSelection;
    private TextView mContactSelectionCounter;
    private View mButtonInvite;
    private int mAllowedSelectionSize = SELECTION_SIZE_LIMIT;
    private HashMap<String, String> mDisplayNames = new HashMap<>();
    private String mStringCouldNotValidateUser;
    private String mDefaultGroupDescription;
    private String mDefaultGroupName;
    private String mNetworkNotAvailable;
    private String mContactSelectionCounterDescription;

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
    private OnSearchActionListener mListener;

    public interface OnSearchActionListener {
        void onStartConversation(final String userName, final String uuid);
    }

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
        /*
         * ensure parameters from arguments are known to parent fragment by loading them
         * before super.onCreate()
         */
        Bundle arguments = getArguments();
        if (arguments != null) {
            mAllowedSelectionSize = arguments.getInt(SELECTION_MAX_SIZE, SELECTION_SIZE_LIMIT);
            setUserSelectionMode(Extra.IS_GROUP.getBoolean(arguments));
        }

        super.onCreate(savedInstanceState);

        mStringCouldNotValidateUser = getString(R.string.group_messaging_could_not_validate_user);
        mDefaultGroupDescription = getString(R.string.group_messaging_group_conversation_created);
        mDefaultGroupName = getString(R.string.group_messaging_new_group_conversation);
        mNetworkNotAvailable = getString(R.string.network_not_available_title);
        mContactSelectionCounterDescription = getString(R.string.group_messaging_create_group_selection_counter);

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        mAxoRegistered = axoMessaging.isRegistered();
        axoMessaging.addStateChangeListener(this);
    }

    public void setOnSearchActionListener(OnSearchActionListener listener) {
        this.mListener = listener;
    }

    public OnSearchActionListener getOnSearchActionListener() {
        return mListener;
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

    @Override
    public void onDetach() {
        super.onDetach();
        mActivityScrollListener = null;
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
        setVisibleScrollbarEnabled(true);
        listView.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mActivityScrollListener != null) {
                    mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                                 int totalItemCount) {
            }
        });

        updatePosition(false /* animate */);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mContactSelection != null) {
            mContactSelection.setItems(getSelectedItems());
        }
        setInviteButtonEnabled();
        setDialpadEnabled(!inUserSelectionMode());
    }

    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DISPLAY_NAMES, new JSONObject(mDisplayNames).toString());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mContactSelection != null) {
            mContactSelection.stopRequestProcessing();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Activity activity = getActivity();

        if (activity != null) {
            ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
            axoMessaging.removeStateChangeListener(this);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            String names = savedInstanceState.getString(DISPLAY_NAMES);
            if (!TextUtils.isEmpty(names)) {
                try {
                    JSONObject json = new JSONObject(names);
                    Iterator<String> keysItr = json.keys();
                    while(keysItr.hasNext()) {
                        String key = keysItr.next();
                        String value = (String) json.get(key);
                        mDisplayNames.put(key, value);
                    }
                } catch (JSONException e) {
                    // leave display names empty
                }
            }
        }

        if (inUserSelectionMode()) {
            prepareUserSelectionView(false);
        }
        ViewUtil.addBottomPaddingToListView(getListView(),
                getResources().getDimensionPixelSize(R.dimen.contact_browser_list_bottom_margin));
    }

    protected void prepareUserSelectionView(boolean animate) {
        // Placeholder for selection list
        ViewStub headerStub = (ViewStub) getView().findViewById(R.id.header_stub);
        headerStub.setLayoutResource(R.layout.widget_current_contact_selection);
        final ViewGroup headerView = (ViewGroup) headerStub.inflate();
        View emptySelection = headerView.findViewById(R.id.empty_selection);
        mContactSelection = (ContactSelection) headerView.findViewById(R.id.selection);
        mContactSelection.setOnSelectionChangedListener(this);
        mContactSelection.setEmptyView(emptySelection);
        mContactSelection.setItems(getSelectedItems());
        mContactSelectionCounter = (TextView) headerView.findViewById(R.id.selection_counter);
        mButtonInvite = headerView.findViewById(R.id.button_invite);
        mButtonInvite.setOnClickListener(this);

        if (animate) {
            AnimUtils.changeHeight(headerView, 0,
                    (int) getResources().getDimension(R.dimen.widget_contact_selection_height), 300);
        }

        updateSelectionCounter();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.button_invite) {
            addSelectionToConversation();
        } else {
            super.onClick(view);
        }
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

    @Override
    public void onItemRemoved(int position, Object item) {
        removeFromSelection((String) item);
    }

    public void setAddToContactNumber(String addToContactNumber) {
        mAddToContactNumber = addToContactNumber;
    }

    public void setStartConversationFlag(boolean startConversationFlag) {
        mStartConversationFlag = startConversationFlag;
    }

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity(), LoadUserInfo.canCallOutboundOca(getOwnContext()));
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());

        adapter.setCheckable(inUserSelectionMode());
        adapter.setSelectedItems(getSelectedItems());
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        setConversationShortcutEnabled();
        setCallShortcutEnabled();

        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        adapter.setCheckable(inUserSelectionMode());
        adapter.setSelectedItems(getSelectedItems());
        adapter.setScExactMatchFilter(ScV1UserLoader.SHOW);
        if (inUserSelectionMode()) {
            adapter.setScDirectoryFilter(ScDirectoryLoader.NAMES_ONLY);
            adapter.setScConversationFilter(ScConversationLoader.NONE);

            setFilter(ContactListFilter.createFilterWithType(ContactListFilter.FILTER_TYPE_WITH_SIP_NUMBERS_ONLY));
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;
        ScDirectoryLoader.clearCachedData();

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                int adjustedPosition = adapter.getSuperPosition(position);

                if (inUserSelectionMode()) {
                    Activity activity = getActivity();
                    if (activity == null) {
                        return;
                    }

                    if (Utilities.isNetworkConnected(activity)) {
                        selectItem(position);
                    }
                    else {
                        hideSoftKeyboard();
                        Snackbar.make(findViewById(R.id.pinned_header_list_layout),
                                mNetworkNotAvailable,
                                Snackbar.LENGTH_LONG)
                                .show();
                    }
                }
                else {
                    if (isGroup(adapter, adjustedPosition)) {
                        startGroupConversation(null, getUuid(adapter, adjustedPosition), true);
                    } else {
                        startCallOrConversation(adjustedPosition, id, adapter.getPhoneNumber(adjustedPosition));
                    }
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL:
                // checkDRAndStartCall(getQueryString());
                listener = getOnPhoneNumberPickerListener();
                if (listener != null) {
                    listener.onCallNumberDirectly(getQueryString());
                }
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_NUMBER_TO_CONTACTS:
                Activity activity = getActivity();

                if (activity == null) {
                    return;
                }

                final String number = TextUtils.isEmpty(mAddToContactNumber) ?
                        adapter.getFormattedQueryString() : mAddToContactNumber;
                final Intent intent = ContactsUtils.getAddNumberToContactIntent(number);  // adds directly to native contacts
                DialerUtils.startActivityWithErrorToast(activity, intent,
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
            case DialerPhoneNumberListAdapter.SHORTCUT_START_GROUP_CHAT:
                enterUserSelectionMode();
                break;
            case DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_GROUP_CHAT:
                if (inUserSelectionMode()) {
                    addToSelection(getQueryString());
                }
                clearQuery();
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
        ZinaMessaging axoMessaging =
                ZinaMessaging.getInstance();
        boolean axoRegistered = axoMessaging.isRegistered();

        /*
         * If AxoMessaging has been registered it is possible to start a conversation
         * with entered number. Check whether fragment has conversation flag set and
         * start conversation if it is otherwise ask user what to do.
         *
         * If AxoMessaging has not been registered, start call.
         */
        if (axoRegistered && !TextUtils.isEmpty(number) && Character.isLetter(number.charAt(0))) {
            if (mStartConversationFlag) {
                validateUser(number);
            }
            else {
                validateUser(position, number);
            }
        }
        else {
            Activity activity = getActivity();
            // check whether call is blocked by local DR
            if (activity != null && !DRUtils.isCallingDrBlocked(activity)) {
                if (!Utilities.isNetworkConnected(activity)) {
                    showValidationError(number, ValidationState.NETWORK_NOT_AVAILABLE,
                            DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL);
                    return;
                }

                super.onItemClick(position, id);
            }
        }
    }

    protected void startConversation(final String userName, final String uuid, final boolean isValidated) {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        // keyboard is not hidden when item is clicked, do it here
        hideSoftKeyboard();

        OnSearchActionListener listener = getOnSearchActionListener();
        if (listener != null) {
            listener.onStartConversation(userName, uuid);
        }

        final Intent conversationIntent = getConversationIntent(userName, uuid, isValidated, activity);

        DialerUtils.startActivityWithErrorToast(activity, conversationIntent,
                R.string.add_contact_not_available);
    }

    protected void startGroupConversation(@Nullable final String userName,
            @Nullable final String uuid, final boolean isValidated) {
        Activity activity = getActivity();

        if (activity == null) {
            return;
        }

        if (TextUtils.isEmpty(uuid)) {
            return;
        }

        // keyboard is not hidden when item is clicked, do it here
        hideSoftKeyboard();

        OnSearchActionListener listener = getOnSearchActionListener();
        if (listener != null) {
            listener.onStartConversation(userName, uuid);
        }

        final Intent conversationIntent = getConversationIntent(userName, uuid, isValidated, activity);
        Extra.GROUP.to(conversationIntent, uuid);
        Extra.IS_GROUP.to(conversationIntent, true);
        if (!TextUtils.isEmpty(userName)) {
            Extra.PARTICIPANTS.to(conversationIntent, new CharSequence[]{userName});
        }

        DialerUtils.startActivityWithErrorToast(activity, conversationIntent,
                R.string.add_contact_not_available);
    }

    @Nullable
    protected Intent getConversationIntent(String userName, String uuid, boolean isValidated, Context context) {
        final Intent conversationIntent = ContactsUtils.getMessagingIntent(uuid, context);
        Extra.ALIAS.to(conversationIntent, userName);
        if (isValidated) {
            Extra.VALID.to(conversationIntent, true);
        }
        // We store these pending attachment strings temporarily in the search fragment
        // They are passed back to the activity when a recipient is selected
        String pendingDataFile = Extra.DATA.from(getArguments());

        if (!TextUtils.isEmpty(pendingDataFile)) {
            Extra.DATA.to(conversationIntent, pendingDataFile);
        } else {
            String pendingDataText = Extra.TEXT.from(getArguments());

            if (!TextUtils.isEmpty(pendingDataText)) {
                Extra.TEXT.to(conversationIntent, pendingDataText);
            }
        }

        Bundle extras = conversationIntent.getExtras();
        Bundle arguments = getArguments();
        if (extras != null) {
            if (arguments != null) {
                extras.putAll(arguments);
            }
        } else {
            extras = arguments;
        }
        conversationIntent.putExtras(extras);

        return conversationIntent;
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

    protected void validateUser(int itemPosition, String number) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();

        final Cursor cursor = (Cursor)adapter.getItem(itemPosition);
        final int uuidIndex = cursor.getColumnIndex(ScDirectoryLoader.SC_UUID_FIELD);

        if (uuidIndex >= 0) {
            if (cursor.getString(uuidIndex) != null) {
                validateUser(number); // Validate the number (username)
            }
        } else {
            // Try to validate a sip username
            number = Utilities.removeSipParts(number);

            validateUser(number);
        }
    }

    protected void showValidationError(final String userName, ValidationState state, int shortcut) {
        Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            int titleId = (shortcut == DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL)
                    ? R.string.dialog_title_unable_to_call
                    : R.string.dialog_title_unable_to_send_messages;

            String message = getString(
                    shortcut == DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL
                            ? R.string.dialog_call_failed_to_send_to
                            : R.string.dialog_message_failed_to_send_to,
                    userName,
                    getString(ValidationState.getStateDescriptionId(state)));

            AlertDialog.Builder alert = new AlertDialog.Builder(activity);

            alert.setTitle(titleId);
            alert.setMessage(message);
            alert.setCancelable(false);
            alert.setPositiveButton(R.string.dialog_button_ok, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            alert.show();
/*
            InfoMsgDialogFragment.showDialog(activity, titleId, message, R.string.dialog_button_ok, -1);
*/
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
        Activity activity = getActivity();
        if (activity == null) {             // happens if fragment was detached
            return;
        }

        if (TextUtils.isEmpty(userName)) {
            showValidationError(userName, ValidationState.USERNAME_EMPTY,
                    DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
        }

        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        Conversation conversation = zinaMessaging.getConversations().findById(userName);

        if (conversation != null
                && conversation.getPartner() != null
                && conversation.getPartner().getUserId() != null
                && conversation.getPartner().isValidated()) {
            startConversation(userName, conversation.getPartner().getUserId(), false);
        } else {
            if (!Utilities.isNetworkConnected(activity)) {
                showValidationError(userName, ValidationState.NETWORK_NOT_AVAILABLE,
                        DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
                return;
            }

            // The getUserData _must not_ run on UI thread because it may trigger a network
            // activity to do a lookup on provisioning server. The validation also fill the
            // Name Lookup cache thus follow-up validations are fast.
            AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask() {

                @Override
                protected void onPostExecute(Integer time) {
                    super.onPostExecute(time);
                    if (mData != null && mUserInfo.mUuid != null) {
                        ScDirectoryLoader.clearCachedData();
                        startConversation(userName, mUserInfo.mUuid, true);
                    } else {
                        showValidationError(userName, ValidationState.VALIDATION_ERROR,
                                DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
                    }
                    setProgressEnabled(false);
                }
            };
            AsyncUtils.execute(getUserDataTask, userName);
            setProgressEnabled(true);
//        }
        }
    }

    private void checkDRAndStartCall(final String number) {
        Activity activity = getActivity();
        if (activity == null || TextUtils.isEmpty(number)) {
            return;
        }

        // if internal code is used allow call to proceed, cheat codes start with *##*
        if (number.startsWith("*##*")) {
            final OnPhoneNumberPickerActionListener listener = getOnPhoneNumberPickerListener();
            if (listener != null) {
                listener.onCallNumberDirectly(number);
            }
            return;
        }

        // if call is blocked due to local DR, show message and return
        if (DRUtils.isCallingDrBlocked(activity)) {
            return;
        }

        // a number is being called, proceed as local DR is already checked
        if (Character.isDigit(number.charAt(0)) || number.startsWith("*")) {
            final OnPhoneNumberPickerActionListener listener = getOnPhoneNumberPickerListener();
            if (listener != null) {
                listener.onCallNumberDirectly(number);
            }
            return;
        }

        if (!Utilities.isNetworkConnected(activity)) {
            showValidationError(number, ValidationState.NETWORK_NOT_AVAILABLE,
                    DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL);
            return;
        }

        boolean forceRefresh = LoadUserInfo.isBrmr() || LoadUserInfo.isBrdr();
        AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask(forceRefresh) {

            @Override
            protected void onPostExecute(Integer time) {
                super.onPostExecute(time);
                // TODO
                // Don't re-read user info, use what is available here.
                if (mData != null && mUserInfo.mUuid != null) {
                    Activity activity = getActivity();
                    if (!DRUtils.isCallingRemoteDrBlocked(activity, number)) {
                        final OnPhoneNumberPickerActionListener listener =
                                getOnPhoneNumberPickerListener();
                        if (listener != null) {
                            listener.onCallNumberDirectly(number);
                        }
                    }
                } else if (number.charAt(0) == '+') {
                    // a verified non-SC number is being called, proceed as local DR is already checked
                    final OnPhoneNumberPickerActionListener listener =
                            getOnPhoneNumberPickerListener();
                    if (listener != null) {
                        listener.onCallNumberDirectly(number);
                    }
                } else {
                    showValidationError(number, ValidationState.VALIDATION_ERROR,
                            DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL);
                }
                setProgressEnabled(false);
            }
        };
        AsyncUtils.execute(getUserDataTask, StringUtils.rtrim(number, '!'));
        setProgressEnabled(true);
    }

    public void validateUserAndStartGroupConversation(final String groupId, final String userName) {
        if (TextUtils.isEmpty(userName)) {
            showValidationError(userName, ValidationState.USERNAME_EMPTY,
                    DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
        }

        ZinaMessaging axoMessaging = ZinaMessaging.getInstance();
        Conversation conversation = axoMessaging.getConversations().findById(groupId);

        if (conversation != null
                && conversation.getPartner().getUserId() != null
                && conversation.getPartner().isValidated()) {
            startGroupConversation(userName, conversation.getPartner().getUserId(), false);
        } else {
            if (!Utilities.isNetworkConnected(getActivity())) {
                showValidationError(userName, ValidationState.NETWORK_NOT_AVAILABLE,
                        DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
                return;
            }

            // The getUserData _must not_ run on UI thread because it may trigger a network
            // activity to do a lookup on provisioning server. The validation also fill the
            // Name Lookup cache thus follow-up validations are fast.
            AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask() {

                @Override
                protected void onPostExecute(Integer time) {
                    super.onPostExecute(time);
                    if (mData != null && mUserInfo.mUuid != null) {
                        ScDirectoryLoader.clearCachedData();
                        startGroupConversation(mUserInfo.mUuid, groupId, true);
                    } else {
                        showValidationError(userName, ValidationState.VALIDATION_ERROR,
                                DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION);
                    }
                }
            };
            AsyncUtils.execute(getUserDataTask, userName);
        }
    }

    private void addContactToScAccount(Cursor cursor, String scName) {

        final Intent intent = new Intent(getOwnContext(), ContactAdder.class);
        intent.putExtra(ContactsUtils.PARAM_ASSERTED_NAME,
                "sip:" + scName + getString(R.string.sc_sip_domain_0));
        intent.putExtra(ContactsUtils.PARAM_TEXT,
                cursor.getString(PhoneNumberListAdapter.PhoneQuery.DISPLAY_NAME));
        getOwnContext().startActivity(intent);
    }

    /**
     * Sets visibility of conversation shortcut.
     *
     * Shortcut is visible if Axolotl is enabled and entered string
     * starts with a letter and is at least two symbols long.
     */
    private void setConversationShortcutEnabled() {
        final boolean showMessagingShortcut = areShowMessagingShortcutsEnabled();

        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        if (adapter != null) {
            boolean changed = adapter.setShortcutEnabled(
                    DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CONVERSATION,
                    false);
            changed |= adapter.setShortcutEnabled(
                    DialerPhoneNumberListAdapter.SHORTCUT_START_GROUP_CHAT,
                    TextUtils.isEmpty(getQueryString()) && !inUserSelectionMode());
            changed |= adapter.setShortcutEnabled(

                    DialerPhoneNumberListAdapter.SHORTCUT_ADD_TO_GROUP_CHAT, false);
            if (changed) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    protected boolean areShowMessagingShortcutsEnabled() {
        String queryString = getQueryString();
        return (Utilities.isValidSipUsername(queryString)
                || Utilities.isValidEmail(queryString))
                && mAxoRegistered;
    }

    /**
     * Sets visibility for call shortcut
     *
     * Shortcut is visible if entered string is "callable". This function is override for
     * RegularSearchListAdapter#setQueryString which does not handle urgent and emergency calls
     * and handles only search strings which end with one or two exclamation marks.
     */
    private void setCallShortcutEnabled() {
        String queryString = getQueryString();
        if (!TextUtils.isEmpty(queryString) && queryString.matches(".*[^!](!){1,2}$")) {
            queryString = StringUtils.rtrim(queryString, '!');
            final boolean showNumberShortcuts =
                    (PhoneNumberUtils.isGlobalPhoneNumber(queryString.replaceAll("\\s", ""))
                            || queryString.startsWith("*"))
                    && !inUserSelectionMode();

            final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
            if (adapter != null) {
                boolean changed = adapter.setShortcutEnabled(
                        DialerPhoneNumberListAdapter.SHORTCUT_DIRECT_CALL,
                        showNumberShortcuts);
                if (changed) {
                    adapter.notifyDataSetChanged();
                }
            }
        }
    }

    protected boolean isGroup(@NonNull DialerPhoneNumberListAdapter adapter, int position) {
        boolean result = false;
        final Cursor cursor = (Cursor) adapter.getItem(position);
        if (cursor != null) {
            final int fieldIdx = cursor.getColumnIndex(ScDirectoryLoader.SC_PRIVATE_FIELD);
            if (fieldIdx > -1) {
                result = ScConversationLoader.GROUP.equals(cursor.getString(fieldIdx));
            }
        }
        return result;
    }

    @Nullable
    protected String getUuid(@NonNull DialerPhoneNumberListAdapter adapter, int position) {
        String result = null;
        final Cursor cursor = (Cursor) adapter.getItem(position);
        if (cursor != null) {
            final int uuidIndex = cursor.getColumnIndex(ScDirectoryLoader.SC_UUID_FIELD);
            if (uuidIndex > -1) {
                result = cursor.getString(uuidIndex);
            }
        }
        return result;
    }

    @NonNull
    public List<String> getSelectedUuids() {
        return getSelectedItems();
    }

    protected void enterUserSelectionMode() {
        setUserSelectionMode(true);
        setDialpadEnabled(false);
        prepareUserSelectionView(true);
        setInviteButtonEnabled();
        configureAdapter();

        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();

        for (int i = 0; i < adapter.getPartitionCount(); i++) {
            DirectoryPartition partition = (DirectoryPartition) adapter.getPartition(i);

            if (partition.getDirectoryType().equals(getString(R.string.contactsList))) {
                adapter.removePartition(i);
                break;
            }
        }

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            ((SearchEditTextLayout) actionBar.getCustomView()).showInputMethod();
        }
    }

    @Override
    protected boolean canAddToSelection() {
        boolean result = true;
        if (getCheckedCount() >= mAllowedSelectionSize) {
            Log.d(TAG, "Maximum number of contacts selected!");
            hideSoftKeyboard();
            Snackbar.make(findViewById(R.id.pinned_header_list_layout),
                    R.string.group_messaging_maximum_number_of_participants_selected,
                    Snackbar.LENGTH_LONG)
                    .show();

            result = false;
        }
        return result;
    }

    @Override
    protected synchronized void addToSelection(String item) {
        super.addToSelection(item);
        setInviteButtonEnabled();
        validateAddition(item);
    }

    @Override
    protected synchronized void removeFromSelection(String item) {
        super.removeFromSelection(item);
        setInviteButtonEnabled();
        updateSelectionCounter();
    }

    @Override
    protected void onAddToSelection(String item, int position) {
        if (mContactSelection != null) {
            mContactSelection.addItem(position);
        }
        clearQuery();
        updateSelectionCounter();
    }

    @Override
    protected void onRemoveFromSelection(String item, int position) {
        if (mContactSelection != null) {
            mContactSelection.removeItem(position);
        }
        updateSelectionCounter();
    }

    @Override
    protected void onClearSelection() {
        if (mContactSelection != null) {
            mContactSelection.setItems(getSelectedItems());
        }
        updateSelectionCounter();
    }

    protected void clearQuery() {
        setQueryString("", false);
        try {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                ((SearchEditTextLayout) actionBar.getCustomView()).clearSearchQuery();
            }
        } catch (Exception e) {
            // ignore, query will still be displayed, can be cleared manually
        }
    }

    protected void setDialpadEnabled(boolean enabled) {
        try {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                ((SearchEditTextLayout) actionBar.getCustomView()).setIsDialpadEnabled(enabled);
            }
        } catch (Exception e) {
            // ignore, dialpad switch button visibility won' change
        }
    }

    protected void setInviteButtonEnabled() {
        Activity activity = getActivity();
        if (mButtonInvite != null) {
            mButtonInvite.setEnabled(getCheckedCount() > 0
                    && activity != null
                    && Utilities.isNetworkConnected(activity));
        }
    }

    protected void addSelectionToConversation() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (!Utilities.isNetworkConnected(activity)) {
            hideSoftKeyboard();
            Snackbar.make(findViewById(R.id.pinned_header_list_layout),
                    mNetworkNotAvailable,
                    Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        // create a new group and invite selected users
        List<String> uuids = getSelectedUuids();
        setProgressEnabled(true);
        final String name = getGeneratedGroupName(uuids);
        final String description = getGeneratedGroupDescription();
        /* do not use generated name as group name so it gets regenerated on user changes */
        final String groupUuid = ZinaMessaging.createNewGroup(null, IOUtils.encode(description));
        if (!TextUtils.isEmpty(groupUuid)) {
            // set default group burn time to 3 days
            int result = ZinaMessaging.setGroupBurnTime(groupUuid, TimeUnit.DAYS.toSeconds(3), 1);

            /*
             * Delay applying changes until after addUser gets called.
             * Unless there are no participants. In that case do it now.
             */
            if (result == MessageErrorCodes.SUCCESS && uuids.size() == 0) {
                result = ZinaMessaging.applyGroupChangeSet(groupUuid);
            }

            if (result == MessageErrorCodes.SUCCESS) {
                Conversation conversation = ConversationUtils.getOrCreateGroupConversation(
                        SilentPhoneApplication.getAppContext(), groupUuid);
                if (conversation != null) {

                    // TODO conversation name may be set to group id
                    ConversationRepository repository =
                            ConversationUtils.getConversations();
                    if (repository != null) {
                        conversation.getPartner().setDisplayName(name);
                        repository.save(conversation);

                        InfoEvent event = MessageUtils.createInfoEvent(groupUuid,
                                InfoEvent.INFO_NEW_GROUP, "Group created", "");
                        repository.historyOf(conversation).save(event);
                    }

                    final Intent conversationIntent = ContactsUtils.getMessagingIntent(groupUuid,
                            activity);
                    Extra.DISPLAY_NAME.to(conversationIntent, name);
                    Extra.GROUP.to(conversationIntent, groupUuid);
                    Extra.IS_GROUP.to(conversationIntent, true);
                    // currently user list can be empty, user can be added from chat view
                    if (uuids.size() > 0) {
                        CharSequence[] members = uuids.toArray(new CharSequence[uuids.size()]);
                        Extra.PARTICIPANTS.to(conversationIntent, members);
                    }

                    OnSearchActionListener listener = getOnSearchActionListener();
                    if (listener != null) {
                        listener.onStartConversation(null, groupUuid);
                    }

                    activity.startActivity(conversationIntent);
                }
                else {
                    ZinaMessaging.leaveGroup(groupUuid);
                    Log.d(TAG, "Failed to create group conversation instance for group: "
                            + groupUuid);
                }
            }
            else {
                Log.d(TAG, "Failed to create group conversation: error code: "
                        + ZinaNative.getErrorCode() + ", info: " + ZinaNative.getErrorInfo());
                Toast.makeText(activity, activity.getString(R.string.group_messaging_create_group_failed) + ": "
                                + ZinaNative.getErrorInfo() + " (" + ZinaNative.getErrorCode() + ")",
                        Toast.LENGTH_SHORT).show();
            }
        }
        else {
            Log.d(TAG, "Failed to create group conversation: error code: "
                    + ZinaNative.getErrorCode() + ", info: " + ZinaNative.getErrorInfo());
            Toast.makeText(activity, activity.getString(R.string.group_messaging_create_group_failed) + ": "
                            + ZinaNative.getErrorInfo() + " (" + ZinaNative.getErrorCode() + ")",
                    Toast.LENGTH_SHORT).show();
        }
        setProgressEnabled(false);
    }

    protected void validateAddition(final String userName) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (TextUtils.isEmpty(userName)) {
            return;
        }

        if (!Utilities.isNetworkConnected(getActivity())) {
            return;
        }

        ZinaMessaging zinaMessaging = ZinaMessaging.getInstance();
        Conversation conversation = zinaMessaging.getConversations().findById(userName);

        ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(userName);
        if (contactEntry != null) {
            String displayName = ConversationUtils.resolveDisplayName(contactEntry, null);
            if (TextUtils.isEmpty(displayName)) {
                displayName = contactEntry.name;
            }
            mDisplayNames.put(userName, displayName);
        }

        if (conversation != null
                && conversation.getPartner().getUserId() != null
                && conversation.getPartner().isValidated()) {
            // user is already validated, return
            if (!mDisplayNames.containsKey(userName)) {
                String displayName = TextUtils.isEmpty(conversation.getPartner().getDisplayName())
                        ? conversation.getPartner().getAlias()
                        : conversation.getPartner().getDisplayName();
                mDisplayNames.put(userName, displayName);
            }
        }
        else {
            AsyncTasks.UserDataBackgroundTask getUserDataTask = new AsyncTasks.UserDataBackgroundTask() {

                @Override
                protected void onPostExecute(Integer time) {
                    if (mData == null || (mUserInfo != null && mUserInfo.mUuid == null)) {
                        removeFromSelection(userName);
                        // show notification that user is invalid
                        hideSoftKeyboard();
                        Snackbar.make(findViewById(R.id.pinned_header_list_layout),
                                String.format(mStringCouldNotValidateUser, userName),
                                Snackbar.LENGTH_LONG)
                                .show();
                    }
                    if (mUserInfo != null && !mDisplayNames.containsKey(mUserInfo.mUuid)) {
                        String displayName = TextUtils.isEmpty(mUserInfo.mDisplayName)
                                ? mUserInfo.mAlias
                                : mUserInfo.mDisplayName;
                        mDisplayNames.put(mUserInfo.mUuid, displayName);
                    }
                }
            };
            AsyncUtils.execute(getUserDataTask, userName);
        }
    }

    // FIXME: clean up, use conversationUtils functions, don't cache names
    protected String getGeneratedGroupName(@NonNull List<String> uuids) {
        String result = null;
        List<String> names = new ArrayList<>();
        /* include oneself */
        String name = LoadUserInfo.getDisplayName();
        if (TextUtils.isEmpty(name)) {
            name = LoadUserInfo.getDisplayAlias();
        }
        name = StringUtils.formatShortName(name);
        if (!TextUtils.isEmpty(name)) {
            names.add(name);
        }
        for (String uuid : uuids) {
            name = StringUtils.formatShortName(mDisplayNames.get(uuid));
            if (!TextUtils.isEmpty(name)) {
                names.add(name);
            }
            Collections.sort(names, StringUtils.NAME_COMPARATOR);
            result = TextUtils.join(", ", names);
        }
        if (TextUtils.isEmpty(result)) {
            result = mDefaultGroupName;
        }
        return result;
    }

    protected String getGeneratedGroupDescription() {
        return mDefaultGroupDescription + " "
                + DATE_FORMAT.format(new Date(System.currentTimeMillis()));
    }

    protected void updateSelectionCounter() {
        if (mContactSelectionCounter != null) {
            int count = getSelectedItems().size();
            mContactSelectionCounter.setVisibility(count > 20 ? View.VISIBLE : View.GONE);
            mContactSelectionCounter.setText(
                    String.format(mContactSelectionCounterDescription, getSelectedItems().size(),
                            mAllowedSelectionSize));
        }
    }

}
