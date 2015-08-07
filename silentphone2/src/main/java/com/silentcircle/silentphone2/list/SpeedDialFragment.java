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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout.LayoutParams;

import com.silentcircle.common.list.ContactTileView;
import com.silentcircle.common.list.OnPhoneNumberPickerActionListener;
import com.silentcircle.common.util.DialerUtils;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.contacts.ContactTileLoaderFactory;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This fragment displays the user's favorite/frequent contacts in a grid.
 */
public class SpeedDialFragment extends Fragment implements OnItemClickListener,
        PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener {

    /**
     * By default, the animation code assumes that all items in a list view are of the same height
     * when animating new list items into view (e.g. from the bottom of the screen into view).
     * This can cause incorrect translation offsets when a item that is larger or smaller than
     * other list item is removed from the list. This key is used to provide the actual height
     * of the removed object so that the actual translation appears correct to the user.
     */
    private static final long KEY_REMOVED_ITEM_HEIGHT = Long.MAX_VALUE;

    private static final String TAG = SpeedDialFragment.class.getSimpleName();
    private static final boolean DEBUG = false;

    private int mAnimationDuration;

    /**
     * Used with LoaderManager.
     */
    private static int LOADER_ID_CONTACT_TILE = 1;

    public interface HostInterface {
        void setDragDropController(DragDropController controller);
    }

    private class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onCreateLoader.");
            return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(getActivity());
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoadFinished, count: " + data.getCount());
            mContactTileAdapter.setContactCursor(data);
            setEmptyViewVisibility(mContactTileAdapter.getCount() == 0);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
        }
    }

    private class ContactTileAdapterListener implements ContactTileView.Listener {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            if (mPhoneNumberPickerActionListener != null) {
                mPhoneNumberPickerActionListener.onPickPhoneNumberAction(contactUri);
            }
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            if (mPhoneNumberPickerActionListener != null) {
                mPhoneNumberPickerActionListener.onCallNumberDirectly(phoneNumber);
            }
        }

        @Override
        public int getApproximateTileWidth() {
            final View view = getView();
            if (view != null)
                return view.getWidth();
            return 0;
        }
    }

    private class ScrollListener implements ListView.OnScrollListener {
        @Override
        public void onScroll(AbsListView view,
                int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (mActivityScrollListener != null) {
                mActivityScrollListener.onListFragmentScroll(firstVisibleItem, visibleItemCount,
                    totalItemCount);
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mActivityScrollListener.onListFragmentScrollStateChange(scrollState);
        }
    }

    private OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener;

    private OnListFragmentScrolledListener mActivityScrollListener;
    private PhoneFavoritesTileAdapter mContactTileAdapter;

    private PhoneFavoriteListView mListView;

    private View mContactTileFrame;

//    private TileInteractionTeaserView mTileInteractionTeaserView;

    private final HashMap<Long, Integer> mItemIdTopMap = new HashMap<>();
    private final HashMap<Long, Integer> mItemIdLeftMap = new HashMap<>();

    /**
     * Layout used when there are no favorites.
     */
    private View mEmptyView;

    private final ContactTileView.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();
    private final ScrollListener mScrollListener = new ScrollListener();

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Construct two base adapters which will become part of PhoneFavoriteMergedAdapter.
        // We don't construct the resultant adapter at this moment since it requires LayoutInflater
        // that will be available on onCreateView().
        mContactTileAdapter = new PhoneFavoritesTileAdapter(activity, mContactTileAdapterListener, this);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManagerNew.getInstance(activity));
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mAnimationDuration = getResources().getInteger(R.integer.fade_duration);
    }

    @Override
    public void onResume() {
        super.onResume();

        getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE).forceLoad();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View parentView = inflater.inflate(R.layout.speed_dial_fragment, container, false);

        mListView = (PhoneFavoriteListView) parentView.findViewById(R.id.contact_tile_list);
        mListView.setOnItemClickListener(this);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.getDragDropController().addOnDragDropListener(mContactTileAdapter);

        final ImageView dragShadowOverlay =
                (ImageView) getActivity().findViewById(R.id.contact_tile_drag_shadow_overlay);
        if (dragShadowOverlay != null)
            mListView.setDragShadowOverlay(dragShadowOverlay);
        else
            Log.wtf(TAG, "dragShadowOverly is null!!");

        mEmptyView = parentView.findViewById(R.id.empty_list_view);
        DialerUtils.configureEmptyListView(
                mEmptyView, R.drawable.empty_speed_dial, R.string.speed_dial_empty, getResources());

        mContactTileFrame = parentView.findViewById(R.id.contact_tile_frame);

//        mTileInteractionTeaserView = (TileInteractionTeaserView) inflater.inflate(
//                R.layout.tile_interactions_teaser_view, mListView, false);

        final LayoutAnimationController controller = new LayoutAnimationController(
                AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_in));
        controller.setDelay(0);
        mListView.setLayoutAnimation(controller);
        mListView.setAdapter(mContactTileAdapter);

        mListView.setOnScrollListener(mScrollListener);
        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        return parentView;
    }

    @SuppressWarnings("unused")
    public boolean hasFrequents() {
        return mContactTileAdapter != null && mContactTileAdapter.getNumFrequents() > 0;
    }

    void setEmptyViewVisibility(final boolean visible) {
        final int previousVisibility = mEmptyView.getVisibility();
        final int newVisibility = visible ? View.VISIBLE : View.GONE;

        if (previousVisibility != newVisibility) {
            final LayoutParams params = (LayoutParams) mContactTileFrame
                    .getLayoutParams();
            params.height = visible ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
            mContactTileFrame.setLayoutParams(params);
            mEmptyView.setVisibility(newVisibility);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        final Activity activity = getActivity();

        try {
            mActivityScrollListener = (OnListFragmentScrolledListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnListFragmentScrolledListener");
        }

        try {
            OnDragDropListener listener = (OnDragDropListener) activity;
            mListView.getDragDropController().addOnDragDropListener(listener);
            ((HostInterface) activity).setDragDropController(mListView.getDragDropController());
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnDragDropListener and HostInterface");
        }

        try {
            mPhoneNumberPickerActionListener = (OnPhoneNumberPickerActionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement PhoneFavoritesFragment.listener");
        }

        // Use initLoader() instead of restartLoader() to refraining unnecessary reload.
        // This method call implicitly assures ContactTileLoaderListener's onLoadFinished() will
        // be called, on which we'll check if "all" contacts should be reloaded again or not.
        getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);
    }

    /**
     * {@inheritDoc}
     *
     * This is only effective for elements provided by {@link #mContactTileAdapter}.
     * {@link #mContactTileAdapter} has its own logic for click events.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        if (position <= contactTileAdapterCount) {
            Log.e(TAG, "onItemClick() event for unexpected position. "
                    + "The position " + position + " is before \"all\" section. Ignored.");
        }
    }

    /**
     * Cache the current view offsets into memory. Once a relayout of views in the ListView
     * has happened due to a dataset change, the cached offsets are used to create animations
     * that slide views from their previous positions to their new ones, to give the appearance
     * that the views are sliding into their new positions.
     */
    private void saveOffsets(int removedItemHeight) {
        final int firstVisiblePosition = mListView.getFirstVisiblePosition();
        if (DEBUG) {
            Log.d(TAG, "Child count : " + mListView.getChildCount());
        }
        for (int i = 0; i < mListView.getChildCount(); i++) {
            final View child = mListView.getChildAt(i);
            final int position = firstVisiblePosition + i;
            final long itemId = mContactTileAdapter.getItemId(position);
            if (DEBUG) {
                Log.d(TAG, "Saving itemId: " + itemId + " for list view child " + i + " Top: "
                        + child.getTop());
            }
            mItemIdTopMap.put(itemId, child.getTop());
            mItemIdLeftMap.put(itemId, child.getLeft());
        }

        mItemIdTopMap.put(KEY_REMOVED_ITEM_HEIGHT, removedItemHeight);
    }

    /*
     * Performs animations for the gridView
     */
    private void animateGridView(final long... idsInPlace) {
        if (mItemIdTopMap.isEmpty()) {
            // Don't do animations if the database is being queried for the first time and
            // the previous item offsets have not been cached, or the user hasn't done anything
            // (dragging, swiping etc) that requires an animation.
            return;
        }

        final ViewTreeObserver observer = mListView.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                final int firstVisiblePosition = mListView.getFirstVisiblePosition();
                final AnimatorSet animSet = new AnimatorSet();
                final ArrayList<Animator> animators = new ArrayList<>();
                for (int i = 0; i < mListView.getChildCount(); i++) {
                    final View child = mListView.getChildAt(i);
                    int position = firstVisiblePosition + i;

                    final long itemId = mContactTileAdapter.getItemId(position);

                    if (containsId(idsInPlace, itemId)) {
                        animators.add(ObjectAnimator.ofFloat(
                                child, "alpha", 0.0f, 1.0f));
                        break;
                    } else {
                        Integer startTop = mItemIdTopMap.get(itemId);
                        Integer startLeft = mItemIdLeftMap.get(itemId);
                        final int top = child.getTop();
                        final int left = child.getLeft();
                        int deltaX;
                        int deltaY;

                        if (startLeft != null) {
                            if (startLeft != left) {
                                deltaX = startLeft - left;
                                animators.add(ObjectAnimator.ofFloat(child, "translationX", deltaX, 0.0f));
                            }
                        }
                        if (startTop != null) {
                            if (startTop != top) {
                                deltaY = startTop - top;
                                animators.add(ObjectAnimator.ofFloat(child, "translationY", deltaY, 0.0f));
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "Found itemId: " + itemId + " for list view child " + i +
                                    " Top: " + top +
                                    " Delta: " + deltaY);
                        }
                    }
                }

                if (animators.size() > 0) {
                    animSet.setDuration(mAnimationDuration).playTogether(animators);
                    animSet.start();
                }

                mItemIdTopMap.clear();
                mItemIdLeftMap.clear();
                return true;
            }
        });
    }

    private boolean containsId(long[] ids, long target) {
        // Linear search on array is fine because this is typically only 0-1 elements long
        for (long id : ids) {
            if (id == target) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDataSetChangedForAnimation(long... idsInPlace) {
        animateGridView(idsInPlace);
    }

    @Override
    public void cacheOffsetsForDatasetChange() {
        saveOffsets(0);
    }

    public AbsListView getListView() {
        return mListView;
    }
}
