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
#ifndef APPINTERFACE_H
#define APPINTERFACE_H

/**
 * @file AppInterface.h
 * @brief Interface to the application
 * @ingroup Zina
 * @{
 */

#include <string>
#include <vector>
#include <list>
#include <memory>

#include "../interfaceTransport/Transport.h"


typedef int32_t (*RECV_FUNC)(const std::string& messageDescriptor, const std::string& attachmentDescriptor, const std::string &messageAttributes);
typedef void (*STATE_FUNC)(int64_t messageIdentifier, int32_t errorCode, const std::string& stateInformation);
typedef void (*NOTIFY_FUNC)(int32_t notifyActionCode, const std::string& userId, const std::string& actionInformation);

typedef int32_t (*GROUP_CMD_RECV_FUNC)(const std::string& commandMessage);
typedef int32_t (*GROUP_MSG_RECV_FUNC)(const std::string& messageDescriptor, const std::string& attachmentDescriptor, const std::string& messageAttributes);
typedef void (*GROUP_STATE_FUNC)(int32_t errorCode, const std::string& stateInformation);

/**
 * @brief Groups classes and data of the ZINA implementation.
 */
namespace zina {

/**
 * @brief Structure that contains return data of @c prepareMessage functions.
 */
typedef struct PreparedMessageData_ {
    uint64_t transportId;           //!<  The transport id of the prepared message
    std::string receiverInfo;            //!<  Some details about the receiver's device of this message
} PreparedMessageData;

class AppInterface
{
public:
    static const int DEVICE_SCAN = 1;

    AppInterface() : receiveCallback_(NULL), stateReportCallback_(NULL), notifyCallback_(NULL), groupMsgCallback_(NULL),
    groupCmdCallback_(NULL), groupStateReportCallback_(NULL) {}

    AppInterface(RECV_FUNC receiveCallback, STATE_FUNC stateReportCallback, NOTIFY_FUNC notifyCallback,
                 GROUP_MSG_RECV_FUNC groupMsgCallback, GROUP_CMD_RECV_FUNC groupCmdCallback,  GROUP_STATE_FUNC groupStateCallback) :
            receiveCallback_(receiveCallback), stateReportCallback_(stateReportCallback), notifyCallback_(notifyCallback),
            groupMsgCallback_(groupMsgCallback), groupCmdCallback_(groupCmdCallback), groupStateReportCallback_(groupStateCallback)
    {}

    virtual ~AppInterface() {}

    /**
     * @brief Set the transport class.
     *
     * @param transport The implementation of the transport interface to send data.
     */
    virtual void setTransport(Transport* transport) = 0;

    /**
     * @brief Get the current Transport.
     * 
     * @return Pointer to the current Transport.
     */
    virtual Transport* getTransport() = 0;

    /**
     * @brief Prepare a user-to-user message for sending.
     *
     * The functions prepares a message and queues it for sending to the receiver' devices.
     * The function only prepares the message(s) but does not send them. To actually send the
     * the messages to the device(s) the application needs to call the @c sendPreparedMessage()
     * function.
     *
     * This function may trigger network actions, thus it must not run on the UI thread.
     *
     * The function creates a list of PreparedMessage data structures that contain information
     * for each prepared message:
     * <ul>
     * <li> a 64 bit integer which is the transport id of the prepared message. Libzina uses this
     *      transport id to identify a message in transit (during send) to the server and to report
     *      a message status to the application. The application must not modify this data and may
     *      use it to setup a queue to monitor the message status reports.</li>
     * <li> A string that contains recipient information. The data and format is the same as returned
     *      by @c AppInterfaceImpl::getIdentityKeys
     * </ul>
     *
     * @deprecated use unique_ptr<list<unique_ptr<PreparedMessageData> > > prepareMessageNormal(const std::string&, const std::string&, const std::string&, bool, int32_t*)
     *
     * @param messageDescriptor      the JSON formatted message descriptor, required
     * @param attachmentDescriptor   Optional, a string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @param result    Pointer to result of the operation, if not @c SUCCESS then the returned list is empty
     * @param normalMsg If true then this is a normal message, if false it's a command message
     * @return A list of prepared message information, or empty on failure
     */
    virtual std::shared_ptr<std::list<std::shared_ptr<PreparedMessageData> > >
    prepareMessage(const std::string& messageDescriptor,
                   const std::string& attachmentDescriptor,
                   const std::string& messageAttributes,
                   bool normalMsg, int32_t* result) = 0;

    /**
     * @brief Prepare a user-to-user message for sending to its sibling devices.
     *
     * This function performs the same actions as the @c prepareMessage function, it only sends
     * the message to the user's sibling devices if such devices are available.
     *
     * @deprecated use unique_ptr<list<unique_ptr<PreparedMessageData> > > prepareMessageSiblings(const std::string&, const std::string&, const std::string&, bool, int32_t*)
     *
     * @param messageDescriptor     the JSON formatted message descriptor, required
     *
     * @param attachmentDescriptor  Optional, a string that contains an attachment descriptor. An empty string
     *                              shows that not attachment descriptor is available.
     * @param messageAttributes     Optional, a JSON formatted string that contains message attributes. An empty
     *                              string shows that not attributes are available.
     * @param result    Pointer to result of the operation, if not @c SUCCESS then the returned list is empty
     * @param normalMsg If true then this is a normal message, if false it's a command message. Messages to
     *                  siblings are usually commands
     * @return A list of prepared message information, or empty on failure
     */
    virtual std::shared_ptr<std::list<std::shared_ptr<PreparedMessageData> > >
    prepareMessageToSiblings(const std::string &messageDescriptor,
                             const std::string &attachmentDescriptor,
                             const std::string &messageAttributes,
                             bool normalMsg, int32_t *result) = 0;

    /**
     * @brief Prepare a user-to-user message for sending.
     *
     * The functions prepares a message and queues it for sending to the receiver' devices.
     * The function only prpares the message(s) but does not send them. To actually send the
     * the messages to the device(s) the application needs to call the @c sendPreparedMessage()
     * function.
     *
     * This function may trigger network actions, thus it must not run on the UI thread.
     *
     * The function creates a list of PreparedMessage data structures that contain information
     * for each prepared message:
     * <ul>
     * <li> a 64 bit integer which is the transport id of the prepared message. Libzina uses this
     *      transport id to identify a message in transit (during send) to the server and to report
     *      a message status to the application. The application must not modify this data and may
     *      use it to setup a queue to monitor the message status reports.</li>
     * <li> A string that contains recipient information. The data and format is the same as returned
     *      by @c AppInterfaceImpl::getIdentityKeys
     * </ul>
     *
     * @param messageDescriptor      the JSON formatted message descriptor, required
     * @param attachmentDescriptor   Optional, a string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @param result    Pointer to result of the operation, if not @c SUCCESS then the returned list is empty
     * @param normalMsg If true then this is a normal message, if false it's a command message
     * @return A list of prepared message information, or empty on failure
     */
    virtual std::unique_ptr<std::list<std::unique_ptr<PreparedMessageData> > >
    prepareMessageNormal(const std::string& messageDescriptor,
                         const std::string& attachmentDescriptor,
                         const std::string& messageAttributes,
                         bool normalMsg, int32_t* result) = 0;

    /**
     * @brief Prepare a user-to-user message for sending to its sibling devices.
     *
     * This function performs the same actions as the @c prepareMessage function, it only sends
     * the message to the user's sibling devices if such devices are available.
     *
     * @param messageDescriptor     the JSON formatted message descriptor, required

     * @param attachmentDescriptor  Optional, a string that contains an attachment descriptor. An empty string
     *                              shows that not attachment descriptor is available.
     * @param messageAttributes     Optional, a JSON formatted string that contains message attributes. An empty
     *                              string shows that not attributes are available.
     * @param result    Pointer to result of the operation, if not @c SUCCESS then the returned list is empty
     * @param normalMsg If true then this is a normal message, if false it's a command message. Messages to
     *                  siblings are usually commands
     * @return A list of prepared message information, or empty on failure
     */
    virtual std::unique_ptr<std::list<std::unique_ptr<PreparedMessageData> > >
    prepareMessageSiblings(const std::string &messageDescriptor,
                           const std::string &attachmentDescriptor,
                           const std::string &messageAttributes,
                           bool normalMsg, int32_t *result) = 0;

    /**
     * @brief Encrypt the prepared messages and send them to the receiver.
     *
     * Queues the prepared message for encryption and sending to the receiver's devices.
     *
     * @param transportIds An array of transport id that identify the messages to encrypt and send.
     * @return Number of queued messages, a negative value on failure
     */
    virtual int32_t doSendMessages(std::shared_ptr<std::vector<uint64_t> > transportIds) = 0;

    /**
     * @brief Remove prepared messages from its queue.
     *
     * This function removes prepared messages from the prepared message queue. The function
     * does not remove prepared messages that were already sent by @c doSendMessages.
     *
     * @param transportIds An array of transport id that identify the messages to remove.
     * @return Number of removed messages, a negative value on failure
     */
    virtual int32_t removePreparedMessages(std::shared_ptr<std::vector<uint64_t> > transportIds) = 0;

    /**
     * @brief Receive a Message from transport
     *
     * The function creates stores the received encrypted message raw data in a database table,
     * creates a message information structure for this message and puts it into the run-Q and
     * returns immediately.
     *
     * The network transport uses this function to hand over received data. Applications should not
     * use this function.
     *
     * @param messageEnvelope The proto-buffer message envelope, encoded as a base64 string
     * @param uid   The SIP receiver callback sets this to the sender's UID if available, an
     *              empty string if not available
     * @param alias The SIP receiver callback sets this to the sender's primary alias name
     *              (display name) if available, an empty string if not available
     *
     * @return Either success or an error code
     */
    virtual int32_t receiveMessage(const std::string& messageEnvelope, const std::string& uid, const std::string& displayName) = 0;

    /**
     * @brief Request names of known trusted ZINA user identities
     *
     * The ZINA library stores an identity (name) for each remote user.
     *
     * @return JSON formatted information about the known users. It returns an empty 
     *         JSON array if no users known. It returns NULL in case the request failed.
     *         Language bindings use appropriate return types.
     */
    virtual std::string* getKnownUsers() = 0;

    /**
     * @brief Get name of own user.
     *
     * The Axolotl library stores an identity (name) for each remote user.
     *
     * @return Reference to internal own user
     */
    virtual const std::string& getOwnUser() const = 0;

    /**
     * @brief Get public part of own identity key.
     * 
     * The returned strings is the B64 encoded data of the own public identity key, optinally
     * followed by a colon and the device name. Thus the returned string:
     *
     *   @c identityKey:deviceName:deviceId:zrtpStatus
     *
     * @return formatted string, device name part may be empty if no device name was defined.
     */
    virtual std::string getOwnIdentityKey() = 0;

    /**
     * @brief Get own device identifier.
     * @return Reference to own device identifier string
     */
    virtual const std::string& getOwnDeviceId() const = 0;

    /**
     * @brief Get a list of all identity keys of a user.
     * 
     * The remote partner or the own account may have more than one device. This function returns
     * the identity keys of remote user's or own sibling devices that this client knows of. The
     * client sends messages only to these known device of the remote user or to the known sibling
     * devices.
     * 
     * The returned strings in the list contain the B64 encoded data of the public identity keys
     * of the known devices, followed by a colon and the device name, followed by a colon and the
     * the device id, followed by a colon and the ZRTP verify state. Format of the returned string:
     *
     *   @c identityKey:deviceName:deviceId:verifyState
     *
     * The device name part may be empty if no device name was defined.
     *
     * @param user the name of the user
     * @return list of identity keys. An empty list if no identity keys are available for that user.
     */
    virtual std::shared_ptr<std::list<std::string> > getIdentityKeys(std::string& user) = 0;

    /**
     * @brief Register device
     *
     * Register this device with the server. The registration requires a device id that's unique
     * for the user's account on the server. The user should have a valid account on the server.
     * 
     * In the Silent Circle use case the user name was provided during account creation, the client computes a
     * unique device id and registers this with the server during the first generic device registration.
     * 
     * @param result To store the result data of the server, usually in case of an error only
     * @return the server return code, usually a HTTP code, e.g. 200 for OK
     */
    virtual int32_t registerZinaDevice(std::string* result) = 0;

     /**
     * @brief Generate and register a set of new pre-keys.
     * 
     * @return Result of the register new pre-key request, usually a HTTP code (200, 404, etc)
     */
    virtual int32_t newPreKeys(int32_t number) = 0;

    /**
     * @brief Get number of pre-keys available on the server.
     * 
     * Checks if the server has pre-keys for this account/device id and return how many keys are
     * available.
     * 
     * @return number of available pre-keys or -1 if request to server failed.
     */
    virtual int32_t getNumPreKeys() const = 0;

    /**
     * @brief Add one message info data structure to the run queue.
     *
     * The function checks if the run-Q thread is active and starts it if not. It
     * then appends the data structure to the end of the run-Q.
     *
     * @param messageToProcess message info structure
     */
    virtual void addMsgInfoToRunQueue(std::unique_ptr<CmdQueueInfo> messageToProcess) = 0;

    // *************************************************************
    // Device handling functions
    // *************************************************************

    /**
     * @brief Rescan user devices.
     *
     * Checks if a user has registered a new Axolotl device and adds it to our conversations.
     * Ths function also performs housekeeping in case the user deleted a device and remove it
     * from our database.
     *
     * @param userName The user account to check.
     *
     */
    virtual void rescanUserDevices(const std::string& userName) = 0;

    /**
     * @brief Re-syncs (re-keying) all devices of a user account.
     *
     * This function performs a re-keying of all devices of a user account. It does it
     * without changing the long-term identity key. The re-keying just uses a new pre-key
     * of the user's account and resets and re-initializes the ratchet contexts.
     *
     * @param userName The user account to sync.
     */
    virtual void reKeyAllDevices(const std::string& userName) = 0;

    /**
     * @brief Re-key a device's conversation data (ratchet context).
     *
     * Re-keys a conversation if it still exists on the server.
     * The function clears the key material and the conversation status. To create
     * new key material and status it's necessary to fetch a new pre-key of the
     * user and setup a new set of keys and status data.
     *
     * @param user the name of the user
     * @param deviceId the user's device
     *
     */
    virtual void reKeyDevice(const std::string &userName, const std::string &deviceId) = 0;

    /**
     * @brief Set Identity key changed flag.
     *
     * If the user (manually) verified the long-term identity key of the partner then the
     * application can call this function to set the flag,
     *
     * @param user the name of the user
     * @param deviceId the user's device
     * @param flag Set the changed id key flag to @c true or @c false
     *
     */
    virtual void setIdKeyVerified(const std::string& userName, const std::string& deviceId, bool flag) = 0;


    // *************************************************************
    // Group chat functions
    // *************************************************************

    /**
     * @brief Create a new group and assign ownership to the creator
     *
     * The function creates a new group and assigns the group's ownership to the creator. This is
     * different to the @c createGroup(string& groupName, string& groupDescription, string& owner)
     * function which creates a new group for an invited member.
     *
     * This function automatically adds this user (myself) to the group.
     *
     * The function sets the group's size to @c Constants::DEFAULT_GROUP_SIZE.
     *
     * @param groupName The name of the new group
     * @param groupDescription Group description, purpose of the group, etc
     * @return the group's UUID, if the string is empty then group creation failed, use
     *         @c AppInterfaceImpl::getErrorInfo() to get error string.
     */
    virtual std::string createNewGroup(const std::string& groupName, std::string& groupDescription) = 0;

    /**
     * @brief Modify number maximum group member.
     *
     * Only the group owner can modify the number of maximum members.
     *
     * If the new size would be less than current active group member the function fails
     * and returns @c false.
     *
     * @param newSize New maximum group members
     * @param groupUuid The group id
     * @return @c true if new size could be set, @c false otherwise, use
     *         @c AppInterfaceImpl::getErrorInfo() to get error string.
     */
    virtual bool modifyGroupSize(const std::string& groupUuid, int32_t newSize) = 0;

    /**
     * @brief Set a group's new name.
     *
     * The function sets a new group name and synchronizes this with the other group
     * members if the user sends a message.
     *
     * @param groupUuid the group id
     * @param groupName the new group name. If this is NULL (nullptr) then the function removes the group name
     *                  update from the change set.
     * @return @c SUCCESS if new name could be set, or an error code
     */
    virtual int32_t setGroupName(const std::string& groupUuid, const std::string* groupName) = 0;

    /**
     * @brief Set a group's new burn time and mode.
     *
     * The function sets a new group bur time and mode and synchronizes this with the other group
     * members if the user sends a message.
     *
     * Currently group burn time handling supports one mode only and it has the value 1.
     *
     * `FROM_SEND_RETROACTIVE = 1:` a mode in which the time a message burns is based on the time
     * it was sent and the current relative burn time on the group (not the burn time in effect when
     * the message was sent or received).
     *
     * @param groupUuid the group id
     * @param burnTime the new group's burn time in seconds
     * @param mode
     * @return @c SUCCESS if new name could be set, or an error code
     */
    virtual int32_t setGroupBurnTime(const std::string& groupUuid, uint64_t burnTime, int32_t mode) = 0;

    /**
     * @brief Set a group's new avatar data.
     *
     * The function sets a new group avatar data and synchronizes this with the other group
     * members if the user sends a message.
     *
     * @param groupUuid the group id
     * @param avatar the new avatar data. If this is NULL (nullptr) then the function removes the
     *               avatar info update from the change set.
     * @return @c SUCCESS if new name could be set, or an error code
     */
    virtual int32_t setGroupAvatar(const std::string& groupUuid, const std::string* avatar) = 0;

    /**
     * @brief Add a user to a group.
     *
     * This function adds a user id (name) to the group's add member change set. If the same name
     * is also present on the group's current remove name change set then this function removes
     * the name from the remove change set
     *
     * @param groupUuid Invite for this group
     * @param userId The invited user's unique id
     * @return @c SUCCESS or error code (<0)
     */
    virtual int32_t addUser(const std::string& groupUuid, const std::string& userId) = 0;

    /**
     * @brief Remove a user's name from the add member update change set.
     *
     * Just remove the user's uid from the add member update change set, no other
     * actions or side effects, thus it is the opposite of `addUser`.
     *
     * @param groupUuid The group id
     * @param userId The user id to remove from the change set
     * @return @c SUCCESS if function could send invitation, error code (<0) otherwise
     */
    virtual int32_t removeUserFromAddUpdate(const std::string& groupUuid, const std::string& userId) = 0;

    /**
     * @brief Cancel group's current change set.
     *
     * Clears the group's current change set.
     *
     * @param groupId Cancel current change set for this group
     * @return @c SUCCESS if function could send invitation, error code (<0) otherwise
     */
    virtual int32_t cancelGroupChangeSet(const std::string& groupId) = 0;

    /**
     * @brief Apply group's current change set.
     *
     * This function applies the current group change set which may include the updates to add
     * a new member, set a group avatar, etc. The function just sends an empty message and this
     * checks for change sets
     *
     * @param groupId Apply current change set for this group
     * @return @c SUCCESS if function could send invitation, error code (<0) otherwise
     */
    virtual int32_t applyGroupChangeSet(const std::string& groupId) = 0;

    /**
     * @brief Send a message to a group with an optional attachment and attributes.
     *
     * Takes JSON formatted message descriptor and send the message. The function accepts
     * an optional JSON formatted attachment descriptor and sends the attachment data to the
     * recipient together with the message.
     *
     * This is a blocking call and the function returns after the transport layer accepted the
     * message and returns.
     *
     * The @c sendMessage function does not interpret or re-format the attachment descriptor. It takes
     * the string, encrypts it with the same key as the message data and puts it into the message
     * bundle. The same is true for the message attributes.
     *
     * The function either amends existing message attributes with the JSON labels 'hash' and 'grpId'
     * or creates a new set of message attributes that contains these two labels and their values.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return @c OK if function could send the message, error code (<0) otherwise
     */
    virtual int32_t sendGroupMessage(const std::string& messageDescriptor, const std::string& attachmentDescriptor, const std::string& messageAttributes) = 0;

    /**
     * @brief Send a group message to a single group member with an optional attachment and attributes.
     *
     * This function works in the same way as the normal @c sendGroupMessage but sends the message to
     * one member only.  If the caller specifies a @c deviceId then the function sends this message to
     * the member's device.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @param recipient              User id of the group member
     * @param device id             If specified then the function sends the message to the selected device
     *                              of the group member. Maybe empty.
     * @return @c OK if function could send the message, error code (<0) otherwise
     */
    virtual int32_t sendGroupMessageToMember(const std::string &messageDescriptor, const std::string &attachmentDescriptor,
                                             const std::string &messageAttributes, const std::string &recipient,
                                             const std::string &deviceId) = 0;
    /**
     * @brief Send a group command to a single group member.
     *
     * This is a blocking call and the function returns after the transport layer accepted the
     * message and returns.
     *
     * The function creates an empty message either using a provided message-id or, if the parameter
     * is {@code null}, it generates a message id.
     *
     * The {@code sendGroupCommandToMember} function does not interpret or re-format the command
     * string. It takes the string, encrypts it with the same key as the message data and puts it
     * into the message bundle.
     *
     *
     * @param groupId               Group of the member
     * @param member                The recipient (user id)
     * @param msgId                 Optional message id
     * @param command               A string that contains a command.
     * @return {@code OK} if function could send the command, error code (<0) otherwise
     */
    virtual int32_t sendGroupCommandToMember(const std::string& groupId, const std::string &member, const std::string &msgId, const std::string &command) = 0;

    /**
     * @brief Leave a group.
     *
     * The application (UI part) calls this function to remove this member (myself) from the
     * group. This function sends the leave group update immediately.
     *
     * @param groupId The group to leave
     * @return @c SUCCESS if 'leave group' processing was OK, error code (<0) otherwise
     */
    virtual int32_t leaveGroup(const std::string& groupId) = 0;

    /**
     * @brief Remove another member (not myself) from a group.
     *
     * The application (UI part) calls this function to remove a member from the
     * group.
     *
     * This function adds a user id (name) to the group's remove member change set. If the same name
     * is also present on the group's current add name change set then this function removes
     * the name from the add change set, thus is opposite to invite/add member
     *
     * @param groupId The group id
     * @param userId The user id of the user to remove
     * @param allowOwnUser If `true* then it's possible to remove the own user. Only ZINA uses this internally
     * @return @c SUCCESS if 'remove from group' processing was OK, error code (<0) otherwise
     */
    virtual int32_t removeUser(const std::string& groupId, const std::string& userId, bool allowOwnUser = false) = 0;

    /**
     * @brief Remove a user's name from the remove member update change set.
     *
     * Just remove the user's uid from the remove (remove group) member update change set, no other
     * actions or side effects, thus this function is the opposite of `removeUser`
     *
     * @param groupUuid The group id
     * @param userId The user id to remove from the change set
     * @return @c SUCCESS if function could send invitation, error code (<0) otherwise
     */
    virtual int32_t removeUserFromRemoveUpdate(const std::string& groupUuid, const std::string& userId) = 0;

    /**
     * @brief Manually burn one or more group message(s).
     *
     * @param groupId The id of the group
     * @param messageIds The message ids of the removed message
     * @return {@code OK} or an error code
     */
    virtual int32_t burnGroupMessage(const std::string& groupId, const std::vector<std::string>& messageIds) = 0;

    // *************************************************************
    // Callback functions to UI part
    // *************************************************************

    /**
     * @brief Callback to UI to receive a Message from transport 
     *
     * Creates a JSON formatted message descriptor of the received message and forwards it to the UI
     * code via a callback functions.
     *
     * The implementation classes for the different language bindings need to perform the necessary
     * setup to be able to call into the UI code. The function and thus also the called function in
     * the UI runs in an own thread. UI frameworks may not directly call UI related functions inside
     * their callback function. Some frameworks provide special functions to run code on the UI 
     * thread even if the current functions runs on another thread.
     *
     * In any case the UI code shall not block processing of this callback function and shall return
     * from the callback function as soon as possible.
     *
     * The @c receiveMessage function does not interpret or re-format the attachment descriptor. It takes
     * the data from the received message bundle, decrypts it with the same key as the message data
     * and forwards the resulting string to the UI code. The UI code can then use this data as input to
     * the attachment handling.
     *
     * The functions creates the following JSON data message descriptor:
     *@verbatim
      {
          "version":    <int32_t>,            # Version of the JSON known users structure,
                                              # 1 for the first implementation

          "sender":     <string>,             # sender name (UID in newer versions)
          "alias":      <string>              # sender alias name (human readable)
          "scClientDevId": <string>           # sender's device id (instance dev id)
          "msgId":      <string>,             # the message UUID
          "message"     <string>              # decrypted message data
      }
     @endverbatim
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor   A string that contains an attachment descriptor. An empty string
     *                               shows that no attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return Either success of an error code (to be defined)
     */
    RECV_FUNC receiveCallback_;

    /**
     * @brief Callback to UI for a Message state change report
     *
     * The Axolotl library uses this callback function to report message state changes to the UI.
     * The library reports message state changes for sending and it also reports if it
     * received a message but could not process it, for example decryption failed.
     *
     * @param messageIdentifier  the unique message identifier. If this identifier is 0 then this 
     *                           report belongs to a received message and the library failed to 
     *                           process it.
     * @param errorCode          The error code
     * @param stateInformation   JSON formatted stat information block that contains the details about
     *                           the new state or some error information.
     */
    STATE_FUNC stateReportCallback_;

    /**
     * @brief Notify callback.
     *
     * The Axolotl library uses this callback function to report data of a SIP NOTIFY to the app.
     *
     * @param notifyActionCode   This code defines which action to perform, for example re-scan a
     *                           user's Axolotl devices
     * @param userId             The user id for which the SIP server sent the NOTIFY
     * @param actionInformation  string that contains details required for the action, currently
     *                           the device identifiers separated with a colon.
     */
    NOTIFY_FUNC notifyCallback_;

    /**
     * @brief Callback to UI to receive a normal group message.
     *
     * JSON format TBD
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor   A string that contains an attachment descriptor. An empty string
     *                               shows that no attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return Either success of an error code (to be defined)
     */
    GROUP_MSG_RECV_FUNC groupMsgCallback_;

    /**
     * @brief Callback to UI to receive a group command message.
     *
     * JSON format TBD
     *
     * @param commandMessage  A JSON formatted string that contains the command message.
     * @return Either success of an error code (to be defined)
     */
    GROUP_CMD_RECV_FUNC groupCmdCallback_;

    /**
     * @brief Callback to UI for a Group Message state change report
     *
     * The Axolotl library uses this callback function to report message state changes to the UI.
     * The library reports message state changes for sending and it also reports if it
     * received a message but could not process it, for example decryption failed.
     *
     * @param errorCode          The error code
     * @param stateInformation   JSON formatted stat information block that contains the details about
     *                           the new state or some error information.
     */
    GROUP_STATE_FUNC groupStateReportCallback_;
};
} // namespace

/**
 * @}
 */

#endif // APPINTERFACE_H
