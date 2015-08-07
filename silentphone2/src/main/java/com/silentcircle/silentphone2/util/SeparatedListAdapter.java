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

package com.silentcircle.silentphone2.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

import com.silentcircle.silentphone2.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Support one list view for different types and headers
 * Created by werner on 23.03.14.
 */
public class SeparatedListAdapter extends BaseAdapter {

    public final Map<String, ArrayAdapter<CallState>> sections = new LinkedHashMap<>();
    public final ArrayAdapter<String> headers;
    public final static int TYPE_SECTION_HEADER = 0;
    public final static int TYPE_CALL_STATE = 1;

    public SeparatedListAdapter(Context context) {
        headers = new ArrayAdapter<>(context, R.layout.call_manager_list_title, R.id.list_header);
    }

    public void addSection(String section, ArrayAdapter<CallState> adapter) {
        headers.add(section);
        sections.put(section, adapter);
    }

    public void insertSection(String section, ArrayAdapter<CallState> adapter, int index) {
        headers.insert(section, index);
        sections.put(section, adapter);
    }

    public void remove(String section) {
        headers.remove(section);
        sections.remove(section);
    }

    public int getSectionPosition(String section) {
        return headers.getPosition(section);
    }

    public Object getItem(int position) {
        int sectionCount = headers.getCount();
        for (int i = 0; i < sectionCount; i++) {
            String section = headers.getItem(i);
            ArrayAdapter adapter = sections.get(section);
            int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0)
                return section;
            if (position < size)
                return adapter.getItem(position - 1);

            // otherwise jump into next section
            position -= size;
        }
        return null;
    }

    public int getPosition(CallState item) {
        int position = 0;
        int sectionCount = headers.getCount();
        for (int i = 0; i < sectionCount; i++) {
            String section = headers.getItem(i);
            ArrayAdapter<CallState> adapter = sections.get(section);
            int size = adapter.getCount() + 1;
            int tmpPos = adapter.getPosition(item);
            if (tmpPos < 0) {
                position += size;
                continue;
            }
            position += tmpPos + 1;
            return position;
        }
        return -1;
    }

    public int getCount() {
        // total together all sections, plus one for each section header
        int total = 0;
        for (Adapter adapter : sections.values())
            total += adapter.getCount() + 1;
        return total;
    }

    public int getViewTypeCount() {
        // This adapter supports two view types: header and call state objects
        return 2;
    }

    public int getItemViewType(int position) {
        int sectionCount = headers.getCount();
        for (int i = 0; i < sectionCount; i++) {
            String section = headers.getItem(i);
            Adapter adapter = sections.get(section);
            int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0)
                return TYPE_SECTION_HEADER;
            if (position < size)
                // Or get item from sub-adapter and check what it is and return type. In this
                // case adjust number of supported types above
                return TYPE_CALL_STATE;

            // otherwise jump into next section
            position -= size;
        }
        return -1;
    }

    public boolean isEnabled(int position) {
        return (getItemViewType(position) != TYPE_SECTION_HEADER);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int sectionNumber = 0;
        int sectionCount = headers.getCount();
        for (int i = 0; i < sectionCount; i++) {
            String section = headers.getItem(i);
            Adapter adapter = sections.get(section);
            int size = adapter.getCount() + 1;

            // check if position inside this section
            if (position == 0)
                return headers.getView(sectionNumber, convertView, parent);
            if (position < size)
                return adapter.getView(position - 1, convertView, parent);

            // otherwise jump into next section
            position -= size;
            sectionNumber++;
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}
