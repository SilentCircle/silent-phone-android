/**
 * @file osSpecifics.h
 * @version 1.0
 * @author Werner Dittmann <Werner.Dittmann@t-online.de>
 *
 * @brief Some functions to adapt to OS and/or compiler specific handling
 * @ingroup Logging
 * @{
 *
 * This modules contains some definitions that are either specific for a particular
 * OS, compiler, or to use include files that are not common.
 *
 */

#ifndef _LOGGING_OSSPECIFICS_H_
#define _LOGGING_OSSPECIFICS_H_

#ifndef __EXPORT
#if defined _WIN32 || defined __CYGWIN__
    #ifdef BUILDING_DLL
        #ifdef __GNUC__
            #define __EXPORT __attribute__ ((dllexport))
        #else
            #define __EXPORT __declspec(dllexport) // Note: actually gcc seems to also supports this syntax.
        #endif
    #else
        #ifdef __GNUC__
            #define __EXPORT __attribute__ ((dllimport))
        #else
            #define __EXPORT __declspec(dllimport) // Note: actually gcc seems to also supports this syntax.
        #endif
    #endif
    #define DLL_LOCAL
#else
    #if __GNUC__ >= 4 || __clang_major__  >= 3
        #define __EXPORT __attribute__ ((visibility ("default")))
        #define __LOCAL  __attribute__ ((visibility ("hidden")))
    #else
        #define DLL_PUBLIC
        #define DLL_LOCAL
    #endif
#endif
#endif

#if defined(_WIN32) || defined(_WIN64)
    #define snprintf _snprintf
#endif

#ifndef DEPRECATED
#if __GNUC__ || __clang_major__  >= 3
    #define DEPRECATED __attribute__((deprecated))
#elif defined(_MSC_VER)
    #define DEPRECATED __declspec(deprecated)
#else
    #pragma message("WARNING: You need to implement DEPRECATED for this compiler")
#define DEPRECATED
#endif
#endif

/**
 * @}
 */
#endif  // _LOGGING_OSSPECIFICS_H_
