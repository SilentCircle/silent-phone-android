#include "AppRepository.h"

#include <stdio.h>
#include <iostream>

#include <cryptcommon/ZrtpRandom.h>
#include <cryptcommon/aescpp.h>


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
        sqlCode_ = (func);          \
        if(sqlCode_ != SQLITE_OK) { \
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

#define DB_VERSION 2

void Log(const char* format, ...);

static const char *beginTransactionSql  = "BEGIN TRANSACTION;";
static const char *commitTransactionSql = "COMMIT;";
static const char *rollbackTransactionSql = "ROLLBACK TRANSACTION;";

/* *****************************************************************************
 * SQL statements to process the conversations table.
 */
static const char* dropConversations = "DROP TABLE conversations;";

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
static const char* selectConversationLike = "SELECT data FROM conversations WHERE name LIKE ?1;";
static const char* selectConversationNames = "SELECT name FROM conversations;";

static const char* deleteConversationSql = "DELETE FROM conversations WHERE name=?1;";

/* *****************************************************************************
 * SQL statements to process the events table.
 */
static const char *dropEvents = "DROP TABLE events;";
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
static const char *dropObjects = "DROP TABLE objects;";
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


/* *****************************************************************************
 * SQL statements to process the attahcment status table.
 */
static const char *dropAttachmentStatus = "DROP TABLE attachmentStatus;";
static const char *createAttachmentStatus = 
    "CREATE TABLE IF NOT EXISTS attachmentStatus (msgId VARCHAR NOT NULL, partnerName VARCHAR, status INTEGER, PRIMARY KEY(msgId));";

static const char* insertAttachmentStatusSql = "INSERT OR REPLACE INTO attachmentStatus (msgId, status, partnerName) VALUES (?1, ?2, ?3);"; 

static const char* selectAttachmentStatus = "SELECT status FROM attachmentStatus WHERE msgId=?1;";
static const char* selectAttachmentStatus2 = "SELECT status FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
static const char* selectMsgIdsWithStatus = "SELECT msgId, partnerName FROM attachmentStatus WHERE status=?1;";

static const char* deleteAttachmentStatusMsgIdSql = "DELETE FROM attachmentStatus WHERE msgId=?1;";
static const char* deleteAttachmentStatusMsgIdSql2 = "DELETE FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
static const char* deleteAttachmentStatusWithStatusSql = "DELETE FROM attachmentStatus WHERE status=?1;";

using namespace axolotl;

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

static int32_t getUserVersion(sqlite3* db)
{
    sqlite3_stmt *stmt;

    int32_t rc = sqlite3_prepare(db, "PRAGMA user_version", -1, &stmt, NULL);
    rc = sqlite3_step(stmt);

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

    int32_t rc = sqlite3_prepare(db, statement, -1, &stmt, NULL);
    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

   return rc;
}

static int32_t enableForeignKeys(sqlite3* db)
{
    sqlite3_stmt *stmt;

    int32_t rc = sqlite3_prepare(db, "PRAGMA foreign_keys=ON;", -1, &stmt, NULL);
    rc = sqlite3_step(stmt);
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
    if (instance_ == NULL) {
        instance_ = new AppRepository();
    }
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
    // If name has size 0 then open im-memory DB, handy for testing
    const char *dbName = name.size() == 0 ? ":memory:" : name.c_str();
    sqlCode_ = sqlite3_open_v2(dbName, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL);

    if (sqlCode_) {
        ERRMSG;
        return(sqlCode_);
    }
    if (keyData_ != NULL)
        sqlite3_key(db, keyData_->data(), keyData_->size());

    memset_volatile((void*)keyData_->data(), 0, keyData_->size());
    delete keyData_; keyData_ = NULL;

    enableForeignKeys(db);

    int32_t version = getUserVersion(db);
    if (version != 0) {
        beginTransaction();
        if (updateDb(version, DB_VERSION) != SQLITE_OK) {
            sqlite3_close(db);
            return SQLITE_ERROR;
        }
        commitTransaction();
    }
    else {
        if (createTables() != SQLITE_OK)
            return sqlCode_;
    }

    setUserVersion(db, DB_VERSION);
    ready = true;
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
    sqlite3_stmt* stmt;

    if (oldVersion < 2) {
        // check if attachmentStatus table is already available
        SQLITE_PREPARE(db, lookupTables, -1, &stmt, NULL);
        int32_t rc = sqlite3_step(stmt);
        sqlite3_finalize(stmt);

        if (rc != SQLITE_ROW) {
            sqlCode_ = SQLITE_PREPARE(db, createAttachmentStatus, -1, &stmt, NULL);
            sqlCode_ = sqlite3_step(stmt);
            if (sqlCode_ != SQLITE_DONE)
                return sqlCode_;
        }
        // If table exists check if we need to update it
        else if (!checkForFieldInTable(db, "attachmentStatus", "partnerName")) {
            const char* addColumn = "ALTER TABLE attachmentStatus ADD partnerName VARCHAR;";
            sqlCode_ = SQLITE_PREPARE(db, addColumn, -1, &stmt, NULL);
            sqlCode_ = sqlite3_step(stmt);
            if (sqlCode_ != SQLITE_DONE)
                return sqlCode_;
        }
        oldVersion = 2;
    }
    if (oldVersion != newVersion)
        return SQLITE_ERROR;
    return SQLITE_OK;
}

int AppRepository::createTables()
{
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

    return SQLITE_OK;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;

}

int32_t AppRepository::storeConversation(const std::string& name, const std::string& conversation)
{
    sqlite3_stmt *stmt;

    // "UPDATE conversations SET data=?1 WHERE name=?2;
    SQLITE_CHK(SQLITE_PREPARE(db, updateConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, conversation.data(), conversation.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);

    // "INSERT OR IGNORE INTO conversations (name, since, nextMsgNumber, state, data)"
    // "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, 1));    // Initialize next message counter with 1
    SQLITE_CHK(sqlite3_bind_int(stmt,   4, 0));    // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  5, conversation.data(), conversation.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::loadConversation(const std::string& name, std::string* conversation) const
{
    sqlite3_stmt *stmt;
    int32_t len;

    // SELECT data FROM conversations WHERE name=?1;
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {        // No such conversation
        sqlite3_finalize(stmt);
        return sqlCode_;
    }

    // Get the conversation data
    len = sqlite3_column_bytes(stmt, 0);
    conversation->assign((const char*)sqlite3_column_blob(stmt, 0), len);

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;

}

bool AppRepository::existConversation(const std::string& name)
{
    sqlite3_stmt *stmt;

    // SELECT data FROM conversations WHERE name LIKE ?1;
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    return (sqlCode_ == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    return false;
}

int32_t AppRepository::deleteConversation(const std::string& name)
{
    sqlite3_stmt *stmt;

    // DELETE FROM conversations WHERE name=?1;
    SQLITE_CHK(SQLITE_PREPARE(db, deleteConversationSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1,name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

list<string>* AppRepository::listConversations() const
{
    sqlite3_stmt *stmt;
    list<string>* result = new list<string>;

    // selectConversationNames = "SELECT name FROM conversations;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversationNames, -1, &stmt, NULL));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        string data((const char*)sqlite3_column_text(stmt, 0));
        result->push_back(data);
    }
    sqlite3_finalize(stmt);
    return result;

cleanup:
    delete result;
    sqlite3_finalize(stmt);
    return NULL;
}


static const char* selectMsgNumber = "SELECT nextMsgNumber FROM conversations WHERE name =?1;";
int32_t AppRepository::getHighestMsgNum(const std::string& name) const
{
    sqlite3_stmt *stmt;
    int32_t number;

    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);

    if (sqlCode_ != SQLITE_ROW) {
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

int32_t AppRepository::insertEvent(const std::string& name, const std::string& eventId, const std::string& event)
{
    sqlite3_stmt *stmt;
    int32_t msgNumber;

    if (existEvent(name, eventId))
        return updateEvent(name, eventId, event);

    beginTransaction();
    msgNumber = getNextSequenceNum(name);

    // "INSERT events (eventid, inserted, msgNumber, state, data, convName)"
    // "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, msgNumber));
    SQLITE_CHK(sqlite3_bind_int(stmt,   4, 0));         // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  5, event.data(), event.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  6, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_DONE) {
        rollbackTransaction();
    }
    else {
        commitTransaction();
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::updateEvent(const std::string& name, const std::string& eventId, const std::string& event)
{
    sqlite3_stmt *stmt;

    // updateEventSql = "UPDATE events SET data=?1 WHERE eventid=?2 AND convName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, updateEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, event.data(), event.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  2, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  3, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;

}

int32_t AppRepository::loadEvent(const std::string& name, const std::string& eventId, std::string* event, int32_t *msgNumber) const
{
    sqlite3_stmt *stmt;
    int32_t len;

    // selectEvent = "SELECT data, msgNumber FROM events WHERE eventid=?1 and convName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectEvent, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return sqlCode_;
    }
    // Get the conversation data
    len = sqlite3_column_bytes(stmt, 0);
    event->assign((const char*)sqlite3_column_blob(stmt, 0), len);
    *msgNumber = sqlite3_column_int(stmt, 1);

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::loadEventWithMsgId(const string& eventId,  string* event)
{
    sqlite3_stmt *stmt;
    int32_t len;

    // selectEventWithId = "SELECT data FROM events WHERE eventid=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectEventWithId, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return sqlCode_;
    }
    // Get the conversation data
    len = sqlite3_column_bytes(stmt, 0);
    event->assign((const char*)sqlite3_column_blob(stmt, 0), len);

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

bool AppRepository::existEvent(const std::string& name, const std::string& eventId)
{
    sqlite3_stmt *stmt;

    // "SELECT data FROM events WHERE eventid=?1 and convName=?2;"
    SQLITE_CHK(SQLITE_PREPARE(db, selectEvent, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    return (sqlCode_ == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    return false;
}

static const char* selectEventAllDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 ORDER by msgNumber DESC;";
static const char* selectEventLimitDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 AND msgNumber>=?2 ORDER BY msgNumber DESC LIMIT ?3;";
static const char* selectEventBetweenDesc = "SELECT data, msgNumber FROM events WHERE convName=?1 AND msgNumber BETWEEN ?2 AND ?3 ORDER BY msgNumber DESC;";

int32_t AppRepository::loadEvents(const std::string& name, uint32_t offset, int32_t number, std::list<std::string*>* events, int32_t *lastMsgNumber) const
{
    sqlite3_stmt *stmt;

    if (offset == -1 && number == -1) {            // selectEvent = "SELECT data, msgNumber FROM events WHERE eventid=?1 and convName=?2;";
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventAllDesc, -1, &stmt, NULL));
    }
    else if (offset == -1 && number > 0) {
        int32_t highestNum = getHighestMsgNum(name);
        int32_t startAt = highestNum - number;
        startAt = (startAt <= 0) ? 1 : startAt;
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventLimitDesc, -1, &stmt, NULL));
        SQLITE_CHK(sqlite3_bind_int(stmt, 2, startAt));
        SQLITE_CHK(sqlite3_bind_int(stmt, 3, number));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, selectEventBetweenDesc, -1, &stmt, NULL));
        SQLITE_CHK(sqlite3_bind_int(stmt, 2, offset));
        SQLITE_CHK(sqlite3_bind_int(stmt, 3, offset+number-1));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        std::string* data = new std::string((const char*)sqlite3_column_blob(stmt, 0), len);
        *lastMsgNumber = sqlite3_column_int(stmt, 1);
        events->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteEvent(const std::string& name, const std::string& eventId)
{
    sqlite3_stmt *stmt;

    // "DELETE FROM events WHERE eventid=?1 AND convName=?2;"
    SQLITE_CHK(SQLITE_PREPARE(db, deleteEventSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteEventName(const std::string& name)
{
    sqlite3_stmt *stmt;

    // "DELETE FROM events WHERE convName=?1;"
    SQLITE_CHK(SQLITE_PREPARE(db, deleteEventNameSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
//    std::cerr << "Deleted records: " << sqlite3_changes(db) << std::endl;
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}


int32_t AppRepository::insertObject(const std::string& name, const std::string& eventId, const std::string& objectId, const std::string& object)
{
    sqlite3_stmt *stmt;
    int32_t msgNumber;

//     "INSERT INTO objects (objectid, inserted,  state, data, eventid, conv)"
//     "VALUES (?1, strftime('%s', ?2, 'unixepoch'), ?3, ?4, ?5, ?6);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertObjectSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, objectId.data(), objectId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, (int64_t)time(NULL)));
    SQLITE_CHK(sqlite3_bind_int(stmt,   3, 0));         // No state yet
    SQLITE_CHK(sqlite3_bind_blob(stmt,  4, object.data(), object.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  5, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  6, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::loadObject(const std::string& name, const std::string& eventId, const std::string& objectId, std::string* object) const
{
    sqlite3_stmt *stmt;
    int32_t len;

    // selectObject = "SELECT data FROM objects WHERE objectid=?1 and eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObject, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objectId.data(), objectId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return sqlCode_;
    }
    // Get the conversation data
    len = sqlite3_column_bytes(stmt, 0);
    object->assign((const char*)sqlite3_column_blob(stmt, 0), len);

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

bool AppRepository::existObject(const std::string& name, const std::string& eventId, const std::string& objId) const
{
    sqlite3_stmt *stmt;

    // selectObject = "SELECT data FROM objects WHERE objectid=?1 and eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObject, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objId.data(), objId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    return (sqlCode_ == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    return false;
}

int32_t AppRepository::loadObjects(const std::string& name, const std::string& eventId, std::list<std::string*>* objects) const
{
    sqlite3_stmt *stmt;

    // selectObjectsMsg = "SELECT data FROM objects WHERE eventid=?1  AND conv=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectObjectsMsg, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        std::string* data = new std::string((const char*)sqlite3_column_blob(stmt, 0), len);
        objects->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteObject(const std::string& name, const std::string& eventId, const std::string& objectId)
{
    sqlite3_stmt *stmt;

    // deleteObjectSql = "DELETE FROM objects WHERE objectid=?1 AND eventid=?2  AND conv=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteObjectSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, objectId.data(), objectId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteObjectMsg(const std::string& name, const std::string& eventId)
{
    sqlite3_stmt *stmt;

    // deleteObjectMsgSql = "DELETE FROM objects WHERE eventid=?1  AND conv=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteObjectMsgSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, eventId.data(), eventId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
//    std::cerr << "Deleted records: " << sqlite3_changes(db) << std::endl;
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::storeAttachmentStatus(const string& mesgId, const string& partnerName, int32_t status)
{
    sqlite3_stmt *stmt;

    // insertAttachmentStatusSql = "INSERT OR REPLACE INTO attachmentStatus (msgId, status, partnerName) VALUES (?1, ?2, ?3);"; 
    SQLITE_CHK(SQLITE_PREPARE(db, insertAttachmentStatusSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), mesgId.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, status));
    if (partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_null(stmt, 3));
    }
    else {
        SQLITE_CHK(sqlite3_bind_text(stmt, 3, partnerName.data(), partnerName.size(), SQLITE_STATIC));
    }
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteAttachmentStatus(const string& mesgId, const string& partnerName)
{
    sqlite3_stmt *stmt;
    // deleteAttachmentStatusMsgIdSql = "DELETE FROM attachmentStatus WHERE msgId=?1;";
    // deleteAttachmentStatusMsgIdSql2 = "DELETE FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
    
    if (partnerName.empty()) {
        SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusMsgIdSql, -1, &stmt, NULL));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusMsgIdSql2, -1, &stmt, NULL));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), mesgId.size(), SQLITE_STATIC));
    if (!partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_text(stmt, 2, partnerName.data(), partnerName.size(), SQLITE_STATIC));
    }
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::deleteWithAttachmentStatus(int32_t status)
{
    sqlite3_stmt *stmt;
    // static const char* deleteAttachmentStatusWithStatusSql = "DELETE FROM attachmentStatus WHERE status=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, deleteAttachmentStatusWithStatusSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, status));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::loadAttachmentStatus(const string& mesgId, const string& partnerName, int32_t* status)
{
    sqlite3_stmt *stmt;
    *status = -1;
    // selectAttachmentStatus = "SELECT status FROM attachmentStatus WHERE msgId=?1;";
    // selectAttachmentStatus2 = "SELECT status FROM attachmentStatus WHERE msgId=?1 AND partnerName=?2;";
    if (partnerName.empty()) {
        SQLITE_CHK(SQLITE_PREPARE(db, selectAttachmentStatus, -1, &stmt, NULL));
    }
    else {
        SQLITE_CHK(SQLITE_PREPARE(db, selectAttachmentStatus2, -1, &stmt, NULL));
    }
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, mesgId.data(), mesgId.size(), SQLITE_STATIC));
    if (!partnerName.empty()) {
        SQLITE_CHK(sqlite3_bind_text(stmt, 2, partnerName.data(), partnerName.size(), SQLITE_STATIC));
    }
    sqlCode_= sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_ROW) {
        ERRMSG;
        sqlite3_finalize(stmt);
        return sqlCode_;
    }
    *status = sqlite3_column_int(stmt, 0);

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int32_t AppRepository::loadMsgsIdsWithAttachmentStatus(int32_t status, list<string>* msgIds)
{
    sqlite3_stmt *stmt;
    const unsigned char* pn;
    // selectMsgIdsWithStatus = "SELECT msgId, partnerName FROM attachmentStatus WHERE status=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgIdsWithStatus, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, status));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t len = sqlite3_column_bytes(stmt, 0);
        string data((const char*)sqlite3_column_text(stmt, 0), len);
        pn = sqlite3_column_text(stmt, 1);
        if (pn != NULL) {
            data.append(":").append((const char*)pn);
        }
        msgIds->push_back(data);
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
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

    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);

    if (sqlCode_ != SQLITE_ROW) {
        sqlite3_finalize(stmt);
        return -1;
    }
    nextNumber = sqlite3_column_int(stmt, 0);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, updateMsgNumber, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt,  1, nextNumber+1));    // Increment nextMsgNumber
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);

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
