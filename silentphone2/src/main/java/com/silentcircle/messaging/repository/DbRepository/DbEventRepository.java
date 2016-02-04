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

package com.silentcircle.messaging.repository.DbRepository;

import android.content.Context;

import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.ObjectRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.util.CryptoUtil;
import com.silentcircle.messaging.util.IOUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import axolotl.AxolotlNative;

/**
 * Implementation of a database event/message repository.
 *
 * Created by werner on 24.05.15.
 */
public class DbEventRepository implements EventRepository {

    private static final String TAG = "DbEventRepository";

    private static final List<Event> EMPTY_LIST = new ArrayList<>();
    private final byte[] repoId;
    private final Context context;

    private final JSONEventAdapter adapter = new JSONEventAdapter();

    /**
     * Construct event history.
     *
     * The id parameter is the unique conversation id that the DB implementation
     * uses to look for events of this conversation.
     *
     * @param id the unique conversation id
     * @param key key to encrypt/decrypt event data
     */
    public DbEventRepository(Context ctx, byte[] id) {
        context = ctx;
        repoId = id;
    }

    @Override
    public ObjectRepository objectsOf(Event event) {
        if( event == null ) {
            return null;
        }
        byte[] eventId = IOUtils.encode(identify(event));
        ObjectRepository objRepo = new DbObjectRepository(context, repoId, eventId);
        return objRepo;
    }

    @Override
    public void clear() {
        List<Event> events = list();
        for (Event event: events) {
            remove(event);
        }
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
        return AxolotlNative.existConversation(repoId);
    }

    @Override
    public boolean exists(String id) {
        byte[] idBytes = IOUtils.encode(id);
        return AxolotlNative.existEvent(repoId, idBytes);
    }

    @Override
    public Event findById(String eventId) {
        byte[] id = IOUtils.encode(eventId);
        int[] code = new int[2];
        byte[] data = AxolotlNative.loadEvent(repoId, id, code);
        if (data != null) {
            return deserialize(new String(data));
        }
        return null;
    }

    @Override
    public List<Event> list() {
        int[] code = new int[2];
        byte[][] events = AxolotlNative.loadEvents(repoId, -1, -1, code);
        if (events == null || events.length == 0)
            return EMPTY_LIST;

        List<Event> eventsList = new ArrayList<>();
        for (byte[] eventData : events) {
            eventsList.add(deserialize(new String(eventData)));
        }
        return eventsList;
    }

    @Override
    public void remove(Event event) {
        ObjectRepository objRepo = objectsOf(event);
        objRepo.clear();

        byte[] id = IOUtils.encode(identify(event));
        AxolotlNative.deleteEvent(repoId, id)     ;
    }

    protected String identify( Event message ) {
        return message.getId();
    }

    @Override
    public void save(Event object) {
        byte[] data = serialize(object).getBytes();

        byte[] id = IOUtils.encode(identify(object));
        AxolotlNative.insertEvent(repoId, id, data);
        Arrays.fill(data, (byte) 0);
    }

    protected String serialize(Event event) {
        if (event == null) {
            return null;
        }
        return adapter.adapt(event).toString();
    }

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
