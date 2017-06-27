/*
Copyright 2017 Silent Circle, LLC

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
// Created by werner on 04.03.17.
//

#include "SQLiteStoreConv.h"
#include "SQLiteStoreInternal.h"

using namespace std;

/* *****************************************************************************
 * SQL statements to process received raw, encrypted message data
 *
 * Regarding the use of 'AUTOINCREMENT' refer to sqlite documentation: https://www.sqlite.org/autoinc.html
 * It actually guarantees a monotonically increasing sequence number which is important because we like
 * to process messages in order we received them.
 */
static const char* dropReceivedRaw = "DROP TABLE receivedRaw;";
static const char* createReceivedRaw =
        "CREATE TABLE IF NOT EXISTS receivedRaw (sequence INTEGER PRIMARY KEY AUTOINCREMENT, rawData BLOB NOT NULL, "
                "uid VARCHAR, displayName VARCHAR, inserted TIMESTAMP DEFAULT(strftime('%s', 'NOW')));";
static const char* insertReceivedRawSql = "INSERT INTO receivedRaw (rawData, uid, displayName) VALUES (?1, ?2, ?3);";
static const char* selectReceivedRaw = "SELECT sequence, rawData, uid, displayName FROM receivedRaw ORDER BY sequence ASC;";
static const char* removeReceivedRaw = "DELETE FROM receivedRaw WHERE sequence=?1;";
static const char* cleanReceivedRaw = "DELETE FROM receivedRaw WHERE inserted < ?1;";

/* *****************************************************************************
 * SQL statements to process temporarily stored received message data
 *
 */
static const char* dropTempMsg = "DROP TABLE TempMsg;";
static const char* createTempMsg =
        "CREATE TABLE IF NOT EXISTS TempMsg (sequence INTEGER PRIMARY KEY AUTOINCREMENT, messageData VARCHAR NOT NULL, "
                "supplementData VARCHAR, msgType INTEGER, inserted TIMESTAMP DEFAULT(strftime('%s', 'NOW')));";
static const char* insertTempMsgSql = "INSERT INTO TempMsg (messageData, supplementData, msgType) VALUES (?1, ?2, ?3);";
static const char* selectTempMsg = "SELECT sequence, messageData, supplementData, msgType FROM TempMsg ORDER BY sequence ASC;";
static const char* removeTempMsg = "DELETE FROM TempMsg WHERE sequence=?1;";
static const char* cleanTempMsgSql = "DELETE FROM TempMsg WHERE inserted < ?1;";

using namespace zina;

static int64_t getSequenceNumber(sqlite3* db, string tableName)
{
    static const char* selectSeq = "select seq from sqlite_sequence where name=?1;";
    sqlite3_stmt *stmt;

    sqlite3_prepare(db, selectSeq, -1, &stmt, NULL);
    sqlite3_bind_text(stmt, 1, tableName.data(), static_cast<int32_t>(tableName.size()), SQLITE_STATIC);
    sqlite3_step(stmt);
    int64_t sequence = sqlite3_column_int64(stmt, 0);
    sqlite3_finalize(stmt);
    return sequence;
}

int32_t SQLiteStoreConv::createMessageQueuesTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;
    int32_t sqlResult;

    SQLITE_PREPARE(db, dropReceivedRaw, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createReceivedRaw, -1, &stmt, NULL));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    SQLITE_PREPARE(db, dropTempMsg, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createTempMsg, -1, &stmt, NULL));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    LOGGER(DEBUGGING, __func__ , " <-- ", sqlResult);
    return SQLITE_OK;

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(ERROR, __func__, ", SQL error: ", sqlResult, ", ", lastError_);
    return sqlResult;


}

int32_t SQLiteStoreConv::updateMessageQueues(int32_t oldVersion)
{
    sqlite3_stmt *stmt;

    (void)oldVersion;

    LOGGER(DEBUGGING, __func__, " -->");

    SQLITE_PREPARE(db, createReceivedRaw, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        LOGGER(ERROR, __func__, ", SQL error adding receive raw table: ", sqlCode_);
        return sqlCode_;
    }

    SQLITE_PREPARE(db, createTempMsg, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        LOGGER(ERROR, __func__, ", SQL error adding temporary message table: ", sqlCode_);
        return sqlCode_;
    }
    LOGGER(DEBUGGING, __func__ , " <--");
    return SQLITE_OK;
}

int32_t SQLiteStoreConv::insertReceivedRawData(const string& rawData, const string& uid, const string& displayName, int64_t* sequence)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* insertReceivedRawSql = "INSERT INTO receivedRaw (rawData, uid, displayName) VALUES (?1, ?2, ?3);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertReceivedRawSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, rawData.data(), static_cast<int32_t>(rawData.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, uid.data(), static_cast<int32_t>(uid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, displayName.data(), static_cast<int32_t>(displayName.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    *sequence = getSequenceNumber(db, "receivedRaw");
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::loadReceivedRawData(list<unique_ptr<StoredMsgInfo> >* rawMessageData)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t len;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* selectReceivedRaw = "SELECT sequence, rawData, uid, displayName FROM receivedRaw ORDER BY sequence ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectReceivedRaw, -1, &stmt, NULL));

    sqlResult= sqlite3_step(stmt);
    while (sqlResult == SQLITE_ROW) {
        auto msgInfo = new StoredMsgInfo;
        msgInfo->sequence = sqlite3_column_int64(stmt, 0);

        // Get raw message data
        len = sqlite3_column_bytes(stmt, 1);
        msgInfo->info_rawMsgData = string((const char*)sqlite3_column_blob(stmt, 1), static_cast<size_t>(len));
        msgInfo->info_uid = (const char*)sqlite3_column_text(stmt, 2);
        msgInfo->info_displayName = (const char*)sqlite3_column_text(stmt, 3);
        rawMessageData->push_back(unique_ptr<StoredMsgInfo>(msgInfo));

        sqlResult = sqlite3_step(stmt);
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteReceivedRawData(int64_t sequence)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeReceivedRaw = "DELETE FROM receivedRaw WHERE sequence=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeReceivedRaw, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, sequence));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <--", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::cleanReceivedRawData(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* cleanReceivedRaw = "DELETE FROM receivedRaw WHERE inserted < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, cleanReceivedRaw, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

/*
static const char* selectTempMsg = "SELECT sequence, messageData, supplementData, msgType FROM TempMsg ORDER BY sequence ASC;";

 */
int32_t SQLiteStoreConv::insertTempMsg(const string& messageData, const string& supplementData, int32_t msgType, int64_t* sequence)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* insertTempMsgSql = "INSERT INTO TempMsg (messageData, supplementData, msgType) VALUES (?1, ?2, ?3);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertTempMsgSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, messageData.data(), static_cast<int32_t>(messageData.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, supplementData.data(), static_cast<int32_t>(supplementData.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt, 3, msgType));

    sqlResult= sqlite3_step(stmt);
    *sequence = getSequenceNumber(db, "TempMsg");
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::loadTempMsg(list<unique_ptr<StoredMsgInfo> >* tempMessageData)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* selectTempMsg = "SELECT sequence, messageData, supplementData, msgType FROM TempMsg ORDER BY sequence ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectTempMsg, -1, &stmt, NULL));

    sqlResult= sqlite3_step(stmt);
    while (sqlResult == SQLITE_ROW) {
        auto msgInfo = new StoredMsgInfo;
        msgInfo->sequence = sqlite3_column_int64(stmt, 0);
        msgInfo->info_msgDescriptor = (const char*)sqlite3_column_text(stmt, 1);
        msgInfo->info_supplementary = (const char*)sqlite3_column_text(stmt, 2);
        msgInfo->info_msgType = sqlite3_column_int(stmt, 3);
        tempMessageData->push_back(unique_ptr<StoredMsgInfo>(msgInfo));

        sqlResult = sqlite3_step(stmt);
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteTempMsg(int64_t sequence)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeTempMsg = "DELETE FROM TempMsg WHERE sequence=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeTempMsg, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, sequence));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <--", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::cleanTempMsg(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* cleanTempMsgSql = "DELETE FROM TempMsg WHERE inserted < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, cleanTempMsgSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}
