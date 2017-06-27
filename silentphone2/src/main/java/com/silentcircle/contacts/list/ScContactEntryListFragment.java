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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.ContactsContract.Directory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.preference.ContactsPreferences;
import com.silentcircle.contacts.widget.CompositeCursorAdapter;
import com.silentcircle.messaging.task.ScConversationLoader;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.silentphone2.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ScContactEntryListFragment<T extends ScContactEntryListAdapter> extends Fragment
        implements OnItemClickListener, OnScrollListener, OnFocusChangeListener, OnTouchListener, LoaderCallbacks<Cursor> {

    private static final String TAG = "ScContactEntryListFrag";

    // TODO: Make this protected. This should not be used from the PeopleActivity but
    // instead use the new startActivityWithResultFromFragment API
    public static final int ACTIVITY_REQUEST_CODE_PICKER = 1;

    private static final String KEY_LIST_STATE = "liststate";
    private static final String KEY_SECTION_HEADER_DISPLAY_ENABLED = "sectionHeaderDisplayEnabled";
    private static final String KEY_PHOTO_LOADER_ENABLED = "photoLoaderEnabled";
    private static final String KEY_QUICK_CONTACT_ENABLED = "quickContactEnabled";
    private static final String KEY_ADJUST_SELECTION_BOUNDS_ENABLED = "adjustSelectionBoundsEnabled";
    private static final String KEY_INCLUDE_PROFILE = "includeProfile";
    private static final String KEY_SEARCH_MODE = "searchMode";
    private static final String KEY_VISIBLE_SCROLLBAR_ENABLED = "visibleScrollbarEnabled";
    private static final String KEY_SCROLLBAR_POSITION = "scrollbarPosition";
    private static final String KEY_QUERY_STRING = "queryString";
    private static final String KEY_DIRECTORY_SEARCH_MODE = "directorySearchMode";
    private static final String KEY_SELECTION_VISIBLE = "selectionVisible";
    private static final String KEY_REQUEST = "request";
    private static final String KEY_DARK_THEME = "darkTheme";
    private static final String KEY_LEGACY_COMPATIBILITY = "legacyCompatibility";
    private static final String KEY_PHONE_INPUT = "phoneInput";
    private static final String KEY_DIRECTORY_RESULT_LIMIT = "directoryResultLimit";
    private static final String KEY_IN_USER_SELECTION_MODE = "userSelectionMode";
    private static final String KEY_SELECTION = "userSelection";

    private static final String KEY_SC_PRE_SELECTOR = "sc_pre_selector";
    private static final String KEY_SC_PRE_SELECTOR_ARGS = "sc_pre_selector_args";

    private static final String KEY_SC_FILTER_PATTERN = "sc_filter_pattern";
    private static final String KEY_SC_CONTENT_COLUMN = "sc_content_column";

    private static final String DIRECTORY_ID_ARG_KEY = "directoryId";
    private static final String DIRECTORY_TYPE_ARG_KEY = "directoryType";

    private static final int DIRECTORY_LOADER_ID = -1;

    private static final int DIRECTORY_SEARCH_DELAY_MILLIS = 600;  // 300ms is not enough on small touch keyboards
    private static final int DIRECTORY_SEARCH_MESSAGE = 1;

    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mQuickContactEnabled = true;
    private boolean mAdjustSelectionBoundsEnabled = true;
    private boolean mIncludeProfile;
    private boolean mSearchMode;
    private boolean mVisibleScrollbarEnabled;
    private int mVerticalScrollbarPosition = View.SCROLLBAR_POSITION_RIGHT;
    private boolean mPhoneInput;
    private String mQueryString;
    private int mDirectorySearchMode = DirectoryListLoader.SEARCH_MODE_NONE;
    private boolean mSelectionVisible;
    private boolean mLegacyCompatibility;
    private boolean mShowEmptyListForEmptyQuery;
    private boolean mInUserSelectionMode;

    private boolean mEnabled = true;

    private T mAdapter;
    private View mView;
    private ListView mListView;

    private List<String> mSelectedItems = new ArrayList<>();

    /**
     * Used for keeping track of the scroll state of the list.
     */
    private Parcelable mListState;

    private int mDisplayOrder;
    private int mSortOrder;
    private int mDirectoryResultLimit = DEFAULT_DIRECTORY_RESULT_LIMIT;

    private ContactPhotoManagerNew mPhotoManager;
//    private ContactListEmptyView mEmptyView;
    private ContactsPreferences mContactsPrefs;

    private boolean mForceLoad;

    private boolean mDarkTheme;

    protected boolean mUserProfileExists;

    private static final int STATUS_NOT_LOADED = 0;
    private static final int STATUS_LOADING = 1;
    private static final int STATUS_LOADED = 2;

    private int mDirectoryListStatus = STATUS_NOT_LOADED;

    /**
     * Indicates whether we are doing the initial complete load of data (false) or
     * a refresh caused by a change notification (true)
     */
    private boolean mLoadPriorityDirectoriesOnly;

    private Context mContext;

    private LoaderManager mLoaderManager;

    private boolean mUseScDirLoader;

    private static class DirectoryDirectorySearchHandler extends Handler {
        private final WeakReference<ScContactEntryListFragment> mFragment;

        public DirectoryDirectorySearchHandler(ScContactEntryListFragment adapter) {
            mFragment = new WeakReference<>(adapter);
        }

        @Override
        public void handleMessage(Message msg) {
            ScContactEntryListFragment fragment = mFragment.get();
            if (msg.what == DIRECTORY_SEARCH_MESSAGE) {
                fragment.loadDirectoryPartition(msg.arg1, (DirectoryPartition) msg.obj);
            }
        }
    }

    private Handler mDelayedDirectorySearchHandler = new DirectoryDirectorySearchHandler(this);

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract T createListAdapter();

    /**
     * @param position Please note that the position is already adjusted for
     *            header views, so "0" means the first list item below header
     *            views.
     */
    protected abstract void onItemClick(int position, long id);

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
        setContext(activity);
        setLoaderManager(super.getLoaderManager());
    }

    /**
     * Sets a context for the fragment in the unit test environment.
     */
    public void setContext(Context context) {
        mContext = context;
        configurePhotoLoader();
    }

    public Context getOwnContext() {
        return mContext;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mAdapter != null) {
                if (mEnabled) {
                    reloadData();
                } else {
                    mAdapter.clearPartitions();
                }
            }
        }
    }

    /**
     * Overrides a loader manager for use in unit tests.
     */
    public void setLoaderManager(LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    @Override
    public LoaderManager getLoaderManager() {
        return mLoaderManager;
    }

    public T getAdapter() {
        return mAdapter;
    }

    public View getOwnView() {
        return mView;
    }

    public ListView getListView() {
        return mListView;
    }

//    public ContactListEmptyView getEmptyView() {
//        return mEmptyView;
//    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED, mSectionHeaderDisplayEnabled);
        outState.putBoolean(KEY_PHOTO_LOADER_ENABLED, mPhotoLoaderEnabled);
        outState.putBoolean(KEY_QUICK_CONTACT_ENABLED, mQuickContactEnabled);
        outState.putBoolean(KEY_ADJUST_SELECTION_BOUNDS_ENABLED, mAdjustSelectionBoundsEnabled);
        outState.putBoolean(KEY_INCLUDE_PROFILE, mIncludeProfile);
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode);
        outState.putBoolean(KEY_VISIBLE_SCROLLBAR_ENABLED, mVisibleScrollbarEnabled);
        outState.putInt(KEY_SCROLLBAR_POSITION, mVerticalScrollbarPosition);
        outState.putInt(KEY_DIRECTORY_SEARCH_MODE, mDirectorySearchMode);
        outState.putBoolean(KEY_SELECTION_VISIBLE, mSelectionVisible);
        outState.putBoolean(KEY_LEGACY_COMPATIBILITY, mLegacyCompatibility);
        outState.putBoolean(KEY_PHONE_INPUT, mPhoneInput);
        outState.putString(KEY_QUERY_STRING, mQueryString);
        outState.putInt(KEY_DIRECTORY_RESULT_LIMIT, mDirectoryResultLimit);
        outState.putBoolean(KEY_DARK_THEME, mDarkTheme);
        outState.putBoolean(KEY_IN_USER_SELECTION_MODE, mInUserSelectionMode);
        outState.putCharSequenceArray(KEY_SELECTION,
                mSelectedItems.toArray(new CharSequence[getSelectedItems().size()]));


        if (mListView != null) {
            outState.putParcelable(KEY_LIST_STATE, mListView.onSaveInstanceState());
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mContactsPrefs = new ContactsPreferences(mContext);
        restoreSavedState(savedState);
        mAdapter = createListAdapter();
        if (savedState != null && mSearchMode) {
            mSearchMode = false;
            setSearchMode(true);
        }
        mContactsPrefs = new ContactsPreferences(mContext);
    }

    public void restoreSavedState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        mSectionHeaderDisplayEnabled = savedState.getBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED);
        mPhotoLoaderEnabled = savedState.getBoolean(KEY_PHOTO_LOADER_ENABLED);
        mQuickContactEnabled = savedState.getBoolean(KEY_QUICK_CONTACT_ENABLED);
        mAdjustSelectionBoundsEnabled = savedState.getBoolean(KEY_ADJUST_SELECTION_BOUNDS_ENABLED);
        mIncludeProfile = savedState.getBoolean(KEY_INCLUDE_PROFILE);
        mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE);
        mVisibleScrollbarEnabled = savedState.getBoolean(KEY_VISIBLE_SCROLLBAR_ENABLED);
        mVerticalScrollbarPosition = savedState.getInt(KEY_SCROLLBAR_POSITION);
        mDirectorySearchMode = savedState.getInt(KEY_DIRECTORY_SEARCH_MODE);
        mSelectionVisible = savedState.getBoolean(KEY_SELECTION_VISIBLE);
        mLegacyCompatibility = savedState.getBoolean(KEY_LEGACY_COMPATIBILITY);
        mPhoneInput = savedState.getBoolean(KEY_PHONE_INPUT);
        mQueryString = savedState.getString(KEY_QUERY_STRING);
        mDirectoryResultLimit = savedState.getInt(KEY_DIRECTORY_RESULT_LIMIT);
        mDarkTheme = savedState.getBoolean(KEY_DARK_THEME);
        mInUserSelectionMode = savedState.getBoolean(KEY_IN_USER_SELECTION_MODE);
        CharSequence[] participants = savedState.getCharSequenceArray(KEY_SELECTION);
        setSelection(participants);

        // Retrieve list state. This will be applied in onLoadFinished
        mListState = savedState.getParcelable(KEY_LIST_STATE);
    }

    @Override
    public void onStart() {
        mContactsPrefs.registerChangeListener(mPreferencesChangeListener);
        mForceLoad = loadPreferences();

        // Call super.onStart() _after_ loading the new preference to make sure
        // ScDirectory loader has processed preference changes.
        super.onStart();

        mDirectoryListStatus = STATUS_NOT_LOADED;
        mLoadPriorityDirectoriesOnly = true;

        startLoading();
    }

    protected void startLoading() {
        if (mAdapter == null) {
            // The method was called before the fragment was started
            return;
        }
        configureAdapter();
        int partitionCount = mAdapter.getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            CompositeCursorAdapter.Partition partition = mAdapter.getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (directoryPartition.getStatus() == DirectoryPartition.STATUS_NOT_LOADED) {
                    if (directoryPartition.isPriorityDirectory() || !mLoadPriorityDirectoriesOnly) {
                        startLoadingDirectoryPartition(i);
                    }
                }
            }
            else {
                getLoaderManager().initLoader(i, null, this);
            }
        }

        // Next time this method is called, we should start loading non-priority directories
        mLoadPriorityDirectoriesOnly = false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
//        Log.d(TAG, "Loader id: " + id);
        if (id == DIRECTORY_LOADER_ID) {
//  TODO            DirectoryListLoader loader = new DirectoryListLoader(mContext);
//            mAdapter.configureDirectoryLoader(loader);
//            return loader;
            return null;
        }
        else {
            Loader<Cursor> loader = null;
            long directoryId = args != null && args.containsKey(DIRECTORY_ID_ARG_KEY) 
                    ? args.getLong(DIRECTORY_ID_ARG_KEY) : Directory.DEFAULT;
            String directoryType = args != null && args.containsKey(DIRECTORY_TYPE_ARG_KEY)
                    ? args.getString(DIRECTORY_TYPE_ARG_KEY) : null;
            if (directoryId == Directory.DEFAULT) {
                loader = createCursorLoader();
                mAdapter.configureLoader((CursorLoaderSc)loader, directoryId, directoryType);
                int directory = mAdapter.getPartitionByDirectoryType(directoryType);

                if (directory != -1) {
                    mAdapter.setIndexedPartition(directory);
                }
            }
            else if (directoryId == ScContactEntryListAdapter.SC_REMOTE_DIRECTORY) {
                loader = new ScDirectoryLoader(mContext);
                ((PhoneNumberListAdapter)mAdapter).configureScDirLoader((ScDirectoryLoader)loader);
            }
            else if (directoryId == ScContactEntryListAdapter.SC_EXISTING_CONVERSATIONS) {
                loader = new ScConversationLoader(mContext);
                ((PhoneNumberListAdapter) mAdapter).configureConversationDirLoader((ScConversationLoader)loader);
            }
            else if (directoryId == ScContactEntryListAdapter.SC_EXACT_MATCH_ON_V1_USER) {
                loader = new ScV1UserLoader(mContext);
                ((PhoneNumberListAdapter) mAdapter).configureExactMatchLoader((ScV1UserLoader)loader);
            }
            return loader;
        }
    }

    public CursorLoaderSc createCursorLoader() {
        return new CursorLoaderSc(mContext, null, null, null, null, null);
    }

    private void startLoadingDirectoryPartition(int partitionIndex) {
        DirectoryPartition partition = (DirectoryPartition)mAdapter.getPartition(partitionIndex);
        partition.setStatus(DirectoryPartition.STATUS_LOADING);
        long directoryId = partition.getDirectoryId();
        String directoryType = partition.getDirectoryType();
        if (directoryId == ScContactEntryListAdapter.SC_REMOTE_DIRECTORY) {
            if (!mUseScDirLoader)
                return;
//            searchProgressVisibility(!TextUtils.isEmpty(mQueryString) ? true : false);
        }
        if (mForceLoad) {
            if (directoryId == Directory.DEFAULT) {
                loadDirectoryPartition(partitionIndex, partition);
            } else {
                loadDirectoryPartitionDelayed(partitionIndex, partition);
            }
        } else {
            Bundle args = new Bundle();
            args.putLong(DIRECTORY_ID_ARG_KEY, directoryId);
            args.putString(DIRECTORY_TYPE_ARG_KEY, directoryType);
            getLoaderManager().initLoader(partitionIndex, args, this);
        }
    }

    /**
     * Queues up a delayed request to search the specified directory. Since
     * directory search will likely introduce a lot of network traffic, we want
     * to wait for a pause in the user's typing before sending a directory request.
     */
    private void loadDirectoryPartitionDelayed(int partitionIndex, DirectoryPartition partition) {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE, partition);
        Message msg = mDelayedDirectorySearchHandler.obtainMessage(DIRECTORY_SEARCH_MESSAGE, partitionIndex, 0, partition);
        mDelayedDirectorySearchHandler.sendMessageDelayed(msg, DIRECTORY_SEARCH_DELAY_MILLIS);
    }

    /**
     * Loads the directory partition.
     */
    protected void loadDirectoryPartition(int partitionIndex, DirectoryPartition partition) {
        Bundle args = new Bundle();
        args.putLong(DIRECTORY_ID_ARG_KEY, partition.getDirectoryId());
        args.putString(DIRECTORY_TYPE_ARG_KEY, partition.getDirectoryType());
        getLoaderManager().restartLoader(partitionIndex, args, this);
    }

    /**
     * Cancels all queued directory loading requests.
     */
    private void removePendingDirectorySearchRequests() {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!mEnabled) {
            return;
        }

        int loaderId = loader.getId();
        if (loaderId == DIRECTORY_LOADER_ID) {
            mDirectoryListStatus = STATUS_LOADED;
            mAdapter.changeDirectories(data);
            startLoading();
        } else {
            // Do not show the /user match if there is already a result in the directory listing
            int remotePartition = mAdapter.getPartitionByDirectoryType(mContext.getString(R.string.scRemoteContactsList));
            int v1UserPartition = mAdapter.getPartitionByDirectoryType(mContext.getString(R.string.scV1UserList));
            Cursor directoryResults = remotePartition == -1 ? null : mAdapter.getPartition(remotePartition) != null
                    ? mAdapter.getCursor(remotePartition) : null;
            Cursor v1UserResults = v1UserPartition == -1 ? null : mAdapter.getPartition(v1UserPartition) != null
                    ? mAdapter.getCursor(v1UserPartition) : null;
            // We now have fresh data - use it
            if (loaderId == remotePartition) {
                directoryResults = data;
            }
            if (loaderId == v1UserPartition) {
                v1UserResults = data;
            }

            if (directoryResults != null && v1UserResults != null
                    && directoryResults.getCount() > 0 && v1UserResults.getCount() > 0) {
                try {
                    MatrixCursor newData = new MatrixCursor(directoryResults.getColumnNames(), 0);
                    if (v1UserResults.moveToFirst()) {
                        do {
                            String uuid = v1UserResults.getString(10);

                            boolean addRow = true;
                            if (uuid != null) {
                                if (directoryResults.moveToFirst()) {
                                    do {
                                        if (uuid.equals(directoryResults.getString(10))) {
                                            addRow = false;
                                            break;
                                        }
                                    }
                                    while (directoryResults.moveToNext());
                                }
                                directoryResults.moveToPosition(0);

                                if (addRow) {
                                    MatrixCursor.RowBuilder row;
                                    row = newData.newRow();
                                    row.add(v1UserResults.getInt(0));
                                    row.add(v1UserResults.getInt(1));
                                    row.add(v1UserResults.getString(2));
                                    row.add(v1UserResults.getString(3));
                                    row.add(v1UserResults.getInt(4));
                                    row.add(v1UserResults.getString(5));
                                    row.add(v1UserResults.getString(6));
                                    row.add(v1UserResults.getString(7));
                                    row.add(v1UserResults.getString(8));
                                    row.add(v1UserResults.getString(9));
                                    row.add(v1UserResults.getString(10));
                                }
                            }
                        } while (v1UserResults.moveToNext());
                    }

                    onPartitionLoaded(v1UserPartition, newData);
                    if (loaderId != v1UserPartition) {
                        onPartitionLoaded(loaderId, data);
                    }
                } catch (Throwable exception) {
                    // If an error occurs, proceed normally
                    // Observed an IllegalStateException when reading string from directoryResults
                    onPartitionLoaded(loaderId, data);
                }
            } else {
                onPartitionLoaded(loaderId, data);
            }

            if (isSearchMode()) {
                int directorySearchMode = getDirectorySearchMode();
                if (directorySearchMode != DirectoryListLoader.SEARCH_MODE_NONE) {
                    if (mDirectoryListStatus == STATUS_NOT_LOADED) {
                        mDirectoryListStatus = STATUS_LOADING;
                        getLoaderManager().initLoader(DIRECTORY_LOADER_ID, null, this);
                    } else {
                        startLoading();
                    }
                }
            } else {
                mDirectoryListStatus = STATUS_NOT_LOADED;
//                getLoaderManager().destroyLoader(DIRECTORY_LOADER_ID);
            }
        }
    }

    protected boolean useScDirectory(boolean use) {
        if (mAdapter != null) {
            mUseScDirLoader = use;
            mAdapter.useScDirectory(use);
            // Re-trigger a search which now includes the SC directory
            if (use) {
                String query = mQueryString;
                mQueryString = null;
                setQueryString(query, false);
            }
            return true;
        }
        return false;
    }

    protected boolean useScDirectoryOrganization(boolean use) {
        if (mAdapter != null) {
            mAdapter.useScDirectoryOrganization(use);
            // Re-trigger a search which now includes the SC directory, filtered by organization
            String query = mQueryString;
            mQueryString = null;
            setQueryString(query, false);
            return true;
        }
        return false;
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    }

    protected void onPartitionLoaded(int partitionIndex, Cursor data) {
        if (partitionIndex >= mAdapter.getPartitionCount()) {
            // When we get unsolicited data, ignore it.  This could happen
            // when we are switching from search mode to the default mode.
            return;
        }

        mAdapter.changeCursor(partitionIndex, data);

        // Remove progress bar after loading
        CompositeCursorAdapter.Partition partition = mAdapter.getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            if (((DirectoryPartition)partition).getDirectoryId() == ScContactEntryListAdapter.SC_REMOTE_DIRECTORY) {
//                searchProgressVisibility(false);
            } else {
                // Check if the partition has been removed
                int scPartition = mAdapter.getPartitionByDirectoryId(ScContactEntryListAdapter.SC_REMOTE_DIRECTORY);

                if (scPartition == -1) {
//                    searchProgressVisibility(false);
                }
            }
        }

        setProfileHeader();
        showCount(partitionIndex, data);

        if (!isLoading()) {
            completeRestoreInstanceState();
        }
    }

    public boolean isLoading() {
        return mAdapter != null && mAdapter.isLoading() || isLoadingDirectoryList();

    }

    public boolean isLoadingDirectoryList() {
        return isSearchMode() && getDirectorySearchMode() != DirectoryListLoader.SEARCH_MODE_NONE
                && (mDirectoryListStatus == STATUS_NOT_LOADED || mDirectoryListStatus == STATUS_LOADING);
    }

    private void searchProgressVisibility(boolean visible) {
        final ProgressBar pb = (ProgressBar)mView.findViewById(R.id.progressBar);
        if (pb != null) {
            if (pb.getVisibility() == (visible ? View.VISIBLE : View.GONE)) {
                return;
            }

            if (visible) {
                pb.clearAnimation();
                pb.setVisibility(View.VISIBLE);
            } else {
                Animation fadeOut = new TranslateAnimation(0, 0, 0, -1 * pb.getHeight());
                fadeOut.setInterpolator(new AccelerateInterpolator());
                fadeOut.setDuration(150);
                fadeOut.setAnimationListener(new Animation.AnimationListener()
                {
                    public void onAnimationEnd(Animation animation)
                    {
                        pb.setVisibility(View.GONE);
                    }
                    public void onAnimationRepeat(Animation animation) {}
                    public void onAnimationStart(Animation animation) {}
                });
                pb.startAnimation(fadeOut);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mContactsPrefs.unregisterChangeListener();
        mAdapter.clearPartitions();
    }

    protected void reloadData() {
        removePendingDirectorySearchRequests();
        mAdapter.onDataReload();
        mLoadPriorityDirectoriesOnly = true;
        mForceLoad = true;
        startLoading();
    }

    /**
     * Configures the empty view. It is called when we are about to populate
     * the list with an empty cursor.
     */
    protected void prepareEmptyView() {
    }

    /**
     * Shows the count of entries included in the list. The default
     * implementation does nothing.
     */
    protected void showCount(int partitionIndex, Cursor data) {
    }

    /**
     * Shows a view at the top of the list with a pseudo local profile prompting the user to add
     * a local profile. Default implementation does nothing.
     */
    protected void setProfileHeader() {
        mUserProfileExists = false;
    }

    /**
     * Provides logic that dismisses this fragment. The default implementation
     * does nothing.
     */
    protected void finish() {
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        if (mSectionHeaderDisplayEnabled != flag) {
            mSectionHeaderDisplayEnabled = flag;
            if (mAdapter != null) {
                mAdapter.setSectionHeaderDisplayEnabled(flag);
            }
            configureVerticalScrollbar();
        }
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setVisibleScrollbarEnabled(boolean flag) {
        if (mVisibleScrollbarEnabled != flag) {
            mVisibleScrollbarEnabled = flag;
            configureVerticalScrollbar();
        }
    }

    public boolean isVisibleScrollbarEnabled() {
        return mVisibleScrollbarEnabled;
    }

    public void setVerticalScrollbarPosition(int position) {
        if (mVerticalScrollbarPosition != position) {
            mVerticalScrollbarPosition = position;
            configureVerticalScrollbar();
        }
    }

    private void configureVerticalScrollbar() {
        boolean hasScrollbar = isVisibleScrollbarEnabled() && isSectionHeaderDisplayEnabled();

        if (mListView != null) {
            mListView.setFastScrollEnabled(hasScrollbar);
            mListView.setFastScrollAlwaysVisible(hasScrollbar);
            mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
            mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

            // Visually it is too much, and functionally it is unnecessary
//            int leftPadding = 0;
//            int rightPadding = 0;
//            if (mVerticalScrollbarPosition == View.SCROLLBAR_POSITION_LEFT) {
//                leftPadding = mContext.getResources().getDimensionPixelOffset(
//                        R.dimen.list_visible_scrollbar_padding);
//            } else {
//                rightPadding = mContext.getResources().getDimensionPixelOffset(
//                        R.dimen.list_visible_scrollbar_padding);
//            }
//            mListView.setPadding(leftPadding, mListView.getPaddingTop(), rightPadding, mListView.getPaddingBottom());
        }
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
        configurePhotoLoader();
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }

    /**
     * Returns true if the list is supposed to visually highlight the selected item.
     */
    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
        if (mAdapter != null)
            mAdapter.setSelectionVisible(mSelectionVisible);
    }

    public void setQuickContactEnabled(boolean flag) {
        this.mQuickContactEnabled = flag;
        if (mAdapter != null)
            mAdapter.setQuickContactEnabled(mQuickContactEnabled);
    }

    public void setAdjustSelectionBoundsEnabled(boolean flag) {
        mAdjustSelectionBoundsEnabled = flag;
    }

    public void setIncludeProfile(boolean flag) {
        mIncludeProfile = flag;
        if(mAdapter != null) {
            mAdapter.setIncludeProfile(flag);
        }
    }

    /**
     * Enter/exit search mode.  By design, a fragment enters search mode only when it has a
     * non-empty query text, so the mode must be tightly related to the current query.
     * For this reason this method must only be called by {@link #setQueryString}.
     *
     * Also note this method doesn't call {@link #reloadData()}; {@link #setQueryString} does it.
     */
    protected void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            setSectionHeaderDisplayEnabled(!mSearchMode);

            if (!flag) {
                mDirectoryListStatus = STATUS_NOT_LOADED;
                getLoaderManager().destroyLoader(DIRECTORY_LOADER_ID);
            }

            if (mAdapter != null) {
                mAdapter.setPinnedPartitionHeadersEnabled(flag);
                mAdapter.setSearchMode(flag);

                mAdapter.clearPartitions();
                if (!flag) {
                    // If we are switching from search to regular display, remove all directory
                    // partitions after default one, assuming they are remote directories which
                    // should be cleaned up on exiting the search mode.
                    //TODO: do we need to run this?
//                    mAdapter.removeDirectoriesAfterDefault();
                    int scPartition = mAdapter.getPartitionByDirectoryId(
                            ScContactEntryListAdapter.SC_REMOTE_DIRECTORY);
                    if (scPartition != -1) {
                        mAdapter.removePartition(scPartition);
                    }
                } else {
                    int scPartition = mAdapter.getPartitionByDirectoryId(
                            ScContactEntryListAdapter.SC_EXACT_MATCH_ON_V1_USER);
                    if (scPartition == -1) {
                        mAdapter.addPartition(0, mAdapter.createV1UserPartition());
                    }

                    scPartition = mAdapter.getPartitionByDirectoryId(
                            ScContactEntryListAdapter.SC_EXISTING_CONVERSATIONS);
                    if (scPartition == -1) {
                        mAdapter.addPartition(1, mAdapter.createLocalScConversationPartition());
                    }

                    // Re-add the SC directory partition it has been removed
                    // Useful when clearing search field and continuing afterwards
                    scPartition = mAdapter.getPartitionByDirectoryId(
                            ScContactEntryListAdapter.SC_REMOTE_DIRECTORY);

                    if (scPartition == -1) {
                        mAdapter.addPartition(2, mAdapter.createRemoteScDirPartition());
                    }
                }

                mAdapter.configureDefaultPartition(false, flag);
            }

            if (mListView != null) {
                mListView.setFastScrollEnabled(!flag);
            }
        }
    }

    public final boolean isSearchMode() {
        return mSearchMode;
    }

    public final void setPhoneInput(boolean phoneInput) {
        mPhoneInput = phoneInput;
    }

    public final boolean isPhoneInput() {
        return mPhoneInput;
    }

    public final String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString, boolean delaySelection) {

        if (!TextUtils.equals(mQueryString, queryString)) {
            if (mShowEmptyListForEmptyQuery && mAdapter != null && mListView != null) {
                if (TextUtils.isEmpty(mQueryString)) {
                    // Restore the adapter if the query used to be empty.
                    mListView.setAdapter(mAdapter);
                } else if (TextUtils.isEmpty(queryString)) {
                    // Instantly clear the list view if the new query is empty.
                    mListView.setAdapter(null);
                }
            }

            mQueryString = queryString;
            setSearchMode(!TextUtils.isEmpty(mQueryString) || mShowEmptyListForEmptyQuery);

            if (mAdapter != null) {
                mAdapter.setQueryString(queryString);
                reloadData();
            }
        }
    }

    public void setShowEmptyListForNullQuery(boolean show) {
        mShowEmptyListForEmptyQuery = show;
    }

    public int getDirectoryLoaderId() {
        return DIRECTORY_LOADER_ID;
    }

    public int getDirectorySearchMode() {
        return mDirectorySearchMode;
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    public boolean isLegacyCompatibilityMode() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibilityMode(boolean flag) {
        mLegacyCompatibility = flag;
    }

    protected int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    protected void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        if (mAdapter != null) {
            mAdapter.setContactNameDisplayOrder(displayOrder);
        }
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        if (mAdapter != null) {
            mAdapter.setSortOrder(sortOrder);
        }
    }

    public void setDirectoryResultLimit(int limit) {
        mDirectoryResultLimit = limit;
    }

    protected boolean loadPreferences() {
        boolean changed = false;
        if (getContactNameDisplayOrder() != mContactsPrefs.getDisplayOrder()) {
            setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
            changed = true;
            if (this instanceof PhoneNumberPickerFragment) {
                boolean displayAlternative = !(getContactNameDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY);
                ScDirectoryLoader.reDisplay(displayAlternative, this.getOwnContext());
            }
        }

        if (getSortOrder() != mContactsPrefs.getSortOrder()) {
            setSortOrder(mContactsPrefs.getSortOrder());
            changed = true;
            if (this instanceof PhoneNumberPickerFragment) {
                boolean sortAlternative = !(getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY);
                ScDirectoryLoader.reSort(sortAlternative, getOwnContext());
            }
        }
        return changed;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        onCreateView(inflater, container);

        boolean searchMode = isSearchMode();
        mAdapter.setSearchMode(searchMode);
        mAdapter.configureDefaultPartition(false, searchMode);
        mAdapter.setPhotoLoader(mPhotoManager);
        mListView.setAdapter(mAdapter);

        if (!isSearchMode()) {
           mListView.setFocusableInTouchMode(true);
           mListView.requestFocus();
        }
        mView.setVisibility(View.VISIBLE);
        return mView;
    }

    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        mView = inflateView(inflater, container);

        mListView = (ListView)mView.findViewById(android.R.id.list);
        if (mListView == null) {
            throw new RuntimeException("Your content must have a ListView whose id attribute is 'android.R.id.list'");
        }

        View emptyView = mView.findViewById(android.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
//            if (emptyView instanceof ContactListEmptyView) {
//                mEmptyView = (ContactListEmptyView)emptyView;
//            }
        }
        mListView.setOnItemClickListener(this);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mListView.setFastScrollEnabled(!isSearchMode());

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        mListView.setDividerHeight(0);

        // We manually save/restore the listview state
        mListView.setSaveEnabled(false);

        configureVerticalScrollbar();
        configurePhotoLoader();

        getAdapter().setFragmentRootView(getView());

// TODO: not active because weights are 0, ContactListViewUtils.applyCardPaddingToView(getResources(), mListView, mView);

    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
//        if (getActivity() != null && getView() != null && !hidden) {
//            // If the padding was last applied when in a hidden state, it may have been applied
//            // incorrectly. Therefore we need to reapply it.
//            ContactListViewUtils.applyCardPaddingToView(getResources(), mListView, getView());
//        }
    }

    protected void configurePhotoLoader() {
        if (isPhotoLoaderEnabled() && mContext != null) {
            if (mPhotoManager == null) {
                mPhotoManager = ContactPhotoManagerNew.getInstance(mContext);
            }
            if (mListView != null) {
                mListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoManager);
            }
        }
    }

    protected void configureAdapter() {
        if (mAdapter == null) {
            return;
        }

        mAdapter.setQuickContactEnabled(mQuickContactEnabled);
        mAdapter.setAdjustSelectionBoundsEnabled(mAdjustSelectionBoundsEnabled);
        mAdapter.setIncludeProfile(mIncludeProfile);
        mAdapter.setQueryString(mQueryString);
        mAdapter.setDirectorySearchMode(mDirectorySearchMode);
        mAdapter.setPinnedPartitionHeadersEnabled(false);
        mAdapter.setContactNameDisplayOrder(mDisplayOrder);
        mAdapter.setSortOrder(mSortOrder);
        mAdapter.setSectionHeaderDisplayEnabled(mSectionHeaderDisplayEnabled);
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setDirectoryResultLimit(mDirectoryResultLimit);
        mAdapter.setDarkTheme(mDarkTheme);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else if (isPhotoLoaderEnabled()) {
            mPhotoManager.resume();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (TextUtils.isEmpty(getQueryString()) && position == 1) {
            // Item is "New group conversation"
        } else if (!inUserSelectionMode()) {
            hideSoftKeyboard();
        }

        int adjPosition = position - mListView.getHeaderViewsCount();
        if (adjPosition >= 0) {
            onItemClick(adjPosition, id);
        }
    }

    protected void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
//        if (view == mListView && hasFocus) {
//            hideSoftKeyboard();
//        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
//            hideSoftKeyboard();
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        removePendingDirectorySearchRequests();
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    public void onClose() {
        hideSoftKeyboard();
        finish();
    }

    /**
     * Restore the list state after the adapter is populated.
     */
    protected void completeRestoreInstanceState() {
        if (mListState != null) {
            mListView.onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

//    protected void setEmptyText(int resourceId) {
//        TextView empty = (TextView) getEmptyView().findViewById(R.id.emptyText);
//        empty.setText(mContext.getText(resourceId));
//        empty.setVisibility(View.VISIBLE);
//    }

    // TODO redesign into an async task or loader
    protected boolean isSyncActive() {
        return false;
    }

    protected boolean hasIccCard() {
        TelephonyManager telephonyManager =
                (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.hasIccCard();
    }

    public void setDarkTheme(boolean value) {
        mDarkTheme = value;
        if (mAdapter != null) 
            mAdapter.setDarkTheme(value);
    }

    /**
     * Processes a result returned by the contact picker.
     */
    public void onPickerResult(Intent data) {
        throw new UnsupportedOperationException("Picker result handler is not implemented.");
    }

    private ContactsPreferences.ChangeListener mPreferencesChangeListener =
            new ContactsPreferences.ChangeListener() {
        @Override
        public void onChange() {
            loadPreferences();
            reloadData();
        }
    };

    protected View findViewById(int viewResourceID) {
        View parent = getView();
        if (parent != null) {
            return parent.findViewById(viewResourceID);
        }
        return null;
    }

    /* Functionality for item selection mode */

    protected void setUserSelectionMode(boolean enabled) {
        mInUserSelectionMode = enabled;
    }

    protected boolean inUserSelectionMode() {
        return mInUserSelectionMode;
    }

    protected void setSelection(@Nullable CharSequence[] participants) {
        if (participants != null) {
            for (CharSequence participant : participants) {
                if (!TextUtils.isEmpty(participant)) {
                    mSelectedItems.add(participant.toString());
                }
            }
        }
    }

    protected void onClearSelection() {
    }

    protected void onAddToSelection(@Nullable String item, int position) {
    }

    protected void onRemoveFromSelection(@Nullable String item, int position) {
    }

    protected String getGroupName() {
        Bundle arguments = getArguments();
        return arguments != null ? Extra.GROUP.from(arguments) : null;
    }

    protected void selectItem(int position) {
        ListView listView = getListView();
        if (listView != null) {
            View view = listView.getChildAt(position - listView.getFirstVisiblePosition() + 1);
            boolean canToggle = true;
            if (view instanceof ContactListItemView) {
                if (!((ContactListItemView) view).isChecked()) {
                    canToggle = canAddToSelection();
                }
                if (canToggle) {
                    ((ContactListItemView) view).toggleChecked();
                    String uuid = (String) view.getTag(R.id.view_holder_userid);
                    if (((ContactListItemView) view).isChecked()) {
                        addToSelection(uuid);
                    } else {
                        removeFromSelection(uuid);
                    }
                }
            }
        }
    }

    @NonNull
    protected List<String> getSelectedItems() {
        return mSelectedItems;
    }

    protected int getCheckedCount() {
        return mSelectedItems.size();
    }

    protected boolean canAddToSelection() {
        return true;
    }

    protected void clearSelection() {
        mSelectedItems.clear();
        onClearSelection();

        ListView listView = getListView();
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view instanceof ContactListItemView) {
                    ((ContactListItemView) view).setChecked(false);
                }
            }
        }
    }

    protected synchronized void addToSelection(@Nullable String item) {
        if (TextUtils.isEmpty(item) || !canAddToSelection()) {
            return;
        }

        if (!mSelectedItems.contains(item)) {
            mSelectedItems.add(item);
            onAddToSelection(item, mSelectedItems.size() - 1);
        }
    }

    protected synchronized void removeFromSelection(@Nullable String item) {
        if (TextUtils.isEmpty(item)) {
            return;
        }

        for (int i = 0; i < mSelectedItems.size(); i++) {
            if (item.equals(mSelectedItems.get(i))) {
                onRemoveFromSelection(item, i);
                mSelectedItems.remove(item);
                break;
            }
        }

        ListView listView = getListView();
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view instanceof ContactListItemView) {
                    if (item.equals(view.getTag(R.id.view_holder_userid))) {
                        ((ContactListItemView) view).setChecked(false);
                    }
                }
            }
        }
    }

}
