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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.contacts.ContactsUtils;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.activities.GroupManagementActivity;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.views.adapters.GroupMemberAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.dialogs.InfoMsgDialogFragment;
import com.silentcircle.silentphone2.util.Utilities;

import java.text.Format;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import zina.ZinaNative;

/**
 * Wizard for group creation.
 */
public class CreateGroupFragment extends Fragment implements View.OnClickListener,
        GroupManagementActivity.OnWizardStateChangeListener,
        GroupMemberAdapter.OnGroupMemberItemClickListener {

    private static final String TAG = CreateGroupFragment.class.getSimpleName();

    public static final String TAG_CREATE_GROUP_FRAGMENT = "com.silentcircle.messaging.fragments.CreateGroupFragment";

    public static final int MIN_GROUP_MEMBERS = 3;
    public static final int MAX_GROUP_MEMBERS = 30;

    public static final int MAX_GROUP_NAME_LENGTH = 40;

    private static final Format DATE_FORMAT =
            android.text.format.DateFormat.getDateFormat(SilentPhoneApplication.getAppContext());;

    protected GroupManagementActivity mParent;
    protected EditText mEditGroupName;
    protected EditText mEditGroupDescription;
    protected EditText mEditGroupMaxMembers;
    protected android.support.design.widget.FloatingActionButton mButtonCreate;
    protected TextInputLayout mTextInputGroupName;
    protected TextInputLayout mTextInputGroupDescription;
    protected TextInputLayout mTextInputGroupMaxMembers;
    protected TextView mTextNumberOfParticipants;
    protected ImageButton mButtonAddParticipants;
    protected RecyclerView mRecyclerView;
    protected View mGroupFieldsToggle;
    protected View mGroupFields;
    protected View mProgress;

    protected CharSequence mErrorTextNameEmpty;
    protected CharSequence mErrorTextNameTaken;
    protected CharSequence mErrorTextDescriptionEmpty;
    protected CharSequence mErrorTextMaxMemberLimits;

    protected GroupMemberAdapter<String> mAdapter;
    protected List<String> mGroupMembers = new ArrayList<>();
    protected String mDefaultGroupName;
    protected String mGeneratedGroupName;
    protected String mGeneratedGroupDescription;
    protected String mDefaultGroupDescription;

    TextView.OnEditorActionListener mMemberCountEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                validateAndCreateGroup();
                return true;
            }
            return false;
        }
    };

    public static CreateGroupFragment newInstance(Bundle args) {
        CreateGroupFragment fragment = new CreateGroupFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public CreateGroupFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();

        if (args != null) {
            setGroupMembers(args.getCharSequenceArray(GroupManagementActivity.GROUP_MEMBERS));
            mGeneratedGroupName = args.getString(GroupManagementActivity.GROUP_NAME);
        }

        mErrorTextNameEmpty = getString(R.string.group_messaging_create_group_name_not_empty);
        mErrorTextNameTaken = getString(R.string.group_messaging_create_group_name_already_used);
        mErrorTextDescriptionEmpty =
                getString(R.string.group_messaging_create_group_description_not_empty);
        mErrorTextMaxMemberLimits =
                getString(R.string.group_messaging_create_group_member_count_limits,
                        MIN_GROUP_MEMBERS, MAX_GROUP_MEMBERS);
        mDefaultGroupName = getString(R.string.group_messaging_new_group_conversation);
        mGeneratedGroupDescription = getString(R.string.group_messaging_conversation_with);
        mDefaultGroupDescription = getString(R.string.group_messaging_group_conversation_created);
    }

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_create_group, container, false);
    }

    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mEditGroupName = (EditText) view.findViewById(R.id.edit_group_name);
        mEditGroupName.setOnEditorActionListener(mMemberCountEditorActionListener);
        mEditGroupDescription = (EditText) view.findViewById(R.id.edit_group_description);
        mEditGroupDescription.setOnEditorActionListener(mMemberCountEditorActionListener);
        mEditGroupMaxMembers = (EditText) view.findViewById(R.id.edit_group_max_members);
        mEditGroupMaxMembers.setOnEditorActionListener(mMemberCountEditorActionListener);
        mEditGroupMaxMembers.setText(R.string.group_messaging_create_group_maximum_members_default);

        mTextInputGroupName = (TextInputLayout) view.findViewById(R.id.textinput_group_name);
        mTextInputGroupDescription = (TextInputLayout) view.findViewById(R.id.textinput_group_description);
        mTextInputGroupMaxMembers = (TextInputLayout) view.findViewById(R.id.textinput_group_max_members);

        mButtonCreate = (android.support.design.widget.FloatingActionButton) view.findViewById(R.id.button_create);
        mButtonCreate.setOnClickListener(this);

        mTextNumberOfParticipants = (TextView) view.findViewById(R.id.edit_group_number_participants);

        mGroupFieldsToggle = view.findViewById(R.id.group_fields_toggle);
        mGroupFieldsToggle.setOnClickListener(this);
        mGroupFields = view.findViewById(R.id.group_fields_container);

        mButtonAddParticipants = (ImageButton) view.findViewById(R.id.edit_group_add_participants);
        mButtonAddParticipants.setOnClickListener(this);

        mProgress = view.findViewById(R.id.create_progress);

        if (savedInstanceState != null) {
            CharSequence[] members =
                    savedInstanceState.getCharSequenceArray(GroupManagementActivity.GROUP_MEMBERS);
            addGroupMembers(members);
        }

        mAdapter = new GroupMemberAdapter<>(mParent);
        mAdapter.setItems(mGroupMembers);
        mAdapter.setOnItemClickListener(this);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());

        mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        /*
        inflater.inflate(R.menu.create_group, menu);
        super.onCreateOptionsMenu(menu, inflater);

        Context context = getActivity();
        if (context != null) {
            ViewUtil.tintMenuIcons(context, menu);
        }
         */
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
    public void onResume() {
        super.onResume();
        mEditGroupName.selectAll();
        mEditGroupName.requestFocus();
        mEditGroupName.post(new Runnable() {
            @Override
            public void run() {
                DialerUtils.showInputMethod(mEditGroupName);
            }
        });
        mParent.setTitle(R.string.group_messaging_title_create_group);
        setSelectionListTitle();
        setTitleAndDescriptionIfNecessary();
    }

    @Override
    public void onPause() {
        super.onPause();
        hideKeyboard();
        mAdapter.stopRequestProcessing();
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequenceArray(GroupManagementActivity.GROUP_MEMBERS,
                mGroupMembers.toArray(new CharSequence[mGroupMembers.size()]));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean result = false;
        switch (item.getItemId()) {
            case R.id.invite_user:
                result = true;
                showSearchView();
                break;
            case R.id.create_group:
                result = true;
                // check validity of input and create group
                validateAndCreateGroup();
                break;
            default:
                break;
        }
        return result || super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
/*
            case R.id.button_cancel:
                // leave group creation wizard
                hideKeyboard();
                if (mParent != null) {
                    mParent.onBackPressed();
                }
                break;
 */
            case R.id.button_create:
                // check validity of input and create group
                validateAndCreateGroup();
                break;
            case R.id.edit_group_add_participants:
                showSearchView();
                break;
            case R.id.group_fields_toggle:
                showAdvancedGroupFields();
            default:
                break;
        }
    }

    @Override
    public void onItemClick(@NonNull View view, int position, @NonNull Object item) {
        // not used
    }

    @Override
    public void onItemLongClick(@NonNull View view, int position, @NonNull Object item) {
        // not used
    }

    @Override
    public void onGroupMemberDeleteClick(View view, View parentView, int position, @NonNull Object item) {
        mGroupMembers.remove((String) item);
        mAdapter.notifyDataSetChanged();
        setSelectionListTitle();
    }

    @Override
    public void onGroupMemberAddToContactsClick(View view, View parentView, int position, @NonNull Object item) {
    }

    @Override
    public void onGroupMemberCallClick(View view, View parentView, int position, @NonNull Object item) {
    }

    @Override
    public boolean onExitView() {
        return false;
    }

    @Override
    public void onVisibilityRestored() {
        mParent.setTitle(R.string.group_messaging_title_create_group);
        // mParent.closeActionBarQueryField();
    }

    public void setGroupMembers(@Nullable CharSequence[] members) {
        mGroupMembers.clear();
        addGroupMembers(members);
    }

    public void addGroupMembers(@Nullable CharSequence[] members) {
        if (members != null) {
            for (CharSequence item : members) {
                if (!TextUtils.isEmpty(item)) {
                    String member = item.toString();
                    if (!mGroupMembers.contains(member)) {
                        mGroupMembers.add(member);
                    }
                }
            }
        }

        // update max group size, if selection size is larger than value in edit field
        try {
            if (mEditGroupMaxMembers != null) {
                int participantCount =
                        Math.max(Integer.valueOf(mEditGroupMaxMembers.getText().toString()),
                            mGroupMembers.size());
                mEditGroupMaxMembers.setText(String.valueOf(participantCount));
            }
        } catch (NumberFormatException e) {
            // leave entered value unchanged
        }

        setSelectionListTitle();
    }

    public void setGroupName(String name) {
        mGeneratedGroupName = name;
    }

    public void setProgressEnabled(boolean enabled) {
        if (mProgress != null) {
            mProgress.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private void setSelectionListTitle() {
        if (mTextNumberOfParticipants != null) {
            Resources resources = SilentPhoneApplication.getAppContext().getResources();
            CharSequence text;
            if (mGroupMembers.size() == 0) {
                text = resources.getString(R.string.group_messaging_add_participants);
            }
            else {
                text = resources.getQuantityString(R.plurals.n_participants, mGroupMembers.size(),
                        mGroupMembers.size());
            }
            mTextNumberOfParticipants.setText(text);
        }
    }

    private void setTitleAndDescriptionIfNecessary() {
        if (TextUtils.isEmpty(mEditGroupName.getText())) {
            String groupName = TextUtils.isEmpty(mGeneratedGroupName)
                    ? mDefaultGroupName
                    : mGeneratedGroupName;
            if (!TextUtils.isEmpty(groupName) && groupName.length() > MAX_GROUP_NAME_LENGTH) {
                groupName = groupName.substring(0, MAX_GROUP_NAME_LENGTH) + "...";
            }
            mEditGroupName.setText(groupName);
            mEditGroupName.selectAll();
        }
        if (TextUtils.isEmpty(mEditGroupDescription.getText())) {
            mEditGroupDescription.setText(mDefaultGroupDescription + " "
                    + DATE_FORMAT.format(new Date(System.currentTimeMillis())));
        }
    }

    private void showSearchView() {
        if (!Utilities.isNetworkConnected(mParent)) {
            InfoMsgDialogFragment.showDialog(mParent, R.string.no_internet,
                    R.string.connected_to_network, android.R.string.ok, -1);
            return;
        }

        Context context = mParent;
        Intent intent = new Intent(context, GroupManagementActivity.class);
        intent.putExtra(GroupManagementActivity.TASK,
                GroupManagementActivity.TASK_ADD_PARTICIPANTS);
        context.startActivity(intent);
    }

    private void showAdvancedGroupFields() {
        mGroupFieldsToggle.setVisibility(View.GONE);
        mGroupFields.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        DialerUtils.hideInputMethod(mEditGroupName);
        DialerUtils.hideInputMethod(mEditGroupDescription);
        DialerUtils.hideInputMethod(mEditGroupMaxMembers);
    }

    private void validateAndCreateGroup() {
        String name = mEditGroupName.getText().toString();
        String description = mEditGroupDescription.getText().toString();
        CharSequence maxMembers = mEditGroupMaxMembers.getText();

        mTextInputGroupName.setError("");
        if (TextUtils.isEmpty(name)) {
            mTextInputGroupName.setError(mErrorTextNameEmpty);
            mEditGroupName.requestFocus();
            return;
        }

        mTextInputGroupDescription.setError("");
        if (TextUtils.isEmpty(description)) {
            showAdvancedGroupFields();
            mTextInputGroupDescription.setError(mErrorTextDescriptionEmpty);
            mEditGroupDescription.requestFocus();
            return;
        }

        mTextInputGroupMaxMembers.setError("");
        int count = 0;
        if (!TextUtils.isEmpty(maxMembers)) {
            try {
                count = Integer.parseInt(maxMembers.toString());
            } catch (NumberFormatException e) {
                showAdvancedGroupFields();
                mEditGroupMaxMembers.requestFocus();
                return;
            }
        }

        if (count < MIN_GROUP_MEMBERS || count > MAX_GROUP_MEMBERS) {
            showAdvancedGroupFields();
            mTextInputGroupMaxMembers.setError(mErrorTextMaxMemberLimits);
            mEditGroupMaxMembers.requestFocus();
            return;
        }

        hideKeyboard();

        if (mParent != null) {
            // TODO: create group in a background task, this takes time
            setProgressEnabled(true);
            String groupUuid = ZinaMessaging.createNewGroup(IOUtils.encode(name),
                    IOUtils.encode(description));
            if (!TextUtils.isEmpty(groupUuid)) {
                Conversation conversation = ConversationUtils.getOrCreateGroupConversation(
                        SilentPhoneApplication.getAppContext(), groupUuid);
                if (conversation != null) {
                    final Intent conversationIntent = ContactsUtils.getMessagingIntent(groupUuid,
                            mParent);
                    Extra.DISPLAY_NAME.to(conversationIntent, name);
                    Extra.GROUP.to(conversationIntent, groupUuid);
                    Extra.IS_GROUP.to(conversationIntent, true);
                    // currently user list can be empty as user can be added from chat view
                    if (mGroupMembers.size() > 0) {
                        CharSequence[] members = mGroupMembers.toArray(new CharSequence[mGroupMembers.size()]);
                        Extra.PARTICIPANTS.to(conversationIntent, members);
                    }
                    mParent.startActivity(conversationIntent);
                    mParent.finish();
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
                Toast.makeText(mParent, R.string.group_messaging_create_group_failed + ": "
                        + ZinaNative.getErrorInfo() + " (" + ZinaNative.getErrorCode() + ")",
                        Toast.LENGTH_SHORT).show();
            }
            setProgressEnabled(false);

        }
    }
}
