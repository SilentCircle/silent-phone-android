/*
Copyright (C) 2017, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.API;
import com.silentcircle.common.util.AsyncTasks;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.HttpUtil;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.LabelIndentSpan;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.json.JSONConversationAdapter;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.DeviceInfo;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.views.ContactInfoItem;
import com.silentcircle.messaging.views.ObservableScrollView;
import com.silentcircle.messaging.views.adapters.PaddedDividerItemDecoration;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivityInternal;
import com.silentcircle.silentphone2.fragments.SingleChoiceDialogFragment;
import com.silentcircle.silentphone2.util.SPAPreferences;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.silentphone2.views.SettingsItem;
import com.silentcircle.userinfo.LoadUserInfo;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import zina.ZinaNative;

/**
 * Fragment to show details for a selected uuid.
 */
public class ContactDetailsFragment extends BaseConversationDetailsFragment implements View.OnClickListener,
        ObservableScrollView.OnScrollChangedListener, DeviceAdapter.OnDeviceItemClickListener,
        AlertDialogFragment.OnAlertDialogConfirmedListener,
        SingleChoiceDialogFragment.OnSingleChoiceDialogItemSelectedListener {

    public static final String RESCAN_USER_DEVICES = "rescanUserDevices";
    public static final String RE_KEY_ALL_DEVICES = "reKeyAllDevices";

    /* Priority for this view to handle message broadcasts, higher than ConversationActivity. */
    private static final int MESSAGE_PRIORITY = 3;

    /* Dialog request code for delete conversation confirmation */
    private static final int REKEY_CONVERSATION = 1;

    private static final String CONVERSATION_ID =
            "com.silentcircle.messaging.fragments.ContactDetailsFragment.CONVERSATION_ID";
    private static final String FLAG_AVATAR_SCROLLED =
            "com.silentcircle.messaging.fragments.GroupManagementFragment.AVATAR_SCROLLED";
    private static final String SCROLL_POSITION =
            "com.silentcircle.messaging.fragments.GroupManagementFragment.SCROLL_POSITION";
    private static final String AVATAR_LOADED =
            "com.silentcircle.messaging.fragments.ContactDetailsFragment.AVATAR_LOADED";

    private AppCompatActivity mParent;

    private com.silentcircle.common.widget.ProgressBar mProgress;
    private com.silentcircle.messaging.views.RecyclerView mRecyclerView;
    private View mEmptyView;
    private TextView mContactInformation;
    private TextView mConversationInformation;
    private ContactInfoItem mDisplayName;
    private ContactInfoItem mOrganization;
    private ContactInfoItem mPhoneNumber;
    private SettingsItem mOptionMute;
    private View mDeviceContainer;
    private ImageView mContactAvatar;
    private ObservableScrollView mGroupInformationContainer;

    private DeviceAdapter mAdapter;
    private Point mDisplaySize = new Point();

    private String mContactId;
    private Set<CharSequence> mAliases = new HashSet<>();
    private AsyncTasks.UserInfo mUserInfo;
    private Uri mPhotoUri;

    private boolean mAvatarLoaded;
    private boolean mAvatarScrolled;
    private int mScrollPosition;

    private int mDetailsLabelSpacing;

    private byte[] mOwnDevice;
    private DeviceInfo.DeviceData mOwnDeviceData;

    private MessagingBroadcastReceiver mViewUpdater = new MessagingBroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Action action = Action.from(intent);
            if (Action.REFRESH_SELF.equals(action)) {
                mAvatarLoaded = false;
                refreshContactData();
            }
        }
    };

    private View.OnLongClickListener mCopyOnLongClick = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            ClipboardManager manager =
                    (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText(null, ((android.widget.TextView) v).getText()));
            Toast.makeText(v.getContext(),
                    R.string.toast_copied_to_clipboard,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
    };

    public static ContactDetailsFragment newInstance(Bundle args) {
        ContactDetailsFragment fragment = new ContactDetailsFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public ContactDetailsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        String conversationId = null;
        if (savedInstanceState != null) {
            mAvatarLoaded = savedInstanceState.getBoolean(AVATAR_LOADED);
            conversationId = savedInstanceState.getString(CONVERSATION_ID);
        }
        if (!TextUtils.isEmpty(conversationId)) {
            setConversation(conversationId);
        }

        Display display = mParent.getWindowManager().getDefaultDisplay();
        display.getSize(mDisplaySize);

        Resources resources = getResources();
        mDetailsLabelSpacing = resources.getDimensionPixelSize(R.dimen.spacing_large);

        if (TextUtils.equals(mContactId, LoadUserInfo.getUuid())) {
            mOwnDevice = ZinaMessaging.getOwnIdentityKey();
            mOwnDeviceData = mOwnDevice != null ? DeviceInfo.parseDeviceInfo(new String(mOwnDevice)) : null;
        }
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
        try {
            mParent = (AppCompatActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be AppCompatActivity.");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mAvatarScrolled = savedInstanceState.getBoolean(FLAG_AVATAR_SCROLLED);
            mScrollPosition = savedInstanceState.getInt(SCROLL_POSITION);
        }
        return inflater.inflate(R.layout.fragment_contact_details, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new DeviceAdapter(mParent, mContactId, null, mOwnDeviceData);
        mAdapter.setOnItemClickListener(this);

        mEmptyView = view.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(mEmptyView, -1,
                R.string.contact_info_device_list_empty, getResources());

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());

        mRecyclerView =
                (com.silentcircle.messaging.views.RecyclerView) view.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.addItemDecoration(new PaddedDividerItemDecoration(mParent));
        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setEmptyView(mEmptyView);

        boolean developer = SPAPreferences.getInstance(mParent).isDeveloper();
        if (developer) {
            View groupDetailsContainer = view.findViewById(R.id.contact_details_container);
            groupDetailsContainer.setVisibility(View.VISIBLE);
        }

        mGroupInformationContainer = (ObservableScrollView) view.findViewById(R.id.contact_details_main_container);
        mGroupInformationContainer.setOnScrollChangedListener(this);

        mContactAvatar = (ImageView) view.findViewById(R.id.contact_avatar);
        mDisplayName = (ContactInfoItem) view.findViewById(R.id.text_display_name);
        // mAlias = (TextView) view.findViewById(R.id.text_alias);
        mOrganization = (ContactInfoItem) view.findViewById(R.id.text_organization);
        mPhoneNumber = (ContactInfoItem) view.findViewById(R.id.text_phone_number);

        // mGroupAvatarContainer = view.findViewById(R.id.contact_avatar_container);
        mDeviceContainer = view.findViewById(R.id.self_device_container);

        mContactInformation = (TextView) view.findViewById(R.id.text_contact_information);
        mContactInformation.setOnLongClickListener(mCopyOnLongClick);
        mConversationInformation = (TextView) view.findViewById(R.id.text_conversation_information);
        mConversationInformation.setOnLongClickListener(mCopyOnLongClick);

        mOptionMute = (SettingsItem) view.findViewById(R.id.contact_details_option_mute);
        mOptionMute.setOnClickListener(this);

        mProgress = (com.silentcircle.common.widget.ProgressBar) view.findViewById(R.id.contact_details_progress);

        adjustScrollToAvatar();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_contact_info, menu);
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem rescanUserDevices = menu.findItem(R.id.action_rescan_devices);
        final MenuItem renewSession = menu.findItem(R.id.action_renew_session);
        final MenuItem addToContacts = menu.findItem(R.id.action_add_contact);
        if (/*(!TextUtils.isEmpty(mAlias) || Utilities.canMessage(mContactId))*/
                !Contact.UNKNOWN_USER_ID.equals(mContactId)) {
            ContactEntry contact = ContactsCache.getContactEntryFromContacts(mContactId);
            addToContacts.setVisible((contact == null || contact.lookupUri == null));
        }

        final boolean isOwnContactView = TextUtils.equals(mContactId, LoadUserInfo.getUuid());
        final boolean canMessageContact = Utilities.canMessage(mContactId);

        rescanUserDevices.setVisible(canMessageContact && !isOwnContactView);
        renewSession.setVisible(canMessageContact && !isOwnContactView);

        Context context = getActivity();
        if (context != null) {
            ViewUtil.tintMenuIcons(context, menu);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mParent.setTitle(getString(R.string.contact_info_title));
        registerReceiver();

        refreshContactData();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(AVATAR_LOADED, mAvatarLoaded);
        outState.putBoolean(FLAG_AVATAR_SCROLLED, mAvatarScrolled);
        outState.putInt(SCROLL_POSITION, mScrollPosition);
        outState.putString(CONVERSATION_ID, mContactId);
    }

    @Override
    public void onPause() {
        unregisterMessagingReceiver(mViewUpdater);
        ContactPhotoManagerNew.getInstance(SilentPhoneApplication.getAppContext())
                .clearRequestsForContext(getActivity());
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.contact_details_option_mute:
                if (mOptionMute.isChecked()) {
                    showSelectionDialog(R.string.dialog_title_mute_conversation,
                            R.array.conversation_mute_array, mConversationMuteIndex,
                            CONVERSATION_MUTE_SELECTION_DIALOG);
                }
                else {
                    setMuteDuration(mContactId, 0L);
                    updateMuteStatus();
                }
                break;
            case R.id.button_leave:
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = false;
        switch (item.getItemId()) {
            case R.id.action_rescan_devices:
                doRescan();
                result = true;
                break;
            case R.id.action_renew_session:
                confirmConversationRekey();
                result = true;
                break;
            default:
                break;
        }
        return result || super.onOptionsItemSelected(item);
    }

    @Override
    public void onScrollChanged(int deltaX, int deltaY) {
        mAvatarScrolled = true;

        int scrollY = mGroupInformationContainer.getScrollY();
        mContactAvatar.setTranslationY(scrollY * 0.5f);
    }

    @Override
    public void onItemClick(final @NonNull View view, final @NonNull Object item) {
        View idContainer = view.findViewById(R.id.id_container);
        if (idContainer != null) {
            idContainer.setVisibility(
                    idContainer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onItemLongClick(final @NonNull View view, final @NonNull Object item) {
    }

    @Override
    public void onCallClick(@NonNull View view, @NonNull Object item) {
        DeviceInfo.DeviceData devData = (DeviceInfo.DeviceData) item;
        String directDial = mContactId + ";xscdevid=" + devData.devId;
        Intent intent = ContactsUtils.getCallIntent(directDial);
        intent.putExtra(DialerActivityInternal.NO_NUMBER_CHECK, true);
        startActivity(intent);
    }

    @Override
    public void onDeleteClick(@NonNull View view, @NonNull Object item) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mProgress.setVisibility(View.VISIBLE);
        DeviceInfo.DeviceData devData = (DeviceInfo.DeviceData) item;
        // Delete the device from the server - this also removes ZINA information from the server
        API.V1.Me.Device.delete(getActivity(), devData.devId, new API.Callback() {
            @Override
            public void onComplete(HttpUtil.HttpResponse httpResponse, Exception exception) {
                doRescan();
            }
        });
    }

    @Override
    public void onAlertDialogConfirmed(DialogInterface dialog, int requestCode, Bundle bundle,
            boolean saveChoice) {
        if (requestCode == REKEY_CONVERSATION) {
            AxoCommandInBackground aib = new AxoCommandInBackground(this);
            aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, RE_KEY_ALL_DEVICES, mContactId);
        }
    }

    @Override
    public void onSingleChoiceDialogItemSelected(DialogInterface dialog, int requestCode, int index) {
        if (mParent == null) {
            return;
        }

        switch (requestCode) {
            case CONVERSATION_MUTE_SELECTION_DIALOG:
                setMuteDuration(mContactId, CONVERSATION_MUTE_DURATIONS[index]);
                updateMuteStatus();
                break;
        }
    }

    @Override
    public void onSingleChoiceDialogCanceled(DialogInterface dialog, int requestCode) {
        switch (requestCode) {
            case CONVERSATION_MUTE_SELECTION_DIALOG:
                updateMuteStatus();
                break;
        }
    }

    @Override
    public void update() {
        updateMuteStatus();
        scheduleNextUpdate();
    }

    @Override
    protected String getConversationId() {
        return mContactId;
    }

    public void setConversation(final @NonNull Conversation conversation) {
        setConversation(conversation.getPartner().getUserId());
    }

    public void setConversation(final @NonNull String conversationId) {
        mContactId = conversationId;
    }

    private void registerReceiver() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        /* register for REFRESH_SELF event */
        IntentFilter filter = Action.filter(Action.REFRESH_SELF);
        registerMessagingReceiver(activity, mViewUpdater, filter, MESSAGE_PRIORITY);
    }

    private void refreshContactData() {
        mAliases.clear();
        mUserInfo = null;
        mPhotoUri = null;

        CharSequence displayName = null;
        CharSequence organization = null;

        final boolean canMessageContact = Utilities.canMessage(mContactId);

        byte[][] aliasesList = ZinaNative.getAliases(mContactId);
        if (aliasesList != null) {
            for (byte[] alias : aliasesList) {
                if (alias != null && alias.length > 0) {
                    try {
                        mAliases.add(new String(alias, "UTF-8"));
                    } catch (UnsupportedEncodingException e) {
                        // ignore alias if not possible to decode
                    }
                }
            }
        }

        ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(mContactId);
        if (contactEntry == null) {
            AsyncUtils.execute(new Runnable() {
                @Override
                public void run() {
                    ContactsCache.getContactEntry(mContactId);
                }
            });
        }
        else {
            mPhotoUri = contactEntry.photoUri;
            if (!TextUtils.isEmpty(contactEntry.alias)) {
                mAliases.add(contactEntry.alias);
            }
            displayName = contactEntry.name;
        }

        if (canMessageContact) {
            byte[] userInfo = ZinaMessaging.getUserInfoFromCache(mContactId);
            if (userInfo != null) {
                mUserInfo = AsyncTasks.parseUserInfo(userInfo);
                if (mUserInfo != null) {
                    organization = mUserInfo.organization;
                    mAliases.add(mUserInfo.mAlias);
                }
            } else {
                AsyncUtils.execute(new Runnable() {
                    @Override
                    public void run() {
                        ZinaMessaging.refreshUserData(mContactId, null);
                    }
                });
            }
        }

        final String aliases = TextUtils.join(", ", mAliases);

        mDisplayName.setText(displayName);
        mDisplayName.setDescription(TextUtils.equals(displayName, aliases) ? null : aliases);
        mDisplayName.setVisibility(
                (!canMessageContact || (TextUtils.isEmpty(displayName) && TextUtils.isEmpty(aliases)))
                        ? View.GONE
                        : View.VISIBLE);
        mOrganization.setDescription(organization);
        mOrganization.setVisibility(TextUtils.isEmpty(organization) ? View.GONE : View.VISIBLE);
        mPhoneNumber.setDescription(Utilities.formatNumber(mContactId));
        mPhoneNumber.setVisibility(canMessageContact ? View.GONE : View.VISIBLE);

        updateMuteStatus();

        mDeviceContainer.setVisibility(canMessageContact ? View.VISIBLE : View.GONE);
        if (canMessageContact) {
            // TODO scan has been run and finished
            getDevicesInfo();
            doRescan();
        }

        if (!mAvatarLoaded) {
            loadContactAvatar();
        }

        // populate debug fields only in debug versions, fields are hidden for non-developers
        if (BuildConfig.DEBUG) {
            formatGroupData(contactEntry);
        }
    }

    private void updateMuteStatus() {
        if (mOptionMute == null) {
            return;
        }
        Conversation conversation = ConversationUtils.getConversation(mContactId);
        CharSequence muteDescription = getMuteStatusDescription(conversation);
        mOptionMute.setChecked(conversation != null && conversation.isMuted());
        mOptionMute.setDescription(muteDescription);
    }

    private void doRescan() {
        mProgress.setVisibility(View.VISIBLE);

        AxoCommandInBackground aib = new AxoCommandInBackground(this);
        aib.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, RESCAN_USER_DEVICES, mContactId);
    }

    private void getDevicesInfo() {
        byte[][] devices = ZinaMessaging.getIdentityKeys(IOUtils.encode(mContactId));
        ArrayList<DeviceInfo.DeviceData> devData = new ArrayList<>(5);
        if (devices != null) {
            for (byte[] device : devices) {
                DeviceInfo.DeviceData devInfo = DeviceInfo.parseDeviceInfo(new String(device));
                if (devInfo != null) {
                    devData.add(devInfo);
                }
            }
        }
        if (TextUtils.equals(mContactId, LoadUserInfo.getUuid())) {
            if (mOwnDeviceData != null) {
                boolean ownDevicePresent = false;
                for (DeviceInfo.DeviceData devInfo : devData) {
                    if (TextUtils.equals(devInfo.devId, mOwnDeviceData.devId)) {
                        ownDevicePresent = true;
                        break;
                    }
                }
                if (!ownDevicePresent) {
                    devData.add(0, mOwnDeviceData);
                }
            }
        }
        setupDeviceList(devData);
    }

    private void setupDeviceList(final @NonNull List<DeviceInfo.DeviceData> devData) {
        mProgress.setVisibility(View.GONE);
        mAdapter.setItems(devData);
    }

    private void formatGroupData(final @Nullable ContactEntry contactEntry) {
        String[] labels = new String[] {"Display name", "Aliases", "IM name", "UUID", "Organization",
                "Lookup uri", "Avatar url"};
        String[] steps = new String[] {
                contactEntry == null ? null : contactEntry.name,
                TextUtils.join(", ", mAliases),
                contactEntry == null ? null : contactEntry.imName,
                mUserInfo == null ? "n/a" : mUserInfo.mUuid,
                mUserInfo == null ? "n/a" : mUserInfo.organization,
                mUserInfo == null ? "n/a" : mUserInfo.mLookupUri,
                mUserInfo == null ? "n/a" : mUserInfo.mAvatarUrl};

        String longestLabel = labels[0];
        for (String label : labels) {
            if (label.length() > longestLabel.length()) {
                longestLabel = label;
            }
        }
        Paint paint = mContactInformation.getPaint();
        Rect bounds = new Rect();
        paint.getTextBounds(longestLabel, 0, longestLabel.length(), bounds);

        SpannableStringBuilder sb = new SpannableStringBuilder("");
        int indent = bounds.width() + mDetailsLabelSpacing;
        LabelIndentSpan.appendNumberedList(sb, labels, steps, indent, "\n");
        mContactInformation.setText(sb, TextView.BufferType.SPANNABLE);

        // Add conversation's json representation
        Conversation conversation = ConversationUtils.getConversation(mContactId);
        if (conversation != null) {
            try {
                mConversationInformation.setText(new JSONConversationAdapter().adapt(conversation).toString(3));
            }
            catch (JSONException e) {
                // could not format conversation's json, ignore as here it is debug information
            }
        }
    }

    private void loadContactAvatar() {
        if (mPhotoUri == null) {
            return;
        }

        final boolean hasContactAvatar = AvatarProvider.getAvatarUrl(mPhotoUri) != null;
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getActivity();
                final int dimension = Math.min(mDisplaySize.x, mDisplaySize.y) / 2;
                final Uri photoUri = mPhotoUri
                        .buildUpon()
                        .appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE,
                                String.valueOf(dimension))
                        .build();
                final Bitmap bitmap = AvatarUtils.getConversationAvatar(activity, photoUri);

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAvatarLoaded = true;
                            if (bitmap != null) {
                                mContactAvatar.setImageBitmap(bitmap);
                                if (hasContactAvatar) {
                                    mContactAvatar.startAnimation(
                                            AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
                                }
                                adjustScrollToAvatar();
                            }
                        }
                    });
                }
            }
        });
    }

    private void adjustScrollToAvatar() {
        final float aspectRatio = ((float) mContactAvatar.getDrawable().getIntrinsicHeight())
                / ((float) mContactAvatar.getDrawable().getIntrinsicWidth());
        final int imageHeight = (int) (aspectRatio * mDisplaySize.x);

        ViewGroup.LayoutParams params = mContactAvatar.getLayoutParams();
        params.height = imageHeight;
        mContactAvatar.requestLayout();

        if (!mAvatarScrolled) {
            mGroupInformationContainer.post(new Runnable() {

                @Override
                public void run() {
                    mGroupInformationContainer.scrollTo(0, imageHeight / 2);
                }
            });
        }
    }

    private void confirmConversationRekey() {
        AlertDialogFragment dialogFragment = AlertDialogFragment.getInstance(
                R.string.rekey_messaging_sessions,
                R.string.warning_rekey_conversation,
                R.string.dialog_button_cancel,
                R.string.dialog_button_ok,
                null,
                false);
        dialogFragment.setTargetFragment(this, REKEY_CONVERSATION);
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                    .add(dialogFragment, AlertDialogFragment.TAG_ALERT_DIALOG)
                    .commitAllowingStateLoss();
        }
    }

    private static class AxoCommandInBackground extends AsyncTask<String, Void, Integer> {

        private String mCommand;
        private final WeakReference<ContactDetailsFragment> mFragmentReference;

        AxoCommandInBackground(final ContactDetailsFragment fragment) {
            mFragmentReference = new WeakReference<>(fragment);
        }

        @Override
        protected Integer doInBackground(String... commands) {
            long startTime = System.currentTimeMillis();
            byte[] data = null;
            if (commands.length >= 1)
                data = IOUtils.encode(commands[1]);
            mCommand = commands[0];

            byte[][] devices = ZinaMessaging.getIdentityKeys(data);
            final int deviceCount = devices != null ? devices.length : 0;

            int[] code = new int[1];
            ZinaMessaging.zinaCommand(mCommand, data, code);

            devices = ZinaMessaging.getIdentityKeys(data);
            final int updatedDeviceCount = devices != null ? devices.length : 0;

            if (RESCAN_USER_DEVICES.equals(mCommand)
                    && !TextUtils.equals(commands[1], LoadUserInfo.getUuid())
                    && deviceCount != updatedDeviceCount) {
                final List<String> conversations =
                        ConversationUtils.getConversationsWithParticipant(commands[1]);
                final ConversationRepository repository = ConversationUtils.getConversations();
                if (repository != null) {
                    ConversationUtils.updateDeviceData(repository, conversations,
                            data);
                }
            }

            return (int) (System.currentTimeMillis() - startTime);
        }

        @Override
        protected void onPostExecute(Integer time) {
            ContactDetailsFragment fragment = mFragmentReference.get();
            if (fragment != null) {
                fragment.getDevicesInfo();
            }
        }
    }
}

class DeviceAdapter extends RecyclerView.Adapter implements View.OnClickListener,
        View.OnLongClickListener {

    interface OnDeviceItemClickListener {

        void onItemClick(@NonNull View view, @NonNull Object item);

        void onItemLongClick(@NonNull View view, @NonNull Object item);

        void onCallClick(@NonNull View view, @NonNull Object item);

        void onDeleteClick(@NonNull View view, @NonNull Object item);
    }

    class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView mDeviceName;
        private TextView mDeviceFingerprint;
        private TextView mDeviceId;
        private View mCurrentDeviceLabel;
        private View mDeviceActionsContainer;
        private ImageButton mDeleteButton;
        private ImageButton mCallButton;
        private ImageView mVerifiedIndicator;
        private View mIdContainer;

        ViewHolder(View itemView) {
            super(itemView);
            mDeviceName = (TextView) itemView.findViewById(R.id.dev_name);
            mDeviceFingerprint = (TextView) itemView.findViewById(R.id.id_key);
            mDeviceId = (TextView) itemView.findViewById(R.id.dev_id);
            mDeviceActionsContainer = itemView.findViewById(R.id.dev_buttons);
            mDeleteButton = (ImageButton) itemView.findViewById(R.id.delete);
            mCallButton = (ImageButton) itemView.findViewById(R.id.call);
            mVerifiedIndicator = (ImageView) itemView.findViewById(R.id.verify_check);
            mCurrentDeviceLabel = itemView.findViewById(R.id.dev_current);
            mIdContainer = itemView.findViewById(R.id.id_container);
        }

        public void bind(final @NonNull DeviceInfo.DeviceData device, final int position) {
            itemView.setTag(device);

            mDeviceName.setText(device.name);
            mDeviceFingerprint.setText(device.identityKey);
            mDeviceId.setText(device.devId);

            int currentDeviceLabelVisibility = View.GONE;
            int deviceActionsVisibility = View.VISIBLE;
            int deleteButtonVisibility = View.GONE;
            if (TextUtils.equals(mContactId, LoadUserInfo.getUuid())) {
                if (mOwnDevice != null && TextUtils.equals(device.devId, mOwnDevice.devId)) {
                    currentDeviceLabelVisibility = View.VISIBLE;
                    deviceActionsVisibility = View.GONE;
                }
                deleteButtonVisibility = View.VISIBLE;
            }

            mCurrentDeviceLabel.setVisibility(currentDeviceLabelVisibility);

            mDeviceActionsContainer.setVisibility(deviceActionsVisibility);
            mDeleteButton.setVisibility(deleteButtonVisibility);

            switch (device.zrtpVerificationState) {
                case "0":
                    mVerifiedIndicator.setVisibility(View.INVISIBLE);
                    break;
                case "1":
                    mVerifiedIndicator.setImageResource(R.drawable.ic_check_white_24dp);
                    mVerifiedIndicator.setColorFilter(mColorBase);
                    mVerifiedIndicator.setVisibility(View.VISIBLE);
                    break;
                case "2":
                    mVerifiedIndicator.setImageResource(R.drawable.ic_check_green_24dp);
                    mVerifiedIndicator.setColorFilter(mColorGreen);
                    mVerifiedIndicator.setVisibility(View.VISIBLE);
                    break;
            }
            mCallButton.setTag(device);
            mCallButton.setOnClickListener(this);
            mDeleteButton.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (view.getId() == R.id.call) {
                if (mListener != null) {
                    mListener.onCallClick(itemView, itemView.getTag());
                }
            }
            else if (view.getId() == R.id.delete) {
                if (mListener != null) {
                    mListener.onDeleteClick(itemView, itemView.getTag());
                }
            }
        }
    }

    private final LayoutInflater mInflater;
    private int mColorBase;
    private int mColorGreen;

    private String mContactId;
    private DeviceInfo.DeviceData mOwnDevice;
    private List<DeviceInfo.DeviceData> mDevices;

    private OnDeviceItemClickListener mListener;

    DeviceAdapter(Context context, String uuid, List<DeviceInfo.DeviceData> devices, DeviceInfo.DeviceData ownDevice) {
        mInflater = LayoutInflater.from(context);
        mDevices = devices;
        mOwnDevice = ownDevice;
        mContactId = uuid;

        mColorBase = ViewUtil.getColorFromAttributeId(context, R.attr.sp_activity_primary_text_color);
        mColorGreen = ContextCompat.getColor(context, R.color.sc_ng_text_green);
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int position) {
        View view = mInflater.inflate(R.layout.contact_info_device, viewGroup, false);
        view.setOnClickListener(this);
        view.setLongClickable(true);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        ((ViewHolder) viewHolder).bind((DeviceInfo.DeviceData) getItem(position), position);
    }

    @Override
    public int getItemCount() {
        return mDevices == null ? 0 : mDevices.size();
    }

    @Override
    public void onClick(View view) {
        if (mListener != null && view != null) {
            mListener.onItemClick(view, view.getTag());
        }
    }

    @Override
    public boolean onLongClick(View view) {
        boolean result = false;
        if (mListener != null && view != null) {
            mListener.onItemLongClick(view, view.getTag());
            result = true;
        }
        return result;
    }

    public void setOnItemClickListener(OnDeviceItemClickListener listener) {
        mListener = listener;
    }

    public void setItems(final List<DeviceInfo.DeviceData> devices) {
        mDevices = devices;
        notifyDataSetChanged();
    }

    public Object getItem(int position) {
        return (mDevices != null && position >= 0 && position < mDevices.size())
                ? mDevices.get(position) : null;
    }
}