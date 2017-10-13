/*
Copyright 2016-2017 Silent Circle, LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
#ifndef APPINTERFACEIMPL_H
#define APPINTERFACEIMPL_H

/**
 * @file AppInterfaceImpl.h
 * @brief Implementation of the UI interface methods
 * @ingroup Zina
 * @{
 */

#include <stdint.h>
#include <map>

#include "AppInterface.h"
#include "../storage/sqlite/SQLiteStoreConv.h"
#include "../util/UUID.h"
#include "../Constants.h"
#include "../ratchet/state/ZinaConversation.h"

typedef int32_t (*HTTP_FUNC)(const std::string& requestUri, const std::string& requestData, const std::string& method, std::string* response);

// Same as in ScProvisioning, keep in sync
typedef int32_t (*S3_FUNC)(const std::string& region, const std::string& requestData, std::string* response);

namespace zina {
typedef enum CmdQueueCommands_ {
    SendMessage = 1,
    ReceivedRawData,
    ReceivedTempMsg,
    CheckForRetry,
    CheckRemoteIdKey,
    SetIdKeyChangeFlag,
    ReKeyDevice,
    ReScanUserDevices
} CmdQueueCommands;

typedef struct CmdQueueInfo_ {
    CmdQueueCommands command;
    std::string stringData1;
    std::string stringData2;
    std::string stringData3;
    std::string stringData4;
    std::string stringData5;
    std::string stringData6;
    std::string stringData7;
    uint64_t uint64Data;
    int64_t int64Data;
    int32_t int32Data;
    bool boolData1;
    bool boolData2;
} CmdQueueInfo;

typedef enum sendCallbackAction_ {
    NoAction = 0,
    ReKeyAction,
    ReScanAction
} SendCallbackAction;

// Define useful names/aliases for the CmdQueueInfo structure, send message operation
#define queueInfo_recipient     stringData1
#define queueInfo_deviceId      stringData2
#define queueInfo_msgId         stringData3
#define queueInfo_deviceName    stringData4
#define queueInfo_message       stringData5
#define queueInfo_attachment    stringData6
#define queueInfo_attributes    stringData7
#define queueInfo_transportMsgId uint64Data
#define queueInfo_callbackAction int32Data
#define queueInfo_toSibling     boolData1
#define queueInfo_newUserDevice boolData2

// Define useful names/aliases for the CmdQueueInfo structure, receive message operation
#define queueInfo_envelope      stringData1
#define queueInfo_uid           stringData2
#define queueInfo_displayName   stringData3
#define queueInfo_supplement    stringData4
#define queueInfo_message_desc  stringData5
#define queueInfo_sequence      int64Data
#define queueInfo_msgType       int32Data

// Define bits for local retrnion handling
#define RETAIN_LOCAL_DATA       0x1
#define RETAIN_LOCAL_META       0x2

class SipTransport;
class MessageEnvelope;
class GroupChangeSet;
class GroupUpdateSetName;
class GroupUpdateSetAvatar;
class GroupUpdateSetBurn;
class GroupBurnMessage;

// This is the ping command the code sends to new devices to create an Axolotl setup
static std::string ping("{\"cmd\":\"ping\"}");

class AppInterfaceImpl : public AppInterface
{
public:
#ifdef UNITTESTS
    explicit AppInterfaceImpl(SQLiteStoreConv* store) : AppInterface(), tempBuffer_(NULL), store_(store), transport_(NULL) {}
    AppInterfaceImpl(SQLiteStoreConv* store, const std::string& ownUser, const std::string& authorization, const std::string& scClientDevId) :
                    AppInterface(), tempBuffer_(NULL), tempBufferSize_(0), ownUser_(ownUser), authorization_(authorization),
                    scClientDevId_(scClientDevId), store_(store), transport_(NULL), siblingDevicesScanned_(false),
                    drLrmm_(false), drLrmp_(false), drLrap_(false), drBldr_(false), drBlmr_(false), drBrdr_(false), drBrmr_(false) {}
#endif
    AppInterfaceImpl(const std::string& ownUser, const std::string& authorization, const std::string& scClientDevId,
                     RECV_FUNC receiveCallback, STATE_FUNC stateReportCallback, NOTIFY_FUNC notifyCallback,
                     GROUP_MSG_RECV_FUNC groupMsgCallback, GROUP_CMD_RECV_FUNC groupCmdCallback,
                     GROUP_STATE_FUNC groupStateCallback);

    ~AppInterfaceImpl();

    // Documentation see AppInterface.h
    void setTransport(Transport* transport) { transport_ = transport; }

    Transport* getTransport()               { return transport_; }

    int32_t receiveMessage(const std::string& messageEnvelope, const std::string& uid, const std::string& displayName);

    std::string* getKnownUsers();

    std::string getOwnIdentityKey();

    std::shared_ptr<std::list<std::string> > getIdentityKeys(std::string& user);

    int32_t registerZinaDevice(std::string* result);

    int32_t removeZinaDevice(std::string& scClientDevId, std::string* result);

    int32_t newPreKeys(int32_t number);

    void addMsgInfoToRunQueue(std::unique_ptr<CmdQueueInfo> messageToProcess);

    int32_t getNumPreKeys() const;

    void rescanUserDevices(const std::string& userName);

    void reKeyAllDevices(const std::string &userName);

    void reKeyDevice(const std::string &userName, const std::string &deviceId);

    void setIdKeyVerified(const std::string& userName, const std::string& deviceId, bool flag);

    std::string createNewGroup(const std::string& groupName, std::string& groupDescription);

    bool modifyGroupSize(const std::string& groupId, int32_t newSize);

    int32_t setGroupName(const std::string& groupUuid, const std::string* groupName);

    int32_t setGroupBurnTime(const std::string& groupUuid, uint64_t burnTime, int32_t mode);

    int32_t setGroupAvatar(const std::string& groupUuid, const std::string* avatar);

    int32_t addUser(const std::string& groupUuid, const std::string& userId);

    int32_t removeUserFromAddUpdate(const std::string& groupUuid, const std::string& userId);

    int32_t cancelGroupChangeSet(const std::string& groupUuid);

    int32_t applyGroupChangeSet(const std::string& groupId);

    int32_t sendGroupMessage(const std::string& messageDescriptor, const std::string& attachmentDescriptor, const std::string& messageAttributes);

    int32_t sendGroupMessageToMember(const std::string &messageDescriptor, const std::string &attachmentDescriptor,
                                     const std::string &messageAttributes, const std::string &recipient,
                                     const std::string &deviceId);

    int32_t sendGroupCommandToMember(const std::string& groupId, const std::string &member, const std::string &msgId,
                                     const std::string &command);

    int32_t leaveGroup(const std::string& groupId);

    int32_t removeUser(const std::string& groupId, const std::string& userId, bool allowOwnUser = false);

    int32_t removeUserFromRemoveUpdate(const std::string& groupUuid, const std::string& userId);

    int32_t burnGroupMessage(const std::string& groupId, const std::vector<std::string>& messageIds);

    DEPRECATED_ZINA std::shared_ptr<std::list<std::shared_ptr<PreparedMessageData> > >
    prepareMessage(const std::string& messageDescriptor,
                   const std::string& attachmentDescriptor,
                   const std::string& messageAttributes,
                   bool normalMsg, int32_t* result);

    DEPRECATED_ZINA std::shared_ptr<std::list<std::shared_ptr<PreparedMessageData> > >
    prepareMessageToSiblings(const std::string &messageDescriptor,
                             const std::string &attachmentDescriptor,
                             const std::string &messageAttributes,
                             bool normalMsg, int32_t *result);

    std::unique_ptr<std::list<std::unique_ptr<PreparedMessageData> > >
    prepareMessageNormal(const std::string &messageDescriptor,
                         const std::string &attachmentDescriptor,
                         const std::string &messageAttributes,
                         bool normalMsg, int32_t *result);

    std::unique_ptr<std::list<std::unique_ptr<PreparedMessageData> > >
    prepareMessageSiblings(const std::string &messageDescriptor,
                           const std::string &attachmentDescriptor,
                           const std::string &messageAttributes,
                           bool normalMsg, int32_t *result);

    int32_t doSendMessages(std::shared_ptr<std::vector<uint64_t> > transportIds);

    int32_t removePreparedMessages(std::shared_ptr<std::vector<uint64_t> > transportIds);

    // **** Below are methods for this implementation, not part of AppInterface.h
    /**
     * @brief Return the stored error code.
     * 
     * Functions of this implementation store error code in case they detect
     * a problem and return @c NULL, for example. In this case the caller should
     * get the error code and the additional error information for detailled error
     * data.
     * 
     * Functions overwrite the stored error code only if they return @c NULL or some
     * other error indicator.
     * 
     * @return The stored error code.
     */
    int32_t getErrorCode() const             { return errorCode_; }

    /**
     * @brief Get name of local user for this Axolotl conversation.
     */
    const std::string& getOwnUser() const         { return ownUser_; }

    /**
     * @brief Get authorization data of local user.
     */
    const std::string& getOwnAuthrization() const { return authorization_; }

    /**
     * @brief Get own device identifier.
     * @return Reference to own device identifier string
     */
    const std::string& getOwnDeviceId() const     { return scClientDevId_; }

    /**
     * @brief Return the stored error information.
     * 
     * Functions of this implementation store error information in case they detect
     * a problem and return @c NULL, for example. In this case the caller should
     * get the error code and the additional error information for detailed error
     * data.
     * 
     * Functions overwrite the stored error information only if they return @c NULL 
     * or some other error indicator.
     * 
     * @return The stored error information string.
     */
    const std::string& getErrorInfo() { return errorInfo_; }

    /**
     * @brief Initialization code must set a HTTP helper function
     * 
     * @param httpHelper Pointer to the helper functions
     */
    static void setHttpHelper(HTTP_FUNC httpHelper);

    /**
     * @brief Initialization code must set a S3 helper function
     *
     * This is used ot post data to Amazon S3 for data retention
     * purposes. If it is not called then data retention is disabled.
     *
     * @param s3Helper Pointer to the helper function
     */
    static void setS3Helper(S3_FUNC httpHelper);

    void setFlags(int32_t flags)  { flags_ = flags; }

    bool isRegistered()           { return ((flags_ & 0x1) == 1); }

    SQLiteStoreConv* getStore()   { return store_; }

    /**
     * This is a functions we need only during development and testing.
     */
    void clearGroupData();


    /**
     * @brief Check for unhandled raw or plain messages in the database and retry.
     *
     * To keep the correct order of messages the function first checks and retries
     * plain messages and then for raw (encrypted) messages in the database queues.
     */
    void retryReceivedMessages();

    /**
     * @brief Set the data retention flags for the local user.
     *
     * The caller sets up a JSON formatted string that holds the data retention flags
     * for the local user. The JSON string
     *<verbatim>
     * {
     * "lrmm": "true" | "false",
     * "lrmp": "true" | "false",
     * "lrap": "true" | "false",
     * "bldr": "true" | "false",
     * "blmr": "true" | "false",
     * "brdr": "true" | "false",
     * "brmr": "true" | "false"
     * }
     *<endverbatim>
     * If the application does not call this function after ZINA initialization then ZINA
     * assumes "false" for each flag, same if the JSON string does not contain a flag or
     * the flag's value is not "true" or "false". Otherwise ZINA sets the flag to the
     * given value.
     *
     * @param flagsJson The JSON data of the flags to set.
     * @return SUCCESS (0) or error code. The function does not change flags in case of
     *         error return
     */
    int32_t setDataRetentionFlags(const std::string& jsonFlags);

    /**
     * @brief Send HELLO command for all groups to a member's device
     *
     * Check the membership of the user in all our groups. If the user is a group member
     * then create and send a group HELLO command to the user'S device. The HELLO command
     * contains a change set which has all our group meta data and members.
     *
     * @param userId The user who added a new device
     * @param deviceId The device id of the new device
     * @param deviceName The device's name
     * @return
     */
    int32_t performGroupHellos(const std::string &userId, const std::string &deviceId, const std::string &deviceName);

    /**
     * @brief Create and send a group HELLO command to a device
     *
     * A group member's new device needs the group data. This function creates and sends
     * the data to the new device.
     *
     * @param groupId To which group this data belongs
     * @param deviceId The new device's device id
     * @param deviceName The device's name
     * @return {@code SUCCESS} or an error code.
     */
    int32_t performGroupHello(const std::string &groupId, const std::string &userId, const std::string &deviceId, const std::string &deviceName);

#ifdef UNITTESTS
        void setStore(SQLiteStoreConv* store) { store_ = store; }
        void setGroupCmdCallback(GROUP_CMD_RECV_FUNC callback) { groupCmdCallback_ = callback; }
        void setGroupMsgCallback(GROUP_MSG_RECV_FUNC callback) { groupMsgCallback_ = callback; }
        void setOwnChecked(bool value) {siblingDevicesScanned_ = value; }

#endif

    // Make the private functions visible for unit tests
#ifndef UNITTESTS
private:
#endif
    // do not support copy, assignment and equals
    AppInterfaceImpl (const AppInterfaceImpl& other ) = delete;
    AppInterfaceImpl& operator= ( const AppInterfaceImpl& other ) = delete;
    bool operator== ( const AppInterfaceImpl& other ) const  = delete;

    int32_t parseMsgDescriptor(const std::string& messageDescriptor, std::string* recipient, std::string* msgId, std::string* message, bool receivedMsg = false);

    /**
     * @brief Handle a group message, either a normal or a command message.
     *
     * The normal receiver function already decrypted the message, attribute, and attachment data.
     */
    int32_t processGroupMessage(int32_t msgType, const std::string &msgDescriptor,
                                const std::string &attachmentDescr, std::string *attributesDescr);

    int32_t processReceivedChangeSet(const GroupChangeSet &changeSet, const std::string &groupId, const std::string &sender,
                                     const std::string &deviceId, bool hasGroup, struct timeval& stamp, GroupChangeSet *ackRmSet);

    /**
     *
     * @brief Process a group command message.
     *
     * The @c processGroupMessage function calls this function after it checked
     * the message type.
     */
    int32_t processGroupCommand(const std::string &msgDescriptor, std::string *commandIn);

    int32_t checkAndProcessChangeSet(const std::string &msgDescriptor, std::string *messageAttributes);

    /**
     * @brief Leave a group.
     *
     * The receiver of the command removes the member from the group. If the receiver is a
     * sibling device, i.e. has the same member id, then it removes all group member data
     * and then the group data. The function only removes/clears group related data, it
     * does not remove/clear the normal ratchet data of the removed group members.
     *
     * @param groupId The group to leave.
     * @param userId Which user leaves
     * @return SUCCESS if the message list was processed without error.
     */
    int32_t processLeaveGroup(const std::string &groupId, const std::string &userId);

    /**
     * @brief Prepare the change set before sending.
     *
     * The function creates a unique update id, blocks update processing, prepares the
     * change set, updates the group and member database.
     *
     * @param groupId The group id of the change set
     * @return SUCCESS if processing was successful, an error code otherwise
     */
    int32_t prepareChangeSetSend(const std::string &groupId);

    /**
     * @brief Create the device specific change set.
     *
     * Each device may have its own change set, depending on ACK state.
     *
     * @param groupId The group id
     * @param deviceId The device id
     * @param attributes The attribute string
     * @param newAttributes The upadted attribute string which contains the change set
     * @return SUCCESS or an error code
     */
    int32_t createChangeSetDevice(const std::string &groupId, const std::string &deviceId, const std::string &attributes, std::string *newAttributes);


    /**
     * @brief All messages containing a change set were queued for sending.
     *
     * The function removes old change sets and enables update processing.
     *
     * @param groupId The group id of the processed change set
     */
    void groupUpdateSendDone(const std::string& groupId);

    /**
     * @brief Helper function add a message info structure to the run-Q
     *
     * @param msgInfo The message information structure of the message to send
     */
    void queuePreparedMessage(std::unique_ptr<CmdQueueInfo> msgInfo);


    std::unique_ptr<std::list<std::unique_ptr<PreparedMessageData> > >
    prepareMessageInternal(const std::string& messageDescriptor,
                           const std::string& attachmentDescriptor,
                           const std::string& messageAttributes,
                           bool toSibling, uint32_t messageType, int32_t* result,
                           const std::string& grpRecipient = Empty,
                           const std::string &groupId = Empty);

    /**
     * @brief Send a message to a user who has a valid ratchet conversation.
     *
     * If the caller provides a valid ratchet conversation the function use this conversation,
     * otherwise it looks up a conversation using data from the message information structure.
     * The function then encrypts the message data and supplementary data and hands over the
     * encrypted message data to the transport send function.
     *
     * This function runs in the run-Q thread only.
     *
     * @param sendInfo The message information structure of the message to send
     * @param zinaConversation an optional valid ratchet conversation
     * @return An error code in case of a failure, @c SUCCESS otherwise
     */
    int32_t sendMessageExisting(const CmdQueueInfo &sendInfo, std::unique_ptr<ZinaConversation> zinaConversation = nullptr);

    /**
     * @brief Send a message to a use who does not have a valid ratchet conversation.
     *
     * The function contacts the server to get a pre-key bundle for the user's device, prepares
     * a ratchet conversation for it, stores it and then call the function of an existing user
     * to further process the message.
     *
     * This function runs in the run-Q thread only.
     *
     * @param sendInfo The message information structure of the message to send
     * @return An error code in case of a failure, @c SUCCESS otherwise
     */
    int32_t sendMessageNewUser(const CmdQueueInfo &sendInfo);

    /**
     * @brief Add a List of message info structure to the run queue.
     *
     * The function checks if the run-Q thread is active and starts it if not. It
     * then appends the list entries to the end of the run-Q.
     *
     * @param messagesToProcess The list of message info structures
     */
    void addMsgInfosToRunQueue(std::list<std::unique_ptr<CmdQueueInfo> >& messagesToProcess);

    /**
     * @brief Setup a retry command message info structure and add it to the run-Q.
     *
     * The application should call this after a fresh start to check and retry messages
     * stored in the persitent database queues.
     */
    void insertRetryCommand();

    /**
     * @brief Check is run-Q thread is actif and start it if not.
     */
    void checkStartRunThread();

    /**
     * @brief The run-Q thread function.
     *
     * @param obj The AppInterface object used by this thread function
     */
    static void commandQueueHandler(AppInterfaceImpl *obj);

    /**
     * @brief Decrypt received message.
     *
     * Gets received messages and decrypts them. If decryption was successful it
     * stores the decrypted message in a temporary message in in the database and
     * saves ratchet state data.
     *
     * It creates a message informatio structure and call the @c processMessagePlain
     * function to handle the plain message data.
     *
     * In case of decryption failures or database access errors the function creates
     * a JSON formatted state report and hands it to the application via the message
     * state report callback.
     *
     * This function runs in the run-Q thread only.
     *
     * @param msgInfo The received message information structure
     */
    void processMessageRaw(const CmdQueueInfo &msgInfo);

    /**
     * @brief Decrypt received message.
     *
     * Gets decrypted messages, performs the callback to the application (caller) and
     * removes the temporarily stored plain message if the callback returns without error.
     *
     * In case the application's callback function returns with an error code the function
     * creates a JSON formatted state report and hands it to the application via the message
     * state report callback.
     *
     * This function runs in the run-Q thread only.
     *
     * @param msgInfo The received message information structure
     */
    void processMessagePlain(const CmdQueueInfo &msgInfo);

    /**
     * @brief Send delivery receipt after successful decryption of the message
     *
     * This function runs in the run-Q thread only.
     *
     * @param plainMsgInfo The message command data
     */
    void sendDeliveryReceipt(const CmdQueueInfo &plainMsgInfo);

    /**
     * @brief Get sibling devices from provisioning server and add missing devices to id key list.
     *
     * @param idKeys List of already known sibling device id keys,
     * @return The list of new, yet unkonwn sibling devices, may be empty.
     */
    std::shared_ptr<std::list<std::string> > addSiblingDevices(std::shared_ptr<std::list<std::string> > idKeys);

    /**
     * @brief Helper function which creates a JSON formatted message descriptor.
     *
     * @param recipient Recipient of the message
     * @param msgId The message's identifier
     * @param msg The message, optional.
     * @return JSON formatted string
     */
    std::string createMessageDescriptor(const std::string& recipient, const std::string& msgId, const std::string& msg = Empty);

#ifdef SC_ENABLE_DR_SEND
    /**
     * @brief Check data retentions flags and prepare for data retention.
     *
     * Check the data retention flags of the local party (the sender) and the remote
     * party (the receiver) to decide if it's OK to retain some data. If it's not OK
     * to retain data the function returns an error code.
     *
     * If it's OK to retain some data then prepare/enhance the message attributes to
     * contain the defined flags to info the remote party.
     *
     * @param recipient The UID of the remote party
     * @param msgAttributes The original message attributes
     * @param newMsgAttributes Contains the enhanced/modified message attrinutes if it's OK
     *        to retain data, not changed if the function returns an error code.
     * @param It data rention is OK then holds local retention flags: 1 - retain data, 2 - retain meta data
     * @return OK if data retention is OK, an error code otherwise
     */
    int32_t checkDataRetentionSend(const std::string &recipient, const std::string &msgAttributes,
                                   shared_ptr<string> newMsgAttributes, uint8_t *localRetentionFlags);
#endif //SC_ENABLE_DR_SEND

#ifdef SC_ENABLE_DR_RECV
    /**
     * @brief Check and perform data retention, send delivery receipt or reject message.
     *
     * The function uses the various data retention flags and the the DR flags in the
     * message attributes to decide if data retention for this message is OK or not.
     *
     * If it's OK to retain the data then perform data retention, prepare and send a
     * delivery receipt and return @c true to the caller. The function also returns
     * @c true if data retention is not enabled at all.
     *
     * If it's not OK to retain the data the functions creates and sends an error command
     * message to the sender and returns @c false.
     *
     * @param plainMsgInfo Data of the received message
     * @return @c true or @c false in case the message was rejected due to DR policy
     */
    bool dataRetentionReceive(shared_ptr<CmdQueueInfo> plainMsgInfo);
#endif // SC_ENABLE_DR_RECV

    /**
     * @brief Check if the message is a command message
     *
     * @param msgType the message type
     * @param attributes JSON formatted string that contains the message attributes
     * @return @c true if it's a command message, @c false otherwise
     */
    bool isCommand(int32_t msgType, const std::string& attributes);

    /**
     * @brief Check if the message is a command message
     *
     * @param plainMsgInfo information about the message
     * @return @c true if it's a command message
     */
    bool isCommand(const CmdQueueInfo& plainMsgInfo);

    /**
     * @brief Send an error response to the sender of the message.
     *
     * The function sets some attributes to provide flexible handling. The local client
     * does not retain (stores) this command message and allows the receiver to retain it.
     *
     * @param error The error code
     * @param sender The message sender's uid
     * @param msgId The id of the message in error
     */
    void sendErrorCommand(const std::string& error, const std::string& sender, const std::string& msgId);

    /**
     * @brief Setup data and call data retention functions.
     *
     * Based on retainInfo the function either store the message meta data and/ot the
     * message plain text data.
     *
     * @param retainInfo Flags that control which data to store
     * @param sendInfo The message information
     * @return SUCCESS or an error code
     */
//    int32_t doSendDataRetention(uint32_t retainInfo, shared_ptr<CmdQueueInfo> sendInfo);

    void checkRemoteIdKeyCommand(const CmdQueueInfo &command);

    void setIdKeyVerifiedCommand(const CmdQueueInfo &command);

    void reKeyDeviceCommand(const CmdQueueInfo &command);

    void rescanUserDevicesCommand(const CmdQueueInfo &command);

    int32_t deleteGroupAndMembers(const std::string& groupId);

    int32_t insertNewGroup(const std::string &groupId, const GroupChangeSet &changeSet, const struct timeval& stamp, std::string *callbackCmd);

    /**
     * @brief Send a message to a specific device of a group member.
     *
     * ZINA uses this function to prepare and send a change set to a group member's device.
     * Thus ZINA can send ACK or other change sets to the sender' device. This function does not
     * support sending of an attachment descriptor.
     *
     * The function adds the group id to the attributes, creates a send message command and
     * queues it for normal send message processing.
     *
     * This function does not prepare or add a change set to the attribute data. Often the
     * attribute has a change set already, for example when sending ACKs
     *
     * @param groupId The group id to get the change set
     * @param userId The group member's id
     * @param deviceId The device of of the group member
     * @param attributes The message attributes, may be empty
     * @param msg the message to send, maybe empty
     * @return @c SUCCESS or an error code (<0)
     */
    int32_t sendGroupMessageToSingleUserDeviceNoCS(const std::string &groupId, const std::string &userId, const std::string &deviceId,
                                                   const std::string &attributes, const std::string &msg, int32_t msgType);

    void makeBinaryDeviceId(const std::string &deviceId, std::string *binaryId);

    bool removeFromPendingChangeSets(const std::string &key);

    int32_t processAcks(const GroupChangeSet &changeSet, const std::string &groupId, const std::string &deviceId);

    int32_t processUpdateName(const GroupUpdateSetName &changeSet, const std::string &groupId, const struct timeval& stamp, GroupChangeSet *ackSet);

    int32_t processUpdateAvatar(const GroupUpdateSetAvatar &changeSet, const std::string &groupId, const struct timeval& stamp, GroupChangeSet *ackSet);

    int32_t processUpdateBurn(const GroupUpdateSetBurn &changeSet, const std::string &groupId, const struct timeval& stamp, GroupChangeSet *ackSet);

    int32_t processBurnMessage(const GroupBurnMessage &changeSet, const std::string &groupId, const struct timeval& stamp, GroupChangeSet *ackSet);

    int32_t processUpdateMembers(const GroupChangeSet &changeSet, const std::string &groupId, const struct timeval& stamp, GroupChangeSet *ackSet);

    /**
     * @brief Helper function to create the JSON formatted supplementary message data.
     *
     * @param attachmentDesc The attachment descriptor of the message, may be empty
     * @param messageAttrib The message attributes, may be empty
     * @return JSON formatted string
     */
    static std::string createSupplementString(const std::string& attachmentDesc, const std::string& messageAttrib);

    /**
     * @brief Helper function to create a JSON formatted error report if sending fails.
     *
     * @param info The message's information structure
     * @param errorCode The error code, failure reason
     * @return JSON formatted string
     */
    static std::string createSendErrorJson(const CmdQueueInfo& info, int32_t errorCode);

    /**
     * @brief Helper function to extract transport ids from prepage message data.
     *
     * The function extracts transport ids from a list of prepared message data and stores
     * them in a unsigned int64 vector, ready for the @c doSendMessages function.
     *
     * @param data List of prepared message data
     * @return Vector with the transport ids
     */
    static std::shared_ptr<std::vector<uint64_t> > extractTransportIds(std::list<std::unique_ptr<PreparedMessageData> >* data);

    void queueMessageToSingleUserDevice(const std::string &userId, const std::string &msgId, const std::string &deviceId,
                                        const std::string &deviceName, const std::string &attributes, const std::string &attachement,
                                        const std::string &msg, int32_t msgType, bool newDevice, SendCallbackAction sendCallbackAction);


    /**
     * @brief Callback function if some action necessary after sending a message.
     *
     * @param sendCallbackAction Which action to perform
     */
    void sendActionCallback(SendCallbackAction sendCallbackAction);

    static std::string generateMsgIdTime() {
        uuid_t uuid = {0};
        uuid_string_t uuidString = {0};

        uuid_generate_time(uuid);
        uuid_unparse(uuid, uuidString);
        return std::string(uuidString);
    }

    char* tempBuffer_;
    size_t tempBufferSize_;
    std::string ownUser_;
    std::string authorization_;
    std::string scClientDevId_;

    int32_t errorCode_;
    std::string errorInfo_;
    SQLiteStoreConv* store_;
    Transport* transport_;
    int32_t flags_;
    // If we send to sibling devices and siblingDevicesScanned_ then check for possible new
    // sibling devices that may have registered while this client was offline.
    // If another sibling device registers for this account it does the same and sends
    // a sync message, the client receives it and we know the new device.
    bool siblingDevicesScanned_;

    // Data retention flags valid for the local user
    bool drLrmm_,       //!< local client retains message metadata
            drLrmp_,    //!< local client retains message plaintext
            drLrap_,    //!< local client retains attachment plaintext
            drBldr_,    //!< Block local data retention
            drBlmr_,    //!< Block local metadata retention
            drBrdr_,    //!< Block remote data retention
            drBrmr_;    //!< Block remote metadata retention
    };
} // namespace

/**
 * @}
 */

#endif // APPINTERFACEIMPL_H
