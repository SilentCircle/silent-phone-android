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
#ifndef SIPTRANSPORT_H
#define SIPTRANSPORT_H

/**
 * @file SipTransport.h
 * @brief Implementation for network transport functions, SIP transport
 * @ingroup Zina
 * @{
 */

#include <utility>
#include <vector>
#include <string>

#include "../Transport.h"
#include "../../interfaceApp/AppInterface.h"
#include "../../interfaceApp/AppInterfaceImpl.h"

static const char* scSipDomain = "@sip.silentcircle.net";


namespace zina {

class SipTransport: public Transport
{
public:
    explicit SipTransport(AppInterface* appInterface) : appInterface_(appInterface), sendAxoData_(nullptr) {}

    ~SipTransport() override;

    void setSendDataFunction(SEND_DATA_FUNC sendData) override { sendAxoData_ = sendData; }

    SEND_DATA_FUNC getTransport() override { return sendAxoData_; }

    void sendAxoMessage(const CmdQueueInfo &info, const std::string& envelope) override;

    int32_t receiveAxoMessage(uint8_t* data, size_t length) override;

    int32_t receiveAxoMessage(uint8_t* data, size_t length, uint8_t* uid,  size_t uidLen,
                              uint8_t* primaryAlias, size_t aliasLen) override;

    void stateReportAxo(int64_t messageIdentifier, int32_t stateCode, uint8_t* data, size_t length) override;

    void notifyAxo(const uint8_t* data, size_t length) override;

    SipTransport(const SipTransport& other) = delete;
    SipTransport(const SipTransport&& other) = delete;
    SipTransport& operator= ( const SipTransport& other ) = delete;
    SipTransport& operator= ( const SipTransport&& other ) = delete;

private:
    AppInterface *appInterface_;
    SEND_DATA_FUNC sendAxoData_;
};
}
/**
 * @}
 */

#endif // SIPTRANSPORT_H
