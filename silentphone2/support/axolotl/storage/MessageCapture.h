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

//
// Created by werner on 05.05.16.
//

#ifndef LIBAXOLOTL_MESSAGECAPTURE_H
#define LIBAXOLOTL_MESSAGECAPTURE_H

/**
 * @file MessageCapture.h
 * @brief Capture message flow
 * @ingroup Zina
 * @{
 *
 * This class contains functions to store and retrieve message trace data.
 * The functions store a subset of message data to provide information about
 * the state of a message, for example if a receiver of a message sent a
 * delivery report or if the client sent a read-receipt for a message.
 *
 * The functions do not store any message content or sensitive data such as
 * location information.
 */

#include <string>
#include <list>
#include <memory>
#include "sqlite/SQLiteStoreConv.h"

class MessageCapture {

public:
    /**
     * @brief Capture received message trace data.
     *
     * @param sender The message sender's name (SC uid)
     * @param messageId The UUID of the message
     * @param deviceId The sender's device id
     * @param convState the relevant data of the ratchet state
     * @param attribute The message attribute string which contains status information
     * @param attachments If set the message contained an attachment descriptor
     * @param force store the data even in case the debug level is less than INFO to log error condition
     * @return SQLite code
     */
    static int32_t captureReceivedMessage(const std::string& sender, const std::string& messageId, const std::string& deviceId,
                                          const std::string &convState, const std::string& attributes, bool attachments,
                                          zina::SQLiteStoreConv &store);

    /**
     * @brief Capture send message trace data.
     *
     * @param receiver The message receiver's name (SC uid)
     * @param deviceId The sender's device id
     * @param deviceId The receiver's device id
     * @param convState the relevant data of the ratchet state
     * @param attribute The message attribute string which contains status information
     * @param attachments If set the message contained an attachment descriptor
     * @param force store the data even in case the debug level is less than INFO to log error condition
     * @return SQLite code
     */
    static int32_t captureSendMessage(const std::string& receiver, const std::string& messageId, const std::string& deviceId,
                                      const std::string &convState, const std::string& attributes, bool attachments,
                                      zina::SQLiteStoreConv &store);

    /**
     * @brief Return a list of message trace records.
     *
     * The function selects and returns a list of JSON formatted message trace records, ordered by the
     * sequence of record insertion. The function supports the following selections:
     * <ul>
     * <li>@c name contains data, @c messageId and @c deviceId are empty: return all message trace records
     *     for this name</li>
     * <li>@c messageId contains data, @c name and @c deviceId are empty: return all message trace records
     *     for this messageId</li>
     * <li>@c deviceId contains data, @c name and @c messageId are empty: return all message trace records
     *     for this deviceId</li>
     * <li>@c messageId and @c deviceId contain data, @c name is empty: return all message trace records
     *     that match the messageId AND deviceId</li>
     * </ul>
     * @param name The message sender's/receiver's name (SC uid)
     * @param messageId The UUID of the message
     * @param deviceId The sender's device id
     * @param traceRecords list of trace records
     * @return SQLite code
     */
    static int32_t loadCapturedMsgs(const std::string& name, const std::string& messageId, const std::string& deviceId,
                                    zina::SQLiteStoreConv &store, std::list<StringUnique> &traceRecords);
};


/**
 * @}
 */
#endif //LIBAXOLOTL_MESSAGECAPTURE_H
