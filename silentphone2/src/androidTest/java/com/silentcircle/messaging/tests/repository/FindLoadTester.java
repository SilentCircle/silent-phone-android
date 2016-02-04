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

package com.silentcircle.messaging.tests.repository;

import android.test.AndroidTestCase;
import android.util.Log;

import com.silentcircle.messaging.model.Contact;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.SCloudObject;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.json.JSONEventAdapter;
import com.silentcircle.messaging.repository.DbRepository.DbConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.repository.ObjectRepository;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import axolotl.AxolotlNative;

/**
 * Perform some find stress testing.
 *
 * Created by werner on 20.09.15.
 */
public class FindLoadTester extends AndroidTestCase {

    private static final String TAG = "FindLoadTester";
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

    private boolean mHadNullConversation;

    private static final String[] partners = {"Alice", "Bob", "Trent", "Mallory"};
    private static final String[] events = {"event_1", "event_2", "event_3", "event_4"};
    private static final String[] objects = {"object_1", "object_2", "object_3", "object_4"};


    @Override
    protected void setUp() throws Exception {
        if (!firstSetup) {
            File dbFile = new File(getContext().getFilesDir(), "testdb.db");
            if (dbFile.exists())
                dbFile.delete();
            Log.d("RepoTester", "DB path: " + dbFile.getAbsolutePath());
            AxolotlNative.repoOpenDatabase(dbFile.getAbsolutePath(), repoKey);
            firstSetup = true;
        }
    }

    public void testConversationFromThreads() throws Exception {
        assertTrue(AxolotlNative.repoIsOpen());

        DbConversationRepository testConv = new DbConversationRepository(getContext(), userName);
        assertTrue(testConv.exists());   // Does a repo for this local user exist?

        assertFalse(testConv.exists(partners[0]));  // not yet

        Conversation conversation = new Conversation();
        conversation.setPartner(new Contact(partners[0]));  // a conversation *must* have a partner
        conversation.setBurnNotice(true);
        conversation.setBurnDelay(TimeUnit.DAYS.toSeconds(7));
        conversation.setLastModified(System.currentTimeMillis());
        conversation.setLocationEnabled(true);
        testConv.save(conversation);
        assertTrue(testConv.exists(partners[0]));

        ExecutorService service =  Executors.newFixedThreadPool(4);

        /*
         * Try to find conversation from different threads multiple times.
         * Each time a valid conversation should be returned.
         */
        for (int i = 0; i < 100; i++) {
            service.submit(new Runnable() {
                @Override
                public void run() {
                    DbConversationRepository testConv = new DbConversationRepository(getContext(), userName);
                    for (int i = 0; i < 100; i++) {
                        Conversation convRead = testConv.findById(partners[0]);
                        if (convRead == null) {

                            System.out.println("AAA convRead: " + convRead + " - " + android.os.Process.myTid() + " - " + i);
                        }
                        if (convRead == null) {
                            mHadNullConversation = true;
                        }
                    }

                }
            });
        }

        /* Wait for test to finish */
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        } catch (Exception e) {
            // ignore interruption
        }

        Conversation convRead = testConv.findById(partners[0]);
        assertNotNull(convRead);
        assertEquals(partners[0], convRead.getPartner().getUsername());
        assertTrue(convRead.hasBurnNotice());

        // Conversation was always found
        assertFalse(mHadNullConversation);

        testConv.remove(conversation);
        assertFalse(testConv.exists(partners[0]));
    }
}
