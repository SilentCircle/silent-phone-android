/*
Copyright (C) 2013-2017, Silent Circle, LLC.  All rights reserved.

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
 * This  implementation is edited version of original Android sources.
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
package com.silentcircle.contacts.list;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.CursorLoader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import com.silentcircle.logs.Log;

import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.silentcircle.common.list.IndexerListAdapter;
import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.list.ContactListPinnedHeaderView;
import com.silentcircle.common.util.SearchUtil;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactPhotoManagerNew.DefaultImageRequest;
import com.silentcircle.contacts.widget.CompositeCursorAdapter;
import com.silentcircle.messaging.task.ScConversationLoader;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.silentphone2.R;
import com.silentcircle.userinfo.LoadUserInfo;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ScContactEntryListAdapter extends IndexerListAdapter {

    private static final String TAG = "ContactEntryListAdapter";

    /**
     * Indicates whether the {@link com.silentcircle.silentcontacts.ScContactsContract.Directory#LOCAL_INVISIBLE} directory should
     * be included in the search.
     */
    private static final boolean LOCAL_INVISIBLE_DIRECTORY_ENABLED = false;

    public static final int SC_EXACT_MATCH_ON_V1_USER = 50;

    public static final int SC_REMOTE_DIRECTORY = 100;

    public static final int SC_EXISTING_CONVERSATIONS = 200;
    
    private int mDisplayOrder;
    private int mSortOrder;

    private boolean mDisplayPhotos;
    private boolean mCircularPhotos = true;
    private boolean mQuickContactEnabled;
    private boolean mAdjustSelectionBoundsEnabled;

    /**
     * indicates if contact queries include profile
     */
    private boolean mIncludeProfile;

    /**
     * indicates if query results includes a profile
     */
    private boolean mProfileExists;

    /**
     * The root view of the fragment that this adapter is associated with.
     */
    private View mFragmentRootView;

    private ContactPhotoManagerNew mPhotoLoader;

    private String mQueryString;
    private String mUpperCaseQueryString;
    private boolean mSearchMode;
    private int mDirectorySearchMode;
    private int mDirectoryResultLimit = Integer.MAX_VALUE;

    private boolean mLoading = true;
    private boolean mEmptyListEnabled = true;

    private boolean mSelectionVisible;

    private ContactListFilter mFilter;
    private String mContactsCount = "";
    
    boolean mDarkTheme = false;

    /** Resource used to provide header-text for default filter. */
    private CharSequence mDefaultFilterHeaderText;

    private boolean mUseScDirLoaderOrg;
    protected String mPreSelector;
    protected String[] mPreSelectorArgs;

    protected String mFilterPattern;
    protected int mContentColumn;

    protected int mScDirectoryFilterType;
    protected int mScConversationFilterType = ScConversationLoader.NONE;
    protected int mScExactMatchFilterType = ScV1UserLoader.SHOW;
    protected boolean mSearchScData;

    private Map<Integer, Pair<String[], int[]>> mPartitionSectionCountMap
            = new LinkedHashMap<>();

    public ScContactEntryListAdapter(Context context) {
        this(context, false, LoadUserInfo.canCallOutboundOca(context));
    }

    public ScContactEntryListAdapter(Context context, boolean enableScDir, boolean enablePhoneDir) {
        super(context);
        setDefaultFilterHeaderText(R.string.local_search_label);
        addPartitions(enableScDir, enablePhoneDir);
    }

    /**
     * @param fragmentRootView Root view of the fragment. This is used to restrict the scope of
     * image loading requests that get cancelled on cursor changes.
     */
    protected void setFragmentRootView(View fragmentRootView) {
        mFragmentRootView = fragmentRootView;
    }

    protected void setDefaultFilterHeaderText(int resourceId) {
        mDefaultFilterHeaderText = getContext().getResources().getText(resourceId);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
        view.setAdjustSelectionBoundsEnabled(isAdjustSelectionBoundsEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partitionIndex, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView) itemView;
        view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
    }

    @Override
    protected View createPinnedSectionHeaderView(Context context, ViewGroup parent) {
        return new ContactListPinnedHeaderView(context, null, parent);
    }

    @Override
    protected void setPinnedSectionTitle(View pinnedHeaderView, String title) {
        ((ContactListPinnedHeaderView)pinnedHeaderView).setSectionHeaderTitle(title);
    }

//    @Override
//    protected void setPinnedHeaderContactsCount(View header) {
//        // Update the header with the contacts count only if a profile header exists
//        // otherwise, the contacts count are shown in the empty profile header view
//        if (mProfileExists) {
//            ((ContactListPinnedHeaderView)header).setCountView(mContactsCount);
//        }
//        else {
//            clearPinnedHeaderContactsCount(header);
//        }
//    }
//
//    @Override
//    protected void clearPinnedHeaderContactsCount(View header) {
//        ((ContactListPinnedHeaderView)header).setCountView(null);
//    }

    public void addPartitions(boolean enableScDir, boolean enablePhoneDir) {
        //TODO: is there any need to add this while not in search mode?
//        if (enableScDir)
//            addPartition(createRemoteScDirPartition());
        if (enableScDir)
            addPartition(createlocalScDirPartition());
        if (enablePhoneDir)
            addPartition(createDefaultDirectoryPartition());
    }

    protected DirectoryPartition createDefaultDirectoryPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(Directory.DEFAULT);
        partition.setDirectoryType(getContext().getString(R.string.contactsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        partition.setLabel(mDefaultFilterHeaderText.toString());
        return partition;
    }

    protected DirectoryPartition createlocalScDirPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(Directory.DEFAULT);
        partition.setDirectoryType(getContext().getString(R.string.scContactsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        partition.setLabel(getContext().getString(R.string.scContactsList));
        return partition;
    }

    protected DirectoryPartition createRemoteScDirPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(SC_REMOTE_DIRECTORY);
        partition.setDirectoryType(getContext().getString(R.string.scRemoteContactsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(false);
        partition.setDisplayName((TextUtils.isEmpty(LoadUserInfo.getDisplayOrg())
                ? mContext.getString(R.string.scRemoteContactsList)
                : LoadUserInfo.getDisplayOrg())
                + " " + mContext.getString(R.string.directory_search_label).toLowerCase());
        return partition;
    }

    protected DirectoryPartition createLocalScConversationPartition() {
        DirectoryPartition partition = new DirectoryPartition(false, true);
        partition.setDirectoryId(SC_EXISTING_CONVERSATIONS);
        partition.setDirectoryType(getContext().getString(R.string.scExistingConversationsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        return partition;
    }

    protected DirectoryPartition createV1UserPartition() {
        DirectoryPartition partition = new DirectoryPartition(false, false);
        partition.setDirectoryId(SC_EXACT_MATCH_ON_V1_USER);
        partition.setDirectoryType(getContext().getString(R.string.scV1UserList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        return partition;
    }

    /**
     * Remove all directories after the default directory. This is typically used when contacts
     * list screens are asked to exit the search mode and thus need to remove all remote directory
     * results for the search.
     *
     * This code assumes that the default directory and directories before that should not be
     * deleted (e.g. Join screen has "suggested contacts" directory before the default director,
     * and we should not remove the directory).
     */
    protected void removeDirectoriesAfterDefault() {
        final int partitionCount = getPartitionCount();
        for (int i = partitionCount - 1; i >= 0; i--) {
            final Partition partition = getPartition(i);
            if ((partition instanceof DirectoryPartition)
                    && ((DirectoryPartition) partition).getDirectoryId() == Directory.DEFAULT) {
                break;
            } else {
                removePartition(i);
            }
        }
    }

    protected int getPartitionByDirectoryId(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition;
            try {
                partition = getPartition(i);
            } catch (IndexOutOfBoundsException exception) {
                return -1;
            }

            if (partition instanceof DirectoryPartition) {
                if (((DirectoryPartition)partition).getDirectoryId() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected int getPartitionByDirectoryType(String type) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition;
            try {
                partition = getPartition(i);
            } catch (IndexOutOfBoundsException exception) {
                return -1;
            }

            if (partition instanceof DirectoryPartition) {
                if (((DirectoryPartition)partition).getDirectoryType().equals(type)) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected DirectoryPartition getDirectoryById(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition;
            try {
                partition = getPartition(i);
            } catch (IndexOutOfBoundsException exception) {
                return null;
            }

            if (partition instanceof DirectoryPartition) {
                final DirectoryPartition directoryPartition = (DirectoryPartition) partition;
                if (directoryPartition.getDirectoryId() == id) {
                    return directoryPartition;
                }
            }
        }
        return null;
    }

    protected boolean isUseScDirLoaderOrg() {
        return mUseScDirLoaderOrg;
    }

    protected void useScDirectory(boolean use) {
        int idx = getPartitionByDirectoryId(SC_REMOTE_DIRECTORY);
        if (idx < 0)
            return;
        if (!use)
            changeCursor(idx, null);
    }

    protected void useScDirectoryOrganization(boolean use) {
        mUseScDirLoaderOrg = use;
    }

    public void searchScData(boolean yesNo) {
        mSearchScData = yesNo;
    }


    public abstract String getContactDisplayName(int position);
    public abstract void configureLoader(CursorLoader loader, long directoryId, String directoryType);

    /**
     * Marks all partitions as "loading"
     */
    public void onDataReload() {
        boolean notify = false;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (!directoryPartition.isLoading()) {
                    notify = true;
                }
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        if (notify) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void clearPartitions() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        super.clearPartitions();
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public String getQueryString() {
        return mQueryString;
    }

    @SuppressLint("DefaultLocale")
    public void setQueryString(String queryString) {
        mQueryString = queryString;
        if (TextUtils.isEmpty(queryString)) {
            mUpperCaseQueryString = null;
        } else {
            mUpperCaseQueryString = SearchUtil.cleanStartAndEndOfSearchQuery(mQueryString.toUpperCase()) ;
        }
    }

    public void setScPreSelector(String preSelector, String[] preSelectorArgs) {
        mPreSelector = preSelector;
        mPreSelectorArgs = preSelectorArgs;
    }

    public void setContentFilter(final String filterPattern, final int contentColumn) {
        mFilterPattern = filterPattern;
        mContentColumn = contentColumn;
    }

    public void setScDirectoryFilter(final int filterType) {
        mScDirectoryFilterType = filterType;
    }

    public void setScConversationFilter(final int filterType) {
        mScConversationFilterType = filterType;
    }

    public void setScExactMatchFilter(final int filterType) {
        mScExactMatchFilterType = filterType;
    }

    public String getUpperCaseQueryString() {
        return mUpperCaseQueryString;
    }

    public int getDirectorySearchMode() {
        return mDirectorySearchMode;
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    public int getDirectoryResultLimit(long directoryId) {
        if (directoryId == SC_REMOTE_DIRECTORY) {
            return ScDirectoryLoader.MAX_RECORDS;
        }
        else if (directoryId == SC_EXISTING_CONVERSATIONS) {
            return ScConversationLoader.MAX_RECORDS;
        }
        else if (directoryId == SC_EXACT_MATCH_ON_V1_USER) {
            return ScV1UserLoader.MAX_RECORDS;
        }
        return mDirectoryResultLimit;
    }

    public int getDirectoryResultLimit(DirectoryPartition directoryPartition) {
        final int limit = directoryPartition.getResultLimit();
        return limit == DirectoryPartition.RESULT_LIMIT_DEFAULT ? mDirectoryResultLimit : limit;
    }

    public void setDirectoryResultLimit(int limit) {
        this.mDirectoryResultLimit = limit;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
    }

    public void setPhotoLoader(ContactPhotoManagerNew photoLoader) {
        mPhotoLoader = photoLoader;
    }

    protected ContactPhotoManagerNew getPhotoLoader() {
        return mPhotoLoader;
    }

    public boolean getDisplayPhotos() {
        return mDisplayPhotos;
    }

    public void setDisplayPhotos(boolean displayPhotos) {
        mDisplayPhotos = displayPhotos;
    }

    public boolean getCircularPhotos() {
        return mCircularPhotos;
    }

    public void setCircularPhotos(boolean circularPhotos) {
        mCircularPhotos = circularPhotos;
    }
    public boolean isEmptyListEnabled() {
        return mEmptyListEnabled;
    }

    public void setEmptyListEnabled(boolean flag) {
        mEmptyListEnabled = flag;
    }

    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
    }

    public boolean isQuickContactEnabled() {
        return mQuickContactEnabled;
    }

    public void setQuickContactEnabled(boolean quickContactEnabled) {
        mQuickContactEnabled = quickContactEnabled;
    }

    public boolean isAdjustSelectionBoundsEnabled() {
        return mAdjustSelectionBoundsEnabled;
    }

    public void setAdjustSelectionBoundsEnabled(boolean enabled) {
        mAdjustSelectionBoundsEnabled = enabled;
    }

    public boolean shouldIncludeProfile() {
        return mIncludeProfile;
    }

    public void setIncludeProfile(boolean includeProfile) {
        mIncludeProfile = includeProfile;
    }

    public void setProfileExists(boolean exists) {
        mProfileExists = exists;
        // Stick the "ME" header for the profile
        if (exists) {
            SectionIndexer indexer = getIndexer();
            if (indexer != null) {
                ((ContactsSectionIndexer) indexer).setProfileHeader(
                        getContext().getString(R.string.user_profile_contacts_list_header));
            }
        }
    }

    public boolean hasProfile() {
        return mProfileExists;
    }

    public void setDarkTheme(boolean value) {
        mDarkTheme = value;
    }

    public void configureDirectoryLoader(DirectoryListLoader loader) {
        loader.setDirectorySearchMode(mDirectorySearchMode);
        loader.setLocalInvisibleDirectoryEnabled(LOCAL_INVISIBLE_DIRECTORY_ENABLED);
    }

    /**
     * Updates partitions according to the directory meta-data contained in the supplied
     * cursor.
     */
    public void changeDirectories(Cursor cursor) {
        if (cursor.getCount() == 0) {
            // Directory table must have at least local directory, without which this adapter will
            // enter very weird state.
            Log.e(TAG, "Directory search loader returned an empty cursor, which implies we have " +
                    "no directory entries.", new RuntimeException());
            return;
        }
        HashSet<Long> directoryIds = new HashSet<Long>();

        int idColumnIndex = cursor.getColumnIndex(Directory._ID);
        int directoryTypeColumnIndex = cursor.getColumnIndex(DirectoryListLoader.DIRECTORY_TYPE);
        int displayNameColumnIndex = cursor.getColumnIndex(Directory.DISPLAY_NAME);
        int photoSupportColumnIndex = cursor.getColumnIndex(Directory.PHOTO_SUPPORT);

        // TODO preserve the order of partition to match those of the cursor
        // Phase I: add new directories
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumnIndex);
            directoryIds.add(id);
            if (getPartitionByDirectoryId(id) == -1) {
                DirectoryPartition partition = new DirectoryPartition(false, true);
                partition.setDirectoryId(id);
                if (isRemoteDirectory(id)) {
                    partition.setLabel(mContext.getString(R.string.directory_search_label));
                } else {
                    partition.setLabel(mDefaultFilterHeaderText.toString());
                }
                partition.setDirectoryType(cursor.getString(directoryTypeColumnIndex));
                partition.setDisplayName(cursor.getString(displayNameColumnIndex));
                int photoSupport = cursor.getInt(photoSupportColumnIndex);
                partition.setPhotoSupported(photoSupport == Directory.PHOTO_SUPPORT_THUMBNAIL_ONLY
                        || photoSupport == Directory.PHOTO_SUPPORT_FULL);
                addPartition(partition);
            }
        }

        // Phase II: remove deleted directories
        int count = getPartitionCount();
        for (int i = count; --i >= 0; ) {
            CompositeCursorAdapter.Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                long id = ((DirectoryPartition)partition).getDirectoryId();
                if (!directoryIds.contains(id)) {
                    removePartition(i);
                }
            }
        }

        invalidate();
        notifyDataSetChanged();
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        if (partitionIndex >= getPartitionCount()) {
            // There is no partition for this data
            return;
        }

        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            ((DirectoryPartition)partition).setStatus(DirectoryPartition.STATUS_LOADED);
        }

        if (mDisplayPhotos && mPhotoLoader != null && isPhotoSupported(partitionIndex)) {
            mPhotoLoader.refreshCache();
        }

        super.changeCursor(partitionIndex, cursor);


//        if (partitionIndex == getIndexedPartition()) { //&& isSectionHeaderDisplayEnabled()) {
        if (((DirectoryPartition) partition).getDirectoryId() == Directory.DEFAULT) {
            updatePartitionSections(partitionIndex, cursor);
            updateIndexer();
        }

        // When the cursor changes, cancel any pending asynchronous photo loads.
        mPhotoLoader.cancelPendingRequests(mFragmentRootView);

        onChangeCursor(partitionIndex);
    }

    public void onChangeCursor(int partitionIndex) {
    }

    public void changeCursor(Cursor cursor) {
        changeCursor(0, cursor);
    }

    /**
     * Updates the indexer, which is used to produce section headers.
     */
    private void updatePartitionSections(int partitionIndex, Cursor cursor) {
        if (cursor == null) {
//            setIndexer(null);
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES) &&
                bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)) {
            String sections[] =
                    bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(
                    Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);

            if (getExtraStartingSection()) {
                // Insert an additional unnamed section at the top of the list.
                String allSections[] = new String[sections.length + 1];
                int allCounts[] = new int[counts.length + 1];
                for (int i = 0; i < sections.length; i++) {
                    allSections[i + 1] = sections[i];
                    allCounts[i + 1] = counts[i];
                }
                allCounts[0] = 1;
                allSections[0] = "";
                setIndexer(new ContactsSectionIndexer(allSections, allCounts));
                mPartitionSectionCountMap.put(partitionIndex, new Pair(allSections, allCounts));
            } else {
                if (sections != null
                        && counts != null
                        && sections.length > 0) {
                    counts[0] = counts[0] + 1;
                    mPartitionSectionCountMap.put(partitionIndex, new Pair(sections, counts));
                }
            }
        } else {
            Map<String, Integer> sectionCountMap = new LinkedHashMap<String, Integer>();

            while (cursor.moveToNext()) {
                try {
                    String displayName = cursor.getString(cursor.getColumnIndex(Contacts.DISPLAY_NAME));

                    if (!TextUtils.isEmpty(displayName)) {
                        String letter = Character.toString(Character.toUpperCase(displayName.charAt(0)));

                        Integer count = sectionCountMap.get(letter);
                        sectionCountMap.put(letter, count == null ? 1 : count + 1);
                    }
                } catch (Exception ignore) {
                    continue;
                }
            }

            if (sectionCountMap.size() == 0) {
                return;
            }

            String sections[] = sectionCountMap.keySet().toArray(new String[sectionCountMap.size()]);
            int counts[] = IOUtils.convertIntegers(
                    sectionCountMap.values().toArray(new Integer[sectionCountMap.size()]));

            if (sections != null
                    && counts != null
                    && sections.length > 0) {
                counts[0] = counts[0] + 1;
                mPartitionSectionCountMap.put(partitionIndex, new Pair(sections, counts));
            }
        }
    }

    private void updateIndexer() {
        String[] sections = new String[0];
        int[] counts = new int[0];
        for (int i = 0; i < getPartitionCount(); i++) {
            if (mPartitionSectionCountMap.containsKey(i)) {
                Pair<String[], int[]> sectionCount = mPartitionSectionCountMap.get(i);

                if (sectionCount == null) {
                    return;
                }

                sections = IOUtils.concat(sections, sectionCount.first);
                counts = IOUtils.concat(counts, sectionCount.second);
            }
        }

        if (sections != null
                && counts != null
                && sections.length > 0) {
            setIndexer(new ContactsSectionIndexer(sections, counts));
        }
    }

    protected boolean getExtraStartingSection() {
        return false;
    }

    @Override
    public int getViewTypeCount() {
        // We need a separate view type for each item type, plus another one for
        // each type with header, plus one for "other".
        return getItemViewTypeCount() * 2 + 1;
    }

    @Override
    public int getItemViewType(int partitionIndex, int position) {
        int type = super.getItemViewType(partitionIndex, position);
        if (!isUserProfile(position)
                && isSectionHeaderDisplayEnabled()
                && partitionIndex == getIndexedPartition()) {
            Placement placement = getItemPlacementInSection(position);
            return placement.firstInSection ? type : getItemViewTypeCount() + type;
        } else {
            return type;
        }
    }

    @Override
    public boolean isEmpty() {
//   TODO     if (contactsListActivity.mProviderStatus != ScContactsContract.ProviderStatus.STATUS_NORMAL) {
//            return true;
//        }

        if (!mEmptyListEnabled) {
            return false;
        } else if (isSearchMode()) {
            return TextUtils.isEmpty(getQueryString());
        } else if (mLoading) {
            // We don't want the empty state to show when loading.
            return false;
        } else {
            return super.isEmpty();
        }
    }

    public boolean isLoading() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition
                    && ((DirectoryPartition) partition).isLoading()) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllPartitionsEmpty() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            if (!isPartitionEmpty(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if partition is a directory partition with specified
     * and has no cursor or an empty cursor.
     */
    public boolean isDirectoryEmpty(int directoryId) {
        boolean result = true;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition) partition;
                if (directoryPartition.getDirectoryId() == directoryId) {
                    result = directoryPartition.isEmpty();
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Changes visibility parameters for the default directory partition.
     */
    public void configureDefaultPartition(boolean showIfEmpty, boolean hasHeader) {
        int defaultPartitionIndex = -1;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition &&
                    ((DirectoryPartition)partition).getDirectoryId() == Directory.DEFAULT) {
                defaultPartitionIndex = i;
                break;
            }
        }
        if (defaultPartitionIndex != -1) {
//            setShowIfEmpty(defaultPartitionIndex, showIfEmpty);
//            setHasHeader(defaultPartitionIndex, hasHeader);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @SuppressWarnings("deprecation")
    protected View newHeaderView(Context context, int partition, Cursor cursor, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.directory_header_new, parent, false);
        if (!getPinnedPartitionHeadersEnabled()) {
            // If the headers are unpinned, there is no need for their background
            // color to be non-transparent. Setting this transparent reduces maintenance for
            // non-pinned headers. We don't need to bother synchronizing the activity's
            // background color with the header background color.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                view.setBackground(null);
            else
                view.setBackgroundDrawable(null);
        }
        return view;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        Partition partition = getPartition(partitionIndex);
        if (!(partition instanceof DirectoryPartition)) {
            return;
        }

        DirectoryPartition directoryPartition = (DirectoryPartition)partition;
        long directoryId = directoryPartition.getDirectoryId();
        TextView labelTextView = (TextView)view.findViewById(R.id.label);
        TextView displayNameTextView = (TextView)view.findViewById(R.id.display_name);
        if(TextUtils.isEmpty(directoryPartition.getLabel())) {
            labelTextView.setText(null);
            labelTextView.setVisibility(View.GONE);
        } else {
            labelTextView.setText(directoryPartition.getLabel());
            labelTextView.setVisibility(View.VISIBLE);
        }
        if (!isRemoteDirectory(directoryId)) {
            displayNameTextView.setText(null);
            displayNameTextView.setVisibility(View.GONE);
        } else {
            String directoryName = directoryPartition.getDisplayName();
            String displayName = !TextUtils.isEmpty(directoryName)
                    ? directoryName
                    : directoryPartition.getDirectoryType();
            displayNameTextView.setText(displayName);
            displayNameTextView.setVisibility(View.VISIBLE);
        }

        final Resources res = getContext().getResources();
        final int headerPaddingTop = (partitionIndex == 1 && getPartition(0).isEmpty()) || partitionIndex == 0
                ? res.getDimensionPixelOffset(R.dimen.search_top_margin)
                : res.getDimensionPixelOffset(R.dimen.directory_header_extra_top_padding);
        // There should be no extra padding at the top of the first directory header
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            view.setPaddingRelative(view.getPaddingStart(), headerPaddingTop, view.getPaddingEnd(), view.getPaddingBottom());
        }
        else {
            view.setPadding(view.getPaddingLeft(), headerPaddingTop, view.getPaddingRight(), view.getPaddingBottom());
        }
        // The display of the cursor count is sort of misleading - it shows the number of number/name data records,
        // not the number of contact entries.
        TextView countText = (TextView)view.findViewById(R.id.count);
        if (directoryPartition.isLoading()) {
            countText.setText(R.string.search_results_searching);
        } else {
            int count;
            if (directoryId == SC_REMOTE_DIRECTORY
                    || directoryId == SC_EXISTING_CONVERSATIONS
                    || directoryId == SC_EXACT_MATCH_ON_V1_USER) {
                // FIXME: There is a cache bug with this, reports # of results incorrectly
                // count = ScDirectoryLoader.getNumberOfRecords();
                count = getResultCount(cursor);
            }
            else {
                count = getResultCount(cursor);
            }
            if (directoryId != Directory.DEFAULT && directoryId != Directory.LOCAL_INVISIBLE) {
                if (directoryId == SC_EXACT_MATCH_ON_V1_USER) {
                    countText.setText(null);
                }
                else if (count >= getDirectoryResultLimit(directoryId)) {
                    countText.setText(mContext.getString(R.string.foundTooManyContacts, getDirectoryResultLimit(directoryId)));
                }
                else {
                    countText.setText(getQuantityText(count, R.string.listFoundAllResultsZero, R.plurals.searchFoundContacts));
                }
            }
            else {
                countText.setText(getQuantityText(count, R.string.listFoundAllContactsZero, R.plurals.searchFoundContacts));
            }
        }
    }

    // Default implementation simply returns number of rows in the cursor.
    // Broken out into its own routine so can be overridden by child classes
    // for eg number of unique contacts for a phone list.
    protected int getResultCount(Cursor cursor) {
        return cursor == null ? 0 : cursor.getCount();
    }

    /**
     * Checks whether the contact entry at the given position represents the user's profile.
     */
    protected boolean isUserProfile(int position) {
        // The profile only ever appears in the first position if it is present.  So if the position
        // is anything beyond 0, it can't be the profile.
        boolean isUserProfile = false;
        if (position == 0) {
            int partition = getPartitionForPosition(position);
            if (partition >= 0) {
                // Save the old cursor position - the call to getItem() may modify the cursor
                // position.
                Cursor oldCursor = getCursor(partition);
                int offset = oldCursor == null ? 0 : oldCursor.getPosition();
                Cursor cursor = (Cursor) getItem(position);
                if (cursor != null) {
                    int profileColumnIndex = cursor.getColumnIndex(Contacts.IS_USER_PROFILE);
                    if (profileColumnIndex != -1) {
                        isUserProfile = cursor.getInt(profileColumnIndex) == 1;
                    }
                    // Restore the old cursor position.
                    cursor.moveToPosition(offset);
                }
            }
        }
        return isUserProfile;
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getContext().getString(zeroResourceId);
        } else {
            String format = getContext().getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    public boolean isPhotoSupported(int partitionIndex) {
        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            return ((DirectoryPartition) partition).isPhotoSupported();
        }
        return true;
    }

    /**
     * Returns the currently selected filter.
     */
    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        mFilter = filter;
    }

    // TODO: move sharable logic (bindXX() methods) to here with extra arguments

    /**
     * Loads the photo for the quick contact view and assigns the contact uri.
     * @param photoIdColumn Index of the photo id column
     * @param photoUriColumn Index of the photo uri column. Optional: Can be -1
     * @param contactIdColumn Index of the contact id column
     * @param lookUpKeyColumn Index of the lookup key column
     * @param displayNameColumn Index of the display name column
     */
    protected void bindQuickContact(final ContactListItemView view, int partitionIndex,
            Cursor cursor, int photoIdColumn, int photoUriColumn, int contactIdColumn,
            int lookUpKeyColumn, int displayNameColumn) {
        long photoId = 0;
        if (!cursor.isNull(photoIdColumn)) {
            photoId = cursor.getLong(photoIdColumn);
        }

        QuickContactBadge quickContact = view.getQuickContact();
        quickContact.assignContactUri(
                getContactUri(partitionIndex, cursor, contactIdColumn, lookUpKeyColumn));

        if (photoId != 0 || photoUriColumn == -1) {
            getPhotoLoader().loadThumbnail(quickContact, photoId, mDarkTheme, mCircularPhotos,
                    null);
        } else {
            final String photoUriString = cursor.getString(photoUriColumn);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            DefaultImageRequest request = null;
            if (photoUri == null) {
                request = getDefaultImageRequestFromCursor(cursor, displayNameColumn,
                        lookUpKeyColumn);
            }
            getPhotoLoader().loadPhoto(quickContact, photoUri, -1, mDarkTheme, mCircularPhotos,
                    request);
        }

    }

    @Override
    public boolean hasStableIds() {
        // Whenever bindViewId() is called, the values passed into setId() are stable or
        // stable-ish. For example, when one contact is modified we don't expect a second
        // contact's Contact._ID values to change.
        return true;
    }

    protected void bindViewId(final ContactListItemView view, Cursor cursor, int idColumn) {
        // Set a semi-stable id, so that talkback won't get confused when the list gets
        // refreshed. There is little harm in inserting the same ID twice.
        long contactId = cursor.getLong(idColumn);
        view.setId((int) (contactId % Integer.MAX_VALUE));
    }

    protected Uri getContactUri(int partitionIndex, Cursor cursor,
            int contactIdColumn, int lookUpKeyColumn) {
        long contactId = cursor.getLong(contactIdColumn);
        String lookupKey = cursor.getString(lookUpKeyColumn);
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        if (directoryId == SC_REMOTE_DIRECTORY || directoryId == SC_EXISTING_CONVERSATIONS)
            return null;                    // We don't have a database URI for SC directory contacts
        // Remote directories must have a lookup key or we don't have
        // a working contact URI
        if (TextUtils.isEmpty(lookupKey) && isRemoteDirectory(directoryId)) {
            return null;
        }
        Uri uri = Contacts.getLookupUri(contactId, lookupKey);
        if (directoryId != Directory.DEFAULT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
        }
        return uri;
    }

    public static boolean isRemoteDirectory(long directoryId) {
        return directoryId != ContactsContract.Directory.DEFAULT
                && directoryId != ContactsContract.Directory.LOCAL_INVISIBLE;
    }

    public void setContactsCount(String count) {
        mContactsCount = count;
    }

    public String getContactsCount() {
        return mContactsCount;
    }

    /**
     * Retrieves the lookup key and display name from a cursor, and returns a
     * {@link DefaultImageRequest} containing these contact details
     *
     * @param cursor Contacts cursor positioned at the current row to retrieve contact details for
     * @param displayNameColumn Column index of the display name
     * @param lookupKeyColumn Column index of the lookup key
     * @return {@link DefaultImageRequest} with the displayName and identifier fields set to the
     * display name and lookup key of the contact.
     */
    public DefaultImageRequest getDefaultImageRequestFromCursor(Cursor cursor,
            int displayNameColumn, int lookupKeyColumn) {
        final String displayName = cursor.getString(displayNameColumn);
        final String lookupKey = cursor.getString(lookupKeyColumn);
        return new DefaultImageRequest(displayName, lookupKey, mCircularPhotos);
    }
}
