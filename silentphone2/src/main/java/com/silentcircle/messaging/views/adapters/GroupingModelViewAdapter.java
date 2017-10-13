/*
Copyright (C) 2016-2017, Silent Circle, LLC.  All rights reserved.

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

import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;

import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.views.PinnedHeaderChatRecyclerView;

import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Adapter to group events on conversation view by date. To be used with {@link PinnedHeaderChatRecyclerView}.
 */
public class GroupingModelViewAdapter extends MultiSelectModelViewAdapter implements SectionIndexer,
        PinnedItemDecoration.PinnedHeaderAdapter {

    private SectionIndexer mIndexer;

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
        public String toString() {
            return "[" + position + ", " + dataPosition + ", " + length + ", "
                    + DateUtils.getRelativeTimeSpanString(date.getTime(),
                            System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR) + "]";
        }
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
            if (models == null) {
                return sections.toArray(new Section[sections.size()]);
            }

            LocalDate previousDay = LocalDate.now();
            long previousTime = -1;
            int position = 0;
            int previousHeaderPosition = 0;
            int previousDataPosition = 0;
            int length = 0;

            for (Object model : models) {
                if (model instanceof Event) {

                    long time = ((Event) model).getComposeTime();
                    if (previousTime == -1) {
                        previousTime = time;
                    }

                    LocalDate day = new LocalDate(time);
                    if (!day.equals(previousDay)) {
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

    @SuppressWarnings("FieldCanBeLocal")
    private RecyclerView.AdapterDataObserver mChangeObserver = new RecyclerView.AdapterDataObserver() {

        @Override
        public void onChanged() {
            refreshSections(getModelProvider().getItems());
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount) {
            refreshSections(getModelProvider().getItems());
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            onItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            refreshSections(getModelProvider().getItems());
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            refreshSections(getModelProvider().getItems());
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            refreshSections(getModelProvider().getItems());
        }

    };

    public GroupingModelViewAdapter(List<?> models, ViewType[] viewTypes) {
        super(models, viewTypes);
        refreshSections(models);
        registerAdapterDataObserver(mChangeObserver);
    }

    @Override
    public int getCount() {
        return getModelProvider().getCount() + (mIndexer != null ? mIndexer.getSections().length : 0);
    }

    @Override
    public int getItemCount() {
        return getCount();
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
    public void setModels(List<?> models) {
        super.setModels(models);
        refreshSections(models);
    }

    @Override
    public boolean isHeaderPosition(int position) {
        // TODO use a set of header positions?
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

    @Override
    public int getPinnedHeaderCount() {
        if (mIndexer == null) {
            return 0;
        }

        return mIndexer.getSections().length;
    }

    @Override
    public boolean isSectionHeaderVisible(int headerPosition, int firstVisiblePosition,
            int lastVisiblePosition) {
        if (mIndexer == null) {
            return false;
        }

        Section section = null;
        for (Object item : mIndexer.getSections()) {
            if (((Section) item).position == headerPosition) {
                section = (Section) item;
                break;
            }
        }

        boolean visible = false;
        if (section != null) {
            visible = !((section.position + section.length) < firstVisiblePosition
                    || section.position > lastVisiblePosition);
        }
        return visible;
    }

    @Override
    public View getHeaderView(View convertView, ViewGroup parent, Object data) {
        if (convertView == null) {
            ViewType viewType = getItemViewType(data);
            convertView = viewType.get(convertView, parent);
            convertView.requestLayout();
        }

        convertView.setTag(data);

        return convertView;
    }

    @Override
    public int getHeaderPositionForItem(int position) {
        int headerPosition = INVALID_POSITION;
        for (Object item : mIndexer.getSections()) {
            Section section = (Section) item;
            if (section.position == position) {
                headerPosition = section.position;
                break;
            }
            else if (section.position <= position && position <= (section.position + section.length)) {
                headerPosition = section.position;
                break;
            }
        }
        return headerPosition;
    }

    @Override
    public Object getHeaderData(int headerPosition) {
        Object result = null;
        if (mIndexer != null) {
            for (Object item : mIndexer.getSections()) {
                if (((Section) item).position == headerPosition) {
                    result = ((Section) item).date;
                    break;
                }
            }
        }
        return result;
    }

    public void refreshSections(List<?> models) {
        mIndexer = new EventDateIndexer(models);
    }

    private boolean isLastDataPosition(int sectionPosition, int position) {
        if (mIndexer == null) {
            return false;
        }

        Section section = (Section) mIndexer.getSections()[sectionPosition];
        return position == (section.position + section.length);
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

}
