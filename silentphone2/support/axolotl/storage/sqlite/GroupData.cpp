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

#include <zrtp/crypto/sha256.h>
#include <zrtp/crypto/sha2.h>

#include "../../interfaceApp/JsonStrings.h"
#include "../../Constants.h"
#include "../../util/Utilities.h"

using namespace std;

/* *****************************************************************************
 * SQL statements to process group chat table
 *
 */
static const char* dropGroups = "DROP TABLE groups;";
static const char* createGroups =
        "CREATE TABLE groups (groupId VARCHAR NOT NULL PRIMARY KEY, name VARCHAR NOT NULL, ownerId VARCHAR NOT NULL, "
                "description VARCHAR, memberCount INTEGER, maxMembers INTEGER, attributes INTEGER, lastModified TIMESTAMP DEFAULT(strftime('%s', 'NOW')),"
                "burnTime INTEGER, avatarInfo VARCHAR, burnMode INTEGER);";
static const char* insertGroupsSql =
        "INSERT INTO groups (groupId, name, ownerId, description, maxMembers, memberCount, attributes) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7);";
static const char* selectAllGroups = "SELECT groupId, name, ownerId, description, maxMembers, memberCount, attributes, lastModified, burnTime, burnMode, avatarInfo FROM groups;";
static const char* selectGroup = "SELECT groupId, name, ownerId, description, maxMembers, memberCount, attributes, lastModified, burnTime, burnMode, avatarInfo FROM groups WHERE groupId=?1;";
static const char* updateGroupMaxMember = "UPDATE groups SET maxMembers=?1 WHERE groupId=?2;";
static const char* incrementGroupMemberCount = "UPDATE groups SET memberCount=memberCount+1 WHERE groupId=?1;";
static const char* decrementGroupMemberCount = "UPDATE groups SET memberCount=memberCount-1 WHERE groupId=?1;";
static const char* setGroupMemberCount = "UPDATE groups SET memberCount=?1 WHERE groupId=?2;";
static const char* setGroupAttributeSql = "UPDATE groups SET attributes=attributes|?1, lastModified=?2 WHERE groupId=?3;";
static const char* clearGroupAttributeSql = "UPDATE groups SET attributes=attributes&~?1, lastModified=?2 WHERE groupId=?3;";
static const char* selectGroupAttributeSql = "SELECT attributes, lastModified FROM groups WHERE groupId=?1;";
static const char* removeGroup = "DELETE FROM groups WHERE groupId=?1;";
static const char* hasGroupSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM groups WHERE groupId=?1) WHEN 1 THEN 1 ELSE 0 END;";

static const char* setGroupNewName = "UPDATE groups SET name=?1, lastModified=?2 WHERE groupId=?3;";
static const char* setGroupBurn = "UPDATE groups SET burnTime=?1, burnMode=?2, lastModified=?3 WHERE groupId=?4;";
static const char* setGroupAvatar = "UPDATE groups SET avatarInfo=?1, lastModified=?2 WHERE groupId=?3;";

/* *****************************************************************************
 * SQL statements to process group member table
 *
 * The columns 'deviceId' and 'ownName' are available for possible future extensions, currently not used.
 */
static const char* dropMembers = "DROP TABLE members;";
static const char* createMembers =
        "CREATE TABLE members (memberId VARCHAR NOT NULL, groupId VARCHAR NOT NULL, deviceId VARCHAR, ownName VARCHAR, "
                "attributes INTEGER, lastModified TIMESTAMP DEFAULT(strftime('%s', 'NOW')), "
                "PRIMARY KEY(memberId, groupId, deviceId), FOREIGN KEY(groupId) REFERENCES groups(groupId));";
static const char* insertMemberSql = "INSERT INTO members (groupId, memberId, attributes) VALUES (?1, ?2, ?3);";
static const char* removeMember = "DELETE FROM members WHERE groupId=?1 AND memberId=?2;";
static const char* removeAllMembers = "DELETE FROM members WHERE groupId=?1;";
static const char* selectGroupMembers = "SELECT groupId, memberId, attributes, lastModified FROM members WHERE groupId=?1 ORDER BY memberId ASC;";
static const char* selectMember =
        "SELECT groupId, memberId, attributes, lastModified FROM members WHERE groupId=?1 AND memberId=?2 ORDER BY memberId ASC;";
static const char* setMemberAttributeSql = "UPDATE members SET attributes=attributes|?1, lastModified=?2 WHERE groupId=?3 AND memberId=?4;";
static const char* clearMemberAttributeSql = "UPDATE members SET attributes=attributes&~?1, lastModified=?2  WHERE groupId=?3 AND memberId=?4;";
static const char* selectMemberAttributeSql = "SELECT attributes, lastModified FROM members WHERE groupId=?1 AND memberId=?2;";
static const char* selectForHash = "SELECT DISTINCT memberId FROM members WHERE groupId=?1 AND attributes&?2 ORDER BY memberId ASC;";
static const char* isMemberOfGroupSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM members WHERE groupId=?1 AND memberId=?2) WHEN 1 THEN 1 ELSE 0 END;";
static const char* isGroupMemberSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM members WHERE memberId=?1) WHEN 1 THEN 1 ELSE 0 END;";

static const char* selectAllGroupsWithParticipant = "SELECT g.groupId, g.name, g.ownerId, g.description, "
        "g.maxMembers, g.memberCount, g.attributes, g.lastModified, g.burnTime, g.burnMode, g.avatarInfo "
        "FROM groups g INNER JOIN members m ON g.groupId=m.groupId WHERE m.memberId=?1;";

/* *****************************************************************************
 * SQL statements to process persistent pending change sets
 *
 */
static const char* dropChangeSets = "DROP TABLE changesets;";
static const char* createChangeSets =
        "CREATE TABLE changesets (groupid VARCHAR NOT NULL, changes BLOB, PRIMARY KEY(groupid));";
static const char* insertChangeSetSql = "INSERT INTO changesets (groupid, changes) VALUES (?1, ?2);";
static const char* removeChangeSetSql = "DELETE FROM changesets WHERE groupid=?1;";
static const char* selectChangeSet = "SELECT groupid, changes FROM changesets WHERE groupid=?1;";

using namespace zina;

int32_t SQLiteStoreConv::createGroupTables()
{
    LOGGER(DEBUGGING, __func__ , " -->");
    sqlite3_stmt* stmt;
    int32_t sqlResult;

    // Create Group table
    SQLITE_PREPARE(db, dropGroups, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createGroups, -1, &stmt, NULL));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    // Create member table
    SQLITE_PREPARE(db, dropMembers, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createMembers, -1, &stmt, NULL));
    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    // Create persistent pending change set table
    SQLITE_PREPARE(db, dropChangeSets, -1, &stmt, NULL);
    sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createChangeSets, -1, &stmt, NULL));
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

int32_t SQLiteStoreConv::updateGroupDataDb(int32_t oldVersion)
{
    sqlite3_stmt *stmt;

    LOGGER(DEBUGGING, __func__, " -->");

    if (oldVersion == 4) {
        SQLITE_PREPARE(db, createGroups, -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding groups table: ", sqlCode_);
            return sqlCode_;
        }
        SQLITE_PREPARE(db, createMembers, -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding members table: ", sqlCode_);
            return sqlCode_;
        }
        return SQLITE_OK;
    }

    if (oldVersion == 6) {
        SQLITE_PREPARE(db, "ALTER TABLE groups ADD COLUMN burnTime INTEGER;", -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding burnTime column: ", sqlCode_);
            return sqlCode_;
        }

        SQLITE_PREPARE(db, "ALTER TABLE groups ADD COLUMN burnMode INTEGER;", -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding burnMode column: ", sqlCode_);
            return sqlCode_;
        }

        SQLITE_PREPARE(db, "ALTER TABLE groups ADD COLUMN avatarInfo VARCHAR;", -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding avatarInfo column: ", sqlCode_);
            return sqlCode_;
        }
        return SQLITE_OK;
    }

    if (oldVersion == 7) {
        SQLITE_PREPARE(db, createChangeSets, -1, &stmt, NULL);
        sqlCode_ = sqlite3_step(stmt);
        sqlite3_finalize(stmt);
        if (sqlCode_ != SQLITE_DONE) {
            LOGGER(ERROR, __func__, ", SQL error adding createChangeSets table: ", sqlCode_);
            return sqlCode_;
        }
        return SQLITE_OK;
    }
    return SQLITE_OK;
}

int32_t SQLiteStoreConv::insertGroup(const string &groupUuid, const string &name, const string &ownerUuid, string& description, int32_t maxMembers)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* insertGroupsSql = "INSERT INTO groups (groupId, name, ownerId, description, maxMembers, memberCount, attribute) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertGroupsSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownerUuid.data(), static_cast<int32_t>(ownerUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, description.data(), static_cast<int32_t>(description.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  5, maxMembers));
    SQLITE_CHK(sqlite3_bind_int(stmt,  6, 0));
    SQLITE_CHK(sqlite3_bind_int(stmt,  7, ACTIVE));

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

int32_t SQLiteStoreConv::deleteGroup(const string &groupUuid)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* removeGroup = "DELETE FROM groups WHERE groupId=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeGroup, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

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


bool SQLiteStoreConv::hasGroup(const string &groupUuid, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* hasGroupSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM groups WHERE groupId=?1) WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, hasGroupSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

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


static cJSON* createGroupJson(sqlite3_stmt *stmt)
{
    cJSON* root = cJSON_CreateObject();

    // name is usually the SC UID string
    cJSON_AddStringToObject(root, GROUP_ID, (const char*)sqlite3_column_text(stmt, 0));
    cJSON_AddStringToObject(root, GROUP_NAME, (const char*)sqlite3_column_text(stmt, 1));
    cJSON_AddStringToObject(root, GROUP_OWNER, (const char*)sqlite3_column_text(stmt, 2));
    cJSON_AddStringToObject(root, GROUP_DESC, (const char*)sqlite3_column_text(stmt, 3));
    cJSON_AddNumberToObject(root, GROUP_MAX_MEMBERS, sqlite3_column_int(stmt, 4));
    cJSON_AddNumberToObject(root, GROUP_MEMBER_COUNT, sqlite3_column_int(stmt, 5));
    cJSON_AddNumberToObject(root, GROUP_ATTRIBUTE, sqlite3_column_int(stmt, 6));
    cJSON_AddNumberToObject(root, GROUP_MOD_TIME, sqlite3_column_int64(stmt, 7));
    cJSON_AddNumberToObject(root, GROUP_BURN_SEC, sqlite3_column_int64(stmt, 8));
    cJSON_AddNumberToObject(root, GROUP_BURN_MODE, sqlite3_column_int(stmt, 9));
    Utilities::setJsonString(root, GROUP_AVATAR, (const char*)sqlite3_column_text(stmt, 10), "");

    return root;
}

shared_ptr<list<shared_ptr<cJSON> > > SQLiteStoreConv::listAllGroups(int32_t *sqlCode)
{
    LOGGER(DEBUGGING, __func__, " -->");
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    shared_ptr<list<shared_ptr<cJSON> > > groups = make_shared<list<shared_ptr<cJSON> > >();

    // char* selectAllGroups = "SELECT groupId, name, ownerId, description, maxMembers, memberCount, attributes, lastModified, burnTime, burnMode, avatarInfo FROM groups;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectAllGroups, -1, &stmt, NULL));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get group records and create a JSON object, wrap it in a shared_ptr with a custom delete
        shared_ptr<cJSON> sharedRoot(createGroupJson(stmt), cJSON_deleter);
        groups->push_back(sharedRoot);

        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return groups;
}

int32_t SQLiteStoreConv::listAllGroups(list<JsonUnique> &groups)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    // char* selectAllGroups = "SELECT groupId, name, ownerId, description, maxMembers, memberCount, attributes, lastModified, burnTime, burnMode, avatarInfo FROM groups;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectAllGroups, -1, &stmt, NULL));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get group records and create a JSON object, wrap it in a unique_ptr with a custom delete
        groups.push_back(JsonUnique(createGroupJson(stmt)));
        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sqlResult;
}

int32_t SQLiteStoreConv::listAllGroupsWithMember(const std::string& participantUuid, list<JsonUnique> &groups)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    LOGGER(DEBUGGING, __func__, " -->");

    /*
     * SELECT g.groupId, g.name, g.ownerId, g.description, g.maxMembers,
     *       g.memberCount, g.attributes, g.lastModified, g.burnTime, g.burnMode, g.avatarInfo
     *       FROM groups g INNER JOIN members m ON g.groupId=m.groupId WHERE m.memberId=?1;
     */
    SQLITE_CHK(SQLITE_PREPARE(db, selectAllGroupsWithParticipant, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, participantUuid.data(), static_cast<int32_t>(participantUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get group records and create a JSON object, wrap it in a unique_ptr with a custom delete
        groups.push_back(JsonUnique(createGroupJson(stmt)));
        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sqlResult;
}

shared_ptr<cJSON> SQLiteStoreConv::listGroup(const string &groupUuid, int32_t *sqlCode)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    shared_ptr<cJSON> sharedJson;

    // char* selectGroup = "SELECT groupId, name, ownerId, description, maxMembers, memberCount, attributes, lastModified, burnTime, burnMode, avatarInfo FROM groups WHERE groupId=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectGroup, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    if (sqlResult == SQLITE_ROW) {
        sharedJson = shared_ptr<cJSON>(createGroupJson(stmt), cJSON_deleter);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sharedJson;
}

int32_t SQLiteStoreConv::modifyGroupMaxMembers(const string &groupUuid, int32_t maxMembers)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* updateGroupMaxMember = "UPDATE groups SET maxMembers=?1 WHERE groupId=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, updateGroupMaxMember, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt,  1, maxMembers));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

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

pair<int32_t, time_t> SQLiteStoreConv::getGroupAttribute(const string& groupUuid, int32_t* sqlCode) const
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t attributes = 0;
    time_t lastModified = 0;
    pair<int32_t, time_t> result;

    // char* selectGroupAttributeSql = "SELECT attributes, lastModified FROM groups WHERE groupId=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectGroupAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    if (sqlResult == SQLITE_ROW) {
        attributes = sqlite3_column_int(stmt, 0);
        lastModified = sqlite3_column_int64(stmt, 1);
        result = pair<int32_t, time_t>(attributes, lastModified);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return result;
}

int32_t SQLiteStoreConv::setGroupAttribute(const string& groupUuid, int32_t attributeMask)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setGroupAttributeSql = "UPDATE groups SET attributes=attributes|?1, lastModified=?2 WHERE groupId=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, setGroupAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt,  1, attributeMask));
    SQLITE_CHK(sqlite3_bind_int64(stmt,2, time(nullptr)));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::clearGroupAttribute(const string& groupUuid, int32_t attributeMask) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* clearGroupAttributeSql = "UPDATE groups SET attributes=attributes&~?1, lastModified=?2 WHERE groupId=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, clearGroupAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, attributeMask));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 2, time(nullptr)));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::setGroupName(const string& groupUuid, const string& name)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setGroupNewName = "UPDATE groups SET name=?1, lastModified=?2 WHERE groupId=?3;";
    sqlResult = SQLITE_PREPARE(db, setGroupNewName, -1, &stmt, NULL);
    sqlite3_bind_text(stmt,  1, name.data(), static_cast<int32_t>(name.size()), SQLITE_STATIC);
    sqlite3_bind_int64(stmt, 2, time(nullptr));
    sqlite3_bind_text(stmt,  3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


int32_t SQLiteStoreConv::setGroupBurnTime(const string& groupUuid, int64_t timeInSeconds, int32_t mode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setGroupBurn = "UPDATE groups SET burnTime=?1, burnMode=?2, lastModified=?3 WHERE groupId=?4;";
    sqlResult = SQLITE_PREPARE(db, setGroupBurn, -1, &stmt, NULL);
    sqlite3_bind_int64(stmt, 1, timeInSeconds);
    sqlite3_bind_int(stmt,   2, mode);
    sqlite3_bind_int64(stmt, 3, time(nullptr));
    sqlite3_bind_text(stmt,  4, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::setGroupAvatarInfo(const string& groupUuid, const string& avatarInfo) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setGroupAvatar = "UPDATE groups SET avatarInfo=?1, lastModified=?2 WHERE groupId=?3;";
    sqlResult = SQLITE_PREPARE(db, setGroupAvatar, -1, &stmt, NULL);
    sqlite3_bind_text(stmt,  1, avatarInfo.data(), static_cast<int32_t>(avatarInfo.size()), SQLITE_STATIC);
    sqlite3_bind_int64(stmt, 2, time(nullptr));
    sqlite3_bind_text(stmt,  3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

static int32_t incrementMemberCount(sqlite3* db, const string& groupUuid) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* incrementGroupMemberCount = "UPDATE groups SET memberCount=memberCount+1 WHERE groupId=?1;";
    sqlResult = SQLITE_PREPARE(db, incrementGroupMemberCount, -1, &stmt, NULL);
    sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

static int32_t decrementMemberCount(sqlite3* db, const string& groupUuid) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* decrementGroupMemberCount = "UPDATE groups SET memberCount=memberCount-1 WHERE groupId=?1;";
    sqlResult = SQLITE_PREPARE(db, decrementGroupMemberCount, -1, &stmt, NULL);
    sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

static int32_t setMemberCount(sqlite3* db, const string& groupUuid, int32_t count) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setGroupMemberCount = "UPDATE groups SET memberCount=?1 WHERE groupId=?2;";
    sqlResult = SQLITE_PREPARE(db, setGroupMemberCount, -1, &stmt, NULL);
    sqlite3_bind_int(stmt,  1, count);
    sqlite3_bind_text(stmt, 2, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC);
    if (sqlResult != SQLITE_OK) {
        goto cleanup;
    }
    sqlResult = sqlite3_step(stmt);

cleanup:
    sqlite3_finalize(stmt);
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::insertMember(const string &groupUuid, const string &memberUuid)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultIncrement;

    // char* insertMemberSql = "INSERT INTO members (groupId, memberId, attributes) VALUES (?1, ?2, ?3);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertMemberSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  3, ACTIVE));

    beginTransaction();
    sqlResultIncrement = incrementMemberCount(db, groupUuid);
    sqlResult = sqlite3_step(stmt);

    if (sqlResult != SQLITE_DONE || sqlResultIncrement != SQLITE_DONE) {
        ERRMSG;
        rollbackTransaction();
    }
    else {
        commitTransaction();
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}

int32_t SQLiteStoreConv::deleteMember(const string &groupUuid, const string &memberUuid)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultDecrement;

    // char* removeMember = "DELETE FROM members WHERE groupId=?1 AND memberId=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeMember, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

    beginTransaction();
    sqlResult= sqlite3_step(stmt);
    sqlResultDecrement = decrementMemberCount(db, groupUuid);

    if (sqlResult != SQLITE_DONE || sqlResultDecrement != SQLITE_DONE) {
        ERRMSG;
        rollbackTransaction();
    }
    else {
        commitTransaction();
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


int32_t SQLiteStoreConv::deleteAllMembers(const string &groupUuid) {
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultDecrement;

    // char* removeAllMembers = "DELETE FROM members WHERE groupId=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeAllMembers, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

    beginTransaction();
    sqlResult= sqlite3_step(stmt);
    sqlResultDecrement = setMemberCount(db, groupUuid, 0);

    if (sqlResult != SQLITE_DONE || sqlResultDecrement != SQLITE_DONE) {
        ERRMSG;
        rollbackTransaction();
    }
    else {
        commitTransaction();
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


static cJSON* createMemberJson(sqlite3_stmt *stmt)
{
    cJSON* root = cJSON_CreateObject();

    cJSON_AddStringToObject(root, GROUP_ID,  (const char*)sqlite3_column_text(stmt, 0));
    cJSON_AddStringToObject(root, MEMBER_ID, (const char*)sqlite3_column_text(stmt, 1));
    cJSON_AddNumberToObject(root, MEMBER_ATTRIBUTE, sqlite3_column_int(stmt, 2));
    cJSON_AddNumberToObject(root, MEMBER_MOD_TIME, sqlite3_column_int64(stmt, 3));

    return root;
}

shared_ptr<list<shared_ptr<cJSON> > > SQLiteStoreConv::getAllGroupMembers(const string &groupUuid, int32_t *sqlCode)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    shared_ptr<list<shared_ptr<cJSON> > > members = make_shared<list<shared_ptr<cJSON> > >();

    // char* selectGroupMembers = "SELECT groupId, memberId, attributes, lastModified FROM members WHERE groupId=?1 ORDER BY memberId ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectGroupMembers, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get member records and create a JSON object, wrap it in a shared_ptr with a custom delete
        shared_ptr<cJSON> sharedRoot(createMemberJson(stmt), cJSON_deleter);
        members->push_back(sharedRoot);
        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return members;
}

int32_t SQLiteStoreConv::getAllGroupMembers(const string &groupUuid, list<JsonUnique> &members)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* selectGroupMembers = "SELECT groupId, memberId, attributes, lastModified FROM members WHERE groupId=?1 ORDER BY memberId ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectGroupMembers, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    while (sqlResult == SQLITE_ROW) {
        // Get member records and create a JSON object, wrap it in a unique_ptr with a custom delete
        members.push_back(JsonUnique(createMemberJson(stmt)));
        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sqlResult;
}

shared_ptr<cJSON> SQLiteStoreConv::getGroupMember(const string &groupUuid, const string &memberUuid, int32_t *sqlCode)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    shared_ptr<cJSON> sharedJson;

    // char* selectMember = "SELECT groupId, memberId, attributes, lastModified FROM members WHERE groupId=?1 AND memberId=?2 ORDER BY memberId ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectMember, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    ERRMSG;

    if (sqlResult == SQLITE_ROW) {
        sharedJson = shared_ptr<cJSON>(createMemberJson(stmt), cJSON_deleter);
    }

    cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return sharedJson;
}

pair<int32_t, time_t> SQLiteStoreConv::getMemberAttribute(const string &groupUuid, const string &memberUuid, int32_t *sqlCode)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t attributes = 0;
    time_t lastModified = 0;
    pair<int32_t, time_t> result;

    // char* selectMemberAttributeSql = "SELECT attributes, lastModified FROM members WHERE groupId=?1 AND memberId=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectMemberAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

    sqlResult= sqlite3_step(stmt);
    if (sqlResult != SQLITE_DONE) {
        ERRMSG;
    }

    if (sqlResult == SQLITE_ROW) {
        attributes = sqlite3_column_int(stmt, 0);
        lastModified = sqlite3_column_int64(stmt, 1);
        result = pair<int32_t, time_t>(attributes, lastModified);
    }

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return result;
}

int32_t SQLiteStoreConv::setMemberAttribute(const string &groupUuid, const string &memberUuid, int32_t attributeMask)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* setMemberAttributeSql = "UPDATE members SET attributes=attributes|?1, lastModified=?2 WHERE groupId=?2 AND memberId=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, setMemberAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt,  1, attributeMask));
    SQLITE_CHK(sqlite3_bind_int64(stmt,2, time(nullptr)));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::clearMemberAttribute(const string &groupUuid, const string &memberUuid, int32_t attributeMask)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;

    // char* clearMemberAttributeSql = "UPDATE members SET attributes=attributes&~?1lastModified=?2 WHERE groupId=?2 AND memberId=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, clearMemberAttributeSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, attributeMask));
    SQLITE_CHK(sqlite3_bind_int64(stmt,2, time(nullptr)));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

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


int32_t SQLiteStoreConv::memberListHash(const string &groupUuid, uint8_t *hash)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    sha256_ctx* ctx;

    // char* selectForHash = "SELECT DISTINCT memberId FROM members WHERE groupId=?1 AND attributes&?2 ORDER BY memberId ASC;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectForHash, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int(stmt,  2, ACTIVE));
    sqlResult = sqlite3_step(stmt);

    ctx = reinterpret_cast<sha256_ctx*>(createSha256Context());

    while (sqlResult == SQLITE_ROW) {
        const uint8_t* data = sqlite3_column_text(stmt, 0);
        int32_t length = sqlite3_column_bytes(stmt, 0);
        sha256Ctx(ctx, const_cast<uint8_t *>(data), static_cast<uint32_t >(length));
        sqlResult = sqlite3_step(stmt);
    }
    closeSha256Context(ctx, hash);


cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}


bool SQLiteStoreConv::isMemberOfGroup(const string &groupUuid, const string &memberUuid, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    // char* isMemberOfGroupSql = "SELECT NULL, CASE EXISTS (SELECT 0 FROM members WHERE groupId=?1 AND memberId=?2) WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, isMemberOfGroupSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupUuid.data(), static_cast<int32_t>(groupUuid.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

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


bool SQLiteStoreConv::isGroupMember(const string &memberUuid, int32_t *sqlCode) {
    sqlite3_stmt *stmt;
    int32_t sqlResult;
    int32_t exists = 0;

    LOGGER(DEBUGGING, __func__, " --> ");

    // char* isGroupMember = "SELECT NULL, CASE EXISTS (SELECT 0 FROM members WHERE memberId=?1) WHEN 1 THEN 1 ELSE 0 END;";
    SQLITE_CHK(SQLITE_PREPARE(db, isGroupMemberSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, memberUuid.data(), static_cast<int32_t>(memberUuid.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);
    if (sqlResult != SQLITE_ROW)
    ERRMSG;

    exists = sqlite3_column_int(stmt, 1);

cleanup:
    sqlite3_finalize(stmt);
    if (sqlCode != NULL)
        *sqlCode = sqlResult;
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);

    return exists == 1;
}

int32_t SQLiteStoreConv::insertChangeSet(const string &groupId, const string& changeSet)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultIncrement;

    // char* insertChangeSetSql = "INSERT INTO changesets (groupid, changes) VALUES (?1, ?2);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertChangeSetSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, changeSet.data(), static_cast<int32_t>(changeSet.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::removeChangeSet(const string &groupId)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultIncrement;

    // char* removeChangeSetSql = "DELETE FROM changesets WHERE groupid=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeChangeSetSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));

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

int32_t SQLiteStoreConv::getGroupChangeSet(const string &groupId, string* changeSet)
{
    sqlite3_stmt *stmt;
    int32_t sqlResult, sqlResultIncrement;

    // char* selectChangeSet = "SELECT groupid, changes FROM changesets WHERE groupid=?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectChangeSet, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, groupId.data(), static_cast<int32_t>(groupId.size()), SQLITE_STATIC));

    sqlResult = sqlite3_step(stmt);

    while (sqlResult == SQLITE_ROW) {
        string grpId(reinterpret_cast<const char*>(sqlite3_column_text(stmt, 0)));

        size_t len = static_cast<size_t>(sqlite3_column_bytes(stmt, 1));
        changeSet->assign(static_cast<const char*>(sqlite3_column_blob(stmt, 1)), len);

        sqlResult = sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    sqlCode_ = sqlResult;
    LOGGER(DEBUGGING, __func__, " <-- ", sqlResult);
    return sqlResult;
}
