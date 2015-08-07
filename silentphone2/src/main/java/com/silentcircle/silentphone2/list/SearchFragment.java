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
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ListView;

import com.silentcircle.common.animation.AnimUtils;
import com.silentcircle.common.list.ContactListItemView;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.common.util.ViewUtil;
import com.silentcircle.contacts.list.PhoneNumberPickerFragment;
import com.silentcircle.contacts.list.ScContactEntryListAdapter;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.activities.DialerActivity;

public class SearchFragment extends PhoneNumberPickerFragment {

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
    public void onAttach(Activity activity) {
        super.onAttach(activity);

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

    @Override
    protected ScContactEntryListAdapter createListAdapter() {
        DialerPhoneNumberListAdapter adapter = new DialerPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(super.usesCallableUri());
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final DialerPhoneNumberListAdapter adapter = (DialerPhoneNumberListAdapter) getAdapter();
        final int shortcutType = adapter.getShortcutTypeFromPosition(position);
        final OnPhoneNumberPickerActionListener listener;

        switch (shortcutType) {
            case DialerPhoneNumberListAdapter.SHORTCUT_INVALID:
                super.onItemClick(position, id);
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
                final Intent intent = DialerActivity.getAddNumberToContactIntent(number);
                DialerUtils.startActivityWithErrorToast(getActivity(), intent,
                        R.string.add_contact_not_available);
                break;
//            case DialerPhoneNumberListAdapter.SHORTCUT_MAKE_VIDEO_CALL:
//                listener = getOnPhoneNumberPickerListener();
//                if (listener != null) {
//                    listener.onCallNumberDirectly(getQueryString(), true /* isVideoCall */);
//                }
//                break;
        }
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
}
