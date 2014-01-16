## Introduction

This repository contains the sources for Silent Circle's Silent Phone for Android project. Using these sources and the instructions that follow, you will be able to build a Silent Phone APK that can be installed on an Android device and with a Silent Circle subscripion be able to make secure encrypted calls.

### What's New In This Version
- These sources are for the version 1.8.1 project, which was the version of the project that was shipping at the time we started building this commit.
- New for this release the build uses the Gradle build system.

### Overview

Silent Phone consists of two major parts. The first comprises the Android Java modules and their associated layout files, resources files, and generated source and resource files. The second part, the phone engine, includes the SIP, RTP, ZRTP, TLS, and codec handling, along with some other functions. It is based on C/C++. The Java part uses standard JNI techniques to communicate with the C/C++ part.

### Prerequisites

This short description assumes that you have a good understanding of the Android SDK, the Android NDK (Native Development Kit), and their build procedures.

To compile and build Silent Phone you need a full Android development environment that includes the Android Java SDK and the Android NDK. Because SilentPhone depends upon Android API 17 (Jelly Bean), make sure you also download and install the necessary SDK modules. You may wish to use the Eclipse Android SDK extensions.

#### Specific Recommendations

Using the following components, we have built the product in a virtual machine, on bare iron, and on a laptop that also serves as a personal machine. Other successful configurations are certainly possible, this is the one we tested with.

- minimum system configuration: 1,536 Mb memory, 20 GB disk

- debian-7.0.0-amd64-DVD-1.iso
  
  In response to "`software selection`" all you need is
         
         - [*] ssh server          <- not used by the build
         - [*] Standard system utilites

         Use a mirror

 - If appropriate, install virtual machine additions.

- Install additional packages:

         $ dpkg --add-architecture i386
         $ apt-get update
         #    some of the tools are 32 bit only
         $ apt-get install ia32-libs
         $ apt-get install git
         $ apt-get install unzip
         $ apt-get install sudo  

- Create and login to a non-privileged account

         $ adduser pat

- Create a local directory to hold the packages and tools needed to build the product

         $ mkdir ~/opt

- java

  The version of java that gives the least trouble is Sun/Oracle JDK6. For the test build, we used the `jdk-6u45-linux-x64.bin` system. It can be acquired from 
  [https://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase6-419409.html#jdk-6u45-oth-JPR]().

  The hash of the version that we tested with is 
         $ openssl sha1 jdk-6u45-linux-x64.bin
         SHA1(jdk-6u45-linux-x64.bin)= 24425cdb69c11e86d6b58757b29e5ba4e4977660

  Unpack java and move it under the local opt directory
         $ sh jdk-6u45-linux-x64.bin
         $ sudo mv jdk1.6.0_45 ~/opt

- ADT - Android Development Toolkit

  While the build doesn't use Eclipse, pulling the whole ADT ensures that many needed things are fetched.

         $ wget http://dl.google.com/android/adt/adt-bundle-linux-x86_64-20130729.zip
         $ unzip adt-bundle-linux-x86_64-20130729.zip -d ~/opt

- NDK - Native Development Kit

  Silent Phone has native components. The lastest NDK causes compiler errors, so use this one instead:

         $ wget https://dl.google.com/android/ndk/android-ndk-r8e-linux-x86_64.tar.bz2
         $ tar -xjf android-ndk-r8e-linux-x86_64.tar.bz2 -C ~/opt

- Android configuration

  Note: We had do the following few steps to update with the needed configurations.

  First, add java and android tools to the path:
  
         $ export PATH=$HOME/opt/jdk1.6.0_45/bin:$HOME/opt/adt-bundle-linux-x86_64-20130729/sdk/tools:$PATH
         
  Next, use the android tool to find the following the following tools and libraries, this list will be reduced in the next release, but for now you need the following: 

    Android SDK Tools, revision 22.3
    Android SDK Platform-tools, revision 19.0.1
    Android SDK Build-tools, revision 19.0.1
    Android SDK Build-tools, revision 19
    Android SDK Build-tools, revision 17
    SDK Platform Android 4.4.2, API 19, revision 2
    SDK Platform Android 4.2.2, API 17, revision 2
    Android Support Repository, revision 4

      $ android list sdk --all 

  For us it was entries 1,2,3,4,8,10,12,82:

     1- Android SDK Tools, revision 22.3
     2- Android SDK Platform-tools, revision 19.0.1
     3- Android SDK Build-tools, revision 19.0.1
     4- Android SDK Build-tools, revision 19
     8- Android SDK Build-tools, revision 17
    10- SDK Platform Android 4.4.2, API 19, revision 2
    12- SDK Platform Android 4.2.2, API 17, revision 2
    82- Android Support Repository, revision 4
                  
  Now use the update tool to fetch the components:

      $ android update sdk --no-ui --all --filter 1,2,3,4,8,10,12,82 
         ...license stuff...
         Do you accept the license 'android-sdk-license-bcbbd656' [y/n]: y

   Installing Archives:
           ...
   Done. 8 packages installed.


  It may also be necessary to change the privileges on one of the files, to permit the build script to execute it:
  
         $ chmod g+x ~/opt/adt-bundle-linux-x86_64-20130729/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant

- Gradle 

  This build uses the Gradle build automation tool.  You can find out more about Gradle at www.gradle.org.  On starting the build the Gradle tool will download updates to itself and other select components used in the production of this product.


### Directory structure

    .
    |-- build                  # build results
    |   |-- apk                # where the resulting apk is found
    |   `...
    |-- gradle
    |-- jni
    |-- libs
    |-- obj
    |-- res                    # Android app resources
    |-- src                    # Android app code
    |-- support
    |   |-- ActionBarSherlock  # see on github
    |   |-- polarssl           # see on github
    |   |-- silentphone        # Main directory for C/C++ sources
    |   |   |-- audio
    |   |   |-- baseclasses
    |   |   |-- codecs
    |   |   |-- encrypt
    |   |   |-- os
    |   |   |-- rtp
    |   |   |-- sdp
    |   |   |-- sipparser
    |   |   |-- stun
    |   |   |-- tiviandroid
    |   |   |-- tiviengine
    |   |   |-- utils
    |   |   |-- video
    |   |   `-- xml
    |   `-- zrtpcpp           # see on github
    `-- templates

##Further Notes:

    gradle  - gradle build support
    libs    - library output directory
    res     - resource files
    src     - the java source file use to build Silent Phone Android
    support - other repositories included by this project
              |- ActionBarSherlock - See on Github - action bar design pattern 
              |- polarssl - See on Github - SSL/TLS cryptography
              |- silentphone - Main directory for Silent Phone C/C++ sources 
              |- zrtpcpp - See on Github - C++ implementation of ZRTP protocol

##Building SilentPhone from the existing Git Repository

Clone the sources from github. If you followed the "Specific Recommendations" above, the supporting toolsets will all be in the expected places. If not, edit `BUILD.sh` and adjust the symbols to match your environment.

cd to the top level repository directory and invoke BUILD.sh

     $ bash -x BUILD.sh >& build.log

##Post-Build Instructions

A successful build results in an apk file in the build/apk directory. Move this file to your Android device and run it to install Silent Phone.  You must have a Silent Circle subscritpion to use Silent Phone, you can purchase one at [https://silentcircle.com]. Enter your acccount credentials into the app.  You can now send and receieve secure calls.

Note: Silent Circle has three products that run on Andriod:  Silent Contacts, Silent Phone and Silent Text, the products work together however to do so requires that they all be signed by the same code signing certificate.  The same codesigning certificate for open source work is included in this project and the others which is different from the certficate used to sign the Play store versions. The practical restriction to be aware of is that apps with mixed certificates are not permitted to share data and hence can not work together on the same Android device.  To clarify there is no problem sending messages or calling between devices for example communitcation between Android and iOS devices.

