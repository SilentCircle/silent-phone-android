#ifndef UIINTERFACE_H
#define UIINTERFACE_H

/**
 * @file AppInterface.h
 * @brief Interface to the application
 * @ingroup Axolotl++
 * @{
 */

#include <string>
#include <vector>
#include <list>

#include "../interfaceTransport/Transport.h"

using namespace std;

typedef int32_t (*RECV_FUNC)(const string&, const string&, const string&);
typedef void (*STATE_FUNC)(int64_t, int32_t, const string&);
typedef void (*NOTIFY_FUNC)(int32_t, const string&, const string&);

namespace axolotl {
class AppInterface
{
public:
    static const int DEVICE_SCAN = 1;

    AppInterface() : receiveCallback_(NULL), stateReportCallback_(NULL), notifyCallback_(NULL) {}

    AppInterface(RECV_FUNC receiveCallback, STATE_FUNC stateReportCallback, NOTIFY_FUNC notifyCallback) : 
                 receiveCallback_(receiveCallback), stateReportCallback_(stateReportCallback), notifyCallback_(notifyCallback) {}

    virtual ~AppInterface() {}

    /**
     * @brief Set the transport class.
     * 
     * Ownership stays with caller, the AppInterface implementation does not delete the Transport.
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
     * @brief Send a message with an optional attachment.
     *
     * Takes JSON formatted message descriptor and send the message. The function accepts
     * an optional JSON formatted attachment descriptor and sends the attachment data to the
     * recipient together with the message.
     *
     * This is a blocking call and the function returns after the transport layer accepted the
     * message and returns. This function may take some time if the recipient is not yet known
     * and has no Axolotl session. In this case the function interrogates the provisioning server
     * to get the necessary Axolotl data of the recipient, creates a session and sends the 
     * message.
     *
     * After encrypting the message the functions forwards the message data to the message handler.
     * The message handler takes the message, processes it and returns a unique message id (see 
     * description of message handler API). The UI should use the unique id to monitor message
     * state, for example if the message was actually sent, etc. Refer to message state report
     * callback below. The message id is an opaque datum.
     *
     * The @c sendMessage function does not interpret or re-format the attachment descriptor. It takes
     * the string, encrypts it with the same key as the message data and puts it into the message
     * bundle. The same is true for the message attributes.
     * 
     * @c sendMessage may send the message to more than one target if the user has more than one
     * device regsieterd for Axolotl usage. In this case the method returns a unique message id
     * for each message sent.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachementDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return unique message identifiers if the messages were processed for sending, 0 if processing
     *         failed.
     */
    virtual vector<int64_t>* sendMessage(const string& messageDescriptor, const string& attachementDescriptor, const string& messageAttributes) = 0;

    /**
     * @brief Send message to siblinge devices.
     * 
     * Similar to @c sendMessage, however send this data to sibling devices, i.e. to other devices that
     * belong to a user. The client uses function for send synchronization message to the siblings to
     * keep them in sync.
     * 
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachementDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that not attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return unique message identifiers if the messages were processed for sending, 0 if processing
     *         failed.
     */
    virtual vector<int64_t>* sendMessageToSiblings(const string& messageDescriptor, const string& attachementDescriptor, const string& messageAttributes) = 0;

    /**
     * @brief Receive a Message from transport
     *
     * Takes JSON formatted message envelope of the received message and forwards it to the UI
     * code via a callback functions. The function accepts an optional JSON formatted attachment
     * descriptor and forwards it to the UI code if a descriptor is available.
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
     * the the data from the received message bundle, decrypts it with the same key as the message data
     * and forwards the resulting string to the UI code. The UI code can then use this data as input to
     * the attachment handling.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachementDescriptor  A string that contains an attachment descriptor. An empty string
     *                               shows that no attachment descriptor is available.
     * @param messageAttributes      Optional, a JSON formatted string that contains message attributes.
     *                               An empty string shows that not attributes are available.
     * @return Either success of an error code (to be defined)
     */
    virtual int32_t receiveMessage(const string& messageEnvelope) = 0;

    /**
     * @brief Send a message state report to the application.
     *
     * The library reports state changes of message that it cannot process, for example decryption failed
     * for a received message.
     *
     * @param messageIdentifier  the unique message identifier. If this identifier is 0 then this 
     *                           report belongs to a received message and the library failed to 
     *                           process it.
     *
     * @param statusCode         The status code. Usually like HTTP or SIP codes. If less 0 then the 
     *                           messageIdentfier may be 0 and this would indicate a problem with a 
     *                           received message.
     * 
     * @param stateInformation   JSON formatted stat information block that contains the details about
     *                           the new state or some error information.
     */
    virtual void messageStateReport(int64_t messageIdentfier, int32_t stateCode, const string& stateInformation) = 0;

    /**
     * @brief Request names of known trusted Axolotl user identities
     *
     * The Axolotl library stores an identity (name) for each remote user.
     *
     * @return JSON formatted information about the known users. It returns an empty 
     *         JSON array if no users known. It returns NULL in case the request failed.
     *         Language bindings use appropriate return types.
     */
    virtual string* getKnownUsers() = 0;

    /**
     * @brief Get name of own user.
     *
     * The Axolotl library stores an identity (name) for each remote user.
     *
     * @return Reference to internal own user
     */
    virtual const string& getOwnUser() const = 0;

    /**
     * @brief Get public part of own identity key.
     * 
     * The returned strings is the B64 encoded data of the own public identity key, optinally
     * followed by a colon and the device name.
     *
     * @return public part of own identity key
     */
    virtual string getOwnIdentityKey() const = 0;

    /**
     * @brief Get a list of all identity keys of a user.
     * 
     * The remote partner may have more than one device. This function returns the identity 
     * keys of remote user's devices that this client knows of. The client sends messages only
     * to these known device of the remote user.
     * 
     * The returned strings in the list contain the B64 encoded data of the public identity keys
     * of the known devices, followed by a colon and the device name, followed by a colon and the
     * the device id, followed by a colon and the ZRTP verify state.
     * 
     * identityKey:device name:device id:verify state
     * 
     * @param user the name of the user
     * @return list of identity keys. An empty list if no identity keys are available for that user.
     */
    virtual list<string>* getIdentityKeys(string& user) const = 0;

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
    virtual int32_t registerAxolotlDevice(string* result) = 0;

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
     * @brief Rescan user device.
     *
     * Checks if a use has registered a new Axolotl device
     *
     */
    virtual void rescanUserDevices(string& userName) = 0;

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
     * the the data from the received message bundle, decrypts it with the same key as the message data
     * and forwards the resulting string to the UI code. The UI code can then use this data as input to
     * the attachment handling.
     *
     * @param messageDescriptor      The JSON formatted message descriptor, required
     * @param attachementDescriptor  A string that contains an attachment descriptor. An empty string
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
     * The library reports state changes of message it got for sending and it also reports if it
     * received a message but could not process it, for example decryption failed.
     *
     * @param messageIdentifier  the unique message identifier. If this identifier is 0 then this 
     *                           report belongs to a received message and the library failed to 
     *                           process it.
     * @param stateInformation   JSON formatted stat information block that contains the details about
     *                           the new state or some error information.
     */
    STATE_FUNC stateReportCallback_;

    /**
     * @brief Notify callback.
     *
     * The Axolotl library uses this callback function to report data of a SIP NOTIFY to the app.
     *
     * @param messageIdentifier  the unique 64-bit transport message identifier. 
     * 
     * @param notifyActionCode   This code defines which action to perform, for example re-scan a
     *                           user's Axolotl devices
     * 
     * @param actionInformation  JSON formatted state information block (string) that contains the
     *                           details required for the action.
     */
    NOTIFY_FUNC notifyCallback_;
};
} // namespace

/**
 * @}
 */

#endif // UIINTERFACE_H
