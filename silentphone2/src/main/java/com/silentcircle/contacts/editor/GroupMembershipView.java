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

/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.silentcircle.contacts.editor;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.silentcircle.contacts.GroupMetaDataLoader;
import com.silentcircle.contacts.interactions.GroupCreationDialogFragment;
import com.silentcircle.contacts.model.RawContactDelta;
import com.silentcircle.contacts.model.RawContactModifier;
import com.silentcircle.contacts.model.dataitem.DataKind;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.GroupMembership;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;

//import com.android.internal.util.Objects;

/**
 * An editor for group membership.  Displays the current group membership list and
 * brings up a dialog to change it.
 */
public class GroupMembershipView extends LinearLayout implements OnClickListener, OnItemClickListener {
    private static final String TAG = "GroupMembershipView";

    private static final int CREATE_NEW_GROUP_GROUP_ID = 133;

    public static final class GroupSelectionItem {
        private final long mGroupId;
        private final String mTitle;
        private boolean mChecked;

        public GroupSelectionItem(long groupId, String title, boolean checked) {
            this.mGroupId = groupId;
            this.mTitle = title;
            mChecked = checked;
        }

        public long getGroupId() {
            return mGroupId;
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void setChecked(boolean checked) {
            mChecked = checked;
        }

        @Override
        public String toString() {
            return mTitle;
        }
    }    
    
    /**
     * Extends the array adapter to show checkmarks on all but the last list item for
     * the group membership popup.  Note that this is highly specific to the fact that the
     * group_membership_list_item.xml is a CheckedTextView object.
     */
    private class GroupMembershipAdapter<T> extends ArrayAdapter<T> {

        public GroupMembershipAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        public boolean getItemIsCheckable(int position) {
            // Item is checkable if it is NOT the last one in the list
            return position != getCount()-1;
        }

        @Override
        public int getItemViewType(int position) {
            return getItemIsCheckable(position) ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View itemView = super.getView(position, convertView, parent);

            // Hide the checkable drawable.  This assumes that the item views
            // are CheckedTextView objects
            final CheckedTextView checkedTextView = (CheckedTextView)itemView;
            if (!getItemIsCheckable(position)) {
                checkedTextView.setCheckMarkDrawable(null);
            }

            return checkedTextView;
        }
    }

    private RawContactDelta mState;
    private Cursor mGroupMetaData;
//    private String mAccountName;
//    private String mAccountType;
    private String mDataSet;
    private TextView mGroupList;
    private GroupMembershipAdapter<GroupSelectionItem> mAdapter;
    private long mDefaultGroupId;
    private long mFavoritesGroupId;
    private ListPopupWindow mPopup;
    private DataKind mKind;
    private boolean mDefaultGroupVisibilityKnown;
    private boolean mDefaultGroupVisible;
    private boolean mCreatedNewGroup;

    private String mNoGroupString;

    private Context mContext;
    private ContactEditorFragment parent;

    public GroupMembershipView(Context context) {
        super(context);
        mContext = context;
    }

    public GroupMembershipView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources resources = mContext.getResources();
        mNoGroupString = mContext.getString(R.string.group_edit_field_hint_text);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mGroupList != null) {
            mGroupList.setEnabled(enabled);
        }
    }

    public void setParent(ContactEditorFragment p) {
        parent = p;
    }

    @SuppressLint("DefaultLocale")
    public void setKind(DataKind kind) {
        mKind = kind;
        TextView kindTitle = (TextView) findViewById(R.id.kind_title);
        kindTitle.setText(getResources().getString(kind.titleRes).toUpperCase());
    }

    public void setGroupMetaData(Cursor groupMetaData) {
        this.mGroupMetaData = groupMetaData;
        updateView();
        // Open up the list of groups if a new group was just created.
        if (mCreatedNewGroup) {
            mCreatedNewGroup = false;
            onClick(this); // This causes the popup to open.
            if (mPopup != null) {
                // Ensure that the newly created group is checked.
                int position = mAdapter.getCount() - 2;
                ListView listView = mPopup.getListView();
                if (!listView.isItemChecked(position)) {
                    // Newly created group is not checked, so check it.
                    listView.setItemChecked(position, true);
                    onItemClick(listView, null, position, listView.getItemIdAtPosition(position));
                }
            }
        }
    }

    public void setState(RawContactDelta state) {
        mState = state;
        mDefaultGroupVisibilityKnown = false;
        mCreatedNewGroup = false;
        updateView();
    }

    private void updateView() {
        if (mGroupMetaData == null || mGroupMetaData.isClosed()) {
            setVisibility(GONE);
            return;
        }
        boolean accountHasGroups = false;
        mFavoritesGroupId = 0;
        mDefaultGroupId = 0;

        StringBuilder sb = new StringBuilder();
        mGroupMetaData.moveToPosition(-1);
        while (mGroupMetaData.moveToNext()) {
            long groupId = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
            if (!mGroupMetaData.isNull(GroupMetaDataLoader.FAVORITES)
                    && mGroupMetaData.getInt(GroupMetaDataLoader.FAVORITES) != 0) {
                mFavoritesGroupId = groupId;
            }
            else if (!mGroupMetaData.isNull(GroupMetaDataLoader.AUTO_ADD)
                    && mGroupMetaData.getInt(GroupMetaDataLoader.AUTO_ADD) != 0) {
                mDefaultGroupId = groupId;
            }
            else {
                accountHasGroups = true;
            }

            // Exclude favorites from the list - they are handled with special UI (star)
            // Also exclude the default group.
            if (groupId != mFavoritesGroupId && groupId != mDefaultGroupId && hasMembership(groupId)) {
                String title = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                if (!TextUtils.isEmpty(title)) {
                    if (sb.length() != 0) {
                        sb.append(", ");
                    }
                    sb.append(title);
                }
            }
        }
        if (!accountHasGroups) {
            setVisibility(GONE);
            return;
        }
        if (mGroupList == null) {
            mGroupList = (TextView)findViewById(R.id.group_list);
            mGroupList.setOnClickListener(this);
        }
        mGroupList.setEnabled(isEnabled());
        if (sb.length() == 0) {
            mGroupList.setText(mNoGroupString);
        }
        else {
            mGroupList.setText(sb);
       }
        setVisibility(VISIBLE);

        if (!mDefaultGroupVisibilityKnown) {
            // Only show the default group (My Contacts) if the contact is NOT in it
            mDefaultGroupVisible = mDefaultGroupId != 0 && !hasMembership(mDefaultGroupId);
            mDefaultGroupVisibilityKnown = true;
        }
    }

    @Override
    public void onClick(View v) {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
            return;
        }

        mAdapter = new GroupMembershipAdapter<GroupSelectionItem>(getContext(), R.layout.group_membership_list_item);

        mGroupMetaData.moveToPosition(-1);
        while (mGroupMetaData.moveToNext()) {
            long groupId = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
            if (groupId != mFavoritesGroupId && (groupId != mDefaultGroupId || mDefaultGroupVisible)) {
                String title = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                boolean checked = hasMembership(groupId);
                mAdapter.add(new GroupSelectionItem(groupId, title, checked));
            }
        }

        mAdapter.add(new GroupSelectionItem(CREATE_NEW_GROUP_GROUP_ID, getContext().getString(R.string.create_group_item_label), 
                false));

        ListView listView;
        mPopup = new ListPopupWindow(getContext(), null);
        mPopup.setAnchorView(mGroupList);
        mPopup.setAdapter(mAdapter);
        mPopup.setModal(true);
        mPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.show();
        listView = mPopup.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setOverScrollMode(OVER_SCROLL_ALWAYS);
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            listView.setItemChecked(i, mAdapter.getItem(i).isChecked());
        }
        listView.setOnItemClickListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListView list = (ListView) parent;
        int count = mAdapter.getCount();

        if (list.isItemChecked(count - 1)) {
            list.setItemChecked(count - 1, false);
            createNewGroup();
            return;
        }

        for (int i = 0; i < count; i++) {
            mAdapter.getItem(i).setChecked(list.isItemChecked(i));
        }

        // First remove the memberships that have been unchecked
        ArrayList<RawContactDelta.ValuesDelta> entries = mState.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (entries != null) {
            for (RawContactDelta.ValuesDelta entry : entries) {
                if (!entry.isDelete()) {
                    Long groupId = entry.getGroupRowId();
                    if (groupId != null && groupId != mFavoritesGroupId
                            && (groupId != mDefaultGroupId || mDefaultGroupVisible)
                            && !isGroupChecked(groupId)) {
                        entry.markDeleted();
                    }
                }
            }
        }

        // Now add the newly selected items
        for (int i = 0; i < count; i++) {
            GroupSelectionItem item = mAdapter.getItem(i);
            long groupId = item.getGroupId();
            if (item.isChecked() && !hasMembership(groupId)) {
                RawContactDelta.ValuesDelta entry = RawContactModifier.insertChild(mState, mKind);
                entry.setGroupRowId(groupId);
            }
        }
        updateView();
    }

    private boolean isGroupChecked(long groupId) {
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            GroupSelectionItem item = mAdapter.getItem(i);
            if (groupId == item.getGroupId()) {
                return item.isChecked();
            }
        }
        return false;
    }

    private boolean hasMembership(long groupId) {
        if (groupId == mDefaultGroupId && mState.isContactInsert()) {
            return true;
        }

        ArrayList<RawContactDelta.ValuesDelta> entries = mState.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (entries != null) {
            for (RawContactDelta.ValuesDelta values : entries) {
                if (!values.isDelete()) {
                    Long id = values.getGroupRowId();
                    if (id != null && id == groupId) {
                        Log.d(TAG, "has a membership, id: " + groupId);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void createNewGroup() {
        if (mPopup != null) {
            mPopup.dismiss();
            mPopup = null;
        }

        GroupCreationDialogFragment.show(((Activity) getContext()).getFragmentManager(),
                new GroupCreationDialogFragment.OnGroupCreatedListener() {
                    @Override
                    public void onGroupCreated() {
                        mCreatedNewGroup = true;
                    }
                });
    }
}
