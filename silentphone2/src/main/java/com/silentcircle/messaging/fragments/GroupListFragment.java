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
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.messaging.activities.GroupManagementActivity;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.silentphone2.R;

import java.util.List;

/**
 * List of groups user has joined.
 */
public class GroupListFragment extends Fragment implements View.OnClickListener,
        GroupManagementActivity.OnWizardStateChangeListener {

    public static final String TAG_GROUP_LIST_FRAGMENT = "com.silentcircle.messaging.fragments.GroupList";

    protected GroupManagementActivity mParent;

    protected RecyclerView mRecyclerView;
    protected View mEmptyView;

    protected GroupMemberAdapter mAdapter;

    protected List<ConversationUtils.GroupData> mGroups;

    public static GroupListFragment newInstance(Bundle args) {
        GroupListFragment fragment = new GroupListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            mParent.finish();
            return;
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
        final View rootView = inflater.inflate(R.layout.fragment_group_list, container, false);
        if (rootView == null) {
            return null;
        }

        mAdapter = new GroupMemberAdapter(mParent);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());

        mRecyclerView = (RecyclerView) rootView.findViewById(android.R.id.list);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setHasFixedSize(true);

        mEmptyView = rootView.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(mEmptyView, R.drawable.empty_call_log,
                R.string.group_messaging_no_known_groups, getResources());

        mParent.setTitle(getString(R.string.group_messaging_title_known_groups));
        mParent.setSubtitle("");

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGroupData(SilentPhoneApplication.getAppContext());
    }

    @Override
    public boolean onExitView() {
        return false;
    }

    @Override
    public void onVisibilityRestored() {
        mParent.setTitle(getString(R.string.group_messaging_title_known_groups));
        mParent.setSubtitle("");
    }

    @Override
    public void onClick(View view) {
        Context context = mParent;
        Intent intent = new Intent(context, GroupManagementActivity.class);
        intent.putExtra(GroupManagementActivity.GROUP_ID, String.valueOf(view.getTag()));
        intent.putExtra(GroupManagementActivity.TASK, GroupManagementActivity.TASK_DETAILS);
        context.startActivity(intent);
    }

    private void refreshGroupData(final Context context) {
        mGroups = ConversationUtils.getGroups();

        boolean isGroupListPopulated = (mGroups != null && mGroups.size() > 0);
        mEmptyView.setVisibility(isGroupListPopulated ? View.GONE : View.VISIBLE);
        mRecyclerView.setVisibility(isGroupListPopulated ? View.VISIBLE : View.GONE);

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private class GroupMemberAdapter extends RecyclerView.Adapter {

        public class ViewHolder extends RecyclerView.ViewHolder {

            private View mRootView;
            private TextView mTextViewName;
            private TextView mTextViewDescription;
            private TextView mTextViewGroupId;
            private TextView mTextViewMemberCount;

            public ViewHolder(View itemView) {
                super(itemView);
                mRootView = itemView;
                mTextViewName = (TextView) itemView.findViewById(R.id.name);
                mTextViewDescription = (TextView) itemView.findViewById(R.id.description);
                mTextViewGroupId = (TextView) itemView.findViewById(R.id.group_id);
                mTextViewMemberCount = (TextView) itemView.findViewById(R.id.member_count);
            }

            public void bind(ConversationUtils.GroupData group, int position) {
                if (group == null) {
                    return;
                }
                mRootView.setTag(group.getGroupId());
                mTextViewName.setText(group.getGroupName());
                mTextViewDescription.setText(group.getGroupDescription());
                mTextViewGroupId.setText(group.getGroupId());
                mTextViewMemberCount.setText(getString(R.string.group_messaging_member_count,
                        group.getMemberCount(), group.getGroupMaxMembers()));
            }
        }

        private final Context mContext;
        private final LayoutInflater mInflater;

        public GroupMemberAdapter(Context context) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
        }

        public Object getItem(int position) {
            return (mGroups != null && position >= 0 && position < mGroups.size())
                    ? mGroups.get(position) : null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = mInflater.inflate(R.layout.messaging_group_item, parent, false);
            view.setOnClickListener(GroupListFragment.this);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ConversationUtils.GroupData group = (ConversationUtils.GroupData) getItem(position);
            final ViewHolder viewHolder = (ViewHolder) holder;
            viewHolder.bind(group, position);
        }

        @Override
        public int getItemCount() {
            return mGroups != null ? mGroups.size() : 0;
        }
    }

}
