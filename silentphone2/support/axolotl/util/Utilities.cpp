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
// Created by werner on 07.06.16.
//

#include <sys/time.h>
#include <string.h>
#include "Utilities.h"

using namespace std;
using namespace zina;


bool Utilities::hasJsonKey(const cJSON* const root, const char* const key) {
    if (root == nullptr)
        return false;
    cJSON* jsonItem = cJSON_GetObjectItem(const_cast<cJSON*>(root), key);
    return jsonItem != nullptr;
}


int32_t Utilities::getJsonInt(const cJSON* const root, const char* const name, int32_t error) {
    if (root == nullptr)
        return error;
    cJSON* jsonItem = cJSON_GetObjectItem(const_cast<cJSON*>(root), name);
    if (jsonItem == nullptr)
        return error;
    return jsonItem->valueint;
}


double Utilities::getJsonDouble(const cJSON* const root, const char* const name, double error) {
    if (root == nullptr)
        return error;
    cJSON* jsonItem = cJSON_GetObjectItem(const_cast<cJSON*>(root), name);
    if (jsonItem == nullptr)
        return error;
    return jsonItem->valuedouble;
}


const char *const Utilities::getJsonString(const cJSON* const root, const char* const name, const char *error) {
    if (root == nullptr)
        return error;
    cJSON* jsonItem = cJSON_GetObjectItem(const_cast<cJSON*>(root), name);
    if (jsonItem == nullptr)
        return error;
    return jsonItem->valuestring;
}

void Utilities::setJsonString(cJSON* const root, const char* const name, const char *value, const char *def) {
    if (root == nullptr || name == nullptr)
        return;

    cJSON_AddStringToObject(root, name, value == nullptr ? def : value);
    return;
}

bool Utilities::getJsonBool(const cJSON *const root, const char *const name, bool error) {
    if (root == nullptr)
        return error;
    cJSON* jsonItem = cJSON_GetObjectItem(const_cast<cJSON*>(root), name);
    if (jsonItem == nullptr)
        return error;
    if (jsonItem->type == cJSON_True || jsonItem->type == cJSON_False)
        return jsonItem->type == cJSON_True;
    return error;
}

shared_ptr<vector<string> >
Utilities::splitString(const string& data, const string delimiter)
{
    shared_ptr<vector<string> > result = make_shared<vector<string> >();

    if (data.empty() || (delimiter.empty() || delimiter.size() > 1)) {
        return result;
    }
    string copy(data);

    size_t pos = 0;
    while ((pos = copy.find(delimiter)) != string::npos) {
        string token = copy.substr(0, pos);
        copy.erase(0, pos + 1);
        result->push_back(token);
    }
    if (!copy.empty()) {
        result->push_back(copy);
    }

    size_t idx = result->empty() ? 0: result->size() - 1;
    while (idx != 0) {
        if (result->at(idx).empty()) {
            result->pop_back();
            idx--;
        }
        else
            break;
    }
    return result;
}

string Utilities::currentTimeMsISO8601()
{
    char buffer[80];
    char outbuf[80];
    struct timeval tv;
    struct tm timeinfo;

    gettimeofday(&tv, NULL);
    time_t currentTime = tv.tv_sec;

    const char* format = "%FT%T";
    strftime(buffer, 80, format ,gmtime_r(&currentTime, &timeinfo));
    snprintf(outbuf, 80, "%s.%03dZ\n", buffer, static_cast<int>(tv.tv_usec / 1000));
    return string(outbuf);
}

string Utilities::currentTimeISO8601()
{
    char outbuf[80];
    struct tm timeinfo;

    time_t currentTime = time(NULL);

    const char* format = "%FT%TZ";
    strftime(outbuf, 80, format, gmtime_r(&currentTime, &timeinfo));
    return string(outbuf);
}

uint64_t Utilities::currentTimeMillis()
{
    struct timeval tv;

    gettimeofday(&tv, 0);

    uint64_t timeStamp = static_cast<uint64_t>(tv.tv_usec / 1000);
    timeStamp += ((uint64_t) tv.tv_sec) * 1000;
    return timeStamp;
}

void Utilities::wipeString(string &toWipe)
{
    // This append is necessary: the GCC C++ string implementation uses shared strings, reference counted. Thus
    // if we set the data buffer to 0 then all other references are also cleared. Appending a blank forces the string
    // implementation to really copy the string and we can set the contents to 0. string.clear() does not clear the
    // contents, just sets the length to 0 which is not good enough.
    toWipe.append(" ");
    wipeMemory((void*)toWipe.data(), toWipe.size());
    toWipe.clear();
}

static const char decimal2hex[] = "0123456789ABCDEF";

string Utilities::urlEncode(string s)
{
    const char *str = s.c_str();
    vector<char> v(s.size());
    v.clear();
    for (size_t i = 0, l = s.size(); i < l; i++)
    {
        char c = str[i];
        if ((c >= '0' && c <= '9') ||
            (c >= 'a' && c <= 'z') ||
            (c >= 'A' && c <= 'Z') ||
            c == '-' || c == '_' || c == '.' || c == '!' || c == '~' ||
            c == '*' || c == '\'' || c == '(' || c == ')')
        {
            v.push_back(c);
        }
        else
        {
            v.push_back('%');
            char d1 = decimal2hex[c >> 4];
            char d2 = decimal2hex[c & 0x0F];

            v.push_back(d1);
            v.push_back(d2);
        }
    }

    return string(v.cbegin(), v.cend());
}
