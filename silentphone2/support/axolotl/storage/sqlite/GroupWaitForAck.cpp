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
// Created by werner on 06.02.17.
//

#include "SQLiteStoreConv.h"
#include "SQLiteStoreInternal.h"

using namespace std;

/* *****************************************************************************
 * SQL statements to store unacknowledged group updates.
 *
 */
static const char *dropWaitForAck = "DROP TABLE waitForAck;";
static const char *createWaitForAck = "CREATE TABLE IF NOT EXISTS waitForAck (groupId VARCHAR NOT NULL, deviceId BLOB NOT NULL, "
        "updateId BLOB NOT NULL, updateType INTEGER, since TIMESTAMP, PRIMARY KEY(groupId, deviceId, updateId, updateType))";

static const char *insertWaitForAck = "INSERT INTO waitForAck (groupId, deviceId, updateId, updateType, since) VALUES (?1, ?2, ?3, ?4, ?5);";
static const char *hasWaitForAck = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateId=?3 AND updateType=?4)"
        " WHEN 1 THEN 1 ELSE 0 END;";

static const char *hasWaitForAckGroupUpdate = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND updateId=?2)"
        " WHEN 1 THEN 1 ELSE 0 END;";

static const char *hasWaitForAckGroupDevice = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND deviceId=?2)"
        " WHEN 1 THEN 1 ELSE 0 END;";

static const char *removeWaitForAck = "DELETE FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateId=?3 AND updateType=?4;";

// Remove all records of a device with a specific update type
static const char* removeWaitForAckType = "DELETE FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateType=?3;";

// Remove all records of a group, used when leaving the group
static const char* removeWaitForAckGroup = "DELETE FROM waitForAck WHERE groupId=?1;";

static const char* cleanWaitForAck = "DELETE FROM waitForAck WHERE since < ?1;";

using namespace zina;

int32_t SQLiteStoreConv::createWaitForAckTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;
    int32_t sqlResult;

    SQLITE_PREPARE(db, dropWaitForAck, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createWaitForAck, -1, &stmt, NULL));
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

int32_t SQLiteStoreConv::updateWaitForAckDb(int32_t oldVersion)
{
    sqlite3_stmt *stmt;

    LOGGER(DEBUGGING, __func__, " -->");

    if (oldVersion == 6) {
        SQLITE_PREPARE(db, createWaitForAck, -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding vector clocks table: ", sqlCode_);
            return sqlCode_;
        }
        return SQLITE_OK;
    }
    return SQLITE_OK;
}

int32_t SQLiteStoreConv::insertWaitAck(const string &groupId, const string &deviceId, const string &updateId, int32_t updateType)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char *insertWaitForAck = "INSERT INTO waitForAck (groupId, deviceId, updateId, updateType) VALUES (?1, ?2, ?3, ?4);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertWaitForAck, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt,  2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt,  3, updateId.data(), static_cast<int32_t>(updateId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,   4, updateType));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 5, time(0)));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::removeWaitAck(const string &groupId, const string &deviceId, const string &updateId, int32_t updateType)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeWaitForAck = "DELETE FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateId=?3 AND updateType=?4;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeWaitForAck, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 3, updateId.data(), static_cast<int32_t>(updateId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  4, updateType));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::removeWaitAckWithType(const string &groupId, const string &deviceId, int32_t updateType)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeWaitForAckType = "DELETE FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateType=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeWaitForAckType, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  3, updateType));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


int32_t SQLiteStoreConv::removeWaitAckWithGroup(const string &groupId)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeWaitForAckGroup = "DELETE FROM waitForAck WHERE groupId=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeWaitForAckGroup, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


bool SQLiteStoreConv::hasWaitAck(const string &groupId, const string &deviceId, const string &updateId, int32_t updateType, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    LOGGER(DEBUGGING, __func__, " --> ");

    // char *hasWaitForAck = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND deviceId=?2 AND updateId=?3 AND updateType=?4)"
    // " WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, hasWaitForAck, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 3, updateId.data(), static_cast<int32_t>(updateId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  4, updateType));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_ROW) {
        ERRMSG;
    }
    exists = sqlite3_column_int(stmt, 1);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return exists == 1;
}

bool SQLiteStoreConv::hasWaitAckGroupUpdate(const string &groupId, const string &updateId, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    LOGGER(DEBUGGING, __func__, " --> ");

    // char *hasWaitForAckGroupUpdate = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND updateId=?2)"
    // " WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, hasWaitForAckGroupUpdate, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, updateId.data(), static_cast<int32_t>(updateId.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_ROW) {
        ERRMSG;
    }
    exists = sqlite3_column_int(stmt, 1);

    cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return exists == 1;
}

bool SQLiteStoreConv::hasWaitAckGroupDevice(const string &groupId, const string &deviceId, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    LOGGER(DEBUGGING, __func__, " --> ");

    // char *hasWaitForAckGroupDevice = "SELECT NULL, CASE EXISTS (SELECT 0 FROM waitForAck WHERE groupId=?1 AND deviceId=?2)"
    // " WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, hasWaitForAckGroupUpdate, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, deviceId.data(), static_cast<int32_t>(deviceId.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_ROW) {
        ERRMSG;
    }
    exists = sqlite3_column_int(stmt, 1);

    cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return exists == 1;
}

int32_t SQLiteStoreConv::cleanWaitAck(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* cleanWaitForAck = "DELETE FROM waitForAck WHERE since < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, cleanWaitForAck, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}
