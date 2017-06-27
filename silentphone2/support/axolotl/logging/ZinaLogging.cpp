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
// Created by werner on 30.11.15.
//

#include "ZinaLogging.h"


#ifdef ANDROID_LOGGER
std::shared_ptr<logging::Logger<logging::AndroidLogPolicy> >
        _globalLogger = std::make_shared<logging::Logger<logging::AndroidLogPolicy> >(std::string(""),  std::string("libzina"));

#elif defined(LINUX_LOGGER)

std::shared_ptr<logging::Logger<logging::CerrLogPolicy> >
        _globalLogger = std::make_shared<logging::Logger<logging::CerrLogPolicy> >(std::string(""), std::string("libzina"));

#elif defined(APPLE_LOGGER)

/**
 * The following code is for internal iOS (APPLE)logging only
 *
 */
static void (*_zina_log_cb)(void *ret, const char *tag, const char *buf) = nullptr;
static void *pLogRet = nullptr;

// this function must be public. Tivi C++ code set its internal log function
void set_zina_log_cb(void *pRet, void (*cb)(void *ret, const char *tag, const char *buf)){
    _zina_log_cb = cb;
    pLogRet = pRet;
}

void logging::zina_log(const char *tag, const char *buf) {
    if(_zina_log_cb){
        _zina_log_cb(pLogRet, tag, buf);
    }
}

std::shared_ptr<logging::Logger<logging::IosLogPolicy> >
        _globalLogger = std::make_shared<logging::Logger<logging::IosLogPolicy> >(std::string(""), std::string("libzina"));

#else
#error "Define Logger instance according to the system in use."
#endif

void setZinaLogLevel(int32_t level)
{
    _globalLogger->setLogLevel(static_cast<LoggingLogLevel>(level));
}
