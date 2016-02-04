#include "SQLiteStoreConv.h"

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

#define DB_VERSION 1

static void *(*volatile memset_volatile)(void *, int, size_t) = memset;

static const char *beginTransactionSql  = "BEGIN TRANSACTION;";
static const char *commitTransactionSql = "COMMIT;";

/* *****************************************************************************
 * The SQLite master table.
 *
 * Used to check if we have valid ZRTP cache tables.
 */
static const char *lookupTables = "SELECT name FROM sqlite_master WHERE type='table' AND name='OwnIdentity';";


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


/* *****************************************************************************
 * SQL statements to process account management table.
 */
static const char* dropAccounts = "DROP TABLE AccountMngmt;";
static const char* createAccounts = 
    "CREATE TABLE AccountMngmt (name VARCHAR NOT NULL PRIMARY KEY, lastUpdated TIMESTAMP, domain VARCHAR);";
static const char* insertAccount = "INSERT INTO AccountMngmt (name, lastUpdated) VALUES (?1, strftime('%s', ?2, 'unixepoch'));";
static const char* updateAccount = "UPDATE zrtpIdRemote SET name=?1,lastUpdated=strftime('%s', ?2, 'unixepoch') WHERE name=?3";
static const char* selectAccountLastUpdated = "SELECT strftime('%s', lastUpdated, 'unixepoch') FROM AccountMngmt WHERE name=?1;";

/* *****************************************************************************
 * SQL statements to process the Pre-key table.
 */
static const char* dropPreKeys = "DROP TABLE PreKeys;";
static const char* createPreKeys = "CREATE TABLE PreKeys (keyid INTEGER NOT NULL PRIMARY KEY, preKeyData BLOB, checkData BLOB);";
static const char* insertPreKey = "INSERT INTO PreKeys (keyId, preKeyData) VALUES (?1, ?2);";
static const char* selectPreKey = "SELECT preKeyData FROM PreKeys WHERE keyid=?1;";
static const char* deletePreKey = "DELETE FROM PreKeys WHERE keyId=?1;";
static const char* selectPreKeyAll = "SELECT keyId, preKeyData FROM PreKeys;";


#ifdef UNITTESTS
// Used in testing and debugging to do in-depth checks
static void hexdump(const char* title, const unsigned char *s, int l) {
    int n=0;

    if (s == NULL) return;

    fprintf(stderr, "%s",title);
    for( ; n < l ; ++n)
    {
        if((n%16) == 0)
            fprintf(stderr, "\n%04x",n);
        fprintf(stderr, " %02x",s[n]);
    }
    fprintf(stderr, "\n");
}
static void hexdump(const char* title, const std::string& in)
{
    hexdump(title, (uint8_t*)in.data(), in.size());
}
#endif

using namespace axolotl;

void Log(const char* format, ...);

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

SQLiteStoreConv* SQLiteStoreConv::instance_ = NULL;

SQLiteStoreConv* SQLiteStoreConv::getStore()
{
    if (instance_ == NULL)
        instance_ = new SQLiteStoreConv();
    return instance_;
}

SQLiteStoreConv::SQLiteStoreConv() : db(NULL), keyData_(NULL), isReady_(false) {}

SQLiteStoreConv::~SQLiteStoreConv()
{
    sqlite3_close(db);
    db = NULL;
    delete keyData_; keyData_ = NULL;
}

int SQLiteStoreConv::beginTransaction()
{
    sqlite3_stmt *stmt;

    SQLITE_CHK(SQLITE_PREPARE(db, beginTransactionSql, -1, &stmt, NULL));

    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        return sqlCode_;
    }
    return SQLITE_OK;

 cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}

int SQLiteStoreConv::commitTransaction()
{
    sqlite3_stmt *stmt;

    SQLITE_CHK(SQLITE_PREPARE(db, commitTransactionSql, -1, &stmt, NULL));

    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        return sqlCode_;
    }
    return SQLITE_OK;

 cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
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
    if (keyData_ == NULL) {
        return -1;
    }
    sqlite3_stmt *stmt;
    int found = 0;

    // If name has size 0 then open im-memory DB, handy for testing
    const char *dbName = name.size() == 0 ? ":memory:" : name.c_str();
    sqlCode_ = sqlite3_open_v2(dbName, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL);

    if (sqlCode_) {
        ERRMSG;
        return(sqlCode_);
    }
    sqlite3_key(db, keyData_->data(), keyData_->size());

    memset_volatile((void*)keyData_->data(), 0, keyData_->size());
    delete keyData_; keyData_ = NULL;

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

    isReady_ = true;
    return SQLITE_OK;

 cleanup:
    sqlite3_finalize(stmt);
    return sqlCode_;
}


int SQLiteStoreConv::createTables()
{
    sqlite3_stmt* stmt;

    /* First drop them, just to be on the save side
     * Ignore errors, there is nothing to drop on empty DB. If ZrtpIdOwn was
     * deleted using DB admin command then we need to drop the remote id table
     * and names also to have a clean state.
     */

    sqlCode_ = SQLITE_PREPARE(db, dropConversations, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createConversations, -1, &stmt, NULL));
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, dropStagedMk, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createStagedMk, -1, &stmt, NULL));
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, dropAccounts, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createAccounts, -1, &stmt, NULL));
    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE) {
        ERRMSG;
        goto cleanup;
    }
    sqlite3_finalize(stmt);

    sqlCode_ = SQLITE_PREPARE(db, dropPreKeys, -1, &stmt, NULL);
    sqlCode_ = sqlite3_step(stmt);
    sqlite3_finalize(stmt);

    SQLITE_CHK(SQLITE_PREPARE(db, createPreKeys, -1, &stmt, NULL));
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


// If the result is a BLOB or UTF-8 string then the sqlite3_column_bytes() routine returns the number of bytes in that BLOB or string.
const static char* dummyId = "__DUMMY__";


std::list<std::string>* SQLiteStoreConv::getKnownConversations(const std::string& ownName)
{
    sqlite3_stmt *stmt;
    int32_t nameLen;

    std::list<std::string>* names = new std::list<std::string>;

    // selectConvNames = "SELECT name FROM Conversations WHERE ownName=?1 ORDER BY name;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConvNames, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, ownName.data(), ownName.size(), SQLITE_STATIC));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        nameLen = sqlite3_column_bytes(stmt, 0);
        std::string name((const char*)sqlite3_column_text(stmt, 0), nameLen);
        names->push_back(name);
    }
    sqlite3_finalize(stmt);
    return names;

cleanup:
    sqlite3_finalize(stmt);
    return NULL;
}

std::list<std::string>* SQLiteStoreConv::getLongDeviceIds(const std::string& name, const std::string& ownName)
{
    sqlite3_stmt *stmt;
    int32_t idLen;
    std::string* id;

    std::list<std::string>* devIds = new std::list<std::string>;

    // selectConvDevices = "SELECT longDevId FROM Conversations WHERE name=?1 AND ownName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConvDevices, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, ownName.data(), ownName.size(), SQLITE_STATIC));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        idLen = sqlite3_column_bytes(stmt, 0);
        string id((const char*)sqlite3_column_text(stmt, 0), idLen);
        if (id.compare(0, id.size(), dummyId, id.size()) == 0)
            continue;
        devIds->push_back(id);
    }
    sqlite3_finalize(stmt);
    return devIds;

cleanup:
    sqlite3_finalize(stmt);
    return NULL;
}


// ***** Session store
std::string* SQLiteStoreConv::loadConversation(const std::string& name, const std::string& longDevId, const std::string& ownName) const 
{ 
    sqlite3_stmt *stmt;
    int32_t len;
    string* data;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }

    // selectConversation = "SELECT sessionData FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), ownName.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {        // No such session, return an empty session record
        sqlite3_finalize(stmt);
        return NULL;
    }
    // Get the session data
    len = sqlite3_column_bytes(stmt, 0);
    data = new string((const char*)sqlite3_column_blob(stmt, 0), len);

    sqlite3_finalize(stmt);

    return data;

cleanup:
    sqlite3_finalize(stmt);
    return NULL;
}

void SQLiteStoreConv::storeConversation(const std::string& name, const std::string& longDevId, const std::string& ownName, const std::string& data)
{
    sqlite3_stmt *stmt;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    // updateConversation = "UPDATE Conversations SET data=?1, WHERE name=?2 AND longDevId=?3 AND ownName=?4;";
    SQLITE_CHK(SQLITE_PREPARE(db, updateConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 1, data.data(), data.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 4, ownName.data(), ownName.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);

    // insertConversation = "INSERT OR IGNORE INTO Conversations (name, secondName, longDevId, data, ownName) VALUES (?1, ?2, ?3, ?4, ?5);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_null(stmt, 2));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 4, data.data(), data.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 5, ownName.data(), ownName.size(), SQLITE_STATIC));
    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

bool SQLiteStoreConv::hasConversation(const std::string& name, const std::string& longDevId, const std::string& ownName) const 
{
    sqlite3_stmt *stmt;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    // selectConversation = "SELECT iv, data FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), ownName.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    return sqlCode_ == SQLITE_ROW; 

cleanup:
    sqlite3_finalize(stmt);
    return false;
}

void SQLiteStoreConv::deleteConversation(const std::string& name, const std::string& longDevId, const std::string& ownName)
{
    sqlite3_stmt *stmt;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    //removeConversation = "DELETE FROM Conversations WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeConversation, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), ownName.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

void SQLiteStoreConv::deleteConversationsName(const std::string& name, const std::string& ownName)
{
    sqlite3_stmt *stmt;

    // removeConversations = "DELETE FROM Conversations WHERE name=?1 AND ownName=?2;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeConversations, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, ownName.data(), ownName.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

list<string>* SQLiteStoreConv::loadStagedMks(const string& name, const string& longDevId, const string& ownName) const
{
    sqlite3_stmt *stmt;
    int32_t len;
    list<string>* keys = new list<string>;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    // selectStagedMks = "SELECT ivkeymk FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectStagedMks, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), ownName.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {        // No stored MKs, return an empty session record
        sqlite3_finalize(stmt);
        delete keys;
        return NULL;
    }
    while (sqlCode_ == SQLITE_ROW) {
        // Get the MK and its iv
        len = sqlite3_column_bytes(stmt, 0);
        string mkivenc((const char*)sqlite3_column_blob(stmt, 0), len);

        keys->push_back(mkivenc);

        sqlCode_= sqlite3_step(stmt);
    }

cleanup:
    sqlite3_finalize(stmt);
    return keys;
}

void SQLiteStoreConv::insertStagedMk(const string& name, const string& longDevId, const string& ownName, const string& MKiv)
{
    sqlite3_stmt *stmt;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    
//     insertStagedMkSql = 
//     "INSERT OR REPLACE INTO stagedMk (name, longDevId, ownName, since, otherkey, ivkeymk, ivkeyhdr) "
//     "VALUES(?1, ?2, ?3, strftime('%s', ?4, 'unixepoch'), ?5, ?6, ?7);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertStagedMkSql, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt,  1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt,  3, ownName.data(), ownName.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 4, time(0)));
    SQLITE_CHK(sqlite3_bind_null(stmt,  5));
    SQLITE_CHK(sqlite3_bind_blob(stmt,  6, MKiv.data(), MKiv.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_null(stmt,  7));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

void SQLiteStoreConv::deleteStagedMk(const string& name, const string& longDevId, const string& ownName, string& MKiv)
{
    sqlite3_stmt *stmt;

    const char* devId;
    int32_t devIdLen;
    if (longDevId.size() > 0) {
        devId = longDevId.c_str();
        devIdLen = longDevId.size();
    }
    else {
        devId = dummyId;
        devIdLen = strlen(dummyId);
    }
    // removeStagedMk = "DELETE FROM stagedMk WHERE name=?1 AND longDevId=?2 AND ownName=?3 AND ivkeymk=?4;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeStagedMk, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_text(stmt, 1, name.data(), name.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 2, devId, devIdLen, SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_text(stmt, 3, ownName.data(), ownName.size(), SQLITE_STATIC));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 4, MKiv.data(), MKiv.size(), SQLITE_STATIC));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

void SQLiteStoreConv::deleteStagedMk(time_t timestamp)
{
    sqlite3_stmt *stmt;
    int32_t cleaned;
    // removeStagedMkTime = "DELETE FROM stagedMk WHERE since < ?1;";
    SQLITE_CHK(SQLITE_PREPARE(db, removeStagedMkTime, -1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int64(stmt, 1, timestamp));

    sqlCode_= sqlite3_step(stmt);
//    cleaned = sqlite3_changes(db);
//    Log("Number of removed old MK: %d", cleaned);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

// ******** PreKey store
string* SQLiteStoreConv::loadPreKey(int32_t preKeyId) const 
{
    sqlite3_stmt *stmt;
    int32_t len;
    string* preKeyData;

    // selectPreKey = "SELECT preKeyData FROM PreKeys WHERE keyid=?1;";

    // SELECT iv, preKeyData FROM PreKeys WHERE keyid=?1 ;
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKey, strlen(selectPreKey)+1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    if (sqlCode_ != SQLITE_ROW) {        // No such pre key
        sqlite3_finalize(stmt);
        return NULL;
    }
    // Get the pre key data
    len = sqlite3_column_bytes(stmt, 0);
    preKeyData = new string((const char*)sqlite3_column_blob(stmt, 0), len);
    sqlite3_finalize(stmt);

    return preKeyData;

cleanup:
    sqlite3_finalize(stmt);
    return NULL;
}

void SQLiteStoreConv::storePreKey(int32_t preKeyId, const string& data)
{
    sqlite3_stmt *stmt;

    // insertPreKey = "INSERT INTO PreKeys (keyId, preKeyData) VALUES (?1, ?2);";
    SQLITE_CHK(SQLITE_PREPARE(db, insertPreKey, strlen(insertPreKey)+1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));
    SQLITE_CHK(sqlite3_bind_blob(stmt, 2, data.data(), data.size(), SQLITE_STATIC));

    sqlCode_ = sqlite3_step(stmt);
    if (sqlCode_ != SQLITE_DONE)
        ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

bool SQLiteStoreConv::containsPreKey(int32_t preKeyId) const
{
    sqlite3_stmt *stmt;

    // SELECT preKeyData FROM PreKeys WHERE keyid=?1 ;
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKey, strlen(selectPreKey)+1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;
    sqlite3_finalize(stmt);
    return (sqlCode_ == SQLITE_ROW);

cleanup:
    sqlite3_finalize(stmt);
    return false; 
}

void SQLiteStoreConv::removePreKey(int32_t preKeyId) 
{
    sqlite3_stmt *stmt;

    // DELETE FROM PreKeys WHERE keyId=?1
    SQLITE_CHK(SQLITE_PREPARE(db, deletePreKey, strlen(deletePreKey)+1, &stmt, NULL));
    SQLITE_CHK(sqlite3_bind_int(stmt, 1, preKeyId));

    sqlCode_= sqlite3_step(stmt);
    ERRMSG;

cleanup:
    sqlite3_finalize(stmt);
}

void SQLiteStoreConv::dumpPreKeys() const
{
    sqlite3_stmt *stmt;

    //  selectPreKeyAll = "SELECT keyId, preKeyData FROM PreKeys;";
    SQLITE_CHK(SQLITE_PREPARE(db, selectPreKeyAll, -1, &stmt, NULL));

    while ((sqlCode_ = sqlite3_step(stmt)) == SQLITE_ROW) {
        int32_t keyId = sqlite3_column_int(stmt, 0);
    }

cleanup:
    sqlite3_finalize(stmt);

}

