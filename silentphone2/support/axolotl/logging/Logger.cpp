/**
 * @file Logger.cpp
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 * @version 1.0
 *
 * @brief C++ logging functions
 * @ingroup Logging
 * @{
 *
 * Definition of global Logger instance, implementation of Logger policies
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
    if (!outStream->is_open())
    {
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

/**
 * @}
 */
