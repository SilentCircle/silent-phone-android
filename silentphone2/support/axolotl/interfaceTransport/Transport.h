#ifndef TRANSPORT_H
#define TRANSPORT_H

/**
 * @file Transport.h
 * @brief Interface for tnetwork transport functions
 * @ingroup Axolotl++
 * @{
 */

#include <stdint.h>
#include <utility>
#include <vector>
#include <string>


//void sendDataFunc(uint8_t* names[], uint8_t* recipientScClientDevIds[], uint8_t* data[], size_t length[], uint64_t msgIds[]);
typedef void (*SEND_DATA_FUNC)(uint8_t* [], uint8_t* [], uint8_t* [], size_t [], uint64_t []);

namespace axolotl{
class Transport
{
public:
    virtual ~Transport() {}

        /**
     * @brief Set the function that actually sends data.
     * 
     * Ownership stays with caller.
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
     * @param recipient The receipient's name.
     * @param msgPairs a vector of message pairs. The first element in each pair contains the long device id
     *                 of one of the recipient's device. The name/device id identifies a unique device. The second
     *                 element of the pair is the message envelope to send.
     * @return a vector of int64_t unique message ids, one id for each message sent.
     */
    virtual std::vector<int64_t>* sendAxoMessage(const std::string& recipient, std::vector< std::pair< std::string, std::string > >* msgPairs) = 0;

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
