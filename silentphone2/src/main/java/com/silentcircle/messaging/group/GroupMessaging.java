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
package com.silentcircle.messaging.group;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.silentcircle.SilentPhoneApplication;
import com.silentcircle.contacts.ContactPhotoManagerNew;
import com.silentcircle.logs.Log;
import com.silentcircle.messaging.listener.MessagingBroadcastManager;
import com.silentcircle.messaging.model.Conversation;
import com.silentcircle.messaging.model.MessageErrorCodes;
import com.silentcircle.messaging.model.MessageStates;
import com.silentcircle.messaging.model.event.ErrorEvent;
import com.silentcircle.messaging.model.event.Event;
import com.silentcircle.messaging.model.event.IncomingMessage;
import com.silentcircle.messaging.model.event.InfoEvent;
import com.silentcircle.messaging.model.event.Message;
import com.silentcircle.messaging.model.event.OutgoingMessage;
import com.silentcircle.messaging.providers.AvatarProvider;
import com.silentcircle.messaging.repository.ConversationRepository;
import com.silentcircle.messaging.repository.EventRepository;
import com.silentcircle.messaging.services.SCloudService;
import com.silentcircle.messaging.services.ZinaMessaging;
import com.silentcircle.messaging.util.Action;
import com.silentcircle.messaging.util.AsyncUtils;
import com.silentcircle.messaging.util.AttachmentUtils;
import com.silentcircle.messaging.util.AvatarUtils;
import com.silentcircle.messaging.util.ConversationUtils;
import com.silentcircle.messaging.util.Extra;
import com.silentcircle.messaging.util.MessageUtils;
import com.silentcircle.messaging.util.Notifications;
import com.silentcircle.messaging.util.UUIDGen;
import com.silentcircle.silentphone2.BuildConfig;
import com.silentcircle.silentphone2.R;
import com.silentcircle.silentphone2.util.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static zina.JsonStrings.ADD_MEMBERS;
import static zina.JsonStrings.COMMAND_TIME;
import static zina.JsonStrings.GROUP_ATTRIBUTE;
import static zina.JsonStrings.GROUP_AVATAR;
import static zina.JsonStrings.GROUP_BURN_MODE;
import static zina.JsonStrings.GROUP_BURN_SEC;
import static zina.JsonStrings.GROUP_COMMAND;
import static zina.JsonStrings.GROUP_DESC;
import static zina.JsonStrings.GROUP_ID;
import static zina.JsonStrings.GROUP_MAX_MEMBERS;
import static zina.JsonStrings.GROUP_NAME;
import static zina.JsonStrings.GROUP_OWNER;
import static zina.JsonStrings.LEAVE;
import static zina.JsonStrings.MEMBERS;
import static zina.JsonStrings.MEMBER_ID;
import static zina.JsonStrings.MSG_DEVICE_ID;
import static zina.JsonStrings.MSG_DISPLAY_NAME;
import static zina.JsonStrings.MSG_ID;
import static zina.JsonStrings.MSG_IDS;
import static zina.JsonStrings.MSG_MESSAGE;
import static zina.JsonStrings.MSG_SENDER;
import static zina.JsonStrings.MSG_VERSION;
import static zina.JsonStrings.NEW_AVATAR;
import static zina.JsonStrings.NEW_BURN;
import static zina.JsonStrings.NEW_GROUP;
import static zina.JsonStrings.NEW_NAME;
import static zina.JsonStrings.READ_RECEIPT;
import static zina.JsonStrings.REMOVE_MSG;
import static zina.JsonStrings.RM_MEMBERS;

/**
 * Implements the group chat / group messaging base functions.
 *
 * Created by werner on 15.06.16.
 */
public class GroupMessaging {

    private static final String TAG = GroupMessaging.class.getSimpleName();

    /* **************************************************************************************
     * All callback functions below run in the receiver thread, not a UI thread, and should
     * return as fast as possible, thus no long running other functions
     ************************************************************************************** */

    public int groupMsgReceive(Context ctx, byte[] messageDescriptor, byte[] attachmentDescriptor, byte[] messageAttributes) {
        Gson gson = new Gson();
        MessageDescriptor msgDescriptor = gson.fromJson(new String(messageDescriptor), MessageDescriptor.class);
        MessageAttributes msgAttribute = gson.fromJson(new String(messageAttributes), MessageAttributes.class);

        /*
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Received group message: [" + new String(messageDescriptor) + "]");
            Log.d(TAG, "         message attrs: [" + new String(messageAttributes) + "]");
        }
         */

        final String recipient = msgAttribute.getGroupId();
        final String sender = msgDescriptor.getSender();
        final String messageId = msgDescriptor.getMsgId();
        final String messageText = msgDescriptor.getMessage();

        if (!ConversationUtils.isGroupKnown(recipient)) {
            Log.e(TAG, "Received message " + messageId + " for an unknown group: " + recipient);
            return -1;
        }

        ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            Log.e(TAG, "Could not save received group message, no repository.");
            return -1;
        }

        final boolean isSyncMessage = getSelfUserName().equals(sender);
        final Conversation conversation =
                ConversationUtils.getOrCreateGroupConversation(ctx, recipient);
        if (conversation == null) {
            Log.e(TAG, "Could not save received group message, no conversation.");
            return -1;
        }

        final EventRepository events = repository.historyOf(conversation);
        if (events == null) {
            Log.e(TAG, "Could not save received group message, conversation history not found.");
            return -1;
        }

        final Message message = isSyncMessage
                ? new OutgoingMessage(sender, messageId, messageText)
                : new IncomingMessage(sender, messageId, messageText);

        /* parse attributes like location the same way as done for regular messages */
        String attributes = null;
        if (messageAttributes != null && messageAttributes.length > 0) {
            attributes = new String(messageAttributes);
        }
        if (!TextUtils.isEmpty(attributes)) {
            MessageUtils.parseAttributes(attributes, message);
        }

        if (attachmentDescriptor != null) {
            String attachment = new String(attachmentDescriptor);
            message.setAttachment(attachment);
            // Make sure the word "Attachment" is localized
            message.setText(ctx.getString(R.string.attachment));
        }

        /*
         * Message text empty at this point which indicates a command message.
         * Don't save it, return.
         */
        if (TextUtils.isEmpty(message.getText())) {
            return MessageErrorCodes.OK;
        }

        /*
         * Check whether event with the same id has already been received and is of the same type.
         * If it is, do not overwrite the message and do not update unread message counters, just
         * return.
         */
        Event duplicateEvent = events.findById(messageId);
        final boolean isDuplicate = duplicateEvent != null
                && (isSyncMessage ? duplicateEvent instanceof OutgoingMessage : duplicateEvent instanceof IncomingMessage);
        if (isDuplicate) {
            return MessageErrorCodes.OK;
        }

        message.setConversationID(recipient);
        message.setState(MessageStates.RECEIVED);
        message.setTime(System.currentTimeMillis());

        // start burn timer immediately
        if (message.getBurnNotice() > 0) {
            // Math.min(message.getComposeTime(), message.getTime()) could also be used
            message.setExpirationTime(message.getComposeTime()
                    + TimeUnit.SECONDS.toMillis(message.getBurnNotice()));
        }
        events.save(message);

        if (!isSyncMessage) {
            conversation.offsetUnreadMessageCount(1);
        }
        conversation.setLastModified(System.currentTimeMillis());
        repository.save(conversation);

        MessageUtils.notifyMessageReceived(ctx, recipient, null,
                message.isRequestReceipt(),
                conversation.isMuted(),
                msgDescriptor.getMsgId());

        if (attachmentDescriptor != null) {
            // Do an initial request to download the thumbnail
            // TODO this can be done when message is first visible ?
            if (!TextUtils.isEmpty(message.getAttachment()) && !AttachmentUtils.hasThumbnail(message)) {
                ctx.startService(SCloudService.getDownloadThumbnailIntent(message,
                        conversation.getPartner().getUserId(), ctx));
            }
        }

        return MessageErrorCodes.OK;
    }

    public int groupCmdReceive(final Context ctx, byte[] commandMessage) {
        final String command = new String(commandMessage);

        Gson gson = new Gson();
        GroupCommand cmd = gson.fromJson(command, GroupCommand.class);
        final String selfUserName = ZinaMessaging.getInstance().getUserName();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Received group command: " + command);
        }

        if (TextUtils.isEmpty(cmd.command)) {
            Log.w(TAG, "Command absent, returning");
            return MessageErrorCodes.OK;
        }

        Intent intent = null;
        switch (cmd.command) {
            case LEAVE:
                if (selfUserName.equals(cmd.getMemberId())) {
                    Log.d(TAG, "Invitation declined on sibling device for group "
                            + cmd.getGroupId() + ", leaving.");
                    /*
                     * as command is a sync version, don't send invitation response
                     * or leave notification when removing conversation
                     */
                    ConversationUtils.deleteConversation(cmd.getGroupId(), false);
                    MessageUtils.notifyConversationUpdated(ctx, cmd.getGroupId(), true);

                    /* if any views have this conversation open they should close */
                    intent = Action.CLOSE_CONVERSATION.intent();
                    Extra.PARTNER.to(intent, cmd.getGroupId());
                }
                break;

            case RM_MEMBERS:
                Log.d(TAG, "RM_MEMBERS: " + command + " Members: " + cmd.getMembers());
                handleGroupEvent(ctx, cmd);
                generateGroupTitleIfNecessary(ctx, cmd);
                generateGroupAvatarIfNecessary(ctx, cmd);
                break;

            case ADD_MEMBERS:
                Log.d(TAG, "ADD_MEMBERS: " + command + " Members: " + cmd.getMembers());
                // TODO reduce number of tasks for a group (e.g., using a queue for each group)
                // running separate tasks for each group is cause for title/avatar occasionally
                // not being set correctly when syncing groups
                handleGroupEvent(ctx, cmd);
                generateGroupTitleIfNecessary(ctx, cmd);
                /*
                 * when new group is created, member list is last to arrive, update avatar when all
                 * information is available.
                 */
                generateGroupAvatarIfNecessary(ctx, cmd);
                break;

            case NEW_GROUP:
                /*
                 * isGroupKnown(ctx, cmd.getGroupId()) can be used to guard against messages for
                 * groups unknown to Zina.
                 *
                 * Immediately create corresponding conversation and refresh conversations list.
                 */
                if (createGroupConversation(ctx, cmd)) {
                    intent = MessageUtils.getNotifyConversationUpdatedIntent(cmd.getGroupId(), false);
                    generateGroupTitleIfNecessary(ctx, cmd);
                }
                break;

            case NEW_AVATAR:
            case NEW_BURN:
            case NEW_NAME:
                /*
                 * isGroupKnown(ctx, cmd.getGroupId()) can be used to guard against messages for
                 * groups unknown to Zina.
                 */
                if (updateGroupConversation(ctx, cmd)) {
                    intent = MessageUtils.getNotifyConversationUpdatedIntent(cmd.getGroupId(), false);
                }
                break;
            case REMOVE_MSG:
                MessageUtils.removeMessage(cmd.getGroupId(), cmd.getMessageIds());
                break;
            case READ_RECEIPT:
                markGroupMessagesAsRead(ctx, cmd);
                Notifications.updateMessageNotification(ctx);
            default:
                intent = null;
                break;
        }
        if (intent != null) {
            MessagingBroadcastManager.getInstance(ctx).sendOrderedBroadcast(intent);
        }
        return MessageErrorCodes.OK;
    }

    public void groupStateCallback(Context ctx, int errorCode, byte[] stateInformation) {
        final String info = (stateInformation != null) ? new String(stateInformation) : "{}";
        Log.d(TAG, "Group message state report: " + errorCode + ", info: " + info);
        if (errorCode != ZinaMessaging.SIP_OK && errorCode != ZinaMessaging.SIP_ACCEPTED) {
            createErrorEvent(ctx, errorCode, info);
        }
    }

    /* **************************************************************************************
     * End of callback function section
     ************************************************************************************** */

    @NonNull
    private String getSelfUserName() {
        String selfUserName = ZinaMessaging.getInstance().getUserName();
        if (selfUserName == null) {
            selfUserName = "";
        }
        return selfUserName;
    }

    private boolean createGroupConversation(Context ctx, GroupCommand cmd) {
        ConversationRepository repository =
                ConversationUtils.getConversations();
        if (repository == null) {
            Log.e(TAG, "Could not create group conversation.");
            return false;
        }

        final String groupId = cmd.getGroupId();
        final Conversation conversation =
                ConversationUtils.getOrCreateGroupConversation(ctx, groupId);

        if (conversation == null) {
            Log.e(TAG, "Could not create group conversation.");
            return false;
        }

        String displayName = cmd.getGroupName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = ctx.getResources().getString(R.string.group_messaging_new_group_conversation);
        }
        conversation.getPartner().setDisplayName(displayName);
        conversation.getPartner().setGroup(true);
        conversation.setLastModified(System.currentTimeMillis());
        repository.save(conversation);

        return true;
    }

    private void handleGroupEvent(@NonNull final Context ctx, @NonNull final GroupCommand cmd) {
        if (!ConversationUtils.isGroupKnown(cmd.getGroupId())) {
            return;
        }
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                List<Event> events = MessageUtils.createInfoEvent(ctx, cmd, getSelfUserName());
                if (events == null || events.size() == 0) {
                    Log.e(TAG, "Could not create group event.");
                }
                if (ADD_MEMBERS.equals(cmd.command)) {
                    ConversationUtils.checkUserDevices(ctx, cmd.getGroupId(), cmd.getMembers());
                }
            }
        });
    }

    private boolean updateGroupConversation(Context ctx, GroupCommand cmd) {
        final ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            Log.e(TAG, "Could not create group conversation.");
            return false;
        }

        final String groupId = cmd.getGroupId();
        final Conversation conversation = ConversationUtils.getConversation(groupId);

        if (conversation == null) {
            Log.e(TAG, "Could not update group conversation.");
            return false;
        }

        switch (cmd.command) {
            case NEW_AVATAR:
                updateGroupAvatar(ctx, cmd);
                break;
            case NEW_BURN:
                long burnTime = cmd.getBurnTime();
                long previousBurnTime = conversation.getBurnDelay();
                conversation.setBurnDelay(burnTime);
                conversation.setBurnNotice(burnTime > 0);
                handleGroupEvent(ctx, cmd);
                if (previousBurnTime > burnTime) {
                    // launch task to update conversation events
                    updateConversationEvents(repository, conversation);
                }
                break;
            case NEW_NAME:
                conversation.getPartner().setDisplayName(cmd.getGroupName());
                handleGroupEvent(ctx, cmd);
                MessageUtils.requestRefresh(groupId);
                break;
        }
        conversation.setLastModified(System.currentTimeMillis());
        repository.save(conversation);

        return true;
    }

    private void updateGroupAvatar(final Context ctx, final GroupCommand cmd) {
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String groupId = cmd.getGroupId();
                    final String avatarInfo = cmd.getAvatarInfo();

                    ConversationRepository repository = ConversationUtils.getConversations();
                    if (repository != null) {
                        Conversation conversation = repository.findByPartner(groupId);
                        if (conversation != null && (!AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo)
                                || !TextUtils.isEmpty(conversation.getAvatarUrl()))) {
                            /*
                             * Currently there is no info available on who changed the avatar,
                             * leave details empty.
                             */
                            final InfoEvent event = MessageUtils.createInfoEvent(cmd.getGroupId(),
                                    AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo)
                                            ? InfoEvent.INFO_AVATAR_REMOVED : InfoEvent.INFO_NEW_AVATAR,
                                    "Group avatar updated", "{}");
                            event.setAttachment(avatarInfo);
                            repository.historyOf(conversation).save(event);
                        }
                    }

                    if (AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo)) {
                        AvatarUtils.setGeneratedGroupAvatar(ctx, groupId);
                    }
                    else if (!TextUtils.isEmpty(avatarInfo)) {
                        // update conversation's avatar url so new avatar gets downloaded
                        if (repository != null) {
                            Conversation conversation = repository.findByPartner(groupId);
                            if (conversation != null) {
                                conversation.setAvatarUrl(AvatarProvider.AVATAR_TYPE_DOWNLOADED);
                                repository.save(conversation);
                            }
                        }
                        // assume that info is JSON describing attachment
                        ConversationUtils.verifyConversationAvatar(ctx, cmd.getGroupId(), avatarInfo);
                    }

                    requestRefresh(ctx, groupId);
                } catch (Throwable t) {
                    // Existing or default avatar will be used.
                }
            }
        });
    }

    private void generateGroupTitleIfNecessary(@NonNull final Context ctx, @NonNull final GroupCommand cmd) {
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String groupId = cmd.getGroupId();
                    final ConversationUtils.GroupData groupData = ConversationUtils.getGroup(groupId);
                    final String groupName = groupData != null ? groupData.getGroupName() : null;
                    // if group's name is not set use generated name as display name
                    if (TextUtils.isEmpty(groupName)) {
                        Set<String> participantIds = new HashSet<>();
                        // add all already known participants
                        participantIds.addAll(ConversationUtils.getGroupParticipants(groupId));
                        // add or remove participants from group command
                        List<String> members = cmd.getMembers();
                        String command = cmd.getCommand();
                        if (members != null) {
                            if (ADD_MEMBERS.equals(command)) {
                                participantIds.addAll(members);
                            }
                            else if (RM_MEMBERS.equals(command)) {
                                participantIds.removeAll(members);
                            }
                        }
                        ConversationUtils.setGeneratedGroupName(groupId, participantIds);

                        // ask views to refresh to see the changes
                        MessageUtils.requestRefresh(groupId);
                    }
                } catch (Throwable t) {
                    // Existing or default avatar will be used.
                }
            }
        });
    }

    private void generateGroupAvatarIfNecessary(@NonNull final Context ctx, @NonNull final GroupCommand cmd) {
        /*
         * Apply rule: if generated group avatar is not yet set but is necessary, generate it.
         */
        AsyncUtils.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String groupId = cmd.getGroupId();
                    final ConversationUtils.GroupData groupData = ConversationUtils.getGroup(groupId);
                    final String avatarInfo = groupData != null ? groupData.getAvatarInfo() : null;

                    if (TextUtils.isEmpty(avatarInfo)
                            || AvatarProvider.AVATAR_TYPE_GENERATED.equals(avatarInfo)) {
                        AvatarUtils.setGeneratedGroupAvatar(ctx, groupId);
                    }

                    requestRefresh(ctx, groupId);
                } catch (Throwable t) {
                    // Existing or default avatar will be used.
                }
            }
        });
    }

    /*
     * {"version":1,
     *  "details":{
     *    "name":"ardr3",
     *    "scClientDevId":"7e50c4a3161509dbe47bdbf7e4aaccdd",
     *    "otherInfo":"Error processing plain text message",
     *    "msgId":"92E7DE40-F119-11E6-B026-1102556DC449",
     *    "errorCode":1,
     *    "sentToId":""},
     *  "type":-1}
     *
     *  {"version":1,
     *   "details":{
     *     "name":"arudzitissm",
     *     "scClientDevId":"411d11997d215867d5e1e692f2ec395e",
     *     "otherInfo":"Error processing plain text message",
     *     "msgId":"18D8211C-F427-11E6-BE0A-F59962EAEB0E",
     *     "errorCode":1,
     *     "sentToId":""
     *     },
     *   "type":-1,
     *   "grpId":"E453F7B8-F426-11E6-ADBB-7B5D1C2C7872"
     *  }
     */
    private void createErrorEvent(final Context context, final int errorCode, final String info) {
        final ErrorEvent event = MessageUtils.parseErrorMessage(new ErrorEvent(errorCode), info);
        final String sender = Utilities.removeSipParts(event.getSender());

        if (TextUtils.isEmpty(sender)) {
            Log.e(TAG, "Failed to determine sender for error [" + info + "]");
            // for developers show received info in a dialog
            /*
            if (SPAPreferences.getInstance(context).isDeveloper()) {
                DialogHelperActivity.showDialog(R.string.debug_information_title,
                        "Group message state report: " + errorCode + " " + info, android.R.string.ok, -1);
            }
             */
            return;
        }

        Log.e(TAG, "createErrorEvent sender [" + sender + "]");

        ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            Log.e(TAG, "Could not retrieve conversation repository.");
            return;
        }

        /*
         * group id can be absent in case of decryption failures!
         * if so, event should be saved in all group conversations where sender is present
         */
        final String groupId = event.getConversationID();
        List<String> conversations;
        if (TextUtils.isEmpty(groupId)) {
            Log.w(TAG, "Group id not found, save event in all relevant group conversations.");
            conversations = ConversationUtils.getConversationsWithParticipant(sender);
        }
        else {
            conversations = new ArrayList<>();
            conversations.add(groupId);
        }

        /*
         * Go through conversation id list and save the constructed error event.
         */
        for (String conversationId : conversations) {
            final Conversation conversation = ConversationUtils.getConversation(conversationId);
            if (conversation == null) {
                continue;
            }

            final EventRepository events = repository.historyOf(conversation);

            event.setDuplicate(false);
            event.setId(UUIDGen.makeType1UUID().toString());
            event.setTime(System.currentTimeMillis());
            event.setConversationID(conversationId);
            events.save(event);

            MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(), sender,
                    true, ZinaMessaging.UPDATE_ACTION_MESSAGE_SEND, event.getId());
        }
    }

    private void updateConversationEvents(final ConversationRepository repository,
            final Conversation conversation) {
        if (repository == null || conversation == null) {
            return;
        }

        final String groupId = conversation.getPartner().getUserId();
        Runnable runnable = new Runnable() {

            @Override
            public void run() {
                AsyncUtils.execute(new com.silentcircle.messaging.task.RefreshTask(
                        repository, conversation) {
                    @Override
                    protected void onPostExecute(List<Event> events) {
                        MessageUtils.requestRefresh(groupId);
                    }
                }, groupId);
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(runnable);
    }

    private void requestRefresh(Context ctx, String groupId) {
        // clear photo cache to see the result for updated avatar
        ContactPhotoManagerNew.getInstance(ctx).refreshCache();
        // ask views to refresh to see the changes
        MessageUtils.requestRefresh(groupId);
    }

    private void markGroupMessagesAsRead(@NonNull final Context ctx, @NonNull final GroupCommand cmd) {
        final ConversationRepository repository = ConversationUtils.getConversations();
        if (repository == null) {
            Log.e(TAG, "Could not mark group message as read, no repository.");
            return;
        }

        final String groupId = cmd.getGroupId();
        final Conversation conversation = ConversationUtils.getConversation(groupId);

        if (conversation == null) {
            Log.e(TAG, "Could not mark group message as read, no conversation.");
            return;
        }

        final EventRepository events = repository.historyOf(conversation);

        String[] messageIds = cmd.getMessageIds();
        if (messageIds != null) {
            int position = 0;
            CharSequence[] ids = new CharSequence[messageIds.length];
            for (String messageId : messageIds) {
                final Event event = events.findById(messageId);
                if (event instanceof Message) {
                    final Message message = (Message)event;
                    int state = message.getState();
                    if (state != MessageStates.READ) {
                        // decrement counters only for incoming messages which would have RECEIVED state
                        if (state == MessageStates.RECEIVED) {
                            int unreadMessageCount = conversation.getUnreadMessageCount();
                            conversation.setUnreadMessageCount(unreadMessageCount > 0 ? (unreadMessageCount - 1) : 0);
                            repository.save(conversation);
                        }
                        // if message is already marked for burning, do not update its state to a weaker one
                        if (state != MessageStates.BURNED) {
                            state = MessageStates.READ;
                        }
                        message.setState(state);
                        events.save(message);
                        ids[position++] = messageId;
                    }
                    else {
                        Log.w(TAG, "markGroupMessagesAsRead: " + messageId + " already has state READ");
                    }
                } else {
                    Log.d(TAG, "markGroupMessagesAsRead: event not found for read receipt " + messageId);
                }
            }
            MessageUtils.notifyConversationUpdated(SilentPhoneApplication.getAppContext(),
                    groupId, false,
                    ZinaMessaging.UPDATE_ACTION_MESSAGE_STATE_CHANGE, ids);
        }
    }

    /**
     * Helper class to support the group command JSON data parsing
     *
     * Not every field is available or filled for every command and command variation. Thus
     * the user of this class needs to know how to deal with a command and its values. As said
     * it's a helper class, the caller should know about the semantics of the commands.
     */
    public static class GroupCommand {
        @SerializedName(GROUP_COMMAND) String command;
        @SerializedName(GROUP_ID) String groupId;
        @SerializedName(GROUP_NAME) String groupName;
        @SerializedName(GROUP_OWNER) String groupOwner;
        @SerializedName(GROUP_DESC) String groupDescription;
        @SerializedName(GROUP_MAX_MEMBERS) int groupMaxMembers;
        @SerializedName(GROUP_ATTRIBUTE) int groupAttribute;
        @SerializedName(MEMBER_ID) String memberId;
        @SerializedName(GROUP_BURN_SEC) long burnTime;
        @SerializedName(GROUP_BURN_MODE) int burnMode;
        @SerializedName(GROUP_AVATAR) String avatarInfo;
        @SerializedName(COMMAND_TIME) long timeStamp;
        @SerializedName(MEMBERS) String[] members;
        @SerializedName(MSG_IDS) String[] messageIds;

        public String getCommand() {
            return command;
        }

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

        public String getMemberId() {
            return memberId;
        }

        public String getAvatarInfo() {
            return avatarInfo;
        }

        public long getBurnTime() {
            return burnTime;
        }

        public int getBurnMode() {
            return burnMode;
        }

        public long getTimestamp() {
            return TimeUnit.SECONDS.toMillis(timeStamp);
        }

        @Nullable
        public List<String> getMembers() {
            return members != null ? Arrays.asList(members) : null;
        }

        @Nullable
        public String[] getMessageIds() {
            return messageIds;
        }
    }

    /*
    {
    "version": 1,
    "sender": "uAGroupMember1",
    "display_name": "uAGroupMember1",
    "scClientDevId": "def11fed",
    "msgId": "5D670E68-3787-11E6-A952-3570306AF836",
    "message": "Group test message."
    }
     */
    public static class MessageDescriptor {
        @SerializedName(MSG_VERSION) String version;
        @SerializedName(MSG_SENDER) String sender;
        @SerializedName(MSG_DISPLAY_NAME) String displayName;
        @SerializedName(MSG_DEVICE_ID) String clientDevId;
        @SerializedName(MSG_ID) String msgId;
        @SerializedName(MSG_MESSAGE) String message;

        public String getVersion() {
            return version;
        }

        public String getSender() {
            return sender;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getClientDevId() {
            return clientDevId;
        }

        public String getMsgId() {
            return msgId;
        }

        public String getMessage() {
            return message;
        }
    }

    /*
    {"grpId":"1D0A5882-378E-11E6-8F01-1F8EFAD17358","hash":"D4sA4UQKzmL1EyLBVk4poBwYpC3YkhYJlEOcd7ftbR0="}
     */
    // Currently only interested in the group id
    private static class MessageAttributes {
        @SerializedName(GROUP_ID) String groupId;

        public String getGroupId() {
            return groupId;
        }
    }
}
