/*
Copyright (C) 2014-2017, Silent Circle, LLC.  All rights reserved.

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

package com.silentcircle.messaging.repository.DbRepository;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.ObjectRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.util.IOUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import zina.ZinaNative;

/**
 * Implementation of a database event/message repository.
 *
 * Created by werner on 24.05.15.
 */
public class DbEventRepository implements EventRepository {

    private static final String TAG = "DbEventRepository";

    private static final List<Event> EMPTY_LIST = new ArrayList<>();
    private final byte[] repoId;

    private final JSONEventAdapter adapter = new JSONEventAdapter();

    /**
     * Construct event history.
     *
     * The id parameter is the unique conversation id that the DB implementation
     * uses to look for events of this conversation.
     *
     * @param id the unique conversation id
     */
    public DbEventRepository(byte[] id) {
        repoId = id;
    }

    @Override
    @Nullable
    public ObjectRepository objectsOf(Event event) {
        if (event == null) {
            return null;
        }

        return objectsOf(identify(event));
    }

    @Override
    @Nullable
    public ObjectRepository objectsOf(String eventId) {
        if (TextUtils.isEmpty(eventId)) {
            return null;
        }

        byte[] id = IOUtils.encode(eventId);
        return new DbObjectRepository(repoId, id);
    }

    @Override
    public void clear() {
        List<Event> events = list();
        for (Event event: events) {
            remove(event);
        }

        /*
         * instead of removing events one by one try to delete all events associated with
         * conversation and all entries from objects and attachmentStatus repos.

        Log.d(TAG, "Clear all events for a conversation");
        ZinaNative.deleteAllEvents(repoId);
         */

        DbObjectRepository.clear(SilentPhoneApplication.getAppContext(), repoId);
    }

    /**
     * Check if a event history exists.
     *
     * It exists if the the unique conversation exists, the DB can then add
     * events to the events table.
     *
     * @return {@code true} if a event history exists, {@code false} if not.
     */
    @Override
    public boolean exists() {
        return ZinaNative.existConversation(repoId);
    }

    @Override
    public boolean exists(String id) {
        byte[] idBytes = IOUtils.encode(id);
        return ZinaNative.existEvent(repoId, idBytes);
    }

    @Override
    @Nullable
    public Event findById(@Nullable String eventId) {
        byte[] id = IOUtils.encode(eventId);
        int[] code = new int[2];
        byte[] data = ZinaNative.loadEvent(repoId, id, code);
        if (data != null) {
            return deserialize(new String(data));
        }
        return null;
    }

    @Override
    public List<Event> list() {
        return list(new PagingContext(-1, -1));
    }

    @Override
    public List<Event> list(PagingContext pagingContext) {
        int[] code = new int[2];
        code[1] = pagingContext.getLastMessageNumber();

        byte[][] events = ZinaNative.loadEvents(repoId, pagingContext.getNextOffset(),
                pagingContext.getPageSize(), pagingContext.getPagingDirection(), code);

        if (events == null || events.length == 0
                || pagingContext.isEndReached(code[1])) {
            return EMPTY_LIST;
        }

        pagingContext.setLastMessageNumber(code[1]);

        List<Event> eventsList = new ArrayList<>();
        for (byte[] eventData : events) {
            eventsList.add(deserialize(new String(eventData)));
        }
        return eventsList;
    }

    @Override
    public List<Event> list(int offset, int number, int direction) {
        int[] code = new int[2];
        code[1] = offset;

        byte[][] events = ZinaNative.loadEvents(repoId, offset,
                number, direction, code);

        if (events == null || events.length == 0) {
            return EMPTY_LIST;
        }

        List<Event> eventsList = new ArrayList<>();
        for (byte[] eventData : events) {
            eventsList.add(deserialize(new String(eventData)));
        }
        return eventsList;
    }

    @Override
    public void remove(@Nullable Event event) {
        if (event == null) {
            return;
        }

        remove(identify(event));
    }

    @Override
    public void remove(@Nullable String eventId) {
        if (TextUtils.isEmpty(eventId)) {
            return;
        }

        ObjectRepository objRepo = objectsOf(eventId);
        if (objRepo != null) {
            objRepo.clear();
        }

        byte[] id = IOUtils.encode(eventId);
        ZinaNative.deleteEvent(repoId, id);
    }

    protected String identify( Event message ) {
        return message.getId();
    }

    @Override
    public void save(Event object) {
        if (object == null) {
            return;
        }

        String serialized = serialize(object);
        if (serialized != null) {
            byte[] data = serialized.getBytes();
            byte[] id = IOUtils.encode(identify(object));
            ZinaNative.insertEvent(repoId, id, data);
            Arrays.fill(data, (byte) 0);
        }
    }

    @Nullable
    protected String serialize(Event event) {
        if (event == null) {
            return null;
        }
        return adapter.adapt(event).toString();
    }

    @Nullable
    private Event deserialize(String serial) {
        if (serial == null) {
            return null;
        }
        try {
            return adapter.adapt(new JSONObject(serial));
        } catch (JSONException exception) {
            return null;
        }
    }
}
