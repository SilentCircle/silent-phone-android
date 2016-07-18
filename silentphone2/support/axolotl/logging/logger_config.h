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
