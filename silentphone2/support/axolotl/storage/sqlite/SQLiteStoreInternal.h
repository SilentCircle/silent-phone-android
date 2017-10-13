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

#ifndef LIBZINA_SQLITESTOREINTERNAL_H_H
#define LIBZINA_SQLITESTOREINTERNAL_H_H

/**
 * @file
 * @brief 
 * @ingroup zina
 * @{
 */

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
 * - an integer (int) variable with name "sqlResult" that stores return codes from sqlite
 * - ERRMSG
 */
#define ERRMSG  {if (SQL_FAIL(sqlResult)) {snprintf(lastError_, (size_t)DB_CACHE_ERR_BUFF_SIZE, \
                          "SQLite3 error: %s, line: %d, error message: %s\n", __FILE__, __LINE__, sqlite3_errmsg(db)); \
                    extendedErrorCode_ = sqlite3_extended_errcode(db); } }

#define SQLITE_CHK(func) {          \
        sqlResult = (func);          \
        if(sqlResult != SQLITE_OK) { \
            ERRMSG;                 \
            goto cleanup;           \
        }                           \
    }

#define SQLITE_PREPARE sqlite3_prepare_v2

#define DB_VERSION 8


/**
 * @}
 */
#endif //LIBZINA_SQLITESTOREINTERNAL_H_H
