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
package com.silentcircle.silentphone2.list;

import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that handles and combines drag events generated from multiple views, and then fires
 * off events to any OnDragDropListeners that have registered for callbacks.
 */
public class DragDropController {

    private final List<OnDragDropListener> mOnDragDropListeners = new ArrayList<>();
    private final DragItemContainer mDragItemContainer;
    private final int[] mLocationOnScreen = new int[2];

    /**
     * Callback interface used to retrieve views based on the current touch coordinates of the
     * drag event. The {@link com.silentcircle.silentphone2.list.DragDropController.DragItemContainer} houses the draggable views that this
     * {@link com.silentcircle.silentphone2.list.DragDropController} controls.
     */
    public interface DragItemContainer {
        public PhoneFavoriteSquareTileView getViewForLocation(int x, int y);
    }

    public DragDropController(DragItemContainer dragItemContainer) {
        mDragItemContainer = dragItemContainer;
    }

    /**
     * @return True if the drag is started, false if the drag is cancelled for some reason.
     */
    boolean handleDragStarted(int x, int y) {
        final PhoneFavoriteSquareTileView tileView = mDragItemContainer.getViewForLocation(x, y);
        if (tileView == null) {
            return false;
        }
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragStarted(x, y, tileView);
        }

        return true;
    }

    public void handleDragHovered(View v, int x, int y) {
        v.getLocationOnScreen(mLocationOnScreen);
        final int screenX = x + mLocationOnScreen[0];
        final int screenY = y + mLocationOnScreen[1];
        final PhoneFavoriteSquareTileView view = mDragItemContainer.getViewForLocation(
                screenX, screenY);
        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragHovered(screenX, screenY, view);
        }
    }

    public void handleDragFinished(int x, int y, boolean isRemoveView) {
        if (isRemoveView) {
            for (int i = 0; i < mOnDragDropListeners.size(); i++) {
                mOnDragDropListeners.get(i).onDroppedOnRemove();
            }
        }

        for (int i = 0; i < mOnDragDropListeners.size(); i++) {
            mOnDragDropListeners.get(i).onDragFinished(x, y);
        }
    }

    public void addOnDragDropListener(OnDragDropListener listener) {
        if (!mOnDragDropListeners.contains(listener)) {
            mOnDragDropListeners.add(listener);
        }
    }
}
