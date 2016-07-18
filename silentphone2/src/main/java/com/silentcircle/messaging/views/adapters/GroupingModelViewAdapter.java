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

package com.silentcircle.messaging.views.adapters;

import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;

import com.silentcircle.common.list.PinnedHeaderListView;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.views.ListView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Adapter to group events on conversation view by date. To be used with {@link PinnedHeaderListView}.
 */
public class GroupingModelViewAdapter extends MultiSelectModelViewAdapter implements SectionIndexer,
        PinnedHeaderListView.PinnedHeaderAdapter {

    private static final Integer VALUE_NO_DRAW = 1;
    private static final int INVALID_POSITION = -1;

    private SectionIndexer mIndexer;
    private boolean mHeaderVisibility[];

    public static class Section {

        public Date date;
        public int position;
        public int dataPosition;
        public int length;

        public Section(Date date, int position, int dataPosition, int length) {
            this.date = date;
            this.position = position;
            this.dataPosition = dataPosition;
            this.length = length;
        }

        /* left for de-bugging */
        /*
        public String toString() {
            return "[" + position + ", " + dataPosition + ", " + length + ", "
                    + DateUtils.getRelativeTimeSpanString(date.getTime(),
                            System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR) + "]";
        }
         */
    }

    public static class EventDateIndexer implements SectionIndexer {

        private final Section[] mSections;

        public EventDateIndexer(List<?> models) {
            mSections = getSections(models);
        }

        @Override
        public Object[] getSections() {
            return mSections;
        }

        @Override
        public int getPositionForSection(int sectionIndex) {
            int result = 0;
            if (sectionIndex < mSections.length) {
                result = mSections[sectionIndex].position;
            }
            return result;
        }

        @Override
        public int getSectionForPosition(int position) {
            int result = INVALID_POSITION;
            int sectionPosition = 0;
            boolean sectionFound = false;
            for (Section section : mSections) {
                if (section.position <= position && position <= (section.position + section.length)) {
                    sectionFound = true;
                    break;
                }
                sectionPosition++;
            }
            if (sectionFound) {
                result = sectionPosition;
            }

            return result;
        }

        private Section[] getSections(List<?> models) {
            ArrayList<Section> sections = new ArrayList<>();

            Calendar previousDay = getDatePart(new Date());
            long previousTime = -1;
            int position = 0;
            int previousHeaderPosition = 0;
            int previousDataPosition = 0;
            int length = 0;

            Date date = new Date();

            for (Object model : models) {
                if (model instanceof Event) {

                    long time = ((Event) model).getComposeTime();
                    if (previousTime == -1) {
                        previousTime = time;
                    }

                    date.setTime(time);
                    Calendar day = getDatePart(date);
                    if (day.compareTo(previousDay) != 0) {
                        // create a new section;
                        if (position > 0) {
                            sections.add(
                                    new Section(new Date(previousTime), previousHeaderPosition,
                                            previousDataPosition, length));

                            previousHeaderPosition += length + 1;
                            previousDataPosition = position;
                            previousDay = day;
                            previousTime = time;
                            length = 0;

                        } else {
                            previousTime = time;
                            previousDay = day;
                        }
                    }
                }

                position += 1;
                length += 1;
            }
            if (length > 0) {
                sections.add(new Section(new Date(previousTime), previousHeaderPosition,
                        previousDataPosition, length));
            }

            return sections.toArray(new Section[sections.size()]);
        }

        private static Calendar getDatePart(Date date) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            return calendar;
        }
    }

    public GroupingModelViewAdapter(List<?> models, ViewType[] viewTypes) {
        super(models, viewTypes);
        mIndexer = new EventDateIndexer(models);
    }

    @Override
    public int getCount() {
        return getModelProvider().getCount() + (mIndexer != null ? mIndexer.getSections().length : 0);
    }

    @Override
    public Object getItem(int position) {
        if (mIndexer == null) {
            return ((position < getModelProvider().getCount())
                    ? getModelProvider().getItem(position) : null);
        }

        if (isHeaderPosition(position)) {
            return ((Section) mIndexer.getSections()[mIndexer.getSectionForPosition(position)]).date;
        } else {
            int dataItemPosition = getItemPosition(position);
            /* call super.getItem(dataItemPosition) with length check */
            return ((dataItemPosition >= 0 && dataItemPosition < getModelProvider().getCount())
                    ? getModelProvider().getItem(dataItemPosition) : null);
        }
    }

    @Override
    public long getItemId(int position) {
        if (mIndexer == null) {
            return ((position >= 0 && position < getModelProvider().getCount())
                    ? getModelProvider().getItemId(position) : 0);
        }

        if (isHeaderPosition(position)) {
            Object item = getItem(position);
            return item != null ? item.hashCode() : 0;
        } else {
            int dataItemPosition = getItemPosition(position);
            /* call super.getItemId(dataItemPosition) with length check */
            return ((dataItemPosition >= 0 && dataItemPosition < getModelProvider().getCount())
                    ? getModelProvider().getItemId(dataItemPosition) : 0);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (isHeaderPosition(position)) {
            Section section =
                    (Section) mIndexer.getSections()[mIndexer.getSectionForPosition(position)];
            return getHeaderView(convertView, parent, section.date);
        } else {
            return super.getView(position, convertView, parent);
        }
    }

    @Override
    public int getViewTypeCount() {
        return getViewTypes().length;
    }

    @Override
    public void setModels(List<?> models) {
        super.setModels(models);
        mIndexer = new EventDateIndexer(models);
    }

    @Override
    public int getPinnedHeaderCount() {
        if (mIndexer == null) {
            return 0;
        }

        return mIndexer.getSections().length;
    }

    @Override
    public View getPinnedHeaderView(int position, View convertView, ViewGroup parent) {
        if (mIndexer == null) {
            return null;
        }

        Section section = (Section) mIndexer.getSections()[position];
        convertView = getHeaderView(convertView, parent, section.date);

        return convertView;
    }

    @Override
    public void configurePinnedHeaders(PinnedHeaderListView listView) {
        if (mIndexer == null) {
            return;
        }

        int size = mIndexer.getSections().length;

        // Cache visibility bits
        if (mHeaderVisibility == null || mHeaderVisibility.length != size) {
            mHeaderVisibility = new boolean[size];
        }
        for (int i = 0; i < size; i++) {
            boolean visible = isSectionHeaderVisible(i, listView.getFirstVisiblePosition(),
                    listView.getLastVisiblePosition());
            mHeaderVisibility[i] = visible;
            if (!visible) {
                listView.setHeaderInvisible(i, true);
            }
        }

        int headerViewsCount = listView.getHeaderViewsCount();

        // Starting at the top, find and pin headers for partitions preceding the visible one(s)
        int maxTopHeader = -1;
        int topHeaderHeight = 0;
        for (int i = 0; i < size; i++) {
            if (mHeaderVisibility[i]) {
                int position = listView.getPositionAt(topHeaderHeight) - headerViewsCount;
                int sectionPosition = getSectionForPosition(position);

                if (i > sectionPosition) {
                    break;
                }

                Object[] sections = getSections();
                if (sectionPosition < 0 || sectionPosition >= sections.length) {
                    break;
                }

                Section section = (Section) sections[sectionPosition];
                if (section == null) {
                    break;
                }

                boolean isLastDataPosition = isLastDataPosition(sectionPosition, position);

                if (isLastDataPosition) {
                    listView.setFadingHeader(i, position, true);
                }
                else {
                    Integer drawable = null;
                    if (section.position <= listView.getFirstVisiblePosition()) {
                        listView.setHeaderPinnedAtTop(i, topHeaderHeight, false);
                        topHeaderHeight += listView.getPinnedHeaderHeight(i);

                        // header view should not be drawn in first pass, only its pinned version
                        // should be drawn
                        drawable = VALUE_NO_DRAW;
                    }

                    // mark header view as drawable or not drawable depending on whether it is pinned
                    if (listView.getFirstVisiblePosition() == section.position) {
                        View view = listView.getChildAt(0);
                        if (view != null) {
                            view.setTag(ListView.TAG_NO_DRAW, drawable);
                        }
                    }
                }

                maxTopHeader = i;
            }
        }

        // Headers in between the top-pinned and bottom-pinned should be hidden
        for (int i = maxTopHeader + 1; i < size; i++) {
            listView.setHeaderInvisible(i, true);
        }
    }

    private boolean isLastDataPosition(int sectionPosition, int position) {
        if (mIndexer == null) {
            return false;
        }

        Section section = (Section) mIndexer.getSections()[sectionPosition];
        return position == (section.position + section.length);
    }

    @Override
    public int getScrollPositionForHeader(int viewIndex) {
        if (mIndexer == null) {
            return 0;
        }

        Section section = null;
        if (viewIndex < mIndexer.getSections().length) {
            section = (Section) mIndexer.getSections()[viewIndex];
        }
        return section != null ? section.position : 0;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (mIndexer == null) {
            return INVALID_POSITION;
        }

        return mIndexer.getPositionForSection(sectionIndex);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (mIndexer == null) {
            return INVALID_POSITION;
        }

        return mIndexer.getSectionForPosition(position);
    }

    @Override
    public Object[] getSections() {
        if (mIndexer == null) {
            return new Section[0];
        } else {
            return mIndexer.getSections();
        }
    }

    @Override
    public boolean isDataPosition(int position) {
        if (mIndexer == null) {
            return super.isDataPosition(position);
        }

        return !isHeaderPosition(position);
    }

    @Override
    public int getDataPosition(int position) {
        if (mIndexer == null) {
            return super.getDataPosition(position);
        }

        return getItemPosition(position);
    }

    @Override
    public int getScreenPosition(int position) {
        if (mIndexer == null) {
            return super.getScreenPosition(position);
        }

        return getSectionPosition(position);
    }


    protected boolean isSectionHeaderVisible(int sectionIndex, int firstVisiblePosition,
            int lastVisiblePosition) {
        if (mIndexer == null) {
            return false;
        }

        Section section = (Section) mIndexer.getSections()[sectionIndex];
        boolean visible = !((section.position + section.length) < firstVisiblePosition
                || section.position > lastVisiblePosition);

        return visible;
    }

    private boolean isHeaderPosition(int position) {
        boolean result = false;
        if (mIndexer != null) {
            for (Object item : mIndexer.getSections()) {
                if (((Section) item).position == position) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    private int getItemPosition(int position) {
        int headerPosition = INVALID_POSITION;
        int dataItemPosition = INVALID_POSITION;
        for (Object item : mIndexer.getSections()) {
            Section section = (Section) item;
            if (section.position == position) {
                headerPosition = position;
                break;
            }
            if (section.position < position && position <= (section.position + section.length)) {
                dataItemPosition = section.dataPosition + (position - section.position - 1);
                break;
            }
        }
        return dataItemPosition;
    }

    /*
     * Translate data item's position to a position taking into account header counts
     *
     */
    private int getSectionPosition(int position) {
        int sectionPosition = INVALID_POSITION;
        for (Object item : mIndexer.getSections()) {
            Section section = (Section) item;
            if (section.dataPosition <= position && position < (section.dataPosition + section.length)) {
                sectionPosition = section.position + (position - section.dataPosition + 1);
                break;
            }
        }
        return sectionPosition;
    }

    private View getHeaderView(View convertView, ViewGroup parent, Date date) {
        if (convertView == null || !(convertView.getTag() instanceof Date)) {
            ViewType viewType = getItemViewType(date);
            convertView = viewType.get(convertView, parent);
        }

        convertView.setTag(date);

        return convertView;
    }

}
