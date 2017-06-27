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

// Functions and data to handle the Run-Q
//
// Created by werner on 29.08.16.
//

#include <condition_variable>
#include <thread>

#include "AppInterfaceImpl.h"

using namespace std;
using namespace zina;

static mutex commandQueueLock;
static list<unique_ptr<CmdQueueInfo> > commandQueue;

static mutex threadLock;
static condition_variable commandQueueCv;
static thread commandQueueThread;

static bool cmdThreadRunning = false;
static bool cmdRun;

#ifdef UNITTESTS
static AppInterfaceImpl* testIf_;
void setTestIfObj_(AppInterfaceImpl* obj)
{
    testIf_ = obj;
}
#endif

void AppInterfaceImpl::checkStartRunThread()
{
    if (!cmdThreadRunning) {
        unique_lock<mutex> lck(threadLock);
        if (!cmdThreadRunning) {
            cmdRun = true;
            commandQueueThread = thread(commandQueueHandler, this);
            cmdThreadRunning = true;
        }
        lck.unlock();
    }
}

void AppInterfaceImpl::addMsgInfoToRunQueue(unique_ptr<CmdQueueInfo> messageToProcess)
{
    checkStartRunThread();

    unique_lock<mutex> listLock(commandQueueLock);
    commandQueue.push_back(move(messageToProcess));
    commandQueueCv.notify_one();

    listLock.unlock();
}

void AppInterfaceImpl::addMsgInfosToRunQueue(list<unique_ptr<CmdQueueInfo> >& messagesToProcess)
{
    checkStartRunThread();

    unique_lock<mutex> listLock(commandQueueLock);
    commandQueue.splice(commandQueue.end(), messagesToProcess);
    commandQueueCv.notify_one();

    listLock.unlock();
}


// process prepared send messages, one at a time
void AppInterfaceImpl::commandQueueHandler(AppInterfaceImpl *obj)
{
    LOGGER(DEBUGGING, __func__, " -->");

    unique_lock<mutex> listLock(commandQueueLock);
    while (cmdRun) {
        while (commandQueue.empty()) commandQueueCv.wait(listLock);

        for (; !commandQueue.empty(); commandQueue.pop_front()) {
            auto& cmdInfo = commandQueue.front();
            listLock.unlock();

            int32_t result;
            switch (cmdInfo->command) {
                case SendMessage: {
#ifndef UNITTESTS
                    result = cmdInfo->queueInfo_newUserDevice ? obj->sendMessageNewUser(*cmdInfo) : obj->sendMessageExisting(*cmdInfo);
                    if (result != SUCCESS) {
                        if (obj->stateReportCallback_ != nullptr) {
                            obj->stateReportCallback_(cmdInfo->queueInfo_transportMsgId, result, createSendErrorJson(*cmdInfo, result));
                        }
                        LOGGER(ERROR, __func__, " Failed to send a message, error code: ", result);
                    }
                    if (cmdInfo->queueInfo_callbackAction != NoAction) {
                        obj->sendActionCallback(static_cast<SendCallbackAction>(cmdInfo->queueInfo_callbackAction));
                    }
#else
                    result = cmdInfo->queueInfo_newUserDevice ? testIf_->sendMessageNewUser(*cmdInfo)
                                                              : testIf_->sendMessageExisting(*cmdInfo);
                    if (result != SUCCESS) {
                        if (testIf_->stateReportCallback_ != nullptr) {
                            testIf_->stateReportCallback_(cmdInfo->queueInfo_transportMsgId, result,
                                                          createSendErrorJson(*cmdInfo, result));
                        }
                        LOGGER(ERROR, __func__, " Failed to send a message, error code: ", result);
                    }
                    if (cmdInfo->queueInfo_callbackAction != NoAction) {
                        testIf_->sendActionCallback(static_cast<SendCallbackAction>(cmdInfo->queueInfo_callbackAction));
                    }
#endif
                }
                break;
                case ReceivedRawData:
#ifndef UNITTESTS
                    obj->processMessageRaw(*cmdInfo);
#else
                    testIf_->processMessageRaw(*cmdInfo);
#endif
                    break;

                case ReceivedTempMsg:
                    obj->processMessagePlain(*cmdInfo);
                    break;

                case CheckRemoteIdKey:
                    obj->checkRemoteIdKeyCommand(*cmdInfo);
                    break;

                case SetIdKeyChangeFlag:
                    obj->setIdKeyVerifiedCommand(*cmdInfo);
                    break;

                case ReKeyDevice:
                    obj->reKeyDeviceCommand(*cmdInfo);
                    break;

                case ReScanUserDevices:
                    obj->rescanUserDevicesCommand(*cmdInfo);
                    break;

                case CheckForRetry:

                    break;
            }
            listLock.lock();
        }
    }
    LOGGER(DEBUGGING, __func__, " <--");
}

shared_ptr<vector<uint64_t> >
AppInterfaceImpl::extractTransportIds(list<unique_ptr<PreparedMessageData> >* data)
{
    auto ids = make_shared<vector<uint64_t> >();

    for (auto it = data->cbegin(); it != data->cend(); ++it) {
        uint64_t id = (*it)->transportId;
        ids->push_back(id);
    }
    return ids;
}

void AppInterfaceImpl::insertRetryCommand()
{
    auto retryCommand = unique_ptr<CmdQueueInfo>(new CmdQueueInfo);
    retryCommand->command = CheckForRetry;
    addMsgInfoToRunQueue(move(retryCommand));
}

void AppInterfaceImpl::retryReceivedMessages()
{
    LOGGER(DEBUGGING, __func__, " -->");
    list<unique_ptr<CmdQueueInfo> > messagesToProcess;
    int32_t plainCounter = 0;
    int32_t rawCounter = 0;

    list<unique_ptr<StoredMsgInfo> > storedMsgInfos;
    int32_t result = store_->loadTempMsg(&storedMsgInfos);

    if (!SQL_FAIL(result)) {
        for (; !storedMsgInfos.empty(); storedMsgInfos.pop_front()) {
            auto& storedInfo = storedMsgInfos.front();
            auto plainMsgInfo = new CmdQueueInfo;

            plainMsgInfo->command = ReceivedTempMsg;
            plainMsgInfo->queueInfo_sequence = storedInfo->sequence;
            plainMsgInfo->queueInfo_message_desc = storedInfo->info_msgDescriptor;
            plainMsgInfo->queueInfo_supplement = storedInfo->info_supplementary;
            plainMsgInfo->queueInfo_msgType = storedInfo->info_msgType;

            sendDeliveryReceipt(*plainMsgInfo);
            messagesToProcess.push_back(unique_ptr<CmdQueueInfo>(plainMsgInfo));
            plainCounter++;
        }
    }
    result = store_->loadReceivedRawData(&storedMsgInfos);
    if (!SQL_FAIL(result)) {
        for (; !storedMsgInfos.empty(); storedMsgInfos.pop_front()) {
            auto& storedInfo = storedMsgInfos.front();
            auto rawMsgInfo = new CmdQueueInfo;

            rawMsgInfo->command = ReceivedRawData;
            rawMsgInfo->queueInfo_sequence = storedInfo->sequence;
            rawMsgInfo->queueInfo_envelope = storedInfo->info_rawMsgData;
            rawMsgInfo->queueInfo_uid = storedInfo->info_uid;
            rawMsgInfo->queueInfo_displayName = storedInfo->info_displayName;

            messagesToProcess.push_back(unique_ptr<CmdQueueInfo>(rawMsgInfo));
            rawCounter++;
        }
    }
    if (!messagesToProcess.empty()) {
        addMsgInfosToRunQueue(messagesToProcess);
        LOGGER(WARNING, __func__, " Queued messages for retry, plain: ", plainCounter, ", raw: ", rawCounter);
    }
    LOGGER(DEBUGGING, __func__, " <--");
}
