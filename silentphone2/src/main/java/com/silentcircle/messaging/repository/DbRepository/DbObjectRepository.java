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
import android.text.TextUtils;
import android.util.Log;

import com.silentcircle.messaging.model.SCloudObject;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.json.JSONSCloudObjectAdapter;
import com.silentcircle.messaging.repository.ObjectRepository;
import com.silentcircle.messaging.util.CryptoUtil;
import com.silentcircle.messaging.util.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import axolotl.AxolotlNative;

/**
 * Object repository based on a DB.
 * <p/>
 * Created by werner on 24.05.15.
 */
public class DbObjectRepository implements ObjectRepository {

    private static final String TAG = "DbObjectRepository";

    private static final List<SCloudObject> EMPTY_LIST = new ArrayList<>();

    private final byte[] repoId;
    private final byte[] eventId;

    private JSONSCloudObjectAdapter adapter = new JSONSCloudObjectAdapter();
    private static final ByteArrayOutputStream SHARED_BUFFER = new ByteArrayOutputStream();
    private File dataRoot;
    private final Context context;

    /**
     * Construct event history.
     * <p/>
     * The repoId parameter is the unique conversation id that the DB implementation
     * uses to look for events of this conversation.
     *
     * @param repoId  the unique conversation id
     * @param eventId The unique event inside the conversation
     */

    DbObjectRepository(Context ctx, byte[] repoId, byte[] eventId) {
        context = ctx;
        this.repoId = repoId;
        this.eventId = eventId;
        File repo = new File(context.getFilesDir(), "objects");
        repo = new File(repo, new String(repoId).trim());

        // Assuming: repoId is "bob", eventId is "evId_1" then dataRoot is: <app main dir>/objects/bob/evId_1
        // Event ids are unique inside a conversation repo
        dataRoot = new File(repo, new String(eventId).trim());
    }

    protected String identify(SCloudObject object) {
        return adapter.identify(object);
    }

    public File getDataFile(SCloudObject object) {
        if(object == null) {
            return null;
        }

        return getDataFile(object.getLocator().toString());
    }

    public File getDataFile(String locator) {
        if(TextUtils.isEmpty(locator)) {
            return null;
        }

        dataRoot.mkdirs();

        // Locator shall be unique inside an event/message
        return new File(dataRoot, String.valueOf(locator));  // do hashing later if necessary
        // return new File( dataRoot, Hash.sha1( String.valueOf( object.getLocator() ) ) );
    }

    @Override
    public byte[] read(SCloudObject object) throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(getDataFile(object));
            SHARED_BUFFER.reset();
            long length = IOUtils.pipe(input, SHARED_BUFFER);
            object.setData(SHARED_BUFFER.toByteArray(), 0, length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) length);
        } finally {
            IOUtils.close(input);
        }
        return object.getData();
    }

    @Override
    public void write(SCloudObject object) throws IOException {
        if (object.getData() == null) {
            return;
        }
        OutputStream output = null;
        try {
            output = new FileOutputStream(getDataFile(object), false);
            output.write(object.getData(), object.getOffset(), object.getSize());
            output.flush();
        } finally {
            IOUtils.close(output);
        }
    }

    @Override
    public void clear() {
        List<SCloudObject> objectsList = list();
        for (SCloudObject object: objectsList)
            remove(object);
    }

    /**
     * Check if a object history exists.
     *
     * It exists if the the unique conversation and unique event exists, the DB can then add
     * object to the objects table.
     *
     * @return {@code true} if a object history exists, {@code false} if not.
     */
    @Override
    public boolean exists() {
        return AxolotlNative.existEvent(repoId, eventId);
    }

    @Override
    public boolean exists(String id) {
        byte[] idBytes = IOUtils.encode(id);
        return AxolotlNative.existObject(repoId, eventId, idBytes);
    }

    @Override
    public SCloudObject findById(String objectId) {
        byte[] id = IOUtils.encode(objectId);
        int[] code = new int[1];
        byte[] data = AxolotlNative.loadObject(repoId, eventId, id, code);
        if (data != null) {
            return deserialize(new String(data));
        }
        return null;
    }

    @Override
    public List<SCloudObject> list() {
        int[] code = new int[1];
        byte[][] objects = AxolotlNative.loadObjects(repoId, eventId, code);
        if (objects == null || objects.length == 0)
            return EMPTY_LIST;

        List<SCloudObject> objectsList = new ArrayList<>();
        for (byte[] data : objects) {
            objectsList.add(deserialize(new String(data)));
        }
        return objectsList;
    }

    @Override
    public void remove(SCloudObject object) {
        File dataFile = getDataFile(object);
        dataFile.delete();
        byte[] id = IOUtils.encode(identify(object));
        AxolotlNative.deleteObject(repoId, eventId, id);
    }

    @Override
    public void save(SCloudObject object) {
        byte[] data = serialize(object).getBytes();

        byte[] id = IOUtils.encode(identify(object));
        AxolotlNative.insertObject(repoId, eventId, id, data);
        Arrays.fill(data, (byte) 0);

    }

    private String serialize( SCloudObject object ) {
        return adapter.serialize( object );
    }

    protected SCloudObject deserialize( String serial ) {
        return adapter.deserialize(serial);
    }
}
