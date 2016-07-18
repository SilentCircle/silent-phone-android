/**
 * @file Logger.h
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 * @version 1.0
 *
 * @brief C++ logging functions
 * @ingroup Logging
 * @{
 *
 * This set of macros and templates implement a flexible C++ logging infrastructure.
 */

#ifndef LOGGING_LOGGER_H
#define LOGGING_LOGGER_H

#include <string>
#include <fstream>
#include <memory>
#include <mutex>
#include <sstream>
#include <iostream>

#include "osSpecifics.h"
#include "logger_config.h"

/**
 * @brief The usual log level definitions.
 */
enum LoggingLogLevel {
    NONE = 0,   //!< No log output
    ERROR,      //!< The Error level log output
    WARNING,    //!< The Warning level log output
    INFO,       //!< The Info level log output
    DEBUGGING,  //!< The Debug level log output
    VERBOSE,    //!< The Verbose level log output
    EPIC        //!< The Epic level log output, only for rare cases :-)
};

/**
 * @brief How to format/output the log data.
 */
enum LoggingLogType {
    RAW = 0,       //!< Output the RAW format as in the LOGGER call
    FULL           //!< Add log header: log line number, date, time, etc
};

/**
 * @brief The LOGGER_INSTANCE default definition
 *
 * Define the standard LOGGER_INSTANCE as @c logInst. The source that include
 * this file may defines its onwl LOGGER_INSTANCE, hoever it must define it before
 * including this file. See the file description above.
 */
#ifndef LOGGER_INSTANCE
#define LOGGER_INSTANCE logInst.
#endif

/**
 * @brief Defines the maximum log level during compile time.
 *
 * See the comments for the @c LOGGER macro below.
 *
 */
#ifndef LOG_MAX_LEVEL
#define LOG_MAX_LEVEL VERBOSE
#endif

/**
 * @brief The logger macro using the log level and a variable number of arguments.
 *
 * Found the idea for this macro in Dr. Dobbs with the following comment (enhanced by me):
 *
 * This code is interesting in that it combines two tests. If you pass the LOG_MAX_LEVEL compile-time
 * constant to LOGGER, the first test is against two such constants (@c level and the LOG_MAX_LEVEL)
 * and any optimizer will pick that up statically and discard the dead branch entirely from generate
 * code. This optimization is so widespread, you can safely count on it in most environments you take
 * your code. The second test examines the runtime logging level, as before.
 *
 * Effectively, LOG_MAX_LEVEL imposes a static plateau on the dynamically allowed range of logging
 * levels: Any logging level above the static plateau is simply eliminated from the code.
 *
 * I tweaked and enhanced the macro to be useful for this logging implementation.
 */
#define LOGGER(level, args...) {\
    if (level > LOG_MAX_LEVEL) ;\
    else if (level > LOGGER_INSTANCE getLogLevel()) ; \
    else LOGGER_INSTANCE print<LoggingLogLevel::level >(args);}


namespace logging {

    class LogPolicy
    {
    public:
        virtual void openStream(const std::string& name) = 0;
        virtual void closeStream() = 0;
        virtual void write(LoggingLogLevel level, const std::string& tag, const std::string& msg) = 0;
        virtual LoggingLogType getLoggingLogType() = 0;
    };

    /**
     * Implementation which allow to write into a file
     */
    class __EXPORT FileLogPolicy : public LogPolicy
    {
        std::unique_ptr<std::ofstream> outStream;

    public:
        FileLogPolicy() : outStream(new std::ofstream()) {}
        ~FileLogPolicy();

        void openStream(const std::string& name);
        void closeStream();
        void write(LoggingLogLevel level, const std::string& tag, const std::string& msg);
        LoggingLogType getLoggingLogType() { return FULL; }
    };

    /**
     * Implements a logging Policy to write to std::cerr directly
     */
    class __EXPORT CerrLogPolicy : public LogPolicy
    {
    public:
        CerrLogPolicy() {}
        ~CerrLogPolicy() {};

        void openStream(const std::string& name) {};
        void closeStream() {};
        void write(LoggingLogLevel level, const std::string& tag, const std::string& msg) { std::cerr << msg << std::endl;};
        LoggingLogType getLoggingLogType() { return FULL; }
    };

#ifdef ANDROID_LOGGER
    /**
     * Implements a logging Policy designed fo Android logging
     */
    class __EXPORT AndroidLogPolicy : public LogPolicy
    {
    public:
        AndroidLogPolicy() {}
        ~AndroidLogPolicy() {};

        void openStream(const std::string& name) {};
        void closeStream() {};
        void write(LoggingLogLevel level, const std::string& tag, const std::string& msg);
        LoggingLogType getLoggingLogType() { return RAW; }
    };
#endif


/**
 * The Logger class uses a LogPolicy which implements the low level functions to output
 * the log data which the logger class produces. Together with the macros @c LOGGER_INSTANCE,
 * @c LOG_MAX_LEVEL and @c LOGGER you can implement global Logger instances or local Logger
 * instances.
 *
 * An example for a global Logger instance:
 * - define a global, project specific Logger source file, for example MyGlobalLogger.cpp
 * - in this source file define you global Logger setup, for example

@verbatim
using namespace std;

#define LOGGER_INSTANCE myGlobalLogger->
#include "Logger.h"
shared_ptr<logging::Logger<logging::CerrLogPolicy> >
        myGlobalLogger = make_shared<logging::Logger<logging::CerrLogPolicy> >(string(""));
@endverbatim

 * Then declare the above Logger instance as external, either in an already existing include
 * file that most or all your project's source modules use or create a small @c MyGlobalLogger.h
 * for this purpose and include it in every source module that requires some logging.
 * The LogPolicy shown in the example uses the @c CerrLogPolicy which simply outputs all
 * log data to @c std::cerr
 *
 * In case of a global Logger instance it's important to define the @c LOGGER_INSTANCE macro
 * before including the @c logger.h files. Otherwise the default definition of the
 * @c LOGGER_INSTANCE macro kicks in.
 *
 * If a source module requires some special logging then it should not include the global
 * logging include file and use the plain @c Logger.h and setup its own Logger. This is a
 * simple setup, for example:
 *
@verbatim
#include "Logger.h"

using namespace logging;
static Logger <FileLogPolicy> logInst("example_1.log");
@endverbatim

 * This example defines a static Logger instance which uses a @c FileLogPolicy that outputs
 * the log data to the file @c example_1.log. The name of the Logger instance @c logInst is
 * the standard definition of the @c LOGGER_INSTANCE macro.
 *
 * Now your functions can set the desired log level for this moduel (standard is @c VERBOSE)
 * and use the @c lOGGER macro to produce log data, for example:
 *
@verbatim
LOGGER_INSTANCE setLogLevel(DEBUG);

LOGGER(ERROR, "Starting the application..");
for( short i = 0 ; i < 3 ; i++ ) {
    LOGGER(DEBUG, "The value of 'i' is ", i , ". " , 3 - i - 1 , " more iterations left ");
}
LOGGER(WARNING, "Loop over");
LOGGER(ERROR, "Exiting the application");
return 0;
@endverbatim
 *
 * For production builds you can restrict the log level during compilation: just define the
 * @c LOG_MAX_LEVEL in a project configuration file or set it via a @c -DLOG_MAX_LEVEL. The
 * implementation of the @c LOGGER macro together with the usual compiler optimizations
 * will remove any logging calls above the defined @c MAX_LOG_LEVEL. This safes space and
 * computing time and also no modifications in the source to change the run time log
 * level - if it isn't compiled then it will not run :-). If you set @c MAX_LOG_LEVEL to
 * @c NONE then no log statement is left in the compiled code.
 */
    template<typename log_policy >
    class __EXPORT Logger
    {
        std::string getTime();
        std::string getLogLineHeader();
        std::stringstream logStream;
        log_policy* policy;
        std::mutex write_mutex;

        //Core printing functionality
        void print_impl();
        template<typename First, typename...Rest> void print_impl(First parm1, Rest...parm);

        LoggingLogLevel logLevel;               //!< Write logs up to this level
        LoggingLogType logType;                 //!< Log type: add full information or log raw format
        LoggingLogLevel currentLogLevel;        //!< The log level as defined for the current LOGGER call
        std::string tag;                        //!< Mainly used for Android logging
        unsigned logLineNumber;

    public:
        /**
         * @brief Create a Logger instance.
         *
         * The Logger instance forwards the name to the defined LogPolicy, for example
         * in case of the @c FileLogPolicy to define the output file name. Other policies
         * may ignore the @c name.
         *
         * @param name The LogPolicy implementation may use this
         */
        explicit Logger(const std::string& name);

        /**
         * @brief Create a Logger instance.
         *
         * The Logger instance forwards the @c name and the @c tag to the defined LogPolicy,
         * for example the @c AndroidLogPolicy can use them to set Android's log tag.
         * Other policies may ignore the @c name and/or @c tag data
         *
         * @param name The LogPolicy implementation may use this.
         * @param tag The LogPolicy implementation may use this.
         */
        Logger(const std::string& name, const std::string& tag);
        ~Logger();

        /**
         * @brief Return the current log level.
         */
        int getLogLevel() { return logLevel; }

        /**
         * @brief Set the current log level.
         *
         * @param level The log level, range is @c NONE to @c EPIC
         */
        void setLogLevel(LoggingLogLevel level) {if (level >= NONE && level <= EPIC) {logLevel = level;}}

        /**
         * @brief Return the current log type.
         */
        int getLogType() { return logType; }

        /**
         * @brief Set the current log type.
         *
         * @param type The log format type, range is @c RAW to @c FULL
         */
        void setLogType(LoggingLogType type) {if (type >= RAW && type <= FULL) {logType = type;}}

        /**
         * @brief Print log data
         *
         * The Logger class formats the log data according to the log type and forwards
         * the data to the LogPolicy implementation of this instance.
         *
         * The logger class uses a @c std::stringstream instance to format the log
         * arguments, for example @c 'logStream << arg'
         *
         * @param args Variable number of arguments.
         */
        template<LoggingLogLevel level, typename...Args >
        void print(Args... args);
    };

    // Template implementations
    template<typename log_policy >
    Logger<log_policy >::Logger(const std::string& name) : logLevel(VERBOSE), currentLogLevel(NONE),
                                                           tag("Logger"), logLineNumber(0)
    {
        policy = new log_policy;
        if (!policy) {
            throw std::runtime_error("LOGGER: Unable to create the logger instance");
        }
        logType = policy->getLoggingLogType();
        policy->openStream( name );
    }

    template<typename log_policy >
    Logger<log_policy >::Logger(const std::string& name, const std::string& inTag) : logLevel(VERBOSE),
                                                                                     currentLogLevel(NONE), tag(inTag),
                                                                                     logLineNumber(0)
    {
        policy = new log_policy;
        if (!policy) {
            throw std::runtime_error("LOGGER: Unable to create the logger instance");
        }
        logType = policy->getLoggingLogType();
        policy->openStream( name );
    }

    template< typename log_policy >
    Logger<log_policy >::~Logger()
    {
        if (policy) {
            policy->closeStream();
            delete policy;
        }
    }

    template< typename LogPolicy >
    void Logger< LogPolicy >::print_impl()
    {
        if (logType == FULL)
            policy->write(currentLogLevel, tag, getLogLineHeader() + logStream.str());
        else
            policy->write(currentLogLevel, tag, logStream.str());
        logStream.str("");
    }

    template< typename LogPolicy >
    template<typename First, typename... Rest >
    void Logger<LogPolicy>::print_impl(First parm1, Rest... parm)
    {
        logStream << parm1;
        print_impl(parm...);
    }

    template<typename LogPolicy>
    template<LoggingLogLevel level, typename... Args>
    void Logger<LogPolicy>::print(Args... args) {
        write_mutex.lock();
        currentLogLevel = level;
        if (logType == FULL) {
            switch (level) {
                case DEBUGGING:
                    logStream << "<DEBUG> :";
                    break;
                case WARNING:
                    logStream << "<WARNING> :";
                    break;
                case ERROR:
                    logStream << "<ERROR> :";
                    break;
                case INFO:
                    logStream << "<INFO> :";
                    break;
                case VERBOSE:
                    logStream << "<VERBOSE> :";
                    break;
                case EPIC:
                    logStream << "<EPIC> :";
                    break;
                default:
                    break;
            }
        }
        print_impl(args...);
        write_mutex.unlock();
    }

    template< typename LogPolicy >
    std::string Logger< LogPolicy >::getTime()
    {
        char dateTime[128];
        struct tm tmData;
        time_t rawTime;

        // Get and format time according to ISO 8601, UTC
        time(&rawTime);
        gmtime_r(&rawTime, &tmData);
        strftime(dateTime, sizeof(dateTime), "%FT%TZ", &tmData);

        return std::string(dateTime);
    }

    template< typename LogPolicy >
    std::string Logger< LogPolicy >::getLogLineHeader()
    {
        std::stringstream header;

        header.str("");
        header.fill('0');
        header.width(7);
        header << logLineNumber++ << " < " << getTime() <<" - ";

        header.fill('0');
        header.width(7);
        header << clock() << " > ~ ";

        return header.str();
    }
}
/**
 * @}
 */

#endif //LOGGING_LOGGER_H
