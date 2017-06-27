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
 * SQL statements to store vector clocks.
 *
 * The table stores vector clocks and groups them by an id and an event type. An id could be a group
 * id, a some other unique id such as UUID according to RFC4122, type 4.
 *
 * The event type is a simple 32-bit integer that defines an event. The event type must be unique inside
 * an id.
 *
 * The vector clock data is a blob, thus the table stores serialized data. When inserting data
 * it's always an INSERT OR REPLACE and the table has only one row per (id, event type) tuple.
 *
 */
static const char* dropVectorClocks = "DROP TABLE VectorClocks;";
static const char* createVectorClocks = "CREATE TABLE if NOT EXISTS VectorClocks"
        "(id VARCHAR NOT NULL, type INTEGER, data BLOB, PRIMARY KEY(id, type));";
static const char* insertVectorClocksSql = "INSERT OR REPLACE INTO VectorClocks (id, type, data) "
        "VALUES (?1, ?2, ?3);";
static const char* selectVectorClocks = "SELECT data FROM VectorClocks WHERE id=?1 AND type=?2;";
static const char* removeVectorClock = "DELETE FROM VectorClocks WHERE id=?1 AND type=?2;";
static const char* removeVectorClocks = "DELETE FROM VectorClocks WHERE id=?1;";

using namespace zina;


int SQLiteStoreConv::createVectorClockTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;
    int32_t sqlResult;

    SQLITE_PREPARE(db, dropVectorClocks, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createVectorClocks, -1, &stmt, NULL));
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

int32_t SQLiteStoreConv::updateVectorClocksDb(int32_t oldVersion)
{
    sqlite3_stmt *stmt;

    LOGGER(DEBUGGING, __func__, " -->");

    if (oldVersion == 6) {
        SQLITE_PREPARE(db, createVectorClocks, -1, &stmt, NULL);
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

int32_t SQLiteStoreConv::insertReplaceVectorClock(const string &id, int32_t type, const string &vectorClock)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    LOGGER(DEBUGGING, __func__, " --> ");

    // char* insertVectorClocksSql = "INSERT OR REPLACE INTO VectorClocks (id, type, data) VALUES (?1, ?2, ?3);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertVectorClocksSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, id.data(), static_cast<int32_t>(id.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, type));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, vectorClock.data(), static_cast<int32_t>(vectorClock.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::loadVectorClock(const string& id, int32_t type, string *vectorClock)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    LOGGER(DEBUGGING, __func__, " --> ");

    // char* selectVectorClocks = "SELECT data FROM VectorClocks WHERE id=?1 AND type=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectVectorClocks, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, id.data(), static_cast<int32_t>(id.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, type));

    sqlResult = sqlite3_step(stmt);
    vectorClock->clear();
    if (SQL_FAIL(sqlResult)) {
        ERRMSG;
    }
    else if (sqlResult == SQLITE_ROW) {
        const int32_t len = sqlite3_column_bytes(stmt, 0);
        vectorClock->assign((const char*)sqlite3_column_blob(stmt, 0), static_cast<size_t>(len));
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteVectorClock(const string& id, int32_t type)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    LOGGER(DEBUGGING, __func__, " --> ");

    // char* removeVectorClock = "DELETE FROM VectorClocks WHERE id=?1 AND type=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeVectorClock, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, id.data(), static_cast<int32_t>(id.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, type));

    sqlResult = sqlite3_step(stmt);
    if (SQL_FAIL(sqlResult)) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteVectorClocks(const string& id)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    LOGGER(DEBUGGING, __func__, " --> ");

    // char* removeVectorClocks = "DELETE FROM VectorClocks WHERE id=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeVectorClocks, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, id.data(), static_cast<int32_t>(id.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    if (SQL_FAIL(sqlResult)) {
        ERRMSG;
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}
