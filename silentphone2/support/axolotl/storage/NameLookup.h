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

#ifndef LIBAXOLOTL_NAMELOOKUP_H
#define LIBAXOLOTL_NAMELOOKUP_H

#include <string>
#include <map>
#include <memory>
#include <utility>
#include <list>

/**
 * @file NameLookup.h
 * @brief Perform lookup and cahing of alias names and return the UID
 *
 * @ingroup Zina
 * @{
 */

namespace zina {

    class UserInfo {
    public:
        explicit UserInfo() : drEnabled(false), drRrmm(false), drRrmp(false), drRrcm(false), drRrcp(false), drRrap(false) { }
        std::string uniqueId;         //!< User's unique name, canonical name, not human readable
        std::string displayName;      //!< User's full/display name as stored in the provisioning server
        std::string alias0;           //!< Primary alias, aka preferred alias, aka alias0
        std::string contactLookupUri; //!< Set by contacts discovery to the contact's lookup key
        std::string avatarUrl;        //!< Avatar URL from provisioning server
        std::string organization;     //!< User's organization
        std::string retainForOrg;     //!< This organization defined the retention policy
        bool   drEnabled;             //!< Data Retention enabled flag
        bool   drRrmm;                //!< RRMM: "remote retains message metadata"
        bool   drRrmp;                //!< RRMP: "remote retains message plaintext"
        bool   drRrcm;                //!< RRCM: "remote retains call metadata"
        bool   drRrcp;                //!< RRCP: "remote retains call plaintext (audio)"
        bool   drRrap;                //!< RRAP: "remote retains attachment plaintext"
        bool   inSameOrganization;    //!< User in same organization as requester
    };

    class NameLookup {
    public:
        enum AliasAdd {
            MissingParameter = -3,
            InsertFailed = -2,  //!< Insert in name Map failed
            UserDataError = -1, //!< User data has incorrect format or misses data
            AliasExisted = 1,   //!< Alias name already exists in the name map
            UuidAdded = 2,      //!< UUID, alias and user data added
            AliasAdded = 3      //!< Alias name added to existing UUID
        };

        static NameLookup* getInstance();

        /**
         * @brief Get UUID of an alias, e.g. a name or number.
         *
         * Because this function may request this mapping from a server the caller
         * must not call this function in the main (UI) thread.
         *
         * @param alias the alias name/number
         * @authorization the authorization data
         *
         * @return A UUID string or empty shared pointer if alias is not known.
         */
        const std::string getUid(const std::string& alias, const std::string& authorization);

        /**
         * @brief Get UserInfo of an alias, e.g. a name or number.
         *
         * This function returns the user information that's stored in the cache or on the
         * provisioning server for the alias. Because this function may request this mapping
         * from a server the caller must not call this function in the main (UI) thread.
         *
         * The @c display_name string contains the user's full/display name as returned by the
         * provisioning server, the @c alias0 is the user's display alias, returned by the
         * provisioning server. The @c lookup_uri may be empty if it was not set in the lookup
         * cache with #addAliasToUuid.
         *
         * Note: the provisioning server never returns a @c lookup_uri string, the application
         * must call #addAliasToUuid to set this string. Despite it's name an application may use
         * this string to store some internal data for a UUID - the name was chosen because we used
         * it to store the @c lookup_uri of a contact entry in Android's contact application.
         *
         * @param alias the alias name/number or the UUID
         * @param authorization the authorization data, can be empty if @c cacheOnly is @c true
         * @param cacheOnly If true only look in the cache, don't contact server if not in cache
         * @return The UserInfo or empty shared pointer if alias is not known.
         */
        const std::shared_ptr<UserInfo> getUserInfo(const std::string& alias, const std::string& authorization, bool cacheOnly = false,
                                                    int32_t* errorCode=NULL);

        /**
         * @brief Get UserInfo of an alias, e.g. a name or number, if in cache
         *
         * This function does no trigger any network actions, save to run from UI thread.
         *
         * @param alias the alias name/number or the UUID
         * @return A JSON string containing the UserInfo or empty shared pointer if alias is not in cache.
         */
        const std::shared_ptr<UserInfo> getUserInfoFromCache(const std::string& alias)
        { return getUserInfo(alias, std::string(), true); }

        /**
         * @brief Return a list of the alias names of a UUID.
         *
         * This function does no trigger any network actions, save to run from UI thread.
         *
         * @param uuid the UUID
         * @authorization the authorization data
         * @return List of strings or empty shared pointer if alias is not known.
         */
        const std::shared_ptr<std::list<std::string> > getAliases(const std::string& uuid);

        /**
         * @brief Add an alias name and user info to an UUID.
         *
         * If the alias name already exists in the map the function is a no-op and returns
         * immediately after amending the @c lookup_uri string if necessary.
         *
         * The function then performs a lookup on the UUID. If it exists then it simply
         * adds the alias name for this UUID and uses the already existing user info, thus
         * ignores the provided user info except for the @c lookup_uri and @c avatar_url strings.
         *
         * If @c lookup_uri or @c avatar_url are empty in the cached user info and it they are
         * available in the provided user info then the functions stores the @c lookup_uri or
         * @c avatar_url string, thus the caller can amend existing user info data with
         * @c lookup_uri and/or @c avatar_url.
         *
         * If the UUID does not exist the function creates an UUID entry in the cache and
         * links the user info to the new entry. Then it adds the alias name to the UUID.
         *
         * If the UUID does not exist the functions creates a UUID entry and links the
         * user info to the new entry. Then it adds the alias name to the UUID.
         *
         * This function does no trigger any network actions, save to run from UI thread.
         *
         * The JSON data should look like this:
         *
         * @param alias the alias name/number
         * @param uuid the UUID
         * @param userInfo a JSON formatted string with the user information of the
         * @authorization the authorization data
         * @return a value > 0 to indicate success, < 0 on failure.
         */
        AliasAdd addAliasToUuid(const std::string& alias, const std::string& uuid, const std::string& userInfo);

        void clearNameCache() { nameMap_.clear(); }

        /**
         * @brief Return the display name of a UUID.
         *
         * This function does no trigger any network actions, save to run from UI thread.

         * @param uuid the UUID
         * @authorization the authorization data
         * @return The display name or an empty shared pointer if none available
         */
        const std::shared_ptr<std::string> getDisplayName(const std::string& uuid);

        /**
         * @brief Refresh cached user data
         *
         * The function accesses the provisioning server to get a fresh set of user data.
         *
         * @param alias the alias name/number or the UUID
         * @param authorization the authorization data, can be empty if @c cacheOnly is @c true
         * @param cacheOnly If true only look in the cache, don't contact server if not in cache
         * @return The refreshed UserInfo or an empty shared pointer if alias is not known or error.
         */
        std::shared_ptr<UserInfo> refreshUserData(const std::string& aliasUuid, const std::string& authorization);

    private:
        int32_t parseUserInfo(const std::string& json, UserInfo &userInfo);
        NameLookup::AliasAdd insertUserInfoWithUuid(const std::string& alias, std::shared_ptr<UserInfo> userInfo);

        std::map<std::string, std::shared_ptr<UserInfo> > nameMap_;
        static NameLookup* instance_;
    };
}
/**
 * @}
 */

#endif //LIBAXOLOTL_NAMELOOKUP_H
