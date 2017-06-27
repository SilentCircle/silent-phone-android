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
// Created by werner on 07.06.16.
//

#ifndef LIBZINALOTL_UTILITIES_H
#define LIBZINALOTL_UTILITIES_H

/**
 * @file Utilities.h
 * @brief Some utility and helper functions
 * @ingroup Zina
 * @{
 */


#include <sys/types.h>
#include <string>
#include <vector>
#include <memory>
#include <cstring>
#include "cJSON.h"

namespace zina {
    class Utilities {

    public:
        /**
         * @brief Return true if the cJSON structure has the given key
         *
         * @param root the pointer to the cJSON structure
         * @name Name of the key
         */
        static bool hasJsonKey(const cJSON* const root, const char* const key);

        /**
         * @brief Return an integer value from a JSON structure.
         *
         * @param root the pointer to the cJSON structure
         * @name Name of the value
         * @error Error value, the function returns this value if the JSON structure contains no @c name
         */
        static int32_t getJsonInt(const cJSON* const root, const char* const name, int32_t error);

        /**
         * @brief Return a double value from a JSON structure.
         *
         * @param root the pointer to the cJSON structure
         * @name Name of the value
         * @error Error value, the function returns this value if the JSON structure contains no @c name
         */
        static double getJsonDouble(const cJSON* const root, const char* const name, double error);

        /**
         * @brief Return a c-string value from a JSON structure.
         *
         * The functions returns a pointer to the c-string inside the cJSON data structure.
         * The caller must not free or modify this pointer.
         *
         * @param root the pointer to the cJSON structure
         * @name Name of the value
         * @error Error value, the function returns this value if the JSON structure contains no @c name
         */
        static const char* const getJsonString(const cJSON* const root, const char* const name, const char* const error);

        /**
         * @brief Set a string in the cJSON root.
         *
         * If `root` or `name` are `nullptr` then return and do not change anything. If the `value` is `nullptr`
         * then use the default.
         *
         * @param root cJSON root
         * @param name the JSON key
         * @param value The value to set
         * @param def Set this if the value is `nullptr`
         */
        static void setJsonString(cJSON* const root, const char* const name, const char *value, const char *def);

        /**
         * @brief Return a boolean value from a JSON structure.
         *
         * The functions returns the boolean value of a JSON name.
         *
         * @param root the pointer to the cJSON structure
         * @name Name of the value
         * @error Error value, the function returns this value if the JSON structure contains no @c name
         */
        static bool getJsonBool(const cJSON* const root, const char* const name, bool error);

        /**
         * @brief Splits a string around matches of the given delimiter character.
         *
         * Trailing empty strings are not included in the resulting array.
         * This function works similar to the Java string split function, however it does
         * not support regular expressions, only a simple delimiter character.
         *
         * @param data The std::string to split
         * @param delimiter The delimiter character
         * @return A vector of strings
         */
        static std::shared_ptr<std::vector<std::string> > splitString(const std::string& data, const std::string delimiter);

        /**
         * @brief Returns a string with date and Time with milliseconds, formatted according to ISO8601.
         *
         * The function uses Zulu (GMT) time, not the local time as input to generate the string.
         * Example of a formatted string: 2016-08-30T13:09:17.122Z
         *
         * @return A formatted string with current Zulu time.
         */
        static std::string currentTimeMsISO8601();

        /**
         * @brief Returns a string with date and Time without milliseconds, formatted according to ISO8601.
         *
         * The function uses Zulu (GMT) time, not the local time as input to generate the string.
         * Example of a formatted string: 2016-08-30T13:09:17Z
         *
         * @return A formatted string with current Zulu time.
         */
        static std::string currentTimeISO8601();

        /**
         * @brief get the current time in milliseconds.
         *
         * @return The time in milliseconds
         */
        static uint64_t currentTimeMillis();

        /**
         * @brief Wipe a string.
         *
         * Fills the internal buffer of a string with zeros.
         *
         * @param toWipe The string to wipe.
         */
        static void wipeString(std::string &toWipe);

        /**
         * @brief Wipe memory.
         *
         * Fills a data buffer with zeros.
         *
         * @param data pointer to the data buffer.
         * @param length length of the data buffer in bytes
         */
        static inline void wipeMemory(void* data, size_t length) {
            static void * (*volatile memset_volatile)(void *, int, size_t) = std::memset;
            memset_volatile(data, 0, length);
        }

        /**
         * @brief URL-encode the input string and return the encoded string
         *
         * @param s Input string
         * @return URL-encoded string
         */
        static std::string urlEncode(std::string s);
    };
}

/**
 * @}
 */
#endif //LIBZINALOTL_UTILITIES_H
