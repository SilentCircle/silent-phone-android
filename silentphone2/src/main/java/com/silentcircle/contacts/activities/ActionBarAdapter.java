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

package com.silentcircle.contacts.activities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.LayoutParams;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SearchView;
import android.widget.SearchView.OnCloseListener;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Spinner;
import android.widget.TextView;

import com.silentcircle.contacts.activities.ActionBarAdapter.Listener.Action;
import com.silentcircle.contacts.list.ContactsRequest;
import com.silentcircle.silentphone2.R;

/**
 * Adapter for the action bar at the top of the Contacts activity.
 * 
 * Modified to handle API 21 toolbar and SlidingTabLayout. The action bar is a toolbar
 * which was set as support action bar, see {@code ScContactsMainActivity}. 
 */
public class ActionBarAdapter implements OnQueryTextListener, OnCloseListener, Spinner.OnItemSelectedListener {

    public interface Listener {
        public abstract class Action {
            public static final int CHANGE_SEARCH_QUERY = 0;
            public static final int START_SEARCH_MODE = 1;
            public static final int STOP_SEARCH_MODE = 2;
        }

        void onAction(int action);

        /**
         * Called when the user selects a tab.  The new tab can be obtained using
         * {@link #getCurrentTab}.
         */
        void onSelectedTabChanged();
    }

    private static final String EXTRA_KEY_SEARCH_MODE = "navBar.searchMode";
    private static final String EXTRA_KEY_QUERY = "navBar.query";
    private static final String EXTRA_KEY_SELECTED_TAB = "navBar.selectedTab";

    private static final String PERSISTENT_LAST_TAB = "actionBarAdapter.lastTab";

    private boolean mSearchMode;
    private String mQueryString;

    private SearchView mSearchView;

    private final Activity mContext;
    private final SharedPreferences mPrefs;

    private Listener mListener;

    private final ActionBar mActionBar;
    private Spinner mSpinner;

    public interface TabState {
        public static int ALL = 0;
        public static int GROUPS = 1;
        public static int FAVORITES = 2;

        public static int COUNT = 3;
        public static int DEFAULT = ALL;
    }

    private int mCurrentTab = TabState.DEFAULT;

    /**
     * Extension of ArrayAdapter to be used for the action bar navigation drop list.  It is not
     * possible to change the text appearance of a text item that is in the spinner header or
     * in the drop down list using a selector xml file.  The only way to differentiate the two
     * is if the view is gotten via {@link #getView(int, android.view.View, android.view.ViewGroup)} or
     * {@link #getDropDownView(int, android.view.View, android.view.ViewGroup)}.
     */
    private class CustomArrayAdapter extends ArrayAdapter<String> {

        public CustomArrayAdapter(Context context, int textResId) {
            super(context, textResId);
        }

        public View getView (int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getView(position, convertView, parent);
            textView.setTextAppearance(mContext, R.style.PeopleNavigationDropDownHeaderTextAppearance);
            return textView;
        }

        public View getDropDownView (int position, View convertView, ViewGroup parent) {
            TextView textView = (TextView) super.getDropDownView(position, convertView, parent);
            textView.setTextAppearance(mContext, R.style.PeopleNavigationDropDownTextAppearance);
            return textView;
        }
    }

    public ActionBarAdapter(Activity context, Listener listener, ActionBar actionBar, boolean isUsingTwoPanes) {
        mContext = context;
        mListener = listener;
        mActionBar = actionBar;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // On wide screens, show the tabs as text (instead of icons)
        if (isUsingTwoPanes) {
            mSpinner = (Spinner)mContext.findViewById(R.id.spinner);
            setupNavigationList();
        }

        // Set up search view.
        View customSearchView = LayoutInflater.from(mActionBar.getThemedContext()).inflate(R.layout.custom_action_bar, null);
        int searchViewWidth = mContext.getResources().getDimensionPixelSize(R.dimen.search_view_width);
        if (searchViewWidth == 0) {
            searchViewWidth = LayoutParams.MATCH_PARENT;
        }
        LayoutParams layoutParams = new LayoutParams(searchViewWidth, LayoutParams.WRAP_CONTENT);
        mSearchView = (SearchView) customSearchView.findViewById(R.id.search_view);
        // Since the {@link SearchView} in this app is "click-to-expand", set the below mode on the
        // {@link SearchView} so that the magnifying glass icon appears inside the editable text
        // field. (In the "click-to-expand" search pattern, the user must explicitly expand the
        // search field and already knows a search is being conducted, so the icon is redundant
        // and can go away once the user starts typing.)
        mSearchView.setIconifiedByDefault(true);
        mSearchView.setQueryHint(mContext.getString(R.string.hint_findContacts));
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setQuery(mQueryString, false);
        mActionBar.setCustomView(customSearchView, layoutParams);
    }

    public void initialize(Bundle savedState, ContactsRequest request) {
        if (savedState == null) {
            mSearchMode = request.isSearchMode();
            mQueryString = request.getQueryString();
            setCurrentTab(loadLastTabPreference());
        } else {
            mSearchMode = savedState.getBoolean(EXTRA_KEY_SEARCH_MODE);
            mQueryString = savedState.getString(EXTRA_KEY_QUERY);

            // Just set to the field here.  The listener will be notified by update().
            setCurrentTab(savedState.getInt(EXTRA_KEY_SELECTED_TAB), false);
        }
        // Show tabs or the expanded {@link SearchView}, depending on whether or not we are in
        // search mode.
        update();
        // Expanding the {@link SearchView} clears the query, so set the query from the
        // {@link ContactsRequest} after it has been expanded, if applicable.
        if (mSearchMode && !TextUtils.isEmpty(mQueryString)) {
            setQueryString(mQueryString);
        }
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        setCurrentTab(pos);
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    /**
     * Change the current tab, and notify the listener.
     */
    public void setCurrentTab(int tab) {
        setCurrentTab(tab, true);
    }

    /**
     * Change the current tab
     */
    public void setCurrentTab(int tab, boolean notifyListener) {
        if (tab >= TabState.COUNT)
            tab = 0;
        if (tab == mCurrentTab) {
            return;
        }
        mCurrentTab = tab;
        if (mSpinner != null)
            mSpinner.setSelection(mCurrentTab);
        if (notifyListener && mListener != null)
            mListener.onSelectedTabChanged();
        saveLastTabPreference(mCurrentTab);
    }

    public int getCurrentTab() {
        return mCurrentTab;
    }

    /**
     * @return Whether in search mode, i.e. if the search view is visible/expanded.
     *
     * Note even if the action bar is in search mode, if the query is empty, the search fragment
     * will not be in search mode.
     */
    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            update();
            if (mSearchView == null) {
                return;
            }
            if (mSearchMode) {
                setFocusOnSearchView();
            } else {
                mSearchView.setQuery(null, false);
            }
        } else if (flag) {
            // Everything is already set up. Still make sure the keyboard is up
            if (mSearchView != null) 
                setFocusOnSearchView();
        }
    }

    public String getQueryString() {
        return mSearchMode ? mQueryString : null;
    }

    public void setQueryString(String query) {
        mQueryString = query;
        if (mSearchView != null) {
            mSearchView.setQuery(query, false);
        }
    }

    /** @return true if the "UP" icon is showing. */
    public boolean isUpShowing() {
        return mSearchMode; // Only shown on the search mode.
    }

    private void setupNavigationList() {
        ArrayAdapter<String> navAdapter = new CustomArrayAdapter(mContext, R.layout.people_navigation_item);
        navAdapter.add(mContext.getString(R.string.contactsAllLabel));
        navAdapter.add(mContext.getString(R.string.contactsGroupsLabel));
        navAdapter.add(mContext.getString(R.string.contactsFavoritesLabel));
        mSpinner.setAdapter(navAdapter);
        mSpinner.setOnItemSelectedListener(this);
    }

    private void updateDisplayOptions() {
        // All the flags we may change in this method.
        final int MASK = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_CUSTOM;

        // The current flags set to the action bar.  (only the ones that we may change here)
        final int current = mActionBar.getDisplayOptions() & MASK;

        // Build the new flags...
        int newFlags = 0;
        newFlags |= ActionBar.DISPLAY_SHOW_TITLE;
        if (mSearchMode) {
            newFlags |= ActionBar.DISPLAY_SHOW_HOME;
            newFlags |= ActionBar.DISPLAY_HOME_AS_UP;
            newFlags |= ActionBar.DISPLAY_SHOW_CUSTOM;
        }
        mActionBar.setHomeButtonEnabled(mSearchMode);
        mActionBar.setTitle(mContext.getString(R.string.app_name_sca));

        if (current != newFlags) {
            // Pass the mask here to preserve other flags that we're not interested here.
            mActionBar.setDisplayOptions(newFlags, MASK);
        }
    }

    private void update() {
        boolean isIconifiedChanging = mSearchView.isIconified() == mSearchMode;
        if (mSearchMode) {
            setFocusOnSearchView();
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // expanding the {@link SearchView} when a search is initiated. Note that a side effect
            // of this method is that the {@link SearchView} query text is set to empty string.
            if (isIconifiedChanging) {
                mSearchView.onActionViewExpanded();
            }
            if (mListener != null) {
                mListener.onAction(Action.START_SEARCH_MODE);
            }
        } 
        else {
            mActionBar.setTitle(null);
            // Since we have the {@link SearchView} in a custom action bar, we must manually handle
            // collapsing the {@link SearchView} when search mode is exited.
            if (isIconifiedChanging) {
                mSearchView.onActionViewCollapsed();
            }
            if (mListener != null) {
                mListener.onAction(Action.STOP_SEARCH_MODE);
                mListener.onSelectedTabChanged();
            }
        }
        updateDisplayOptions();
    }

    @Override
    public boolean onQueryTextChange(String queryString) {
        // TODO: Clean up SearchView code because it keeps setting the SearchView query,
        // invoking onQueryChanged, setting up the fragment again, invalidating the options menu,
        // storing the SearchView again, and etc... unless we add in the early return statements.
        if (queryString.equals(mQueryString)) {
            return false;
        }
        mQueryString = queryString;
        if (!mSearchMode) {
            if (!TextUtils.isEmpty(queryString)) {
                setSearchMode(true);
            }
        } else if (mListener != null) {
            mListener.onAction(Action.CHANGE_SEARCH_QUERY);
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        // When the search is "committed" by the user, then hide the keyboard so the user can
        // more easily browse the list of results.
        if (mSearchView != null) {
            InputMethodManager imm = (InputMethodManager) mContext.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
            }
            mSearchView.clearFocus();
        }
        return true;
    }

    @Override
    public boolean onClose() {
        setSearchMode(false);
        return false;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(EXTRA_KEY_SEARCH_MODE, mSearchMode);
        outState.putString(EXTRA_KEY_QUERY, mQueryString);
        outState.putInt(EXTRA_KEY_SELECTED_TAB, mCurrentTab);
    }

    /**
     * Clears the focus from the {@link SearchView} if we are in search mode.
     * This will suppress the IME if it is visible.
     */
    public void clearFocusOnSearchView() {
        if (isSearchMode()) {
            if (mSearchView != null) {
                mSearchView.clearFocus();
            }
        }
    }

    public void setFocusOnSearchView() {
        mSearchView.requestFocus();
        mSearchView.setIconified(false); // Workaround for the "IME not popping up" issue.
    }

    private void saveLastTabPreference(int tab) {
        mPrefs.edit().putInt(PERSISTENT_LAST_TAB, tab).apply();
    }

    private int loadLastTabPreference() {
        try {
            return mPrefs.getInt(PERSISTENT_LAST_TAB, TabState.DEFAULT);
        } catch (IllegalArgumentException e) {
            // Preference is corrupt?
            return TabState.DEFAULT;
        }
    }
}
