## How to compile and build SilentPhone

SilentPhone consists of two major parts: the Android Java modules and their
associated layout files, resources files, and generated source and resource
files. The second part is C/C++ based the phone engine which includes the SIP,
RTP, ZRTP, TLS, and codec handling among some other functions. The Java part
uses standard JNI techniques to communicate with the C/C++ parts.


### Prerequisites

This short description assumes that you have a good understanding of the
Android SDK, the Android NDK, and their build procedures.

To compile and build SilentPhone you require a full Android development
environment that includes the Android Java SDK and the Android NDK (Native
Development Kit). Because SilentPhone shall support Android API 10
(Gingerbread 2.3.x) make sure you also download and install the necessary SDK
modules. Furthermore I recommend to use the Eclipse Android SDK extensions.


### Directory structure

SilentPhone uses a set of sources that must be compiled and linked
together. To simplify development we have set up a specific directory
structure that contains the sources and build files. In the following figure
_$SRC\_ROOT_ denotes the root directory of this specific directory layout. The
figure does not show every directory, only the top level and relevant
directories.

    $SRC_ROOT
     |
     |-- tivi                       # Root for SilentPhone, Tivi C/C++ sources
     |   |-- libs
     |   |   |-- polarssl-1.1.1     # PolarSSL 1.1.1 sources
     |   |   `-- libzrtptivi-3.0.0  # ZRTP lib sources (see remark below)
     |   |-- scandroid              # SilentPhone Android main directory
     |   |   |-- assets
     |   |   |-- bin
     |   |   |-- gen
     |   |   |-- jni
     |   |   |-- libs
     |   |   |-- obj
     |   |   |-- res
     |   |   |-- src
     |   |-- sources                # Main directory for C/C++ sources
     |   |   |-- android
     |   |   |-- apple
     |   |   |-- audio
     |   |   |-- baseclasses
     |   |   |-- codecs
     |   |   |-- encrypt
     |   |   |-- gui
     |   |   |-- ios
     |   |   |-- os
     |   |   |-- rtp
     |   |   |-- sdp
     |   |   |-- sipparser
     |   |   |-- stun
     |   |   |-- tiviandroid
     |   |   |-- tiviengine
     |   |   |-- tools
     |   |   |-- uml
     |   |   |-- utils
     |   |   |-- video
     |   |   |-- winpc
     |   |   `-- xml
     |   `-- tina
     `-- zrtpcpp                    # Development directory for ZRTP
         |-- bnlib
         |-- clients
         |   |-- ccrtp
         |   `-- tivi               # Tivi (iOS, Android) extensions
         |-- cmake
         |-- common
         |-- cryptcommon
         |-- demo
         |-- doc
         |-- srtp
         `-- zrtp
          

**NOTES:** 

  - Depending on the development environment you may either use the
    `libs/libzrtptivi-3.0.0` directory or the main `zrtpcpp` directory. The `libs`
    subdirectory shall contain a stable version of the ZRTP/SRTP sources and the
    `zrtpcpp` directory may contain a development version. In any case the ZRTP
    directories must contain the non-public extensions for the Tivi client.
  - The directory name `scandroid` is not fixed and you may choose a different
    name. However, this directory shall be at this position in the directory
    structure. 
  - The directory names _tivi_, _zrtpcpp_, and _libzrtptivi-3.0.0_ are not
    fixed. If you use different names you must adapt the names in the Android
    NDK build files.


### Building the SilentPhone client from exising Git repository

SilentPhone's Git repository contains all necessary shared libraries to build
the required shared library and to install it. After cloning the repository
you should perform a build for the shared libraries to make sure all libraries
are installed in their correct places. 

After that step you can build SilentPhone. Either setup an Eclipse Android
project or use the `ant` command line tools to generate the application
package.

**NOTE:** After the SilentPhone code basis becomes more stable we may put the
libraries also into the Git repository. This saves the extra build step. 


### Building the shared libraries

After you built the library once it's usually not necessary to re-build it,
except you need to fix a problem inside the library code.

The directory `scandroid/jni` contains the build files to compile and link the
shared libraries for SilentPhone. Depending on your directory setup and naming
you need to adapt some lines in `jni/Android.mk`. The build files assumes the
shown directory structure.

The `Android.mk` file contains two `ifeq` sections that show how to define the
path names for different environments. Use this as a template to setup your
own path names.

If required you may also adapt the line that defines `ZRTP_SRC_PATH` to point
to the correct ZRTP source directory. Some comments in `Android.mk` already
show two different configurations.

After you adapted the build files you may just run `ndk-build` from the
command line. The NDK build script copies the shared libraries to their
correct places inside the Android project. Now you can build the SilentPhone
client with either the Eclipse Android extensions and with the `ant` command
line tool.




