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

package com.silentcircle.messaging.tests.services;

import android.test.AndroidTestCase;
import android.util.Log;

import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.json.JSONEventAdapter;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Test functions for AxoMessaging.
 * Created by werner on 07.06.15.
 */
public class AxoMessagingTester extends AndroidTestCase {

    static {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("tina");
        System.loadLibrary("aec");
        System.loadLibrary("tivi");
    }

    private static String msgDescriptor = "{\n" +
            " \"message\":\"{\n" +
            "    \\\"sender\\\":\\\"nodid1\\\",\\\"id\\\":\\\"3a9c09f2-f0d7-487f-8f7f-85b82ded3f7c\\\",\n" +
            "    \\\"burn_notice\\\":0,\\\"time\\\":1433660352705,\n" +
            "    \\\"text\\\":\\\"{\\\\\\\"message\\\\\\\":\\\\\\\"Testing\\\\\\\",\\\\\\\"request_receipt\\\\\\\":1}\\\",\n" +
            "    \\\"conversation_id\\\":\\\"Wernerd\\\",\\\"state\\\":\\\"COMPOSED\\\",\n" +
            "    \\\"expiration_time\\\":9223372036854775807,\\\"type\\\":\\\"OUTGOING_MESSAGE\\\",\n" +
            "    \\\"recipient\\\":\\\"nodid1\\\"}\",\n" +
            " \"scClientDevId\":\"a950822206fa77a5f729f4ea8743e183\",\n" +
            " \"sender\":\"Wernerd\",\n" +
            " \"version\":1\n" +
            " }";
    @Override
    protected void setUp() throws Exception {
    }

    public void testMessageParse() throws Exception {
        JSONObject obj;
        JSONObject messageObj = null;
        String sender;
        String scDevId;
        String message;
        // Get information from internally used message descriptor
        obj = new JSONObject(msgDescriptor);
        sender = obj.getString("sender");
        scDevId = obj.getString("scClientDevId");
        message = obj.getString("message");
        messageObj = new JSONObject(message);
        JSONEventAdapter adapter = new JSONEventAdapter();
        Message incoming = (Message) adapter.adapt(messageObj);
        Log.d("AxoTester", "++++ sender: " + incoming.getSender());
        Log.d("AxoTester", "++++ id: " + incoming.getId());
    }
}
