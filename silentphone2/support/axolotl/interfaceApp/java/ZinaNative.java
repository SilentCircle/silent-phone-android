/*
Copyright 2016 Silent Circle, LLC

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

package zina;

// This file uses annotations.
//
// The directory contains a jar file that defines the annoations that are in use in Android  .
//
// To create the JNI interface file:
// - cd to the ZinaNative.java directory
// - run 'javac -cp android-support-annotations.jar -d . ZinaNative.java'
// - run 'javah zina.ZinaNative'
//
// After this you can remove the created 'zina' directory.

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.support.annotation.Nullable;

/**
 * Native functions and callbacks for ZINA library.
 *
 * The functions in this class often use JSON formatted strings to exchange data with the
 * native ZINA library functions.
 *
 * The native functions expect UTF-8 encoded strings and return UTF-8 encoded strings.
 *
 * Java uses a modified UTF-8 encoding in its String class and this encoding could
 * give problems if a Java program hands over a pure Java String to the native functions.
 * Fortunately Java can convert its internal encoding into the correct standard encoding.
 * The following Java code snippet shows how to to it:
 * <code>
 * String str = "contains some other characters öäüß á â ã";
 * byte[] utf8Encoded = str.getBytes("UTF-8");   // Encode to standard UTF-8 and store as bytes
 * <code>
 * A Java function then uses the byte array as a parameter to the native function. On callback
 * it just works the other way around. This is the reason why the interface uses {@code byte[]}
 * and not {@code String}.
 */
@SuppressWarnings({"unused", "JniMissingFunction"})
public abstract class ZinaNative { //  extends Service {  -- depends on the implementation of the real Java class

    /**
     * Some constants, mirrored from C++ files
     */
    public static final int DEVICE_SCAN = 1;        //!< Notify callback requests a device re-scan (AppInterface.h)

    /**
     * Class returned by native prepareMessage* functions.
     *
     * Created by werner on 25.08.16.
     */
    public static class PreparedMessageData {
        public long   transportId;           //!< The transport id of the prepared message
        public String receiverInfo;          //!< Some details about the receiver's device of this message. Format as returned by getIdentityKeys(...)

        public PreparedMessageData() {}

        public PreparedMessageData(final String info, long id) {
            receiverInfo = info;
            transportId = id;
        }
    }

    /**
     * Initialize the ZINA library.
     *
     * The following native functions MUST NOT be static because their native implementation
     * use the "this" object.
     *
     * An application must call this functions before it can use any other ZINA library
     * functions.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param flags Lower 4 bits define debugging level, upper bits some flags
     * @param dbName the full path of the database filename
     * @param dbPassPhrase the pass phrase to encrypt database content
     * @param userName the local username, for SC it's the name of the user's account
     * @param authorization some authorization code, for SC it's the API key of this device
     * @param scClientDevId the sender's device id, same as used to register the device (v1/me/device/{device_id}/)
     * @param retentionFlags The data retentions flags as JSON formatted string, see setDataRetentionFlags()
     * @return 1 if call was OK and a 'own' conversation existed, 2 if an 'own' conversation was created and
     *         initialized, a negative value in case of errors
     */
    public native int doInit(int flags, String dbName, byte[] dbPassPhrase, byte[] userName, byte[] authorization,
                             byte[] scClientDevId, String retentionFlags);

    /**
     * Send a message with an optional attachment.
     *
     * Takes JSON formatted message descriptor and sends the message. The function accepts
     * an optional JSON formatted attachment descriptor and sends the attachment data to the
     * recipient together with the message.
     *
     * This is a blocking call and the function returns after the transport layer accepted the
     * message and returns. This function may take some time if the recipient is not yet known
     * and has no ratchet session. In this case the function interrogates the provisioning server
     * to get the necessary ratchet data of the recipient, creates a session and then sends the
     * message.
     *
     * After encrypting the message the function forwards the message data to the message handler.
     * The message handler takes the message, processes it and returns a unique message id (see
     * description of message handler API). The UI should use the unique id to monitor message
     * state, for example if the message was actually sent, etc. Refer to message state report
     * callback below. The message id is an opaque datum.
     *
     * The @c sendMessage function does not interpret or re-format the attachment descriptor. It takes
     * the string, encrypts it with the same key as the message data and puts it into the message
     * bundle.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, string, required.
     *
     * @param attachmentDescriptor   Optional, a string that contains an attachment descriptor. An empty
     *                               string ot {@code null} shows that not attachment descriptor is available.
     *
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string ot {@code null} shows that not attributes are available.
     *
     * @param resultCode an int array with at least a length of one. The functions returns the
     *        request result code at index 0
     *
     * @param normalMsg If true then this is a normal message, if false it's a command message.
     *
     * @return A list of unique 64-bit transport message identifiers, one for each message set to the user's
     *         devices. In case of error the functions return {@code null} and the {@code getErrorCode} and
     *         {@code getErrorInfo} have the details.
     */
    @WorkerThread
    public static native PreparedMessageData[] prepareMessageNormal(byte[] messageDescriptor, @Nullable byte[] attachmentDescriptor,
                                            @Nullable byte[] messageAttributes, boolean normalMsg, int[] resultCode);

    /**
     * Send message to sibling devices.
     *
     * Similar to {@code sendMessage, however send this data to sibling devices, i.e. to other devices that
     * belong to a user. The client uses function for send synchronization message to the siblings to
     * keep them in sync.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor   A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     *
     * @param resultCode an int array with at least a length of one. The functions returns the
     *        request result code at index 0
     *
     * @param normalMsg If true then this is a normal message, if false it's a command message. Messages to
     *                  siblings are usually commands
     *
     * @return unique message identifiers if the messages were processed for sending, 0 if processing
     *         failed.
     */
    @WorkerThread
    public static native PreparedMessageData[] prepareMessageSiblings(byte[] messageDescriptor, @Nullable byte[] attachmentDescriptor,
                                                         @Nullable byte[] messageAttributes, boolean normalMsg, int[] resultCode);

    /**
     * Encrypt the prepared messages and send them to the receiver.
     *
     * Queue the prepared message for encryption and sending to the receiver's devices.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions, and synchronized queue handling, thus should run on an own
     * worker thread, however not absolute necessary.
     *
     * @param transportIds An array of transport id that identify the messages to encrypt and send.
     * @return Number of queued messages, a negative value on failure
     */
    public static native int doSendMessages(@NonNull long[] transportIds);

    /**
     * Remove prepared messages from its queue.
     *
     * This function removes prepared messages from the prepared message queue. The function
     * does not remove prepared messages that were already sent by @c doSendMessages.
     *
     * The function does no trigger any network actions, save to run from UI thread,
     * uses database functions, and synchronized queue handling, thus should run on an own
     * worker thread, however not absolute necessary.
     *
     * @param transportIds An array of transport id that identify the messages to remove.
     * @return Number of removed messages, a negative value on failure
     */
    public static native int removePreparedMessages(@NonNull long[] transportIds);

    /**
     * Request names of known trusted ZINA user identities.
     *
     * The ZINA library stores an identity (name) for each remote user.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @return JSON formatted information about the known users. It returns an empty
     *         string if no users known. It returns NULL in case the request failed.
     *         Language bindings use appropriate return types.
     */
    public static native byte[] getKnownUsers();

    /**
     * Get public part of own identity key.
     *
     * The returned array contains the B64 encoded data of the own public identity key, optionally
     * followed by a colon and the device name.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @return public part of own identity key, {@code null} if no own identity key available
     */
    public static native byte[] getOwnIdentityKey();

    /**
     * Get a list of all identity keys of a remote party.
     *
     * The remote partner may have more than one device. This function returns the identity
     * keys of remote user's devices that this client knows of. The client sends messages only
     * to these known device of the remote user.
     *
     * The returned strings in the list contain the B64 encoded data of the public identity keys
     * of the known devices, followed by a colon and the device name, followed by a colon and the
     * the device id, followed by a colon and the ZRTP verify state:
     *
     * identityKey:device name:device id:verify state
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions. Consider using a worker thread, may take some time to run
     * all database actions
     *
     * @param user the name of the user
     * @return array of identity keys.
     */
    @Nullable
    public static native byte[][] getIdentityKeys(@Nullable byte[] user);

    /**
     * Request a user's ZINA device names.
     *
     * Ask the server for known ZINA devices of a user.
     *
     * @return JSON formatted information about user's ZINA devices. It returns {@code null}
     *         if no devices known for this user.
     */
    @WorkerThread
    public static native byte[] getZinaDevicesUser(byte[] userName);

    /**
     * Register ZINA device.
     *
     * Register this device with the server. The registration requires a device id that's unique
     * for the user's account on the server. The user should have a valid account on the server.
     *
     * In the Silent Circle use case the user name was provided during account creation, the client computes a
     * unique device id and registers this with the server during the first generic device registration.
     *
     * @param resultCode an int array with at least a length of one. The functions returns the
     *        request result code at index 0
     * @return a JSON string as UTF-8 encoded bytes, contains information in case of failures.
     */
    @WorkerThread
    public static native byte[] registerZinaDevice(int[] resultCode);

    /**
     * Remove ZINA device.
     *
     * Remove an ZINA device from a user's account.
     *
     * @param resultCode an int array with at least a length of one. The functions returns the
     *        request result code at index 0
     * @param deviceId the SC device id of the device to remove.
     * @return a JSON string as UTF-8 encoded bytes, contains information in case of failures.
     */
    @WorkerThread
    public static native byte[] removeZinaDevice(byte[] deviceId, int[] resultCode);

    /**
     * Generate and register a set of new pre-keys.
     *
     * @return Result of the register new pre-key request, usually a HTTP code (200, 404, etc)
     */
    @WorkerThread
    public static native int newPreKeys(int number);

    /**
     * Get number of pre-keys available on the server.
     *
     * Checks if the server has pre-keys for this account/device id and return how many keys are
     * available.
     *
     * @return number of available pre-keys or -1 if request to server failed.
     */
    @WorkerThread
    public static native int getNumPreKeys();

    /**
     * Return the stored error code.
     *
     * Functions of this implementation store error code in case they detect
     * a problem and return {@code null}, for example. In this case the caller should
     * get the error code and the additional error information for detailed error
     * data.
     *
     * Functions overwrite the stored error code only if they return {@code null} or some
     * other error indicator.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @return The stored error code.
     */
    public static native int getErrorCode();

    /**
     * Return the stored error information.
     *
     * Functions of this implementation store error information in case they detect
     * a problem and return {@code null}, for example. In this case the caller should
     * get the error code and the additional error information for detailed error
     * data.
     *
     * Functions overwrite the stored error information only if they return {@code null}
     * or some other error indicator.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @return The stored error information string.
     */
    public static native String getErrorInfo();

    /**
     * For testing only, not available in production code.
     *
     * Returns -1 in production code.
     */
    public static native int testCommand(String command, byte[] data);

    /**
     * Command interface to send management command and to request management information.
     *
     * @param command the management command string.
     * @param optional data required for the command.
     * @return a string depending on command.
     * @param resultCode an int array with at least a length of one. The functions returns the
     *        request result code at index 0
     */
    @WorkerThread
    public static native String zinaCommand(String command, byte[] data, int[] resultCode);

    /**
     * Receive a Message callback function.
     *
     * Takes JSON formatted message descriptor of the received message and forwards it to the UI
     * code via a callback functions. The function accepts an optional JSON formatted attachment
     * descriptor and forwards it to the UI code if a descriptor is available.
     *
     * The implementation classes for the different language bindings need to perform the necessary
     * setup to be able to call into the UI code.
     *
     * In any case the UI code shall not block processing of this callback function and shall return
     * from the callback function as soon as possible.
     *
     * The {@code receiveMessage} function does not interpret or re-format the attachment descriptor. It takes
     * the the data from the received message bundle, decrypts it with the same key as the message data
     * and forwards the resulting string to the UI code. The UI code can then use this data as input to
     * the attachment handling.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, string, required.
     *
     * @param attachmentDescriptor   Optional, a string that contains an attachment descriptor. An empty
     *                               string ot {@code null} shows that not attachment descriptor is available.
     *
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string ot {@code null} shows that not attributes are available.
     * @return Either success or an error code (to be defined)
     */
    @WorkerThread
    public abstract int receiveMessage(byte[] messageDescriptor, byte[] attachmentDescriptor, byte[] messageAttributes);

    /**
     * Message state change callback function.
     *
     * The ZINA library uses this callback function to report message state changes to the UI.
     * The library reports state changes of message it got for sending and it also reports if it
     * received a message but could not process it, for example decryption failed.
     *
     * @param messageIdentifier  the unique 64-bit transport message identifier. If this identifier
     *                           is 0 then this report belongs to a received message and the library
     *                           failed to process it.
     *
     * @param statusCode         The status code as reported from the network transport, usually like
     *                           HTTP or SIP codes. If less 0 then the messageIdentifier may be 0 and
     *                           this would indicate a problem with a received message.
     *
     * @param stateInformation   JSON formatted state information block (string) that contains the
     *                           details about the new state of some error information.
     */
    @WorkerThread
    public abstract void messageStateReport(long messageIdentifier, int statusCode, byte[] stateInformation);

    /**
     * Helper function to perform HTTP(S) requests callback function.
     *
     * The ZINA library uses this callback to perform HTTP(S) requests. The application should
     * implement this function to contact a provisioning server or other server that can return
     * required ZINA data. If the application really implements this as HTTP(S) or not is an
     * implementation detail of the application.
     *
     * The ZINA library creates a request URI, provides request data if required, specifies the
     * method (GET, PUT) to use. On return the function sets the request return code in the code
     * array at index 0 and returns response data as byte array.
     *
     * The functions returns after the HTTP(S) or other network requests return.
     *
     * @param requestUri the request URI without the protocol and domain part.
     * @param requestData data for the request if required
     * @param method to use, GET, PUT
     * @param code to return the request result code at index 0 (200, 404 etc)
     * @return the received data
     */
    @WorkerThread
    public abstract byte[] httpHelper(byte[] requestUri, String method, byte[] requestData, int[] code);

    /**
     * Notify callback.
     *
     * The ZINA library uses this callback function to report data of a SIP NOTIFY to the app.
     *
     * @param messageIdentifier  the unique 64-bit transport message identifier.
     *
     * @param notifyActionCode   This code defines which action to perform, for example re-scan a
     *                           user's ZINA devices
     *
     * @param actionInformation  JSON formatted state information block (string) that contains the
     *                           details required for the action.
     */
    @WorkerThread
    public abstract void notifyCallback(int notifyActionCode, byte[] actionInformation, byte[] deviceId);


    // *************************************************************
    // Group chat functions and callbacks
    // *************************************************************

    /**
     * Create a new group and assign ownership to the creator
     *
     * The function creates a new group and assigns the group's ownership to the creator.
     *
     * The function sets the group's size to {@code Constants::DEFAULT_GROUP_SIZE}.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param groupName The name of the new group
     * @param groupDescription Group description, purpose of the group, etc
     * @param maxMembers Maximum number of group members. If this number is bigger than a system
     *                   defined maximum number then the function does not create the group.
     * @return the group's UUID, if the string is empty then group creation failed, use
     *         {@code AppInterfaceImpl::getErrorInfo()} to get error string.
     */
    public static native String createNewGroup(byte[] groupName, byte[] groupDescription);

    /**
     * Modify number maximum group member.
     *
     * Only the group owner can modify the number of maximum members.
     *
     * If the new size would be less than current active group member the function fails
     * and returns {@code false.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param newSize New maximum group members
     * @param groupUuid The group id
     * @return {@code} true if new size could be set, {@code false} otherwise, use
     *         {@code AppInterfaceImpl::getErrorInfo()} to get error string.
     */
    public static native boolean modifyGroupSize(@NonNull String groupUuid, int newSize);

    /**
     * Set a group's new name.
     *
     * The function sets a new group name and synchronizes this with the other group
     * members if the user sends a message.
     *
     * @param groupUuid the group id
     * @param groupName the new group name. If this is NULL (nullptr) then the function removes the group name
     *                  update from the change set.
     * @return {@code SUCCESS} if new name could be set, or an error code
     */
    public static native int setGroupName(@NonNull String groupUuid, byte[] groupName);

    /**
     * Set a group's new burn time and mode.
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
     * @return {@code SUCCESS} if new name could be set, or an error code
     */
    public static native int setGroupBurnTime(@NonNull String groupUuid, long burnTime, int mode);

    /**
     * Set a group's new avatar data.
     *
     * The function sets a new group avatar data and synchronizes this with the other group
     * members if the user sends a message.
     *
     * @param groupUuid the group id
     * @param avatar the new avatar data. If this is NULL (nullptr) then the function removes the
     *               avatar info update from the change set.
     * @return {@code SUCCESS} if new name could be set, or an error code
     */
    public static native int setGroupAvatar(@NonNull String groupUuid, byte[] avatar);

    /**
     * Get data of all known groups.
     *
     * Creates and returns JSON data structures that contain the groups' data.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return Byte array of JSON formatted data as byte array.
     */
    public static native byte[][] listAllGroups(@NonNull int[] code);

    /**
     * Get data of all known groups which have certain user as participant.
     *
     * Creates and returns JSON data structures that contain the groups' data.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param participantUuid User id to look for in group participants
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return Byte array of JSON formatted data as byte array.
     */
    public static native byte[][] listAllGroupsWithMember(@NonNull String participantUuid, @NonNull int[] code);

    /**
     * Get data of a single group.
     *
     * Returns a JSON data structure that contains the group's data.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param groupUuid The group's UUID (RFC4122 time based UUID)
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return Byte array of JSON formatted data
     */
    public static native byte[] getGroup(@NonNull String groupUuid, @NonNull int[] code);

    /**
     * Get all members of a specified group.
     *
     * Creates and returns a list of JSON data structures that contain the group's
     * members data.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param groupUuid The group's UUID (RFC4122 time based UUID)
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return Byte array of JSON formatted data as byte array.
     */
    public static native byte[][]  getAllGroupMembers(@NonNull String groupUuid, @NonNull int[] code);

    /**
     * Get a member of a specified group.
     *
     * Creates and returns a JSON data structure that contains the member's data.
     *
     * This function does no trigger any network actions, save to run from UI thread,
     * uses database functions.
     *
     * @param groupUuid The group's UUID (RFC4122 time based UUID)
     * @param memberUuid the new member's UID
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return Byte array of JSON formatted data
     */
    public static native byte[] getGroupMember(@NonNull String groupUuid, @NonNull byte[]memberUuid, @NonNull int[] code);

    /**
     * Add a user to a group (same as invite)
     *
     * @param groupUuid Invite for this group
     * @param userId The invited user's unique id
     * @return {@code SUCCESS} or error code (<0)
     */
    public static native int addUser(@NonNull String groupUuid, @NonNull byte[] userId);

    /**
     * Remove a user's name from the add member update change set.
     *
     * Just remove the user's uid from the add member update change set, no other
     * actions or side effects, thus it is the opposite of `addUser`.
     *
     * @param groupUuid The group id
     * @param userId The user id to remove from the change set
     * @return {@code SUCCESS} if function could send invitation, error code (<0) otherwise
     */
    public static native int removeUserFromAddUpdate(@NonNull String groupUuid, @NonNull byte[] userId);

    /**
     * Cancel group's current change set.
     *
     * Clears the group's current change set.
     *
     * @param groupId Cancel current change set for this group
     * @return {@code SUCCESS} if function could send invitation, error code (<0) otherwise
     */
    public static native int cancelGroupChangeSet(@NonNull String groupId);

    /**
     * Apply group's current change set.
     *
     * This function applies the current group change set which may include the updates to add
     * a new member, set a group avatar, etc. The function just sends an empty message and this
     * checks for change sets
     *
     * @param groupId Apply current change set for this group
     * @return {@code SUCCESS} if function could send invitation, error code (<0) otherwise
     */
    public static native int applyGroupChangeSet(@NonNull String groupId);

    /**
     * Send a message to a group with an optional attachment and attributes.
     *
     * Takes JSON formatted message descriptor and send the message. The function accepts
     * an optional JSON formatted attachment descriptor and sends the attachment data to the
     * recipient together with the message.
     *
     * This is a blocking call and the function returns after the transport layer accepted the
     * message and returns.
     *
     * The {@code sendMessage} function does not interpret or re-format the attachment descriptor. It takes
     * the string, encrypts it with the same key as the message data and puts it into the message
     * bundle. The same is true for the message attributes.
     *
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachmentDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return {@code OK} if function could send the message, error code (<0) otherwise
     */
    @WorkerThread
    public static native int sendGroupMessage(@NonNull byte[] messageDescriptor, @Nullable byte[] attachmentDescriptor,
                                              @Nullable byte[] messageAttributes);

    /**
     * Send a group message to a single group member with an optional attachment and attributes.
     *
     * This function works in the same way as the normal {@code sendGroupMessage} but sends the message to
     * one member only. If the caller specifies a {@code deviceId} then the function sends this message to
     * the member's device.
     *
     * @param messageDescriptor     The JSON formatted message descriptor, required
     * @param attachmentDescriptor  A string that contains an attachment descriptor. An empty string
     *                              shows that not attachment descriptor is available.
     * @param messageAttributes     Optional, a JSON formatted string that contains message attributes.
     *                              An empty string shows that not attributes are available.
     * @param recipient             User id of the group member
     * @param device id             If specified then the function sends the message to the selected device
     *                              of the group member. Maybe {@code null} or empty.
     * @return @c OK if function could send the message, error code (<0) otherwise
     */
    @WorkerThread
    public static native int sendGroupMessageToMember(@NonNull byte[] messageDescriptor, @Nullable byte[] attachmentDescriptor,
                                                      @Nullable byte[] messageAttributes, @NonNull byte[] recipient, @Nullable String deviceId);

    /**
     * Send a group command to a single group member.
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
     * @param groupId               Group of the member
     * @param member                The recipient (user id)
     * @param msgId                 Optional message id
     * @param command               A string that contains a command.
     * @return {@code OK} if function could send the command, error code (<0) otherwise
     */
    @WorkerThread
    public static native int sendGroupCommandToMember(@NonNull String groupId, @NonNull byte[] member, String msgId, @NonNull byte[] command);

    /**
     * Leave a group.
     *
     * The application (UI part) calls this function to remove this member (myself) from the
     * group. This function sends the leave group update immediately.
     *
     * @param groupId The group to leave
     * @return {@code SUCCESS} if 'leave group' processing was OK, error code (<0) otherwise
     */
    @WorkerThread
    public static native int leaveGroup(@NonNull String groupId);

    /**
     * Remove another member (not myself) from a group.
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
     * @return {@code SUCCESS} if 'remove from group' processing was OK, error code (<0) otherwise
     */
    public static native int removeUser(@NonNull String groupId, @NonNull byte[] userId);

    /**
     * @brief Remove a user's name from the remove member update change set.
     *
     * Just remove the user's uid from the remove (remove group) member update change set, no other
     * actions or side effects, thus this function is the opposite of `removeUser`
     *
     * @param groupId The group id
     * @param userId The user id to remove from the change set
     * @return {@code SUCCESS} if function could send invitation, error code (<0) otherwise
     */
    public static native int removeUserFromRemoveUpdate(@NonNull String groupId, @NonNull byte[] userId);

    /**
     * Manually burn one or more group message(s).
     *
     * @param groupId The id of the group
     * @param messageId The message id of the removed message
     * @return {@code OK} or an error code
     */
    @WorkerThread
    public static native int burnGroupMessage(@NonNull String groupId, @NonNull String[] messageId);

    /**
     * Callback to UI to receive a normal group message.
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
    public abstract int groupMsgReceive(byte[]messageDescriptor, byte[]attachmentDescriptor, byte[] messageAttributes);

    /**
     * Callback to UI to receive a group command message.
     *
     * JSON format TBD
     *
     * @param commandMessage  A JSON formatted string that contains the command message.
     * @return Either success of an error code (to be defined)
     */
    public abstract int groupCmdReceive(byte[] commandMessage);

    /**
     * Callback to UI for a Group Message state change report
     *
     * The ZINA library uses this callback function to report message state changes to the UI.
     * The library reports message state changes for sending and it also reports if it
     * received a message but could not process it, for example decryption failed.
     *
     * @param errorCode          The error code
     * @param stateInformation   JSON formatted stat information block that contains the details about
     *                           the new state or some error information.
     */
    public abstract void groupStateCallback(int errorCode, byte[] stateInformation);


    /*
     ***************************************************************
     * Below the native interfaces for the repository database
     * *************************************************************
     */

    /**
     * Open the repository database.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param databaseName The path and filename of the database file.
     * @return {@code true} if open was OK, {@code false} if not.
     */
    public static native int repoOpenDatabase(String databaseName, byte[] keyData);

    /**
     * Close the repository database.
     */
    public static native void repoCloseDatabase();

    /**
     * Check if repository database is open.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @return {@code true} if repository is open, {@code false} if not.
     */
    public static native boolean repoIsOpen();

    /**
     * Checks if a conversation for the name pattern exists.
     *
     * A unique conversation consists of a local username, a special separator and the
     * partner name, concatenated to one string. See comment for
     * {@link com.silentcircle.messaging.repository.DbRepository.DbConversationRepository}.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name This name is the parameter with the unique conversation name.
     * @return {@code true} if the pattern exists, {@code false} if not.
     */
    public static native boolean existConversation(byte[] name);

    /**
     * @brief Store serialized conversation data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name the unique conversation name
     * @param conversation The serialized data of the conversation data structure
     * @return An SQLITE code.
     */
    public static native int storeConversation(byte[]name, byte[] conversation);

    /**
     * Load and return serialized conversation data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name the unique conversation name
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return The serialized data of the conversation data structure, {@code null} if no
     *         such conversation
     */
    public static native byte[] loadConversation(byte[]name, int[] code);

    /**
     * Delete a conversation.
     *
     * Deletes a conversation and all its related data, including messages, events, objects.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name Name of conversation
     * @return A SQLITE code.
     */
    public static native int deleteConversation(byte[] name);

    /**
     * Return a list of names for all known conversations.
     *
     * @return A list of names for conversations, {@code null} in case of an error.
     */
    public static native byte[][] listConversations();

    /**
     * Insert serialized event/message data.
     *
     * The functions inserts the event/message data and assigns a sequence number to this
     * record. The sequence number is unique inside the set of messages of a conversation.
     *
     * The functions returns and error in case a record with the same event id for this
     * conversation already exists.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param event The serialized data of the event data structure
     * @return A SQLITE code.
     */
    public static native int insertEvent(byte[] name, byte[] eventId, byte[] event);

    /**
     * Load and returns one serialized event/message data.
     *
     * The functions returns the sequence number of the loaded event record at index 1 of
     * the return {@code code} array
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param code array of length 2 to return the request result code at index 0 (usually
     *             a SQLITE code) and the message sequence number at index 1.
     * @return The serialized data of the event/message data structure, {@code null} if no
     *         such event/message
     */
    public static native byte[] loadEvent(byte[] name, byte[]eventId, int[] code);

    /**
     * Load a message with a defined message id.
     *
     * Lookup and load a message based on the unique message id (UUID). The function does
     * not restrict the lookup to a particular conversation.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param eventId The message id
     * @param code An int array with a minimum length of 1. Index 0 has the SQL code
     *        on return
     * @return the message or {@code null} if no message found
     */
    public static native byte[] loadEventWithMsgId(byte[] eventId, int[] code);

    /**
     * Checks if an event exists.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @return {@code true} if the event exists, {@code false} if not.
     */
    public static native boolean existEvent(byte[] name, byte[] eventId);

    /**
     * Load and returns a set of serialized event/message data.
     *
     * Each event/message record has an increasing serial number and the highest serial number
     * is the newest message. This function provides several ways to select the set of message
     * records to return:
     *
     * If {@code direction} is -1 then the functions takes offset (in case of -1 the highest available
     * message number) and retrieves {@code number} messages. It sorts the message
     * records is descending order, thus the newest message is the first in the returned vector.
     *
     * If {@code direction} is 1 then the function takes {@code offset} as a sequence number of a
     * record and starts to select {@code number} of records or until the end of the record table,
     * sorted in ascending order.
     *
     * If {@code offset} and {@code number} are both -1 the the functions return all message records,
     * sorted in descending order.
     *
     * If {@code direction} is not -1 or 1 and if {@code offset} and {@code number} are not -1 then the function
     * selects records between {@code offset} and {@code offset+number-1} .
     *
     * The functions returns the sequence number of the last (oldest) event record, i.e. the
     * smallest found sequence number in case {@code direction} is -1 and largest sequence number in
     * all other cases.
     *
     * This function does no trigger any network actions, save to run from UI thread, maybe
     * I/O bound, consider using a background thread.
     *
     * @param name The conversation partner's name
     * @param offset Where to start to retrieve the events/message
     * @param number How many events/messages to load
     * @param direction Paging direction from youngest to oldest (-1) or from oldest to youngest (1)
     * @param code array of length 2 to return the request result code at index 0 (usually
     *             a SQLITE code) and the message sequence number at index 1. The message number
     *             0 indicates that no messages were read.
     * @return Array of byte arrays that contain the serialized event data
     */
    public static native byte[][] loadEvents(byte[]name, int offset, int number, int direction, int[] code);

    /**
     * Delete an event from a conversation.
     *
     * Deletes an event/message and all its related data.
     *
     * This function does not trigger any network actions, save to run from UI thread.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @return A SQLITE code.
     */
    public static native int deleteEvent(byte[] name, byte[] eventId);

    /**
     * Delete all events from given conversation.
     *
     * Deletes all events/messages for conversation and their related data in data base.
     *
     * This function does not trigger any network actions, safe to run from UI thread.
     *
     * @param name Name of conversation
     * @return A SQLITE code.
     */
    public static native int deleteAllEvents(byte[] name);

    /**
     * Insert serialized Object (attachment) data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param objectId The object id, unique inside the event it belongs to
     * @param object The serialized data of the object data structure
     * @return A SQLITE code.
     */
    public static native int insertObject(byte[] name, byte[] eventId, byte[] objectId, byte[] object);

    /**
     * @brief Load and returns one serialized object descriptor data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name The conversation partner's name
     * @param eventId The event id
     * @param objectId The object id, unique inside the event it belongs to
     * @param code array of length 1 to return the request result code at index 0, usually a SQLITE code
     * @return The serialized data of the object description data structure, {@code null} if no
     *         such object
     */
    public static native byte[] loadObject(byte[] name, byte[] eventId, byte[] objectId,int[] code);

    /**
     * Checks if an object descriptor exists.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @param objectId Id of the object
     * @return {@code true} if the object exists, {@code false} if not.
     */
    public static native boolean existObject(byte[] name, byte[] eventId, byte[] objectId);

    /**
     * Load and returns the serialized data of all object descriptors for this event/message.
     *
     * This function does no trigger any network actions, save to run from UI thread, maybe
     * I/O bound, consider using a background thread.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @return The list of serialized data of the object data structures, {@code null} if none
     *         exist.
     */
    public static native byte[][] loadObjects(byte[]name, byte[] eventId, int[] code);

    /**
     * Delete an object descriptor from the event/message.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @param objectId Id of the object
     * @return A SQLITE code.
     */
    public static native int deleteObject(byte[] name, byte[] eventId, byte[] objectId);

    /**
     * Insert or update the attachment status.
     *
     * If no entry exists for the msgId then insert a new entry and set its
     * status to 'status'. If an entry already exists then update the status
     * only.
     *
     * If the caller provides a {@code partnerName} then the function stores it together
     * with the message id.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param msgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe {@code null}
     * @param status the new attachment status
     * @return the SQL code
     */
    public static native int storeAttachmentStatus(byte[] msgId, byte[] partnerName, int status);

    /**
     * Delete the attachment status entry.
     *
     * If the partner name is {@code null} then the function uses only the message id to
     * perform the delete request. Otherwise it requires full match, message id and
     * partner name to delete the status entry.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param msgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe {@code null}
     * @return the SQL code
     */
    public static native int deleteAttachmentStatus(byte[] msgId, byte[] partnerName);

    /**
     * Delete all attachment status entries with a given status.
     *
     * @param status the status code
     * @return the SQL code
     */
    public static native int deleteWithAttachmentStatus(int status);

    /**
     * Return attachment status for msgId.
     *
     * If the partner name is {@code null} then the function uses only the message id to
     * perform the load request. Otherwise it requires full match, message id and
     * partner name to load the status entry.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param msgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe {@code null}
     * @param code is an array with a minimum  length of 1, index 0 contains
     *        the SQL code on return
     * @return the attachment status or -1 in case of error
     */
    public static native int loadAttachmentStatus(byte[] msgId, byte[] partnerName, int[] code);

    /**
     * Return all message ids with a given status.
     *
     * Returns a string array that contains the message identifier as UUID string.
     * If a partner name was set then the functions appends a colon (:) and the
     * partner name to the UUID string.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param status finds all attachment message ids with this status.
     * @param code is an array with a minimum  length of 1, index 0 contains
     *        the SQL code on return
     * @return a String array with the found message ids (UUID) or {@code null}
     *         in case of error
     */
    public static native String[] loadMsgsIdsWithAttachmentStatus(int status, int[] code);


    /*
     ***************************************************************
     * Below the native interface for the SClound crypto primitives
     * -- these do not really belong to ZINA, however it's simpler
     *    to leave it in one library --
     * *************************************************************
     */

    /**
     * Prepare and setup a new file encryption.
     *
     * This function takes the data and the meta-data of a file, creates an internal context
     * and populates it with the data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param context  Some random bytes which the functions uses to salt the locator (the file
     *                 name of the encrypted file). This is optional and may be {@code null}.
     *
     * @param data     The data to encrypt
     *
     * @param metaData The meta data that describes the data
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a long integer that identifies the internally created context. The call shall not
     *         modify this long integer.
     */
    public static native long cloudEncryptNew (byte[] context, byte[] data, byte[] metaData, int[] errorCode);

    /**
     * Compute the encryption key, IV, and the locator for the encrypted file.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @return the result or error code
     */
    public static native int cloudCalculateKey (long scloudRef);  // blocksize not required

    /**
     * Get the JSON structured key information.
     *
     * The caller can request the computed key data. The function returns a JSON string which
     * contains the data:
     *
     * {"version":2,"keySuite":0,"symkey":"0E685AC318D9D465B40416ABA4C7ACA57A2D50E0695320224DDB08B38FAD68BA"}
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a byte array (UTF-8 data) that contains the JSON string.
     */
    public static native byte[] cloudEncryptGetKeyBLOB(long scloudRef, int[] errorCode);

    /**
     * Get the JSON structured segment information.
     *
     * The caller can request the segment data. The function returns a JSON string which
     * contains the data:
     *
     * [1,"QGn3vsKDOaCels0gQGuEPlBDkPIA",{"version":2,"keySuite":0,"symkey":"0E685AC318D9D465B40416ABA4C7ACA57A2D50E0695320224DDB08B38FAD68BA"}]
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param segNum  The number of the segment
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a byte array (UTF-8 data) that contains the JSON string.
     */
    public static native byte[] cloudEncryptGetSegmentBLOB(long scloudRef, int segNum, int[] errorCode);

    /**
     * Get the binary locator information.
     *
     * The caller can request the computed binary locator data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a byte array that contains the data.
     */
    public static native byte[] cloudEncryptGetLocator(long scloudRef, int[] errorCode);

    /**
     * Get the URL Base64 encoded locator information.
     *
     * The caller can request a URL Base64 encode locator string.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a byte array (UTF-8 data) that contains the string.
     */
    public static native byte[] cloudEncryptGetLocatorREST(long scloudRef, int[] errorCode);

    /**
     * Encrypt and return the data.
     *
     * The function adds a header, the meta data to the file (raw) data and encrypts this
     * bundle. The {@code buffer} must be large enough to hold all data. Refer to
     * {@code cloudEncryptBufferSize} to get the required buffer size.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a byte array with the formatted and encrypted data
     */
    public static native byte[] cloudEncryptNext(long scloudRef, int[] errorCode);

    /**
     * Prepare and setup file decryption.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param key   the key information  to decrypt the file. Must be in JSON format
     *              as returned by {@code cloudEncryptGetKeyBLOB}.
     *
     * @param errorCode A 1 element integer array that returns the result code/error code.
     *
     * @return a long integer that identifies the internally created context. The call shall not
     *         modify this long integer.
     */
    public static native long cloudDecryptNew (byte[] key, int[] errorCode);

    /**
     * Decrypt a formatted file.
     *
     * Call this function to either decrypt a whole file at once or read the file in smaller
     * parts and call this function with the data until no more data available.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @param in  a byte buffer that contains the file to or a part of the file to encrypt. The
     *            file must be encrypted with {@code cloudEncryptNext}.
     *
     * @return result code
     */
    public static native int cloudDecryptNext(long scloudRef, byte[] in);

    /**
     * Get the decrypted data.
     *
     * After the caller has no more data to decrypt it needs to call this function which returns
     * the decrypted data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @return  a byte buffer that contains all the decrypted data of the file or {@code null}
     */
    public static native byte[] cloudGetDecryptedData(long scloudRef);

    /**
     * Get the decrypted meta data.
     *
     * After the caller has no more data to decrypt it needs to call this function which returns
     * the decrypted meta data.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     *
     * @return  a byte buffer that contains all the decrypted meta data of the file or {@code null}
     */
    public static native byte[] cloudGetDecryptedMetaData(long scloudRef);

    /**
     * Free the context data.
     *
     * The caller must call this function after all the encrypt or decrypt operations are complete.
     * The context is invalid after this call.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param scloudRef the long integer context identifier
     */
    public static native void cloudFree(long scloudRef);

    /*
     ***************************************************************
     * Below the native interface for the alias name lookup functions
     * -- these do not really belong to ZINA, however it's simpler
     *    to leave it in one library --
     * *************************************************************
     */

    /**
     * Get the UID (canonical name) for the alias.
     *
     * This function returns the user's UID (canonical name) for the alias name. Because this
     * function may request this mapping from a server the caller shall not call this function
     * in the main (UI) thread.
     *
     * @param alias The alias
     * @param authorization The API-key, may be {@code null}. If this is {@code null} then the
     *                      functions uses the authorization data that the call defined in the
     *                      #doInit call.
     * @return the UID for the alias or {@code null} if no UID exists for the alias.
     */
    @WorkerThread
    public static native String getUid(String alias, byte[] authorization);

    /**
     * Get the user information for the alias.
     *
     * This function returns the user information that's stored in the cache or on the
     * provisioning server for the alias. Because this function may request this mapping
     * from a server the caller must not call this function in the main (UI) thread.
     *
     * The function returns a JSON formatted string:
     * <pre>
     * {
     *   "uid":          "<string>"
     *   "display_name": "<string>"
     *   "alias0":       "<string>"
     *   "lookup_uri":   "<string>"
     *   "avatar_url":   "<string>"
     *   "dr_enabled":   "<bool>"
     * }
     * </pre>
     *
     * The {@code display_name} string contains the user's full/display name as returned by the
     * provisioning server, the {@code alias0} is the user's preferred alias, returned by the
     * provisioning server. The {@code lookup_uri} may be empty if it was not set in the lookup
     * cache with #addAliasToUuid.
     *
     * Note: the provisioning server never returns a {@code lookup_uri} string, the application
     * must call #addAliasToUuid to set this string. Despite it's name an application may use
     * this string to store some internal data for a UUID - the name was chosen because we used
     * it to store the {@code lookup_uri} of a contact entry in Android's contact application.
     *
     * @param alias An alias name or the UUID
     * @param authorization The API-key, may be {@code null}. If this is {@code null} then the
     *                      functions uses the authorization data that the call defined in the
     *                      #doInit call.
     * @param errorCode A 1 element integer array that returns the result code/error code, can
     *                  {@code null}.
     *
     * @return a JSON formatted string as UTF byte array or {@code null} if no user data exists
     *         for the alias.
     */
    @WorkerThread
    public static native byte[] getUserInfo(String alias, byte[] authorization, int[] errorCode);

    /**
     * Refresh cached user data.
     *
     * The function accesses the provisioning server to get a fresh set of user data.
     *
     * @param alias the alias name/number or the UUID
     * @param authorization the authorization data, can be empty if {@code cacheOnly} is {@code true}
     * @param cacheOnly If true only look in the cache, don't contact server if not in cache
     * @return  a JSON formatted string as UTF byte array or {@code null} if no user data exists
     *         for the alias.
     *
     * @see getUserInfo(String alias, byte[] authorization)
     */
    @WorkerThread
    public static native byte[] refreshUserData(String aliasUuid, byte[] authorization);

    /**
     * Get the user information for the alias from cache.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param alias An alias name or the UUID
     * @param authorization The API-key, may be {@code null}. If this is {@code null} then the
     *                      functions uses the authorization data that the call defined in the
     *                      #doInit call.
     * @return a JSON formatted string as UTF byte array or {@code null} if no user data exists
     *         for the alias in the cache.
     *
     * @see getUserInfo(String alias, byte[] authorization)
     */
    public static native byte[] getUserInfoFromCache(String alias);

    /**
     * Return a list of the alias names of a UUID.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param uuid the UUID
     * @authorization the authorization data
     * @return Array of strings (encoded as UTF-8 bytes) or {@code null} if alias is not known.
     */
    public static native byte[][] getAliases(String uuid);

    /**
     * Add an alias name and user info to an UUID.
     *
     * If the alias name already exists in the map the function is a no-op and returns
     * immediately after amending the {@code lookup_uri} string if necessary..
     *
     * The function then performs a lookup on the UUID. If it exists then it simply
     * adds the alias name for this UUID and uses the already existing user info, thus
     * ignores the provided user info except for the {@code lookup_uri} and {@code avatar_url}
     * strings.
     *
     * If {@code lookup_uri} or {@code avatar_url} are empty in the cached user info and if
     * they are available in the provided user info then the functions stores the
     * {@code lookup_uri} or {@code avatar_url} string, thus the caller can amend existing
     * user info data with {@code lookup_uri} and/or {@code avatar_url}.
     *
     * If the UUID does not exist the function creates an UUID entry in the cache and
     * links the user info to the new entry. Then it adds the alias name to the UUID.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * The JSON data should look like this:
     * <pre>
     * {
     *   "uuid":          "<string>",
     *   "display_name":  "<string>",
     *   "display_alias": "<string>"
     *   "lookup_uri":    "<string>"
     *   "avatar_url":    "<string>"
     *   "dr_enabled":   "<bool>"
     * }
     * </pre>
     *
     * @param alias the alias name/number
     * @param uuid the UUID
     * @param userInfo a JSON formatted string with the user information
     * @authorization the authorization data
     * @return a value > 0 to indicate success, < 0 on failure.
     */
    public static native int addAliasToUuid(String alias, String uuid, byte[] userInfo);

    /**
     * Return the display name of a UUID.
     *
     * This function does no trigger any network actions, save to run from UI thread.
     *
     * @param uuid the UUID
     * @authorization the authorization data
     * @return The display name or a {@code null} pointer if none available
     */
    public static native byte[] getDisplayName(String uuid);

    /**
     * Return an array list of message trace records.
     *
     * The function selects and returns a list of JSON formatted message trace records, ordered by the
     * sequence of record insertion. The function supports the following selections:
     * <ul>
     * <li>{@code name} contains data, {@code messageId} and {@code deviceId} are empty: return all message trace records
     *     for this name</li>
     * <li>{@code messageId} contains data, {@code name} and {@code deviceId} are empty: return all message trace records
     *     for this messageId</li>
     * <li>{@code deviceId} contains data, {@code name} and {@code messageId} are empty: return all message trace records
     *     for this deviceId</li>
     * <li>{@code messageId} and {@code} deviceId contain data, {@code} name is empty: return all message trace records
     *     that match the messageId AND deviceId</li>
     * </ul>
     * @param name The message sender's/receiver's name (SC uid)
     * @param messageId The UUID of the message
     * @param deviceId The sender's device id
     * @param errorCode A 1 element integer array that returns the result code/error code.
     * @return byte array of UTF-8 byte trace records, maybe empty, or {@code null} in case of parameter error.
     */
    public static native byte[][] loadCapturedMsgs(byte[] name, byte[] messageId, byte[] deviceId, int[] errorCode);

    /**
     * Send the message data to the Data Retention S3 bucket if this user is
     * configured for data retention.
     *
     * @param callid The call id of the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param recipient The userid of the recipient.
     * @param composedTime The time the message was composed as an epoch value.
     * @param sentTime The time the message was sent as an epoch value.
     * @param message The plain text of the message.
     */
    public static native void sendDrMessageData(String callid, String direction, String recipient, long composedTime, long sentTime, String message);

    /**
     * Send the message metadata to the Data Retention S3 bucket if this user is
     * configured for data retention.
     *
     * @param callid The call id of the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param recipient The userid of the recipient.
     * @param composedTime The time the message was composed as an epoch value.
     * @param sentTime The time the message was sent as an epoch value.
     */
    public static native void sendDrMessageMetadata(String callid, String direction, String recipient, long composedTime, long sentTime);

    /**
     * Send the in call metadata to the Data Retention S3 bucket if this user is
     * configured for data retention.
     *
     * @param callid The call id for the call.
     * @param isIncoming true if this is an incoming call, false if it is an outgoing call.
     * @param recipient Userid of the recipient of the call.
     * @param start Time that the call started as an epoch value.
     * @param end Time that the call ended as an epoch value.
     */
    public static native void sendDrInCircleCallMetadata(String callid, boolean isIncoming, String recipient, long start, long end);

    /**
     * Send the Silent World call metadata to the Data Retention S3 bucket if this user is
     * configured for data retention.
     *
     * @param callid The call id for the call.
     * @param isIncoming true if this is an incoming call, false if it is an outgoing call.
     * @param srcTn Source PSTN number or empty if none.
     * @param dstTn PSTN telephone number called in E164 format.
     * @param start Time that the call started as an epoch value.
     * @param end Time that the call ended as an epoch value.
     */
    public static native void sendDrSilentWorldCallMetadata(String callid, boolean isIncoming, String srcTn, String dstTn, long start, long end);

    /**
     * Process any queued pending Data Retention requests.
     *
     */
    public static native void processPendingDrRequests();

    /**
     * Check if data retention is enabled for the current user.
     *
     * @return {@code true} if data retention is enabled, {@code false} if not.
     */
    public static native boolean isDrEnabled();

    /**
     * Check if data retention is enabled for a specific user.
     *
     * @return {@code true} if data retention is enabled, {@code false} if not.
     */
    public static native boolean isDrEnabledForUser(String user);

    /**
     * Set the data retention flags for the local user.
     *
     * The caller sets up a JSON formatted string that holds the data retention flags
     * for the local user. The JSON string
     *<pre>
     * {
     * "lrmm": "true" | "false",
     * "lrmp": "true" | "false",
     * "lrap": "true" | "false",
     * "bldr": "true" | "false",
     * "blmr": "true" | "false",
     * "brdr": "true" | "false",
     * "brmr": "true" | "false"
     * }
     *</pre>
     * If the application does not call this function after ZINA initialization then ZINA
     * assumes "false" for each flag, same if the JSON string does not contain a flag or
     * the flag's value is not "true" or "false". Otherwise ZINA sets the flag to the given value.
     *
     * @param flagsJson The JSON data of the flags to set.
     * @return SUCESS (0) or an error code, The function does not change flags in case of
     *         error return
     */
    public static native int setDataRetentionFlags(String flagsJson);
}
