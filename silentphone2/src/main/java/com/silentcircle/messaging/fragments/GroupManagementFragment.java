/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.util.Pair;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.common.util.CallUtils;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.StringUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.common.widget.LabelIndentSpan;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.GroupManagementActivity;
import com.silentcircle.messaging.listener.LaunchConfirmDialogOnClick;
import com.silentcircle.messaging.listener.MessagingBroadcastReceiver;
import com.silentcircle.messaging.listener.OnConfirmListener;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.providers.PictureProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.views.AvatarActionsDialog;
import com.silentcircle.messaging.views.ObservableScrollView;
import com.silentcircle.messaging.views.SwipeRevealLayout;
import com.silentcircle.messaging.views.ViewBinderHelper;
import com.silentcircle.messaging.views.adapters.PaddedDividerItemDecoration;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.fragments.SettingsFragment;
import com.silentcircle.silentphone2.passcode.AppLifecycleNotifier;
import com.silentcircle.silentphone2.services.TiviPhoneService;
import com.silentcircle.silentphone2.util.Utilities;
import com.silentcircle.userinfo.LoadUserInfo;
import com.silentcircle.userinfo.activities.AvatarCropActivity;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.Format;
import java.util.ArrayList;
import java.util.Date;

import zina.JsonStrings;
import zina.ZinaNative;

/**
 * Group management fragment, lists group users, allows to leave group.
 */
public class GroupManagementFragment extends BaseFragment implements View.OnClickListener,
        GroupManagementActivity.OnWizardStateChangeListener,
        com.silentcircle.messaging.views.adapters.GroupMemberAdapter.OnGroupMemberItemClickListener,
        ObservableScrollView.OnScrollChangedListener {

    private static final String TAG = GroupManagementFragment.class.getSimpleName();

    /* Priority for this view to handle message broadcasts, higher than ConversationActivity. */
    private static final int MESSAGE_PRIORITY = 3;
    /* Maximum length of group name */
    public static final int GROUP_NAME_MAX_LENGTH = 50;

    public static final String TAG_GROUP_MANAGEMENT_FRAGMENT = "com.silentcircle.messaging.fragments.GroupManagement";

    private static final String AVATAR_IMAGE_CAPTURE_URI =
            "com.silentcircle.messaging.fragments.GroupManagementFragment.AVATAR_IMAGE_CAPTURE_URI";
    private static final String FLAG_AVATAR_SCROLLED =
            "com.silentcircle.messaging.fragments.GroupManagementFragment.AVATAR_SCROLLED";
    private static final String SCROLL_POSITION =
            "com.silentcircle.messaging.fragments.GroupManagementFragment.SCROLL_POSITION";

    private static final Format DATE_FORMAT =
            android.text.format.DateFormat.getDateFormat(SilentPhoneApplication.getAppContext());;

    private static final int GALLERY_IMAGE_FOR_AVATAR = 10098;
    private static final int CAPTURED_IMAGE_FOR_AVATAR = 10099;
    private static final int CROPPED_IMAGE_FOR_AVATAR = 10199;
    private static final int RESULT_ADD_CONTACT = 20100;

    private static final int AVATAR_FADE_IN_DURATION = 200;

    public static final int PERMISSION_CAMERA = 1;

    protected GroupManagementActivity mParent;

    protected RecyclerView mRecyclerView;
    protected View mEmptyView;
    protected Button mButtonSync;
    protected Button mButtonLeave;
    protected TextView mGroupInformation;
    protected TextView mGroupTitle;
    protected TextView mGroupCreator;
    protected TextView mGroupDescription;
    protected ImageView mGroupAvatar;
    protected ViewGroup mGroupAvatarContainer;
    protected ObservableScrollView mGroupInformationContainer;
    protected View mGroupInformationEditorContainer;
    protected EditText mEditGroupName;
    // protected EditText mEditGroupMaxMembers;
    protected Button mButtonCancel;
    protected Button mButtonSave;
    protected ImageButton mButtonEditGroupTitle;
    protected ImageButton mButtonEditGroupAvatar;

    protected GroupMemberAdapter mAdapter;

    protected String mGroupId;
    protected String mGroupDisplayName;
    protected ArrayList<ConversationUtils.MemberData> mGroupMembers = new ArrayList<>();
    protected ConversationUtils.GroupData mGroupData;
    protected boolean mCloseOnLeaveGroup;
    protected boolean mAvatarLoaded;
    protected boolean mAvatarScrolled;
    protected int mScrollPosition;

    private Uri mPendingImageCaptureUri;

    protected int mDetailsLabelSpacing;

    protected Point mDisplaySize = new Point();

    TextView.OnEditorActionListener mMemberCountEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                updateGroup();
                showEditGroupView(false);
                return true;
            }
            return false;
        }
    };

    private MessagingBroadcastReceiver mViewUpdater = new MessagingBroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Action action = Action.from(intent);
            if (Action.REFRESH_SELF.equals(action)) {
                mAvatarLoaded = false;
                refreshGroupData();
            }
            else if (Action.CLOSE_CONVERSATION.equals(action)) {
                if (!ConversationUtils.isGroupKnown(mGroupId)) {
                    Log.w(TAG, "Group conversation not valid, exiting.");
                    if (mParent != null) {
                        Toast.makeText(mParent,
                                mParent.getString(R.string.group_messaging_leaving_group_unknown),
                                Toast.LENGTH_SHORT).show();

                        exitToDialerActivity(mParent);
                    }
                }
            }
        }
    };

    public static GroupManagementFragment newInstance(Bundle args) {
        GroupManagementFragment fragment = new GroupManagementFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupManagementFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
        }

        Display display = mParent.getWindowManager().getDefaultDisplay();
        display.getSize(mDisplaySize);

        setGroup(args.getString(GroupManagementActivity.GROUP_ID));
        if (TextUtils.isEmpty(mGroupId)) {
            Toast.makeText(mParent, "Group id empty", Toast.LENGTH_LONG).show();
        }
        mCloseOnLeaveGroup = args.getBoolean(GroupManagementActivity.CLOSE_ON_LEAVE_GROUP, false);

        Resources resources = getResources();
        mDetailsLabelSpacing = resources.getDimensionPixelSize(R.dimen.spacing_large);
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
            mParent = (GroupManagementActivity) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException("Activity must be GroupManagementActivity.");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mParent = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // retrieve avatar image if set
        if (savedInstanceState != null) {
            mPendingImageCaptureUri = savedInstanceState.getParcelable(AVATAR_IMAGE_CAPTURE_URI);
            mAvatarScrolled = savedInstanceState.getBoolean(FLAG_AVATAR_SCROLLED);
            mScrollPosition = savedInstanceState.getInt(SCROLL_POSITION);
        }

        return inflater.inflate(R.layout.fragment_group_details, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAdapter = new GroupMemberAdapter(mParent);
        mAdapter.setOnItemClickListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());

        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.addItemDecoration(new PaddedDividerItemDecoration(mParent));
        mRecyclerView.setNestedScrollingEnabled(false);

        mButtonSync = (Button) view.findViewById(R.id.button_sync);
        mButtonSync.setOnClickListener(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Drawable drawable = ViewUtil.getTintedCompatDrawable(getActivity(),
                    R.drawable.ic_sync_white_24dp,
                    R.attr.sp_activity_primary_text_color);
            mButtonSync.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }

        mButtonLeave = (Button) view.findViewById(R.id.button_leave);
        mButtonLeave.setOnClickListener(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Drawable drawable = ViewUtil.getTintedCompatDrawable(getActivity(),
                    R.drawable.ic_exit_to_app_white_24dp,
                    R.attr.sp_activity_primary_text_color);
            mButtonLeave.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
        }

        boolean developer = false;
        if(mParent != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
            developer = prefs.getBoolean(SettingsFragment.DEVELOPER, false);
        }
        if (developer) {
            View groupDetailsContainer = view.findViewById(R.id.group_details_container);
            groupDetailsContainer.setVisibility(View.VISIBLE);
        }
        mGroupInformation = (TextView) view.findViewById(R.id.text_group_information);

        mGroupInformationContainer = (ObservableScrollView) view.findViewById(R.id.group_details_main_container);
        mGroupInformationContainer.setOnScrollChangedListener(this);
        mGroupInformationEditorContainer = view.findViewById(R.id.group_details_edit_container);

        mGroupTitle = (TextView) view.findViewById(R.id.text_group_title);
        mGroupDescription = (TextView) view.findViewById(R.id.text_group_description);
        mGroupCreator = (TextView) view.findViewById(R.id.text_group_creator);
        mGroupTitle.setOnClickListener(this);

        mGroupAvatar = (ImageView) view.findViewById(R.id.group_avatar);
        mGroupAvatar.setOnClickListener(this);
        mGroupAvatarContainer = (ViewGroup) view.findViewById(R.id.group_avatar_container);
        final ConversationUtils.GroupData groupData = ConversationUtils.getGroup(mGroupId);
        final String avatarInfo = groupData != null ? groupData.getAvatarInfo() : null;
        final boolean isGeneratedAvatar = TextUtils.isEmpty(avatarInfo)
                || AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo);
        // Show the placeholder if the avatar is not currently generated
        if (!isGeneratedAvatar) {
            mGroupAvatar.setVisibility(View.VISIBLE);
        }

        mEditGroupName = (EditText) view.findViewById(R.id.edit_group_name);
        mEditGroupName.setOnEditorActionListener(mMemberCountEditorActionListener);
        /*
        mEditGroupMaxMembers = (EditText) view.findViewById(R.id.edit_group_max_members);
        mEditGroupMaxMembers.setOnEditorActionListener(mMemberCountEditorActionListener);
         */
        mButtonEditGroupTitle = (ImageButton) view.findViewById(R.id.edit_group_title);
        mButtonEditGroupTitle.setOnClickListener(this);

        mButtonEditGroupAvatar = (ImageButton) view.findViewById(R.id.edit_group_avatar);
        mButtonEditGroupAvatar.setOnClickListener(this);

        mButtonCancel = (Button) view.findViewById(R.id.button_cancel);
        mButtonSave = (Button) view.findViewById(R.id.button_save);
        mButtonCancel.setOnClickListener(this);
        mButtonSave.setOnClickListener(this);

        mEmptyView = view.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(mEmptyView, R.drawable.empty_call_log,
                R.string.group_messaging_group_empty, getResources());

        adjustScrollToAvatar();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        /*
        inflater.inflate(R.menu.menu_group_management, menu);
        super.onCreateOptionsMenu(menu, inflater);

        Context context = getActivity();
        if (context != null) {
            ViewUtil.tintMenuIcons(context, menu);
        }
         */
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGroupData();
        registerReceiver();
    }

    @Override
    public void onPause() {
        if (mAdapter != null) {
            mAdapter.stopRequestProcessing();
        }
        unregisterMessagingReceiver(mViewUpdater);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mAdapter != null) {
            mAdapter.stopRequestProcessing();
        }

        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(AVATAR_IMAGE_CAPTURE_URI, mPendingImageCaptureUri);
        outState.putBoolean(FLAG_AVATAR_SCROLLED, mAvatarScrolled);
        outState.putInt(SCROLL_POSITION, mScrollPosition);
    }

    @Override
    public boolean onExitView() {
        boolean result = false;
        if (mGroupInformationEditorContainer.getVisibility() == View.VISIBLE) {
            showEditGroupView(false);
            result = true;
        }
        return result;
    }

    @Override
    public void onVisibilityRestored() {
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_leave:
                Context context = getActivity();
                if (context == null) {
                    return;
                }

                new LaunchConfirmDialogOnClick(R.string.are_you_sure,
                        R.string.warning_leave_group_conversation,
                        new OnConfirmListener() {
                            @Override
                            public void onConfirm(Context context, int which) {
                                leaveGroup();
                            }
                        }).show(context);
                break;
            case R.id.button_cancel:
                showEditGroupView(false);
                break;
            case R.id.button_save:
                if (updateGroup()) {
                    showEditGroupView(false);
                }
                break;
            case R.id.text_group_title:
            case R.id.edit_group_title:
                showEditGroupView(true);
                break;
            case R.id.group_avatar:
            case R.id.edit_group_avatar:
                // allow to choose image action
                if (!Utilities.isNetworkConnected(mParent)) {
                    InfoMsgDialogFragment.showDialog(mParent, R.string.no_internet,
                            R.string.connected_to_network, android.R.string.ok, -1);
                }
                else {
                    showAvatarActionDialog(mParent);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = false;
        switch (item.getItemId()) {
            case R.id.edit_group:
                showEditGroupView(true);
                result = true;
                break;
            default:
                break;
        }
        return result || super.onOptionsItemSelected(item);
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
                    cropImageIntent.putExtra(
                            com.silentcircle.userinfo.activities.AvatarCropActivity.FLAG_RESIZE_TO_DEFAULT_SIZE, false);
                    startActivityForResult(cropImageIntent, CROPPED_IMAGE_FOR_AVATAR);
                } catch (IOException e) {
                    // Failed to set avatar
                }
            } else if (requestCode == CROPPED_IMAGE_FOR_AVATAR) {
                Uri selectedImageUri = data.getData();
                handleAvatarImageSelection(selectedImageUri);
            } else if (requestCode == RESULT_ADD_CONTACT) {
                // refresh items to hide add-to-contacts button if necessary
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }
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

    @Override
    public void onGroupMemberAddToContactsClick(View view, View parentView, int position, @NonNull Object item) {
        ConversationUtils.MemberData groupMember = (ConversationUtils.MemberData) item;
        ContactEntry contactEntry = ContactsCache.getContactEntry(groupMember.getMemberId());

        byte[] avatar = null;
        String name = null;
        if (contactEntry != null) {
            name = contactEntry.name;
            Uri uri = contactEntry.photoUri;
            if (uri != null) {
                uri = uri.buildUpon()
                        .appendQueryParameter(AvatarProvider.PARAM_AVATAR_SIZE,
                                String.valueOf(AvatarProvider.LOADED_AVATAR_SIZE))
                        .build();
                avatar = AvatarUtils.getConversationAvatarAsByteArray(getActivity(), uri);
            }
        }

        String id = groupMember.getMemberId();
        if ((TextUtils.isEmpty(id) || Utilities.isUuid(id))
                && !TextUtils.isEmpty(contactEntry.alias)) {
            id = contactEntry.alias;
        }
        addToContact(id, name, avatar);
    }

    @Override
    public void onGroupMemberDeleteClick(View view, View parentView, int position, @NonNull Object item) {
        // do nothing
    }

    @Override
    public void onGroupMemberCallClick(View view, View parentView, int position, @NonNull Object item) {
        ConversationUtils.MemberData groupMember = (ConversationUtils.MemberData) item;
        CallUtils.checkAndLaunchSilentPhoneCall(mParent, groupMember.getMemberId());
    }

    @Override
    public void onItemClick(@NonNull View view, int position, @NonNull Object item) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // open conversation with this user
        ConversationUtils.MemberData groupMember = (ConversationUtils.MemberData) item;
        if (groupMember.getMemberId().equals(LoadUserInfo.getUuid())) {
            return;
        }

        Intent intent = ContactsUtils.getMessagingIntent(groupMember.getMemberId(), activity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }

    @Override
    public void onItemLongClick(@NonNull View view, int position, @NonNull Object item) {
        // do nothing
    }

    @Override
    public void onScrollChanged(int deltaX, int deltaY) {
        mAvatarScrolled = true;

        int scrollY = mGroupInformationContainer.getScrollY();
        mGroupAvatar.setTranslationY(scrollY * 0.5f);

        // slide edit avatar button
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mButtonEditGroupAvatar.getLayoutParams();
        params.topMargin = Math.min(scrollY, mGroupAvatar.getHeight() - mGroupAvatar.getHeight() / 4);
        mButtonEditGroupAvatar.requestLayout();

        if (mAdapter != null) {
            mAdapter.clearSelectedPosition();
        }
    }

    public void setGroup(String group) {
        mGroupId = group;
    }

    public void setCloseOnLeaveGroup(boolean closeOnLeaveGroup) {
        mCloseOnLeaveGroup = closeOnLeaveGroup;
    }

    private void registerReceiver() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        /* register for REFRESH_SELF, CLOSE_CONVERSATION events */
        IntentFilter filter = Action.filter(Action.REFRESH_SELF, Action.CLOSE_CONVERSATION);
        registerMessagingReceiver(activity, mViewUpdater, filter, MESSAGE_PRIORITY);
    }

    private void refreshGroupData() {
        Conversation conversation = ConversationUtils.getConversation(mGroupId);
        mGroupData = ConversationUtils.getGroup(mGroupId);

        if (mGroupData != null) {
            formatGroupData(mGroupData);

            mGroupDisplayName = conversation != null
                    ? conversation.getPartner().getDisplayName()
                    : mGroupData.getGroupName();
            mParent.setTitle(R.string.group_messaging_group_members);
            mParent.setSubtitle(mGroupData.getGroupDescription());

            mGroupTitle.setText(mGroupDisplayName);
            mGroupDescription.setText(mGroupData.getGroupDescription());
            mGroupCreator.setText(MessageUtils.getDisplayName(mGroupData.getGroupOwner()));

            mEditGroupName.setText(mGroupDisplayName);
            mEditGroupName.setFilters(new InputFilter[] {new InputFilter.LengthFilter(GROUP_NAME_MAX_LENGTH)});
            /*
            mEditGroupMaxMembers.setText(String.valueOf(mGroupData.getGroupMaxMembers()));
             */
            if (!mAvatarLoaded) {
                loadGroupAvatar();
            }

            ConversationUtils.verifyConversationAvatar(SilentPhoneApplication.getAppContext(),
                    mGroupId, mGroupData.getAvatarInfo());
        }

        final int[] code = new int[1];
        byte[][] groupMembers = ZinaMessaging.getAllGroupMembers(mGroupId, code);
        if (groupMembers != null) {
            Gson gson = new Gson();
            mGroupMembers.clear();
            for (byte[] member : groupMembers) {
                ConversationUtils.MemberData memberData = gson.fromJson(new String(member),
                        ConversationUtils.MemberData.class);
                mGroupMembers.add(memberData);
            }
        }

        mEmptyView.setVisibility(mGroupMembers.size() > 0 ? View.GONE : View.VISIBLE);
        mRecyclerView.setVisibility(mGroupMembers.size() > 0 ? View.VISIBLE : View.GONE);

        if (mAdapter != null) {
            mAdapter.setItems(mGroupMembers);
            mAdapter.notifyDataSetChanged();
        }
        if (mParent != null) {
            mParent.invalidateOptionsMenu();
        }
    }

    private void showEditGroupView(boolean show) {
        if (show) {
            mParent.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            mParent.setTitle(R.string.group_messaging_edit_group_details);
            mParent.setSubtitle("");
            mEditGroupName.selectAll();
            mEditGroupName.requestFocus();
            mEditGroupName.post(new Runnable() {
                @Override
                public void run() {
                    DialerUtils.showInputMethod(mEditGroupName);
                }
            });

            /*
            mEditGroupMaxMembers.selectAll();
            mEditGroupMaxMembers.requestFocus();
            mEditGroupMaxMembers.post(new Runnable() {
                @Override
                public void run() {
                    DialerUtils.showInputMethod(mEditGroupMaxMembers);
                }
            });
             */
            mScrollPosition = mGroupInformationContainer.getScrollY();
        }
        else {
            mParent.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            DialerUtils.hideInputMethod(mEditGroupName);
            refreshGroupData();
            mGroupInformationContainer.post(new Runnable() {
                @Override
                public void run() {
                    mGroupInformationContainer.scrollTo(
                            mGroupInformationContainer.getScrollX(), mScrollPosition);
                }
            });
        }
        mGroupInformationContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        mGroupInformationEditorContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void handleAvatarImageSelection(@Nullable Uri selectedImageUri) {
        String base64 = null;
        if (selectedImageUri != null) {
            base64 = AttachmentUtils.getFileAsBase64String(mParent, selectedImageUri);
        }

        if (!TextUtils.isEmpty(base64)) {
            Context context = SilentPhoneApplication.getAppContext();
            ConversationUtils.setConversationAvatar(context, mGroupId,
                    AvatarProvider.AVATAR_TYPE_DEFAULT, base64);
            loadGroupAvatar();
            // reset photo manager to display correct image in other views
            ContactPhotoManagerNew.getInstance(context).refreshCache();

            InfoEvent event = createAvatarChangeEvent(mGroupId, InfoEvent.INFO_NEW_AVATAR,
                    "You updated group avatar");

            Intent serviceIntent = Action.UPLOAD.intent(SilentPhoneApplication.getAppContext(),
                    SCloudService.class);
            Extra.PARTNER.to(serviceIntent, mGroupId);
            if (event != null) {
                Extra.ID.to(serviceIntent, event.getId());
            }
            serviceIntent.putExtra("IS_UNIQUE", true);
            serviceIntent.putExtra(SCloudService.FLAG_GROUP_AVATAR, true);
            serviceIntent.setData(selectedImageUri);
            SilentPhoneApplication.getAppContext().startService(serviceIntent);

            MessageUtils.requestRefresh(mGroupId);
        }
    }

    private void handleAvatarGeneration() {
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                int result = ZinaNative.setGroupAvatar(mGroupId,
                        IOUtils.encode(AvatarProvider.AVATAR_TYPE_GENERATED));
                if (result == MessageErrorCodes.SUCCESS) {
                    AvatarUtils.setGeneratedGroupAvatar(mParent, mGroupId);
                    // clear photo cache to see the result for updated avatar
                    ContactPhotoManagerNew.getInstance(mParent).refreshCache();
                    // load avatar in this view
                    loadGroupAvatar();

                    createAvatarChangeEvent(mGroupId, InfoEvent.INFO_AVATAR_REMOVED,
                            "You removed group avatar");

                    ConversationUtils.applyGroupChangeSet(mParent, mGroupId);
                } else {
                    Log.w(TAG, "Failed to update group's avatar info.");

                    Toast.makeText(mParent,
                            mParent.getString(R.string.group_messaging_edit_group_avatar_failed)
                                    + ": " + ZinaNative.getErrorInfo()
                                    + " (" + ZinaNative.getErrorCode() + ")",
                            Toast.LENGTH_SHORT).show();
                }

                MessageUtils.requestRefresh(mGroupId);
            }
        });
    }

    @Nullable
    private InfoEvent createAvatarChangeEvent(@NonNull String groupId, int tag, String text) {
        InfoEvent result = null;
        CharSequence displayName = LoadUserInfo.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = LoadUserInfo.getDisplayAlias();
        }
        String details = StringUtils.jsonFromPairs(
                new Pair<String, Object>(JsonStrings.MEMBER_ID, LoadUserInfo.getUuid()),
                new Pair<String, Object>(JsonStrings.MSG_DISPLAY_NAME, displayName));
        InfoEvent event = MessageUtils.createInfoEvent(groupId, tag, text, details);
        ConversationRepository repository = ConversationUtils.getConversations();
        if (repository != null) {
            Conversation conversation = repository.findByPartner(groupId);
            if (conversation != null) {
                repository.historyOf(conversation).save(event);
                result = event;
                conversation.setLastModified(System.currentTimeMillis());
                repository.save(conversation);
            }
        }
        return result;
    }

    private boolean updateGroup() {
        boolean updateResult = true;
        CharSequence groupName = mEditGroupName.getText();
        if (TextUtils.isEmpty(groupName)) {
            Log.d(TAG, "Group name cannot be empty on update");
            Toast.makeText(mParent,
                    mParent.getString(R.string.group_messaging_edit_group_name_empty),
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        if (!TextUtils.equals(groupName, mGroupData != null ? mGroupData.getGroupName() : "")) {
            int result = ZinaNative.setGroupName(mGroupId, IOUtils.encode(groupName.toString()));
            if (result == MessageErrorCodes.SUCCESS) {
                CharSequence displayName = LoadUserInfo.getDisplayName();
                if (TextUtils.isEmpty(displayName)) {
                    displayName = LoadUserInfo.getDisplayAlias();
                }
                ConversationUtils.updateGroupConversationName(
                        mGroupId, groupName.toString(), LoadUserInfo.getUuid(), displayName);

                ConversationUtils.applyGroupChangeSet(mParent, mGroupId);
            }
            else {
                updateResult = false;
                Log.d(TAG, "Failed to update group name: error code: "
                        + ZinaNative.getErrorCode() + ", info: " + ZinaNative.getErrorInfo());
                Toast.makeText(mParent,
                        mParent.getString(R.string.group_messaging_edit_group_name_failed) + ": "
                                + ZinaNative.getErrorInfo() + " (" + ZinaNative.getErrorCode() + ")",
                        Toast.LENGTH_SHORT).show();
            }
        }
        /*
        CharSequence maxMembers = mEditGroupMaxMembers.getText();
        if (!TextUtils.isEmpty(maxMembers)) {
            try {
                int count = Integer.parseInt(maxMembers.toString());
                if (count < CreateGroupFragment.MIN_GROUP_MEMBERS) {
                    updateResult = false;
                    Log.e(TAG, "Could not update group size: minimum number or participants is "
                            + CreateGroupFragment.MIN_GROUP_MEMBERS);
                    Toast.makeText(mParent,
                            R.string.group_messaging_edit_group_members_failed_minimum,
                            Toast.LENGTH_SHORT).show();

                } else if (count > CreateGroupFragment.MAX_GROUP_MEMBERS) {
                    updateResult = false;
                    Log.e(TAG, "Could not update group size: maximum number or participants is "
                            + CreateGroupFragment.MAX_GROUP_MEMBERS);
                    Toast.makeText(mParent,
                            R.string.group_messaging_edit_group_members_failed_maximum,
                            Toast.LENGTH_SHORT).show();
                } else if (mGroupData.getMemberCount() < count) {
                    boolean result = ZinaNative.modifyGroupSize(mGroupId, count);
                    if (!result) {
                        String errorInfo = ZinaNative.getErrorInfo();
                        Log.e(TAG, "Could not update group size: " + errorInfo);
                        Toast.makeText(mParent, errorInfo, Toast.LENGTH_SHORT).show();
                    } else {
                        Log.d(TAG, "Maximum number of group members updated to " + maxMembers);
                        Toast.makeText(mParent,
                                R.string.group_messaging_edit_group_members_updated,
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    updateResult = false;
                    Log.e(TAG, "Could not update group size: group already has more members than "
                            + maxMembers);
                    Toast.makeText(mParent,
                            R.string.group_messaging_edit_group_members_failed_already_more,
                            Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Could not update group size: could not parse " + maxMembers);
                Toast.makeText(mParent,
                        R.string.group_messaging_edit_group_members_failed,
                        Toast.LENGTH_SHORT).show();
            }
        }
         */
        return updateResult;
    }

    private void leaveGroup() {
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                ConversationUtils.deleteConversation(mGroupId);

                if (mParent == null) {
                    return;
                }

                mParent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mParent,
                                mParent.getString(R.string.group_messaging_leaving_group,
                                        mGroupDisplayName),
                                Toast.LENGTH_SHORT).show();

                        if (mCloseOnLeaveGroup) {
                            // try to return to dialer activity and clear conversation and group management screens
                            exitToDialerActivity(mParent);
                        }
                        else {
                            // return to previous view
                            mParent.onBackPressed();
                        }

                        // refresh conversations list so removed conversation does not appear
                        MessageUtils.requestRefresh();
                    }
                });
            }
        });
    }

    private void exitToDialerActivity(@NonNull Context context) {
        Intent intent = new Intent(context, DialerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    private void formatGroupData(ConversationUtils.GroupData groupData) {
        String[] labels = new String[] {"Id", "Description", "Name", "Owner", "Attr",
                "Max members", "Last modified", "Member count", "Burn delay", "Burn mode",
                "Avatar info"};
        String[] steps = new String[] {groupData.getGroupId(),
                groupData.getGroupDescription(),
                groupData.getGroupName(),
                groupData.getGroupOwner(),
                String.valueOf(groupData.getGroupAttribute()),
                String.valueOf(groupData.getGroupMaxMembers()),
                DATE_FORMAT.format(new Date(groupData.getLastModified())),
                String.valueOf(groupData.getMemberCount()),
                String.valueOf(groupData.getBurnTime()),
                String.valueOf(groupData.getBurnMode()),
                groupData.getAvatarInfo()};

        String text = labels[0];
        for (String label : labels) {
            if (label.length() > text.length()) {
                text = label;
            }
        }
        Paint paint = mGroupInformation.getPaint();
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        SpannableStringBuilder sb = new SpannableStringBuilder("");
        int indent = bounds.width() + mDetailsLabelSpacing;
        LabelIndentSpan.appendNumberedList(sb, labels, steps, indent, "\n");
        mGroupInformation.setText(sb, TextView.BufferType.SPANNABLE);
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
                        handleAvatarGeneration();
                    }

                });
        dialog.setCameraButtonEnabled(Utilities.hasCamera());
        dialog.setDeleteButtonEnabled(
                !AvatarProvider.AVATAR_TYPE_GENERATED.equals(mGroupData != null ? mGroupData.getAvatarInfo() : null));
        dialog.show();
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

    private void addToContact(String partnerId, String name, byte[] photoData) {
        Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT, ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);

        ArrayList<ContentValues> data = new ArrayList<>();
        ContentValues contentValues = new ContentValues();
        contentValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
        contentValues.put(ContactsContract.CommonDataKinds.Website.LABEL, partnerId);
        try {
            partnerId = URLEncoder.encode(partnerId, "UTF-8");
        } catch (UnsupportedEncodingException ignore) {}
        contentValues.put(ContactsContract.CommonDataKinds.Website.URL, "silentphone:" + partnerId);
        contentValues.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_OTHER);
        data.add(contentValues);

        if (photoData != null) {
            contentValues = new ContentValues();
            contentValues.put(ContactsContract.Contacts.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
            contentValues.put(ContactsContract.CommonDataKinds.Photo.PHOTO, photoData);
            data.add(contentValues);
        }
        intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data);

        AppLifecycleNotifier.getSharedInstance().onWillStartExternalActivity(true);
        startActivityForResult(intent, RESULT_ADD_CONTACT);
    }

    private void loadGroupAvatar() {
        // TODO load and keep low resolution avatar if available and show that while loading large one
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                final Activity activity = getActivity();
                final int dimension = Math.min(mDisplaySize.x, mDisplaySize.y) / 2;
                final Bitmap bitmap = AvatarUtils.getConversationAvatar(getActivity(),
                        AvatarUtils.getAvatarProviderUriGroup(mGroupId,
                                R.drawable.ic_profile_group_placeholder, dimension));

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final ConversationUtils.GroupData groupData = ConversationUtils.getGroup(mGroupId);
                            final String avatarInfo = groupData != null ? groupData.getAvatarInfo() : null;
                            final boolean isGeneratedAvatar = TextUtils.isEmpty(avatarInfo)
                                    || AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo);

                            mAvatarLoaded = true;
                            if (bitmap != null) {
                                // Not necessary to fade-in generated avatars - they load quickly
                                // If the place holder is visible here, it means the avatar is being generated
                                if (isGeneratedAvatar && mGroupAvatar.getVisibility() != View.VISIBLE) {
                                    mGroupAvatar.setImageDrawable(new BitmapDrawable(activity.getResources(), bitmap));
                                    mGroupAvatar.setVisibility(View.VISIBLE);
                                    mGroupAvatar.startAnimation(
                                            AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
                                } else {
                                    Drawable[] drawables = new Drawable[] {
                                            ContextCompat.getDrawable(activity, R.drawable.ic_profile_group_placeholder),
                                            new BitmapDrawable(activity.getResources(), bitmap)
                                    };
                                    TransitionDrawable transitionDrawable = new TransitionDrawable(drawables);
                                    transitionDrawable.setCrossFadeEnabled(true);
                                    mGroupAvatar.setImageDrawable(transitionDrawable);
                                    transitionDrawable.startTransition(AVATAR_FADE_IN_DURATION);
                                }
                            }
                            adjustScrollToAvatar();
                        }
                    });
                }
            }
        });
    }

    private void adjustScrollToAvatar() {
        final float aspectRatio = ((float) mGroupAvatar.getDrawable().getIntrinsicHeight())
                / ((float) mGroupAvatar.getDrawable().getIntrinsicWidth());
        final int imageHeight = (int) (aspectRatio * mDisplaySize.x);

        ViewGroup.LayoutParams params = mGroupAvatar.getLayoutParams();
        params.height = imageHeight;
        mGroupAvatar.requestLayout();

        if (!mAvatarScrolled) {
            mGroupInformationContainer.post(new Runnable() {

                @Override
                public void run() {
                    mGroupInformationContainer.scrollTo(0, imageHeight / 2);
                }
            });
        }
    }

    private static class GroupMemberAdapter extends com.silentcircle.messaging.views.adapters.GroupMemberAdapter<ConversationUtils.MemberData> {

        class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

            private View mMemberContainer;
            private TextView mMemberName;
            private TextView mMemberAlias;
            private TextView mLastModified;
            private QuickContactBadge mContactBadge;
            private ImageButton mButtonAddToContacts;
            private ImageButton mButtonRemove;
            private ImageButton mButtonCall;

            private SwipeRevealLayout mSwipeLayout;

            ViewHolder(View itemView) {
                super(itemView);
                mMemberContainer = itemView.findViewById(R.id.member_layout);
                mMemberName = (TextView) itemView.findViewById(R.id.name);
                mMemberAlias = (TextView) itemView.findViewById(R.id.alias);
                mLastModified = (TextView) itemView.findViewById(R.id.time);
                mContactBadge = (QuickContactBadge) itemView.findViewById(R.id.quick_contact_photo);
                mButtonAddToContacts =  (ImageButton) itemView.findViewById(R.id.add_to_contacts);
                mButtonRemove = (ImageButton) itemView.findViewById(R.id.remove);
                mButtonCall = (ImageButton) itemView.findViewById(R.id.call);

                mMemberContainer.setOnClickListener(this);
                mButtonAddToContacts.setOnClickListener(this);
                mButtonRemove.setOnClickListener(this);
                mButtonCall.setOnClickListener(this);
                itemView.findViewById(R.id.call_layout).setOnClickListener(this);

                mSwipeLayout = (SwipeRevealLayout) itemView.findViewById(R.id.swipeable_item);;
            }

            public void bind(ConversationUtils.MemberData groupMember, int position) {
                if (groupMember == null) {
                    return;
                }

                itemView.setTag(groupMember);
                itemView.setTag(R.id.position, position);
                mMemberContainer.setTag(groupMember);
                mMemberContainer.setTag(R.id.position, position);

                String memberId = groupMember.getMemberId();
                ContactEntry contact = ContactsCache.getContactEntryFromContacts(memberId);
                mButtonAddToContacts.setVisibility((contact == null || contact.lookupUri == null)
                        && !memberId.equals(LoadUserInfo.getUuid())
                        ? View.VISIBLE : View.GONE);
                mButtonRemove.setVisibility(View.GONE);
                mLastModified.setText(android.text.format.DateUtils.getRelativeTimeSpanString(
                        groupMember.getLastModified(),
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS,
                        android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE));
                mLastModified.setVisibility(View.GONE);

                /* Allow call button only if there are no calls with that user currently */
                mSwipeLayout.setSectLeftEnabled(!TiviPhoneService.calls.hasCallWith(memberId));

                ContactEntry contactEntry = ContactsCache.getContactEntryFromCacheIfExists(memberId);

                if (contactEntry == null) {
                    mMemberName.setText(memberId);
                    mMemberAlias.setVisibility(View.GONE);
                } else {
                    mMemberName.setText(contactEntry.name);

                    String alias = memberId;
                    if (TextUtils.isEmpty(alias) || Utilities.isUuid(alias)) {
                        alias = contactEntry.alias;
                    }
                    if (!TextUtils.isEmpty(alias)
                            && !alias.equals(contactEntry.name)) {
                        mMemberAlias.setText(alias);
                        mMemberAlias.setVisibility(View.VISIBLE);
                    } else {
                        mMemberAlias.setVisibility(View.GONE);
                    }
                }

                AvatarUtils.setPhoto(getPhotoManager(), mContactBadge, contactEntry);
                if (ContactsCache.hasExpired(contactEntry)) {
                    doContactRequest(memberId, position, contactEntry);
                }

                mBinderHelper.bind(mSwipeLayout, memberId);
            }

            @Override
            public void onClick(View view) {
                // close swiped items
                clearSelectedPosition();

                switch (view.getId()) {
                    case R.id.remove:
                        if (mListener != null) {
                            mListener.onGroupMemberDeleteClick(view, itemView,
                                    (Integer) itemView.getTag(R.id.position), itemView.getTag());
                        }
                        break;
                    case R.id.add_to_contacts:
                        if (mListener != null) {
                            mListener.onGroupMemberAddToContactsClick(view, itemView,
                                    (Integer) itemView.getTag(R.id.position), itemView.getTag());
                        }
                        break;
                    case R.id.call:
                    case R.id.call_layout:
                        if (mListener != null) {
                            mListener.onGroupMemberCallClick(view, itemView,
                                    (Integer) itemView.getTag(R.id.position), itemView.getTag());
                        }
                        break;
                    case R.id.member_layout:
                    default:
                        GroupMemberAdapter.this.onClick(view);
                        break;
                }
            }
        }

        private final ViewBinderHelper mBinderHelper;
        private OnGroupMemberItemClickListener mListener;

        GroupMemberAdapter(Context context) {
            super(context);
            mBinderHelper = new ViewBinderHelper();
            mBinderHelper.setOpenOnlyOne(true);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.messaging_swipeable_group_member, parent, false);
            view.setOnClickListener(this);
            view.setLongClickable(true);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ConversationUtils.MemberData groupMember = (ConversationUtils.MemberData) getItem(position);
            final ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bind(groupMember, position);
        }

        public void setOnItemClickListener(OnGroupMemberItemClickListener listener) {
            mListener = listener;
            super.setOnItemClickListener(listener);
        }

        void clearSelectedPosition() {
            mBinderHelper.closeAll();
        }
    }
}
