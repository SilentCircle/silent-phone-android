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
#ifndef TRANSPORT_H
#define TRANSPORT_H

/**
 * @file Transport.h
 * @brief Interface for tnetwork transport functions
 * @ingroup Zina
 * @{
 */

#include <stdint.h>
#include <utility>
#include <vector>
#include <string>
#include <memory>

// bool g_sendDataFuncAxoNew(uint8_t* name, uint8_t* devId, uint8_t* envelope, size_t size, uint64_t msgId){
typedef bool (*SEND_DATA_FUNC)(uint8_t*, uint8_t*, uint8_t*, size_t, uint64_t);

namespace zina {

// Forward declaration to avoid include of AppInterfaceImpl.h
typedef struct CmdQueueInfo_ CmdQueueInfo;

class Transport
{
public:
    virtual ~Transport() {}

    /**
     * @brief Set the function that actually sends data.
     *
     * @param sendData The function that actually sends the data.
     */
    virtual void setSendDataFunction(SEND_DATA_FUNC sendData) = 0;

    /**
     * @brief Get the current sendData function pointer.
     * 
     * @return Pointer to the current sendData function.
     */
    virtual SEND_DATA_FUNC getTransport() = 0;

    /**
     * @brief Prepare and send a message via the transport.
     * 
     * The App interface calls this function after it prepared the message envelopes. If the user has
     * more than one Axolotl device then the function sends out all envelopes, one for each device.
     * 
     * @param info The meta-data of the message ot send
     * @param envelope The message envelope, serialized as string and B64 encoded
     */
    virtual void sendAxoMessage(const CmdQueueInfo& info, const std::string& envelope) = 0;

    /**
     * @brief Receive data from network transport - callback function for network layer.
     *
     * The network layer calls this function to forward a received Axolotl message bundle. The network
     * transport can delete its data buffer after the call returns.
     *
     * @param data    pointer to received data, printable characters
     * @param length  length of the data array (may not be 0 terminated)
     * @return Success (1) if function can process the message, -10 for generic error, -13 if message
     *         is not for this client.
     */
    virtual int32_t receiveAxoMessage(uint8_t* data, size_t length) = 0;

    /**
     * @brief Receive data from network transport - callback function for network layer.
     *
     * The network layer calls this function to forward a received Axolotl message bundle. The network
     * transport can delete its data buffer after the call returns.
     *
     * The transport layer calls this function to hand over information it can obtain
     * from the transport protocol, e.g. SIP.
     *
     * @param data    pointer to received data, printable characters
     * @param length  length of the data array (may not be 0 terminated)
     * @param uid     pointer the user's UID if available, currently only for SIP transport
     * @param uidLen  length of @c uid data (may not be 0 terminated)
     * @param primaryAlias  pointer to the users's alias name (from header in SIP) printable characters
     * @param aliasLen  length of the @c alias data (may not be 0 terminated)
     * @return Success (1) if function can process the message, -10 for generic error, -13 if message
     *         is not for this client.
     */
    virtual int32_t receiveAxoMessage(uint8_t* data, size_t length, uint8_t* uid,  size_t uidLen,
                                      uint8_t* primaryAlias, size_t aliasLen) = 0;

    /**
     * @brief Report message status changes - callback function for network layer.
     * 
     * The network layer calls this function if message state changes, for example sent to server. 
     * 
     * @param messageIdentifier the unique message identifier that was created by the send message function.
     * @param stateCode the status code for this message
     * @param data optional, supplementary data for the current message state.
     * @param length length of the optional data array.
     */
    virtual void stateReportAxo(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length) = 0;

    /**
     * @brief Report SIP notify information.
     * 
     * The network layer calls this function to hand over information from SIP NOTIFY packets.
     *
     * @param data optional, supplementary data for the current message state.
     * @param length length of the optional data array.
     */
    virtual void notifyAxo(uint8_t* data, size_t length) = 0;

private:
};
} // namespace

/**
 * @}
 */
#endif // TRANSPORT_H
