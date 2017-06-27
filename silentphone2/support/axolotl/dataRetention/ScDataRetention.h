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

#ifndef SCDATARETENTION_H
#define SCDATARETENTION_H

/**
 * @file ScDataRetention.h
 * @brief Implementation of the data retention interface for Silent Circle.
 * @ingroup Zina
 * @{
 */

#include <string>
#include <memory>
#include <time.h>

/* Macro to control whether data retention functionality is enabled or disabled.
   Use "#if defined(SC_ENABLE_DR)" to test.
*/
//#define SC_ENABLE_DR

typedef int32_t (*HTTP_FUNC)(const std::string& requestUri, const std::string& method, const std::string& requestData, std::string* response);

typedef int32_t (*S3_FUNC)(const std::string& requestUri, const std::string& requestData, std::string* response);

struct cJSON;

namespace zina {

/* Basic implementation of Maybe in C++ */
template <typename T>
class maybe {
private:
    bool valid_;
    T value_;

public:
    maybe() : valid_(false) { }
    maybe(T const& v) : value_(v), valid_(true) { }
    maybe<T>& operator=(T const& v) {
        valid_ = true;
        value_ = v;
        return *this;
    }

    operator T() {
        // If not valid, this results in the default constructor
        // of type T being returned.
        if (!valid_) {
            return T();
        }
        return value_;
    }

    void set_invalid() {
        valid_ = false;
        value_ = T();
    }

    bool is_valid() const {
        return valid_;
    }
};

/* Location data that can be attached to a message and may be
   stored as metadata */
struct DrLocationData {
    bool enabled_;
    maybe<double> latitude_;
    maybe<double> longitude_;
    maybe<int32_t> time_;
    maybe<double> altitude_;
    maybe<double> accuracy_horizontal_;
    maybe<double> accuracy_vertical_;

    DrLocationData() : enabled_(false) { }
    explicit DrLocationData(cJSON* json, bool detailed);
};

/* Attachment data that can be attached to a message and may be
   stored as metadata */
struct DrAttachmentData {
    bool attached_;
    maybe<std::string> content_type_;
    maybe<std::string> exported_filename_;
    maybe<std::string> filename_;
    maybe<std::string> display_name_;
    maybe<std::string> sha256_;
    maybe<long> file_size_;

    DrAttachmentData() : attached_(false) { }
    explicit DrAttachmentData(cJSON* json, bool detailed);
};

#if defined(SC_ENABLE_DR)
/* The request classes are only compiled if data retention is enabled. This makes
   accidental use of DR in a disabled build a compile error.
*/
class DrRequest {
private:
    std::string authorization_;
protected:
    HTTP_FUNC httpHelper_;
    S3_FUNC s3Helper_;

    struct MessageMetadata {
      std::string url;
      std::string callid;
      std::string src_uuid;
      std::string src_alias;
      std::string dst_uuid;
      std::string dst_alias;
    };

    /**
     * @brief Requests a presigned Amazon S3 URL and other associated metadata for the user.
     *
     * This uses httpHelper to make a request to the Data Retention Broker.
     *
     * @param url_suffix The suffix of the presigned URL (eg. "event.json", "audio.opus", etc)
     * @param callid The callid of the message or call.
     * @param recipient The userid of the recipient of the message.
     * @param startTime The start time of the message or call.
     * @param metadata The data returned by the data retention broker.
     * @return zero on success, -1 for a failure that can be retried, -2 for a failure that cannot be retried.
     */
    int getPresignedUrl(const std::string& url_suffix,
                        const std::string& callid,
                        const std::string& recipient,
                        time_t startTime,
                        MessageMetadata* metadata);

public:
    /**
     * @brief Base constructor for a Data Retention request
     *
     * @param httpHelper HTTP helper function used to make HTTP requests.
     * @param s3Helper S3 helper function used to post data to Amazon S3.
     * @param authorization API Key for making AW requests.
     */
    explicit DrRequest(HTTP_FUNC httpHelper, S3_FUNC s3Helper, const std::string& authorization);
    virtual ~DrRequest() { }

    /**
     * @brief Convert request to a serialized JSON format for storage in pending events database.
     *
     * @return Serialized request in a JSON string.
     */
    virtual std::string toJSON() = 0;

    /**
     * @brief Run the request. Makes HTTP requests via HTTP helper and S3 helper.
     *
     * @return true if the request should be removed from the queue, false if it should remain to be retried later.
     */
    virtual bool run() = 0;

    DrRequest(DrRequest const&); // = delete;
    void operator=(DrRequest const&); // = delete;
};

class MessageRequest : public DrRequest {
private:
    std::string callid_;
    std::string direction_;
    std::string recipient_;
    time_t composed_;
    time_t sent_;
    std::string message_;

public:
    /**
     * @brief Construct a Message data retention request
     *
     * @param httpHelper HTTP helper function used to make HTTP requests.
     * @param s3Helper S3 helper function used to post data to Amazon S3.
     * @param authorization API Key for making AW requests.
     * @param callid Callid for the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param recipient Userid of the recipient of the message.
     * @param composed Time that the message was composed.
     * @param sent Time that the message was sent.
     * @param message Plain text of sent message.
     */
    MessageRequest(HTTP_FUNC httpHelper,
                   S3_FUNC s3Helper,
                   const std::string& authorization,
                   const std::string& callid,
                   const std::string& direction,
                   const std::string& recipient,
                   time_t composed,
                   time_t sent,
                   const std::string& message);
    MessageRequest(HTTP_FUNC httpHelper, S3_FUNC s3Helper, const std::string& authorization, cJSON* json);
    virtual std::string toJSON() override;
    virtual bool run() override;
};


class MessageMetadataRequest : public DrRequest {
private:
    std::string callid_;
    std::string direction_;
    DrLocationData location_;
    DrAttachmentData attachment_;
    std::string recipient_;
    time_t composed_;
    time_t sent_;

public:
    /**
     * @brief Construct a Message metadata data retention request
     *
     * @param httpHelper HTTP helper function used to make HTTP requests.
     * @param s3Helper S3 helper function used to post data to Amazon S3.
     * @param authorization API Key for making AW requests.
     * @param callid Callid for the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param location The location attribute details.
     * @param attachment The attachment details.
     * @param recipient Userid of the recipient of the message.
     * @param composed Time that the message was composed.
     * @param sent Time that the message was sent.
     */
    MessageMetadataRequest(HTTP_FUNC httpHelper,
                           S3_FUNC s3Helper,
                           const std::string& authorization,
                           const std::string& callid,
                           const std::string& direction,
                           const DrLocationData& location,
                           const DrAttachmentData& attachment,
                           const std::string& recipient,
                           time_t composed,
                           time_t sent);
    MessageMetadataRequest(HTTP_FUNC httpHelper, S3_FUNC s3Helper, const std::string& authorization, cJSON* json);
    virtual std::string toJSON() override;
    virtual bool run() override;
};

class InCircleCallMetadataRequest : public DrRequest {
private:
    std::string callid_;
    std::string direction_;
    std::string recipient_;
    time_t start_;
    time_t end_;

public:
    /**
     * @brief Construct an in circle call data retention request
     *
     * @param httpHelper HTTP helper function used to make HTTP requests.
     * @param s3Helper S3 helper function used to post data to Amazon S3.
     * @param authorization API Key for making AW requests.
     * @param callid Callid for the message.
     * @param direction "placed" or "received" indicating direction of call.
     * @param recipient Userid of the recipient of the call.
     * @param start Time that the call started.
     * @param end Time that the call ended.
     */
    InCircleCallMetadataRequest(HTTP_FUNC httpHelper,
                                S3_FUNC s3Helper,
                                const std::string& authorization_,
                                const std::string& callid,
                                const std::string direction,
                                const std::string recipient,
                                time_t start,
                                time_t end);
    InCircleCallMetadataRequest(HTTP_FUNC httpHelper, S3_FUNC s3Helper, const std::string& authorization, cJSON* json);
    virtual std::string toJSON() override;
    virtual bool run() override;
};

class SilentWorldCallMetadataRequest : public DrRequest {
private:
    std::string callid_;
    std::string direction_;
    std::string srctn_;
    std::string dsttn_;
    time_t start_;
    time_t end_;

public:
    /**
     * @brief Construct a Silent World call data retention request
     *
     * @param httpHelper HTTP helper function used to make HTTP requests.
     * @param s3Helper S3 helper function used to post data to Amazon S3.
     * @param authorization API Key for making AW requests.
     * @param callid Callid for the message.
     * @param direction "placed" or "received" indicating direction of call.
     * @param srctn PSTN number for source or empty for none.
     * @param dsttn PSTN number for destination.
     * @param start Time that the call started.
     * @param end Time that the call ended.
     */
    SilentWorldCallMetadataRequest(HTTP_FUNC httpHelper,
                                   S3_FUNC s3Helper,
                                   const std::string& authorization_,
                                   const std::string& callid,
                                   const std::string direction,
                                   const std::string srctn,
                                   const std::string dsttn,
                                   time_t start,
                                   time_t end);
    SilentWorldCallMetadataRequest(HTTP_FUNC httpHelper, S3_FUNC s3Helper, const std::string& authorization, cJSON* json);
    virtual std::string toJSON() override;
    virtual bool run() override;
};
#endif

class ScDataRetention
{
public:
    /**
     * @brief Initialization code must set a HTTP helper function
     *
     * @param httpHelper Pointer to the helper function
     */
    static void setHttpHelper(HTTP_FUNC httpHelper);

    /**
     * @brief Initialization code must set a S3 helper function
     *
     * @param s3Helper Pointer to the helper function
     */
    static void setS3Helper(S3_FUNC s3Helper);

    /**
     * @brief Initialization code must set the API Key for AW calls.
     *
     * @param authorization API key for AW calls.
     */
    static void setAuthorization(const std::string& authorization);

private:
    /**
     * @brief function pointer to the HTTP helper function
     *
     * This is a blocking function and returns after the server answered the HTTP request.
     * The @c requestUri includes the protocol specifier, e.g. HTTP or HTTPS, and 
     * the domain name. The helper function should not add a protocol or domain as it
     * usually does for internal AW requests if one is already provided.
     *
     * @param requestUri This is the request URL.
     * @param method the method to use, for example PUT or GET
     * @param requestData This is data for the request, JSON string, not every request has data
     * @param response This string receives the response, usually a JSON formatted string
     * @return the request return code, usually a HTTP code like 200 or something like that.
     */
    static HTTP_FUNC httpHelper_;
    static S3_FUNC s3Helper_;

    static std::string authorization_;

public:
    ScDataRetention() {}
    ~ScDataRetention() {}

#if defined(SC_ENABLE_DR)
    /**
     * @brief Convert a serialized request in JSON format back to a DrRequest object
     *
     * @param json A serialized DrRequest object in JSON format.
     * @return The DrRequest object deserialized from JSON.
     */
    static DrRequest* requestFromJSON(const std::string& json);
#endif

    /**
     * @brief Store message data in the customers Amazon S3 bucket.
     *
     * If the request fails it is stored in a sqlite table and will be retried
     * on the next message or call send.
     *
     * @param callid Callid for the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param recipient Userid of the recipient of the message.
     * @param composed Time that the message was composed.
     * @param sent Time that the message was sent.
     * @param message Plain text of message.
     */
    static void sendMessageData(const std::string& callid,
                                const std::string& direction,
                                const std::string& recipient,
                                time_t composed,
                                time_t sent,
                                const std::string& message);
    /**
     * @brief Store a message data retention event in the customers Amazon S3 bucket.
     *
     * If the request fails it is stored in a sqlite table and will be retried
     * on the next message or call send.
     *
     * @param callid Callid for the message.
     * @param direction The direction of the message. "sent" or "received".
     * @param location The location attribute details.
     * @param attachment The attachment details.
     * @param recipient Userid of the recipient of the message.
     * @param composed Time that the message was composed.
     * @param sent Time that the message was sent.
     */
    static void sendMessageMetadata(const std::string& callid,
                                    const std::string& direction,
                                    const DrLocationData& location,
                                    const DrAttachmentData& attachment,
                                    const std::string& recipient,
                                    time_t composed,
                                    time_t sent);
    /**
     * @brief Store an in circle call data retention event in the customers Amazon S3 bucket.
     *
     * If the request fails it is stored in a sqlite table and will be retried
     * on the next message or call send.
     *
     * @param callid Callid for the message.
     * @param direction "placed" or "received" indicating direction of call.
     * @param recipient Userid of the recipient of the call.
     * @param start Time that the call started.
     * @param end Time that the call ended.
     */
    static void sendInCircleCallMetadata(const std::string& callid,
                                         const std::string& direction,
                                         const std::string& recipient,
                                         time_t start,
                                         time_t end);

    /**
     * @brief Store a Silent World call data retention event in the customers Amazon S3 bucket.
     *
     * If the request fails it is stored in a sqlite table and will be retried
     * on the next message or call send.
     *
     * @param callid Callid for the message.
     * @param direction "placed" or "received" indicating direction of call.
     * @param srctn PSTN number for source or empty for none.
     * @param dsttn PSTN number for destination.
     * @param start Time that the call started.
     * @param end Time that the call ended.
     */
    static void sendSilentWorldCallMetadata(const std::string& callid,
                                            const std::string& direction,
                                            const std::string& srctn,
                                            const std::string& dsttn,
                                            time_t start,
                                            time_t end);
    /**
     * @brief Run all stored pending data retention requests.
     *
     * Iterates over stored pending requests and executes them. They
     * are removed from the pending request table if they succeed. If
     * a request fails then none of the remaining pending requests are
     * executed. They'll be retried next time a message or call happens.
     *
     * This is run after any message or call data retention request is
     * made. It can also be called by a client to send outstanding
     * requests on resumption of network connection or startup.
     */
    static void processRequests();

    /**
     * @brief Get status of whether the user has data retention enabled on their account.
     *
     * This will make an HTTP request using httpHelper to the Data Retention broker.
     *
     * @param enabled Will contain true or false depending if data retention is enabled
     *        on the account. If the request fails then it is unchanged.
     * @return HTTP status code of AW request to determine if data retention is enabled.
     */
    static int isEnabled(bool* enabled);

    /**
     * @brief Get status of whether a specific user has data retention enabled on their account.
     *
     * This will make an HTTP request using httpHelper to the Data Retention broker.
     *
     * @param user The alias of the user to be checked.
     * @param enabled Will contain true or false depending if data retention is enabled
     *        on the account. If the request fails then it is unchanged.
     * @return HTTP status code of AW request to determine if data retention is enabled.
     */
    static int isEnabled(const std::string& user, bool* enabled);

    ScDataRetention(const ScDataRetention& other)  = delete;
    ScDataRetention& operator=(const ScDataRetention& other)  = delete;
    bool operator==(const ScDataRetention& other) const = delete;

};
} // namespace

/**
 * @}
 */

#endif // SCDATARETENTION_H
