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

import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.common.list.ContactEntry;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ContactsCache;
import com.silentcircle.messaging.views.AvatarListView;
import com.silentcircle.silentphone2.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 */
public class AvatarModelViewAdapter extends GroupingModelViewAdapter implements AvatarListView.PinnedAvatarAdapter {

    private SectionIndexer mIndexer;
    private boolean mAvatarVisibility[];

    private static ContactPhotoManagerNew sContactPhotoManager;

    static {
        sContactPhotoManager = ContactPhotoManagerNew.getInstance(SilentPhoneApplication.getAppContext());
    }

    public static class Section {

        public String userId;
        public int position;
        public int dataPosition;
        public int length;

        public Section(String userId, int position, int dataPosition, int length) {
            this.userId = userId;
            this.position = position;
            this.dataPosition = dataPosition;
            this.length = length;
        }

        /* left for de-bugging */
        public String toString() {
            return "[" + userId + ", " + position + ", " + dataPosition + ", " + length + "]";
        }
    }

    private HashMap<String, QuickContactBadge> mUserIdToAvatarMap = new HashMap<>();

    public static class AvatarIndexer implements SectionIndexer {

        private final Section[] mSections;
        private final AvatarModelViewAdapter mAdapter;

        public AvatarIndexer(AvatarModelViewAdapter adapter, List<?> models) {
            mAdapter = adapter;
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

            int position = 0;
            Section section = null;
            String previousSender = null;
            int length = 0;

            for (Object model : models) {

                if (model instanceof IncomingMessage) {
                    String sender = ((IncomingMessage) model).getSender();
                    if (sender != null && !sender.equals(previousSender)) {
                        previousSender = sender;
                        section = new Section(sender, mAdapter.getScreenPosition(position), position, 0);
                        sections.add(section);
                    }
                    else {
                        length += 1;
                    }
                }
                else {
                    setSectionLength(section, length);
                    section = null;
                    previousSender = null;
                    length = 0;
                }

                position += 1;
            }

            // set length for last section
            setSectionLength(section, length);

            return sections.toArray(new Section[sections.size()]);
        }

        private void setSectionLength(@Nullable Section section, int length) {
            if (section != null) {
                // calculate section length in screen 'coordinates'
                section.length = mAdapter.getScreenPosition(section.dataPosition + length)
                        - section.position;
            }
        }

    }


    public AvatarModelViewAdapter(List<?> models, ViewType[] viewTypes) {
        super(models, viewTypes);
        mIndexer = new AvatarIndexer(this, models);
    }

    @Override
    public int getPinnedAvatarCount() {
        if (mIndexer == null) {
            return 0;
        }

        return mIndexer.getSections().length;
    }

    @Override
    public void setModels(List<?> models) {
        super.setModels(models);
        mIndexer = new AvatarIndexer(this, models);
    }

    @Override
    public View getPinnedAvatarView(int viewIndex, View convertView, ViewGroup parent) {
        if (mIndexer == null) {
            return null;
        }

        Section section = (Section) mIndexer.getSections()[viewIndex];
        QuickContactBadge view = mUserIdToAvatarMap.get(section.userId);
        if (view == null) {
            view = new QuickContactBadge(parent.getContext());
            int size = (int) parent.getContext().getResources().getDimension(R.dimen.contact_photo_size);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            view.setLayoutParams(params);

            ContactEntry contactEntry = ContactsCache.getContactEntryFromCache(section.userId);
            AvatarUtils.setPhoto(sContactPhotoManager, view, contactEntry);
            /* avoid showing triangle for badge */
            view.setEnabled(false);

            mUserIdToAvatarMap.put(section.userId, view);
        }

        return view;
    }

    protected boolean isSectionAvatarVisible(int sectionIndex, int firstVisiblePosition,
            int lastVisiblePosition) {
        if (mIndexer == null) {
            return false;
        }

        Section section = (Section) mIndexer.getSections()[sectionIndex];
        boolean visible = !((section.position + section.length) < firstVisiblePosition
                || section.position > lastVisiblePosition);

        return visible;
    }

    @Override
    public void configurePinnedAvatars(AvatarListView listView) {
        if (mIndexer == null) {
            return;
        }

        int size = mIndexer.getSections().length;

        // Cache visibility bits
        if (mAvatarVisibility == null || mAvatarVisibility.length != size) {
            mAvatarVisibility = new boolean[size];
        }
        for (int i = 0; i < size; i++) {
            boolean visible = isSectionAvatarVisible(i, listView.getFirstVisiblePosition(),
                    listView.getLastVisiblePosition());
            mAvatarVisibility[i] = visible;
            if (!visible) {
                listView.setAvatarInvisible(i, true);
            }
        }

        // Starting at the top, find and pin headers for partitions preceding the visible one(s)
        int maxTopHeader = -1;
        for (int i = 0; i < size; i++) {
            if (mAvatarVisibility[i]) {

                Section section = (Section) mIndexer.getSections()[i];
                if (section == null) {
                    break;
                }

                listView.setAvatarPinnedAtItem(i, section.position, section.length);

                maxTopHeader = i;
            }
        }

        // Headers in between the top-pinned and bottom-pinned should be hidden
        for (int i = maxTopHeader + 1; i < size; i++) {
            listView.setAvatarInvisible(i, true);
        }
    }

}
