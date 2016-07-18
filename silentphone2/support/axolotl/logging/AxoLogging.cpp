//
// Created by werner on 30.11.15.
//

#include "AxoLogging.h"

#ifdef ANDROID_LOGGER
std::shared_ptr<logging::Logger<logging::AndroidLogPolicy> >
        _globalLogger = std::make_shared<logging::Logger<logging::AndroidLogPolicy> >(std::string(""),  std::string("axolib"));

#elif defined(LINUX_LOGGER) || defined(APPLE_LOGGER)
std::shared_ptr<logging::Logger<logging::CerrLogPolicy> >
        _globalLogger = std::make_shared<logging::Logger<logging::CerrLogPolicy> >(std::string(""), std::string("axolib"));

#else
#error "Define Logger instance according to the system in use."
#endif

void setAxoLogLevel(int32_t level)
{
    _globalLogger->setLogLevel(static_cast<LoggingLogLevel>(level));
}