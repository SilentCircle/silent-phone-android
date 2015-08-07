/*
Copyright (C) 2015, Silent Circle, LLC. All rights reserved.

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
 * Copyright (C) 2009 The Android Open Source Project
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

package com.silentcircle.contacts.quickcontact;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.SimpleOnPageChangeListener;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.silentcircle.contacts.Collapser;
import com.silentcircle.contacts.model.Contact;
import com.silentcircle.contacts.model.ContactLoader;
import com.silentcircle.contacts.model.RawContact;
import com.silentcircle.contacts.model.dataitem.DataItem;
import com.silentcircle.contacts.utils.Constants;
import com.silentcircle.contacts.utils.ImageViewDrawableSetter;
import com.silentcircle.contacts.utils.SchedulingUtils;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Email;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Phone;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.SipAddress;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.StructuredPostal;
import com.silentcircle.silentcontacts2.ScContactsContract.CommonDataKinds.Website;
import com.silentcircle.silentcontacts2.ScContactsContract.QuickContact;
import com.silentcircle.silentcontacts2.ScContactsContract.RawContacts;
import com.silentcircle.silentphone2.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// TODO: Save selected tab index during rotation

/**
 * Mostly translucent {@link android.app.Activity} that shows QuickContact dialog. It loads
 * data asynchronously, and then shows a popup with details centered around
 * {@link android.content.Intent#getSourceBounds()}.
 */
public class QuickContactActivityV19 extends ActionBarActivity {
    private static final String TAG = "QuickContact";

    private static final boolean TRACE_LAUNCH = false;
    private static final String TRACE_TAG = "quickcontact";
    private static final int POST_DRAW_WAIT_DURATION = 60;

    private Uri mLookupUri;
    private String[] mExcludeMimes;
    private List<String> mSortedActionMimeTypes = Lists.newArrayList();

    private FloatingChildLayout mFloatingLayout;

    private View mPhotoContainer;
    private ViewGroup mTrack;
    private HorizontalScrollView mTrackScroller;
    private View mSelectedTabRectangle;
    private View mLineAfterTrack;

    private ImageView mOpenDetailsImage;
    private ImageButton mOpenDetailsPushLayerButton;
    private ViewPager mListPager;

    private ContactLoader mContactLoader;

    private final ImageViewDrawableSetter mPhotoSetter = new ImageViewDrawableSetter();

    /**
     * Keeps the default action per mimetype. Empty if no default actions are set
     */
    private HashMap<String, Action> mDefaultsMap = new HashMap<String, Action>();

    /**
     * Set of {@link Action} that are associated with the aggregate currently
     * displayed by this dialog, represented as a map from {@link String}
     * MIME-type to a list of {@link Action}.
     */
    private ActionMultiMap mActions = new ActionMultiMap();

    /**
     * {@link #LEADING_MIMETYPES} and {@link #TRAILING_MIMETYPES} are used to sort MIME-types.
     *
     * <p>The MIME-types in {@link #LEADING_MIMETYPES} appear in the front of the dialog,
     * in the order specified here.</p>
     *
     * <p>The ones in {@link #TRAILING_MIMETYPES} appear in the end of the dialog, in the order
     * specified here.</p>
     *
     * <p>The rest go between them, in the order in the array.</p>
     */
    private static final List<String> LEADING_MIMETYPES = Lists.newArrayList(
            Phone.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE);

    /** See {@link #LEADING_MIMETYPES}. */
    private static final List<String> TRAILING_MIMETYPES = Lists.newArrayList(
            StructuredPostal.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE);

    /** Id for the background loader */
    private static final int LOADER_ID = 0;


    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (TRACE_LAUNCH) android.os.Debug.startMethodTracing(TRACE_TAG);

        // Parse intent
        final Intent intent = getIntent();

        Uri lookupUri = intent.getData();

        mLookupUri = Preconditions.checkNotNull(lookupUri, "missing lookupUri");

        mExcludeMimes = intent.getStringArrayExtra(QuickContact.EXTRA_EXCLUDE_MIMES);

        mContactLoader = (ContactLoader) getLoaderManager().initLoader(LOADER_ID, null, mLoaderCallbacks);

        // Show QuickContact in front of soft input
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM, WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);

        setContentView(R.layout.quickcontact_activity);

        mFloatingLayout = (FloatingChildLayout) findViewById(R.id.floating_layout);
        mTrack = (ViewGroup) findViewById(R.id.track);
        mTrackScroller = (HorizontalScrollView) findViewById(R.id.track_scroller);
        mOpenDetailsImage = (ImageView) findViewById(R.id.contact_details_image);
        mOpenDetailsPushLayerButton = (ImageButton) findViewById(R.id.open_details_push_layer);
        mListPager = (ViewPager) findViewById(R.id.item_list_pager);
        mSelectedTabRectangle = findViewById(R.id.selected_tab_rectangle);
        mLineAfterTrack = findViewById(R.id.line_after_track);

        mFloatingLayout.setOnOutsideTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleOutsideTouch();
                return true;
            }
        });

        final OnClickListener openDetailsClickHandler = new OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, mLookupUri);
                mContactLoader.cacheResult();
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                startActivity(intent);
                close(false);
            }
        };
        mOpenDetailsPushLayerButton.setOnClickListener(openDetailsClickHandler);
        mListPager.setAdapter(new ViewPagerAdapter(getFragmentManager()));
        mListPager.setOnPageChangeListener(new PageChangeListener());

        final Rect sourceBounds = intent.getSourceBounds();
        if (sourceBounds != null) {
            mFloatingLayout.setChildTargetScreen(sourceBounds);
        }

        // find and prepare correct header view
        mPhotoContainer = findViewById(R.id.photo_container);
        setHeaderNameText(R.id.name, R.string.missing_name);

        SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
            @Override
            public void run() {
                mFloatingLayout.fadeInBackground();
            }
        });
    }

    private void handleOutsideTouch() {
        if (mFloatingLayout.isContentFullyVisible()) {
            close(true);
        }
    }

    private void close(boolean withAnimation) {
        // cancel any pending queries
        getLoaderManager().destroyLoader(LOADER_ID);

        if (withAnimation) {
            mFloatingLayout.fadeOutBackground();
            final boolean animated = mFloatingLayout.hideContent(new Runnable() {
                @Override
                public void run() {
                    // Wait until the final animation frame has been drawn, otherwise
                    // there is jank as the framework transitions to the next Activity.
                    SchedulingUtils.doAfterDraw(mFloatingLayout, new Runnable() {
                        @Override
                        public void run() {
                            // Unfortunately, we need to also use postDelayed() to wait a moment
                            // for the frame to be drawn, else the framework's activity-transition
                            // animation will kick in before the final frame is available to it.
                            // This seems unavoidable.  The problem isn't merely that there is no
                            // post-draw listener API; if that were so, it would be sufficient to
                            // call post() instead of postDelayed().
                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, POST_DRAW_WAIT_DURATION);
                        }
                    });
                }
            });
            if (!animated) {
                // If we were in the wrong state, simply quit (this can happen for example
                // if the user pushes BACK before anything has loaded)
                finish();
            }
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        close(true);
    }

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(int id, int resId) {
        setHeaderNameText(id, getText(resId));
    }

    /** Assign this string to the view if it is not empty. */
    private void setHeaderNameText(int id, CharSequence value) {
        final View view = mPhotoContainer.findViewById(id);
        if (view instanceof TextView) {
            if (!TextUtils.isEmpty(value)) {
                ((TextView)view).setText(value);
            }
        }
    }

    /**
     * Check if the given MIME-type appears in the list of excluded MIME-types
     * that the most-recent caller requested.
     */
    private boolean isMimeExcluded(String mimeType) {
        if (mExcludeMimes == null) return false;
        for (String excludedMime : mExcludeMimes) {
            if (TextUtils.equals(excludedMime, mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle the result from the ContactLoader
     */
    private void bindData(Contact data) {
        final ResolveCache cache = ResolveCache.getInstance(this);
        final Context context = this;

        mOpenDetailsImage.setVisibility(isMimeExcluded(RawContacts.CONTENT_ITEM_TYPE) ? View.GONE : View.VISIBLE);

        mDefaultsMap.clear();

        final ImageView photoView = (ImageView) mPhotoContainer.findViewById(R.id.photo);
        mPhotoSetter.setupContactPhoto(data, photoView);

        for (RawContact rawContact : data.getRawContacts()) {
            for (DataItem dataItem : rawContact.getDataItems()) {
                final String mimeType = dataItem.getMimeType();

                // Skip this data item if MIME-type excluded
                if (isMimeExcluded(mimeType))
                    continue;

                final long dataId = dataItem.getId();
                final boolean isPrimary = dataItem.isPrimary();
                final boolean isSuperPrimary = dataItem.isSuperPrimary();

                if (dataItem.getDataKind() != null) {
                    // Build an action for this data entry, find a mapping to a UI
                    // element, build its summary from the cursor, and collect it
                    // along with all others of this MIME-type.
                    final Action action = new DataAction(context, dataItem);
                    final boolean wasAdded = considerAdd(action, cache, isSuperPrimary);
                    if (wasAdded) {
                        // Remember the default
                        if (isSuperPrimary || (isPrimary && (mDefaultsMap.get(mimeType) == null))) {
                            mDefaultsMap.put(mimeType, action);
                        }
                    }
                }
            }
        }

        // Collapse Action Lists (remove e.g. duplicate e-mail addresses from different sources)
        for (List<Action> actionChildren : mActions.values()) {
            Collapser.collapseList(actionChildren);
        }
        setHeaderNameText(R.id.name, data.getDisplayName());

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());
        mSortedActionMimeTypes.clear();
        // First, add LEADING_MIMETYPES, which are most common.
        for (String mimeType : LEADING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
            }
        }

        // Add all the remaining ones that are not TRAILING
        for (String mimeType : containedTypes.toArray(new String[containedTypes.size()])) {
            if (!TRAILING_MIMETYPES.contains(mimeType)) {
                mSortedActionMimeTypes.add(mimeType);
                containedTypes.remove(mimeType);
            }
        }

        // Then, add TRAILING_MIMETYPES, which are least common.
        for (String mimeType : TRAILING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                containedTypes.remove(mimeType);
                mSortedActionMimeTypes.add(mimeType);
            }
        }

        // Add buttons for each mimetype
        mTrack.removeAllViews();
        for (String mimeType : mSortedActionMimeTypes) {
            final View actionView = inflateAction(mimeType, cache, mTrack);
            mTrack.addView(actionView);
        }

        final boolean hasData = !mSortedActionMimeTypes.isEmpty();
        mTrackScroller.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mSelectedTabRectangle.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mLineAfterTrack.setVisibility(hasData ? View.VISIBLE : View.GONE);
        mListPager.setVisibility(hasData ? View.VISIBLE : View.GONE);
    }

    /**
     * Consider adding the given {@link Action}, which will only happen if
     * {@link android.content.pm.PackageManager} finds an application to handle
     * {@link Action#getIntent()}.
     * @param action the action to handle
     * @param resolveCache cache of applications that can handle actions
     * @param front indicates whether to add the action to the front of the list
     * @return true if action has been added
     */
    private boolean considerAdd(Action action, ResolveCache resolveCache, boolean front) {
        if (resolveCache.hasResolve(action)) {
            mActions.put(action.getMimeType(), action, front);
            return true;
        }
        return false;
    }

    /**
     * Inflate the in-track view for the action of the given MIME-type, collapsing duplicate values.
     * Will use the icon provided by the {@link com.silentcircle.contacts.model.dataitem.DataKind}.
     */
    private View inflateAction(String mimeType, ResolveCache resolveCache, ViewGroup root) {
        final CheckableImageView typeView = (CheckableImageView) getLayoutInflater().inflate(
                R.layout.quickcontact_track_button, root, false);

        List<Action> children = mActions.get(mimeType);
        typeView.setTag(mimeType);
        final Action firstInfo = children.get(0);

        // Set icon and listen for clicks
        final CharSequence descrip = resolveCache.getDescription(firstInfo);
        final Drawable icon = resolveCache.getIcon(firstInfo);
        typeView.setChecked(false);
        typeView.setContentDescription(descrip);
        typeView.setImageDrawable(icon);
        typeView.setOnClickListener(mTypeViewClickListener);

        return typeView;
    }

    private CheckableImageView getActionViewAt(int position) {
        return (CheckableImageView) mTrack.getChildAt(position);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        final QuickContactListFragment listFragment = (QuickContactListFragment) fragment;
        listFragment.setListener(mListFragmentListener);
    }

    private LoaderCallbacks<Contact> mLoaderCallbacks =  new LoaderCallbacks<Contact>() {
        @Override
        public void onLoaderReset(Loader<Contact> loader) {
        }

        @Override
        public void onLoadFinished(Loader<Contact> loader, Contact data) {
            if (isFinishing()) {
                close(false);
                return;
            }
            if (data.isError()) {
                // This shouldn't ever happen, so throw an exception. The {@link ContactLoader}
                // should log the actual exception.
                throw new IllegalStateException("Failed to load contact", data.getException());
            }
            if (data.isNotFound()) {
                Log.i(TAG, "No contact found: " + ((ContactLoader)loader).getLookupUri());
                Toast.makeText(QuickContactActivityV19.this, R.string.invalidContactMessage, Toast.LENGTH_LONG).show();
                close(false);
                return;
            }
            bindData(data);
            mListPager.getAdapter().notifyDataSetChanged();  // bind data modifies MIME type list

            if (TRACE_LAUNCH) android.os.Debug.stopMethodTracing();
            if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
                Log.d(Constants.PERFORMANCE_TAG, "QuickContact shown");
            }

            // Data bound and ready, pull curtain to show. Put this on the Handler to ensure
            // that the layout passes are completed
            SchedulingUtils.doAfterLayout(mFloatingLayout, new Runnable() {
                @Override
                public void run() {
                    mFloatingLayout.showContent(new Runnable() {
                        @Override
                        public void run() {
                            mContactLoader.upgradeToFullContact();
                        }
                    });
                }
            });
        }

        @Override
        public Loader<Contact> onCreateLoader(int id, Bundle args) {
            if (mLookupUri == null) {
                Log.wtf(TAG, "Lookup uri wasn't initialized. Loader was started too early");
            }
            return new ContactLoader(getApplicationContext(), mLookupUri,
                    false /*loadGroupMetaData*/, false /*loadStreamItems*/,
                    false /*loadInvitableAccountTypes*/, false /*postViewNotification*/,
                    true /*computeFormattedPhoneNumber*/);
        }
    };

    /** A type (e.g. Call/Addresses was clicked) */
    private final OnClickListener mTypeViewClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            final CheckableImageView actionView = (CheckableImageView)view;
            final String mimeType = (String) actionView.getTag();
            int index = mSortedActionMimeTypes.indexOf(mimeType);
            mListPager.setCurrentItem(index, true);
        }
    };

    private class ViewPagerAdapter extends FragmentPagerAdapter {
        public ViewPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public Fragment getItem(int position) {
            QuickContactListFragment fragment = new QuickContactListFragment();
            final String mimeType = mSortedActionMimeTypes.get(position);
            final List<Action> actions = mActions.get(mimeType);
            fragment.setActions(actions);
            return fragment;
        }

        @Override
        public int getCount() {
            return mSortedActionMimeTypes.size();
        }
    }

    private class PageChangeListener extends SimpleOnPageChangeListener {
        @Override
        public void onPageSelected(int position) {
            final CheckableImageView actionView = getActionViewAt(position);
            mTrackScroller.requestChildRectangleOnScreen(actionView,
                    new Rect(0, 0, actionView.getWidth(), actionView.getHeight()), false);
            renderSelectedRectangle(position, 0);
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            renderSelectedRectangle(position, positionOffset);
        }

        private void renderSelectedRectangle(int position, float positionOffset) {
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams) mSelectedTabRectangle.getLayoutParams();
            final int width = layoutParams.width;
            layoutParams.leftMargin = (int) ((position + positionOffset) * width);
            mSelectedTabRectangle.setLayoutParams(layoutParams);
        }
    }

    private final QuickContactListFragment.Listener mListFragmentListener = new QuickContactListFragment.Listener() {
        @Override
        public void onOutsideClick() {
            // If there is no background, we want to dismiss, because to the user it seems
            // like he had touched outside. If the ViewPager is solid however, those taps
            // must be ignored
            final boolean isTransparent = mListPager.getBackground() == null;
            if (isTransparent) handleOutsideTouch();
        }

        @Override
        public void onItemClicked(final Action action, final boolean alternate) {
            final Runnable startAppRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(alternate ? action.getAlternateIntent() : action.getIntent());
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(QuickContactActivityV19.this, R.string.quickcontact_missing_app, Toast.LENGTH_SHORT).show();
                    }

                    close(false);
                }
            };
            // Defer the action to make the window properly repaint
            new Handler().post(startAppRunnable);
        }
    };
}
