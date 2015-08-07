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
 * Copyright (C) 2011 The Android Open Source Project
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

package com.silentcircle.contacts;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

/**
 * Handles scrolling back of a list tied to a header.
 * <p>
 * This is used to implement a header that scrolls up with the content of a list to be partially
 * obscured.
 */
public class BackScrollManager {
    /** Defines the header to be scrolled. */
    public interface ScrollableHeader {
        /** Sets the offset by which to scroll. */
        public void setOffset(int offset);
        /** Gets the maximum offset that should be applied to the header. */
        public int getMaximumScrollableHeaderOffset();
    }

    private final ScrollableHeader mHeader;
    private final ListView mListView;

    private final AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (firstVisibleItem != 0) {
                // The first item is not shown, the header should be pinned at the top.
                mHeader.setOffset(mHeader.getMaximumScrollableHeaderOffset());
                return;
            }

            View firstVisibleItemView = view.getChildAt(firstVisibleItem);
            if (firstVisibleItemView == null) {
                return;
            }
            // We scroll the header up, but at most pin it to the top of the screen.
            int offset;

            offset = Math.min((int) -view.getChildAt(firstVisibleItem).getY(), mHeader.getMaximumScrollableHeaderOffset()) ;
            mHeader.setOffset(offset);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            // Nothing to do here.
        }
    };

    /**
     * Creates a new instance of a {@link BackScrollManager} that connected the header and the list
     * view.
     */
    public static void bind(ScrollableHeader header, ListView listView) {
        BackScrollManager backScrollManager = new BackScrollManager(header, listView);
        backScrollManager.bind();
    }

    private BackScrollManager(ScrollableHeader header, ListView listView) {
        mHeader = header;
        mListView = listView;
    }

    private void bind() {
        mListView.setOnScrollListener(mScrollListener);
        // We disable the scroll bar because it would otherwise be incorrect because of the hidden
        // header.
        mListView.setVerticalScrollBarEnabled(false);
    }
}
