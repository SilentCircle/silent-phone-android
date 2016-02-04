#ifndef PROVISIONING_H
#define PROVISIONING_H

/**
 * @file Provisioning.h
 * @brief Interface for the required provisioning server functions
 * @ingroup Axolotl++
 * @{
 */

#include <string>
#include <list>
#include <utility>

#include "../axolotl/crypto/DhPublicKey.h"
#include "../storage/sqlite/SQLiteStoreConv.h"

using namespace std;

namespace axolotl {
class Provisioning
{
public:
    ~Provisioning();

    /**
     * @brief Register a device for use with Axolotl
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
    static int32_t registerAxoDevice(const std::string& request, const std::string& authorization, const std::string& scClientDevId, std::string* result);

    /**
     * @brief Remove a Axolotl device from user's account.
     * 
     * @param name the user's name
     * @param scClientDevId the unique device id of one of the user's registered Axolotl devices
     * @param authorization autorization data, may be needed for some servers
     * @param result To store the result data of the server, usually in case of an error only
     */
    static int32_t removeAxoDevice(const string& scClientDevId, const string& authorization, std::string* result);


    /**
     * @brief Get a pre-key bundle for the user/device id
     * 
     * This function contacts the server to read one set of pre-key data for the user name/device id
     * combination 
     * 
     * @param name the user's name
     * @param longDevId the unique device id of one of the user's registered Axolotl devices
     * @param authorization autorization data, may be needed for some servers
     * @param preIdKeys the pair contains the returned pre key as first member, the identity of the remote
     *                  parte as the second member.
     * @return a pre-key id or @c 0 on failure
     */
    static int32_t getPreKeyBundle(const string& name, const string& longDevId, const string& authorization, 
                                   pair< const axolotl::DhPublicKey*, const axolotl::DhPublicKey* >* preIdKeys );
//    static int32_t getPreKeyBundle(const std::string& name, const std::string& longDevId, const std::string& authorization);

    /**
     * @brief Get number of available pre-keys on the server.
     * 
     * @param longDevId the SC device id
     * @param authorization autorization data, may be needed for some servers
     * @return number of available pre-keys, -1 if request to server failed.
     */
    static int32_t getNumPreKeys(const string& longDevId, const string& authorization);

    /**
     * @brief Get the availabe registered Axolotl device of a user
     * 
     * A user may register severval devices for Axolotl usage. A sender (Alice) should send messages to
     * all available devices of the other user (Bob). This keeps Bob's message display on his devices in
     * sync.
     * 
     * @param name username of the other user
     * @param authorization autorization data, may be needed for some servers
     * @return a list of available device ids (long device ids), @c NULL if the request to server failed.
     */
    static list<pair<string, string> >* getAxoDeviceIds(const std::string& name, const std::string& authorization);

    /**
     * @brief Set new pre-keys.
     * 
     * If the number of available pre-keys get to low then a slient should generate and publish
     * new pre-keys.The server appends the pre-keys to the remaining existing pre-keys.
     *
     * @param store The persitent Axolotl store to store and retrieve state information.
     * @param longDevId the unique device id of one of the user's registered Axolotl devices
     * @param authorization autorization data, required to identify the user/device for which to append
     *        the new pre-keys.
     * @param number How man pre-keys to add
     * @param result To store the result data of the server, usually in case of an error only
     * @return the server's request return code, e.g. 200 or 404 or alike.
     */
    static int32_t newPreKeys(SQLiteStoreConv* store, const string& longDevId, const string& authorization, int32_t number, string* result);

};
} // namespace

/**
 * @}
 */

#endif // PROVISIONING_H
