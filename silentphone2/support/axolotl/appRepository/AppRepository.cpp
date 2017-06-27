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
#include "AppRepository.h"

#include <iostream>
#include <mutex>          // std::mutex, std::unique_lock
#include <algorithm>

#include <cryptcommon/ZrtpRandom.h>

#include "../logging/ZinaLogging.h"

/* *****************************************************************************
 * A few helping macros. 
 * These macros require some names/patterns in the methods that use these 
 * macros:
 * 
 * ERRMSG requires:
 * - a variable with name "db" is the pointer to sqlite3
 * - a char* with name "lastError" points to a buffer of at least SQL_CACHE_ERR_BUFF_SIZE chars
 *
 * SQLITE_CHK requires:
 * - a cleanup label, the macro goes to that label in case of error
 * - an integer (int) variable with name "rc" that stores return codes from sqlite
 * - ERRMSG
 */
#define ERRMSG  {snprintf(lastError_, (size_t)DB_CACHE_ERR_BUFF_SIZE, \
                          "SQLite3 error: %s, line: %d, error message: %s\n", __FILE__, __LINE__, sqlite3_errmsg(db));}

#define SQLITE_CHK(func) {          \
        sqlResult = (func);          \
        if(sqlResult != SQLITE_OK) { \
            ERRMSG;                 \
            goto cleanup;           \
        }                           \
    }

#define SQLITE_USE_V2

#ifdef SQLITE_USE_V2
#define SQLITE_PREPARE sqlite3_prepare_v2
#else
#define SQLITE_PREPARE sqlite3_prepare
#endif

#define DB_VERSION 3

using namespace std;

static mutex sqlLock;

void Log(const char* format, ...);

static const char *beginTransactionSql  = "BEGIN TRANSACTION;";
static const char *commitTransactionSql = "COMMIT;";
static const char *rollbackTransactionSql = "ROLLBACK TRANSACTION;";

/* *****************************************************************************
 * SQL statements to process the conversations table.
 */
// static const char* dropConversations = "DROP TABLE conversations;";

/* SQLite doesn't care about the VARCHAR length. */
static const char* createConversations =
    "CREATE TABLE IF NOT EXISTS conversations(name VARCHAR NOT NULL PRIMARY KEY, since TIMESTAMP, nextMsgNumber UNSIGNED INTEGER, state INTEGER, data BLOB);";

// Storing Session data for a name/deviceId pair first tries to update. If it succeeds then
// the following INSERT OR IGNORE is a no-op. Otherwise the function INSERT a complete new record:
// - Try to update any existing row
// - Make sure it exists
static const char* updateConversation = "UPDATE conversations SET data=?1 WHERE name=?2;";
static const char* insertConversation = 
    "INSERT OR IGNORE INTO conversations (name, since, nextMsgNumber, state, data)"
    "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5);";

static const char* selectConversation = "SELECT data FROM conversations WHERE name=?1;";
// static const char* selectConversationLike = "SELECT data FROM conversations WHERE name LIKE ?1;";
static const char* selectConversationNames = "SELECT name FROM conversations;";

static const char* deleteConversationSql = "DELETE FROM conversations WHERE name=?1;";

/* *****************************************************************************
 * SQL statements to process the events table.
 */
// static const char *dropEvents = "DROP TABLE events;";
static const char *createEvents =
    "CREATE TABLE IF NOT EXISTS events (eventid VARCHAR NOT NULL, inserted TIMESTAMP, msgNumber UNSIGNED INTEGER, state INTEGER, data BLOB,"
    "convName VARCHAR NOT NULL, PRIMARY KEY(eventid, convName), FOREIGN KEY(convName) REFERENCES conversations(name));";

static const char* updateEventSql = "UPDATE events SET data=?1 WHERE eventid=?2 AND convName=?3;";
// static const char* insertEventSql =
//     "INSERT OR REPLACE INTO events (eventid, inserted, msgNumber, state, data, convName)"
//     "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";

static const char* insertEventSql =
    "INSERT INTO events (eventid, inserted, msgNumber, state, data, convName)"
    "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";

static const char* selectEvent = "SELECT data, msgNumber FROM events WHERE eventid=?1 AND convName=?2;";
static const char* selectEventWithId = "SELECT data FROM events WHERE eventid=?1;";

static const char* deleteEventSql = "DELETE FROM events WHERE eventid=?1 AND convName=?2;";
static const char* deleteEventNameSql = "DELETE FROM events WHERE convName=?1;";

/* *****************************************************************************
 * SQL statements to process the objects table.
 */
// static const char *dropObjects = "DROP TABLE objects;";
static const char *createObjects =
    "CREATE TABLE IF NOT EXISTS objects (objectid VARCHAR NOT NULL, inserted TIMESTAMP, state INTEGER, data BLOB,"
    "event VARCHAR NOT NULL, conv VARCHAR NOT NULL, PRIMARY KEY(objectid, event), FOREIGN KEY(event, conv) REFERENCES events(eventid, convName));";

static const char* insertObjectSql =
    "INSERT OR REPLACE INTO objects (objectid, inserted,  state, data, event, conv)"
    "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";

static const char* selectObject = "SELECT data FROM objects WHERE objectid=?1 AND event=?2 AND conv=?3;";
static const char* selectObjectsMsg = "SELECT data FROM objects WHERE event=?1 AND conv=?2;";

static const char* deleteObjectSql = "DELETE FROM objects WHERE objectid=?1 AND event=?2 AND conv=?3;";
static const char* deleteObjectMsgSql = "DELETE FROM objects WHERE event=?1 AND conv=?2;";

static const char* deleteObjectsWithNameSql = "DELETE FROM objects WHERE conv=?1;";

/* *****************************************************************************
 * SQL statements to process the attahcment status table.
 */
// static const char *dropAttachmentStatus = "DROP TABLE attachmentStatus;";
static const char *createAttachmentStatus = 
    "CREATE TABLE IF NOT EXISTS attachmentStatus (msgId VARCHAR NOT NULL, partnerName VARCHAR, status INTEGER, PRIMARY KEY(msgId));";

static const char* insertAttachmentStatusSql = "INSERT OR REPLACE INTO attachmentStatus (msgId, status, partnerName) VALUES (?1, ?2, ?3);"; 

static const char* selectAttachmentStatus = "SELECT status FROM attachmentStatus WHERE msgId=?1;";
static const char* selectAttachmentStatus2 = "SELECT status FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
static const char* selectMsgIdsWithStatus = "SELECT msgId, partnerName FROM attachmentStatus WHERE status=?1;";

static const char* deleteAttachmentStatusMsgIdSql = "DELETE FROM attachmentStatus WHERE msgId=?1;";
static const char* deleteAttachmentStatusMsgIdSql2 = "DELETE FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
static const char* deleteAttachmentStatusWithStatusSql = "DELETE FROM attachmentStatus WHERE status=?1;";

static const char* deleteAttachmentStatusWithNameSql = "DELETE FROM attachmentStatus WHERE partnerName=?1;";

/* *****************************************************************************
 * SQL statements to process the pending data retention event metadata
 */
static const char *createDrPending =
  "CREATE TABLE IF NOT EXISTS drPendingEvent ( startTime INTEGER, data BLOB );";
static const char* insertDrPendingSql = "INSERT OR REPLACE INTO drPendingEvent (startTime, data ) VALUES(?1, ?2);";
static const char* selectDrPendingSql = "SELECT rowid,data from drPendingEvent ORDER BY startTime ASC;";
static const char* deleteDrPendingSql = "DELETE FROM drPendingEvent where rowid = ?1";

using namespace zina;

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

static int32_t getUserVersion(sqlite3* db)
{
    sqlite3_stmt *stmt;

    sqlite3_prepare(db, "PRAGMA user_version", -1, &stmt, NULL);
    int32_t rc = sqlite3_step(stmt);

    int32_t version = 0;
    if (rc == SQLITE_ROW) {
        version = sqlite3_column_int(stmt,  0);
    }
    sqlite3_finalize(stmt);
    return version;
}

static int32_t setUserVersion(sqlite3* db, int32_t newVersion)
{
    sqlite3_stmt *stmt;

    char statement[100];
    snprintf(statement, 90, "PRAGMA user_version = %d", newVersion);

    sqlite3_prepare(db, statement, -1, &stmt, NULL);
    int32_t rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

   return rc;
}

static int32_t enableForeignKeys(sqlite3* db)
{
    sqlite3_stmt *stmt;

    sqlite3_prepare(db, "PRAGMA foreign_keys=ON;", -1, &stmt, NULL);
    int32_t rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    return rc;
}

static bool checkForFieldInTable(sqlite3* dbp, const char* table, const char* field)
{
    sqlite3_stmt* stmt;

    string pragma("PRAGMA table_info(");
    pragma.append(table).append(")");

    int32_t sqlCode = SQLITE_PREPARE(dbp, pragma.c_str(), -1, &stmt, NULL);
    if (sqlCode != SQLITE_OK)
        return false;

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        const unsigned char* fieldName = sqlite3_column_text(stmt, 1);
        if (strcmp((const char*)fieldName, field) == 0) {
            sqlite3_finalize(stmt);
            return true;
        }
    }
    sqlite3_finalize(stmt);
    return false;
}

AppRepository* AppRepository::instance_ = NULL;

AppRepository* AppRepository::getStore()
{
    unique_lock<mutex> lck(sqlLock);
    if (instance_ == NULL) {
        instance_ = new AppRepository();
    }
    lck.unlock();
    return instance_;
}

AppRepository::AppRepository() : db(NULL), keyData_(NULL), ready(false) {}

AppRepository::~AppRepository()
{
    sqlite3_close(db);
    db = NULL;
    delete keyData_; keyData_ = NULL;
}


int AppRepository::openStore(const std::string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    unique_lock<mutex> lck(sqlLock);
    if (ready) {
        LOGGER(DEBUGGING, __func__ , " <-- is ready");
        return SQLITE_OK;
    }

    // If name has size 0 then open im-memory DB, handy for testing
    const char *dbName = name.size() == 0 ? ":memory:" : name.c_str();
    sqlCode_ = sqlite3_open_v2(dbName, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL);

    if (sqlCode_) {
        ERRMSG;
        LOGGER(ERROR, __func__ , " <-- error code: ", sqlCode_);
        return(sqlCode_);
    }
    if (keyData_ != NULL) {
        sqlite3_key(db, keyData_->data(), static_cast<int>(keyData_->size()));

        memset_volatile((void *) keyData_->data(), 0, keyData_->size());
        delete keyData_;
        keyData_ = NULL;
    }

    enableForeignKeys(db);

    int32_t version = getUserVersion(db);
    if (version != 0) {
        beginTransaction();
        if (updateDb(version, DB_VERSION) != SQLITE_OK) {
            sqlite3_close(db);
            LOGGER(ERROR, __func__ , " <-- update failed.");
            return SQLITE_ERROR;
        }
        commitTransaction();
    }
    else {
        if (createTables() != SQLITE_OK) {
            LOGGER(ERROR, __func__ , " <-- table creation failed.");
            return sqlCode_;
        }
    }
    setUserVersion(db, DB_VERSION);
    ready = true;
    lck.unlock();
    LOGGER(DEBUGGING, __func__ , " <--");
    return SQLITE_OK;
}

/* *****************************************************************************
 * The SQLite master table.
 *
 * Used to check if we have valid attachmentStatus table.
 */
static const char *lookupTables = "SELECT name FROM sqlite_master WHERE type='table' AND name='attachmentStatus';";

int32_t AppRepository::updateDb(int32_t oldVersion, int32_t newVersion) 
{
    LOGGER(DEBUGGING, __func__, " -->");
    sqlite3_stmt* stmt;

    if (oldVersion == 1) {
        // check if attachmentStatus table is already available
        SQLITE_PREPARE(db, lookupTables, -1, &stmt, NULL);
        int32_t rc = sqlite3_step(stmt);
        sqlite3_finalize(stmt);

        if (rc != SQLITE_ROW) {
            sqlCode_ = SQLITE_PREPARE(db, createAttachmentStatus, -1, &stmt, NULL);
            sqlCode_ = sqlite3_step(stmt);
            if (sqlCode_ != SQLITE_DONE) {
                LOGGER(ERROR, __func__, ", SQL error: ", sqlCode_);
                return sqlCode_;
            }
        }
        // If table exists check if we need to update it
        else if (!checkForFieldInTable(db, "attachmentStatus", "partnerName")) {
            const char* addColumn = "ALTER TABLE attachmentStatus ADD partnerName VARCHAR;";
            sqlCode_ = SQLITE_PREPARE(db, addColumn, -1, &stmt, NULL);
            sqlCode_ = sqlite3_step(stmt);
            if (sqlCode_ != SQLITE_DONE) {
                LOGGER(ERROR, __func__, ", SQL error (add column): ", sqlCode_);
                return sqlCode_;
            }
        }
        oldVersion = 2;
    }
    if (oldVersion == 2) {
        // Add data retention tables
        sqlCode_ = SQLITE_PREPARE(db, createDrPending, -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error: ", sqlCode_);
            return sqlCode_;
        }
        sqlite3_finalize(stmt);
        oldVersion = 3;
    }
    if (oldVersion != newVersion) {
        LOGGER(ERROR, __func__, ", Version numbers mismatch");
        return SQLITE_ERROR;
    }
    LOGGER(DEBUGGING, __func__, " <--");
    return SQLITE_OK;
}

int AppRepository::createTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;

    sqlCode_ = SQLITE_PREPARE(db, createConversations, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, createEvents, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, createObjects, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, createAttachmentStatus, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, createDrPending, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    LOGGER(DEBUGGING, __func__ , " <--");
    return SQLITE_OK;

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(ERROR, __func__, ", SQL error: ", sqlCode_, ", ", lastError_);
    return sqlCode_;

}

int32_t AppRepository::storeConversation(const string& name, const string& conversation)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // "UPDATE conversations SET data=?1 WHERE name=?2;
    SQLITE_CHK(SQLITE_PREPARE(db, updateConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, conversation.data(), static_cast<int>(conversation.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    // "INSERT OR IGNORE INTO conversations (name, since, nextMsgNumber, state, data)"
    // "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, 1));    // Initialize next message counter with 1
    SQLITE_CHK(sqlite3_bind_int(stmt,   4, 0));    // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  5, conversation.data(), static_cast<int>(conversation.size()), SQLITE_STATIC));
    sqlResult = sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadConversation(const string& name, string* const conversation) const
{
    LOGGER(DEBUGGING, __func__ , " -->");

    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    // SELECT data FROM conversations WHERE name=?1;
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {        // Found a conversation
        // Get the conversation data
        len = sqlite3_column_bytes(stmt, 0);
        conversation->assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

bool AppRepository::existConversation(const string& name, int32_t* const sqlCode)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    bool retVal = false;

    // SELECT data FROM conversations WHERE name LIKE ?1;
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    retVal = (sqlResult == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return retVal;
}

int32_t AppRepository::deleteConversation(const string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // DELETE FROM conversations WHERE name=?1;
    SQLITE_CHK(SQLITE_PREPARE(db, deleteConversationSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1,name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

list<string>* AppRepository::listConversations(int32_t* const sqlCode) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    list<string>* result = new list<string>;

    // selectConversationNames = "SELECT name FROM conversations;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversationNames, -1, &stmt, NULL));

    while (sqlite3_step(stmt) == SQLITE_ROW) {
        string data((const char*)sqlite3_column_text(stmt, 0));
        result->push_back(data);
    }
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__ , " <--");
    return result;

cleanup:
    delete result;
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return NULL;
}


static const char* selectMsgNumber = "SELECT nextMsgNumber FROM conversations WHERE name =?1;";
int32_t AppRepository::getHighestMsgNum(const string& name) const
{
    sqlite3_stmt *stmt;
    int32_t number;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlResult = sqlite3_step(stmt);

    if (sqlResult != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return -1;
    }
    number = sqlite3_column_int(stmt, 0);
    sqlite3_finalize(stmt);
    return number-1;     // Conversation record store next message number, actual is -1

cleanup:
    sqlite3_finalize(stmt);
    return -1;
}

int32_t AppRepository::insertEvent(const string& name, const string& eventId, const string& event)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t msgNumber;
    int32_t sqlResult;

    if (existEvent(name, eventId))
        return updateEvent(name, eventId, event);

    beginTransaction();
    msgNumber = getNextSequenceNum(name);

    // "INSERT events (eventid, inserted, msgNumber, state, data, convName)"
    // "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, msgNumber));
    SQLITE_CHK(sqlite3_bind_int(stmt,   4, 0));         // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  5, event.data(), static_cast<int>(event.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  6, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult != SQLITE_DONE) {
        LOGGER(ERROR, "INSERT failed, rollback, code: ", sqlResult);
        rollbackTransaction();
    }
    else {
        commitTransaction();
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlCode_;
}

int32_t AppRepository::updateEvent(const string& name, const string& eventId, const string& event)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // updateEventSql = "UPDATE events SET data=?1 WHERE eventid=?2 AND convName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, updateEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, event.data(), static_cast<int>(event.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  2, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  3, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlResult = sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;

}

int32_t AppRepository::loadEvent(const string& name, const string& eventId, string* const event, int32_t* const msgNumber) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    // selectEvent = "SELECT data, msgNumber FROM events WHERE eventid=?1 and convName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectEvent, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {
        // Get the conversation data
        len = sqlite3_column_bytes(stmt, 0);
        event->assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
        *msgNumber = sqlite3_column_int(stmt, 1);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadEventWithMsgId(const string& eventId,  string* const event)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    // selectEventWithId = "SELECT data FROM events WHERE eventid=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectEventWithId, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {
        // Get the conversation data
        len = sqlite3_column_bytes(stmt, 0);
        event->assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

bool AppRepository::existEvent(const string& name, const string& eventId, int32_t* const sqlCode)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    bool retVal = false;

    // "SELECT data FROM events WHERE eventid=?1 and convName=?2;"
    SQLITE_CHK(SQLITE_PREPARE(db, selectEvent, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    retVal = (sqlResult == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return retVal;
}

static const char* selectEventAllDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 ORDER by msgNumber DESC;";
static const char* selectEventLimitDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 AND msgNumber<=?2 ORDER BY msgNumber DESC LIMIT ?3;";
static const char* selectEventLimitAsc = "SELECT data, msgNumber FROM events WHERE convName=?1 AND msgNumber>=?2 ORDER BY msgNumber ASC LIMIT ?3;";
static const char* selectEventBetweenDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 AND msgNumber BETWEEN ?2 AND ?3 ORDER BY msgNumber DESC;";

#define FROM_YOUNGEST_TO_OLDEST -1
#define FROM_OLDEST_TO_YOUNGEST 1

int32_t AppRepository::loadEvents(const string& name, int32_t offset, int32_t number, int32_t direction, list<std::string*>* const events, int32_t* const lastMsgNumber) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    if (offset == -1 && number == -1) {            // selectEvent = "SELECT data, msgNumber FROM events WHERE eventid=?1 and convName=?2;";
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventAllDesc, -1, &stmt, NULL));
    }
    else if (direction == FROM_YOUNGEST_TO_OLDEST) {
        int32_t highestNum = getHighestMsgNum(name);
        int32_t startAt = offset == -1 ? highestNum : offset;
        startAt = (startAt <= 0) ? 1 : startAt;
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventLimitDesc, -1, &stmt, NULL));
        SQLITE_CHK(sqlite3_bind_int(stmt, 2, startAt));
        SQLITE_CHK(sqlite3_bind_int(stmt, 3, number));
    }
    else if (direction == FROM_OLDEST_TO_YOUNGEST) {
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventLimitAsc, -1, &stmt, NULL));
        SQLITE_CHK(sqlite3_bind_int(stmt, 2, offset));
        SQLITE_CHK(sqlite3_bind_int(stmt, 3, number));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventBetweenDesc, -1, &stmt, NULL));
        SQLITE_CHK(sqlite3_bind_int(stmt, 2, offset));
        SQLITE_CHK(sqlite3_bind_int(stmt, 3, offset+number-1));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        std::string* data = new std::string((const char*)sqlite3_column_blob(stmt, 0), len);
        *lastMsgNumber = sqlite3_column_int(stmt, 1);
        events->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteEvent(const string& name, const string& eventId)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // "DELETE FROM events WHERE eventid=?1 AND convName=?2;"
    SQLITE_CHK(SQLITE_PREPARE(db, deleteEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteEventName(const string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // "DELETE FROM events WHERE convName=?1;"
    SQLITE_CHK(SQLITE_PREPARE(db, deleteEventNameSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
//    std::cerr << "Deleted records: " << sqlite3_changes(db) << std::endl;
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}


int32_t AppRepository::insertObject(const string& name, const string& eventId, const string& objectId, const string& object)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

//     "INSERT INTO objects (objectid, inserted,  state, data, eventid, conv)"
//     "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertObjectSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, objectId.data(), static_cast<int>(objectId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, 0));         // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  4, object.data(), static_cast<int>(object.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  5, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  6, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadObject(const string& name, const string& eventId, const string& objectId, string* const object) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    // selectObject = "SELECT data FROM objects WHERE objectid=?1 and eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObject, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objectId.data(), static_cast<int>(objectId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {
        // Get the conversation data
        len = sqlite3_column_bytes(stmt, 0);
        object->assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

bool AppRepository::existObject(const string& name, const string& eventId, const string& objId, int32_t* const sqlCode) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    bool retVal = false;

    // selectObject = "SELECT data FROM objects WHERE objectid=?1 and eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObject, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objId.data(), static_cast<int>(objId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    retVal = (sqlResult == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return retVal;
}

int32_t AppRepository::loadObjects(const string& name, const string& eventId, list<string*>* const objects) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // selectObjectsMsg = "SELECT data FROM objects WHERE eventid=?1  AND conv=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObjectsMsg, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        std::string* data = new std::string((const char*)sqlite3_column_blob(stmt, 0), len);
        objects->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteObject(const string& name, const string& eventId, const string& objectId)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // deleteObjectSql = "DELETE FROM objects WHERE objectid=?1 AND eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteObjectSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objectId.data(), static_cast<int>(objectId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteObjectMsg(const std::string& name, const std::string& eventId)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // deleteObjectMsgSql = "DELETE FROM objects WHERE eventid=?1  AND conv=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteObjectMsgSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), static_cast<int>(eventId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
//    std::cerr << "Deleted records: " << sqlite3_changes(db) << std::endl;
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteObjectName(const std::string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // deleteObjectsWithNameSql = "DELETE FROM objects WHERE conv=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteObjectsWithNameSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
//    std::cerr << "Deleted records: " << sqlite3_changes(db) << std::endl;
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::storeAttachmentStatus(const string& mesgId, const string& partnerName, int32_t status)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // insertAttachmentStatusSql = "INSERT OR REPLACE INTO attachmentStatus (msgId, status, partnerName) VALUES (?1, ?2, ?3);"; 
    SQLITE_CHK(SQLITE_PREPARE(db, insertAttachmentStatusSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), static_cast<int>(mesgId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, status));
    if (partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_null(stmt, 3));
    }
    else {
        SQLITE_CHK(sqlite3_bind_text(stmt, 3, partnerName.data(), static_cast<int>(partnerName.size()), SQLITE_STATIC));
    }
    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteAttachmentStatus(const string& mesgId, const string& partnerName)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    // deleteAttachmentStatusMsgIdSql = "DELETE FROM attachmentStatus WHERE msgId=?1;";
    // deleteAttachmentStatusMsgIdSql2 = "DELETE FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";

    if (partnerName.empty()) {
        SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusMsgIdSql, -1, &stmt, NULL));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusMsgIdSql2, -1, &stmt, NULL));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), static_cast<int>(mesgId.size()), SQLITE_STATIC));
    if (!partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_text(stmt, 2, partnerName.data(), static_cast<int>(partnerName.size()), SQLITE_STATIC));
    }
    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteWithAttachmentStatus(int32_t status)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    // static const char* deleteAttachmentStatusWithStatusSql = "DELETE FROM attachmentStatus WHERE status=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusWithStatusSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, status));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteAttachmentStatusWithName(const std::string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    // static const char* deleteAttachmentStatusWithNameSql = "DELETE FROM attachmentStatus WHERE partnerName=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusWithNameSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadAttachmentStatus(const string& mesgId, const string& partnerName, int32_t* const status)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    *status = -1;
    // selectAttachmentStatus = "SELECT status FROM attachmentStatus WHERE msgId=?1;";
    // selectAttachmentStatus2 = "SELECT status FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
    if (partnerName.empty()) {
        SQLITE_CHK(SQLITE_PREPARE(db, selectAttachmentStatus, -1, &stmt, NULL));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, selectAttachmentStatus2, -1, &stmt, NULL));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), static_cast<int>(mesgId.size()), SQLITE_STATIC));
    if (!partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_text(stmt, 2, partnerName.data(), static_cast<int>(partnerName.size()), SQLITE_STATIC));
    }
    sqlResult = sqlite3_step(stmt);
    if (sqlResult == SQLITE_ROW) {
        *status = sqlite3_column_int(stmt, 0);
    }

cleanup:
    ERRMSG;
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadMsgsIdsWithAttachmentStatus(int32_t status, list<string>* const msgIds)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    const unsigned char* pn;
    int32_t sqlResult;

    // selectMsgIdsWithStatus = "SELECT msgId, partnerName FROM attachmentStatus WHERE status=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgIdsWithStatus, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, status));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        string data((const char*)sqlite3_column_text(stmt, 0), static_cast<size_t>(len));
        pn = sqlite3_column_text(stmt, 1);
        if (pn != NULL) {
            data.append(":").append((const char*)pn);
        }
        msgIds->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::storeDrPendingEvent(time_t startTime, const string& data)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, insertDrPendingSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, static_cast<int64_t>(startTime)));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, data.data(), static_cast<int>(data.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::loadDrPendingEvents(list<pair<int64_t, string>>& objects) const
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // selectDrPendingSql = "SELECT data from drPendingEvent ORDER BY startTime ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectDrPendingSql, -1, &stmt, NULL));
    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        int64_t rowid = sqlite3_column_int64(stmt, 0);
        int32_t len = sqlite3_column_bytes(stmt, 1);
        std::string data((const char*)sqlite3_column_blob(stmt, 1), len);
        objects.push_back(make_pair(rowid, data));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

int32_t AppRepository::deleteDrPendingEvents(vector<int64_t>& rows)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // deleteDrPendingSql = "DELETE FROM drPendingEvent where rowid = ?1";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteDrPendingSql, -1, &stmt, NULL));
    // Vector is sorted in descending order first to ensure highest rows are
    // deleted first.
    std::sort(rows.begin(), rows.end(), std::greater<int>());
    for (int64_t rowid : rows) {
        SQLITE_CHK(sqlite3_bind_int64(stmt, 1, rowid));
        sqlResult = sqlite3_step(stmt);
        SQLITE_CHK(sqlite3_reset(stmt));
        SQLITE_CHK(sqlite3_clear_bindings(stmt));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return sqlResult;
}

/*
 * Private functions
 */
static const char* updateMsgNumber = "UPDATE conversations SET nextMsgNumber=?1 WHERE name=?2;";

// This function should run in a transaction to be able to rollback the change in case
// insertion of the event/message key fails.
int32_t AppRepository::getNextSequenceNum(const std::string& name)
{
    sqlite3_stmt *stmt;
    int32_t nextNumber;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlResult= sqlite3_step(stmt);

    if (sqlResult != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return -1;
    }
    nextNumber = sqlite3_column_int(stmt, 0);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, updateMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt,  1, nextNumber+1));    // Increment nextMsgNumber
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int>(name.size()), SQLITE_STATIC));
    sqlite3_step(stmt);

    sqlite3_finalize(stmt);
    return nextNumber;

cleanup:
    sqlite3_finalize(stmt);
    return -1;
}

// The transaction helpers should not overwrite the sqlcode_ member variable
int AppRepository::beginTransaction()
{
    sqlite3_stmt *stmt;
    int rc;

    SQLITE_PREPARE(db, beginTransactionSql, -1, &stmt, NULL);

    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (rc != SQLITE_DONE) {
        ERRMSG;
        return rc;
    }
    return SQLITE_OK;
}

int AppRepository::commitTransaction()
{
    sqlite3_stmt *stmt;
    int rc;

    SQLITE_PREPARE(db, commitTransactionSql, -1, &stmt, NULL);

    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (rc != SQLITE_DONE) {
        ERRMSG;
        return rc;
    }
    return SQLITE_OK;
}

int AppRepository::rollbackTransaction()
{
    sqlite3_stmt *stmt;
    int rc;

    SQLITE_PREPARE(db, rollbackTransactionSql, -1, &stmt, NULL);

    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (rc != SQLITE_DONE) {
        ERRMSG;
        return rc;
    }
    return SQLITE_OK;
}
