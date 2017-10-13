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
#include "SQLiteStoreConv.h"
#include "SQLiteStoreInternal.h"
#include "../../util/Utilities.h"

#pragma clang diagnostic push
#pragma ide diagnostic ignored "ClangTidyInspection"
using namespace std;

static mutex sqlLock;

static const char *beginTransactionSql  = "BEGIN TRANSACTION;";
static const char *commitTransactionSql = "COMMIT;";
static const char *rollbackTransactionSql = "ROLLBACK TRANSACTION;";

static const char *beginSavepointSql  = "SAVEPOINT %s;";
static const char *commitSavepointSql = "RELEASE SAVEPOINT %s;";
static const char *rollbackSavepointSql = "ROLLBACK TO SAVEPOINT %s;";

/* *****************************************************************************
 * SQL statements to process the sessions table.
 */
static const char* dropConversations = "DROP TABLE Conversations;";
static const char* createConversations = 
    "CREATE TABLE Conversations ("
    "name VARCHAR NOT NULL, longDevId VARCHAR NOT NULL, ownName VARCHAR NOT NULL, secondName VARCHAR,"
    "flags INTEGER, since TIMESTAMP, data BLOB, checkData BLOB,"
    "PRIMARY KEY(name, longDevId, ownName));";

// Storing Session data for a name/deviceId pair first tries to update. If it succeeds then
// the following INSERT OR IGNORE is a no-op. Otherwise the function INSERT a complete new record:
// - Try to update any existing row
// - Make sure it exists
static const char* updateConversation = "UPDATE Conversations SET data=?1 WHERE name=?2 AND longDevId=?3 AND ownName=?4;";
static const char* insertConversation = "INSERT OR IGNORE INTO Conversations (name, secondName, longDevId, data, ownName) VALUES (?1, ?2, ?3, ?4, ?5);";
static const char* selectConversation = "SELECT data FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";

static const char* selectConvNames = "SELECT DISTINCT name FROM Conversations WHERE ownName=?1 ORDER BY name;";
static const char* selectConvDevices = "SELECT longDevId FROM Conversations WHERE name=?1 AND ownName=?2;";

// Delete a specific sessions
static const char* removeConversation = "DELETE FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
// Delete all sessions for that name
static const char* removeConversations = "DELETE FROM Conversations WHERE name=?1 AND ownName=?2;";

/* *****************************************************************************
 * SQL statments for the staged message key table
 */
static const char* dropStagedMk = "DROP TABLE stagedMk;";
static const char* createStagedMk = 
    "CREATE TABLE stagedMk (name VARCHAR NOT NULL, longDevId VARCHAR NOT NULL, ownName VARCHAR NOT NULL,"
    "since TIMESTAMP, otherkey BLOB, ivkeymk BLOB, ivkeyhdr BLOB);";

static const char* insertStagedMkSql = 
    "INSERT OR REPLACE INTO stagedMk (name, longDevId, ownName, since, otherkey, ivkeymk, ivkeyhdr) "
    "VALUES(?1, ?2, ?3, strftime('%s', ?4, 'unixepoch'), ?5, ?6, ?7);";

static const char* selectStagedMks = "SELECT ivkeymk FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
static const char* removeStagedMk = "DELETE FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3 AND ivkeymk=?4;";

static const char* removeStagedMkTime = "DELETE FROM stagedMk WHERE since < ?1;";

static const char* hasStagedMkSql =
        "SELECT NULL, CASE EXISTS (SELECT 0 FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3 AND ivkeymk=?4) WHEN 1 THEN 1 ELSE 0 END;";

/* *****************************************************************************
 * SQL statements to process the Pre-key table.
 */
static const char* dropPreKeys = "DROP TABLE PreKeys;";
static const char* createPreKeys = "CREATE TABLE PreKeys (keyid INTEGER NOT NULL PRIMARY KEY, preKeyData BLOB, checkData BLOB);";
static const char* insertPreKey = "INSERT INTO PreKeys (keyId, preKeyData) VALUES (?1, ?2);";
static const char* selectPreKey = "SELECT preKeyData FROM PreKeys WHERE keyid=?1;";
static const char* deletePreKey = "DELETE FROM PreKeys WHERE keyId=?1;";
static const char* selectPreKeyAll = "SELECT keyId, preKeyData FROM PreKeys;";

/* *****************************************************************************
 * SQL statements to process the message hash table.
 */
static const char* dropMsgHash = "DROP TABLE MsgHash;";
static const char* createMsgHash = "CREATE TABLE MsgHash (msgHash BLOB NOT NULL PRIMARY KEY, since TIMESTAMP);";
static const char* insertMsgHashSql = "INSERT INTO MsgHash (msgHash, since) VALUES (?1, strftime('%s', ?2, 'unixepoch'));";
static const char* selectMsgHash = "SELECT msgHash FROM MsgHash WHERE msgHash=?1;";
static const char* removeMsgHash = "DELETE FROM MsgHash WHERE since < ?1;";

/* *****************************************************************************
 * SQL statements to process the message trace/state table.
 *
 * Flags: hold the booleans attachment and received
 */
static const int32_t ATTACHMENT = 1;
static const int32_t RECEIVED   = 2;
static const char* dropMsgTrace = "DROP TABLE MsgTrace;";
static const char* createMsgTrace =
        "CREATE TABLE MsgTrace (name VARCHAR NOT NULL, messageId VARCHAR NOT NULL, deviceId VARCHAR NOT NULL, convstate VARCHAR NOT NULL, "
        "attributes VARCHAR NOT NULL, stored TIMESTAMP DEFAULT(STRFTIME('%Y-%m-%dT%H:%M:%f', 'NOW')), flags INTEGER);";
static const char* insertMsgTraceSql =
        "INSERT INTO MsgTrace (name, messageId, deviceId, convstate, attributes, flags) VALUES (?1, ?2, ?3, ?4, ?5, ?6);";
static const char* selectMsgTraceMsgId =
        "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE messageId=?1 ORDER BY ROWID ASC ;";
static const char* selectMsgTraceName =
        "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE name=?1 ORDER BY ROWID ASC ;";
static const char* selectMsgTraceDevId =
        "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE deviceId=?1 ORDER BY ROWID ASC ;";
static const char* selectMsgTraceMsgDevId =
        "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE messageId=?1 AND deviceId=?2 ORDER BY ROWID ASC ;";

// See comment in deleteMsgTrace regarding the not fully qualified SQL statement to remove old trace records.
static const char* removeMsgTrace = "DELETE FROM MsgTrace WHERE STRFTIME('%s', stored)";


#ifdef UNITTESTS
// Used in testing and debugging to do in-depth checks
static void hexdump(const char* title, const unsigned char *s, size_t l) {
    size_t n = 0;

    if (s == nullptr) return;

    fprintf(stderr, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            fprintf(stderr, "\n%04x", static_cast<int>(n));
        fprintf(stderr, " %02x",s[n]);
    }
    fprintf(stderr, "\n");
}
#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
static void hexdump(const char* title, const std::string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}
#pragma clang diagnostic pop
#endif

using namespace zina;

static int32_t getUserVersion(sqlite3* db)
{
    sqlite3_stmt *stmt;

    sqlite3_prepare(db, "PRAGMA user_version", -1, &stmt, nullptr);
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

    sqlite3_prepare(db, statement, -1, &stmt, nullptr);
    int32_t rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

   return rc;
}

SQLiteStoreConv* SQLiteStoreConv::instance_ = nullptr;

SQLiteStoreConv* SQLiteStoreConv::getStore()
{
    if (instance_ != nullptr) {
        return instance_;
    }
    unique_lock<mutex> lck(sqlLock);
    if (instance_ == nullptr)
        instance_ = new SQLiteStoreConv();
    lck.unlock();
    return instance_;
}

SQLiteStoreConv::SQLiteStoreConv() : db(nullptr), keyData_(nullptr), isReady_(false) {}

SQLiteStoreConv::~SQLiteStoreConv()
{
    sqlite3_close(db);
    db = nullptr;
    delete keyData_; keyData_ = nullptr;
}

int SQLiteStoreConv::beginTransaction()
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, beginTransactionSql, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;
}

int SQLiteStoreConv::commitTransaction()
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, commitTransactionSql, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;
}

int SQLiteStoreConv::rollbackTransaction()
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    SQLITE_CHK(SQLITE_PREPARE(db, rollbackTransactionSql, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;
}



int SQLiteStoreConv::beginSavepoint(const std::string& savepointName)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    char cmdBuffer[200];

    snprintf(cmdBuffer, 190, beginSavepointSql, savepointName.c_str());
    SQLITE_CHK(SQLITE_PREPARE(db, cmdBuffer, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;
}

int SQLiteStoreConv::commitSavepoint(const std::string& savepointName)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    char cmdBuffer[200];

    snprintf(cmdBuffer, 190, commitSavepointSql, savepointName.c_str());
    SQLITE_CHK(SQLITE_PREPARE(db, cmdBuffer, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;
}


int SQLiteStoreConv::rollbackSavepoint(const std::string& savepointName)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    char cmdBuffer[200];

    snprintf(cmdBuffer, 190, rollbackSavepointSql, savepointName.c_str());
    SQLITE_CHK(SQLITE_PREPARE(db, cmdBuffer, -1, &stmt, nullptr));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    return sqlResult;

}

static int32_t enableForeignKeys(sqlite3* db)
{
    sqlite3_stmt *stmt;

    sqlite3_prepare_v2(db, "PRAGMA foreign_keys=ON;", -1, &stmt, nullptr);
    int32_t rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return rc;
}

/*
 * SQLite uses the following table structure to manage some internal data
 *
 * CREATE TABLE sqlite_master (
 *   type TEXT,
 *   name TEXT,
 *   tbl_name TEXT,
 *   rootpage INTEGER,
 *   sql TEXT
 * );
 */
int SQLiteStoreConv::openStore(const std::string& name)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    if (keyData_ == nullptr) {
        LOGGER(ERROR, __func__ , " No password defined.");
        return -1;
    }
    unique_lock<mutex> lck(sqlLock);
    // Don't try to open twice
    if (isReady_)
        return SQLITE_CANTOPEN;

    // If name has size 0 then open im-memory DB, handy for testing
    const char *dbName = name.empty() ? ":memory:" : name.c_str();
    int32_t sqlResult;
    sqlCode_ = sqlResult = sqlite3_open_v2(dbName, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nullptr);

    if (sqlCode_ != SQLITE_OK) {
        ERRMSG;
        LOGGER(ERROR, __func__, " Failed to open database: ", sqlCode_, ", ", lastError_);
        return(sqlCode_);
    }
    if (keyData_ != nullptr) {
        sqlite3_key(db, keyData_->data(), static_cast<int>(keyData_->size()));

        Utilities::wipeMemory((void *) keyData_->data(), keyData_->size());
        delete keyData_;
        keyData_ = nullptr;
    }

    enableForeignKeys(db);

    int32_t version = getUserVersion(db);
    if (version != 0) {
        beginTransaction();
        if (updateDb(version, DB_VERSION) != SQLITE_OK) {
            sqlite3_close(db);
            LOGGER(ERROR, __func__ , " <-- update failed, existing version: ", version);
            return SQLITE_ERROR;
        }
        commitTransaction();
    }
    else {
        if (createTables() != SQLITE_OK) {
            sqlite3_close(db);
            LOGGER(ERROR, __func__ , " <-- table creation failed.");
            return sqlCode_;
        }
    }
    setUserVersion(db, DB_VERSION);

    isReady_ = true;
    lck.unlock();
    LOGGER(DEBUGGING, __func__ , " <-- ");
    return SQLITE_OK;
}


int SQLiteStoreConv::createTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;
    int32_t sqlResult;

    /* First drop them, just to be on the save side
     * Ignore errors, there is nothing to drop on empty DB. If ZrtpIdOwn was
     * deleted using DB admin command then we need to drop the remote id table
     * and names also to have a clean state.
     */

    SQLITE_PREPARE(db, dropConversations, -1, &stmt, nullptr);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createConversations, -1, &stmt, nullptr));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    SQLITE_PREPARE(db, dropStagedMk, -1, &stmt, nullptr);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createStagedMk, -1, &stmt, nullptr));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    SQLITE_PREPARE(db, dropPreKeys, -1, &stmt, nullptr);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createPreKeys, -1, &stmt, nullptr));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    SQLITE_PREPARE(db, dropMsgHash, -1, &stmt, nullptr);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createMsgHash, -1, &stmt, nullptr));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    SQLITE_PREPARE(db, dropMsgTrace, -1, &stmt, nullptr);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createMsgTrace, -1, &stmt, nullptr));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlResult = createGroupTables();
    if (sqlResult != SQLITE_OK) {
        goto cleanup1;
    }

    sqlResult = createMessageQueuesTables();
    if (sqlResult != SQLITE_OK) {
        goto cleanup1;
    }

    sqlResult = createVectorClockTables();
    if (sqlResult != SQLITE_OK) {
        goto cleanup1;
    }

    sqlResult = createWaitForAckTables();
    if (sqlResult != SQLITE_OK) {
        goto cleanup1;
    }

    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return SQLITE_OK;

cleanup:
    sqlite3_finalize(stmt);
cleanup1:
    LOGGER(ERROR, __func__, ", SQL error: ", sqlResult, ", ", lastError_);
    return sqlResult;
}

/* *****************************************************************************
 * The SQLite master table.
 *
 * Used to check if we have valid message hash table.
 */
static const char *lookupTables = "SELECT name FROM sqlite_master WHERE type='table' AND name='MsgHash';";

int32_t SQLiteStoreConv::updateDb(int32_t oldVersion, int32_t newVersion) {
    sqlite3_stmt *stmt;

    LOGGER(DEBUGGING, __func__, " -->");

    // Version 2 adds the message hash table
    if (oldVersion == 1) {
        // check if MsgHash table is already available
        SQLITE_PREPARE(db, lookupTables, -1, &stmt, nullptr);
        int32_t rc = sqlite3_step(stmt);
        sqlite3_finalize(stmt);

        // If not then create it
        if (rc != SQLITE_ROW) {
            sqlCode_ = SQLITE_PREPARE(db, createMsgHash, -1, &stmt, nullptr);
            sqlCode_ = sqlite3_step(stmt);
            sqlite3_finalize(stmt);
            if (sqlCode_ != SQLITE_DONE) {
                LOGGER(ERROR, __func__, ", SQL error adding hash table: ", sqlCode_);
                return sqlCode_;
            }
        }
        oldVersion = 2;
    }

    // Version 3 adds the message trace table
    const char* traceTable =
            "CREATE TABLE MsgTrace (name VARCHAR NOT NULL, messageId VARCHAR NOT NULL, deviceId VARCHAR NOT NULL, "
                    "attributes VARCHAR NOT NULL, stored TIMESTAMP DEFAULT(STRFTIME('%Y-%m-%dT%H:%M:%f', 'NOW')), flags INTEGER);";

    if (oldVersion == 2) {
        SQLITE_PREPARE(db, traceTable, -1, &stmt, nullptr);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding trace table: ", sqlCode_);
            return sqlCode_;
        }
        oldVersion = 3;
    }

    // Version 4 adds the conversation state column to the trace table
    if (oldVersion == 3) {
        SQLITE_PREPARE(db, "ALTER TABLE MsgTrace ADD COLUMN convstate VARCHAR;", -1, &stmt, nullptr);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding convstate column: ", sqlCode_);
            return sqlCode_;
        }
        oldVersion = 4;
    }

    if (oldVersion == 4) {
        sqlCode_ = updateGroupDataDb(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }
        oldVersion = 5;
    }

    if (oldVersion == 5) {

        sqlCode_ = updateMessageQueues(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }

        oldVersion = 6;
    }

    if (oldVersion == 6) {
        // Version 6 adds the burn time and avatar info column to the group table, vector clock table
        sqlCode_ = updateGroupDataDb(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }

        sqlCode_ = updateVectorClocksDb(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }
        sqlCode_ = updateWaitForAckDb(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }

        oldVersion = 7;
    }

    // Version 7 adds table for persistent pending group change sets
    if (oldVersion == 7) {
        sqlCode_ = updateGroupDataDb(oldVersion);
        if (sqlCode_ != SQLITE_OK) {
            return sqlCode_;
        }
        oldVersion = 8;
    }

    if (oldVersion != newVersion) {
        LOGGER(ERROR, __func__, ", Version numbers mismatch");
        return SQLITE_ERROR;
    }
    LOGGER(DEBUGGING, __func__ , " <-- ", sqlCode_);
    return SQLITE_OK;
}


// If the result is a BLOB or UTF-8 string then the sqlite3_column_bytes() routine returns the number of bytes in that BLOB or string.
const static char* dummyId = "__DUMMY__";


unique_ptr<set<std::string> >  SQLiteStoreConv::getKnownConversations(const string& ownName, int32_t* sqlCode)
{
    sqlite3_stmt *stmt;
    int32_t nameLen;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");
    unique_ptr<set<string> > names(new set<string>);

    // selectConvNames = "SELECT name FROM Conversations WHERE ownName=?1 ORDER BY name;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConvNames, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        nameLen = sqlite3_column_bytes(stmt, 0);
        string name((const char*)sqlite3_column_text(stmt, 0), static_cast<size_t>(nameLen));
        names->insert(name);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != nullptr)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return names;

}

// This is a deprecated function
#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
shared_ptr<list<string> > SQLiteStoreConv::getLongDeviceIds(const string& name, const string& ownName, int32_t* sqlCode)
{
    LOGGER(DEBUGGING, __func__, " -->");
    shared_ptr<list<string> > devIds = make_shared<list<string> >();

    list<StringUnique> devs;
    int32_t sqlResult = getLongDeviceIds(name, ownName, devs);
    for (const auto& dev : devs) {
        const string tmp(*dev);
        devIds->push_back(tmp);
    }

    if (sqlCode != nullptr)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return devIds;
}
#pragma clang diagnostic pop

int32_t SQLiteStoreConv::getLongDeviceIds(const string& name, const string& ownName, list<StringUnique> &devIds)
{
    sqlite3_stmt *stmt;
    int32_t idLen;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // selectConvDevices = "SELECT longDevId FROM Conversations WHERE name=?1 AND ownName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConvDevices, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        idLen = sqlite3_column_bytes(stmt, 0);
        StringUnique id(new string((const char*)sqlite3_column_text(stmt, 0), static_cast<size_t>(idLen)));
        if (id->compare(0, id->size(), dummyId, id->size()) == 0 || id->find('_') != string::npos) {
            continue;
        }
        devIds.push_back(move(id));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


// ***** Session store
StringUnique SQLiteStoreConv::loadConversation(const string& name, const string& longDevId, const string& ownName, int32_t* sqlCode) const
{ 
    sqlite3_stmt *stmt;
    int32_t len;
    StringUnique data;
    int32_t sqlResult;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }

    // selectConversation = "SELECT sessionData FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {        // session found, return session record
        // Get the session data
        LOGGER(DEBUGGING, __func__, " Conversation session found");
        len = sqlite3_column_bytes(stmt, 0);
        data = StringUnique(new string((const char*)sqlite3_column_blob(stmt, 0), len));
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != nullptr)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return data;
}

int32_t SQLiteStoreConv::storeConversation(const string& name, const string& longDevId, const string& ownName, const string& data)
{
    static char savepointName[] = "conversation";

    sqlite3_stmt *stmt;
    int32_t sqlResult;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }
    // Lock the DB in this case because it's a two-step procedure where we use
    // some data from the shared DB pointer (sqlite3_changes(db))
    unique_lock<mutex> lck(sqlLock);

    // updateConversation = "UPDATE Conversations SET data=?1, WHERE name=?2 AND longDevId=?3 AND ownName=?4;";
    SQLITE_CHK(SQLITE_PREPARE(db, updateConversation, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, data.data(), static_cast<int32_t>(data.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    beginSavepoint(savepointName);
    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    stmt = nullptr;

    if (!SQL_FAIL(sqlResult) && sqlite3_changes(db) <= 0) {
        // insertConversation = "INSERT OR IGNORE INTO Conversations (name, secondName, longDevId, data, ownName) VALUES (?1, ?2, ?3, ?4, ?5);";
        SQLITE_CHK(SQLITE_PREPARE(db, insertConversation, -1, &stmt, nullptr));
        SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
        SQLITE_CHK(sqlite3_bind_null(stmt, 2));
        SQLITE_CHK(sqlite3_bind_text(stmt, 3, devId, devIdLen, SQLITE_STATIC));
        SQLITE_CHK(sqlite3_bind_blob(stmt, 4, data.data(), static_cast<int32_t>(data.size()), SQLITE_STATIC));
        SQLITE_CHK(sqlite3_bind_text(stmt, 5, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));
        sqlResult = sqlite3_step(stmt);
        ERRMSG;
    }
    if (!SQL_FAIL(sqlResult)) {
        commitSavepoint(savepointName);
    }
    else {
        LOGGER(ERROR, __func__, " Store conversation failed, rolling back transaction");
        rollbackSavepoint(savepointName);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    lck.unlock();
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

bool SQLiteStoreConv::hasConversation(const string& name, const string& longDevId, const string& ownName, int32_t* sqlCode) const 
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    bool retVal = false;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }
    // selectConversation = "SELECT iv, data FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;
    retVal = sqlResult == SQLITE_ROW;
    LOGGER(DEBUGGING, __func__, " Found conversation: ", retVal);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != nullptr)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return retVal;
}

int32_t SQLiteStoreConv::deleteConversation(const string& name, const string& longDevId, const string& ownName)
{
    sqlite3_stmt *stmt = nullptr;
    int32_t sqlResult;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }

    //removeConversation = "DELETE FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeConversation, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteConversationsName(const string& name, const string& ownName)
{
    sqlite3_stmt *stmt = nullptr;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");
    if (isGroupMember(name)) {
        sqlResult = SQLITE_CONSTRAINT;
        goto cleanup;
    }
    // removeConversations = "DELETE FROM Conversations WHERE name=?1 AND ownName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeConversations, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::loadStagedMks(const string& name, const string& longDevId, const string& ownName, list<string> &keys) const
{
    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }
    // selectStagedMks = "SELECT ivkeymk FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectStagedMks, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get the MK and its iv
        len = sqlite3_column_bytes(stmt, 0);
        if (len > 0) {
            string mkivenc((const char *) sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
            keys.push_back(mkivenc);
        }
        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

static bool hasStagedMk(sqlite3* db, const string& name, const string& longDevId, const string& ownName, const string& MKiv)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    // char* hasStagedMkSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3 AND ivkeymk=?4) WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_PREPARE(db, hasStagedMkSql, -1, &stmt, nullptr);
    sqlite3_bind_text(stmt,  1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC);
    sqlite3_bind_text(stmt,  2, longDevId.data(), static_cast<int32_t>(longDevId.size()), SQLITE_STATIC);
    sqlite3_bind_text(stmt,  3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC);
    sqlite3_bind_blob(stmt,  4, MKiv.data(), static_cast<int32_t>(MKiv.size()), SQLITE_STATIC);

    sqlResult = sqlite3_step(stmt);

    if (sqlResult == SQLITE_ROW) {
        exists = sqlite3_column_int(stmt, 1);
    }
    else
        LOGGER(INFO, __func__, " SQL error: ", sqlResult);


    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", exists);
    return exists == 1;
}

int32_t SQLiteStoreConv::insertStagedMk(const string& name, const string& longDevId, const string& ownName, const string& MKiv)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult = SQLITE_OK;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }

    if (hasStagedMk(db, name, string(devId), ownName, MKiv)) {
        sqlCode_ = sqlResult;
        LOGGER(DEBUGGING, __func__, " <-- MK exists in DB, skip");
        return sqlResult;
    }

//     insertStagedMkSql =
//     "INSERT OR REPLACE INTO stagedMk (name, longDevId, ownName, since, otherkey, ivkeymk, ivkeyhdr) "
//     "VALUES(?1, ?2, ?3, strftime('%s', ?4, 'unixepoch'), ?5, ?6, ?7);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertStagedMkSql, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 4, time(0)));
    SQLITE_CHK(sqlite3_bind_null(stmt,  5));
    SQLITE_CHK(sqlite3_bind_blob(stmt,  6, MKiv.data(), static_cast<int32_t>(MKiv.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_null(stmt,  7));

    sqlResult = sqlite3_step(stmt);
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteStagedMk(const string& name, const string& longDevId, const string& ownName, const string& MKiv)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    const char* devId;
    int32_t devIdLen;

    LOGGER(DEBUGGING, __func__, " -->");
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = static_cast<int32_t>(longDevId.size());
    }
    else {
        devId = dummyId;
        devIdLen = static_cast<int32_t>(strlen(dummyId));
    }
    // removeStagedMk = "DELETE FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3 AND ivkeymk=?4;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeStagedMk, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), static_cast<int32_t>(ownName.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 4, MKiv.data(), static_cast<int32_t>(MKiv.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteStagedMk(time_t timestamp)
{
    sqlite3_stmt *stmt;
//    int32_t cleaned;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");
    // removeStagedMkTime = "DELETE FROM stagedMk WHERE since < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeStagedMkTime, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlResult= sqlite3_step(stmt);
//    cleaned = sqlite3_changes(db);
//    LOGGER(INFO, "Number of removed old MK: ", cleaned);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

// ******** PreKey store
int32_t SQLiteStoreConv::loadPreKey(const int32_t preKeyId, string &preKeyData) const
{
    sqlite3_stmt *stmt;
    int32_t len;
    int32_t sqlResult;

    // selectPreKey = "SELECT preKeyData FROM PreKeys WHERE keyid=?1;";

    // SELECT iv, preKeyData FROM PreKeys WHERE keyid=?1 ;
    LOGGER(DEBUGGING, __func__, " -->");
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKey, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult == SQLITE_ROW) {        // No such pre key
        // Get the pre key data
        len = sqlite3_column_bytes(stmt, 0);
        preKeyData.assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t >(len));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::storePreKey(int32_t preKeyId, const string& preKeyData)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // insertPreKey = "INSERT INTO PreKeys (keyId, preKeyData) VALUES (?1, ?2);";
    LOGGER(DEBUGGING, __func__, " -->");

    SQLITE_CHK(SQLITE_PREPARE(db, insertPreKey, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, preKeyData.data(), static_cast<int32_t>(preKeyData.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

bool SQLiteStoreConv::containsPreKey(int32_t preKeyId, int32_t* sqlCode) const
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    bool retVal = false;

    LOGGER(DEBUGGING, __func__, " -->");

    // SELECT preKeyData FROM PreKeys WHERE keyid=?1 ;
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKey, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;
    retVal = (sqlResult == SQLITE_ROW);
    LOGGER(DEBUGGING, __func__, " Found preKey: ", retVal);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != nullptr)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return retVal;
}

int32_t SQLiteStoreConv::removePreKey(int32_t preKeyId)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // DELETE FROM PreKeys WHERE keyId=?1
    SQLITE_CHK(SQLITE_PREPARE(db, deletePreKey, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlResult = sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <--", sqlResult);
    return sqlResult;
}

#pragma clang diagnostic push
#pragma ide diagnostic ignored "OCUnusedGlobalDeclarationInspection"
void SQLiteStoreConv::dumpPreKeys() const
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    //  selectPreKeyAll = "SELECT keyId, preKeyData FROM PreKeys;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKeyAll, -1, &stmt, nullptr));

    while ((sqlResult = sqlite3_step(stmt)) == SQLITE_ROW) {
        sqlite3_column_int(stmt, 0);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
}
#pragma clang diagnostic pop

// ***** Message hash / time table to detect duplicate message from server

int32_t SQLiteStoreConv::insertMsgHash(const string& msgHash)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* insertMsgHashSql = "INSERT INTO MsgHash (msgHash, since) VALUES (?1, strftime('%s', ?2, 'unixepoch'));";
    SQLITE_CHK(SQLITE_PREPARE(db, insertMsgHashSql, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_blob(stmt,  1, msgHash.data(), static_cast<int32_t>(msgHash.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, time(0)));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::hasMsgHash(const string& msgHash)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* selectMsgHash = "SELECT msgHash FROM MsgHash WHERE msgHash=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectMsgHash, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, msgHash.data(), static_cast<int32_t>(msgHash.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteMsgHashes(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeMsgHash = "DELETE FROM MsgHash WHERE since < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeMsgHash, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::insertMsgTrace(const string &name, const string &messageId, const string &deviceId,
                                        const string& convState, const string &attributes, bool attachment, bool received)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    int32_t flag = attachment ? ATTACHMENT : 0;
    flag = received ? flag | RECEIVED : flag;

    // char* insertMsgTraceSql = "INSERT INTO MsgTrace (name, messageId, deviceId, convstate, attributes, flags) VALUES (?1, ?2, ?3, ?4, ?5);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertMsgTraceSql, -1, &stmt, nullptr));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, messageId.data(), static_cast<int32_t>(messageId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, convState.data(), static_cast<int32_t>(convState.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 5, attributes.data(), static_cast<int32_t>(attributes.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  6, flag));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::loadMsgTrace(const string &name, const string &messageId, const string &deviceId, list<StringUnique> &traceRecords)
{
    sqlite3_stmt *stmt = nullptr;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    int32_t selection = 0;
    if (!messageId.empty() && !deviceId.empty())
        selection = 1;
    else if (!name.empty())
        selection = 2;
    else if (!messageId.empty())
        selection = 3;
    else if (!deviceId.empty())
        selection = 4;

    switch (selection) {
        case 1:
            // char* selectMsgTraceMsgDevId =
            //"SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE messageId=?1 AND deviceId=?2 ORDER BY ROWID ASC ;";
            SQLITE_CHK(SQLITE_PREPARE(db, selectMsgTraceMsgDevId, -1, &stmt, nullptr));
            SQLITE_CHK(sqlite3_bind_text(stmt, 1, messageId.data(), static_cast<int32_t>(messageId.size()), SQLITE_STATIC));
            SQLITE_CHK(sqlite3_bind_text(stmt, 2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
            break;
        case 2:
            // char* selectMsgTraceName =
            //      "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE name=?1 ORDER BY ROWID ASC ;";
            SQLITE_CHK(SQLITE_PREPARE(db, selectMsgTraceName, -1, &stmt, nullptr));
            SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
            break;
        case 3:
            // char* selectMsgTraceMsgId =
            //     "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE messageId=?1 ORDER BY ROWID ASC ;";
            SQLITE_CHK(SQLITE_PREPARE(db, selectMsgTraceMsgId, -1, &stmt, nullptr));
            SQLITE_CHK(sqlite3_bind_text(stmt, 1, messageId.data(), static_cast<int32_t>(messageId.size()), SQLITE_STATIC));
            break;
        case 4:
            // char* selectMsgTraceDevId =
            //     "SELECT name, messageId, deviceId, convstate, attributes, STRFTIME('%Y-%m-%dT%H:%M:%f', stored), flags FROM MsgTrace WHERE deviceId=?1 ORDER BY ROWID ASC ;";
            SQLITE_CHK(SQLITE_PREPARE(db, selectMsgTraceDevId, -1, &stmt, nullptr));
            SQLITE_CHK(sqlite3_bind_text(stmt, 1, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
            break;
        default:
            sqlResult = SQLITE_ERROR;
            goto cleanup;
            break;
    }

    sqlResult= sqlite3_step(stmt);
    ERRMSG;
    if (sqlResult != SQLITE_ROW) {        // No stored records for this selection
        LOGGER(INFO, __func__, " <-- No message trace records for: ", name, messageId, deviceId);
        goto cleanup;
    }
    while (sqlResult == SQLITE_ROW) {
        // Get trace fields and create a JSON formatted string
        JsonUnique jsonUnique(cJSON_CreateObject());
        cJSON* root = jsonUnique.get();
        // name is usually the SC UID string
        cJSON_AddStringToObject(root, "name", (const char*)sqlite3_column_text(stmt, 0));
        cJSON_AddStringToObject(root, "msgId", (const char*)sqlite3_column_text(stmt, 1));
        cJSON_AddStringToObject(root, "devId", (const char*)sqlite3_column_text(stmt, 2));
        cJSON_AddStringToObject(root, "state", (const char*)sqlite3_column_text(stmt, 3));
        cJSON_AddStringToObject(root, "attr", (const char*)sqlite3_column_text(stmt, 4));
        cJSON_AddStringToObject(root, "time", (const char*)sqlite3_column_text(stmt, 5));

        int32_t flag = sqlite3_column_int(stmt, 6);
        cJSON_AddNumberToObject(root, "received", ((flag & RECEIVED) == RECEIVED) ? 1 : 0);
        cJSON_AddNumberToObject(root, "attachment", ((flag & ATTACHMENT) == ATTACHMENT) ? 1 : 0);

        CharUnique out(cJSON_PrintUnformatted(root));
        traceRecords.push_back(StringUnique(new string(out.get())));

        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sqlResult;
}


int32_t SQLiteStoreConv::deleteMsgTrace(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // Bind with two strftime functions and compare them with < doesn't seem to work. Thus
    // we use the trick and compile the full SQL statement as string and then prepare it.
    // This is also required because for the trace records we store the timestamp in ISO
    // format with fractions of a second to get a more precise timestamp. Otherwise we would
    // have a timestamp with a precision of a full second. To remove old trace records it's
    // OK to use a second based timestamp.

    // char* removeMsgTrace = "DELETE FROM MsgTrace WHERE STRFTIME('%s', stored)";
    char strfTime[400];
    snprintf(strfTime, sizeof(strfTime)-1, "%s < strftime('%%s', %ld, 'unixepoch');", removeMsgTrace, timestamp);

    SQLITE_CHK(SQLITE_PREPARE(db, strfTime, -1, &stmt, nullptr));

    // The following sequence somehow doesn't work even if the removeMsgTrace terminates with ' <?1;'
//    SQLITE_CHK(SQLITE_PREPARE(db, removeMsgTrace, -1, &stmt, NULL));
//    SQLITE_CHK(sqlite3_bind_text(stmt, 1, strfTime, -1, SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

#pragma clang diagnostic pop
