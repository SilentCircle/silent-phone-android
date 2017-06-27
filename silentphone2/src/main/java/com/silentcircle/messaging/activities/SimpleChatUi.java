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
package com.silentcircle.messaging.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.IOUtils;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.R;

import org.json.JSONException;
import org.json.JSONObject;

//import static zina.JsonStrings.ACCEPTED;
import static zina.JsonStrings.GROUP_ATTRIBUTE;
import static zina.JsonStrings.GROUP_DESC;
import static zina.JsonStrings.GROUP_ID;
import static zina.JsonStrings.GROUP_MAX_MEMBERS;
import static zina.JsonStrings.GROUP_MEMBER_COUNT;
import static zina.JsonStrings.GROUP_MOD_TIME;
import static zina.JsonStrings.GROUP_NAME;
import static zina.JsonStrings.GROUP_OWNER;
import static zina.JsonStrings.MEMBER_ATTRIBUTE;
import static zina.JsonStrings.MEMBER_ID;
import static zina.JsonStrings.MEMBER_MOD_TIME;
import static zina.JsonStrings.MSG_ID;
import static zina.JsonStrings.MSG_MESSAGE;
import static zina.JsonStrings.MSG_RECIPIENT;
import static zina.JsonStrings.MSG_SENDER;
import static zina.JsonStrings.MSG_VERSION;

public class SimpleChatUi extends Activity {

    public static final String ACTION_GROUP_CREATE = "createGroup";
    public static final String ACTION_GROUP_INVITE = "invite";
    public static final String ACTION_GROUP_SHOW_INVITE = "showInvite";
    public static final String ACTION_GROUP_INVITE_ANSWER = "inviteAnswer";
    public static final String ACTION_SHOW_MEMBERS = "showMembers";
    public static final String ACTION_SEND_MSG = "sendMessage";
    public static final String ACTION_GROUP_LEAVE = "leaveGroup";
    public static final String ACTION_SHOW_MSG = "showMessage";

    // Use this during development and testing only
    public static final String ACTION_CLEAR_GROUP_DATA = "clearGroupData";

    public static final String EXTRA_GROUP_CMD_MESSAGE = "cmdMessage";

    private LinearLayout mainLayout;

    private TextInputEditText groupName;
    private TextInputLayout groupNameLabel;

    private TextInputEditText groupDescription;
    private TextInputEditText groupUser;
    private TextInputEditText groupMessage;

    private Button doAction;
    private TextView displayAction;
    private TextView actionResult;

    private String groupUuid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_chat_ui);
        mainLayout = (LinearLayout)findViewById(R.id.simpleGroupUi);
        groupName = (TextInputEditText)findViewById(R.id.groupName);
        groupNameLabel = (TextInputLayout)groupName.getParent();
        groupNameLabel.setErrorEnabled(true);

        groupDescription = (TextInputEditText)findViewById(R.id.groupDescription);
        groupUser = (TextInputEditText)findViewById(R.id.groupUser);
        groupMessage = (TextInputEditText)findViewById(R.id.groupMsg);

        doAction = (Button)findViewById(R.id.doAction);
        displayAction = (TextView)findViewById(R.id.action);
        actionResult = (TextView)findViewById(R.id.actionResult);

        showKnownGroups();
        parseIntentAndAct(getIntent());
    }

    private void parseIntentAndAct(Intent intent) {
        if (intent == null) {
            groupNameLabel.setError("No Intent");
            return;
        }
        String action = intent.getAction();
        if (action == null) {
            groupNameLabel.setError("No action specified");
            return;
        }

        int result;
        switch (action) {
            case ACTION_GROUP_CREATE:
                displayAction.setText("Create a new group");
                createNewGroup();
                break;

            case ACTION_GROUP_INVITE:
                displayAction.setText("Invite another user");
                inviteUser();
                break;

            case ACTION_GROUP_SHOW_INVITE:
                displayAction.setText("Show a group invitation");
                showInvitationAndAccept(intent);
                break;

            case ACTION_GROUP_LEAVE:
                displayAction.setText("Leave the test group");
                leaveGroup();
                break;

            case ACTION_GROUP_INVITE_ANSWER:
                displayAction.setText("Answer to invitation");
                showInvitationAnswer(intent);
                break;

            case ACTION_CLEAR_GROUP_DATA:
                displayAction.setText("Clear all group related data in DB");
                int[] code = new int[1];
                ZinaMessaging.zinaCommand("clearGroupData", null, code);
                showKnownGroups();
                break;

            case ACTION_SHOW_MEMBERS:
                displayAction.setText("Show members of the test group");
                showKnownGroups();
                showMembers();
                break;

            case ACTION_SEND_MSG:
                displayAction.setText("Send a message to the test group");
                showKnownGroups();
                sendMessage();
                break;

            case ACTION_SHOW_MSG:
                displayAction.setText("Got message for the test group");
                showMessage(intent);
                break;

            default:
                groupNameLabel.setError("Unknown command");
        }
    }

    private void createNewGroup() {
//        doAction.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String name = groupName.getText().toString();
//                String description = groupDescription.getText().toString();
//                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(description)) {
//                    groupNameLabel.setError("Missing data, cannot create group");
//                    return;
//                }
//                String groupUuid = ZinaMessaging.createNewGroup(IOUtils.encode(name), IOUtils.encode(description), 10);
//                actionResult.setText("Created group, id: " + groupUuid);
//            }
//        });
    }

    private void inviteUser() {
        actionResult.setText(groupUuid);

//        doAction.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String userId = groupUser.getText().toString();
//                if (TextUtils.isEmpty(groupUuid) || TextUtils.isEmpty(userId)) {
//                    groupNameLabel.setError("Missing group UUID or user id");
//                    return;
//                }
//                int result = ZinaMessaging.inviteUser(groupUuid, IOUtils.encode(userId));
//                Snackbar snackbar = Snackbar.make(mainLayout, "invite user result: " + result, Snackbar.LENGTH_LONG);
//                snackbar.show();
//            }
//        });
    }

    private void showInvitationAndAccept(final Intent intent) {
//        final byte[] commandMsg = intent.getByteArrayExtra(EXTRA_GROUP_CMD_MESSAGE);
//        groupName.setText(intent.getStringExtra(GROUP_NAME));
//        groupDescription.setText(intent.getStringExtra(GROUP_DESC));
//        groupUser.setText(intent.getStringExtra(MEMBER_ID));            // The inviting member
//
//        doAction.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                int result = ZinaMessaging.answerInvitation(commandMsg, true, null);
//                Snackbar snackbar = Snackbar.make(mainLayout, "invite user result: " + result, Snackbar.LENGTH_LONG);
//                snackbar.show();
//            }
//        });
    }

    private void showInvitationAnswer(Intent intent) {
//        groupName.setText(intent.getStringExtra(GROUP_NAME));
//        groupUser.setText(intent.getStringExtra(MEMBER_ID));            // The invited member
//        boolean accepted = intent.getBooleanExtra(ACCEPTED, false);
//        actionResult.setText("Invitation was " + (accepted ? "Accepted" : "Not Accepted"));
    }


    private void sendMessage() {
        if (TextUtils.isEmpty(groupUuid)) {
            groupNameLabel.setError("No group selected");
            return;
        }
        doAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = groupMessage.getText().toString();
                if (TextUtils.isEmpty(message)) {
                    groupNameLabel.setError("No message");
                    return;
                }
                final byte[] msgDescriptor = createMessageDescriptor(message);
                int result = ZinaMessaging.sendGroupMessage(msgDescriptor, null, null);
                actionResult.setText("Sent message: " + result);
            }
        });
    }

    private void showMessage(Intent intent) {
        groupName.setText(intent.getStringExtra(GROUP_ID));
        groupUser.setText(intent.getStringExtra(MSG_SENDER));
        groupMessage.setText(intent.getStringExtra(MSG_MESSAGE));
    }

    private void showKnownGroups() {
        int[] code = new int[1];
        byte[][] groups = ZinaMessaging.listAllGroups(code);

        if (groups == null || groups.length == 0) {
            groupName.setText(null);
            groupNameLabel.setError("No Group found");
            groupDescription.setText(null);
            groupUuid = null;
            return;
        }
        groupNameLabel.setError(null);

        Gson gson = new Gson();
        for (byte[] group : groups) {
            GroupData groupData = gson.fromJson(new String(group), GroupData.class);
            groupName.setText(groupData.groupName);
            groupDescription.setText(groupData.groupDescription);
            groupUuid = groupData.groupId;
        }
    }

    private void showMembers() {
        if (TextUtils.isEmpty(groupUuid)) {
            groupNameLabel.setError("No group selected");
            return;
        }
        int[] code = new int[1];
        byte[][] members = ZinaMessaging.getAllGroupMembers(groupUuid, code);

        Gson gson = new Gson();
        StringBuilder sb = new StringBuilder();
        for (byte[] member: members) {
            MemberData memberData = gson.fromJson(new String(member), MemberData.class);
            sb.append(memberData.getMemberId()).append(';');
        }
        groupUser.setText(sb.toString());
    }

    private void leaveGroup() {
        if (TextUtils.isEmpty(groupUuid)) {
            groupNameLabel.setError("No group selected");
            return;
        }
        doAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int result = ZinaMessaging.leaveGroup(groupUuid);
                actionResult.setText("Leave group: " + result);
            }
        });
    }

    private byte[] createMessageDescriptor(@NonNull String message) {

        String msgId = UUIDGen.makeType1UUID().toString();
        final JSONObject obj = new JSONObject();
        try {
            obj.put(MSG_VERSION, 1);
            obj.put(MSG_RECIPIENT, groupUuid);
            obj.put(MSG_ID, msgId);
            obj.put(MSG_MESSAGE, message);
        } catch (JSONException ignore) {
            // In practice, this superfluous exception can never actually happen.
        }
        return IOUtils.encode(obj.toString());
    }

    /**
     * Helper class to support the group data JSON parsing
     *
     * The 'lastModified' is given in seconds since the epoch, the getter multiplies
     * with 1000 and converts it into ms since the epoch.
     */
    private static class GroupData {
        @SerializedName(GROUP_ID) String groupId;
        @SerializedName(GROUP_NAME) String groupName;
        @SerializedName(GROUP_OWNER) String groupOwner;
        @SerializedName(GROUP_DESC) String groupDescription;
        @SerializedName(GROUP_MAX_MEMBERS) int groupMaxMembers;
        @SerializedName(GROUP_ATTRIBUTE) int groupAttribute;
        @SerializedName(GROUP_MEMBER_COUNT) int memberCount;
        @SerializedName(GROUP_MOD_TIME) long lastModified;

        public String getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getGroupOwner() {
            return groupOwner;
        }

        public String getGroupDescription() {
            return groupDescription;
        }

        public int getGroupMaxMembers() {
            return groupMaxMembers;
        }

        public int getGroupAttribute() {
            return groupAttribute;
        }

        public int getMemberCount() {
            return memberCount;
        }

        public long getLastModified() {
            return lastModified * 1000;
        }
    }


    /**
     * Helper class to support the group data JSON parsing
     *
     * The 'lastModified' is given in seconds since the epoch, the getter multiplies
     * with 1000 and converts it into ms since the epoch.
     */
    private static class MemberData {
        @SerializedName(GROUP_ID) String groupId;
        @SerializedName(MEMBER_ID) String memberId;
        @SerializedName(MEMBER_ATTRIBUTE) int memberAttribute;
        @SerializedName(MEMBER_MOD_TIME) long lastModified;

        public String getGroupId() {
            return groupId;
        }

        public String getMemberId() {
            return memberId;
        }

        public int getMemberAttribute() {
            return memberAttribute;
        }

        public long getLastModified() {
            return lastModified * 1000;
        }
    }
}
