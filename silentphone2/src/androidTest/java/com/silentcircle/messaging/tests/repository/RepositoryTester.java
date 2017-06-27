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

package com.silentcircle.messaging.tests.repository;

import android.test.AndroidTestCase;
import android.util.Log;

import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.SCloudObject;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.DbRepository.DbConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.repository.ObjectRepository;
import com.silentcircle.messaging.util.IOUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.List;

import zina.ZinaNative;

/**
 * Test Repository implementation
 *
 * Created by werner on 25.05.15.
 */
public class RepositoryTester extends AndroidTestCase {

    private static final String TAG = "RepositoryTester";
    static {
        System.loadLibrary("stlport_shared");
        System.loadLibrary("sqlcipher_android");
        System.loadLibrary("database_sqlcipher");
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("tina");
        System.loadLibrary("aec");
        System.loadLibrary("tivi");
    }

    // exactly 32 bytes for a 256bit key
    static final byte[] repoKey =    {1,2,3,4,5,6,7,8,9,0,0,9,8,7,6,5,4,3,2,1,1,3,5,7,9,2,4,6,9,8,7,6};

    private final String userName = "werner";
    private static boolean firstSetup = false;

    private static final String[] partners = {"Alice", "Bob", "Trent", "Mallory"};
    private static final String[] events = {"event_1", "event_2", "event_3", "event_4"};
    private static final String[] objects = {"object_1", "object_2", "object_3", "object_4"};
    private static final String FORMAT_EVENT_ID = "event_%d";

    private static final int PAGE_SIZE = 10;
    private static final int EVENT_COUNT_517 = 517;

    @Override
    protected void setUp() throws Exception {
        if (!firstSetup) {
            File dbFile = new File(getContext().getFilesDir(), "testdb.db");
            if (dbFile.exists())
                dbFile.delete();
            Log.d("RepoTester", "DB path: " + dbFile.getAbsolutePath());
            ZinaNative.repoOpenDatabase(dbFile.getAbsolutePath(), repoKey);
            firstSetup = true;
        }
    }

    public void testConversation() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        DbConversationRepository testConv = new DbConversationRepository(userName);
        assertTrue(testConv.exists());   // Does a repo for this local user exist?

        assertFalse(testConv.exists(partners[0]));  // not yet

        Conversation conversation = new Conversation(partners[0]);

        testConv.save(conversation);
        assertTrue(testConv.exists(partners[0]));  //

        Conversation convRead = testConv.findById(partners[0]);
        assertEquals(partners[0], convRead.getPartner().getUserId());
        assertFalse(conversation.hasBurnNotice());

        // *** Following tests cannot work here, they require full Axolotl setup
//        List<Conversation> allConversations = testConv.list();
//        assertTrue(allConversations == null);
//        testConv.clear();

        conversation.setBurnNotice(true);
        testConv.save(conversation);
        convRead = testConv.findById(partners[0]);
        assertEquals(partners[0], convRead.getPartner().getUserId());
        assertTrue(conversation.hasBurnNotice());

        testConv.remove(conversation);
        assertFalse(testConv.exists(partners[0]));  // not anymore
    }

    public void testEventHistory() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        DbConversationRepository convRepo = new DbConversationRepository(userName);
        Conversation conversation = new Conversation(partners[1]);
        convRepo.save(conversation);

        // Get a event/message history for the partner (in this case Bob)
        EventRepository evHistory = convRepo.historyOf(conversation);
        assertTrue(evHistory.exists());

        assertFalse(evHistory.exists(events[0]));       // No such event yet

        Message msg = new Message();
        msg.setId(events[0]);                           // a message (event) *must* have an id
        msg.setSender(partners[1]);                     // just for fun
        evHistory.save(msg);                            // Save the message

        Event msgRead = evHistory.findById(events[0]);  // ready back with the event id
        assertTrue(msgRead instanceof Message);         // and should decode as Message
        assertEquals(events[0], msgRead.getId());
        assertEquals(partners[1], ((Message) msgRead).getSender());

        msgRead = evHistory.findById(events[1]);
        assertNull(msgRead);

        int[] code = new int[1];
        byte[] msgByte = ZinaNative.loadEventWithMsgId(IOUtils.encode(events[0]), code);
        assertNotNull(msgByte);
        assertTrue((msgByte.length > 1));
        final JSONEventAdapter adapter = new JSONEventAdapter();
        msgRead = adapter.adapt(new JSONObject(new String(msgByte)));
        assertTrue((msgRead instanceof Message));
        assertEquals(partners[1], ((Message) msgRead).getSender());

        // Now add some move events
        msg = new Message();
        msg.setId(events[1]);
        msg.setSender(partners[1]);
        evHistory.save(msg);

        msg = new Message();
        msg.setId(events[2]);
        msg.setSender(partners[1]);
        evHistory.save(msg);

        msg = new Message();
        msg.setId(events[3]);
        msg.setSender(partners[1]);
        msg.setState(MessageStates.COMPOSED);
        evHistory.save(msg);

        msgRead = evHistory.findById(events[3]);
        assertEquals(MessageStates.COMPOSED, ((Message) msgRead).getState());

        msg.setState(MessageStates.SENT);
        evHistory.save(msg);              // save (update) message with different state
        msgRead = evHistory.findById(events[3]);
        assertEquals(MessageStates.SENT, ((Message) msgRead).getState());

        List<Event> eventList = evHistory.list();
        assertNotNull(eventList);
        assertEquals(4, eventList.size());
        for (Event ev: eventList) {
            assertTrue(ev instanceof Message);
            assertEquals(partners[1], ((Message) ev).getSender());
        }

        evHistory.remove(msg);      // remove the last message from repo
        assertFalse(evHistory.exists(msg.getId()));

        evHistory.clear();          // clear all
        eventList = evHistory.list();
        assertEquals(0, eventList.size());
    }

    byte[] objectData = {0x0d, 0x0e, 0x0a, 0x0d, 0x0c, 0x0a, 0x0f, 0x0e};
    public void testObjectHistory() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        // First setup a conversation and a message inside the conversation
        DbConversationRepository convRepo = new DbConversationRepository(userName);
        Conversation conversation = new Conversation(partners[2]);
        convRepo.save(conversation);

        // Get a event/message history for the partner (in this case Trent)
        EventRepository evHistory = convRepo.historyOf(conversation);
        assertTrue(evHistory.exists());
        Message msg = new Message();
        msg.setId(events[0]);                           // a message (event) _must_ have an id
        msg.setSender(partners[1]);                     // just for fun
        evHistory.save(msg);                            // Save the message

        ObjectRepository objRepo = evHistory.objectsOf(msg);
        assertTrue(objRepo.exists());

        SCloudObject scObject = new SCloudObject();
        scObject.setLocator(objects[0]);                // A Could object *must* have a locator
        scObject.setData(objectData);                   // necessary to test the "write" data to file (the object data)
        objRepo.save(scObject);                         // save object description
        objRepo.write(scObject);                        // save object data as file
        assertTrue(objRepo.exists(objects[0]));         // Use "locator" again to check if it exists

        SCloudObject scObjRead = objRepo.findById(objects[0]);
        assertEquals(objects[0], scObjRead.getLocator().toString());
        byte[] dataRead = objRepo.read(scObjRead);
        for (int i = 0; i < objectData.length; i++)
            assertEquals(objectData[i], dataRead[i]);

        scObject.setSize(1111);
        objRepo.save(scObject);                         // save (update) object description again with a different size
        scObjRead = objRepo.findById(objects[0]);
        assertEquals(1111, scObjRead.getSize());

        scObjRead = objRepo.findById(objects[1]);
        assertNull(scObjRead);

        scObject = new SCloudObject();
        scObject.setLocator(objects[1]);                // A Could object *must* have a locator
        scObject.setData(objectData);                   // necessary to test the "write" data to file (the object data)
        objRepo.save(scObject);                         // save object description
        objRepo.write(scObject);

        scObject = new SCloudObject();
        scObject.setLocator(objects[2]);                // A Could object *must* have a locator
        scObject.setData(objectData);                   // necessary to test the "write" data to file (the object data)
        objRepo.save(scObject);                         // save object description
        objRepo.write(scObject);

        scObject = new SCloudObject();
        scObject.setLocator(objects[3]);                // A Could object *must* have a locator
        scObject.setData(objectData);                   // necessary to test the "write" data to file (the object data)
        objRepo.save(scObject);                         // save object description
        objRepo.write(scObject);

        List<SCloudObject> objList = objRepo.list();
        assertNotNull(objList);
        assertEquals(4, objList.size());
        for (SCloudObject obj: objList) {
            dataRead = objRepo.read(obj);
            for (int i = 0; i < objectData.length; i++)
                assertEquals(objectData[i], dataRead[i]);
        }

        objRepo.remove(scObject);      // remove the last object from repo
        assertFalse(objRepo.exists(scObject.getLocator().toString()));

        objRepo.clear();
        objList = objRepo.list();
        assertNotNull(objList);
    }

    public static final int SQLITE_OK = 0;
    public static final int SQLITE_ROW = 100;

    public void testAttachmentStatus() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        String msgId_1 = "msgid_1";
        String msgId_2 = "msgid_2";
        String msgId_3 = "msgid_3";
        String msgId_4 = "msgid_4";

        int sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), null, 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        int[] code = new int[1];
        int status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), null, code);
        assertEquals(1, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Update the message status to 2
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), null, 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Check the status
        status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), null, code);
        assertEquals(2, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        sqlCode = ZinaNative.deleteAttachmentStatus(IOUtils.encode(msgId_1), null);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // No entry any more, returns -1
        status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), null, code);
        assertEquals(-1, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Store 4 msg ids with same status
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), null, 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_2), null, 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_3), null, 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_4), null, 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Load all msg ids with the same status
        String[] msgIds = ZinaNative.loadMsgsIdsWithAttachmentStatus(1, code);
        assertEquals(4, msgIds.length);
        assertFalse(msgIds[0].contains(":"));
//        assertTrue(msgIds[0].equals(msgId_1));

        // Update 2 msg ids with a new status
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_3), null, 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_4), null, 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Delete all msg is with status 2
        sqlCode = ZinaNative.deleteWithAttachmentStatus(2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // No entries with status 2
        msgIds = ZinaNative.loadMsgsIdsWithAttachmentStatus(1, code);
        assertEquals(2, msgIds.length);
    }

    public void testAttachmentStatusPartner() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        String partner = "partner";
        String msgId_1 = "msgid_1";
        String msgId_2 = "msgid_2";
        String msgId_3 = "msgid_3";
        String msgId_4 = "msgid_4";

        int sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        int[] code = new int[1];
        int status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), code);
        assertEquals(1, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Update the message status to 2
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Check the status
        status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), code);
        assertEquals(2, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        sqlCode = ZinaNative.deleteAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner));
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // No entry any more, returns -1
        status = ZinaNative.loadAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), code);
        assertEquals(-1, status);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Store 4 msg ids with same status
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_1), IOUtils.encode(partner), 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_2), IOUtils.encode(partner), 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_3), IOUtils.encode(partner), 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_4), IOUtils.encode(partner), 1);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Load all msg ids with the same status
        String[] msgIds = ZinaNative.loadMsgsIdsWithAttachmentStatus(1, code);
        assertEquals(4, msgIds.length);
        assertTrue(msgIds[0].contains(":"));
//        assertTrue(msgIds[0].equals(msgId_1));

        // Update 2 msg ids with a new status
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_3), IOUtils.encode(partner), 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));
        sqlCode = ZinaNative.storeAttachmentStatus(IOUtils.encode(msgId_4), IOUtils.encode(partner), 2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // Delete all msg is with status 2
        sqlCode = ZinaNative.deleteWithAttachmentStatus(2);
        assertFalse((sqlCode > SQLITE_OK && sqlCode < SQLITE_ROW));

        // No entries with status 2
        msgIds = ZinaNative.loadMsgsIdsWithAttachmentStatus(1, code);
        assertEquals(2, msgIds.length);
    }

    public void testEvengtHistoryPaging() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        DbConversationRepository convRepo = new DbConversationRepository(userName);
        Conversation conversation = new Conversation(partners[1]);
        convRepo.save(conversation);

        // Get a event/message history for the partner (in this case Bob)
        EventRepository evHistory = convRepo.historyOf(conversation);
        assertTrue(evHistory.exists());

        assertFalse(evHistory.exists(events[0]));       // No such event yet

        // create messages
        for (int i = 0; i < EVENT_COUNT_517; i++) {
            Message msg = new Message();
            msg.setId(String.format(FORMAT_EVENT_ID, i));   // a message (event) *must* have an id
            msg.setSender(partners[1]);
            msg.setText(String.format(FORMAT_EVENT_ID, i));
            evHistory.save(msg);                            // Save the message
        }

        int eventCount;
        int totalEventCount = 0;

        // going downwards from top, youngest element
        EventRepository.PagingContext pagingContext =
                new EventRepository.PagingContext(EventRepository.PagingContext.START_FROM_YOUNGEST,
                        PAGE_SIZE);
        do {
            List<Event> eventList = evHistory.list(pagingContext);
            eventCount = eventList.size();
            totalEventCount += eventCount;
        } while (eventCount > 0);
        assertTrue(totalEventCount == EVENT_COUNT_517);

        // going upwards from bottom, oldest element
        pagingContext =
                new EventRepository.PagingContext(EventRepository.PagingContext.START_FROM_OLDEST,
                        PAGE_SIZE);
        totalEventCount = 0;
        do {
            List<Event> eventList = evHistory.list(pagingContext);
            eventCount = eventList.size();
            totalEventCount += eventCount;
        } while (eventCount > 0);
        assertTrue(totalEventCount == EVENT_COUNT_517);

        // retrieve whole list at once
        List<Event> eventList = evHistory.list();
        assertNotNull(eventList);
        assertEquals(EVENT_COUNT_517, eventList.size());
        for (Event ev: eventList) {
            assertTrue(ev instanceof Message);
            assertEquals(partners[1], ((Message) ev).getSender());
        }

        evHistory.clear();          // clear all
        eventList = evHistory.list();
        assertEquals(0, eventList.size());
    }


    public void testEvengtHistoryPagingWithDelete() throws Exception {
        assertTrue(ZinaNative.repoIsOpen());

        DbConversationRepository convRepo = new DbConversationRepository(userName);
        Conversation conversation = new Conversation(partners[1]);
        convRepo.save(conversation);

        // Get a event/message history for the partner (in this case Bob)
        EventRepository evHistory = convRepo.historyOf(conversation);
        assertTrue(evHistory.exists());

        assertFalse(evHistory.exists(events[0]));       // No such event yet

        // create messages
        for (int i = 0; i < EVENT_COUNT_517; i++) {
            Message msg = new Message();
            msg.setId(String.format(FORMAT_EVENT_ID, i));   // a message (event) *must* have an id
            msg.setSender(partners[1]);
            msg.setText(String.format(FORMAT_EVENT_ID, i));
            evHistory.save(msg);                            // Save the message
        }

        // remove four pages of events in total
        for (int i = EVENT_COUNT_517 - (2 * PAGE_SIZE); i < EVENT_COUNT_517; i++) {
            Event event = evHistory.findById(String.format(FORMAT_EVENT_ID, i));
            if (event != null) {
                evHistory.remove(event);
            }
        }

        for (int i = 200 - (2 * PAGE_SIZE); i < 200; i++) {
            Event event = evHistory.findById(String.format(FORMAT_EVENT_ID, i));
            if (event != null) {
                evHistory.remove(event);
            }
        }

        {
            // retrieve whole list at once
            List<Event> eventList = evHistory.list();
            assertNotNull(eventList);
            assertEquals(EVENT_COUNT_517 - (4 * PAGE_SIZE), eventList.size());
        }

        int eventCount;
        int totalEventCount = 0;

        // going downwards from top, youngest element
        EventRepository.PagingContext pagingContext =
                new EventRepository.PagingContext(EventRepository.PagingContext.START_FROM_YOUNGEST,
                        PAGE_SIZE);
        do {
            List<Event> eventList = evHistory.list(pagingContext);
            eventCount = eventList.size();
            totalEventCount += eventCount;
        } while (eventCount > 0);
        assertTrue(totalEventCount == EVENT_COUNT_517 - (4 * PAGE_SIZE));

        // retrieve whole list at once
        List<Event> eventList = evHistory.list();
        assertNotNull(eventList);
        assertEquals(EVENT_COUNT_517 - (4 * PAGE_SIZE), eventList.size());
        for (Event ev: eventList) {
            assertTrue(ev instanceof Message);
            assertEquals(partners[1], ((Message) ev).getSender());
        }

        evHistory.clear();          // clear all
        eventList = evHistory.list();
        assertEquals(0, eventList.size());
    }
}
