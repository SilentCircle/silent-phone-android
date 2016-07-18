/*
Copyright (C) 2016, Silent Circle, LLC.  All rights reserved.

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
package com.silentcircle.messaging.repository;


import com.silentcircle.messaging.model.event.Event;

import java.util.List;

public interface EventRepository extends Repository<Event> {

    ObjectRepository objectsOf(Event event);

    List<Event> list(PagingContext pagingContext);

    class PagingContext {

        public static final int START_FROM_YOUNGEST = -1;
        public static final int START_FROM_OLDEST = 1;

        private int mLastPosition;
        private final int mStartPosition;
        private final int mPageSize;

        public PagingContext(int startPosition, int pageSize) {
            mStartPosition = startPosition;
            mLastPosition = startPosition;
            mPageSize = pageSize;
        }

        public int getNextOffset() {
            return mStartPosition == START_FROM_YOUNGEST
                    ? (mLastPosition != mStartPosition ? (mLastPosition - 1) : START_FROM_YOUNGEST)
                    : ((mLastPosition != mStartPosition) ? (mLastPosition + 1): START_FROM_OLDEST);
        }

        public void setLastMessageNumber(int lastPosition) {
            mLastPosition = lastPosition;
        }

        public int getLastMessageNumber() {
            return mLastPosition;
        }

        public int getPagingDirection() {
            return mStartPosition;
        }

        public boolean isEndReached(int lastPosition) {
            return (mLastPosition == lastPosition);
        }

        public int getStartPosition() {
            return mStartPosition;
        }

        public int getPageSize() {
            return mPageSize;
        }
    }
}
