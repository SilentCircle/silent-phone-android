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
#include "Logger.h"

using namespace std;
using namespace logging;

FileLogPolicy::~FileLogPolicy()
{
    if (outStream) {
        closeStream();
    }
}

void FileLogPolicy::openStream(const std::string& name)
{
    outStream->open(name.c_str(), std::ios_base::binary|std::ios_base::out);
    if (!outStream->is_open()) {
        throw(std::runtime_error("LOGGER: Unable to open an output stream"));
    }
}

void FileLogPolicy::closeStream()
{
    if (outStream) {
        outStream->close();
    }
}

void FileLogPolicy::write(LoggingLogLevel level, const std::string& tag, const std::string& msg)
{
    (void)level;
    (void)tag;

    (*outStream) << msg << std::endl;
}

#ifdef ANDROID_LOGGER
void AndroidLogPolicy::write(LoggingLogLevel level, const std::string& tag, const std::string& msg)
{
    android_LogPriority priority = ANDROID_LOG_UNKNOWN;
    switch (level) {
        case DEBUGGING:
            priority = ANDROID_LOG_DEBUG;
            break;
        case WARNING:
            priority = ANDROID_LOG_WARN;
            break;
        case ERROR:
            priority = ANDROID_LOG_ERROR;
            break;
        case INFO:
            priority = ANDROID_LOG_INFO;
            break;
        case VERBOSE:
        case EPIC:
            priority = ANDROID_LOG_VERBOSE;
            break;
        default:
            break;
    }
    if (priority != ANDROID_LOG_UNKNOWN)
        __android_log_print(priority, tag.c_str(), "%s", msg.c_str());
}
#endif
