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
#ifndef APPREPOSITORY_H
#define APPREPOSITORY_H

/**
 * @file AppRepository.h
 * @brief Implementation of a repository for a messaging application.
 * @ingroup Zina
 * @{
 */

#include <string>
#include <stdint.h>
#include <list>
#include <vector>

#include <sqlcipher/sqlite3.h>

#define DB_CACHE_ERR_BUFF_SIZE  1000
#define OUR_KEY_LENGTH          32

#define SQL_FAIL(code) ((code) > SQLITE_OK && (code) < SQLITE_ROW)

namespace zina {

class AppRepository 
{
public:
    /**
     * @brief Get the app repository store instance.
     * 
     * The app repository is a singleton and this call returns the instance.
     * Use @c isReady to check if this store is ready for use.
     * 
     * @return Either a new or an already open app repository.
     */
    static AppRepository* getStore();

    /**
     * @brief Close the Axolotl store instance.
     */
    static void closeStore() { delete instance_; instance_ = NULL;}

#ifdef UNITTESTS
    static AppRepository* getStoreForTesting() {return new AppRepository(); }
    static AppRepository* closeStoreForTesting(AppRepository* store) {delete store; }
#endif

    /**
     * @brief Open app repository.
     * 
     * @param filename Filename of the database, including path.
     * @return an SQLite code
     */
    int openStore(const std::string& filename);

    /**
     * @brief Set key to use for SQLCipher.
     * 
     * The length of the key must be 32 bytes
     * 
     * @param keyData a @cstd::string container with the key data
     * @return @c true is key is OK, @c false otherwise.
     */
    bool setKey(const std::string& keyData) {if (keyData.size() != OUR_KEY_LENGTH) return false; keyData_ = new std::string(keyData); return true; }

    /**
     * @brief Get the last SQLite error message.
     * 
     * If a functions returns an error code or if the stored SQLite code is
     * not equal @c SQLITE_OK then this function returns a pointer to the last
     * SQLite error message.
     *
     * This functions not thread safe, use it for unit testing, single threaded.
     * 
     * @return pointer to SQLite error message.
     */
    const char* getLastError() {return lastError_;}

    /**
     * @brief Return the SQLite code of the last SQLite function.
     * 
     * Many functions internally use SQLite which may return an SQLite error.
     * In this case the functions store the SQLite code and the caller can
     * check if all operations were successful.
     *
     * This functions not thread safe, use it for unit testing, single threaded.
     *
     * @return last seen SQLite error code.
     */
    int32_t getSqlCode() const {return sqlCode_;}

    /**
     * @brief Store serialized conversation data.
     * 
     * @param name The conversation partner's name
     * @param conversation The serialized data of the conversation data structure
     * @return An SQLite code.
     */
    int32_t storeConversation(const std::string& name, const std::string& conversation);

    /**
     * @brief Load and return serialized conversation data.
     * 
     * @param name The conversation partner's name
     * @param conversation The serialized data of the conversation data structure
     * @return An SQLITE code.
     */
    int32_t loadConversation(const std::string& name, std::string* const conversation) const;

    /**
     * @brief Checks if a conversation for the name exists.
     *
     * @param name Name of the conversation
     * @return @c true if the conversation exists, @c false if not.
     */
    bool existConversation(const std::string& name, int32_t* const sqlCode = NULL);

    /**
     * @brief Delete serialized conversation data.
     * 
     * The database enforces the restriction that the application can delete the conversation
     * only if no events/messages are stored for this conversation. The function return 
     * the SQLite error code 19, 'Abort due to constraint violation' in this case.
     * 
     * @param name The conversation partner's name
     * @return An SQLite code.
     */
    int32_t deleteConversation(const std::string& name);

    /**
     * @brief Return a list of names for all known conversations.
     * 
     * @return A list of names for conversations, @c NULL in case of an error.
     */
    std::list<std::string>* listConversations(int32_t* const sqlCode = NULL) const;

    /**
     * @brief Insert serialized event/message data.
     * 
     * The functions inserts the event/message data and assignes a sequence number to this
     * record. The sequence number is unique inside the set of messages of a conversation.
     *
     * The functions returns and error in case a record with the same event id for this
     * conversation already exists.
     * 
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param event The serialized data of the event data structure
     * @return A SQLite code.
     */
    int32_t insertEvent(const std::string& name, const std::string& eventId, const std::string& event);

    /**
     * @brief Update serialized event/message data.
     * 
     * The functions updates the event/message data.
     * 
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param event The serialized data of the event data structure
     * @return A SQLite code.
     */
    int32_t updateEvent(const std::string& name, const std::string& eventId, const std::string& event);

    /**
     * @brief Get the highest event/message sequence number.
     * 
     * @param name The conversation partner's name
     * @return the highest message number or -1 in case of a failure.
     */
    int32_t getHighestMsgNum(const std::string& name) const;

    /**
     * @brief Load and returns one serialized event/message data.
     *
     * The functions returns the sequence number of the loaded event record.
     *
     * @param name The conversation partner's name
     * @param eventId The event id, unique inside partner's conversation
     * @param event Contains the serialized data of the event data structure on return
     * @param msgNumber Pointer to an integer. Ths function sets the integer to the message
     *                  sequence number.
     * @return A SQLite code.
     */
    int32_t loadEvent(const std::string& name, const std::string& eventId, std::string* const event, int32_t* const msgNumber) const;

    /**
     * @brief Load a message with a given message id.
     *
     * Lookup and load a message based on the unique message id (UUID). The function does
     * not restrict the lookup to a particular conversation.
     *
     * @param msgId The message id
     * @param event The serialized data of the event data structure
     * @return A SQLite code.
     */
    int32_t loadEventWithMsgId(const std::string& eventId, std::string* const event);

    /**
     * @brief Checks if a event exists.
     *
     * @param name Name of conversation
     * @param eventId Id of the event
     * @return @c true if the event exists, @c false if not.
     */
    bool existEvent(const std::string& name, const std::string& eventId, int32_t* const sqlCode = NULL);

    /**
     * @brief Load and returns a set of serialized event/message data.
     * 
     * Because each event/message record has a serial number and the highest serial number
     * is the newest message this function provides several ways to select the set of message
     * records to return:
     * 
     * If @c offset is -1 then the functions takes the highest available message number and
     * subtracts @c number and starts with this message. It sorts the message records in
     * descending order, thus the newest message is the first in the returned vector.
     * If the computation results in a negative record number then the function starts with
     * record number 1.
     * 
     * If @c offset is not -1 then the function takes this number as a sequence number of a
     * record and starts to select @c number of records or until the end of the record table,
     * sorted in descending order.
     * 
     * If @c offset and @c number are both -1 the the function returns all message records,
     * sorted in descending order.
     * 
     * The function may return less events than requested if the application deleted event
     * records in the selected range. The function returns the sequence number of the last
     * (oldest) event record, i.e. the smallest found sequence number.
     * 
     * @param name The conversation partner's name
     * @param offset Where to start to retrieve the events/message
     * @param number how many event/message to load
     * @param events The serialized data of the events data structure, the caller must delete 
     *               the string pointers
     * @param lastMsgNumber Pointer to an integer. The function sets the integer to the sequence
     *                      number of the oldest message in the returned data list.
     * @return A SQLite code.
     */
    int32_t loadEvents(const std::string& name, int32_t offset, int32_t number, int32_t direction,
                       std::list<std::string*>* const events, int32_t* const lastMsgNumber) const;

    /**
     * @brief Delete a single event.
     * 
     * The database enforces the restriction that the application can delete the event
     * only if no objects are stored for this event. The function return the SQLite 
     * error code 19, 'Abort due to constraint violation' in this case.
     * 
     * @param name The conversation partner's name
     * @param eventId the id of the event
     * @return An SQLite code.
     */
    int32_t deleteEvent(const std::string& name, const std::string& eventId);

    /**
     * @brief Delete all events for the defined conversation.
     * 
     * The database enforces the restriction that the application can delete the events
     * only if no objects are stored for this event. The function return the SQLite 
     * error code 19, 'Abort due to constraint violation' in this case.
     * 
     * @param name The conversation partner's name
     * @return An SQLite code.
     */
    int32_t deleteEventName(const std::string& name);

    /**
     * @brief Insert serialized object data.
     * 
     * The functions inserts the object data.
     *
     * The functions returns and error in case a record with the same object id for this
     * event already exists.
     * 
     * @param name The conversation partner's name
     * @param eventId The event id
     * @param objectId  The object id, unique inside the event it belongs to
     * @param object The serialized data of the object data structure
     * @return A SQLite code.
     */
    int32_t insertObject(const std::string& name, const std::string& eventId, const std::string& objectId, const std::string& object);

    /**
     * @brief Load and returns one serialized object data.
     * 
     * @param name The conversation partner's name
     * @param eventId The event id
     * @param objectId The object id, unique inside the event it belongs to
     * @param object The serialized data of the event data structure
     * @return A SQLite code.
     */
    int32_t loadObject(const std::string& name, const std::string& eventId, const std::string& objectId, std::string* const object) const;

     /**
      * @brief Checks if an object exists.
      *
      * @param name Name of conversation
      * @param eventId Id of the event
      * @param objectId The object id, unique inside the event it belongs to
      * @return @c true if the event exists, @c false if not.
      */
     bool existObject(const std::string& name, const std::string& eventId, const std::string& objId, int32_t* const sqlCode = NULL) const;

    /**
     * @brief Load and returns he set of serialized object data that belong to a event/message.
     * 
     * @param name The conversation partner's name
     * @param eventId The event id
     * @param objects The serialized data of the event data structure, the caller must delete the string pointers
     * @return A SQLite code.
     */
    int32_t loadObjects(const std::string& name, const std::string& eventId, std::list<std::string*>* const objects) const;

    /**
     * @brief Delete a single object.
     * 
     * @param name The conversation partner's name
     * @param eventId the id of the event
     * @param objectId The object id, unique inside the event it belongs to
     * @return An SQLITE code.
     */
    int32_t deleteObject(const std::string& name, const std::string& eventId, const std::string& objectId);

    /**
     * @brief Delete a all objects that belong to an event/message.
     * 
     * @param name The conversation partner's name
     * @param eventId the id of the event
     * @return An SQLITE code.
     */
    int32_t deleteObjectMsg(const std::string& name, const std::string& eventId);

    /**
     * @brief Delete all objects that belong to a conversation.
     *
     * @param name The conversation partner's name
     * @return An SQLITE code.
     */
    int32_t deleteObjectName(const std::string& name);

    /**
     * @brief Insert or update the attachment status.
     *
     * If no entry exists for the msgId then insert a new entry and set its
     * status to 'status'. If an entry already exists then update the status
     * only.
     * 
     * If the caller provides a @c partnerName then the function stores it together
     * with the message id.
     * 
     * @param msgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe an empty string
     * @param status the new attchment status
     * @return the SQL code
     */
    int32_t storeAttachmentStatus(const std::string& msgId, const std::string& partnerName, int32_t status);

    /**
     * @brief Delete the attachment status entry.
     *
     * If the partner name is @c NULL then the function uses only the message id to
     * perform the delete request. Otherwise it requires full match, message id and
     * partner name to delete the status entry.
     * 
     * @param mesgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe an empty string
     * @return the SQL code
     */
    int32_t deleteAttachmentStatus(const std::string&  mesgId, const std::string& partnerName);

    /**
     * @brief Delete all attachment status entries with a given status.
     *
     * @param the status code
     * @return the SQL code
     */
    int32_t deleteWithAttachmentStatus(int32_t status);

    /**
     * @brief Delete all attachment status entries for a given conversation.
     *
     * @param @param partnerName Name of the conversation partner
     * @return the SQL code
     */
    int32_t deleteAttachmentStatusWithName(const std::string& partnerName);

    /**
     * @brief Return attachment status for message id.
     *
     * If the partner name is @c NULL then the function uses only the message id to
     * perform the load request. Otherwise it requires full match, message id and
     * partner name to load the status entry.
     * 
     * @param mesgId the message identifier of the attachment status entry.
     * @param partnerName Name of the conversation partner, maybe an empty string
     * @param status contains the attachment status on return
     * @return the SQL code
     */
    int32_t loadAttachmentStatus(const std::string& mesgId, const std::string& partnerName, int32_t* const status);

    /**
     * @brief Return all message ids with a given status.
     *
     * Returns a string list that contains the message identifiers as UUID string.
     * If a partner name was set then the functions appends a colon (:) and the
     * partner name to the UUID string.
     *
     * @param status finds all attachment message ids with this status.
     * @param msgIds pointer to a (empty) list of strings to store the found message ids
     * @return the SQL code
     */
    int32_t loadMsgsIdsWithAttachmentStatus(int32_t status, std::list<std::string>* const msgIds);

    /**
     * @brief Store data retention pending events
     * 
     * @param startTime The time the message or call event started
     * @param data The JSON serialized data of the event
     * @return An SQLite code.
     */
    int32_t storeDrPendingEvent(time_t startTime, const std::string& data);

    /**
     * @brief Load and returns the set of serialized data retention event data
     *        along with the an identifier that can be used to remove it later.
     * @param objects A list of pairs containing an identifier for the stored
     *        event data and a string containing the JSON event data.
     * @return A SQLite code.
     */
    int32_t loadDrPendingEvents(std::list<std::pair<int64_t, std::string>>& objects) const;

    /**
     * @brief Delete data retention pending event data identified by the list of
     *        identifiers.
     *
     * @param rows A list of identifiers of the rows to delete. These identifiers
     *        should be obtained y a loadDrPendingEvents call.
     * @return the SQL code
     */
    int32_t deleteDrPendingEvents(std::vector<int64_t>& rows);

    /**
     * @brief Return ready status.
     *
     * @return @c true if repository is ready (open), @c false if not.
     */
    bool isReady() { return ready; }

private:
    AppRepository();
    ~AppRepository();

    AppRepository(const AppRepository& other) = delete;
    AppRepository& operator=(const AppRepository& other) = delete;
    bool operator==(const AppRepository& other) const = delete;

    /**
     * Create AppRepository tables in database.
     *
     * openCache calls this function if it the user version is 0. 
     */
    int createTables();

    int beginTransaction();
    int commitTransaction();
    int rollbackTransaction();

    /**
     * @brief Update database version.
     * 
     * This function runs in a transaction and any changes are discarded if the
     * function closes the database or returns a code other than SQLITE_OK.
     * 
     * @param oldVersion the current version of the database
     * @param newVersion the target version for the database
     * @return @c SQLITE_OK to commit any changes, any other code closes the database with rollback.
     */
    int32_t updateDb(int32_t oldVersion, int32_t newVersion);

    int32_t getNextSequenceNum(const std::string& name);

    static AppRepository* instance_;
    sqlite3* db;
    std::string* keyData_;
    bool ready;

    mutable int32_t sqlCode_;
    mutable char lastError_[DB_CACHE_ERR_BUFF_SIZE];
};
} // namespace zina

/**
 * @}
 */

#endif // APPREPOSITORY_H
