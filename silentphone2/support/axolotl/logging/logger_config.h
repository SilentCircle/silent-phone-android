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
/**
 * @file logger_config.h
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 * @version 1.0
 */
#ifndef LOGGING_LOGGER_CONFIG_H
#define LOGGING_LOGGER_CONFIG_H

#ifdef __ANDROID__
    #include <android/log.h>
    #define ANDROID_LOGGER  // to use the __android_log_print(ANDROID_LOG_xxx, tag, "%s", logString); functions
#elif defined _WIN32 || defined __CYGWIN__
    #define WINDOWS_LOGGER
#elif defined __linux__
    #define LINUX_LOGGER
#elif defined __APPLE__
    #define APPLE_LOGGER
// add other specifics here
#endif

#endif //LOGGING_LOGER_CONFIG_H_H
