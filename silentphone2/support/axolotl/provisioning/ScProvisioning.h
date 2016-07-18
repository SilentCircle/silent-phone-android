#ifndef SCPROVISIONING_H
#define SCPROVISIONING_H

/**
 * @file ScProvisioning.h
 * @brief Implementation of the provisioning interface for Silent Circle provisioning server.
 * @ingroup Axolotl++
 * @{
 */

#include <string>
#include "Provisioning.h"

// The fixed strings used in the SC implementation to form the request URLs
// /v1/<user>/axolotl/prekey/<scClientDevId>/?api_key=<API_key> - GET

static const std::string GET("GET");
static const std::string PUT("PUT");
static const std::string DELETE("DELETE");

typedef int32_t (*HTTP_FUNC)(const std::string& requestUri, const std::string& method, const std::string& requestData, std::string* response);

namespace axolotl {
class ScProvisioning : public Provisioning
{
public:
    /**
     * @brief Initialization code must set a HTTP helper function
     * 
     * @param httpHelper Pointer to the helper functions
     */
    static void setHttpHelper(HTTP_FUNC httpHelper);

private:
    friend class Provisioning;
    /**
     * @brief functions pointer to the HTTP helper function
     * 
     * This is a blocking funtion and returns after the server answered the HTTP request.
     * The @c requestURL does not include the protocol specifier, e.g. HTTP or HTTPS, and 
     * not the domain name. The helper function adds these to form a valid address.
     * 
     * For the SC implementation a request URL looks like this: 
     @verbatim
     /v1/<user>/axolotl/prekey/<scClientDevId>/?api_key=<API_key>
     @endverbatim
     *
     * @param requestUri This is the request URL.
     * @param method the method to use, for example PUT or GET
     * @param requestData This is data for the request, JSON string, not every request has data
     * @param response This string receives the response, usually a JSON formatted string
     * @return the request return code, usually a HTTP code like 200 or something like that.
     */
    static HTTP_FUNC httpHelper_;

    ScProvisioning() {}
    ~ScProvisioning() {}

    ScProvisioning(const ScProvisioning& other)  = delete;
    ScProvisioning& operator=(const ScProvisioning& other)  = delete;
    bool operator==(const ScProvisioning& other) const = delete;

};
} // namespace

/**
 * @}
 */

#endif // SCPROVISIONING_H
