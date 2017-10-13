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
//
// Created by werner on 30.10.15.
//

#include <iostream>
#include "NameLookup.h"
#include <mutex>          // std::mutex, std::unique_lock
#include "../util/cJSON.h"
#include "../Constants.h"
#include "../provisioning/Provisioning.h"
#include "../util/Utilities.h"

using namespace std;
using namespace zina;

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

    LOGGER(DEBUGGING, __func__ , " -->");
    shared_ptr<UserInfo> userInfo = getUserInfo(alias, authorization);
    LOGGER(DEBUGGING, __func__ , " <--");
    return (userInfo) ? userInfo->uniqueId : Empty;
}

/*
 * Structure of the user info JSON that the provisioning server returns:

{"display_alias": <string>,
 "display_organization": <string>,
 "same_organization": <bool>
 "avatar_url": <string>,
 "display_name": <string>,
 "dr_enabled": <bool>,          // Will be removed?
 "uuid": <string>
 "data_retention": {
    "for_org_name": "Subman",
    "retained_data": {
        "attachment_plaintext": false,
        "call_metadata": true,
        "call_plaintext": false,
        "message_metadata": true,
        "message_plaintext": false
    }
  }
}
 *
 */
int32_t NameLookup::parseUserInfo(const string& json, UserInfo &userInfo)
{
    LOGGER(DEBUGGING, __func__ , " --> ");
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
    userInfo.uniqueId.assign(tmpData->valuestring);

    tmpData = cJSON_GetObjectItem(root, "default_alias");
    if (tmpData == NULL || tmpData->valuestring == NULL) {
        tmpData = cJSON_GetObjectItem(root, "display_alias");
        if (tmpData == NULL || tmpData->valuestring == NULL) {
            cJSON_Delete(root);
            LOGGER(ERROR, __func__, " Missing 'default_alias' or 'display_alias' field.");
            return JS_FIELD_MISSING;
        }
    }
    userInfo.alias0.assign(tmpData->valuestring);

    tmpData = cJSON_GetObjectItem(root, "display_name");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo.displayName.assign(tmpData->valuestring);
    }
    tmpData = cJSON_GetObjectItem(root, "lookup_uri");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo.contactLookupUri.assign(tmpData->valuestring);
    }
    tmpData = cJSON_GetObjectItem(root, "avatar_url");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo.avatarUrl.assign(tmpData->valuestring);
    }
    userInfo.drEnabled = Utilities::getJsonBool(tmpData, "dr_enabled", false);

    tmpData = cJSON_GetObjectItem(root, "display_organization");
    if (tmpData != NULL && tmpData->valuestring != NULL) {
        userInfo.organization.assign(tmpData->valuestring);
    }

    userInfo.inSameOrganization = Utilities::getJsonBool(tmpData, "same_organization", false);

    tmpData = cJSON_GetObjectItem(root, "data_retention");

    if (tmpData != NULL) {
        userInfo.retainForOrg = Utilities::getJsonString(tmpData, "for_org_name", "");
        tmpData = cJSON_GetObjectItem(tmpData, "retained_data");
        if (tmpData != NULL) {
            userInfo.drRrmm = Utilities::getJsonBool(tmpData, "message_metadata", false);
            userInfo.drRrmp = Utilities::getJsonBool(tmpData, "message_plaintext", false);
            userInfo.drRrcm = Utilities::getJsonBool(tmpData, "call_metadata", false);
            userInfo.drRrcp = Utilities::getJsonBool(tmpData, "call_plaintext", false);
            userInfo.drRrap = Utilities::getJsonBool(tmpData, "attachment_plaintext", false);
        }
    }

    cJSON_Delete(root);
    LOGGER(DEBUGGING, __func__ , " <--");
    return OK;
}

NameLookup::AliasAdd
NameLookup::insertUserInfoWithUuid(const string& alias, shared_ptr<UserInfo> userInfo)
{
    auto ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(userInfo->uniqueId, userInfo));

    // For existing accounts (old accounts) the UUID and the display alias are identical
    // Don't add an alias entry in this case
    if (alias.compare(userInfo->uniqueId) != 0) {
        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, userInfo));
    }
    return UuidAdded;
}

const shared_ptr<UserInfo> NameLookup::getUserInfo(const string &alias, const string &authorization, bool cacheOnly, int32_t* errorCode) {

    LOGGER(DEBUGGING, __func__ , " --> ", alias);
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
        LOGGER(DEBUGGING, __func__ , " <-- cached data");
        if (it->second->displayName == USER_NULL_NAME) {
            return shared_ptr<UserInfo>();
        }
        return it->second;
    }
    if (cacheOnly) {
        LOGGER(DEBUGGING, __func__ , " <-- cached data");
        return shared_ptr<UserInfo>();
    }
    if (authorization.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing authorization");
        if (errorCode != NULL)
            *errorCode = GENERIC_ERROR;
        return shared_ptr<UserInfo>();
    }

    lck.unlock();
    string result;
    int32_t code = Provisioning::getUserInfo(alias, authorization, &result);
    lck.lock();

    // Return empty pointer in case of HTTP error
    if (code >= 400) {
        char temp[1000];
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
    code = parseUserInfo(result, *userInfo);
    if (code != OK) {
        LOGGER(ERROR, __func__ , " Error return from parsing.");
        return shared_ptr<UserInfo>();
    }

    pair<map<string, shared_ptr<UserInfo> >::iterator, bool> ret;

    // Check if we already have the user's UID in the map. If not then cache the
    // userInfo with the UID
    it = nameMap_.find(userInfo->uniqueId);
    if (it == nameMap_.end()) {
        insertUserInfoWithUuid(alias, userInfo);
    }
    else {
        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, it->second));
        userInfo = it->second;
    }
    lck.unlock();
    if (userInfo->displayName == USER_NULL_NAME) {
        LOGGER(DEBUGGING, __func__ , " <-- return null name");
        return shared_ptr<UserInfo>();
    }
    LOGGER(DEBUGGING, __func__ , " <-- ", alias, ", ", userInfo->displayName);
    return userInfo;
}

shared_ptr<UserInfo> NameLookup::refreshUserData(const string& aliasUuid, const string& authorization)
{
    LOGGER(DEBUGGING, __func__ , " -->");
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
    }
    string result;
    int32_t code = Provisioning::getUserInfo(aliasUuid, authorization, &result);
    if (code >= 400) {
        LOGGER(ERROR, __func__ , " <-- no refresh for unknown user");
        return shared_ptr<UserInfo>();
    }
    UserInfo userInfo;
    code = parseUserInfo(result, userInfo);
    if (code != OK) {
        LOGGER(ERROR, __func__ , " Error return from parsing.");
        return shared_ptr<UserInfo>();
    }
    // Replace existing data, don't touch the lookup_uri because the server _never_ sends
    // it. Only the application may delete it.
    it->second->displayName.assign(userInfo.displayName);
    it->second->alias0.assign(userInfo.alias0);
    it->second->avatarUrl.assign(userInfo.avatarUrl);
    it->second->organization.assign(userInfo.organization);
    it->second->inSameOrganization= userInfo.inSameOrganization;
    it->second->drEnabled = userInfo.drEnabled;

    it->second->drRrmm = userInfo.drRrmm;
    it->second->drRrmp = userInfo.drRrmp;
    it->second->drRrcm = userInfo.drRrcm;
    it->second->drRrcp = userInfo.drRrcp;
    it->second->drRrap = userInfo.drRrap;
    it->second->retainForOrg = userInfo.retainForOrg;

    return it->second;
}

const shared_ptr<list<string> > NameLookup::getAliases(const string& uuid)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    shared_ptr<list<string> > aliasList = make_shared<list<string> >();
    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- empty uuid");
        return shared_ptr<list<string> >();
    }
    unique_lock<mutex> lck(nameLock);

    if (nameMap_.size() == 0) {
        LOGGER(DEBUGGING, __func__ , " <-- empty name map");
        return shared_ptr<list<string> >();
    }
    for (map<string, shared_ptr<UserInfo> >::iterator it=nameMap_.begin(); it != nameMap_.end(); ++it) {
        shared_ptr<UserInfo> userInfo = (*it).second;
        // Add aliases to the result. If the map entry is the UUID entry then add the default alias
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
    LOGGER(DEBUGGING, __func__ , " <--");
    return aliasList;
}

NameLookup::AliasAdd NameLookup::addAliasToUuid(const string& alias, const string& uuid, const string& userData)
{
    LOGGER(DEBUGGING, __func__ , " -->");

    unique_lock<mutex> lck(nameLock);

    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing UUID data");
        return UserDataError;
    }

    shared_ptr<UserInfo> userInfo = make_shared<UserInfo>();
    int32_t code = parseUserInfo(userData, *userInfo);
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

        LOGGER(DEBUGGING, __func__ , " <-- alias already exists");
        return AliasExisted;
    }

    pair<map<string, shared_ptr<UserInfo> >::iterator, bool> ret;

    // Check if we already have the user's UID in the map. If not then cache the
    // userInfo with the UUID and add the alias for the UUID
    AliasAdd retValue;
    it = nameMap_.find(uuid);
    if (it == nameMap_.end()) {
        insertUserInfoWithUuid(alias, userInfo);
        retValue = UuidAdded;
    }
    else {
        it->second->contactLookupUri.assign(userInfo->contactLookupUri);
        it->second->avatarUrl.assign(userInfo->avatarUrl);

        ret = nameMap_.insert(pair<string, shared_ptr<UserInfo> >(alias, it->second));
        retValue = AliasAdded;
    }
    LOGGER(DEBUGGING, __func__ , " <--");
    lck.unlock();
    return retValue;
}

const shared_ptr<string> NameLookup::getDisplayName(const string& uuid)
{
    LOGGER(DEBUGGING, __func__ , " -->");
    shared_ptr<string> displayName = make_shared<string>();

    if (uuid.empty()) {
        LOGGER(ERROR, __func__ , " <-- missing UUID data");
        return shared_ptr<string>();
    }
    unique_lock<mutex> lck(nameLock);

    if (nameMap_.size() == 0) {
        LOGGER(DEBUGGING, __func__ , " <-- empty name map");
        return shared_ptr<string>();
    }
    map<string, shared_ptr<UserInfo> >::iterator it;
    it = nameMap_.find(uuid);
    if (it != nameMap_.end()) {
        *displayName = (*it).second->displayName;
    }
    lck.unlock();
    LOGGER(DEBUGGING, __func__ , " <--");
    return displayName;
}

