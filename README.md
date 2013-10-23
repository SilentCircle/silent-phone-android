## Introduction

This repository contains the sources for Silent Circle's Silent Phone for Android project. Using these sources and the the instructions that follow, you should be able to build a Silent Phone APK that can be installed and run on an Android device.

### What's New In This Version
- These instructions have been updated with details about the build environment, to make it easier for those who want to build the APK from source.
- These sources are for the version 1.6.5 project, which was the version of the project that was shipping at the time we started building this commit.

### Coming "Soon"
- We will update these sources to the currently shipping Silent Phone 1.6.6 release.
- Beyond the 1.6.6 release we will switch to using gradle as the build tool.

### Overview

Silent Phone consists of two major parts. The first comprises the Android Java modules and their associated layout files, resources files, and generated source and resource files. The second part, the phone engine, includes the SIP, RTP, ZRTP, TLS, and codec handling, along with some other functions. It is based on C/C++. The Java part uses standard JNI techniques to communicate with the C/C++ part.

### Prerequisites

This short description assumes that you have a good understanding of the Android SDK, the Android NDK (Native Development Kit), and their build procedures.

To compile and build Silent Phone you need a full Android development environment that includes the Android Java SDK and the Android NDK. Because SilentPhone depends upon Android API 17 (Jelly Bean), make sure you also download and install the necessary SDK modules. You may wish to use the Eclipse Android SDK extensions.

#### Specific Recommendations

Using the following components, we have built the product in a virtual machine, on bare iron, and on a laptop that also serves as a personal machine. Other successful configurations are certainly possible, but this is the one we tested with.

- minimum system configuration: 512 Mb memory, 20 GB disk
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

- java

  The version of java that gives the least trouble is Sun/Oracle JDK6. For the test build, we used the `jdk-6u45-linux-x64.bin` system. It can be acquired from 
  
  [https://www.oracle.com/technetwork/java/javasebusiness/downloads/java-archive-downloads-javase6-419409.html#jdk-6u45-oth-JPR]().

         $ sh jdk-6u45-linux-x64.bin
         $ sudo mv jdk1.6.0_45 /opt

  The hash of the version that we tested with is 
         $ openssl sha1 jdk-6u45-linux-x64.bin
         SHA1(jdk-6u45-linux-x64.bin)= 24425cdb69c11e86d6b58757b29e5ba4e4977660

- ADT - Android Development Toolkit

  While the build doesn't use Eclipse, pulling the whole ADT ensures that many needed things are fetched.

         $ wget http://dl.google.com/android/adt/adt-bundle-linux-x86_64-20130729.zip
         $ sudo unzip adt-bundle-linux-x86_64-20130729.zip -d /opt

- NDK - Native Development Kit

  Silent Phone has native components. The lastest NDK causes compiler errors, so use this one instead:

         $ wget https://dl.google.com/android/ndk/android-ndk-r8e-linux-x86_64.tar.bz2
         $ sudo tar -xjf android-ndk-r8e-linux-x86_64.tar.bz2 -C /opt

- Android configuration

  Note: We had do the following few steps as the root user in order to update with the needed configurations.

  First, add java and android tools to the path:
  
         $ export PATH=$PATH:/opt/jdk1.6.0_45/bin:/opt/adt-bundle-linux-x86_64-20130729/sdk/tools
         
  Next, use the android tool to find "SDK Platform Android 4.2.2, API 17, revision 2" and "Android Support Repository, revision 2"

         $ android list sdk

  For us it was entries 4 and 44:
  
          4- SDK Platform Android 4.2.2, API 17, revision 2
         44- Android Support Repository, revision 2

  Update those entries, doing the higher number one first so the ordering does not change:

         $ android update sdk --no-ui --filter 44
           ...license stuff...
           Do you accept the license 'android-sdk-license-bcbbd656' [y/n]: y

           Installing Archives:
             Preparing to install archives
             Downloading Android Support Repository, revision 2
             Installing Android Support Repository, revision 2
               Installed Android Support Repository, revision 299%)
           Done. 1 package installed.

  Then:
         
         $ android update sdk --no-ui --filter 4
           ...license stuff...
           Do you accept the license 'android-sdk-license-bcbbd656' [y/n]: y
 
           Installing Archives:
             Preparing to install archives
             Downloading SDK Platform Android 4.2.2, API 17, revision 2
             Installing SDK Platform Android 4.2.2, API 17, revision 2
               Installed SDK Platform Android 4.2.2, API 17, revision 296%)
           Done. 1 package installed.

  It may also be necessary to change the privileges on one of the files, to permit the build script to execute it:
  
         $ chmod g+x /opt/adt-bundle-linux-x86_64-20130729/eclipse/plugins/org.apache.ant_1.8.3.v201301120609/bin/ant
           
  You can now exit root.

- Fix up access to /opt

  (It could be that the prerequisites don't permit the needed access.)
  
         $ sudo groupadd android
         $ sudo usermod -a -G android my-account-goes-here
         $ sudo chgrp -R android /opt/adt-bundle-linux-x86_64-20130729 /opt/android-ndk-r8e /opt/jdk1.6.0_45

  In order to have the shell pick up the android group, you will have to logout and log back in.

### Directory structure

    .
    |-- README.md
    |-- BUILD.sh
    |-- gradle
    |-- jni
    |-- libs
    |-- res
    |-- src
    `-- support                 
        |-- ActionBarSherlock   # see on github 
        |-- polarssl            # see on github
        |-- silentphone         # Main directory for C/C++ sources
        |   |-- audio
        |   |-- baseclasses
        |   |-- codecs
        |   |-- encrypt
        |   |-- os
        |   |-- rtp
        |   |-- sdp
        |   |-- sipparser
        |   |-- stun
        |   |-- tiviandroid
        |   |-- tiviengine
        |   |-- utils
        |   |-- video
        |   `-- xml
        `-- zrtpcpp             # see on github

##Further Notes:

    gradle  - not used to build this project
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

A successful build will result in a `silentphone.apk` file in the top level directory. Move this file to your Android device and run it to install Silent Phone. Then go to [https://silentcircle.com/login]() and enter your account credentials. On the next screen click on "`Add Silent Phone`" and enter the displayed activation code into your android device. You can now use your application to send and receive calls.
