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
#ifndef PROVISIONING_H
#define PROVISIONING_H

/**
 * @file Provisioning.h
 * @brief Interface for the required provisioning server functions
 * @ingroup ZINA
 * @{
 */

#include <string>
#include <list>
#include <utility>

#include "../ratchet/crypto/DhPublicKey.h"
#include "../storage/sqlite/SQLiteStoreConv.h"

namespace zina {
class Provisioning
{
public:
    virtual ~Provisioning();

    /**
     * @brief Register a device for use with ZINA
     * 
     * This functions is just a forwarder that takes a JSON string, sends it via httpHelper to
     * the server and returns the server's result.
     * 
     * @param jsonString The JSON string to register the device.
     * @param authorization autorization data, may be needed for some servers
     * @param scClientDevId the same string as used to register the device (v1/me/device/{device_id}/)
     * @param result To store the result data of the server, usually in case of an error only
     * @return the server's request return code, e.g. 200 or 404 or alike.
     */
    static int32_t registerZinaDevice(const std::string& request, const std::string& authorization, const std::string& scClientDevId, std::string* result);

    /**
     * @brief Remove a ZINA device from user's account.
     * 
     * @param scClientDevId the unique device id of one of the user's registered ZINA devices
     * @param authorization autorization data, may be needed for some servers
     * @param result To store the result data of the server, usually in case of an error only
     */
    static int32_t removeZinaDevice(const std::string& scClientDevId, const std::string& authorization, std::string* result);

    /**
     * @brief Get a pre-key bundle for the user/device id
     * 
     * This function contacts the server to read one set of pre-key data for the user name/device id
     * combination 
     * 
     * @param name the user's name
     * @param longDevId the unique device id of one of the user's registered ZINA devices
     * @param authorization autorization data, may be needed for some servers
     * @param preIdKeys the pair contains the returned pre key as first member, the identity of the remote
     *                  parte as the second member.
     * @return a pre-key id or @c 0 on failure
     */
    static int32_t getPreKeyBundle(const std::string& name, const std::string& longDevId, const std::string& authorization,
                                   std::pair<PublicKeyUnique, PublicKeyUnique>* preIdKeys );

    /**
     * @brief Get number of available pre-keys on the server.
     * 
     * @param longDevId the SC device id
     * @param authorization autorization data, may be needed for some servers
     * @return number of available pre-keys, -1 if request to server failed.
     */
    static int32_t getNumPreKeys(const std::string& longDevId, const std::string& authorization);

    /**
     * @brief Get the availabe registered ZINA device of a user
     * 
     * A user may register severval devices for ZINA usage. A sender (Alice) should send messages to
     * all available devices of the other user (Bob). This keeps Bob's message display on his devices in
     * sync.
     *
     * @deprecated Use int32_t getZinaDeviceIds(const std::string&, const std::string&, list<pair<string, string> > *) instead.
     *
     * @param name username of the other user
     * @param authorization authorization data, may be needed for some servers
     * @param errorCode If not null then it's set to the return code
     * @return a list of available device ids (long device ids), @c NULL if the request to server failed.
     */
    DEPRECATED_ZINA static std::shared_ptr<std::list<std::pair<std::string, std::string> > >
    getZinaDeviceIds(const std::string& name, const std::string& authorization, int32_t* errorCode = NULL);

    /**
     * @brief Get the availabe registered ZINA device of a user
     *
     * A user may register severval devices for ZINA usage. A sender (Alice) should send messages to
     * all available devices of the other user (Bob). This keeps Bob's message display on his devices in
     * sync.
     *
     * @param name username of the other user
     * @param authorization authorization data, may be needed for some servers
     * @param deviceIds List of device ids, output
     * @return a list of available device ids (long device ids), @c NULL if the request to server failed.
     */
    static int32_t getZinaDeviceIds(const std::string& name, const std::string& authorization,
                                    std::list<std::pair<std::string, std::string> > &deviceIds);

    /**
     * @brief Set new pre-keys.
     * 
     * If the number of available pre-keys get to low then a slient should generate and publish
     * new pre-keys.The server appends the pre-keys to the remaining existing pre-keys.
     *
     * @param store The persitent ZINA store to store and retrieve state information.
     * @param longDevId the unique device id of one of the user's registered ZINA devices
     * @param authorization autorization data, required to identify the user/device for which to append
     *        the new pre-keys.
     * @param number How man pre-keys to add
     * @param result To store the result data of the server, usually in case of an error only
     * @return the server's request return code, e.g. 200 or 404 or alike.
     */
    static int32_t newPreKeys(SQLiteStoreConv* store, const std::string& longDevId, const std::string& authorization,
                              int32_t number, std::string* result);

    static int32_t getUserInfo(const std::string& alias, const std::string& authorization, std::string* result);
};
} // namespace

/**
 * @}
 */

#endif // PROVISIONING_H
