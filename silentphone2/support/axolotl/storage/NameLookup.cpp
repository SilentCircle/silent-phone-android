//
// Created by werner on 30.10.15.
//

#include <iostream>
#include "NameLookup.h"
#include <mutex>          // std::mutex, std::unique_lock
#include "../util/cJSON.h"
#include "../axolotl/Constants.h"
#include "../provisioning/Provisioning.h"
#include "../logging/AxoLogging.h"


using namespace axolotl;

static mutex nameLock;           // mutex for critical section

NameLookup* NameLookup::instance_ = NULL;

NameLookup* NameLookup::getInstance()
{
    unique_lock<mutex> lck(nameLock);
    if (instance_ == NULL)
        instance_ = new NameLookup();
    lck.unlock();
    return instance_;
}

static string USER_NULL_NAME("_!NULL!_");
static const char* nullData =
                "{\"display_name\": \"%s\",\"uuid\": \"%s\",\"default_alias\": \"%s\"}";



const string NameLookup::getUid(const string &alias, const string& authorization) {

    LOGGER(INFO, __func__ , " -->");
    shared_ptr<UserInfo> userInfo = getUserInfo(alias, authorization);
    LOGGER(INFO, __func__ , " <--");
    return (userInfo) ? userInfo->uniqueId : Empty;
}

/*
 * Structure of the user info JSON that the provisioning server returns:

{"display_alias": <string>, "avatar_url": <string>, "display_name": <string>, "uuid": <string>}
 *
 */
int32_t NameLookup::parseUserInfo(const string& json, shared_ptr<UserInfo> userInfo)
{
    LOGGER(INFO, __func__ , " -->");
    cJSON* root = cJSON_Parse(json.c_str());
    if (root == NULL) {
        LOGGER(ERROR, __func__ , " JSON data not parseable: ", json);
        return CORRUPT_DATA;
    }

    cJSON* tmpData = cJSON_GetObjectItem(root, "uuid");
    if (tmpData == NULL || tmpData->valuestring == NULL) {
        cJSON_Delete(root);
        LOGGER(ERROR, __func__ , " Missing 'uuid' field.");
        return JS_FIELD_MISSING;
    }
    userInfo->uniqueId.assign(tmpData->valuestring);

    tmpData = cJSON_GetObjectItem(root, "default_alias");
    if (tmpData == NULL || tmpData->valuestring == NULL) {
        tmpData = cJSON_GetObjectItem(root, "display_alias");
        if (tmpData == NULL || tmpData->valuestring == NULL) {
            cJSON_Delete(root);
            LOGGER(ERROR, __func__, " Missing 'default_alias' or 'display_alias' field.");
            return JS_FIELD_MISSING;
        }
    }
    userInfo->alias0.assign(tmpData->valuestring);

    tmpData = cJSON_GetObjectItem(root, "display_name");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo->displayName.assign(tmpData->valuestring);
    }
    tmpData = cJSON_GetObjectItem(root, "lookup_uri");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo->contactLookupUri.assign(tmpData->valuestring);
    }
    tmpData = cJSON_GetObjectItem(root, "avatar_url");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo->avatarUrl.assign(tmpData->valuestring);
    }
    cJSON_Delete(root);
    LOGGER(INFO, __func__ , " <--");
    return OK;
}

const shared_ptr<UserInfo> NameLookup::getUserInfo(const string &alias, const string &authorization, bool cacheOnly, int32_t* errorCode) {

    LOGGER(INFO, __func__ , " -->");
    if (alias.empty()) {
        LOGGER(ERROR, __func__ , " <-- empty alias name");
        if (errorCode != NULL)
            *errorCode = GENERIC_ERROR;
        return shared_ptr<UserInfo>();
    }

    // Check if this alias name already exists in the name map
    unique_lock<mutex> lck(nameLock);
    map<string, shared_ptr<UserInfo> >::iterator it;
    it = nameMap_.find(alias);
    if (it != nameMap_.end()) {
        LOGGER(INFO, __func__ , " <-- cached data");
        if (it->second->displayName == USER_NULL_NAME) {
            return shared_ptr<UserInfo>();
        }
        return it->second;
    }
    if (cacheOnly) {
        return shared_ptr<UserInfo>();
    }
    if (authorization.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing authorization");
        if (errorCode != NULL)
            *errorCode = GENERIC_ERROR;
        return shared_ptr<UserInfo>();
    }

    string result;
    int32_t code = Provisioning::getUserInfo(alias, authorization, &result);

    // Return empty pointer in case of HTTP error
    char temp[1000];
    if (code >= 400) {
        // If server returns "not found" then add a invalid user data structure. Thus
        // another lookup with the same name will have a cache hit, avoiding a network
        // round trip but still returning an empty pointer signaling a non-existing name.
        if (code == 404) {
            snprintf(temp, 990, nullData, USER_NULL_NAME.c_str(), alias.c_str(), alias.c_str());
            result = temp;
        }
        else {
            LOGGER(ERROR, __func__ , " <-- error return from server: ", code);
            if (errorCode != NULL)
                *errorCode = code;
            return shared_ptr<UserInfo>();
        }
    }

    shared_ptr<UserInfo> userInfo = make_shared<UserInfo>();
    code = parseUserInfo(result, userInfo);
    if (code != OK) {
        LOGGER(ERROR, __func__ , " Error return from parsing.");
        return shared_ptr<UserInfo>();
    }

    pair<map<string, shared_ptr<UserInfo> >::iterator, bool> ret;

    // Check if we already have the user's UID in the map. If not then cache the
    // userInfo with the UID
    it = nameMap_.find(userInfo->uniqueId);
    if (it == nameMap_.end()) {
        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(userInfo->uniqueId, userInfo));
        if (!ret.second) {
            LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 0);
            return shared_ptr<UserInfo>();
        }
        // For existing account (old accounts) the UUID and the primary alias could be identical
        // Don't add an alias entry in this case
        if (alias.compare(userInfo->uniqueId) != 0) {
            ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, userInfo));
            if (!ret.second) {
                LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 1);
                return shared_ptr<UserInfo>();
            }
        }
    }
    else {
        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, it->second));
        if (!ret.second) {
            LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 2);
            return shared_ptr<UserInfo>();
        }
        userInfo = it->second;
    }
    lck.unlock();
    if (userInfo->displayName == USER_NULL_NAME) {
        LOGGER(INFO, __func__ , " <-- return null name");
        return shared_ptr<UserInfo>();
    }
    LOGGER(INFO, __func__ , " <-- ", userInfo->displayName);
    return userInfo;
}

shared_ptr<UserInfo> NameLookup::refreshUserData(const string& aliasUuid, const string& authorization)
{
    LOGGER(INFO, __func__ , " -->");
    if (aliasUuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- empty alias name");
        return shared_ptr<UserInfo>();
    }

    // Check if this alias name already exists in the name map
    unique_lock<mutex> lck(nameLock);
    map<string, shared_ptr<UserInfo> >::iterator it;
    it = nameMap_.find(aliasUuid);
    if (it == nameMap_.end()) {
        lck.unlock();
        return getUserInfo(aliasUuid, authorization, false);
        LOGGER(INFO, __func__ , " <-- No cached data, just load");
    }
    string result;
    int32_t code = Provisioning::getUserInfo(aliasUuid, authorization, &result);
    if (code >= 400) {
        LOGGER(ERROR, __func__ , " <-- no refresh for unknown user");
        return shared_ptr<UserInfo>();
    }
    shared_ptr<UserInfo> userInfo = make_shared<UserInfo>();
    code = parseUserInfo(result, userInfo);
    if (code != OK) {
        LOGGER(ERROR, __func__ , " Error return from parsing.");
        return shared_ptr<UserInfo>();
    }
    // Replace existing data, don't touch the lookup_uri because the server _never_ sends
    // it. Only the application may delete it.
    it->second->displayName.assign(userInfo->displayName);
    it->second->alias0.assign(userInfo->alias0);
    it->second->avatarUrl.assign(userInfo->avatarUrl);

    return it->second;
}

const shared_ptr<list<string> > NameLookup::getAliases(const string& uuid)
{
    LOGGER(INFO, __func__ , " -->");
    shared_ptr<list<string> > aliasList = make_shared<list<string> >();
    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- empty uuid");
        return shared_ptr<list<string> >();
    }
    unique_lock<mutex> lck(nameLock);

    if (nameMap_.size() == 0) {
        LOGGER(INFO, __func__ , " <-- empty name map");
        return shared_ptr<list<string> >();
    }
    for (map<string, shared_ptr<UserInfo> >::iterator it=nameMap_.begin(); it != nameMap_.end(); ++it) {
        shared_ptr<UserInfo> userInfo = (*it).second;
        // Add aliases to the result. If the map entry if the UUID entry then add the default alias
        if (uuid == userInfo->uniqueId) {
            if (uuid != (*it).first) {
                aliasList->push_back((*it).first);
            }
            else {
                if (!(*it).second->alias0.empty())
                    aliasList->push_back((*it).second->alias0);
            }
        }
    }
    lck.unlock();
    LOGGER(INFO, __func__ , " <--");
    return aliasList;
}

NameLookup::AliasAdd NameLookup::addAliasToUuid(const string& alias, const string& uuid, const string& userData)
{
    LOGGER(INFO, __func__ , " -->");

    unique_lock<mutex> lck(nameLock);

    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing UUID data");
        return UserDataError;
    }

    shared_ptr<UserInfo> userInfo = make_shared<UserInfo>();
    int32_t code = parseUserInfo(userData, userInfo);
    if (code != OK) {
        LOGGER(ERROR, __func__ , " Error return from parsing.");
        return UserDataError;
    }

    // Check if this alias name already exists in the name map, if yes amend
    // lookup URI string if necessary, then return
    map<string, shared_ptr<UserInfo> >::iterator it;
    it = nameMap_.find(alias);
    if (it != nameMap_.end()) {
        it->second->contactLookupUri.assign(userInfo->contactLookupUri);
        it->second->avatarUrl.assign(userInfo->avatarUrl);

        LOGGER(INFO, __func__ , " <-- alias already exists");
        return AliasExisted;
    }

    pair<map<string, shared_ptr<UserInfo> >::iterator, bool> ret;

    // Check if we already have the user's UID in the map. If not then cache the
    // userInfo with the UUID and add the alias for the UUID
    AliasAdd retValue;
    it = nameMap_.find(uuid);
    if (it == nameMap_.end()) {
        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(userInfo->uniqueId, userInfo));
        if (!ret.second) {
            LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 0);
            return InsertFailed;
        }
        // For existing accounts (old accounts) the UUID and the display alias are identical
        // Don't add an alias entry in this case
        if (alias.compare(userInfo->uniqueId) != 0) {
            ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, userInfo));
            if (!ret.second) {
                LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 1);
                return InsertFailed;
            }
        }
        retValue = UuidAdded;
    }
    else {
        it->second->contactLookupUri.assign(userInfo->contactLookupUri);
        it->second->avatarUrl.assign(userInfo->avatarUrl);

        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, it->second));
        if (!ret.second) {
            LOGGER(ERROR, __func__ , " Insert in cache list failed. ", 2);
            return InsertFailed;
        }
        retValue = AliasAdded;
    }
    LOGGER(INFO, __func__ , " <--");
    lck.unlock();
    return retValue;
}

const shared_ptr<string> NameLookup::getDisplayName(const string& uuid)
{
    LOGGER(INFO, __func__ , " -->");
    shared_ptr<string> displayName = make_shared<string>();

    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing UUID data");
        return shared_ptr<string>();
    }
    unique_lock<mutex> lck(nameLock);

    if (nameMap_.size() == 0) {
        LOGGER(INFO, __func__ , " <-- empty name map");
        return shared_ptr<string>();
    }
    map<string, shared_ptr<UserInfo> >::iterator it;
    it = nameMap_.find(uuid);
    if (it != nameMap_.end()) {
        *displayName = (*it).second->displayName;
    }
    lck.unlock();
    LOGGER(INFO, __func__ , " <--");
    return displayName;
}

